import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { renderHook, waitFor } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import type { ReactNode } from 'react';
import { describe, expect, it } from 'vitest';
import { apiUrl, problem, server } from '../test/server';
import {
  useIngestionRun,
  useIngestionRuns,
  useIngestionSources,
  useUnpublishRun,
} from './ingestion';

function wrapper() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  const Wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  );
  return { Wrapper, queryClient };
}

const run = {
  id: 7,
  source: { code: 'sympla', name: 'Sympla' },
  startedAt: '2026-03-01T10:00:00Z',
  finishedAt: '2026-03-01T10:05:00Z',
  durationSeconds: 300,
  status: 'SUCCESS',
  created: 3,
  updated: 2,
  unchanged: 1,
  failed: 0,
};

describe('useIngestionRuns', () => {
  it('sends page, size and only the filters that are set', async () => {
    const queries: URLSearchParams[] = [];
    server.use(
      http.get(apiUrl('/admin/ingestion-runs'), ({ request }) => {
        queries.push(new URL(request.url).searchParams);
        return HttpResponse.json({
          items: [run],
          page: { page: 2, size: 20, totalElements: 41, totalPages: 3 },
        });
      }),
    );
    const { Wrapper } = wrapper();
    const { result } = renderHook(
      () => useIngestionRuns(2, { source: 'sympla', status: 'FAILED' }),
      { wrapper: Wrapper },
    );

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data?.items[0].id).toBe(7);
    expect(queries[0].get('page')).toBe('2');
    expect(queries[0].get('size')).toBe('20');
    expect(queries[0].get('source')).toBe('sympla');
    expect(queries[0].get('status')).toBe('FAILED');
  });

  it('omits unset filters', async () => {
    const queries: URLSearchParams[] = [];
    server.use(
      http.get(apiUrl('/admin/ingestion-runs'), ({ request }) => {
        queries.push(new URL(request.url).searchParams);
        return HttpResponse.json({
          items: [],
          page: { page: 0, size: 20, totalElements: 0, totalPages: 0 },
        });
      }),
    );
    const { Wrapper } = wrapper();
    const { result } = renderHook(() => useIngestionRuns(0), {
      wrapper: Wrapper,
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(queries[0].has('source')).toBe(false);
    expect(queries[0].has('status')).toBe(false);
  });

  it('rejects with the Problem body', async () => {
    server.use(
      http.get(apiUrl('/admin/ingestion-runs'), () =>
        problem(500, 'Banco indisponível.'),
      ),
    );
    const { Wrapper } = wrapper();
    const { result } = renderHook(() => useIngestionRuns(0), {
      wrapper: Wrapper,
    });

    await waitFor(() => expect(result.current.isError).toBe(true));
    expect(result.current.error).toMatchObject({
      detail: 'Banco indisponível.',
    });
  });
});

describe('useIngestionSources', () => {
  it('lists the sources', async () => {
    server.use(
      http.get(apiUrl('/admin/ingestion-sources'), () =>
        HttpResponse.json([{ code: 'sympla', name: 'Sympla' }]),
      ),
    );
    const { Wrapper } = wrapper();
    const { result } = renderHook(() => useIngestionSources(), {
      wrapper: Wrapper,
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data).toEqual([{ code: 'sympla', name: 'Sympla' }]);
  });
});

describe('useIngestionRun', () => {
  it('stays idle without an id', () => {
    const { Wrapper } = wrapper();
    const { result } = renderHook(() => useIngestionRun(undefined), {
      wrapper: Wrapper,
    });
    expect(result.current.fetchStatus).toBe('idle');
  });

  it('fetches the detail by id', async () => {
    server.use(
      http.get(apiUrl('/admin/ingestion-runs/:id'), ({ params }) =>
        HttpResponse.json({
          ...run,
          id: Number(params.id),
          errors: [],
          createdEvents: [],
        }),
      ),
    );
    const { Wrapper } = wrapper();
    const { result } = renderHook(() => useIngestionRun(7), {
      wrapper: Wrapper,
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data?.id).toBe(7);
  });
});

describe('useUnpublishRun', () => {
  it('posts, returns the count and invalidates runs and events', async () => {
    server.use(
      http.post(apiUrl('/admin/ingestion-runs/:id/unpublish'), () =>
        HttpResponse.json({ unpublished: 2 }),
      ),
    );
    const { Wrapper, queryClient } = wrapper();
    queryClient.setQueryData(['ingestion', 'run', 7], { id: 7 });
    queryClient.setQueryData(['events', 'admin', 0], { items: [] });
    const { result } = renderHook(() => useUnpublishRun(), {
      wrapper: Wrapper,
    });

    const data = await result.current.mutateAsync(7);

    expect(data).toEqual({ unpublished: 2 });
    await waitFor(() => {
      expect(
        queryClient.getQueryState(['ingestion', 'run', 7])?.isInvalidated,
      ).toBe(true);
      expect(
        queryClient.getQueryState(['events', 'admin', 0])?.isInvalidated,
      ).toBe(true);
    });
  });

  it('rejects with the Problem on a conflict', async () => {
    server.use(
      http.post(apiUrl('/admin/ingestion-runs/:id/unpublish'), () =>
        problem(409, 'A execução ainda está em andamento.'),
      ),
    );
    const { Wrapper } = wrapper();
    const { result } = renderHook(() => useUnpublishRun(), {
      wrapper: Wrapper,
    });

    await expect(result.current.mutateAsync(7)).rejects.toMatchObject({
      status: 409,
    });
  });
});
