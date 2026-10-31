import { Link, useSearchParams } from 'react-router';
import { useIngestionRuns, useIngestionSources } from '../api/ingestion';
import { ErrorMessage } from '../components/ErrorMessage';
import { parsePage } from '../components/EventList';
import { Pagination } from '../components/Pagination';
import { formatDateTime } from '../format/date';
import {
  formatDuration,
  isRunStatus,
  RUN_STATUS_LABEL,
  RUN_STATUSES,
  RunStatusBadge,
} from './runLabels';

const field = 'rounded border border-gray-300 bg-white px-2 py-1';

export function AdminRunsPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const page = parsePage(searchParams.get('page'));
  const source = searchParams.get('fonte') || undefined;
  const statusParam = searchParams.get('status');
  const status = isRunStatus(statusParam) ? statusParam : undefined;

  const runs = useIngestionRuns(page - 1, { source, status });
  const sources = useIngestionSources();

  /** Applies filter changes and goes back to page 1. */
  const setFilters = (next: { fonte?: string; status?: string }) => {
    const params: Record<string, string> = {};
    const fonte = 'fonte' in next ? next.fonte : source;
    const nextStatus = 'status' in next ? next.status : status;
    if (fonte) params.fonte = fonte;
    if (nextStatus) params.status = nextStatus;
    setSearchParams(params);
  };
  const goTo = (target: number) => {
    const params = new URLSearchParams(searchParams);
    if (target <= 1) params.delete('page');
    else params.set('page', String(target));
    setSearchParams(params);
  };

  if (runs.error) return <ErrorMessage error={runs.error} />;
  if (!runs.data) return <p aria-busy="true">Carregando execuções…</p>;

  const { items, page: metadata } = runs.data;
  const showPagination = metadata.totalPages > 1 || page > 1;

  return (
    <section>
      <h1 className="mb-4 text-2xl font-semibold">Execuções do agente</h1>

      <form
        aria-label="Filtros"
        className="mb-6 flex flex-wrap items-end gap-4"
        onSubmit={(event) => event.preventDefault()}
      >
        {sources.data && (
          <label className="flex flex-col gap-1 text-sm">
            Fonte
            <select
              className={field}
              value={source ?? ''}
              onChange={(event) => setFilters({ fonte: event.target.value })}
            >
              <option value="">Todas</option>
              {sources.data.map((item) => (
                <option key={item.code} value={item.code}>
                  {item.name}
                </option>
              ))}
            </select>
          </label>
        )}
        <label className="flex flex-col gap-1 text-sm">
          Status
          <select
            className={field}
            value={status ?? ''}
            onChange={(event) => setFilters({ status: event.target.value })}
          >
            <option value="">Todos</option>
            {RUN_STATUSES.map((value) => (
              <option key={value} value={value}>
                {RUN_STATUS_LABEL[value]}
              </option>
            ))}
          </select>
        </label>
      </form>

      {items.length === 0 ? (
        <p>Nenhuma execução encontrada.</p>
      ) : (
        <table className="w-full border-collapse bg-white text-left">
          <thead>
            <tr className="border-b border-gray-300">
              <th className="p-2">Fonte</th>
              <th className="p-2">Início</th>
              <th className="p-2">Duração</th>
              <th className="p-2">Status</th>
              <th className="p-2">Criados</th>
              <th className="p-2">Atualizados</th>
              <th className="p-2">Inalterados</th>
              <th className="p-2">Com falha</th>
              <th className="p-2">Ações</th>
            </tr>
          </thead>
          <tbody>
            {items.map((run) => (
              <tr key={run.id} className="border-b border-gray-200">
                <td className="p-2">{run.source.name}</td>
                <td className="p-2">{formatDateTime(run.startedAt)}</td>
                <td className="p-2">{formatDuration(run.durationSeconds)}</td>
                <td className="p-2">
                  <RunStatusBadge status={run.status} />
                </td>
                <td className="p-2">{run.created}</td>
                <td className="p-2">{run.updated}</td>
                <td className="p-2">{run.unchanged}</td>
                <td className="p-2">{run.failed}</td>
                <td className="p-2">
                  <Link
                    to={`/admin/execucoes/${run.id}`}
                    aria-label={`Ver execução ${run.id}`}
                    className="text-blue-700 underline"
                  >
                    Detalhes
                  </Link>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
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
