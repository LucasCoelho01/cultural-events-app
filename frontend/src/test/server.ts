import { http, HttpResponse } from 'msw';
import { setupServer } from 'msw/node';
import type { Event } from '../api/events';

// Fictional city: tests must never depend on a real one.
export const testCity = {
  id: 1,
  ibgeCode: 1,
  uf: 'ZZ',
  name: 'Cidade Teste',
  slug: 'cidade-teste',
  timezone: 'America/Recife',
};

export const testVenue = {
  id: '11111111-1111-4111-8111-111111111111',
  cityId: 1,
  name: 'Teatro Teste',
  slug: 'teatro-teste',
  addressLine: 'Rua Fictícia, 100',
  neighborhood: 'Bairro Teste',
};

/** 2099-03-01T20:00Z is 17:00 in America/Recife (UTC-3). */
export const FUTURE = '2099-03-01T20:00:00Z';
export const PAST = '2001-03-01T20:00:00Z';

export function makeEvent(overrides: Partial<Event> = {}): Event {
  return {
    id: '22222222-2222-4222-8222-222222222222',
    cityId: testCity.id,
    title: 'Show Teste',
    slug: 'show-teste',
    description: 'Um show de teste.',
    status: 'PUBLISHED',
    ageRating: '14 anos',
    priceMinCents: 2500,
    priceMaxCents: 8000,
    isFree: false,
    ticketUrl: 'https://ingressos.example.com/show-teste',
    imageUrl: null,
    origin: 'MANUAL',
    version: 0,
    createdAt: PAST,
    updatedAt: PAST,
    category: { id: 1, slug: 'shows', name: 'Shows' },
    venue: { id: testVenue.id, name: testVenue.name, slug: testVenue.slug },
    occurrences: [{ startsAt: FUTURE, endsAt: null, status: 'SCHEDULED' }],
    ...overrides,
  };
}

export function eventPage(items: Event[], page = 0, totalPages = 1) {
  return {
    items,
    page: {
      page,
      size: 12,
      totalElements: items.length,
      totalPages,
    },
  };
}

export const apiUrl = (path: string) => `*/api/v1${path}`;

export const problem = (status: number, detail: string) =>
  HttpResponse.json(
    { title: 'Erro', status, detail },
    { status, headers: { 'Content-Type': 'application/problem+json' } },
  );

export const server = setupServer(
  http.get(apiUrl('/cities/default'), () => HttpResponse.json(testCity)),
  http.get(apiUrl('/cities/:uf/:slug'), () => HttpResponse.json(testCity)),
  http.get(apiUrl('/events'), () =>
    HttpResponse.json(eventPage([makeEvent()])),
  ),
  http.get(apiUrl('/cities/:uf/:slug/events/:eventSlug'), () =>
    HttpResponse.json(makeEvent()),
  ),
  http.get(apiUrl('/venues/:id'), () => HttpResponse.json(testVenue)),
);
