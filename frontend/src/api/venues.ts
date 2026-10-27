import { useQuery } from '@tanstack/react-query';
import { client } from './client';

export function useVenue(id: string | undefined) {
  return useQuery({
    queryKey: ['venues', id ?? ''] as const,
    enabled: Boolean(id),
    queryFn: async () => {
      const { data, error } = await client.GET('/venues/{id}', {
        params: { path: { id: id! } },
      });
      if (error) throw error;
      return data;
    },
  });
}

/** Venues of a city for the admin select; the API caps a page at 100. */
export function useVenues(uf: string | undefined, slug: string | undefined) {
  return useQuery({
    queryKey: ['venues', 'city', uf ?? '', slug ?? ''] as const,
    enabled: Boolean(uf && slug),
    queryFn: async () => {
      const { data, error } = await client.GET('/venues', {
        params: { query: { city: `${uf}/${slug}`, size: 100 } },
      });
      if (error) throw error;
      return data;
    },
  });
}
