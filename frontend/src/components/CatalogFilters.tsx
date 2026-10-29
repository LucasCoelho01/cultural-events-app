import type { CategoryOption } from '../api/categories';
import { PERIOD_PRESETS } from '../format/period';
import type { PeriodPreset } from '../format/period';
import type { CatalogFilters as Filters } from './filterParams';

const PRESET_LABELS: Record<PeriodPreset, string> = {
  hoje: 'Hoje',
  'fim-de-semana': 'Este fim de semana',
  '7-dias': 'Próximos 7 dias',
};

const field = 'rounded border border-gray-300 bg-white px-2 py-1';
const button =
  'rounded border border-gray-300 bg-white px-3 py-1 aria-pressed:border-gray-900 aria-pressed:bg-gray-900 aria-pressed:text-white';

type CatalogFiltersProps = {
  filters: Filters;
  /** Flattened category tree; `null` hides the select (not loaded or failed). */
  categories: CategoryOption[] | null;
  showClear: boolean;
  /** The custom range is inverted or already over; it is ignored and a message is shown. */
  invalidRange: boolean;
  onChange: (changes: Partial<Filters>) => void;
  onClear: () => void;
};

export function CatalogFilters({
  filters,
  categories,
  showClear,
  invalidRange,
  onChange,
  onClear,
}: CatalogFiltersProps) {
  return (
    <form
      aria-label="Filtros"
      className="mb-6 flex flex-wrap items-end gap-4"
      onSubmit={(event) => event.preventDefault()}
    >
      {categories && (
        <label className="flex flex-col gap-1 text-sm">
          Categoria
          <select
            className={field}
            value={filters.categoria ?? ''}
            onChange={(event) =>
              onChange({ categoria: event.target.value || undefined })
            }
          >
            <option value="">Todas</option>
            {categories.map((category) => (
              <option key={category.id} value={category.slug}>
                {'  '.repeat(category.depth)}
                {category.name}
              </option>
            ))}
          </select>
        </label>
      )}

      <fieldset className="flex flex-col gap-1 text-sm">
        <legend className="mb-1">Período</legend>
        <div className="flex flex-wrap gap-2">
          {PERIOD_PRESETS.map((preset) => (
            <button
              key={preset}
              type="button"
              className={button}
              aria-pressed={filters.periodo === preset}
              onClick={() =>
                onChange({
                  periodo: filters.periodo === preset ? undefined : preset,
                })
              }
            >
              {PRESET_LABELS[preset]}
            </button>
          ))}
        </div>
      </fieldset>

      <label className="flex flex-col gap-1 text-sm">
        De
        <input
          type="date"
          className={field}
          value={filters.de ?? ''}
          onChange={(event) =>
            onChange({ de: event.target.value || undefined })
          }
        />
      </label>
      <label className="flex flex-col gap-1 text-sm">
        Até
        <input
          type="date"
          className={field}
          value={filters.ate ?? ''}
          onChange={(event) =>
            onChange({ ate: event.target.value || undefined })
          }
        />
      </label>

      {showClear && (
        <button type="button" className={button} onClick={onClear}>
          Limpar filtros
        </button>
      )}

      {invalidRange && (
        <p role="alert" className="w-full text-sm text-red-700">
          A data inicial deve ser anterior à final
        </p>
      )}
    </form>
  );
}
