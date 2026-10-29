import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { delay, http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import { renderApp } from '../test/render';
import {
  apiUrl,
  eventPage,
  FUTURE,
  makeEvent,
  PAST,
  problem,
  server,
  testVenue,
} from '../test/server';

const LIST = '/zz/cidade-teste';
const DETAIL = '/zz/cidade-teste/eventos/show-teste';

/** Registers an /events handler that records the query strings it receives. */
function recordEvents(respond: (page: number) => Response | Promise<Response>) {
  const calls: URLSearchParams[] = [];
  server.use(
    http.get(apiUrl('/events'), ({ request }) => {
      const params = new URL(request.url).searchParams;
      calls.push(params);
      return respond(Number(params.get('page')));
    }),
  );
  return calls;
}

describe('event list', () => {
  it('requests published events of the city and renders a linked card', async () => {
    const calls = recordEvents(() =>
      HttpResponse.json(eventPage([makeEvent()])),
    );
    renderApp(LIST);

    const link = await screen.findByRole('link', { name: /Show Teste/ });
    expect(link).toHaveAttribute('href', '/zz/cidade-teste/eventos/show-teste');
    expect(link).toHaveTextContent('Shows');
    expect(link).toHaveTextContent('Teatro Teste');
    expect(link).toHaveTextContent(/1 de mar\. de 2099/);
    expect(link).toHaveTextContent('17:00');
    expect(
      link.textContent?.replaceAll(String.fromCharCode(160), ' '),
    ).toContain('R$ 25,00 – R$ 80,00');

    const params = calls[0];
    expect(params.get('city')).toBe('zz/cidade-teste');
    expect(params.get('status')).toBe('PUBLISHED');
    expect(params.get('page')).toBe('0');
    expect(params.get('size')).toBe('12');
    expect(Number.isNaN(Date.parse(params.get('from') ?? ''))).toBe(false);
  });

  it('shows "Grátis", omits a missing venue and flags events without future sessions', async () => {
    recordEvents(() =>
      HttpResponse.json(
        eventPage([
          makeEvent({ title: 'Evento Livre', isFree: true, venue: null }),
          makeEvent({
            id: '33333333-3333-4333-8333-333333333333',
            title: 'Evento Antigo',
            slug: 'evento-antigo',
            occurrences: [
              { startsAt: PAST, endsAt: null, status: 'SCHEDULED' },
            ],
          }),
        ]),
      ),
    );
    renderApp(LIST);

    const free = await screen.findByRole('link', { name: /Evento Livre/ });
    expect(free).toHaveTextContent('Grátis');
    expect(free).not.toHaveTextContent('Teatro Teste');
    expect(
      screen.getByRole('link', { name: /Evento Antigo/ }),
    ).toHaveTextContent('Sem sessões futuras');
  });

  it('shows the loading state', async () => {
    recordEvents(async () => {
      await delay(100);
      return HttpResponse.json(eventPage([makeEvent()]));
    });
    renderApp(LIST);

    expect(await screen.findByText('Carregando eventos…')).toBeInTheDocument();
    await screen.findByRole('link', { name: /Show Teste/ });
  });

  it('shows the empty state on the first page, without pagination buttons', async () => {
    recordEvents(() => HttpResponse.json(eventPage([])));
    renderApp(LIST);

    expect(await screen.findByText('Nenhum evento encontrado.')).toBeVisible();
    expect(
      screen.queryByRole('button', { name: /Anterior|Próxima/ }),
    ).not.toBeInTheDocument();
  });

  it('shows the Problem detail on API errors', async () => {
    recordEvents(() => problem(500, 'Falha ao listar eventos.'));
    renderApp(LIST);

    const alert = await screen.findByText(
      'Falha ao listar eventos.',
      undefined,
      {
        timeout: 5000,
      },
    );
    expect(alert).toBeVisible();
    expect(screen.queryByRole('link', { name: /Show Teste/ })).toBeNull();
  });

  it('shows the Problem detail when the city does not exist', async () => {
    server.use(
      http.get(apiUrl('/cities/:uf/:slug'), () =>
        problem(404, 'Cidade não encontrada.'),
      ),
      http.get(apiUrl('/events'), () => problem(404, 'Cidade não encontrada.')),
    );
    renderApp('/xx/inexistente');

    const alerts = await screen.findAllByText('Cidade não encontrada.');
    expect(alerts.length).toBeGreaterThan(0);
    expect(screen.queryByRole('list')).not.toBeInTheDocument();
  });
});

describe('event list pagination and ?page=', () => {
  it('moves between pages, updating ?page= (one-based) and the API page (zero-based)', async () => {
    const calls = recordEvents((page) =>
      HttpResponse.json(
        eventPage([makeEvent({ title: `Evento da página ${page}` })], page, 3),
      ),
    );
    const user = userEvent.setup();
    renderApp(LIST);

    await screen.findByText('Evento da página 0');
    expect(screen.getByRole('button', { name: 'Anterior' })).toBeDisabled();
    expect(screen.getByText('Página 1 de 3')).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: 'Próxima' }));
    await screen.findByText('Evento da página 1');
    expect(screen.getByText('Página 2 de 3')).toBeInTheDocument();
    expect(calls.at(-1)?.get('page')).toBe('1');

    await user.click(screen.getByRole('button', { name: 'Próxima' }));
    await screen.findByText('Evento da página 2');
    expect(screen.getByRole('button', { name: 'Próxima' })).toBeDisabled();

    await user.click(screen.getByRole('button', { name: 'Anterior' }));
    await screen.findByText('Evento da página 1');
  });

  it('reads ?page=3 as API page 2', async () => {
    const calls = recordEvents(() =>
      HttpResponse.json(eventPage([makeEvent()], 2, 5)),
    );
    renderApp(`${LIST}?page=3`);

    await screen.findByText('Página 3 de 5');
    expect(calls[0].get('page')).toBe('2');
  });

  it.each(['0', '-1', 'abc'])(
    'treats ?page=%s as the first page',
    async (value) => {
      const calls = recordEvents(() =>
        HttpResponse.json(eventPage([makeEvent()])),
      );
      renderApp(`${LIST}?page=${value}`);

      await screen.findByRole('link', { name: /Show Teste/ });
      expect(calls[0].get('page')).toBe('0');
    },
  );

  it('shows the empty state beyond the last page, with "Anterior" enabled', async () => {
    recordEvents(() => HttpResponse.json(eventPage([], 98, 2)));
    renderApp(`${LIST}?page=99`);

    expect(await screen.findByText('Nenhum evento encontrado.')).toBeVisible();
    expect(screen.getByRole('button', { name: 'Anterior' })).toBeEnabled();
    expect(screen.getByRole('button', { name: 'Próxima' })).toBeDisabled();
  });
});

