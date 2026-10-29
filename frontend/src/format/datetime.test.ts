import { describe, expect, it } from 'vitest';
import { isoToLocal, isValidLocal, localToIso } from './datetime';

describe('localToIso', () => {
  it('applies the offset of the city zone (America/Recife, UTC-3)', () => {
    const iso = localToIso('2099-03-01T17:00', 'America/Recife');
    expect(iso).toBe('2099-03-01T17:00:00-03:00');
    expect(new Date(iso).toISOString()).toBe('2099-03-01T20:00:00.000Z');
  });

  it('handles zones ahead of UTC', () => {
    const iso = localToIso('2099-03-01T08:30', 'Asia/Kolkata');
    expect(iso).toBe('2099-03-01T08:30:00+05:30');
    expect(new Date(iso).toISOString()).toBe('2099-03-01T03:00:00.000Z');
  });

  it('follows daylight saving time (America/New_York)', () => {
    expect(localToIso('2099-01-15T20:00', 'America/New_York')).toBe(
      '2099-01-15T20:00:00-05:00',
    );
    expect(localToIso('2099-07-15T20:00', 'America/New_York')).toBe(
      '2099-07-15T20:00:00-04:00',
    );
  });

  it('picks the right offset right after the spring-forward transition', () => {
    // 2024-03-10 02:00 -> 03:00 in New York; 03:30 is already EDT (-04:00).
    expect(localToIso('2024-03-10T03:30', 'America/New_York')).toBe(
      '2024-03-10T03:30:00-04:00',
    );
    expect(localToIso('2024-03-10T01:30', 'America/New_York')).toBe(
      '2024-03-10T01:30:00-05:00',
    );
  });

  it('rejects malformed or impossible values', () => {
    expect(() => localToIso('2099-02-30T10:00', 'America/Recife')).toThrow(
      RangeError,
    );
    expect(() => localToIso('01/03/2099 17:00', 'America/Recife')).toThrow(
      RangeError,
    );
  });
});

describe('isoToLocal', () => {
  it('shows the wall clock of the city zone', () => {
    expect(isoToLocal('2099-03-01T20:00:00Z', 'America/Recife')).toBe(
      '2099-03-01T17:00',
    );
    expect(isoToLocal('2030-01-01T02:00:00Z', 'America/Recife')).toBe(
      '2029-12-31T23:00',
    );
  });

  it('accepts ISO strings with offsets', () => {
    expect(isoToLocal('2099-03-01T17:00:00-03:00', 'America/Recife')).toBe(
      '2099-03-01T17:00',
    );
  });

  it('round-trips with localToIso, including around DST', () => {
    for (const [local, zone] of [
      ['2099-03-01T17:00', 'America/Recife'],
      ['2099-07-15T20:00', 'America/New_York'],
      ['2099-12-31T23:59', 'Asia/Kolkata'],
    ] as const) {
      expect(isoToLocal(localToIso(local, zone), zone)).toBe(local);
    }
  });
});

describe('isValidLocal', () => {
  it('validates real dates and times only', () => {
    expect(isValidLocal('2099-03-01T17:00')).toBe(true);
    expect(isValidLocal('2099-02-30T17:00')).toBe(false);
    expect(isValidLocal('2099-03-01T24:00')).toBe(false);
    expect(isValidLocal('')).toBe(false);
    expect(isValidLocal('2099-03-01')).toBe(false);
  });
});
