import { http, HttpResponse } from 'msw';
import { setupServer } from 'msw/node';

// Fictional city: tests must never depend on a real one.
export const testCity = {
  id: 1,
  ibgeCode: 1,
  uf: 'ZZ',
  name: 'Cidade Teste',
  slug: 'cidade-teste',
  timezone: 'America/Recife',
};

export const apiUrl = (path: string) => `*/api/v1${path}`;

export const problem = (status: number, detail: string) =>
  HttpResponse.json(
    { title: 'Erro', status, detail },
    { status, headers: { 'Content-Type': 'application/problem+json' } },
  );

export const server = setupServer(
  http.get(apiUrl('/cities/default'), () => HttpResponse.json(testCity)),
  http.get(apiUrl('/cities/:uf/:slug'), () => HttpResponse.json(testCity)),
);
