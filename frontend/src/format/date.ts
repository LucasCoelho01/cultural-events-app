export type Occurrence = {
  startsAt: string;
  status: 'SCHEDULED' | 'CANCELLED' | 'SOLD_OUT';
};

/** Formata no fuso IANA da cidade; fuso inválido cai no fuso do navegador. */
export function formatDateTime(iso: string, timeZone?: string): string {
  const options: Intl.DateTimeFormatOptions = {
    dateStyle: 'medium',
    timeStyle: 'short',
  };
  try {
    return new Intl.DateTimeFormat('pt-BR', { ...options, timeZone }).format(
      new Date(iso),
    );
  } catch (error) {
    if (!(error instanceof RangeError)) throw error;
    return new Intl.DateTimeFormat('pt-BR', options).format(new Date(iso));
  }
}

const isFuture = (o: Occurrence, now: Date) =>
  new Date(o.startsAt).getTime() > now.getTime();

const byStart = (a: Occurrence, b: Occurrence) =>
  new Date(a.startsAt).getTime() - new Date(b.startsAt).getTime();

/** Sessões com início posterior a `now`, ordenadas por início. */
export function futureOccurrences<T extends Occurrence>(
  occurrences: T[],
  now: Date,
): T[] {
  return occurrences.filter((o) => isFuture(o, now)).sort(byStart);
}

/** Primeira sessão agendada (`SCHEDULED`) no futuro, se houver. */
export function nextOccurrence<T extends Occurrence>(
  occurrences: T[],
  now: Date,
): T | undefined {
  return futureOccurrences(occurrences, now).find(
    (o) => o.status === 'SCHEDULED',
  );
}
