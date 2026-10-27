import {
  startTransition,
  useActionState,
  useState,
  type FormEvent,
  type ReactNode,
} from 'react';
import { Link, useNavigate } from 'react-router';
import { flattenCategories, useCategories } from '../api/categories';
import {
  fetchEvent,
  useCreateEvent,
  useUpdateEvent,
  type Event,
} from '../api/events';
import { isProblem } from '../api/problem';
import { useVenues } from '../api/venues';
import { ErrorMessage, GENERIC_MESSAGE } from '../components/ErrorMessage';
import {
  emptyDraft,
  emptySession,
  fromEvent,
  mapProblemErrors,
  toCommand,
  validateEvent,
  type EventDraft,
  type EventStatus,
  type FieldErrors,
  type OccurrenceStatus,
  type SessionDraft,
} from './eventDraft';
import { EVENT_STATUS_LABEL, OCCURRENCE_STATUS_LABEL } from './labels';

type FormResult =
  | { kind: 'idle' }
  | { kind: 'invalid'; fields: FieldErrors }
  | { kind: 'error'; fields: FieldErrors; top: string[] }
  | { kind: 'conflict'; latest: Event };

type EventFormProps = {
  /** Event being edited; absent when creating. */
  event?: Event;
  cityId: number;
  timeZone: string;
  uf: string;
  slug: string;
  /** Called with the server's copy when the user chooses to reload after a conflict. */
  onReload?: (latest: Event) => void;
};

const input = 'w-full rounded border border-gray-300 bg-white px-3 py-2';

function Field({
  id,
  label,
  error,
  children,
}: {
  id: string;
  label: string;
  error?: string;
  children: ReactNode;
}) {
  return (
    <div className="space-y-1">
      <label htmlFor={id} className="block font-medium">
        {label}
      </label>
      {children}
      {error && (
        <p id={`${id}-error`} className="text-sm text-red-700">
          {error}
        </p>
      )}
    </div>
  );
}

