import { isoToLocal, localToIso } from './datetime';

export const PERIOD_PRESETS = ['hoje', 'fim-de-semana', '7-dias'] as const;
export type PeriodPreset = (typeof PERIOD_PRESETS)[number];

/** `from`/`to` as ISO 8601 with offset; `to` is omitted for open-ended ranges. */
export type DateRange = { from: string; to?: string };

const DAY_MS = 24 * 60 * 60 * 1000;

/** "YYYY-MM-DD" moved by `days`, using UTC arithmetic so the browser zone never matters. */
function addDays(day: string, days: number): string {
  const [year, month, date] = day.split('-').map(Number);
  const moved = new Date(Date.UTC(year, month - 1, date) + days * DAY_MS);
  return moved.toISOString().slice(0, 10);
}

/** Day of week of a "YYYY-MM-DD" date: 0 = Sunday ... 6 = Saturday. */
function dayOfWeek(day: string): number {
  const [year, month, date] = day.split('-').map(Number);
  return new Date(Date.UTC(year, month - 1, date)).getUTCDay();
}

/** The current instant, rounded down to the minute, as ISO 8601 with the zone offset. */
export function nowIso(now: Date, timeZone: string): string {
  return localToIso(isoToLocal(now.toISOString(), timeZone), timeZone);
}

/**
 * Range from the start of `firstDay` to the end of `lastDay` (both "YYYY-MM-DD" in
 * `timeZone`). `from` is never earlier than `now`, so past sessions are left out.
 * `to` ends at 23:59 because `localToIso` works in minutes.
 */
function dayRange(
  firstDay: string | undefined,
  lastDay: string | undefined,
  now: Date,
  timeZone: string,
): DateRange {
  const current = nowIso(now, timeZone);
  const start = firstDay ? localToIso(`${firstDay}T00:00`, timeZone) : current;
  return {
    from: Date.parse(start) > Date.parse(current) ? start : current,
    ...(lastDay && { to: localToIso(`${lastDay}T23:59`, timeZone) }),
  };
}

/** Range of a shortcut, computed in the zone of the city. */
export function periodRange(
  preset: PeriodPreset,
  now: Date,
  timeZone: string,
): DateRange {
  const today = isoToLocal(now.toISOString(), timeZone).slice(0, 10);
  switch (preset) {
    case 'hoje':
      return dayRange(today, today, now, timeZone);
    case '7-dias':
      return dayRange(today, addDays(today, 6), now, timeZone);
    case 'fim-de-semana': {
      const weekday = dayOfWeek(today);
      const saturday =
        weekday === 0 ? addDays(today, -1) : addDays(today, 6 - weekday);
      return dayRange(saturday, addDays(saturday, 1), now, timeZone);
    }
  }
}

/**
 * True when the custom range cannot match anything: `de` is after `ate`, or `ate`
 * is before today in `timeZone`. Dates are "YYYY-MM-DD"; either may be missing.
 */
export function isInvalidRange(
  de: string | undefined,
  ate: string | undefined,
  now: Date,
  timeZone: string,
): boolean {
  const today = isoToLocal(now.toISOString(), timeZone).slice(0, 10);
  return Boolean((de && ate && de > ate) || (ate && ate < today));
}

/**
 * Range between two dates ("YYYY-MM-DD"); either may be missing.
 * Returns `null` when neither is given or when the range is invalid (`isInvalidRange`).
 */
export function customRange(
  de: string | undefined,
  ate: string | undefined,
  now: Date,
  timeZone: string,
): DateRange | null {
  if ((!de && !ate) || isInvalidRange(de, ate, now, timeZone)) return null;
  return dayRange(de, ate, now, timeZone);
}
