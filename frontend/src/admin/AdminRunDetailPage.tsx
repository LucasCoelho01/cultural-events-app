import { useState } from 'react';
import { Link, useParams } from 'react-router';
import { useIngestionRun, useUnpublishRun } from '../api/ingestion';
import { ErrorMessage } from '../components/ErrorMessage';
import { formatDateTime } from '../format/date';
import { EVENT_STATUS_LABEL } from './labels';
import { formatDuration, RunStatusBadge } from './runLabels';

const notFound = {
  title: 'Not Found',
  status: 404,
  detail: 'Execução não encontrada.',
};

const plural = (count: number) =>
  count === 1 ? '1 evento' : `${count} eventos`;

export function AdminRunDetailPage() {
  const { id: idParam } = useParams();
  const id = /^\d+$/.test(idParam ?? '') ? Number(idParam) : undefined;
  const run = useIngestionRun(id);
  const unpublish = useUnpublishRun();
  const [confirming, setConfirming] = useState(false);
  const [done, setDone] = useState<number | null>(null);

  if (id === undefined) return <ErrorMessage error={notFound} />;
  if (run.error) return <ErrorMessage error={run.error} />;
  if (!run.data) return <p aria-busy="true">Carregando execução…</p>;

  const detail = run.data;
  const publishedCount = detail.createdEvents.filter(
    (event) => event.status === 'PUBLISHED',
  ).length;
  const running = detail.status === 'RUNNING';
  const disabled = running || publishedCount === 0 || unpublish.isPending;

  const confirm = () =>
    unpublish.mutate(id, {
      onSuccess: (result) => setDone(result.unpublished),
      onSettled: () => setConfirming(false),
    });

  return (
    <section>
      <p className="mb-2 text-sm">
        <Link to="/admin/execucoes" className="text-blue-700 underline">
          ← Execuções do agente
        </Link>
      </p>
      <h1 className="mb-4 text-2xl font-semibold">
        Execução #{detail.id} · {detail.source.name}
      </h1>

      <dl className="mb-6 grid max-w-xl grid-cols-2 gap-x-6 gap-y-1 bg-white p-4">
        <dt>Status</dt>
        <dd>
          <RunStatusBadge status={detail.status} />
        </dd>
        <dt>Início</dt>
        <dd>{formatDateTime(detail.startedAt)}</dd>
        <dt>Fim</dt>
        <dd>
          {detail.finishedAt
            ? formatDateTime(detail.finishedAt)
            : 'Em andamento'}
        </dd>
        <dt>Duração</dt>
        <dd>{formatDuration(detail.durationSeconds)}</dd>
        <dt>Criados</dt>
        <dd>{detail.created}</dd>
        <dt>Atualizados</dt>
        <dd>{detail.updated}</dd>
        <dt>Inalterados</dt>
        <dd>{detail.unchanged}</dd>
        <dt>Com falha</dt>
        <dd>{detail.failed}</dd>
      </dl>

      <h2 className="mb-2 text-xl font-semibold">Erros</h2>
      {detail.errors.length === 0 ? (
        <p className="mb-6">Nenhum erro registrado</p>
      ) : (
        <table className="mb-6 w-full border-collapse bg-white text-left">
          <thead>
            <tr className="border-b border-gray-300">
              <th className="p-2">Código</th>
              <th className="p-2">Item</th>
              <th className="p-2">Detalhe</th>
            </tr>
          </thead>
          <tbody>
            {detail.errors.map((error, index) => (
              <tr key={index} className="border-b border-gray-200">
                <td className="p-2">{error.code ?? '—'}</td>
                <td className="p-2">{error.externalId}</td>
                <td className="p-2">{error.detail}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}

      <div className="mb-2 flex items-center justify-between">
        <h2 className="text-xl font-semibold">Eventos criados</h2>
        {!confirming && (
          <button
            type="button"
            className="rounded bg-red-700 px-4 py-2 text-white disabled:cursor-not-allowed disabled:opacity-50"
            disabled={disabled}
            onClick={() => {
              unpublish.reset();
              setDone(null);
              setConfirming(true);
            }}
          >
            Despublicar eventos desta execução
          </button>
        )}
      </div>

      {confirming && (
        <div
          role="alertdialog"
          aria-label="Confirmar despublicação"
          className="mb-4 flex flex-wrap items-center gap-3 rounded border border-red-300 bg-red-50 p-4"
        >
          <span>
            Despublicar {plural(publishedCount)}? Eles voltam para rascunho e
            nada é excluído.
          </span>
          <button
            type="button"
            autoFocus
            className="rounded border border-gray-300 bg-white px-3 py-1"
            onClick={() => setConfirming(false)}
          >
            Cancelar
          </button>
          <button
            type="button"
            className="rounded bg-red-700 px-3 py-1 text-white disabled:opacity-50"
            disabled={unpublish.isPending}
            onClick={confirm}
          >
            Confirmar
          </button>
        </div>
      )}

      {unpublish.isError && (
        <div className="mb-4">
          <ErrorMessage error={unpublish.error} />
        </div>
      )}
      {done !== null && (
        <p role="status" className="mb-4 text-green-800">
          {plural(done)} despublicado{done === 1 ? '' : 's'}.
        </p>
      )}

      {detail.createdEvents.length === 0 ? (
        <p>Nenhum evento criado nesta execução</p>
      ) : (
        <table className="w-full border-collapse bg-white text-left">
          <thead>
            <tr className="border-b border-gray-300">
              <th className="p-2">Título</th>
              <th className="p-2">Status</th>
              <th className="p-2">Ações</th>
            </tr>
          </thead>
          <tbody>
            {detail.createdEvents.map((event) => (
              <tr key={event.id} className="border-b border-gray-200">
                <td className="p-2">{event.title}</td>
                <td className="p-2">{EVENT_STATUS_LABEL[event.status]}</td>
                <td className="p-2">
                  <Link
                    to={`/admin/eventos/${event.id}/editar`}
                    aria-label={`Editar ${event.title}`}
                    className="text-blue-700 underline"
                  >
                    Editar
                  </Link>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </section>
  );
}