export function EventForm({
  event,
  cityId,
  timeZone,
  uf,
  slug,
  onReload,
}: EventFormProps) {
  const navigate = useNavigate();
  const categories = useCategories();
  const venues = useVenues(uf, slug);
  const create = useCreateEvent();
  const update = useUpdateEvent();
  const [draft, setDraft] = useState<EventDraft>(() =>
    event ? fromEvent(event, timeZone) : emptyDraft(),
  );

  // The action reads the latest `draft`: React re-binds it on every render.
  const [result, submit, pending] = useActionState<FormResult, FormData>(
    async () => {
      const invalid = validateEvent(draft, new Date(), timeZone);
      if (Object.keys(invalid).length > 0) {
        return { kind: 'invalid', fields: invalid };
      }
      const body = toCommand(draft, cityId, timeZone);
      try {
        if (event) {
          await update.mutateAsync({
            id: event.id,
            body: { ...body, status: draft.status, version: event.version },
          });
        } else {
          await create.mutateAsync(body);
        }
        navigate('/admin/eventos');
        return { kind: 'idle' };
      } catch (error) {
        return failure(error, event, draft.sessions.length);
      }
    },
    { kind: 'idle' },
  );

  if (categories.error ?? venues.error) {
    return <ErrorMessage error={categories.error ?? venues.error} />;
  }
  if (!categories.data || !venues.data) {
    return <p aria-busy="true">Carregando formulário…</p>;
  }

  const fields: FieldErrors =
    result.kind === 'invalid' || result.kind === 'error' ? result.fields : {};
  const set = <K extends keyof EventDraft>(key: K, value: EventDraft[K]) =>
    setDraft((current) => ({ ...current, [key]: value }));
  const setSession = (index: number, patch: Partial<SessionDraft>) =>
    set(
      'sessions',
      draft.sessions.map((session, i) =>
        i === index ? { ...session, ...patch } : session,
      ),
    );

  // Not <form action>: React 19 resets the form after an action and that clears <select>s.
  const handleSubmit = (e: FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    startTransition(() => submit(new FormData(e.currentTarget)));
  };

  const categoryOptions = flattenCategories(categories.data);
  const venueOptions = venues.data.items;
  const currentVenue = event?.venue;
  const venueMissing =
    currentVenue && !venueOptions.some((venue) => venue.id === currentVenue.id);

  return (
    <form onSubmit={handleSubmit} noValidate className="max-w-2xl space-y-4">
      <h1 className="text-2xl font-semibold">
        {event ? 'Editar evento' : 'Novo evento'}
      </h1>

      {result.kind === 'error' && result.top.length > 0 && (
        <ErrorMessage
          error={{ title: 'Erro', status: 422, detail: result.top[0] }}
          details={result.top.slice(1)}
        />
      )}
      {result.kind === 'conflict' && (
        <div
          role="alert"
          className="rounded border border-amber-400 bg-amber-50 p-4 text-amber-900"
        >
          <p>O evento foi alterado por outra pessoa.</p>
          <button
            type="button"
            className="mt-2 rounded border border-amber-600 px-3 py-1"
            onClick={() => onReload?.(result.latest)}
          >
            Recarregar
          </button>
        </div>
      )}

      <Field id="title" label="Título" error={fields.title}>
        <input
          id="title"
          name="title"
          className={input}
          value={draft.title}
          onChange={(e) => set('title', e.target.value)}
          aria-invalid={Boolean(fields.title)}
        />
      </Field>

      <Field id="slug" label="Slug (opcional)" error={fields.slug}>
        <input
          id="slug"
          name="slug"
          className={input}
          value={draft.slug}
          onChange={(e) => set('slug', e.target.value)}
          aria-invalid={Boolean(fields.slug)}
        />
      </Field>

      <Field id="categoryId" label="Categoria" error={fields.categoryId}>
        <select
          id="categoryId"
          name="categoryId"
          className={input}
          value={draft.categoryId}
          onChange={(e) => set('categoryId', e.target.value)}
          aria-invalid={Boolean(fields.categoryId)}
        >
          <option value="">Selecione…</option>
          {categoryOptions.map((option) => (
            <option key={option.id} value={String(option.id)}>
              {'    '.repeat(option.depth)}
              {option.name}
            </option>
          ))}
        </select>
      </Field>

      <Field id="venueId" label="Local" error={fields.venueId}>
        <select
          id="venueId"
          name="venueId"
          className={input}
          value={draft.venueId}
          onChange={(e) => set('venueId', e.target.value)}
          aria-invalid={Boolean(fields.venueId)}
        >
          <option value="">Sem local</option>
          {venueMissing && (
            <option value={currentVenue.id}>{currentVenue.name}</option>
          )}
          {venueOptions.map((venue) => (
            <option key={venue.id} value={venue.id}>
              {venue.name}
            </option>
          ))}
        </select>
      </Field>

      <Field id="description" label="Descrição" error={fields.description}>
        <textarea
          id="description"
          name="description"
          rows={5}
          className={input}
          value={draft.description}
          onChange={(e) => set('description', e.target.value)}
          aria-invalid={Boolean(fields.description)}
        />
      </Field>

      <Field id="status" label="Status" error={fields.status}>
        <select
          id="status"
          name="status"
          className={input}
          value={draft.status}
          onChange={(e) => set('status', e.target.value as EventStatus)}
        >
          {(Object.keys(EVENT_STATUS_LABEL) as EventStatus[]).map((status) => (
            <option key={status} value={status}>
              {EVENT_STATUS_LABEL[status]}
            </option>
          ))}
        </select>
      </Field>

      <Field
        id="ageRating"
        label="Classificação etária"
        error={fields.ageRating}
      >
        <input
          id="ageRating"
          name="ageRating"
          className={input}
          value={draft.ageRating}
          onChange={(e) => set('ageRating', e.target.value)}
          aria-invalid={Boolean(fields.ageRating)}
        />
      </Field>

      <fieldset className="space-y-3">
        <legend className="font-medium">Preço</legend>
        <label className="flex items-center gap-2">
          <input
            type="checkbox"
            name="isFree"
            checked={draft.isFree}
            onChange={(e) => set('isFree', e.target.checked)}
          />
          Gratuito
        </label>
        {fields.isFree && (
          <p className="text-sm text-red-700">{fields.isFree}</p>
        )}
        <Field id="priceMin" label="Preço mínimo (R$)" error={fields.priceMin}>
          <input
            id="priceMin"
            name="priceMin"
            inputMode="decimal"
            className={input}
            disabled={draft.isFree}
            value={draft.isFree ? '' : draft.priceMin}
            onChange={(e) => set('priceMin', e.target.value)}
            aria-invalid={Boolean(fields.priceMin)}
          />
        </Field>
        <Field id="priceMax" label="Preço máximo (R$)" error={fields.priceMax}>
          <input
            id="priceMax"
            name="priceMax"
            inputMode="decimal"
            className={input}
            disabled={draft.isFree}
            value={draft.isFree ? '' : draft.priceMax}
            onChange={(e) => set('priceMax', e.target.value)}
            aria-invalid={Boolean(fields.priceMax)}
          />
        </Field>
      </fieldset>

      <Field id="ticketUrl" label="Link de ingresso" error={fields.ticketUrl}>
        <input
          id="ticketUrl"
          name="ticketUrl"
          className={input}
          value={draft.ticketUrl}
          onChange={(e) => set('ticketUrl', e.target.value)}
          aria-invalid={Boolean(fields.ticketUrl)}
        />
      </Field>

      <Field id="imageUrl" label="Imagem (URL)" error={fields.imageUrl}>
        <input
          id="imageUrl"
          name="imageUrl"
          className={input}
          value={draft.imageUrl}
          onChange={(e) => set('imageUrl', e.target.value)}
          aria-invalid={Boolean(fields.imageUrl)}
        />
      </Field>

      <fieldset className="space-y-3">
        <legend className="font-medium">Sessões (horário de {timeZone})</legend>
        {fields.occurrences && (
          <p className="text-sm text-red-700">{fields.occurrences}</p>
        )}
        {draft.sessions.map((session, index) => {
          const n = index + 1;
          const prefix = `occurrences.${index}`;
          return (
            <fieldset
              key={session.key}
              className="space-y-2 rounded border border-gray-200 p-3"
            >
              <legend className="px-1 text-sm">Sessão {n}</legend>
              <Field
                id={`startsAt-${index}`}
                label={`Início da sessão ${n}`}
                error={fields[`${prefix}.startsAt`]}
              >
                <input
                  id={`startsAt-${index}`}
                  name={`startsAt-${index}`}
                  type="datetime-local"
                  className={input}
                  value={session.startsAt}
                  onChange={(e) =>
                    setSession(index, { startsAt: e.target.value })
                  }
                />
              </Field>
              <Field
                id={`endsAt-${index}`}
                label={`Fim da sessão ${n}`}
                error={fields[`${prefix}.endsAt`]}
              >
                <input
                  id={`endsAt-${index}`}
                  name={`endsAt-${index}`}
                  type="datetime-local"
                  className={input}
                  value={session.endsAt}
                  onChange={(e) =>
                    setSession(index, { endsAt: e.target.value })
                  }
                />
              </Field>
              <Field
                id={`sessionStatus-${index}`}
                label={`Situação da sessão ${n}`}
                error={fields[`${prefix}.status`]}
              >
                <select
                  id={`sessionStatus-${index}`}
                  name={`sessionStatus-${index}`}
                  className={input}
                  value={session.status}
                  onChange={(e) =>
                    setSession(index, {
                      status: e.target.value as OccurrenceStatus,
                    })
                  }
                >
                  {(
                    Object.keys(OCCURRENCE_STATUS_LABEL) as OccurrenceStatus[]
                  ).map((status) => (
                    <option key={status} value={status}>
                      {OCCURRENCE_STATUS_LABEL[status]}
                    </option>
                  ))}
                </select>
              </Field>
              <button
                type="button"
                className="text-red-700 underline"
                onClick={() =>
                  set(
                    'sessions',
                    draft.sessions.filter((_, i) => i !== index),
                  )
                }
              >
                Remover sessão {n}
              </button>
            </fieldset>
          );
        })}
        <button
          type="button"
          className="rounded border border-gray-300 px-3 py-1"
          onClick={() => set('sessions', [...draft.sessions, emptySession()])}
        >
          Adicionar sessão
        </button>
      </fieldset>

      <div className="flex gap-3">
        <button
          type="submit"
          disabled={pending}
          className="rounded bg-blue-700 px-4 py-2 text-white disabled:opacity-50"
        >
          {pending ? 'Salvando…' : 'Salvar'}
        </button>
        <Link to="/admin/eventos" className="px-4 py-2 underline">
          Cancelar
        </Link>
      </div>
    </form>
  );
}

/** Turns a failed save into what the form shows: field errors, a top message or the conflict notice. */
async function failure(
  error: unknown,
  event: Event | undefined,
  sessionCount: number,
): Promise<FormResult> {
  if (!isProblem(error)) {
    return { kind: 'error', fields: {}, top: [GENERIC_MESSAGE] };
  }
  if (error.status === 422) {
    return { kind: 'error', ...mapProblemErrors(error, sessionCount) };
  }
  if (error.status === 409 && event) {
    // 409 also means "slug already exists": only a changed version is a concurrency conflict.
    try {
      const latest = await fetchEvent(event.id);
      if (latest.version !== event.version) return { kind: 'conflict', latest };
    } catch {
      // Could not tell; fall through and show the server's own message.
    }
  }
  return {
    kind: 'error',
    fields: {},
    top: [error.detail ?? error.title],
  };
}
