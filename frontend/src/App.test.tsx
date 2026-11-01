import { screen } from '@testing-library/react';
import { http } from 'msw';
import { describe, expect, it } from 'vitest';
import { ErrorMessage } from './components/ErrorMessage';
import { render } from '@testing-library/react';
import { apiUrl, problem, server, testCity } from './test/server';
import { renderApp } from './test/render';

describe('layout', () => {
  it('shows the app name and the default city in the header', async () => {
    renderApp('/zz/cidade-teste');
    expect(
      screen.getByRole('img', { name: 'Cultural Events' }),
    ).toBeInTheDocument();
    expect(await screen.findByText('Cidade Teste - ZZ')).toBeInTheDocument();
  });

  it('shows the Problem detail in the header when the city fails', async () => {
    server.use(
      http.get(apiUrl('/cities/:uf/:slug'), () =>
        problem(404, 'Cidade não encontrada.'),
      ),
    );
    renderApp('/xx/inexistente');
    const alerts = await screen.findAllByRole('alert');
    expect(alerts[0]).toHaveTextContent('Cidade não encontrada.');
  });

  it('shows the 404 page for unknown routes', async () => {
    renderApp('/a/b/c/d');
    expect(
      await screen.findByRole('heading', { name: 'Página não encontrada' }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole('img', { name: 'Cultural Events' }),
    ).toBeInTheDocument();
  });
});

describe('redirect from /', () => {
  it('navigates to the default city route', async () => {
    renderApp('/');
    expect(
      await screen.findByRole('heading', {
        name: `Eventos em ${testCity.name}`,
      }),
    ).toBeInTheDocument();
  });

  it('shows the Problem detail and does not redirect on failure', async () => {
    server.use(
      http.get(apiUrl('/cities/default'), () =>
        problem(500, 'Cidade padrão inválida.'),
      ),
    );
    renderApp('/');
    const alerts = await screen.findAllByRole('alert');
    expect(alerts[0]).toHaveTextContent('Cidade padrão inválida.');
    expect(screen.queryByRole('heading')).not.toBeInTheDocument();
  });
});

describe('ErrorMessage', () => {
  it('renders the detail of a Problem', () => {
    render(
      <ErrorMessage error={{ title: 'x', status: 500, detail: 'Falhou.' }} />,
    );
    expect(screen.getByRole('alert')).toHaveTextContent('Falhou.');
  });

  it('falls back to a generic message for non-Problem errors', () => {
    render(<ErrorMessage error={new TypeError('Failed to fetch')} />);
    const alert = screen.getByRole('alert');
    expect(alert).toHaveTextContent('Não foi possível concluir a operação');
    expect(alert).not.toHaveTextContent('Failed to fetch');
  });
});
