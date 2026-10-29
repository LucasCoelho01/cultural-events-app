import { describe, expect, it } from 'vitest';
import { customRange, isInvalidRange, nowIso, periodRange } from './period';

const ZONE = 'America/Recife'; // UTC-3, no daylight saving
// 2099-03-10 is a Tuesday.
const at = (local: string) => new Date(`${local}:00-03:00`);

describe('periodRange', () => {
  it('"hoje" runs from now to the end of the day', () => {
    expect(periodRange('hoje', at('2099-03-10T15:00'), ZONE)).toEqual({
      from: '2099-03-10T15:00:00-03:00',
      to: '2099-03-10T23:59:00-03:00',
    });
  });

  it('uses the city day when UTC is already the next day', () => {
    // 22:30 in Recife is 01:30 of the 11th in UTC.
    expect(periodRange('hoje', at('2099-03-10T22:30'), ZONE)).toEqual({
      from: '2099-03-10T22:30:00-03:00',
      to: '2099-03-10T23:59:00-03:00',
    });
  });

  it('uses the city day when UTC is still the previous day', () => {
    // 2099-03-11T01:30Z is already the 11th in Auckland (UTC+13) but the 10th in Recife.
    const instant = new Date('2099-03-11T01:30:00Z');
    expect(periodRange('hoje', instant, 'Pacific/Auckland').to).toBe(
      '2099-03-11T23:59:00+13:00',
    );
    expect(periodRange('hoje', instant, ZONE).to).toBe(
      '2099-03-10T23:59:00-03:00',
    );
  });

  it('"7-dias" ends on the sixth day after today', () => {
    expect(periodRange('7-dias', at('2099-03-10T09:00'), ZONE)).toEqual({
      from: '2099-03-10T09:00:00-03:00',
      to: '2099-03-16T23:59:00-03:00',
    });
  });

  it('"7-dias" crosses a month boundary', () => {
    expect(periodRange('7-dias', at('2099-03-29T09:00'), ZONE).to).toBe(
      '2099-04-04T23:59:00-03:00',
    );
  });

  it('"fim-de-semana" on a Wednesday is the coming Saturday and Sunday', () => {
    expect(periodRange('fim-de-semana', at('2099-03-11T10:00'), ZONE)).toEqual({
      from: '2099-03-14T00:00:00-03:00',
      to: '2099-03-15T23:59:00-03:00',
    });
  });

  it('"fim-de-semana" on a Friday night still points to the next day', () => {
    expect(periodRange('fim-de-semana', at('2099-03-13T23:00'), ZONE)).toEqual({
      from: '2099-03-14T00:00:00-03:00',
      to: '2099-03-15T23:59:00-03:00',
    });
  });

  it('"fim-de-semana" on a Saturday starts now', () => {
    expect(periodRange('fim-de-semana', at('2099-03-14T11:30'), ZONE)).toEqual({
      from: '2099-03-14T11:30:00-03:00',
      to: '2099-03-15T23:59:00-03:00',
    });
  });

  it('"fim-de-semana" on a Sunday is the weekend in progress', () => {
    expect(periodRange('fim-de-semana', at('2099-03-15T18:00'), ZONE)).toEqual({
      from: '2099-03-15T18:00:00-03:00',
      to: '2099-03-15T23:59:00-03:00',
    });
  });
});

describe('customRange', () => {
  const now = at('2099-03-10T15:00');

  it('spans from the start of `de` to the end of `ate`', () => {
    expect(customRange('2099-03-12', '2099-03-14', now, ZONE)).toEqual({
      from: '2099-03-12T00:00:00-03:00',
      to: '2099-03-14T23:59:00-03:00',
    });
  });

  it('accepts only `de`', () => {
    expect(customRange('2099-03-12', undefined, now, ZONE)).toEqual({
      from: '2099-03-12T00:00:00-03:00',
    });
  });

  it('accepts only `ate`, starting now', () => {
    expect(customRange(undefined, '2099-03-14', now, ZONE)).toEqual({
      from: '2099-03-10T15:00:00-03:00',
      to: '2099-03-14T23:59:00-03:00',
    });
  });

  it('clamps a past `de` to now', () => {
    expect(customRange('2099-03-01', '2099-03-14', now, ZONE)?.from).toBe(
      '2099-03-10T15:00:00-03:00',
    );
  });

  it('returns null when `de` is after `ate`', () => {
    expect(customRange('2099-03-14', '2099-03-12', now, ZONE)).toBeNull();
    expect(isInvalidRange('2099-03-14', '2099-03-12', now, ZONE)).toBe(true);
  });

  it('returns null when `ate` is before today in the zone of the city', () => {
    expect(customRange(undefined, '2099-03-09', now, ZONE)).toBeNull();
    expect(customRange('2099-03-01', '2099-03-09', now, ZONE)).toBeNull();
    expect(isInvalidRange(undefined, '2099-03-09', now, ZONE)).toBe(true);
  });

  it('accepts `ate` equal to today in the zone, not in UTC', () => {
    // 22:30 of the 10th in Recife is already the 11th in UTC.
    const lateEvening = at('2099-03-10T22:30');
    expect(customRange(undefined, '2099-03-10', lateEvening, ZONE)).toEqual({
      from: '2099-03-10T22:30:00-03:00',
      to: '2099-03-10T23:59:00-03:00',
    });
    expect(isInvalidRange(undefined, '2099-03-10', lateEvening, ZONE)).toBe(
      false,
    );
  });

  it('returns null without dates', () => {
    expect(customRange(undefined, undefined, now, ZONE)).toBeNull();
    expect(isInvalidRange('2099-03-12', undefined, now, ZONE)).toBe(false);
  });
});

describe('nowIso', () => {
  it('rounds down to the minute and carries the zone offset', () => {
    expect(nowIso(new Date('2099-03-10T18:00:45Z'), ZONE)).toBe(
      '2099-03-10T15:00:00-03:00',
    );
  });
});
