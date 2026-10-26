import { useParams } from 'react-router';
import { useCity } from '../api/cities';
import { ErrorMessage } from './ErrorMessage';

export function CityPage() {
  const { uf, cidade } = useParams();
  const { data, error, isError } = useCity(uf, cidade);
  if (isError) return <ErrorMessage error={error} />;
  if (!data) return <p aria-busy="true">Carregando…</p>;
  return <h1 className="text-2xl font-semibold">Eventos em {data.name}</h1>;
}
