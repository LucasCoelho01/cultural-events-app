import { useState } from 'react';
import { useSearchParams } from 'react-router';
import { flattenCategories, useCategories } from '../api/categories';
import { useCity } from '../api/cities';
import { useEvents } from '../api/events';
import { customRange, isInvalidRange, periodRange } from '../format/period';
import {
  clearFilters,
  hasFilters,
  parseFilters,
  resolveCategory,
  withFilters,
  type CategoriesState,
  type CatalogFilters as Filters,
} from './filterParams';
import { CatalogFilters } from './CatalogFilters';
import { ErrorMessage } from './ErrorMessage';
import { EventCard } from './EventCard';
import { Pagination } from './Pagination';
import { SearchBox } from './SearchBox';

/** One-based page from `?page=`; missing or invalid values mean the first page. */
export function parsePage(value: string | null): number {
  const page = Number(value);
  return Number.isInteger(page) && page >= 1 ? page : 1;
}

export function EventList({ uf, cidade }: { uf: string; cidade: string }) {
  const [searchParams, setSearchParams] = useSearchParams();
  const page = parsePage(searchParams.get('page'));
  const [now] = useState(() => new Date());
  const [searchReset, setSearchReset] = useState(0);
  const parsed = parseFilters(searchParams);

  const city = useCity(uf, cidade);
  const timeZone = city.data?.timezone;

  const categories = useCategories();
  const categoriesState: CategoriesState =
    categories.status === 'success'
      ? {
          status: 'success',
          slugs: flattenCategories(categories.data).map((c) => c.slug),
        }
      : { status: categories.status === 'error' ? 'error' : 'pending' };
  const { category, ready } = resolveCategory(
    parsed.categoria,
    categoriesState,
  );
  const filters: Filters = { ...parsed, categoria: category };

  // Without the zone of the city a period cannot be computed, so the request waits.
  const needsZone = Boolean(filters.periodo || filters.de || filters.ate);
  const range =
    timeZone && filters.periodo
      ? periodRange(filters.periodo, now, timeZone)
      : timeZone
        ? customRange(filters.de, filters.ate, now, timeZone)
        : null;

  const events = useEvents(uf, cidade, page - 1, {
    q: filters.q,
    category,
    range: range ?? undefined,
    enabled: ready && (!needsZone || Boolean(timeZone)),
  });

  const error = city.error ?? events.error;
  if (error) return <ErrorMessage error={error} />;
  if (!city.data) return <p aria-busy="true">Carregando eventos…</p>;

  const change = (changes: Partial<Filters>) =>
    setSearchParams(withFilters(searchParams, changes));
  const clear = () => {
    setSearchReset((count) => count + 1);
    setSearchParams(clearFilters(searchParams));
  };
  const goTo = (target: number) => {
    const next = new URLSearchParams(searchParams);
    if (target <= 1) next.delete('page');
    else next.set('page', String(target));
    setSearchParams(next);
  };

  const active = hasFilters(filters);
  const filtering = Boolean(filters.q || category || range);
  const items = events.data?.items;
  const emptyWithFilters = items?.length === 0 && filtering;
  const cardRange = range
    ? {
        from: new Date(range.from),
        to: range.to ? new Date(range.to) : undefined,
      }
    : undefined;

  return (
    <section>
      <SearchBox
        key={`${filters.q ?? ''}|${searchReset}`}
        value={filters.q}
        onSearch={(q) => change({ q })}
      />
      <CatalogFilters
        filters={filters}
        categories={categories.data ? flattenCategories(categories.data) : null}
        showClear={active && !emptyWithFilters}
        invalidRange={isInvalidRange(
          filters.de,
          filters.ate,
          now,
          city.data.timezone,
        )}
        onChange={change}
        onClear={clear}
      />
      {!events.data || !items ? (
        <p aria-busy="true">Carregando eventos…</p>
      ) : (
        <>
          {items.length === 0 ? (
            filtering ? (
              <div>
                <p>
                  {filters.q
                    ? `Nenhum evento para "${filters.q}"`
                    : 'Nenhum evento para esses filtros'}
                </p>
                <button
                  type="button"
                  className="mt-2 rounded border border-gray-300 bg-white px-3 py-1"
                  onClick={clear}
                >
                  Limpar filtros
                </button>
              </div>
            ) : (
              <p>Nenhum evento encontrado.</p>
            )
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
                  range={cardRange}
                />
              ))}
            </ul>
          )}
          {(events.data.page.totalPages > 1 || page > 1) && (
            <Pagination
              page={page}
              totalPages={events.data.page.totalPages}
              onPrevious={() => goTo(page - 1)}
              onNext={() => goTo(page + 1)}
            />
          )}
        </>
      )}
    </section>
  );
}
