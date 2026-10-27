import { describe, expect, it } from 'vitest';
import type { Problem } from '../api/problem';
import { makeEvent } from '../test/server';
import {
  emptyDraft,
  emptySession,
  fromEvent,
  mapProblemErrors,
  toCommand,
  validateEvent,
  type EventDraft,
} from './eventDraft';

const TZ = 'America/Recife';
const NOW = new Date('2030-06-01T12:00:00Z');

function draft(overrides: Partial<EventDraft> = {}): EventDraft {
  return {
    ...emptyDraft(),
    title: 'Show',
    categoryId: '1',
    sessions: [{ ...emptySession(), startsAt: '2099-03-01T17:00' }],
    ...overrides,
  };
}

describe('validateEvent', () => {
  it('accepts a minimal valid draft', () => {
    expect(validateEvent(draft(), NOW, TZ)).toEqual({});
  });

  it('requires title and category', () => {
    const errors = validateEvent(
      draft({ title: '   ', categoryId: '' }),
      NOW,
      TZ,
    );
    expect(errors.title).toBeDefined();
    expect(errors.categoryId).toBeDefined();
  });

  it('limits sizes', () => {
    const errors = validateEvent(
      draft({
        title: 'a'.repeat(256),
        slug: 'a'.repeat(256),
        description: 'a'.repeat(20001),
        ageRating: '12345678901',
      }),
      NOW,
      TZ,
    );
    expect(Object.keys(errors).sort()).toEqual([
      'ageRating',
      'description',
      'slug',
      'title',
    ]);
  });

  it('checks the slug pattern but lets it be empty', () => {
    expect(validateEvent(draft({ slug: '' }), NOW, TZ).slug).toBeUndefined();
    expect(
      validateEvent(draft({ slug: 'meu-evento-2' }), NOW, TZ).slug,
    ).toBeUndefined();
    for (const slug of ['Meu Evento', 'a--b', '-a', 'a-', 'é']) {
      expect(validateEvent(draft({ slug }), NOW, TZ).slug).toBeDefined();
    }
  });

  it('accepts only http(s) links', () => {
    const ok = draft({
      ticketUrl: 'https://example.com/t',
      imageUrl: 'http://example.com/i.png',
    });
    expect(validateEvent(ok, NOW, TZ)).toEqual({});
    const bad = validateEvent(
      draft({ ticketUrl: 'javascript:alert(1)', imageUrl: 'ftp://x.com/a' }),
      NOW,
      TZ,
    );
    expect(bad.ticketUrl).toBeDefined();
    expect(bad.imageUrl).toBeDefined();
  });

  it('validates prices: format and min <= max', () => {
    expect(
      validateEvent(draft({ priceMin: 'abc', priceMax: '10,999' }), NOW, TZ),
    ).toMatchObject({
      priceMin: expect.any(String),
      priceMax: expect.any(String),
    });
    expect(
      validateEvent(draft({ priceMin: '90', priceMax: '50' }), NOW, TZ)
        .priceMax,
    ).toBeDefined();
    expect(
      validateEvent(draft({ priceMin: '50', priceMax: '50' }), NOW, TZ),
    ).toEqual({});
    expect(validateEvent(draft({ priceMin: '10' }), NOW, TZ)).toEqual({});
  });

  it('ignores price fields when the event is free', () => {
    expect(
      validateEvent(draft({ isFree: true, priceMin: 'abc' }), NOW, TZ),
    ).toEqual({});
  });

  it('requires a future session only for PUBLISHED events', () => {
    const past = [{ ...emptySession(), startsAt: '2001-03-01T17:00' }];
    expect(
      validateEvent(draft({ status: 'PUBLISHED', sessions: past }), NOW, TZ)
        .occurrences,
    ).toBeDefined();
    expect(
      validateEvent(draft({ status: 'PUBLISHED', sessions: [] }), NOW, TZ)
        .occurrences,
    ).toBeDefined();
    expect(
      validateEvent(draft({ status: 'DRAFT', sessions: [] }), NOW, TZ),
    ).toEqual({});
    expect(
      validateEvent(draft({ status: 'CANCELLED', sessions: past }), NOW, TZ),
    ).toEqual({});
    expect(validateEvent(draft({ status: 'PUBLISHED' }), NOW, TZ)).toEqual({});
  });

  it('evaluates "future" in the city zone', () => {
    // 2030-06-01T12:00Z is 09:00 in Recife: 10:00 local is future, 08:00 is past.
    const at = (startsAt: string) =>
      draft({
        status: 'PUBLISHED',
        sessions: [{ ...emptySession(), startsAt }],
      });
    expect(
      validateEvent(at('2030-06-01T10:00'), NOW, TZ).occurrences,
    ).toBeUndefined();
    expect(
      validateEvent(at('2030-06-01T08:00'), NOW, TZ).occurrences,
    ).toBeDefined();
  });

  it('validates sessions: missing start, end before start, repeated start', () => {
    const errors = validateEvent(
      draft({
        sessions: [
          { ...emptySession(), startsAt: '' },
          {
            ...emptySession(),
            startsAt: '2099-03-01T17:00',
            endsAt: '2099-03-01T16:00',
          },
          { ...emptySession(), startsAt: '2099-04-01T17:00' },
          { ...emptySession(), startsAt: '2099-04-01T17:00' },
        ],
      }),
      NOW,
      TZ,
    );
    expect(errors['occurrences.0.startsAt']).toBeDefined();
    expect(errors['occurrences.1.endsAt']).toBeDefined();
    expect(errors['occurrences.2.startsAt']).toBeDefined();
    expect(errors['occurrences.3.startsAt']).toBeDefined();
    expect(errors['occurrences.1.startsAt']).toBeUndefined();
  });
});

