import { useState } from 'react';
import { Link, useSearchParams } from 'react-router';
import { useDefaultCity } from '../api/cities';
import { useAdminEvents, useDeleteEvent } from '../api/events';
import { ErrorMessage } from '../components/ErrorMessage';
import { parsePage } from '../components/EventList';
import { Pagination } from '../components/Pagination';
import { formatDateTime, nextOccurrence } from '../format/date';
import { EVENT_STATUS_LABEL } from './labels';

export function AdminEventsPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const page = parsePage(searchParams.get('page'));
  const [now] = useState(() => new Date());
  const [confirmingId, setConfirmingId] = useState<string | null>(null);

  const city = useDefaultCity();
  const events = useAdminEvents(page - 1);
  const remove = useDeleteEvent();

  const error = city.error ?? events.error;
  if (error) return <ErrorMessage error={error} />;
  if (!city.data || !events.data) {
    return <p aria-busy="true">Carregando eventos…</p>;
  }

  const { items, page: metadata } = events.data;
  const goTo = (target: number) =>
    setSearchParams(target <= 1 ? {} : { page: String(target) });
  const showPagination = metadata.totalPages > 1 || page > 1;

  const confirmDelete = (id: string) =>
    remove.mutate(id, { onSettled: () => setConfirmingId(null) });

  return (
    <section>
      <div className="mb-4 flex items-center justify-between">
        <h1 className="text-2xl font-semibold">Eventos</h1>
        <Link
          to="/admin/eventos/novo"
          className="rounded bg-blue-700 px-4 py-2 text-white"
        >
          Novo evento
        </Link>
      </div>

      {remove.isError && (
        <div className="mb-4">
          <ErrorMessage error={remove.error} />
        </div>
      )}

      {items.length === 0 ? (
        <p>Nenhum evento encontrado.</p>
      ) : (
        <table className="w-full border-collapse bg-white text-left">
          <thead>
            <tr className="border-b border-gray-300">
              <th className="p-2">Título</th>
              <th className="p-2">Status</th>
              <th className="p-2">Próxima sessão</th>
              <th className="p-2">Ações</th>
            </tr>
          </thead>
          <tbody>
            {items.map((event) => {
              const next = nextOccurrence(event.occurrences, now);
              return (
                <tr key={event.id} className="border-b border-gray-200">
                  <td className="p-2">{event.title}</td>
                  <td className="p-2">{EVENT_STATUS_LABEL[event.status]}</td>
                  <td className="p-2">
                    {next
                      ? formatDateTime(next.startsAt, city.data.timezone)
                      : 'Sem sessões futuras'}
                  </td>
                  <td className="p-2">
                    {confirmingId === event.id ? (
                      <div
                        role="alertdialog"
                        aria-label={`Confirmar exclusão de ${event.title}`}
                        className="flex items-center gap-2"
                      >
                        <span>Excluir este evento?</span>
                        <button
                          type="button"
                          autoFocus
                          className="rounded border border-gray-300 px-3 py-1"
                          onClick={() => setConfirmingId(null)}
                        >
                          Cancelar
                        </button>
                        <button
                          type="button"
                          className="rounded bg-red-700 px-3 py-1 text-white disabled:opacity-50"
                          disabled={remove.isPending}
                          onClick={() => confirmDelete(event.id)}
                        >
                          Confirmar
                        </button>
                      </div>
                    ) : (
                      <div className="flex gap-3">
                        <Link
                          to={`/admin/eventos/${event.id}/editar`}
                          aria-label={`Editar ${event.title}`}
                          className="text-blue-700 underline"
                        >
                          Editar
                        </Link>
                        <button
                          type="button"
                          aria-label={`Excluir ${event.title}`}
                          className="text-red-700 underline"
                          onClick={() => {
                            remove.reset();
                            setConfirmingId(event.id);
                          }}
                        >
                          Excluir
                        </button>
                      </div>
                    )}
                  </td>
                </tr>
              );
            })}
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
