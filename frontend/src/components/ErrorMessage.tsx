import { isProblem } from '../api/problem';

const GENERIC_MESSAGE =
  'Não foi possível concluir a operação. Tente novamente.';

export function ErrorMessage({ error }: { error: unknown }) {
  const message =
    isProblem(error) && error.detail ? error.detail : GENERIC_MESSAGE;
  return (
    <div
      role="alert"
      className="rounded border border-red-300 bg-red-50 p-4 text-red-800"
    >
      {message}
    </div>
  );
}
