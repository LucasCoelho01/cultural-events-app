type PaginationProps = {
  /** One-based current page, as shown in the URL. */
  page: number;
  totalPages: number;
  onPrevious: () => void;
  onNext: () => void;
};

const button =
  'rounded border border-gray-300 bg-white px-4 py-2 disabled:cursor-not-allowed disabled:opacity-50';

export function Pagination({
  page,
  totalPages,
  onPrevious,
  onNext,
}: PaginationProps) {
  return (
    <nav aria-label="Paginação" className="mt-6 flex items-center gap-4">
      <button
        type="button"
        className={button}
        disabled={page <= 1}
        onClick={onPrevious}
      >
        Anterior
      </button>
      <span>
        Página {page} de {Math.max(totalPages, 1)}
      </span>
      <button
        type="button"
        className={button}
        disabled={page >= totalPages}
        onClick={onNext}
      >
        Próxima
      </button>
    </nav>
  );
}
