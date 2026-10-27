import type { EventStatus, OccurrenceStatus } from './eventDraft';

export const EVENT_STATUS_LABEL: Record<EventStatus, string> = {
  DRAFT: 'Rascunho',
  PUBLISHED: 'Publicado',
  CANCELLED: 'Cancelado',
  ARCHIVED: 'Arquivado',
};

export const OCCURRENCE_STATUS_LABEL: Record<OccurrenceStatus, string> = {
  SCHEDULED: 'Agendada',
  CANCELLED: 'Cancelada',
  SOLD_OUT: 'Esgotada',
};
