const LOCAL_PATTERN = /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})$/;

/** Offset in ms of `timeZone` at the instant `utcMs` (wall clock minus UTC). */
function offsetAt(utcMs: number, timeZone: string): number {
  const parts = new Intl.DateTimeFormat('en-US', {
    timeZone,
    hourCycle: 'h23',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
  }).formatToParts(new Date(utcMs));
  const get = (type: string) =>
    Number(parts.find((part) => part.type === type)?.value);
  const wall = Date.UTC(
    get('year'),
    get('month') - 1,
    get('day'),
    get('hour'),
    get('minute'),
    get('second'),
  );
  return wall - Math.floor(utcMs / 1000) * 1000;
}

/** True when `local` is a real "YYYY-MM-DDTHH:mm" date and time. */
export function isValidLocal(local: string): boolean {
  const match = LOCAL_PATTERN.exec(local);
  if (!match) return false;
  const [year, month, day, hour, minute] = match.slice(1).map(Number);
  const date = new Date(Date.UTC(year, month - 1, day, hour, minute));
  return (
    date.getUTCFullYear() === year &&
    date.getUTCMonth() === month - 1 &&
    date.getUTCDate() === day &&
    date.getUTCHours() === hour &&
    date.getUTCMinutes() === minute
  );
}

const pad = (value: number) => String(value).padStart(2, '0');

/**
 * Wall-clock time typed in `timeZone` -> ISO 8601 with offset
 * ("2099-03-01T17:00" in America/Recife -> "2099-03-01T17:00:00-03:00").
 * Two passes find the offset in force at the target instant, which stays correct
 * across daylight-saving transitions.
 */
export function localToIso(local: string, timeZone: string): string {
  const match = LOCAL_PATTERN.exec(local);
  if (!match || !isValidLocal(local)) {
    throw new RangeError(`Invalid local date-time: ${local}`);
  }
  const [year, month, day, hour, minute] = match.slice(1).map(Number);
  const asUtc = Date.UTC(year, month - 1, day, hour, minute);
  const firstGuess = asUtc - offsetAt(asUtc, timeZone);
  const offset = offsetAt(firstGuess, timeZone);
  const minutes = Math.round(offset / 60000);
  const sign = minutes < 0 ? '-' : '+';
  const abs = Math.abs(minutes);
  return `${local}:00${sign}${pad(Math.floor(abs / 60))}:${pad(abs % 60)}`;
}

/** Instant -> wall-clock "YYYY-MM-DDTHH:mm" in `timeZone` (for datetime-local inputs). */
export function isoToLocal(iso: string, timeZone: string): string {
  const instant = new Date(iso).getTime();
  const wall = new Date(instant + offsetAt(instant, timeZone));
  return (
    `${wall.getUTCFullYear()}-${pad(wall.getUTCMonth() + 1)}-${pad(wall.getUTCDate())}` +
    `T${pad(wall.getUTCHours())}:${pad(wall.getUTCMinutes())}`
  );
}
