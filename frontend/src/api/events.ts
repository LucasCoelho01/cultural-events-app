import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { client } from './client';
import type { components } from './schema';

export type Event = components['schemas']['Event'];

export const EVENTS_PAGE_SIZE = 12;

export const eventKeys = {
  list: (uf: string, slug: string, page: number, from: string) =>
    ['events', 'list', uf, slug, page, from] as const,
  detail: (uf: string, slug: string, eventSlug: string) =>
    ['events', 'detail', uf, slug, eventSlug] as const,
};

/** Current instant, rounded down to the minute, fixed for the component's lifetime. */
function useFrom(): string {
  const [from] = useState(() => {
    const now = new Date();
    now.setSeconds(0, 0);
    return now.toISOString();
  });
  return from;
}

/** Published events of a city with future sessions; `page` is zero-based, as in the API. */
export function useEvents(
  uf: string | undefined,
  slug: string | undefined,
  page: number,
) {
  const from = useFrom();
  return useQuery({
    queryKey: eventKeys.list(uf ?? '', slug ?? '', page, from),
    enabled: Boolean(uf && slug),
    placeholderData: keepPreviousData,
    queryFn: async () => {
      const { data, error } = await client.GET('/events', {
        params: {
          query: {
            city: `${uf}/${slug}`,
            status: 'PUBLISHED',
            from,
            page,
            size: EVENTS_PAGE_SIZE,
          },
        },
      });
      if (error) throw error;
      return data;
    },
  });
}

export function usePublishedEvent(
  uf: string | undefined,
  slug: string | undefined,
  eventSlug: string | undefined,
) {
  return useQuery({
    queryKey: eventKeys.detail(uf ?? '', slug ?? '', eventSlug ?? ''),
    enabled: Boolean(uf && slug && eventSlug),
    queryFn: async () => {
      const { data, error } = await client.GET(
        '/cities/{uf}/{slug}/events/{eventSlug}',
        {
          params: {
            path: { uf: uf!, slug: slug!, eventSlug: eventSlug! },
          },
        },
      );
      if (error) throw error;
      return data;
    },
  });
}
