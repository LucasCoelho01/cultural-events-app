import { useState } from 'react';
import { useParams } from 'react-router';
import { useCity } from '../api/cities';
import { usePublishedEvent } from '../api/events';
import { useVenue } from '../api/venues';
import { formatDateTime, futureOccurrences } from '../format/date';
import { formatPrice } from '../format/price';
import { safeHttpUrl } from '../format/url';
import { ErrorMessage } from './ErrorMessage';

const STATUS_LABEL = {
  SCHEDULED: null,
  CANCELLED: 'Cancelada',
  SOLD_OUT: 'Esgotada',
} as const;

function Venue({ id, name }: { id: string; name: string }) {
  // The address is a bonus: if it fails to load, only the name is shown.
  const { data } = useVenue(id);
  const address = [data?.addressLine, data?.neighborhood]
    .filter(Boolean)
    .join(' – ');
  return (
    <section>
      <h2 className="font-semibold">Local</h2>
      <p>{name}</p>
      {address && <p className="text-gray-600">{address}</p>}
    </section>
  );
}

export function EventPage() {
  const { uf, cidade, slug } = useParams();
  const [now] = useState(() => new Date());
  const city = useCity(uf, cidade);
  const { data: event, error, isError } = usePublishedEvent(uf, cidade, slug);

  if (isError) return <ErrorMessage error={error} />;
  if (!event) return <p aria-busy="true">Carregando…</p>;

  const price = formatPrice(event);
  const ticketUrl = safeHttpUrl(event.ticketUrl);
  const sessions = futureOccurrences(event.occurrences, now);

  return (
    <article className="space-y-4">
      <h1 className="text-2xl font-semibold">{event.title}</h1>
      <p className="text-gray-600">{event.category.name}</p>
      {event.description && <p>{event.description}</p>}
      {event.ageRating && <p>Classificação: {event.ageRating}</p>}
      {price && <p className="font-medium">{price}</p>}

      <section>
        <h2 className="font-semibold">Sessões</h2>
        {city.isError ? (
          <ErrorMessage error={city.error} />
        ) : !city.data ? (
          <p aria-busy="true">Carregando sessões…</p>
        ) : sessions.length === 0 ? (
          <p>Sem sessões futuras</p>
        ) : (
          <ul>
            {sessions.map((session) => (
              <li key={session.startsAt}>
                {formatDateTime(session.startsAt, city.data.timezone)}
                {STATUS_LABEL[session.status] && (
                  <span className="ml-2 text-sm text-red-700">
                    {STATUS_LABEL[session.status]}
                  </span>
                )}
              </li>
            ))}
          </ul>
        )}
      </section>

      {event.venue && <Venue id={event.venue.id} name={event.venue.name} />}

      {ticketUrl && (
        <a
          href={ticketUrl}
          target="_blank"
          rel="noopener noreferrer"
          className="inline-block rounded bg-blue-700 px-4 py-2 text-white"
        >
          Comprar ingressos
        </a>
      )}
    </article>
  );
}
