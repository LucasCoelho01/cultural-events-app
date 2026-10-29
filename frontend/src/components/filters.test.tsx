import { fireEvent, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { renderApp } from '../test/render';
import { apiUrl, eventPage, makeEvent, problem, server } from '../test/server';

const LIST = '/zz/cidade-teste';
// Wednesday 2099-03-11, 10:00 in America/Recife (UTC-3).
const NOW = '2099-03-11T13:00:00Z';
const NOW_LOCAL = '2099-03-11T10:00:00-03:00';
// Without a period `from` is the plain current instant, as before the filters existed.
const NOW_ISO = '2099-03-11T13:00:00.000Z';

beforeEach(() => {
  vi.useFakeTimers({ toFake: ['Date'] });
  vi.setSystemTime(new Date(NOW));
});
afterEach(() => vi.useRealTimers());

/** Records the query string of every /events request. */
function recordEvents(
  respond: (params: URLSearchParams) => Response | Promise<Response> = (
    params,
  ) =>
    HttpResponse.json(eventPage([makeEvent()], Number(params.get('page')), 5)),
) {
  const calls: URLSearchParams[] = [];
  server.use(
    http.get(apiUrl('/events'), ({ request }) => {
      const params = new URL(request.url).searchParams;
      calls.push(params);
      return respond(params);
    }),
  );
  return calls;
}

const location = () => screen.getByTestId('location').textContent;
const lastCall = (calls: URLSearchParams[]) => calls[calls.length - 1];

describe('category filter', () => {
  it('sends the slug, updates the URL and indents subcategories', async () => {
    const calls = recordEvents();
    const user = userEvent.setup();
    renderApp(LIST);

    const select = await screen.findByLabelText('Categoria');
    expect(select).toHaveValue('');
    const rock = screen.getByRole('option', { name: /Rock/ });
    expect(rock.textContent?.startsWith('  ')).toBe(true);
    expect(screen.getByRole('option', { name: 'Shows' }).textContent).toBe(
      'Shows',
    );
    expect(calls[0].get('category')).toBeNull();

    await user.selectOptions(select, 'shows');
    await waitFor(() => expect(lastCall(calls).get('category')).toBe('shows'));
    expect(lastCall(calls).get('page')).toBe('0');
    expect(location()).toBe(`${LIST}?categoria=shows`);

    await user.selectOptions(select, '');
    await waitFor(() => expect(lastCall(calls).get('category')).toBeNull());
    expect(location()).toBe(LIST);
  });

  it('waits for GET /categories before requesting events and then sends the slug once', async () => {
    let release = () => {};
    const gate = new Promise<void>((resolve) => (release = resolve));
    server.use(
      http.get(apiUrl('/categories'), async () => {
        await gate;
        return HttpResponse.json([
          {
            id: 1,
            name: 'Shows',
            slug: 'shows',
            sortOrder: 10,
            parentSlug: null,
            children: [],
          },
        ]);
      }),
    );
    const calls = recordEvents();
    renderApp(`${LIST}?categoria=shows`);

    await screen.findByRole('heading', { name: /Eventos em/ });
    await new Promise((resolve) => setTimeout(resolve, 100));
    expect(calls).toHaveLength(0);
    expect(screen.getByText('Carregando eventos…')).toBeInTheDocument();

    release();
    await screen.findByRole('link', { name: /Show Teste/ });
    expect(calls).toHaveLength(1);
    expect(calls[0].get('category')).toBe('shows');
  });

  it('ignores a slug that does not exist, without error', async () => {
    const calls = recordEvents();
    renderApp(`${LIST}?categoria=inexistente`);

    await screen.findByRole('link', { name: /Show Teste/ });
    expect(calls).toHaveLength(1);
    expect(calls[0].get('category')).toBeNull();
    expect(screen.getByLabelText('Categoria')).toHaveValue('');
    expect(screen.queryByRole('button', { name: 'Limpar filtros' })).toBeNull();
  });

  it('ignores the category and hides the select when GET /categories fails', async () => {
    server.use(
      http.get(apiUrl('/categories'), () =>
        problem(500, 'Falha nas categorias.'),
      ),
    );
    const calls = recordEvents();
    renderApp(`${LIST}?categoria=shows`);

    await screen.findByRole('link', { name: /Show Teste/ });
    expect(calls).toHaveLength(1);
    expect(calls[0].get('category')).toBeNull();
    expect(screen.queryByLabelText('Categoria')).toBeNull();
    expect(screen.queryByText('Falha nas categorias.')).toBeNull();
  });
});

describe('period filter', () => {
  it('sends the range of "Hoje" in the zone of the city', async () => {
    const calls = recordEvents();
    const user = userEvent.setup();
    renderApp(LIST);

    await user.click(await screen.findByRole('button', { name: 'Hoje' }));
    await waitFor(() => expect(lastCall(calls).get('to')).not.toBeNull());
    expect(lastCall(calls).get('from')).toBe(NOW_LOCAL);
    expect(lastCall(calls).get('to')).toBe('2099-03-11T23:59:00-03:00');
    expect(location()).toBe(`${LIST}?periodo=hoje`);
    expect(screen.getByRole('button', { name: 'Hoje' })).toHaveAttribute(
      'aria-pressed',
      'true',
    );
  });

  it.each([
    ['fim-de-semana', '2099-03-14T00:00:00-03:00', '2099-03-15T23:59:00-03:00'],
    ['7-dias', NOW_LOCAL, '2099-03-17T23:59:00-03:00'],
  ])('reads ?periodo=%s from the URL', async (periodo, from, to) => {
    const calls = recordEvents();
    renderApp(`${LIST}?periodo=${periodo}`);

    await screen.findByRole('link', { name: /Show Teste/ });
    expect(calls).toHaveLength(1);
    expect(calls[0].get('from')).toBe(from);
    expect(calls[0].get('to')).toBe(to);
  });

  it('sends a custom range and writes it to the URL', async () => {
    const calls = recordEvents();
    renderApp(LIST);

    fireEvent.change(await screen.findByLabelText('De'), {
      target: { value: '2099-03-14' },
    });
    fireEvent.change(screen.getByLabelText('Até'), {
      target: { value: '2099-03-16' },
    });

    await waitFor(() =>
      expect(lastCall(calls).get('to')).toBe('2099-03-16T23:59:00-03:00'),
    );
    expect(lastCall(calls).get('from')).toBe('2099-03-14T00:00:00-03:00');
    expect(location()).toBe(`${LIST}?de=2099-03-14&ate=2099-03-16`);
  });

  it('ignores a range that starts after it ends and shows the message', async () => {
    const calls = recordEvents();
    renderApp(`${LIST}?de=2099-03-16&ate=2099-03-14`);

    expect(
      await screen.findByText('A data inicial deve ser anterior à final'),
    ).toBeVisible();
    await screen.findByRole('link', { name: /Show Teste/ });
    expect(calls).toHaveLength(1);
    expect(calls[0].get('from')).toBe(NOW_ISO);
    expect(calls[0].get('to')).toBeNull();
  });

  it('treats an `ate` before today in the zone of the city as an invalid range', async () => {
    const calls = recordEvents();
    renderApp(`${LIST}?ate=2099-03-10`);

    expect(
      await screen.findByText('A data inicial deve ser anterior à final'),
    ).toBeVisible();
    await screen.findByRole('link', { name: /Show Teste/ });
    expect(calls).toHaveLength(1);
    expect(calls[0].get('from')).toBe(NOW_ISO);
    expect(calls[0].get('to')).toBeNull();
  });

  it('accepts an `ate` equal to today', async () => {
    const calls = recordEvents();
    renderApp(`${LIST}?ate=2099-03-11`);

    await screen.findByRole('link', { name: /Show Teste/ });
    expect(calls[0].get('to')).toBe('2099-03-11T23:59:00-03:00');
    expect(
      screen.queryByText('A data inicial deve ser anterior à final'),
    ).toBeNull();
  });

  it('shows an error and sends no request when the city fails to load', async () => {
    server.use(
      http.get(apiUrl('/cities/:uf/:slug'), () =>
        problem(500, 'Cidade indisponível.'),
      ),
    );
    const calls = recordEvents();
    renderApp(`${LIST}?periodo=hoje`);

    expect(
      (await screen.findAllByText('Cidade indisponível.')).length,
    ).toBeGreaterThan(0);
    expect(calls).toHaveLength(0);
  });
});

describe('filters in the URL', () => {
  it('goes back to the first page when a filter changes', async () => {
    const calls = recordEvents();
    const user = userEvent.setup();
    renderApp(`${LIST}?page=3`);

    await screen.findByText('Página 3 de 5');
    expect(calls[0].get('page')).toBe('2');

    await user.selectOptions(screen.getByLabelText('Categoria'), 'teatro');
    await waitFor(() => expect(lastCall(calls).get('category')).toBe('teatro'));
    expect(lastCall(calls).get('page')).toBe('0');
    expect(location()).toBe(`${LIST}?categoria=teatro`);
  });

  it('goes back to the first page when a period is chosen', async () => {
    const calls = recordEvents();
    const user = userEvent.setup();
    renderApp(`${LIST}?categoria=shows&page=2`);

    await screen.findByText('Página 2 de 5');
    expect(calls[0].get('page')).toBe('1');

    await user.click(screen.getByRole('button', { name: 'Hoje' }));
    await waitFor(() => expect(lastCall(calls).get('to')).not.toBeNull());
    expect(lastCall(calls).get('page')).toBe('0');
    expect(lastCall(calls).get('category')).toBe('shows');
    expect(location()).toBe(`${LIST}?categoria=shows&periodo=hoje`);
  });

  it('shows the Problem detail when the API rejects the filters with 400', async () => {
    recordEvents(() => problem(400, 'Filtro de eventos inválido.'));
    renderApp(`${LIST}?categoria=shows`);

    expect(
      await screen.findByText('Filtro de eventos inválido.'),
    ).toBeVisible();
    expect(screen.queryByRole('link', { name: /Show Teste/ })).toBeNull();
  });

  it('keeps the filters when moving between pages', async () => {
    const calls = recordEvents();
    const user = userEvent.setup();
    renderApp(`${LIST}?categoria=shows&periodo=hoje`);

    await user.click(await screen.findByRole('button', { name: 'Próxima' }));
    await waitFor(() => expect(lastCall(calls).get('page')).toBe('1'));
    expect(lastCall(calls).get('category')).toBe('shows');
    expect(lastCall(calls).get('to')).toBe('2099-03-11T23:59:00-03:00');
    expect(location()).toBe(`${LIST}?categoria=shows&periodo=hoje&page=2`);
  });

  it('combines filters with ?page= from the URL', async () => {
    const calls = recordEvents();
    renderApp(`${LIST}?categoria=shows&periodo=hoje&page=2`);

    await screen.findByRole('link', { name: /Show Teste/ });
    expect(calls[0].get('category')).toBe('shows');
    expect(calls[0].get('to')).toBe('2099-03-11T23:59:00-03:00');
    expect(calls[0].get('page')).toBe('1');
  });

  it('ignores invalid values without error and without the clear button', async () => {
    const calls = recordEvents();
    renderApp(`${LIST}?periodo=amanha&de=2099-02-31&ate=abc`);

    await screen.findByRole('link', { name: /Show Teste/ });
    expect(calls[0].get('from')).toBe(NOW_ISO);
    expect(calls[0].get('to')).toBeNull();
    expect(screen.queryByRole('button', { name: 'Limpar filtros' })).toBeNull();
  });

  it('lets ?periodo= win over ?de=&ate=', async () => {
    const calls = recordEvents();
    renderApp(`${LIST}?periodo=hoje&de=2099-03-01&ate=2099-03-03`);

    await screen.findByRole('link', { name: /Show Teste/ });
    expect(calls[0].get('from')).toBe(NOW_LOCAL);
    expect(calls[0].get('to')).toBe('2099-03-11T23:59:00-03:00');
  });

  it('clears every filter and the page with "Limpar filtros"', async () => {
    const calls = recordEvents();
    const user = userEvent.setup();
    renderApp(`${LIST}?categoria=shows&periodo=hoje&page=2`);

    await user.click(
      await screen.findByRole('button', { name: 'Limpar filtros' }),
    );
    await waitFor(() => expect(lastCall(calls).get('category')).toBeNull());
    expect(lastCall(calls).get('to')).toBeNull();
    expect(lastCall(calls).get('page')).toBe('0');
    expect(lastCall(calls).get('from')).toBe(NOW_ISO);
    expect(location()).toBe(LIST);
    expect(screen.queryByRole('button', { name: 'Limpar filtros' })).toBeNull();
  });
});

describe('empty state with filters', () => {
  it('says no event matches and offers to clear', async () => {
    const calls = recordEvents((params) =>
      HttpResponse.json(eventPage(params.get('category') ? [] : [makeEvent()])),
    );
    const user = userEvent.setup();
    renderApp(`${LIST}?categoria=teatro`);

    expect(
      await screen.findByText('Nenhum evento para esses filtros'),
    ).toBeVisible();
    expect(screen.queryByText('Nenhum evento encontrado.')).toBeNull();

    await user.click(screen.getByRole('button', { name: 'Limpar filtros' }));
    await screen.findByRole('link', { name: /Show Teste/ });
    expect(location()).toBe(LIST);
    expect(lastCall(calls).get('category')).toBeNull();
  });

  it('keeps "Anterior" available beyond the last page', async () => {
    recordEvents(() => HttpResponse.json(eventPage([], 98, 2)));
    renderApp(`${LIST}?categoria=shows&page=99`);

    expect(
      await screen.findByText('Nenhum evento para esses filtros'),
    ).toBeVisible();
    expect(screen.getByRole('button', { name: 'Anterior' })).toBeEnabled();
    expect(
      screen.getByRole('button', { name: 'Limpar filtros' }),
    ).toBeVisible();
  });
});

describe('card session with a period', () => {
  const wednesday = {
    startsAt: '2099-03-11T20:00:00Z',
    endsAt: null,
    status: 'SCHEDULED' as const,
  };
  const saturday = { ...wednesday, startsAt: '2099-03-14T20:00:00Z' };

  it('shows the next session overall without a period', async () => {
    recordEvents(() =>
      HttpResponse.json(
        eventPage([makeEvent({ occurrences: [wednesday, saturday] })]),
      ),
    );
    renderApp(LIST);

    const card = await screen.findByRole('link', { name: /Show Teste/ });
    expect(card).toHaveTextContent(/11 de mar\. de 2099/);
  });

  it('shows the first session inside the period, not the next overall', async () => {
    recordEvents(() =>
      HttpResponse.json(
        eventPage([makeEvent({ occurrences: [wednesday, saturday] })]),
      ),
    );
    renderApp(`${LIST}?periodo=fim-de-semana`);

    const card = await screen.findByRole('link', { name: /Show Teste/ });
    expect(card).toHaveTextContent(/14 de mar\. de 2099/);
    expect(card).not.toHaveTextContent(/11 de mar\./);
  });

  it('says there is no session in the period when none falls inside it', async () => {
    recordEvents(() =>
      HttpResponse.json(eventPage([makeEvent({ occurrences: [wednesday] })])),
    );
    renderApp(`${LIST}?periodo=fim-de-semana`);

    const card = await screen.findByRole('link', { name: /Show Teste/ });
    expect(card).toHaveTextContent('Sem sessões no período');
    expect(card).not.toHaveTextContent('Sem sessões futuras');
  });
});
