import { useQueryClient } from '@tanstack/react-query';
import { useParams } from 'react-router';
import { useDefaultCity } from '../api/cities';
import { eventKeys, useEvent, type Event } from '../api/events';
import { ErrorMessage } from '../components/ErrorMessage';
import { EventForm } from './EventForm';

export const OTHER_CITY_MESSAGE =
  'Este evento pertence a outra cidade e não pode ser editado aqui.';

export function NewEventPage() {
  const city = useDefaultCity();
  if (city.error) return <ErrorMessage error={city.error} />;
  if (!city.data) return <p aria-busy="true">Carregando…</p>;
  return (
    <EventForm
      cityId={city.data.id}
      timeZone={city.data.timezone}
      uf={city.data.uf}
      slug={city.data.slug}
    />
  );
}

export function EditEventPage() {
  const { id } = useParams();
  const queryClient = useQueryClient();
  const city = useDefaultCity();
  const event = useEvent(id);

  const error = city.error ?? event.error;
  if (error) return <ErrorMessage error={error} />;
  if (!city.data || !event.data) return <p aria-busy="true">Carregando…</p>;

  // The form only edits events of the default city (its venues and time zone).
  if (event.data.cityId !== city.data.id) {
    return (
      <ErrorMessage
        error={{
          title: 'Outra cidade',
          status: 403,
          detail: OTHER_CITY_MESSAGE,
        }}
      />
    );
  }

  const reload = (latest: Event) =>
    queryClient.setQueryData(eventKeys.one(latest.id), latest);

  return (
    // The key remounts the form (and its state) whenever the loaded version changes.
    <EventForm
      key={`${event.data.id}:${event.data.version}`}
      event={event.data}
      cityId={city.data.id}
      timeZone={city.data.timezone}
      uf={city.data.uf}
      slug={city.data.slug}
      onReload={reload}
    />
  );
}
