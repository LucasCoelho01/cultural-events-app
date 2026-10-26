import { Navigate } from 'react-router';
import { useDefaultCity } from '../api/cities';
import { ErrorMessage } from './ErrorMessage';

export function RedirectToDefaultCity() {
  const { data, error, isError } = useDefaultCity();
  if (isError) return <ErrorMessage error={error} />;
  if (!data) return <p aria-busy="true">Carregando…</p>;
  return <Navigate replace to={`/${data.uf.toLowerCase()}/${data.slug}`} />;
}
