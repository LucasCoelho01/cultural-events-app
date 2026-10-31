import {
  keepPreviousData,
  useMutation,
  useQuery,
  useQueryClient,
} from '@tanstack/react-query';
import { client } from './client';
import type { components } from './schema';

export type IngestionRun = components['schemas']['IngestionRunSummary'];
export type IngestionRunDetail = components['schemas']['IngestionRunDetail'];
export type IngestionRunStatus = components['schemas']['IngestionRunStatus'];
export type IngestionSource = components['schemas']['SourceRef'];

export const RUNS_PAGE_SIZE = 20;

export const ingestionKeys = {
  all: ['ingestion'] as const,
  runs: (page: number, source: string, status: string) =>
    ['ingestion', 'runs', page, source, status] as const,
  sources: ['ingestion', 'sources'] as const,
  run: (id: number) => ['ingestion', 'run', id] as const,
};

export type RunsFilters = {
  /** Source code, e.g. `sympla`. */
  source?: string;
  status?: IngestionRunStatus;
};

/** Agent runs, most recent first; `page` is zero-based, as in the API. */
export function useIngestionRuns(
  page: number,
  { source, status }: RunsFilters = {},
) {
  return useQuery({
    queryKey: ingestionKeys.runs(page, source ?? '', status ?? ''),
    placeholderData: keepPreviousData,
    queryFn: async () => {
      const { data, error } = await client.GET('/admin/ingestion-runs', {
        params: {
          query: {
            page,
            size: RUNS_PAGE_SIZE,
            ...(source && { source }),
            ...(status && { status }),
          },
        },
      });
      if (error) throw error;
      return data;
    },
  });
}

/** Sources for the runs filter. */
export function useIngestionSources() {
  return useQuery({
    queryKey: ingestionKeys.sources,
    queryFn: async () => {
      const { data, error } = await client.GET('/admin/ingestion-sources');
      if (error) throw error;
      return data;
    },
  });
}

/** One run with its errors and created events; `id` is the numeric run id. */
export function useIngestionRun(id: number | undefined) {
  return useQuery({
    queryKey: ingestionKeys.run(id ?? 0),
    enabled: id !== undefined,
    queryFn: async () => {
      const { data, error } = await client.GET('/admin/ingestion-runs/{id}', {
        params: { path: { id: id! } },
      });
      if (error) throw error;
      return data;
    },
  });
}

/** Unpublishes (PUBLISHED → DRAFT) the events a run created; resolves with the count. */
export function useUnpublishRun() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (id: number) => {
      const { data, error } = await client.POST(
        '/admin/ingestion-runs/{id}/unpublish',
        { params: { path: { id } } },
      );
      if (error) throw error;
      return data;
    },
    // Refresh even on failure: a 409 means the data changed under the admin.
    onSettled: () =>
      Promise.all([
        queryClient.invalidateQueries({ queryKey: ingestionKeys.all }),
        queryClient.invalidateQueries({ queryKey: ['events'] }),
      ]),
  });
}
