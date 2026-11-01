import { fireEvent, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import { renderApp } from '../test/render';
import { apiUrl, eventPage, makeEvent, problem, server } from '../test/server';

const LIST = '/zz/cidade-teste';
const DETAIL = '/zz/cidade-teste/eventos/show-teste';
const logo = () => screen.getByRole('link', { name: 'Cultural Events' });

describe('logo link', () => {
  it('goes to the current city home on a city route', async () => {
    renderApp(DETAIL);
    await screen.findByRole('heading', { name: 'Show Teste' });
    expect(logo()).toHaveAttribute('href', '/zz/cidade-teste');
  });

  it('goes to "/" outside a city route', async () => {
    renderApp('/admin/eventos');
    expect(logo()).toHaveAttribute('href', '/');
  });

  it('stays in the header when the city does not exist', async () => {
    server.use(
      http.get(apiUrl('/cities/:uf/:slug'), () =>
        problem(404, 'Cidade não encontrada'),
      ),
    );
    renderApp(LIST);
    expect(
      await screen.findAllByText(/Cidade não encontrada/),
    ).not.toHaveLength(0);
    expect(logo()).toBeInTheDocument();
  });
});

describe('event card image', () => {
  it('renders a lazy image with the title as alt', async () => {
    server.use(
      http.get(apiUrl('/events'), () =>
        HttpResponse.json(
          eventPage([makeEvent({ imageUrl: 'https://img.example.com/a.jpg' })]),
        ),
      ),
    );
    renderApp(LIST);
    const card = await screen.findByRole('link', { name: /Show Teste/ });
    const img = within(card).getByRole('img', { name: 'Show Teste' });
    expect(img).toHaveAttribute('loading', 'lazy');
    fireEvent.error(img);
    expect(within(card).getByTestId('image-placeholder')).toBeInTheDocument();
  });

  it('shows the placeholder without imageUrl', async () => {
    server.use(
      http.get(apiUrl('/events'), () =>
        HttpResponse.json(eventPage([makeEvent({ imageUrl: null })])),
      ),
    );
    renderApp(LIST);
    const card = await screen.findByRole('link', { name: /Show Teste/ });
    expect(within(card).getByTestId('image-placeholder')).toBeInTheDocument();
    expect(card).toHaveTextContent('Shows');
  });
});

describe('back button', () => {
  it('points to the list origin kept in the link state', async () => {
    renderApp(DETAIL, { from: '/zz/cidade-teste?q=jazz&page=2' });
    const back = await screen.findByRole('link', {
      name: '← Voltar para os eventos',
    });
    expect(back).toHaveAttribute('href', '/zz/cidade-teste?q=jazz&page=2');
  });

  it('points to the city home on direct access', async () => {
    renderApp(DETAIL);
    const back = await screen.findByRole('link', {
      name: '← Voltar para os eventos',
    });
    expect(back).toHaveAttribute('href', '/zz/cidade-teste');
  });

  it.each(['https://outro.site', '//outro.site', '', 42])(
    'ignores an invalid origin (%s)',
    async (from) => {
      renderApp(DETAIL, { from });
      const back = await screen.findByRole('link', {
        name: '← Voltar para os eventos',
      });
      expect(back).toHaveAttribute('href', '/zz/cidade-teste');
    },
  );

  it('keeps filters and page when going list -> event -> back', async () => {
    const user = userEvent.setup();
    server.use(
      http.get(apiUrl('/events'), () =>
        HttpResponse.json(eventPage([makeEvent()], 1, 3)),
      ),
    );
    renderApp(`${LIST}?q=show&page=2`);
    await user.click(await screen.findByRole('link', { name: /Show Teste/ }));
    const back = await screen.findByRole('link', {
      name: '← Voltar para os eventos',
    });
    expect(back).toHaveAttribute('href', '/zz/cidade-teste?q=show&page=2');
    await user.click(back);
    expect(screen.getByTestId('location')).toHaveTextContent(
      '/zz/cidade-teste?q=show&page=2',
    );
  });
});

describe('event banner', () => {
  it('shows the placeholder instead of the banner without imageUrl', async () => {
    renderApp(DETAIL);
    await screen.findByRole('heading', { name: 'Show Teste' });
    expect(screen.getByTestId('image-placeholder')).toBeInTheDocument();
  });
});
