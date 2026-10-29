import {
  keepPreviousData,
  useMutation,
  useQuery,
  useQueryClient,
} from '@tanstack/react-query';
import { useState } from 'react';
import { client } from './client';
import type { components } from './schema';

export type Event = components['schemas']['Event'];

export const EVENTS_PAGE_SIZE = 12;
export const ADMIN_EVENTS_PAGE_SIZE = 20;

export const eventKeys = {
  list: (
    uf: string,
    slug: string,
    page: number,
    from: string,
    to: string,
    category: string,
  ) => ['events', 'list', uf, slug, page, from, to, category] as const,
  admin: (page: number) => ['events', 'admin', page] as const,
  one: (id: string) => ['events', 'one', id] as const,
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

export type EventsFilters = {
  /** Category slug, already confirmed to exist (the API answers 404 otherwise). */
  category?: string;
  /** Sessions window; without it `from` is the current instant and `to` is omitted. */
  range?: { from: string; to?: string };
  /** False holds the request back, e.g. while the filters are still being resolved. */
  enabled?: boolean;
};

/** Published events of a city with future sessions; `page` is zero-based, as in the API. */
export function useEvents(
  uf: string | undefined,
  slug: string | undefined,
  page: number,
  { category, range, enabled = true }: EventsFilters = {},
) {
  const now = useFrom();
  const from = range?.from ?? now;
  const to = range?.to;
  return useQuery({
    queryKey: eventKeys.list(
      uf ?? '',
      slug ?? '',
      page,
      from,
      to ?? '',
      category ?? '',
    ),
    enabled: Boolean(uf && slug) && enabled,
    placeholderData: keepPreviousData,
    queryFn: async () => {
      const { data, error } = await client.GET('/events', {
        params: {
          query: {
            city: `${uf}/${slug}`,
            status: 'PUBLISHED',
            from,
            ...(to && { to }),
            ...(category && { category }),
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

export type EventInput = components['schemas']['EventInput'];
export type EventUpdateInput = components['schemas']['EventUpdateInput'];

/** Events of the default city in every status; `page` is zero-based, as in the API. */
export function useAdminEvents(page: number) {
  return useQuery({
    queryKey: eventKeys.admin(page),
    placeholderData: keepPreviousData,
    queryFn: async () => {
      const { data, error } = await client.GET('/events', {
        params: { query: { page, size: ADMIN_EVENTS_PAGE_SIZE } },
      });
      if (error) throw error;
      return data;
    },
  });
}

/** Any-status event by id, for the edit form. */
export function useEvent(id: string | undefined) {
  return useQuery({
    queryKey: eventKeys.one(id ?? ''),
    enabled: Boolean(id),
    queryFn: async () => {
      const { data, error } = await client.GET('/events/{id}', {
        params: { path: { id: id! } },
      });
      if (error) throw error;
      return data;
    },
  });
}

/** Fresh copy of an event, outside the cache (used to tell a version conflict apart). */
export async function fetchEvent(id: string): Promise<Event> {
  const { data, error } = await client.GET('/events/{id}', {
    params: { path: { id } },
  });
  if (error) throw error;
  return data;
}

export function useCreateEvent() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (body: EventInput) => {
      const { data, error } = await client.POST('/events', { body });
      if (error) throw error;
      return data;
    },
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['events'] }),
  });
}

export function useUpdateEvent() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async ({
      id,
      body,
    }: {
      id: string;
      body: EventUpdateInput;
    }) => {
      const { data, error } = await client.PUT('/events/{id}', {
        params: { path: { id } },
        body,
      });
      if (error) throw error;
      return data;
    },
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['events'] }),
  });
}

export function useDeleteEvent() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (id: string) => {
      const { error } = await client.DELETE('/events/{id}', {
        params: { path: { id } },
      });
      if (error) throw error;
    },
    // Refresh even on failure: a 404 means the row is already gone.
    onSettled: () => queryClient.invalidateQueries({ queryKey: ['events'] }),
  });
}
