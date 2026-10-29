import { Link } from 'react-router';
import type { Event } from '../api/events';
import { formatDateTime, nextOccurrence } from '../format/date';
import { formatPrice } from '../format/price';

type EventCardProps = {
  event: Event;
  uf: string;
  cidade: string;
  timeZone: string;
  now: Date;
  /** Active period: the card shows the first session inside it. */
  range?: { from: Date; to?: Date };
};

export function EventCard({
  event,
  uf,
  cidade,
  timeZone,
  now,
  range,
}: EventCardProps) {
  const next = nextOccurrence(event.occurrences, now, range);
  const price = formatPrice(event);
  return (
    <li>
      <Link
        to={`/${uf}/${cidade}/eventos/${event.slug}`}
        className="block rounded border border-gray-200 bg-white p-4 shadow-sm hover:shadow"
      >
        <h2 className="text-lg font-semibold">{event.title}</h2>
        <p className="text-sm text-gray-600">{event.category.name}</p>
        {event.venue && <p className="text-sm">{event.venue.name}</p>}
        <p className="mt-2 text-sm">
          {next
            ? formatDateTime(next.startsAt, timeZone)
            : range
              ? 'Sem sessões no período'
              : 'Sem sessões futuras'}
        </p>
        {price && <p className="text-sm font-medium">{price}</p>}
      </Link>
    </li>
  );
}
