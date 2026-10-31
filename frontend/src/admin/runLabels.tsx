import type { IngestionRunStatus } from '../api/ingestion';

export const RUN_STATUS_LABEL: Record<IngestionRunStatus, string> = {
  RUNNING: 'Em andamento',
  SUCCESS: 'Sucesso',
  PARTIAL: 'Parcial',
  FAILED: 'Falhou',
};

export const RUN_STATUSES = Object.keys(
  RUN_STATUS_LABEL,
) as IngestionRunStatus[];

export function isRunStatus(
  value: string | null | undefined,
): value is IngestionRunStatus {
  return value != null && value in RUN_STATUS_LABEL;
}

const BADGE_COLORS: Record<IngestionRunStatus, string> = {
  SUCCESS: 'bg-green-100 text-green-900',
  PARTIAL: 'bg-yellow-100 text-yellow-900',
  FAILED: 'bg-red-100 text-red-900',
  RUNNING: 'bg-blue-100 text-blue-900',
};

/** Colored badge; the label is always shown so color is never the only cue. */
export function RunStatusBadge({ status }: { status: IngestionRunStatus }) {
  return (
    <span
      data-status={status}
      className={`inline-block rounded px-2 py-0.5 text-sm font-medium ${BADGE_COLORS[status]}`}
    >
      {RUN_STATUS_LABEL[status]}
    </span>
  );
}

/** "45 s", "5 min 30 s", "1 h 2 min"; a running run has no duration yet. */
export function formatDuration(seconds: number | null | undefined): string {
  if (seconds == null) return 'Em andamento';
  if (seconds < 60) return `${seconds} s`;
  const minutes = Math.floor(seconds / 60);
  if (minutes < 60) {
    const rest = seconds % 60;
    return rest === 0 ? `${minutes} min` : `${minutes} min ${rest} s`;
  }
  const hours = Math.floor(minutes / 60);
  const restMinutes = minutes % 60;
  return restMinutes === 0 ? `${hours} h` : `${hours} h ${restMinutes} min`;
}
