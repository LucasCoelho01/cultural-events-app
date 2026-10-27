import type { Event } from '../api/events';
import type { Problem } from '../api/problem';
import type { components } from '../api/schema';
import { isoToLocal, isValidLocal, localToIso } from './datetime';
import { formatReais, parseReais } from './money';

export type EventStatus = components['schemas']['EventStatus'];
export type OccurrenceStatus = components['schemas']['OccurrenceStatus'];
export type EventInput = components['schemas']['EventInput'];
export type EventUpdateInput = components['schemas']['EventUpdateInput'];

export type SessionDraft = {
  /** Stable React key; never sent to the API. */
  key: string;
  startsAt: string;
  endsAt: string;
  status: OccurrenceStatus;
};

/** Everything the form edits, as the text/ids the inputs hold. */
export type EventDraft = {
  title: string;
  slug: string;
  categoryId: string;
  venueId: string;
  description: string;
  status: EventStatus;
  ageRating: string;
  isFree: boolean;
  priceMin: string;
  priceMax: string;
  ticketUrl: string;
  imageUrl: string;
  sessions: SessionDraft[];
};

/**
 * Error messages by form key: `title`, `slug`, `categoryId`, `venueId`, `description`,
 * `status`, `ageRating`, `isFree`, `priceMin`, `priceMax`, `ticketUrl`, `imageUrl`,
 * `occurrences` (the session list) and `occurrences.<i>.startsAt|endsAt|status`.
 */
export type FieldErrors = Record<string, string>;

let sessionCounter = 0;
export const newSessionKey = () => `session-${++sessionCounter}`;

export function emptySession(): SessionDraft {
  return {
    key: newSessionKey(),
    startsAt: '',
    endsAt: '',
    status: 'SCHEDULED',
  };
}

export function emptyDraft(): EventDraft {
  return {
    title: '',
    slug: '',
    categoryId: '',
    venueId: '',
    description: '',
    status: 'DRAFT',
    ageRating: '',
    isFree: false,
    priceMin: '',
    priceMax: '',
    ticketUrl: '',
    imageUrl: '',
    sessions: [],
  };
}

export function fromEvent(event: Event, timeZone: string): EventDraft {
  return {
    title: event.title,
    slug: event.slug,
    categoryId: String(event.category.id),
    venueId: event.venue?.id ?? '',
    description: event.description ?? '',
    status: event.status,
    ageRating: event.ageRating ?? '',
    isFree: event.isFree,
    priceMin: formatReais(event.priceMinCents),
    priceMax: formatReais(event.priceMaxCents),
    ticketUrl: event.ticketUrl ?? '',
    imageUrl: event.imageUrl ?? '',
    sessions: event.occurrences.map((occurrence) => ({
      key: newSessionKey(),
      startsAt: isoToLocal(occurrence.startsAt, timeZone),
      endsAt: occurrence.endsAt ? isoToLocal(occurrence.endsAt, timeZone) : '',
      status: occurrence.status,
    })),
  };
}

const orNull = (text: string) => (text.trim() === '' ? null : text.trim());

/** Builds the request body. Assumes `validateEvent` found no errors. */
export function toCommand(
  draft: EventDraft,
  cityId: number,
  timeZone: string,
): EventInput {
  const price = (text: string) => {
    if (draft.isFree) return null;
    const cents = parseReais(text);
    return cents === 'invalid' ? null : cents;
  };
  return {
    cityId,
    categoryId: Number(draft.categoryId),
    venueId: orNull(draft.venueId),
    title: draft.title.trim(),
    slug: orNull(draft.slug),
    description: orNull(draft.description),
    status: draft.status,
    ageRating: orNull(draft.ageRating),
    priceMinCents: price(draft.priceMin),
    priceMaxCents: price(draft.priceMax),
    isFree: draft.isFree,
    ticketUrl: orNull(draft.ticketUrl),
    imageUrl: orNull(draft.imageUrl),
    occurrences: draft.sessions.map((session) => ({
      startsAt: localToIso(session.startsAt, timeZone),
      endsAt: session.endsAt ? localToIso(session.endsAt, timeZone) : null,
      status: session.status,
    })),
  };
}

const SLUG = /^[a-z0-9]+(-[a-z0-9]+)*$/;
const HTTP_URL = /^https?:\/\/\S+$/;

/**
 * Client-side mirror of the contract rules. The server stays the authority: this only
 * saves a round trip for mistakes that are obvious.
 */
