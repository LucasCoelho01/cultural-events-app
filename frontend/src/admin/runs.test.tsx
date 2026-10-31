import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import type { IngestionRun, IngestionRunDetail } from '../api/ingestion';
import { renderApp } from '../test/render';
import { apiUrl, problem, server } from '../test/server';

const LIST = '/admin/execucoes';
const EVENT_A = '33333333-3333-4333-8333-333333333333';
const EVENT_B = '44444444-4444-4444-8444-444444444444';
const EVENT_C = '55555555-5555-4555-8555-555555555555';

const makeRun = (overrides: Partial<IngestionRun> = {}): IngestionRun => ({
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
  ...overrides,
});

const runPage = (items: IngestionRun[], page = 0, totalPages = 1) => ({
  items,
  page: { page, size: 20, totalElements: items.length, totalPages },
});

const makeDetail = (
  overrides: Partial<IngestionRunDetail> = {},
): IngestionRunDetail => ({
  ...makeRun(),
  errors: [],
  createdEvents: [],
  ...overrides,
});

const threeEvents: IngestionRunDetail['createdEvents'] = [
  { id: EVENT_A, title: 'Show A', status: 'PUBLISHED' },
  { id: EVENT_B, title: 'Show B', status: 'PUBLISHED' },
  { id: EVENT_C, title: 'Show C', status: 'DRAFT' },
];

const sources = [
  { code: 'sympla', name: 'Sympla' },
  { code: 'outra', name: 'Outra Fonte' },
];

function mockList(items: IngestionRun[], queries: URLSearchParams[] = []) {
  server.use(
    http.get(apiUrl('/admin/ingestion-sources'), () =>
      HttpResponse.json(sources),
    ),
    http.get(apiUrl('/admin/ingestion-runs'), ({ request }) => {
      queries.push(new URL(request.url).searchParams);
      return HttpResponse.json(runPage(items));
    }),
  );
  return queries;
}

function mockDetail(detail: IngestionRunDetail) {
  server.use(
    http.get(apiUrl('/admin/ingestion-runs/:id'), () =>
      HttpResponse.json(detail),
    ),
  );
}

describe('runs table', () => {
  it('shows a badge per status, the counters and the duration', async () => {
    mockList([
      makeRun({ id: 1, status: 'SUCCESS' }),
      makeRun({ id: 2, status: 'PARTIAL', failed: 4, durationSeconds: 45 }),
      makeRun({ id: 3, status: 'FAILED', durationSeconds: 3720 }),
      makeRun({
        id: 4,
        status: 'RUNNING',
        finishedAt: null,
        durationSeconds: null,
      }),
    ]);
    renderApp(LIST);

    const rows = await screen.findAllByRole('row');
    const [success, partial, failed, running] = rows.slice(1);
    expect(within(success).getByText('Sucesso')).toHaveAttribute(
      'data-status',
      'SUCCESS',
    );
    expect(within(partial).getByText('Parcial')).toHaveAttribute(
      'data-status',
      'PARTIAL',
    );
    expect(within(failed).getByText('Falhou')).toHaveAttribute(
      'data-status',
      'FAILED',
    );
    expect(
      within(running).getByText('Em andamento', {
        selector: 'span[data-status]',
      }),
    ).toHaveAttribute('data-status', 'RUNNING');
    expect(success).toHaveTextContent('5 min');
    expect(partial).toHaveTextContent('45 s');
    expect(failed).toHaveTextContent('1 h 2 min');
    expect(within(running).getAllByText('Em andamento')).toHaveLength(2);
    expect(success).toHaveTextContent('Sympla');
    expect(
      within(success)
        .getAllByRole('cell')
        .slice(4, 8)
        .map((cell) => cell.textContent),
    ).toEqual(['3', '2', '1', '0']);
    expect(
      within(success).getByRole('link', { name: 'Ver execução 1' }),
    ).toHaveAttribute('href', '/admin/execucoes/1');
  });

  it('sends the filters, goes back to page 1 and keeps them in the URL', async () => {
    const queries = mockList([makeRun()]);
    renderApp(`${LIST}?page=2`);

    await screen.findByRole('table');
    await userEvent.selectOptions(screen.getByLabelText('Fonte'), 'sympla');
    await userEvent.selectOptions(screen.getByLabelText('Status'), 'FAILED');

    await waitFor(() => {
      const last = queries.at(-1)!;
      expect(last.get('source')).toBe('sympla');
      expect(last.get('status')).toBe('FAILED');
      expect(last.get('page')).toBe('0');
    });
    expect(screen.getByTestId('location')).toHaveTextContent(
      `${LIST}?fonte=sympla&status=FAILED`,
    );
  });

  it('reads the filters from the URL', async () => {
    const queries = mockList([makeRun()]);
    renderApp(`${LIST}?fonte=sympla&status=PARTIAL`);

    await screen.findByRole('table');
    expect(queries[0].get('source')).toBe('sympla');
    expect(queries[0].get('status')).toBe('PARTIAL');
    expect(screen.getByLabelText('Status')).toHaveValue('PARTIAL');
  });

  it('shows the empty state', async () => {
    mockList([]);
    renderApp(LIST);

    expect(
      await screen.findByText('Nenhuma execução encontrada.'),
    ).toBeInTheDocument();
    expect(screen.queryByRole('table')).not.toBeInTheDocument();
  });

  it('shows the Problem detail and no table when the API fails', async () => {
    server.use(
      http.get(apiUrl('/admin/ingestion-sources'), () =>
        HttpResponse.json(sources),
      ),
      http.get(apiUrl('/admin/ingestion-runs'), () =>
        problem(500, 'Banco indisponível.'),
      ),
    );
    renderApp(LIST);

    const alerts = await screen.findAllByRole('alert');
    expect(alerts[0]).toHaveTextContent('Banco indisponível.');
    expect(screen.queryByRole('table')).not.toBeInTheDocument();
  });
});