describe('event detail', () => {
  it('shows all fields, the address of the venue and a ticket link', async () => {
    renderApp(DETAIL);

    expect(
      await screen.findByRole('heading', { name: 'Show Teste' }),
    ).toBeInTheDocument();
    expect(screen.getByText('Um show de teste.')).toBeInTheDocument();
    expect(screen.getByText('Classificação: 14 anos')).toBeInTheDocument();
    expect(
      await screen.findByText('Rua Fictícia, 100 – Bairro Teste'),
    ).toBeVisible();
    expect(screen.getByText('Teatro Teste')).toBeVisible();
    const sessions = screen.getByRole('listitem');
    expect(sessions).toHaveTextContent(/1 de mar\. de 2099/);
    expect(sessions).toHaveTextContent('17:00');
    expect(
      screen.getByRole('link', { name: 'Comprar ingressos' }),
    ).toHaveAttribute('href', 'https://ingressos.example.com/show-teste');
  });

  it('lists only future sessions, keeping the status of cancelled and sold out ones', async () => {
    server.use(
      http.get(apiUrl('/cities/:uf/:slug/events/:eventSlug'), () =>
        HttpResponse.json(
          makeEvent({
            occurrences: [
              { startsAt: PAST, endsAt: null, status: 'SCHEDULED' },
              { startsAt: FUTURE, endsAt: null, status: 'CANCELLED' },
              {
                startsAt: '2099-04-01T20:00:00Z',
                endsAt: null,
                status: 'SOLD_OUT',
              },
            ],
          }),
        ),
      ),
    );
    renderApp(DETAIL);

    await screen.findByRole('heading', { name: 'Show Teste' });
    const items = await screen.findAllByRole('listitem');
    expect(items).toHaveLength(2);
    expect(items[0]).toHaveTextContent('Cancelada');
    expect(items[1]).toHaveTextContent('Esgotada');
  });

  it('shows "Sem sessões futuras" when every session is in the past', async () => {
    server.use(
      http.get(apiUrl('/cities/:uf/:slug/events/:eventSlug'), () =>
        HttpResponse.json(
          makeEvent({
            occurrences: [
              { startsAt: PAST, endsAt: null, status: 'SCHEDULED' },
            ],
          }),
        ),
      ),
    );
    renderApp(DETAIL);

    expect(await screen.findByText('Sem sessões futuras')).toBeVisible();
  });

  it('omits absent optional fields without errors and skips the venue request', async () => {
    let venueRequests = 0;
    server.use(
      http.get(apiUrl('/cities/:uf/:slug/events/:eventSlug'), () =>
        HttpResponse.json(
          makeEvent({
            description: null,
            ageRating: null,
            ticketUrl: null,
            venue: null,
            priceMinCents: null,
            priceMaxCents: null,
          }),
        ),
      ),
      http.get(apiUrl('/venues/:id'), () => {
        venueRequests++;
        return HttpResponse.json(testVenue);
      }),
    );
    renderApp(DETAIL);

    await screen.findByRole('heading', { name: 'Show Teste' });
    expect(screen.queryByText(/Classificação/)).not.toBeInTheDocument();
    expect(
      screen.queryByRole('link', { name: 'Comprar ingressos' }),
    ).toBeNull();
    expect(screen.queryByRole('heading', { name: 'Local' })).toBeNull();
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
    expect(venueRequests).toBe(0);
  });

  it('does not render a link for a non-http ticket URL', async () => {
    server.use(
      http.get(apiUrl('/cities/:uf/:slug/events/:eventSlug'), () =>
        HttpResponse.json(makeEvent({ ticketUrl: 'javascript:alert(1)' })),
      ),
    );
    renderApp(DETAIL);

    await screen.findByRole('heading', { name: 'Show Teste' });
    expect(
      screen.queryByRole('link', { name: 'Comprar ingressos' }),
    ).toBeNull();
  });

  it('shows the Problem detail when the event is not found', async () => {
    server.use(
      http.get(apiUrl('/cities/:uf/:slug/events/:eventSlug'), () =>
        problem(404, 'Evento não encontrado: show-teste'),
      ),
    );
    renderApp(DETAIL);

    expect(
      await screen.findByText('Evento não encontrado: show-teste'),
    ).toBeVisible();
    expect(screen.queryByRole('heading', { name: 'Show Teste' })).toBeNull();
  });

  it('shows the Problem detail on server errors', async () => {
    server.use(
      http.get(apiUrl('/cities/:uf/:slug/events/:eventSlug'), () =>
        problem(500, 'Erro ao carregar o evento.'),
      ),
    );
    renderApp(DETAIL);

    expect(
      await screen.findByText('Erro ao carregar o evento.', undefined, {
        timeout: 5000,
      }),
    ).toBeVisible();
  });

  it('keeps the event and shows only the venue name when the venue fails', async () => {
    server.use(
      http.get(apiUrl('/venues/:id'), () =>
        problem(404, 'Local não encontrado.'),
      ),
    );
    renderApp(DETAIL);

    await screen.findByRole('heading', { name: 'Show Teste' });
    expect(await screen.findByText('Teatro Teste')).toBeVisible();
    await waitFor(() => {
      expect(screen.queryByText(/Rua Fictícia/)).not.toBeInTheDocument();
    });
    expect(screen.queryByText('Local não encontrado.')).not.toBeInTheDocument();
  });

  it('shows an error instead of sessions when the city time zone is unavailable', async () => {
    server.use(
      http.get(apiUrl('/cities/:uf/:slug'), () =>
        problem(404, 'Cidade não encontrada.'),
      ),
    );
    renderApp(DETAIL);

    await screen.findByRole('heading', { name: 'Show Teste' });
    const alerts = await screen.findAllByText('Cidade não encontrada.');
    expect(alerts.length).toBeGreaterThan(0);
    expect(screen.queryByText(/1 de mar\. de 2099/)).not.toBeInTheDocument();
  });
});