describe('toCommand', () => {
  it('converts prices to cents and sessions to ISO with offset', () => {
    const body = toCommand(
      draft({
        slug: ' meu-show ',
        venueId: 'venue-id',
        priceMin: '25,50',
        priceMax: '80',
        description: '',
        sessions: [
          {
            ...emptySession(),
            startsAt: '2099-03-01T17:00',
            endsAt: '2099-03-01T19:00',
            status: 'SOLD_OUT',
          },
        ],
      }),
      7,
      TZ,
    );
    expect(body).toMatchObject({
      cityId: 7,
      categoryId: 1,
      venueId: 'venue-id',
      title: 'Show',
      slug: 'meu-show',
      description: null,
      priceMinCents: 2550,
      priceMaxCents: 8000,
      isFree: false,
      occurrences: [
        {
          startsAt: '2099-03-01T17:00:00-03:00',
          endsAt: '2099-03-01T19:00:00-03:00',
          status: 'SOLD_OUT',
        },
      ],
    });
  });

  it('sends null prices when free, and null for blank optionals', () => {
    const body = toCommand(
      draft({ isFree: true, priceMin: '10', priceMax: '20', venueId: '' }),
      1,
      TZ,
    );
    expect(body).toMatchObject({
      isFree: true,
      priceMinCents: null,
      priceMaxCents: null,
      venueId: null,
      slug: null,
      ageRating: null,
      ticketUrl: null,
      imageUrl: null,
    });
  });
});

describe('fromEvent', () => {
  it('fills the draft in the city zone and keeps end and status of sessions', () => {
    const event = makeEvent({
      occurrences: [
        {
          startsAt: '2099-03-01T20:00:00Z',
          endsAt: '2099-03-01T22:00:00Z',
          status: 'SOLD_OUT',
        },
      ],
    });
    const result = fromEvent(event, TZ);
    expect(result).toMatchObject({
      title: 'Show Teste',
      slug: 'show-teste',
      categoryId: '1',
      priceMin: '25,00',
      priceMax: '80,00',
      isFree: false,
    });
    expect(result.sessions).toMatchObject([
      {
        startsAt: '2099-03-01T17:00',
        endsAt: '2099-03-01T19:00',
        status: 'SOLD_OUT',
      },
    ]);
  });

  it('round-trips through toCommand without losing the session data', () => {
    const event = makeEvent({
      occurrences: [
        {
          startsAt: '2099-03-01T20:00:00Z',
          endsAt: '2099-03-01T22:00:00Z',
          status: 'CANCELLED',
        },
      ],
    });
    const body = toCommand(fromEvent(event, TZ), 1, TZ);
    expect(
      body.occurrences?.map((o) => ({
        startsAt: new Date(o.startsAt).toISOString(),
        endsAt: o.endsAt ? new Date(o.endsAt).toISOString() : null,
        status: o.status,
      })),
    ).toEqual([
      {
        startsAt: '2099-03-01T20:00:00.000Z',
        endsAt: '2099-03-01T22:00:00.000Z',
        status: 'CANCELLED',
      },
    ]);
  });
});

describe('mapProblemErrors', () => {
  const problem = (errors: { field: string; message: string }[]): Problem => ({
    title: 'Unprocessable Entity',
    status: 422,
    detail: 'A requisição contém campos inválidos.',
    errors,
  });

  it('maps fields by name, price fields to the reais inputs', () => {
    const { fields, top } = mapProblemErrors(
      problem([
        { field: 'title', message: 'em branco' },
        { field: 'priceMaxCents', message: 'menor que o mínimo' },
        { field: 'priceMinCents', message: 'negativo' },
        { field: 'isFree', message: 'gratuito com preço' },
        { field: 'occurrences', message: 'sem sessão futura' },
      ]),
      0,
    );
    expect(fields).toEqual({
      title: 'em branco',
      priceMax: 'menor que o mínimo',
      priceMin: 'negativo',
      isFree: 'gratuito com preço',
      occurrences: 'sem sessão futura',
    });
    expect(top).toEqual(['A requisição contém campos inválidos.']);
  });

  it('maps session fields by index', () => {
    const { fields } = mapProblemErrors(
      problem([
        { field: 'occurrences[1].endsAt', message: 'antes do início' },
        { field: 'occurrences[0].startsAt', message: 'repetido' },
      ]),
      2,
    );
    expect(fields).toEqual({
      'occurrences.1.endsAt': 'antes do início',
      'occurrences.0.startsAt': 'repetido',
    });
  });

  it('sends unknown fields and sessions that do not exist to the top', () => {
    const { fields, top } = mapProblemErrors(
      problem([
        { field: 'cityId', message: 'a cidade informada não existe' },
        { field: 'occurrences[5].startsAt', message: 'inválido' },
      ]),
      1,
    );
    expect(fields).toEqual({});
    expect(top).toEqual([
      'A requisição contém campos inválidos.',
      'cityId: a cidade informada não existe',
      'occurrences[5].startsAt: inválido',
    ]);
  });

  it('joins several messages for the same field', () => {
    const { fields } = mapProblemErrors(
      problem([
        { field: 'slug', message: 'primeira' },
        { field: 'slug', message: 'segunda' },
      ]),
      0,
    );
    expect(fields.slug).toBe('primeira segunda');
  });

  it('works without errors[]', () => {
    const { fields, top } = mapProblemErrors(
      { title: 'x', status: 422, detail: 'Só o detail.' },
      0,
    );
    expect(fields).toEqual({});
    expect(top).toEqual(['Só o detail.']);
  });
});
