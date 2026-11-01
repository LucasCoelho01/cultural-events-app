import { useState } from 'react';
import { Link, useLocation, useParams } from 'react-router';
import { useCity } from '../api/cities';
import { usePublishedEvent } from '../api/events';
import { useVenue } from '../api/venues';
import { formatDateTime, futureOccurrences } from '../format/date';
import { formatPrice } from '../format/price';
import { safeHttpUrl } from '../format/url';
import { ErrorMessage } from './ErrorMessage';
import { categoryChipClass } from './categoryChip';
import { EventImage } from './EventImage';

const STATUS_LABEL = {
  SCHEDULED: null,
  CANCELLED: 'Cancelada',
  SOLD_OUT: 'Esgotada',
} as const;

const block = 'rounded-2xl border border-gray-200 bg-white p-5 shadow-sm';

/** Origin list URL from the link state; only internal paths are accepted. */
function originOf(state: unknown): string | null {
  const from = (state as { from?: unknown } | null)?.from;
  return typeof from === 'string' &&
    from.startsWith('/') &&
    !from.startsWith('//')
    ? from
    : null;
}

function Venue({ id, name }: { id: string; name: string }) {
  // The address is a bonus: if it fails to load, only the name is shown.
  const { data } = useVenue(id);
  const address = [data?.addressLine, data?.neighborhood]
    .filter(Boolean)
    .join(' – ');
  return (
    <section className={block}>
      <h2 className="font-semibold">Local</h2>
      <p>{name}</p>
      {address && <p className="text-gray-600">{address}</p>}
    </section>
  );
}

export function EventPage() {
  const { uf, cidade, slug } = useParams();
  const [now] = useState(() => new Date());
  const location = useLocation();
  const city = useCity(uf, cidade);
  const { data: event, error, isError } = usePublishedEvent(uf, cidade, slug);

  if (isError) return <ErrorMessage error={error} />;
  if (!event) return <p aria-busy="true">Carregando…</p>;

  const price = formatPrice(event);
  const ticketUrl = safeHttpUrl(event.ticketUrl);
  const sessions = futureOccurrences(event.occurrences, now);

  const back = originOf(location.state) ?? `/${uf}/${cidade}`;

  return (
    <article className="space-y-4">
      <Link
        to={back}
        className="inline-block rounded font-medium text-brand-purple-dark underline-offset-2 hover:underline"
      >
        ← Voltar para os eventos
      </Link>
      <EventImage
        src={event.imageUrl}
        title={event.title}
        className="rounded-2xl shadow-sm"
      />
      <div className={block}>
        <span
          className={`inline-block rounded-full px-3 py-0.5 text-xs font-semibold ${categoryChipClass(event.category.slug)}`}
        >
          {event.category.name}
        </span>
        <h1 className="mt-2 text-3xl font-bold">{event.title}</h1>
        {event.description && <p className="mt-3">{event.description}</p>}
        {event.ageRating && (
          <p className="mt-3 text-sm text-gray-700">
            Classificação: {event.ageRating}
          </p>
        )}
        {price && <p className="mt-3 text-xl font-bold">{price}</p>}
      </div>

      <section className={block}>
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
                  <span className="ml-2 text-sm font-medium text-red-700">
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
          className="inline-block rounded-full bg-brand-purple-dark px-6 py-3 font-semibold text-white hover:bg-brand-purple"
        >
          Comprar ingressos
        </a>
      )}
    </article>
  );
}
