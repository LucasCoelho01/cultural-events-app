import { Link, useLocation } from 'react-router';
import type { Event } from '../api/events';
import { formatDateTime, nextOccurrence } from '../format/date';
import { formatPrice } from '../format/price';
import { categoryChipClass } from './categoryChip';
import { EventImage } from './EventImage';

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
  const { pathname, search } = useLocation();
  const next = nextOccurrence(event.occurrences, now, range);
  const price = formatPrice(event);
  return (
    <li>
      <Link
        to={`/${uf}/${cidade}/eventos/${event.slug}`}
        state={{ from: pathname + search }}
        className="block h-full overflow-hidden rounded-2xl border border-gray-200 bg-white shadow-sm transition-shadow hover:shadow-lg"
      >
        <EventImage src={event.imageUrl} title={event.title} />
        <div className="space-y-2 p-4">
          <span
            className={`inline-block rounded-full px-3 py-0.5 text-xs font-semibold ${categoryChipClass(event.category.slug)}`}
          >
            {event.category.name}
          </span>
          <h2 className="text-lg font-semibold">{event.title}</h2>
          {event.venue && (
            <p className="text-sm text-gray-700">{event.venue.name}</p>
          )}
          <p className="text-sm font-semibold text-brand-purple-dark">
            {next
              ? formatDateTime(next.startsAt, timeZone)
              : range
                ? 'Sem sessões no período'
                : 'Sem sessões futuras'}
          </p>
          {price && <p className="text-base font-bold">{price}</p>}
        </div>
      </Link>
    </li>
  );
}
