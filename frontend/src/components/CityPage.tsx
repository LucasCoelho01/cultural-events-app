import { useParams } from 'react-router';
import { useCity } from '../api/cities';
import { ErrorMessage } from './ErrorMessage';
import { EventList } from './EventList';

export function CityPage() {
  const { uf, cidade } = useParams();
  const { data, error, isError } = useCity(uf, cidade);
  if (isError) return <ErrorMessage error={error} />;
  if (!data || !uf || !cidade) return <p aria-busy="true">Carregando…</p>;
  return (
    <div>
      <h1 className="mb-4 text-2xl font-semibold">Eventos em {data.name}</h1>
      <EventList uf={uf} cidade={cidade} />
    </div>
  );
}
