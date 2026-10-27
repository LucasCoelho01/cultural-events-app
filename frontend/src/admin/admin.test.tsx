import { fireEvent, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { delay, http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import type { Event } from '../api/events';
import { renderApp } from '../test/render';
import {
  apiUrl,
  eventPage,
  makeEvent,
  PAST,
  problem,
  server,
  testCity,
} from '../test/server';

const LIST = '/admin/eventos';
const NEW = '/admin/eventos/novo';
const EVENT_ID = '22222222-2222-4222-8222-222222222222';
const EDIT = `/admin/eventos/${EVENT_ID}/editar`;

type Body = Record<string, unknown>;

/** Records the JSON body of every write to /events and answers with `respond`. */
function recordWrites(
  method: 'post' | 'put',
  respond: () => Response | Promise<Response> = () =>
    HttpResponse.json(makeEvent(), { status: method === 'post' ? 201 : 200 }),
) {
  const bodies: Body[] = [];
  const path = method === 'post' ? '/events' : '/events/:id';
  server.use(
    http[method](apiUrl(path), async ({ request }) => {
      bodies.push((await request.json()) as Body);
      return respond();
    }),
  );
  return bodies;
}

const sessionEvent = (overrides: Partial<Event> = {}) =>
  makeEvent({
    version: 3,
    occurrences: [
      {
        startsAt: '2099-03-01T20:00:00Z',
        endsAt: '2099-03-01T22:00:00Z',
        status: 'SOLD_OUT',
      },
      { startsAt: '2099-04-01T20:00:00Z', endsAt: null, status: 'SCHEDULED' },
    ],
    ...overrides,
  });

async function formReady() {
  await screen.findByLabelText('Título');
}

describe('admin table', () => {
  it('lists every status without sending the status filter', async () => {
    const queries: URLSearchParams[] = [];
    server.use(
      http.get(apiUrl('/events'), ({ request }) => {
        queries.push(new URL(request.url).searchParams);
        return HttpResponse.json(
          eventPage([
            makeEvent({ id: 'a', title: 'Um rascunho', status: 'DRAFT' }),
            makeEvent({ id: 'b', title: 'Um publicado', status: 'PUBLISHED' }),
            makeEvent({ id: 'c', title: 'Um cancelado', status: 'CANCELLED' }),
            makeEvent({ id: 'd', title: 'Um arquivado', status: 'ARCHIVED' }),
          ]),
        );
      }),
    );
    renderApp(LIST);

    const row = (await screen.findByText('Um rascunho')).closest('tr')!;
    expect(row).toHaveTextContent('Rascunho');
    expect(row).toHaveTextContent(/1 de mar\. de 2099/);
    expect(row).toHaveTextContent('17:00');
    for (const [title, label] of [
      ['Um publicado', 'Publicado'],
      ['Um cancelado', 'Cancelado'],
      ['Um arquivado', 'Arquivado'],
    ]) {
      expect(screen.getByText(title).closest('tr')).toHaveTextContent(label);
    }
    expect(queries[0].has('status')).toBe(false);
    expect(queries[0].get('page')).toBe('0');
  });

  it('flags events without a future session', async () => {
    server.use(
      http.get(apiUrl('/events'), () =>
        HttpResponse.json(
          eventPage([
            makeEvent({
              title: 'Evento Antigo',
              occurrences: [
                { startsAt: PAST, endsAt: null, status: 'SCHEDULED' },
              ],
            }),
          ]),
        ),
      ),
    );
    renderApp(LIST);

    const row = (await screen.findByText('Evento Antigo')).closest('tr')!;
    expect(row).toHaveTextContent('Sem sessões futuras');
  });

  it('shows the empty state and keeps the link to create an event', async () => {
    server.use(
      http.get(apiUrl('/events'), () => HttpResponse.json(eventPage([]))),
    );
    renderApp(LIST);

    expect(await screen.findByText('Nenhum evento encontrado.')).toBeVisible();
    expect(screen.getByRole('link', { name: 'Novo evento' })).toHaveAttribute(
      'href',
      '/admin/eventos/novo',
    );
  });

  it('shows the Problem detail on API errors', async () => {
    server.use(
      http.get(apiUrl('/events'), () => problem(500, 'Falha ao listar.')),
    );
    renderApp(LIST);

    expect(await screen.findByText('Falha ao listar.')).toBeVisible();
    expect(screen.queryByRole('table')).not.toBeInTheDocument();
  });

  it('shows the Problem detail when the default city is unavailable', async () => {
    server.use(
      http.get(apiUrl('/cities/default'), () =>
        problem(500, 'Cidade padrão inválida.'),
      ),
    );
    renderApp(LIST);

    const alerts = await screen.findAllByText('Cidade padrão inválida.');
    expect(alerts.length).toBeGreaterThan(0);
  });

  it('paginates with ?page= (one-based) and the API page (zero-based)', async () => {
    const pages: string[] = [];
    server.use(
      http.get(apiUrl('/events'), ({ request }) => {
        const page = new URL(request.url).searchParams.get('page') ?? '';
        pages.push(page);
        return HttpResponse.json(
          eventPage([makeEvent({ title: `Evento ${page}` })], Number(page), 2),
        );
      }),
    );
    renderApp(LIST);

    await screen.findByText('Evento 0');
    await userEvent.click(screen.getByRole('button', { name: 'Próxima' }));
    await screen.findByText('Evento 1');
    expect(pages).toContain('1');
  });

  it('is not captured by the public city route', async () => {
    renderApp(LIST);

    expect(
      await screen.findByRole('heading', { name: 'Eventos' }),
    ).toBeVisible();
    expect(screen.queryByRole('heading', { name: /Eventos em/ })).toBeNull();
  });

  it('has a discreet "Admin" link in the header', async () => {
    renderApp('/zz/cidade-teste');

    expect(await screen.findByRole('link', { name: 'Admin' })).toHaveAttribute(
      'href',
      '/admin/eventos',
    );
  });
});

describe('delete with in-page confirmation', () => {
  function stateful(initial: Event[]) {
    const events = [...initial];
    const deleted: string[] = [];
    server.use(
      http.get(apiUrl('/events'), () => HttpResponse.json(eventPage(events))),
      http.delete(apiUrl('/events/:id'), ({ params }) => {
        deleted.push(String(params.id));
        const index = events.findIndex((e) => e.id === params.id);
        if (index >= 0) events.splice(index, 1);
        return new HttpResponse(null, { status: 204 });
      }),
    );
    return deleted;
  }

  const two = [
    makeEvent({ id: 'id-1', title: 'Primeiro' }),
    makeEvent({ id: 'id-2', title: 'Segundo' }),
  ];

  it('deletes only after confirming, and refreshes the table', async () => {
    const deleted = stateful(two);
    renderApp(LIST);

    await userEvent.click(
      await screen.findByRole('button', { name: 'Excluir Primeiro' }),
    );
    expect(deleted).toEqual([]);
    const dialog = screen.getByRole('alertdialog', {
      name: 'Confirmar exclusão de Primeiro',
    });
    await userEvent.click(
      within(dialog).getByRole('button', { name: 'Confirmar' }),
    );

    await waitFor(() => expect(screen.queryByText('Primeiro')).toBeNull());
    expect(deleted).toEqual(['id-1']);
    expect(screen.getByText('Segundo')).toBeVisible();
  });

  it('sends nothing when cancelled', async () => {
    const deleted = stateful(two);
    renderApp(LIST);

    await userEvent.click(
      await screen.findByRole('button', { name: 'Excluir Primeiro' }),
    );
    await userEvent.click(screen.getByRole('button', { name: 'Cancelar' }));

    expect(screen.queryByRole('alertdialog')).toBeNull();
    expect(screen.getByText('Primeiro')).toBeVisible();
    expect(deleted).toEqual([]);
  });

  it('shows the Problem detail and refreshes when the event is already gone', async () => {
    let listed = two;
    server.use(
      http.get(apiUrl('/events'), () => HttpResponse.json(eventPage(listed))),
      http.delete(apiUrl('/events/:id'), () => {
        listed = [two[1]];
        return problem(404, 'Evento não encontrado.');
      }),
    );
    renderApp(LIST);

    await userEvent.click(
      await screen.findByRole('button', { name: 'Excluir Primeiro' }),
    );
    await userEvent.click(screen.getByRole('button', { name: 'Confirmar' }));

    expect(await screen.findByText('Evento não encontrado.')).toBeVisible();
    await waitFor(() => expect(screen.queryByText('Primeiro')).toBeNull());
  });
});

describe('create', () => {
  async function fillValid(user = userEvent.setup()) {
    renderApp(NEW);
    await formReady();
    await user.type(screen.getByLabelText('Título'), 'Show Novo');
    await user.selectOptions(screen.getByLabelText('Categoria'), '1');
    return user;
  }

  it('sends cents, offset ISO and the city id, then returns to the table', async () => {
    const bodies = recordWrites('post');
    const user = await fillValid();

    await user.selectOptions(screen.getByLabelText('Status'), 'PUBLISHED');
    await user.selectOptions(screen.getByLabelText('Local'), 'Teatro Teste');
    await user.type(screen.getByLabelText('Preço mínimo (R$)'), '25,50');
    await user.type(screen.getByLabelText('Preço máximo (R$)'), '80');
    await user.click(screen.getByRole('button', { name: 'Adicionar sessão' }));
    fireEvent.change(screen.getByLabelText('Início da sessão 1'), {
      target: { value: '2099-03-01T17:00' },
    });
    await user.click(screen.getByRole('button', { name: 'Salvar' }));

    expect(
      await screen.findByRole('heading', { name: 'Eventos' }),
    ).toBeVisible();
    expect(bodies).toHaveLength(1);
    expect(bodies[0]).toMatchObject({
      cityId: testCity.id,
      categoryId: 1,
      venueId: '11111111-1111-4111-8111-111111111111',
      title: 'Show Novo',
      slug: null,
      status: 'PUBLISHED',
      isFree: false,
      priceMinCents: 2550,
      priceMaxCents: 8000,
      occurrences: [
        {
          startsAt: '2099-03-01T17:00:00-03:00',
          endsAt: null,
          status: 'SCHEDULED',
        },
      ],
    });
  });

  it('sends isFree with null prices and disables the price fields', async () => {
    const bodies = recordWrites('post');
    const user = await fillValid();

    await user.click(screen.getByLabelText('Gratuito'));
    expect(screen.getByLabelText('Preço mínimo (R$)')).toBeDisabled();
    expect(screen.getByLabelText('Preço máximo (R$)')).toBeDisabled();
    await user.click(screen.getByRole('button', { name: 'Salvar' }));

    await screen.findByRole('heading', { name: 'Eventos' });
    expect(bodies[0]).toMatchObject({
      isFree: true,
      priceMinCents: null,
      priceMaxCents: null,
    });
  });

  it('allows a draft with no sessions', async () => {
    const bodies = recordWrites('post');
    const user = await fillValid();

    await user.click(screen.getByRole('button', { name: 'Salvar' }));

    await screen.findByRole('heading', { name: 'Eventos' });
    expect(bodies[0]).toMatchObject({ status: 'DRAFT', occurrences: [] });
  });

  it('adds and removes sessions', async () => {
    const user = userEvent.setup();
    renderApp(NEW);
    await formReady();

    await user.click(screen.getByRole('button', { name: 'Adicionar sessão' }));
    await user.click(screen.getByRole('button', { name: 'Adicionar sessão' }));
    fireEvent.change(screen.getByLabelText('Início da sessão 2'), {
      target: { value: '2099-05-01T10:00' },
    });
    await user.click(screen.getByRole('button', { name: 'Remover sessão 1' }));

    expect(screen.queryByLabelText('Início da sessão 2')).toBeNull();
    expect(screen.getByLabelText('Início da sessão 1')).toHaveValue(
      '2099-05-01T10:00',
    );
  });

  it('shows the slug conflict detail on top, without the concurrency notice, keeping the typed values', async () => {
    recordWrites('post', () =>
      problem(409, 'Já existe um evento com esse slug na cidade.'),
    );
    const user = await fillValid();

    await user.click(screen.getByRole('button', { name: 'Salvar' }));

    expect(
      await screen.findByText('Já existe um evento com esse slug na cidade.'),
    ).toBeVisible();
    expect(screen.queryByText(/alterado por outra pessoa/)).toBeNull();
    expect(screen.getByLabelText('Título')).toHaveValue('Show Novo');
    expect(screen.getByLabelText('Categoria')).toHaveValue('1');
  });

  it('disables "Salvar" while saving and sends a single request', async () => {
    const bodies = recordWrites('post', async () => {
      await delay(150);
      return HttpResponse.json(makeEvent(), { status: 201 });
    });
    const user = await fillValid();

    await user.click(screen.getByRole('button', { name: 'Salvar' }));
    const saving = await screen.findByRole('button', { name: 'Salvando…' });
    expect(saving).toBeDisabled();
    fireEvent.click(saving);

    await screen.findByRole('heading', { name: 'Eventos' });
    expect(bodies).toHaveLength(1);
  });

  it('shows a generic message when the network fails', async () => {
    recordWrites('post', () => HttpResponse.error());
    const user = await fillValid();

    await user.click(screen.getByRole('button', { name: 'Salvar' }));

    expect(
      await screen.findByText(/Não foi possível concluir a operação/),
    ).toBeVisible();
  });
});

describe('edit', () => {
  function serve(event: Event) {
    server.use(http.get(apiUrl('/events/:id'), () => HttpResponse.json(event)));
  }

  it('fills the form from the event, in the city time zone', async () => {
    serve(sessionEvent());
    renderApp(EDIT);

    await formReady();
    expect(screen.getByLabelText('Título')).toHaveValue('Show Teste');
    expect(screen.getByLabelText('Categoria')).toHaveValue('1');
    expect(screen.getByLabelText('Preço mínimo (R$)')).toHaveValue('25,00');
    expect(screen.getByLabelText('Preço máximo (R$)')).toHaveValue('80,00');
    expect(screen.getByLabelText('Início da sessão 1')).toHaveValue(
      '2099-03-01T17:00',
    );
    expect(screen.getByLabelText('Fim da sessão 1')).toHaveValue(
      '2099-03-01T19:00',
    );
    expect(screen.getByLabelText('Situação da sessão 1')).toHaveValue(
      'SOLD_OUT',
    );
  });

  it('sends the loaded version and keeps end and status of the sessions', async () => {
    serve(sessionEvent());
    const bodies = recordWrites('put');
    const user = userEvent.setup();
    renderApp(EDIT);
    await formReady();

    await user.clear(screen.getByLabelText('Título'));
    await user.type(screen.getByLabelText('Título'), 'Show Editado');
    await user.click(screen.getByRole('button', { name: 'Salvar' }));

    expect(
      await screen.findByRole('heading', { name: 'Eventos' }),
    ).toBeVisible();
    expect(bodies).toHaveLength(1);
    expect(bodies[0]).toMatchObject({
      version: 3,
      title: 'Show Editado',
      status: 'PUBLISHED',
      priceMinCents: 2500,
      priceMaxCents: 8000,
      occurrences: [
        {
          startsAt: '2099-03-01T17:00:00-03:00',
          endsAt: '2099-03-01T19:00:00-03:00',
          status: 'SOLD_OUT',
        },
        {
          startsAt: '2099-04-01T17:00:00-03:00',
          endsAt: null,
          status: 'SCHEDULED',
        },
      ],
    });
  });

  it('shows the Problem detail when the event does not exist', async () => {
    server.use(
      http.get(apiUrl('/events/:id'), () =>
        problem(404, 'Evento não encontrado.'),
      ),
    );
    renderApp(EDIT);

    expect(await screen.findByText('Evento não encontrado.')).toBeVisible();
    expect(screen.queryByLabelText('Título')).toBeNull();
  });

  it('does not open the form for an event of another city', async () => {
    serve(sessionEvent({ cityId: testCity.id + 1 }));
    renderApp(EDIT);

    expect(
      await screen.findByText(
        'Este evento pertence a outra cidade e não pode ser editado aqui.',
      ),
    ).toBeVisible();
    expect(screen.queryByLabelText('Título')).toBeNull();
    expect(screen.queryByRole('button', { name: 'Salvar' })).toBeNull();
  });

  it('shows the concurrency notice on a 409 with a newer version, and reloads on demand', async () => {
    let current = sessionEvent();
    server.use(
      http.get(apiUrl('/events/:id'), () => HttpResponse.json(current)),
    );
    const bodies = recordWrites('put', () =>
      problem(409, 'A versão informada está desatualizada.'),
    );
    const user = userEvent.setup();
    renderApp(EDIT);
    await formReady();

    await user.clear(screen.getByLabelText('Título'));
    await user.type(screen.getByLabelText('Título'), 'Minha edição');
    current = sessionEvent({ version: 4, title: 'Edição de outra pessoa' });
    await user.click(screen.getByRole('button', { name: 'Salvar' }));

    expect(
      await screen.findByText('O evento foi alterado por outra pessoa.'),
    ).toBeVisible();
    expect(screen.getByLabelText('Título')).toHaveValue('Minha edição');

    await user.click(screen.getByRole('button', { name: 'Recarregar' }));
    await waitFor(() =>
      expect(screen.getByLabelText('Título')).toHaveValue(
        'Edição de outra pessoa',
      ),
    );
    expect(screen.queryByText(/alterado por outra pessoa/)).toBeNull();

    // The next save uses the reloaded version.
    const next = recordWrites('put');
    await user.click(screen.getByRole('button', { name: 'Salvar' }));
    await screen.findByRole('heading', { name: 'Eventos' });
    expect(bodies).toHaveLength(1);
    expect(next[0]).toMatchObject({ version: 4 });
  });

  it('shows the detail on top for a 409 that is not a version conflict', async () => {
    serve(sessionEvent());
    recordWrites('put', () =>
      problem(409, 'Já existe um evento com esse slug na cidade.'),
    );
    const user = userEvent.setup();
    renderApp(EDIT);
    await formReady();

    await user.click(screen.getByRole('button', { name: 'Salvar' }));

    expect(
      await screen.findByText('Já existe um evento com esse slug na cidade.'),
    ).toBeVisible();
    expect(screen.queryByText(/alterado por outra pessoa/)).toBeNull();
    expect(screen.getByLabelText('Título')).toHaveValue('Show Teste');
  });
});

describe('422 mapping', () => {
  const unprocessable = (errors: { field: string; message: string }[]) =>
    HttpResponse.json(
      {
        title: 'Unprocessable Entity',
        status: 422,
        detail: 'A requisição contém campos inválidos.',
        errors,
      },
      { status: 422, headers: { 'Content-Type': 'application/problem+json' } },
    );

  const fieldOf = (label: string) =>
    screen.getByLabelText(label).closest('div') as HTMLElement;

  it('puts each error next to its field and the rest on top', async () => {
    server.use(
      http.get(apiUrl('/events/:id'), () => HttpResponse.json(sessionEvent())),
    );
    recordWrites('put', () =>
      unprocessable([
        { field: 'title', message: 'o título não gera um slug válido' },
        {
          field: 'priceMaxCents',
          message: 'o preço máximo não pode ser menor',
        },
        { field: 'occurrences[1].endsAt', message: 'fim antes do início' },
        { field: 'cityId', message: 'a cidade informada está inativa' },
      ]),
    );
    const user = userEvent.setup();
    renderApp(EDIT);
    await formReady();

    await user.click(screen.getByRole('button', { name: 'Salvar' }));

    expect(
      await screen.findByText('A requisição contém campos inválidos.'),
    ).toBeVisible();
    expect(fieldOf('Título')).toHaveTextContent(
      'o título não gera um slug válido',
    );
    expect(fieldOf('Preço máximo (R$)')).toHaveTextContent(
      'o preço máximo não pode ser menor',
    );
    expect(fieldOf('Fim da sessão 2')).toHaveTextContent('fim antes do início');
    const top = screen.getByRole('alert');
    expect(top).toHaveTextContent('cityId: a cidade informada está inativa');
    expect(top).not.toHaveTextContent('o título não gera');
    expect(screen.getByLabelText('Título')).toHaveValue('Show Teste');
  });

  it('maps an "occurrences" error to the session list', async () => {
    recordWrites('post', () =>
      unprocessable([
        {
          field: 'occurrences',
          message: 'um evento publicado precisa de ao menos uma sessão futura',
        },
      ]),
    );
    const user = userEvent.setup();
    renderApp(NEW);
    await formReady();
    await user.type(screen.getByLabelText('Título'), 'Show');
    await user.selectOptions(screen.getByLabelText('Categoria'), '1');
    await user.click(screen.getByRole('button', { name: 'Salvar' }));

    expect(
      await screen.findByText(
        'um evento publicado precisa de ao menos uma sessão futura',
      ),
    ).toBeVisible();
  });
});

describe('client-side validation', () => {
  async function submitWith(
    setup: (user: ReturnType<typeof userEvent.setup>) => Promise<void> | void,
  ) {
    const bodies = recordWrites('post');
    const user = userEvent.setup();
    renderApp(NEW);
    await formReady();
    await setup(user);
    await user.click(screen.getByRole('button', { name: 'Salvar' }));
    return bodies;
  }

  it('requires title and category and sends nothing', async () => {
    const bodies = await submitWith(() => {});

    expect(await screen.findByText('Informe o título.')).toBeVisible();
    expect(screen.getByText('Selecione a categoria.')).toBeVisible();
    expect(bodies).toHaveLength(0);
  });

  it('rejects a minimum price above the maximum', async () => {
    const bodies = await submitWith(async (user) => {
      await user.type(screen.getByLabelText('Título'), 'Show');
      await user.selectOptions(screen.getByLabelText('Categoria'), '1');
      await user.type(screen.getByLabelText('Preço mínimo (R$)'), '90');
      await user.type(screen.getByLabelText('Preço máximo (R$)'), '50');
    });

    expect(
      await screen.findByText(
        'O preço máximo não pode ser menor que o mínimo.',
      ),
    ).toBeVisible();
    expect(bodies).toHaveLength(0);
  });

  it.each(['abc', '10,999'])('rejects the price "%s"', async (value) => {
    const bodies = await submitWith(async (user) => {
      await user.type(screen.getByLabelText('Título'), 'Show');
      await user.selectOptions(screen.getByLabelText('Categoria'), '1');
      await user.type(screen.getByLabelText('Preço mínimo (R$)'), value);
    });

    expect(await screen.findByText(/Informe um valor em reais/)).toBeVisible();
    expect(bodies).toHaveLength(0);
  });

  it('requires a future session to publish', async () => {
    const bodies = await submitWith(async (user) => {
      await user.type(screen.getByLabelText('Título'), 'Show');
      await user.selectOptions(screen.getByLabelText('Categoria'), '1');
      await user.selectOptions(screen.getByLabelText('Status'), 'PUBLISHED');
    });

    expect(
      await screen.findByText(/precisa de ao menos uma sessão futura/),
    ).toBeVisible();
    expect(bodies).toHaveLength(0);
  });

  it('flags repeated session starts and an end before the start', async () => {
    const bodies = await submitWith(async (user) => {
      await user.type(screen.getByLabelText('Título'), 'Show');
      await user.selectOptions(screen.getByLabelText('Categoria'), '1');
      await user.click(
        screen.getByRole('button', { name: 'Adicionar sessão' }),
      );
      await user.click(
        screen.getByRole('button', { name: 'Adicionar sessão' }),
      );
      for (const n of [1, 2]) {
        fireEvent.change(screen.getByLabelText(`Início da sessão ${n}`), {
          target: { value: '2099-03-01T17:00' },
        });
      }
      fireEvent.change(screen.getByLabelText('Fim da sessão 1'), {
        target: { value: '2099-03-01T16:00' },
      });
    });

    expect(
      await screen.findAllByText('Já existe uma sessão com esse início.'),
    ).toHaveLength(2);
    expect(
      screen.getByText('O fim da sessão não pode ser anterior ao início.'),
    ).toBeVisible();
    expect(bodies).toHaveLength(0);
  });
});

describe('select options', () => {
  it('indents subcategories under their parent', async () => {
    renderApp(NEW);
    await formReady();

    const options = within(screen.getByLabelText('Categoria')).getAllByRole(
      'option',
    );
    const texts = options.map((option) => option.textContent ?? '');
    expect(texts).toEqual(['Selecione…', 'Shows', '    Rock', 'Teatro']);
  });

  it('lists the venues of the default city plus "Sem local"', async () => {
    renderApp(NEW);
    await formReady();

    const options = within(screen.getByLabelText('Local')).getAllByRole(
      'option',
    );
    expect(options.map((o) => o.textContent)).toEqual([
      'Sem local',
      'Teatro Teste',
    ]);
  });

  it('shows the Problem detail and no form when options fail to load', async () => {
    server.use(
      http.get(apiUrl('/categories'), () =>
        problem(500, 'Falha nas categorias.'),
      ),
    );
    renderApp(NEW);

    expect(await screen.findByText('Falha nas categorias.')).toBeVisible();
    expect(screen.queryByRole('button', { name: 'Salvar' })).toBeNull();
  });
});

describe('time zone', () => {
  it('converts session times with the zone of the default city', async () => {
    server.use(
      http.get(apiUrl('/cities/default'), () =>
        HttpResponse.json({ ...testCity, timezone: 'Asia/Kolkata' }),
      ),
    );
    const bodies = recordWrites('post');
    const user = userEvent.setup();
    renderApp(NEW);
    await formReady();
    await user.type(screen.getByLabelText('Título'), 'Show');
    await user.selectOptions(screen.getByLabelText('Categoria'), '1');
    await user.click(screen.getByRole('button', { name: 'Adicionar sessão' }));
    fireEvent.change(screen.getByLabelText('Início da sessão 1'), {
      target: { value: '2099-03-01T08:30' },
    });
    await user.click(screen.getByRole('button', { name: 'Salvar' }));

    await screen.findByRole('heading', { name: 'Eventos' });
    expect(bodies[0]).toMatchObject({
      occurrences: [{ startsAt: '2099-03-01T08:30:00+05:30' }],
    });
  });
});