describe('admin navigation', () => {
  it('has a header link that opens the runs page', async () => {
    mockList([makeRun()]);
    renderApp('/admin/eventos');

    await userEvent.click(
      await screen.findByRole('link', { name: 'Execuções do agente' }),
    );

    expect(
      await screen.findByRole('heading', { name: 'Execuções do agente' }),
    ).toBeInTheDocument();
    expect(screen.getByTestId('location')).toHaveTextContent(LIST);
  });

  it('routes /admin/execucoes/:id to the detail page', async () => {
    mockDetail(makeDetail({ id: 9 }));
    renderApp('/admin/execucoes/9');

    expect(
      await screen.findByRole('heading', { name: /Execução #9/ }),
    ).toBeInTheDocument();
  });
});

describe('run detail', () => {
  it('shows the summary, the errors and the created events with edit links', async () => {
    mockDetail(
      makeDetail({
        status: 'PARTIAL',
        failed: 2,
        errors: [
          {
            code: 'EXTRACTION_FAILED',
            externalId: '123',
            detail: 'sem título',
          },
          { code: null, externalId: '-', detail: 'erro inesperado' },
        ],
        createdEvents: threeEvents,
      }),
    );
    renderApp('/admin/execucoes/7');

    expect(
      await screen.findByRole('heading', { name: /Execução #7 · Sympla/ }),
    ).toBeInTheDocument();
    expect(screen.getByText('Parcial')).toHaveAttribute(
      'data-status',
      'PARTIAL',
    );
    expect(screen.getByText('EXTRACTION_FAILED')).toBeInTheDocument();
    expect(screen.getByText('123')).toBeInTheDocument();
    expect(screen.getByText('sem título')).toBeInTheDocument();
    expect(screen.getByText('erro inesperado')).toBeInTheDocument();
    for (const [title, id] of [
      ['Show A', EVENT_A],
      ['Show B', EVENT_B],
      ['Show C', EVENT_C],
    ]) {
      expect(
        screen.getByRole('link', { name: `Editar ${title}` }),
      ).toHaveAttribute('href', `/admin/eventos/${id}/editar`);
    }
    expect(screen.getByText('Show C').closest('tr')).toHaveTextContent(
      'Rascunho',
    );
  });

  it('shows the empty messages', async () => {
    mockDetail(makeDetail());
    renderApp('/admin/execucoes/7');

    expect(
      await screen.findByText('Nenhum erro registrado'),
    ).toBeInTheDocument();
    expect(
      screen.getByText('Nenhum evento criado nesta execução'),
    ).toBeInTheDocument();
  });

  it('shows the Problem detail on a 404', async () => {
    server.use(
      http.get(apiUrl('/admin/ingestion-runs/:id'), () =>
        problem(404, 'Execução não encontrada: 99'),
      ),
    );
    renderApp('/admin/execucoes/99');

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Execução não encontrada: 99',
    );
  });

  it('does not call the API for an id that is not a number', async () => {
    renderApp('/admin/execucoes/abc');

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Execução não encontrada.',
    );
  });

  it('shows the Problem detail on a server error', async () => {
    server.use(
      http.get(apiUrl('/admin/ingestion-runs/:id'), () =>
        problem(500, 'Banco indisponível.'),
      ),
    );
    renderApp('/admin/execucoes/7');

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Banco indisponível.',
    );
  });
});

describe('batch unpublish', () => {
  const buttonName = 'Despublicar eventos desta execução';

  /** Serves the detail from `state`; a successful POST flips the published events to DRAFT. */
  function mockStateful() {
    const state = { events: threeEvents, gets: 0, posts: 0 };
    server.use(
      http.get(apiUrl('/admin/ingestion-runs/:id'), () => {
        state.gets++;
        return HttpResponse.json(makeDetail({ createdEvents: state.events }));
      }),
      http.post(apiUrl('/admin/ingestion-runs/:id/unpublish'), () => {
        state.posts++;
        state.events = state.events.map((event) => ({
          ...event,
          status: 'DRAFT' as const,
        }));
        return HttpResponse.json({ unpublished: 2 });
      }),
    );
    return state;
  }

  it('asks for confirmation with the count, then posts and reloads', async () => {
    const state = mockStateful();
    renderApp('/admin/execucoes/7');

    await userEvent.click(
      await screen.findByRole('button', { name: buttonName }),
    );
    const dialog = screen.getByRole('alertdialog');
    expect(dialog).toHaveTextContent('2 eventos');
    expect(state.posts).toBe(0);

    await userEvent.click(
      within(dialog).getByRole('button', { name: 'Confirmar' }),
    );

    expect(
      await screen.findByText('2 eventos despublicados.'),
    ).toBeInTheDocument();
    await waitFor(() =>
      expect(screen.getByText('Show A').closest('tr')).toHaveTextContent(
        'Rascunho',
      ),
    );
    expect(state.posts).toBe(1);
    expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument();
    // Nothing left to unpublish.
    expect(screen.getByRole('button', { name: buttonName })).toBeDisabled();
  });

  it('sends no POST when the admin cancels', async () => {
    const state = mockStateful();
    renderApp('/admin/execucoes/7');

    await userEvent.click(
      await screen.findByRole('button', { name: buttonName }),
    );
    await userEvent.click(screen.getByRole('button', { name: 'Cancelar' }));

    expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument();
    expect(state.posts).toBe(0);
  });

  it('shows the conflict detail and reloads the data', async () => {
    const state = { gets: 0 };
    server.use(
      http.get(apiUrl('/admin/ingestion-runs/:id'), () => {
        state.gets++;
        return HttpResponse.json(makeDetail({ createdEvents: threeEvents }));
      }),
      http.post(apiUrl('/admin/ingestion-runs/:id/unpublish'), () =>
        problem(409, 'O evento foi alterado por outra requisição.'),
      ),
    );
    renderApp('/admin/execucoes/7');

    await userEvent.click(
      await screen.findByRole('button', { name: buttonName }),
    );
    await userEvent.click(screen.getByRole('button', { name: 'Confirmar' }));

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'O evento foi alterado por outra requisição.',
    );
    await waitFor(() => expect(state.gets).toBeGreaterThan(1));
  });

  it('disables the button while the run is in progress', async () => {
    let posts = 0;
    server.use(
      http.post(apiUrl('/admin/ingestion-runs/:id/unpublish'), () => {
        posts++;
        return HttpResponse.json({ unpublished: 0 });
      }),
    );
    mockDetail(
      makeDetail({
        status: 'RUNNING',
        finishedAt: null,
        durationSeconds: null,
        createdEvents: threeEvents,
      }),
    );
    renderApp('/admin/execucoes/7');

    const button = await screen.findByRole('button', { name: buttonName });
    expect(button).toBeDisabled();
    await userEvent.click(button);
    expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument();
    expect(posts).toBe(0);
  });

  it('disables the button when no created event is published', async () => {
    mockDetail(
      makeDetail({
        createdEvents: [{ id: EVENT_C, title: 'Show C', status: 'DRAFT' }],
      }),
    );
    renderApp('/admin/execucoes/7');

    expect(
      await screen.findByRole('button', { name: buttonName }),
    ).toBeDisabled();
  });
});
