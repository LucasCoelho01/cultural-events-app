import { useState } from 'react';
import { useSearchParams } from 'react-router';
import { useCity } from '../api/cities';
import { useEvents } from '../api/events';
import { ErrorMessage } from './ErrorMessage';
import { EventCard } from './EventCard';
import { Pagination } from './Pagination';

/** One-based page from `?page=`; missing or invalid values mean the first page. */
export function parsePage(value: string | null): number {
  const page = Number(value);
  return Number.isInteger(page) && page >= 1 ? page : 1;
}

export function EventList({ uf, cidade }: { uf: string; cidade: string }) {
  const [searchParams, setSearchParams] = useSearchParams();
  const page = parsePage(searchParams.get('page'));
  const [now] = useState(() => new Date());

  const city = useCity(uf, cidade);
  const events = useEvents(uf, cidade, page - 1);

  const error = city.error ?? events.error;
  if (error) return <ErrorMessage error={error} />;
  if (!city.data || !events.data) {
    return <p aria-busy="true">Carregando eventos…</p>;
  }

  const goTo = (target: number) =>
    setSearchParams(target <= 1 ? {} : { page: String(target) });
  const { items, page: metadata } = events.data;
  const showPagination = metadata.totalPages > 1 || page > 1;

  return (
    <section>
      {items.length === 0 ? (
        <p>Nenhum evento encontrado.</p>
      ) : (
        <ul className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {items.map((event) => (
            <EventCard
              key={event.id}
              event={event}
              uf={uf}
              cidade={cidade}
              timeZone={city.data.timezone}
              now={now}
            />
          ))}
        </ul>
      )}
      {showPagination && (
        <Pagination
          page={page}
          totalPages={metadata.totalPages}
          onPrevious={() => goTo(page - 1)}
          onNext={() => goTo(page + 1)}
        />
      )}
    </section>
  );
}
