import createClient from 'openapi-fetch';
import type { paths } from './schema';

// Absolute URL on the current origin: same behavior in the browser (Vite proxy
// handles /api) and in jsdom, where fetch rejects relative URLs.
export const client = createClient<paths>({
  baseUrl: new URL('/api/v1', window.location.origin).href,
  // Resolve fetch per call so test mocks installed after import still apply.
  fetch: (request) => globalThis.fetch(request),
});
