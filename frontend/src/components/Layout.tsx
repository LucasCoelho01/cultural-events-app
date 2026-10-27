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
  return (
    <div className="min-h-screen bg-gray-50 text-gray-900">
      <header className="flex items-center justify-between bg-white px-6 py-4 shadow">
        <strong>{APP_NAME}</strong>
        <div className="flex items-center gap-4">
          <CityName />
          <Link to="/admin/eventos" className="text-sm text-gray-500">
            Admin
          </Link>
        </div>
      </header>
      <main className="p-6">
        <Outlet />
      </main>
    </div>
  );
}
