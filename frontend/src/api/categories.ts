import { useQuery } from '@tanstack/react-query';
import { client } from './client';
import type { components } from './schema';

export type Category = components['schemas']['Category'];

export type CategoryOption = { id: number; name: string; depth: number };

/** Flattens the category tree depth-first, keeping the depth for indentation. */
export function flattenCategories(
  tree: Category[],
  depth = 0,
): CategoryOption[] {
  return tree.flatMap((category) => [
    { id: category.id, name: category.name, depth },
    ...flattenCategories(category.children, depth + 1),
  ]);
}

export function useCategories() {
  return useQuery({
    queryKey: ['categories'] as const,
    staleTime: 5 * 60 * 1000,
    queryFn: async () => {
      const { data, error } = await client.GET('/categories');
      if (error) throw error;
      return data;
    },
  });
}
