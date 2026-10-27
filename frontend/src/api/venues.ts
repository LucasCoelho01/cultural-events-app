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
