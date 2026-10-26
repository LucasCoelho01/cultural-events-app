import { useQuery } from '@tanstack/react-query';
import { client } from './client';

export const cityKeys = {
  default: ['cities', 'default'] as const,
  detail: (uf: string, slug: string) => ['cities', uf, slug] as const,
};

export function useDefaultCity() {
  return useQuery({
    queryKey: cityKeys.default,
    queryFn: async () => {
      const { data, error } = await client.GET('/cities/default');
      if (error) throw error;
      return data;
    },
  });
}

export function useCity(uf: string | undefined, slug: string | undefined) {
  return useQuery({
    queryKey: cityKeys.detail(uf ?? '', slug ?? ''),
    enabled: Boolean(uf && slug),
    queryFn: async () => {
      const { data, error } = await client.GET('/cities/{uf}/{slug}', {
        params: { path: { uf: uf!, slug: slug! } },
      });
      if (error) throw error;
      return data;
    },
  });
}
