import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { renderApp } from '../test/render';
import { apiUrl, eventPage, makeEvent, problem, server } from '../test/server';

const LIST = '/zz/cidade-teste';
const NOW = '2099-03-11T13:00:00Z';

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
const searchField = () =>
  screen.findByRole('searchbox', { name: 'Buscar eventos' });

describe('search box', () => {
  it('applies on Enter, trims the text, sends q and goes back to page 1', async () => {
    const calls = recordEvents();
    const user = userEvent.setup();
    renderApp(`${LIST}?page=3`);

    await user.type(await searchField(), '  musica {Enter}');

    await waitFor(() => expect(lastCall(calls).get('q')).toBe('musica'));
    expect(lastCall(calls).get('page')).toBe('0');
    expect(location()).toBe(`${LIST}?q=musica`);
  });

  it('applies with the button', async () => {
    const calls = recordEvents();
    const user = userEvent.setup();
    renderApp(LIST);

    await user.type(await searchField(), 'rock');
    await user.click(screen.getByRole('button', { name: 'Buscar' }));

    await waitFor(() => expect(lastCall(calls).get('q')).toBe('rock'));
    expect(location()).toBe(`${LIST}?q=rock`);
  });

  it('does not search while typing', async () => {
    const calls = recordEvents();
    const user = userEvent.setup();
    renderApp(LIST);

    await user.type(await searchField(), 'rock');
    await waitFor(() => expect(calls.length).toBeGreaterThan(0));
    const before = calls.length;
    await user.type(await searchField(), ' and roll');

    expect(calls).toHaveLength(before);
    expect(calls.every((call) => call.get('q') === null)).toBe(true);
    expect(location()).toBe(LIST);
  });

  it('keeps the other filters and drops the page', async () => {
    const calls = recordEvents();
    const user = userEvent.setup();
    renderApp(`${LIST}?categoria=shows&page=3`);

    await user.type(await searchField(), 'rock{Enter}');

    await waitFor(() => expect(lastCall(calls).get('q')).toBe('rock'));
    expect(lastCall(calls).get('category')).toBe('shows');
    expect(lastCall(calls).get('page')).toBe('0');
    expect(location()).toBe(`${LIST}?categoria=shows&q=rock`);
  });

  it('refuses a single character without searching', async () => {
    const calls = recordEvents();
    const user = userEvent.setup();
    renderApp(LIST);

    await user.type(await searchField(), ' a {Enter}');

    expect(
      await screen.findByText('Digite ao menos 2 caracteres'),
    ).toBeInTheDocument();
    expect(location()).toBe(LIST);
    expect(calls.every((call) => call.get('q') === null)).toBe(true);
  });

  it('removes q when the field is emptied and submitted', async () => {
    const calls = recordEvents();
    const user = userEvent.setup();
    renderApp(`${LIST}?q=rock`);

    const field = await searchField();
    await user.clear(field);
    await user.type(field, '{Enter}');

    await waitFor(() => expect(lastCall(calls).get('q')).toBeNull());
    expect(location()).toBe(LIST);
  });
});

describe('q in the URL', () => {
  it('fills the field and sends q with the other filters', async () => {
    const calls = recordEvents();
    renderApp(`${LIST}?q=musica&categoria=shows&page=2`);

    expect(await searchField()).toHaveValue('musica');
    await waitFor(() => expect(calls.length).toBeGreaterThan(0));
    expect(lastCall(calls).get('q')).toBe('musica');
    expect(lastCall(calls).get('category')).toBe('shows');
    expect(lastCall(calls).get('page')).toBe('1');
  });

  it.each(['a', '%20%20', 'a'.repeat(101)])(
    'ignores the invalid value %s',
    async (value) => {
      const calls = recordEvents();
      renderApp(`${LIST}?q=${value}`);

      expect(await searchField()).toHaveValue('');
      await waitFor(() => expect(calls.length).toBeGreaterThan(0));
      expect(calls.every((call) => call.get('q') === null)).toBe(true);
      expect(
        screen.queryByRole('button', { name: 'Limpar filtros' }),
      ).not.toBeInTheDocument();
    },
  );

  it('keeps q on the next page', async () => {
    const calls = recordEvents();
    const user = userEvent.setup();
    renderApp(`${LIST}?q=rock&categoria=shows`);

    await user.click(await screen.findByRole('button', { name: 'Próxima' }));

    await waitFor(() => expect(lastCall(calls).get('page')).toBe('1'));
    expect(lastCall(calls).get('q')).toBe('rock');
    expect(location()).toBe(`${LIST}?q=rock&categoria=shows&page=2`);
  });
});

describe('clearing the search', () => {
  it('"Limpar filtros" with only q empties the field and the URL', async () => {
    const calls = recordEvents();
    const user = userEvent.setup();
    renderApp(`${LIST}?q=rock&page=2`);

    await user.click(
      await screen.findByRole('button', { name: 'Limpar filtros' }),
    );

    await waitFor(() => expect(lastCall(calls).get('q')).toBeNull());
    expect(lastCall(calls).get('page')).toBe('0');
    expect(location()).toBe(LIST);
    expect(await searchField()).toHaveValue('');
    expect(
      screen.queryByRole('button', { name: 'Limpar filtros' }),
    ).not.toBeInTheDocument();
  });

  it('"Limpar filtros" also clears a typed but unsent term', async () => {
    recordEvents();
    const user = userEvent.setup();
    renderApp(`${LIST}?categoria=shows`);

    await user.type(await searchField(), 'abc');
    await user.click(screen.getByRole('button', { name: 'Limpar filtros' }));

    expect(await searchField()).toHaveValue('');
    expect(location()).toBe(LIST);
  });
});

describe('empty and error states', () => {
  it('names the term when the search finds nothing', async () => {
    const calls = recordEvents(() => HttpResponse.json(eventPage([])));
    renderApp(`${LIST}?q=xyzabc`);

    expect(
      await screen.findByText('Nenhum evento para "xyzabc"'),
    ).toBeInTheDocument();
    expect(lastCall(calls).get('q')).toBe('xyzabc');
    expect(
      screen.getByRole('button', { name: 'Limpar filtros' }),
    ).toBeInTheDocument();
  });

  it('names the term even with other filters, and clearing requests without q', async () => {
    const calls = recordEvents((params) =>
      HttpResponse.json(
        params.get('q') ? eventPage([]) : eventPage([makeEvent()]),
      ),
    );
    const user = userEvent.setup();
    renderApp(`${LIST}?q=xyzabc&categoria=shows`);

    expect(
      await screen.findByText('Nenhum evento para "xyzabc"'),
    ).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Limpar filtros' }));

    await waitFor(() => expect(lastCall(calls).get('q')).toBeNull());
    expect(lastCall(calls).get('category')).toBeNull();
    expect(location()).toBe(LIST);
  });

  it('keeps the old message for other filters without q', async () => {
    recordEvents(() => HttpResponse.json(eventPage([])));
    renderApp(`${LIST}?categoria=shows`);

    expect(
      await screen.findByText('Nenhum evento para esses filtros'),
    ).toBeInTheDocument();
  });

  it('shows the Problem detail when the API answers 400 to a search', async () => {
    recordEvents(() => problem(400, 'Parâmetro de requisição inválido: q.'));
    renderApp(`${LIST}?q=rock`);

    expect(
      await screen.findByText('Parâmetro de requisição inválido: q.'),
    ).toBeInTheDocument();
    expect(screen.queryByRole('list')).not.toBeInTheDocument();
  });
});
