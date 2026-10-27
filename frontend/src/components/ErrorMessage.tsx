import { isProblem } from '../api/problem';

export const GENERIC_MESSAGE =
  'Não foi possível concluir a operação. Tente novamente.';

type ErrorMessageProps = {
  error: unknown;
  /** Extra lines shown under the main message (e.g. errors with no matching form field). */
  details?: string[];
};

export function ErrorMessage({ error, details = [] }: ErrorMessageProps) {
  const message =
    isProblem(error) && error.detail ? error.detail : GENERIC_MESSAGE;
  return (
    <div
      role="alert"
      className="rounded border border-red-300 bg-red-50 p-4 text-red-800"
    >
      {message}
      {details.length > 0 && (
        <ul className="mt-2 list-disc pl-5">
          {details.map((line) => (
            <li key={line}>{line}</li>
          ))}
        </ul>
      )}
    </div>
  );
}
