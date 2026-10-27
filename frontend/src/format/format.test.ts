import { describe, expect, it } from 'vitest';
import { formatDateTime, futureOccurrences, nextOccurrence } from './date';
import { formatPrice } from './price';

// Intl uses U+00A0 between "R$" and the number; compare with plain spaces.
const plain = (text: string) => text.replaceAll(String.fromCharCode(160), ' ');

describe('formatPrice', () => {
  it('returns "Grátis" when free, even with values', () => {
    expect(
      formatPrice({ isFree: true, priceMinCents: 100, priceMaxCents: 200 }),
    ).toBe('Grátis');
  });

  it('formats a range', () => {
    expect(
      plain(
        formatPrice({
          isFree: false,
          priceMinCents: 2500,
          priceMaxCents: 8000,
        }),
      ),
    ).toBe('R$ 25,00 – R$ 80,00');
  });

  it('formats a single price (only min, only max, or equal)', () => {
    const only = (min: number | null, max: number | null) =>
      plain(
        formatPrice({ isFree: false, priceMinCents: min, priceMaxCents: max }),
      );
    expect(only(2500, null)).toBe('R$ 25,00');
    expect(only(null, 2500)).toBe('R$ 25,00');
    expect(only(2500, 2500)).toBe('R$ 25,00');
  });

  it('formats thousands and cents in pt-BR', () => {
    expect(
      plain(
        formatPrice({
          isFree: false,
          priceMinCents: 123456,
          priceMaxCents: 123456,
        }),
      ),
    ).toBe('R$ 1.234,56');
  });

  it('returns empty text without price', () => {
    expect(formatPrice({ isFree: false })).toBe('');
    expect(
      formatPrice({ isFree: false, priceMinCents: null, priceMaxCents: null }),
    ).toBe('');
  });
});

describe('formatDateTime', () => {
  it('uses the city time zone, crossing the day boundary', () => {
    const text = plain(
      formatDateTime('2030-01-01T02:00:00Z', 'America/Recife'),
    );
    expect(text).toContain('31 de dez. de 2029');
    expect(text).toContain('23:00');
  });

  it('falls back to the browser time zone when the zone is invalid', () => {
    expect(() =>
      formatDateTime('2030-01-01T12:00:00Z', 'Nao/Existe'),
    ).not.toThrow();
    expect(formatDateTime('2030-01-01T12:00:00Z', 'Nao/Existe')).toContain(
      '2030',
    );
  });
});

describe('occurrences', () => {
  const now = new Date('2030-06-01T12:00:00Z');
  const at = (
    startsAt: string,
    status: 'SCHEDULED' | 'CANCELLED' = 'SCHEDULED',
  ) => ({
    startsAt,
    status,
  });

  it('keeps only future sessions, ordered by start', () => {
    const list = [
      at('2030-08-01T20:00:00Z'),
      at('2030-01-01T20:00:00Z'),
      at('2030-07-01T20:00:00Z'),
    ];
    expect(futureOccurrences(list, now).map((o) => o.startsAt)).toEqual([
      '2030-07-01T20:00:00Z',
      '2030-08-01T20:00:00Z',
    ]);
  });

  it('picks the next SCHEDULED session, skipping past and cancelled ones', () => {
    const list = [
      at('2030-01-01T20:00:00Z'),
      at('2030-07-01T20:00:00Z', 'CANCELLED'),
      at('2030-08-01T20:00:00Z'),
    ];
    expect(nextOccurrence(list, now)?.startsAt).toBe('2030-08-01T20:00:00Z');
  });

  it('returns undefined without a future scheduled session', () => {
    expect(nextOccurrence([], now)).toBeUndefined();
    expect(nextOccurrence([at('2030-01-01T20:00:00Z')], now)).toBeUndefined();
  });
});
