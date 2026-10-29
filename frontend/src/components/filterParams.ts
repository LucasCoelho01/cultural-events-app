import { isValidLocal } from '../format/datetime';
import { PERIOD_PRESETS, type PeriodPreset } from '../format/period';

export type CatalogFilters = {
  categoria?: string;
  periodo?: PeriodPreset;
  de?: string;
  ate?: string;
};

export const FILTER_KEYS = ['categoria', 'periodo', 'de', 'ate'] as const;
type FilterKey = (typeof FILTER_KEYS)[number];

const isDay = (value: string | null): value is string =>
  value !== null && isValidLocal(`${value}T00:00`);

/** Filters from the query string; invalid values are dropped, `periodo` beats `de`/`ate`. */
export function parseFilters(params: URLSearchParams): CatalogFilters {
  const categoria = params.get('categoria')?.trim() || undefined;
  const periodo = params.get('periodo');
  if (PERIOD_PRESETS.includes(periodo as PeriodPreset)) {
    return { categoria, periodo: periodo as PeriodPreset };
  }
  const de = params.get('de');
  const ate = params.get('ate');
  return {
    categoria,
    de: isDay(de) ? de : undefined,
    ate: isDay(ate) ? ate : undefined,
  };
}

export function hasFilters(filters: CatalogFilters): boolean {
  return FILTER_KEYS.some((key) => filters[key] !== undefined);
}

/**
 * Copy of `params` with `changes` applied (`undefined` removes a key). Changing a
 * filter always goes back to the first page, so `page` is dropped. A shortcut and a
 * custom range exclude each other.
 */
export function withFilters(
  params: URLSearchParams,
  changes: Partial<Record<FilterKey, string | undefined>>,
): URLSearchParams {
  const next = new URLSearchParams(params);
  next.delete('page');
  if (changes.periodo) {
    next.delete('de');
    next.delete('ate');
  }
  if (changes.de !== undefined || changes.ate !== undefined) {
    next.delete('periodo');
  }
  for (const [key, value] of Object.entries(changes)) {
    if (value) next.set(key, value);
    else next.delete(key);
  }
  return next;
}

/** Same params without any filter and without `page`. */
export function clearFilters(params: URLSearchParams): URLSearchParams {
  const next = new URLSearchParams(params);
  next.delete('page');
  for (const key of FILTER_KEYS) next.delete(key);
  return next;
}

export type CategoriesState =
  | { status: 'pending' }
  | { status: 'error' }
  | { status: 'success'; slugs: string[] };

/**
 * `GET /events` answers 404 for an unknown category, so the slug from the URL is only
 * sent once `GET /categories` has confirmed it. While categories load the events
 * query must wait (`ready: false`); a missing slug or a failed load drops the filter.
 */
export function resolveCategory(
  slug: string | undefined,
  state: CategoriesState,
): { category?: string; ready: boolean } {
  if (!slug) return { ready: true };
  if (state.status === 'pending') return { ready: false };
  if (state.status === 'error') return { ready: true };
  return state.slugs.includes(slug)
    ? { category: slug, ready: true }
    : { ready: true };
}
