import { QueryClientProvider } from '@tanstack/react-query';
import { render, screen } from '@testing-library/react';
import { http } from 'msw';
import { MemoryRouter } from 'react-router';
import { describe, expect, it } from 'vitest';
import { App } from '../App';
import { apiUrl, problem, server } from '../test/server';
import { createQueryClient, shouldRetry } from './queryClient';

describe('shouldRetry', () => {
  const problemWith = (status: number) => ({ title: 'x', status });

  it('does not retry 4xx Problems', () => {
    expect(shouldRetry(0, problemWith(404))).toBe(false);
    expect(shouldRetry(0, problemWith(422))).toBe(false);
  });

  it('retries 5xx Problems and network errors up to 2 times', () => {
    expect(shouldRetry(0, problemWith(500))).toBe(true);
    expect(shouldRetry(1, problemWith(503))).toBe(true);
    expect(shouldRetry(0, new TypeError('Failed to fetch'))).toBe(true);
    expect(shouldRetry(2, problemWith(500))).toBe(false);
    expect(shouldRetry(2, new TypeError('Failed to fetch'))).toBe(false);
  });
});

describe('city 404 with the production query client', () => {
  it('shows the Problem detail after a single request', async () => {
    let requests = 0;
    server.use(
      http.get(apiUrl('/cities/:uf/:slug'), () => {
        requests++;
        return problem(404, 'Cidade não encontrada.');
      }),
    );

    render(
      <QueryClientProvider client={createQueryClient()}>
        <MemoryRouter initialEntries={['/xx/inexistente']}>
          <App />
        </MemoryRouter>
      </QueryClientProvider>,
    );

    const alerts = await screen.findAllByRole('alert');
    expect(alerts[0]).toHaveTextContent('Cidade não encontrada.');
    // Layout header and page share one query (same key), so a single request.
    expect(requests).toBe(1);
  });
});
