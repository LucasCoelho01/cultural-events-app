import { Link, Outlet, useParams } from 'react-router';
import { useCity, useDefaultCity } from '../api/cities';
import { ErrorMessage } from './ErrorMessage';

export const APP_NAME = 'Cultural Events';

function CityName() {
  const { uf, cidade } = useParams();
  const routed = useCity(uf, cidade);
  const fallback = useDefaultCity();
  const query = uf && cidade ? routed : fallback;

  if (query.isError) return <ErrorMessage error={query.error} />;
  if (!query.data) return <span aria-busy="true">…</span>;
  return (
    <span>
      {query.data.name} - {query.data.uf}
    </span>
  );
}

export function Layout() {
  const { uf, cidade } = useParams();
  const home = uf && cidade ? `/${uf}/${cidade}` : '/';
  return (
    <div className="min-h-screen bg-gray-50 font-sans text-gray-900">
      <header className="bg-white shadow">
        <div
          aria-hidden="true"
          className="h-1.5 bg-gradient-to-r from-brand-purple via-brand-pink to-brand-orange"
        />
        <div className="mx-auto flex max-w-6xl flex-wrap items-center justify-between gap-3 px-6 py-3">
          <Link to={home} className="rounded">
            <img src="/logo.svg" alt={APP_NAME} className="h-10 w-auto" />
          </Link>
          <div className="flex flex-wrap items-center gap-4 text-sm">
            <span className="font-medium text-brand-purple-dark">
              <CityName />
            </span>
            <Link
              to="/admin/eventos"
              className="rounded text-gray-700 underline-offset-2 hover:underline"
            >
              Admin
            </Link>
            <Link
              to="/admin/execucoes"
              className="rounded text-gray-700 underline-offset-2 hover:underline"
            >
              Execuções do agente
            </Link>
          </div>
        </div>
      </header>
      <main className="mx-auto max-w-6xl p-6">
        <Outlet />
      </main>
    </div>
  );
}