export function validateEvent(
  draft: EventDraft,
  now: Date,
  timeZone: string,
): FieldErrors {
  const errors: FieldErrors = {};

  if (draft.title.trim() === '') errors.title = 'Informe o título.';
  else if (draft.title.length > 255)
    errors.title = 'O título deve ter no máximo 255 caracteres.';

  if (draft.categoryId === '') errors.categoryId = 'Selecione a categoria.';

  const slug = draft.slug.trim();
  if (slug !== '') {
    if (slug.length > 255)
      errors.slug = 'O slug deve ter no máximo 255 caracteres.';
    else if (!SLUG.test(slug))
      errors.slug =
        'Use letras minúsculas, números e hífens (ex.: meu-evento).';
  }

  if (draft.description.length > 20000)
    errors.description = 'A descrição deve ter no máximo 20000 caracteres.';
  if (draft.ageRating.length > 10)
    errors.ageRating = 'A classificação deve ter no máximo 10 caracteres.';

  for (const field of ['ticketUrl', 'imageUrl'] as const) {
    const url = draft[field].trim();
    if (url === '') continue;
    if (url.length > 2048)
      errors[field] = 'O link é longo demais (máximo 2048).';
    else if (!HTTP_URL.test(url))
      errors[field] = 'Informe um link começando com http:// ou https://.';
  }

  validatePrices(draft, errors);
  validateSessions(draft, now, timeZone, errors);
  return errors;
}

function validatePrices(draft: EventDraft, errors: FieldErrors) {
  if (draft.isFree) return;
  const min = parseReais(draft.priceMin);
  const max = parseReais(draft.priceMax);
  const message = 'Informe um valor em reais, com até 2 casas (ex.: 25,50).';
  if (min === 'invalid') errors.priceMin = message;
  if (max === 'invalid') errors.priceMax = message;
  if (typeof min === 'number' && typeof max === 'number' && min > max) {
    errors.priceMax = 'O preço máximo não pode ser menor que o mínimo.';
  }
}

function validateSessions(
  draft: EventDraft,
  now: Date,
  timeZone: string,
  errors: FieldErrors,
) {
  const starts = new Map<string, number[]>();
  let hasFuture = false;

  draft.sessions.forEach((session, index) => {
    const prefix = `occurrences.${index}`;
    let start: number | null = null;

    if (session.startsAt === '') {
      errors[`${prefix}.startsAt`] = 'Informe o início da sessão.';
    } else if (!isValidLocal(session.startsAt)) {
      errors[`${prefix}.startsAt`] = 'Data e hora inválidas.';
    } else {
      const iso = localToIso(session.startsAt, timeZone);
      start = new Date(iso).getTime();
      if (start > now.getTime()) hasFuture = true;
      starts.set(iso, [...(starts.get(iso) ?? []), index]);
    }

    if (session.endsAt !== '') {
      if (!isValidLocal(session.endsAt)) {
        errors[`${prefix}.endsAt`] = 'Data e hora inválidas.';
      } else if (
        start !== null &&
        new Date(localToIso(session.endsAt, timeZone)).getTime() < start
      ) {
        errors[`${prefix}.endsAt`] =
          'O fim da sessão não pode ser anterior ao início.';
      }
    }
  });

  for (const indexes of starts.values()) {
    if (indexes.length > 1) {
      for (const index of indexes) {
        errors[`occurrences.${index}.startsAt`] =
          'Já existe uma sessão com esse início.';
      }
    }
  }

  if (draft.status === 'PUBLISHED' && !hasFuture) {
    errors.occurrences =
      'Um evento publicado precisa de ao menos uma sessão futura.';
  }
}

const SIMPLE_FIELDS: Record<string, string> = {
  title: 'title',
  slug: 'slug',
  categoryId: 'categoryId',
  venueId: 'venueId',
  description: 'description',
  status: 'status',
  ageRating: 'ageRating',
  isFree: 'isFree',
  priceMinCents: 'priceMin',
  priceMaxCents: 'priceMax',
  ticketUrl: 'ticketUrl',
  imageUrl: 'imageUrl',
  occurrences: 'occurrences',
};

const OCCURRENCE_FIELD = /^occurrences\[(\d+)\]\.(startsAt|endsAt|status)$/;

function formKey(field: string, sessionCount: number): string | null {
  const simple = SIMPLE_FIELDS[field];
  if (simple) return simple;
  const match = OCCURRENCE_FIELD.exec(field);
  if (match && Number(match[1]) < sessionCount) {
    return `occurrences.${match[1]}.${match[2]}`;
  }
  return null;
}

/**
 * Maps the `errors[]` of a 422 `Problem` to form keys. Messages for fields the form does
 * not have (or sessions that no longer exist) go to `top`, after the problem's `detail`.
 */
export function mapProblemErrors(
  problem: Problem,
  sessionCount: number,
): { fields: FieldErrors; top: string[] } {
  const fields: FieldErrors = {};
  const top: string[] = [];
  if (problem.detail) top.push(problem.detail);

  for (const error of problem.errors ?? []) {
    const key = formKey(error.field, sessionCount);
    if (key === null) {
      top.push(`${error.field}: ${error.message}`);
    } else {
      fields[key] = fields[key]
        ? `${fields[key]} ${error.message}`
        : error.message;
    }
  }
  return { fields, top };
}
