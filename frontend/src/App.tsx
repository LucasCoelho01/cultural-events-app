import { Route, Routes } from 'react-router';
import { AdminEventsPage } from './admin/AdminEventsPage';
import { AdminRunDetailPage } from './admin/AdminRunDetailPage';
import { AdminRunsPage } from './admin/AdminRunsPage';
import { EditEventPage, NewEventPage } from './admin/EventFormPage';
import { CityPage } from './components/CityPage';
import { EventPage } from './components/EventPage';
import { Layout } from './components/Layout';
import { NotFound } from './components/NotFound';
import { RedirectToDefaultCity } from './components/RedirectToDefaultCity';

export function App() {
  return (
    <Routes>
      <Route element={<Layout />}>
        <Route index element={<RedirectToDefaultCity />} />
        <Route path="admin/eventos" element={<AdminEventsPage />} />
        <Route path="admin/eventos/novo" element={<NewEventPage />} />
        <Route path="admin/eventos/:id/editar" element={<EditEventPage />} />
        <Route path="admin/execucoes" element={<AdminRunsPage />} />
        <Route path="admin/execucoes/:id" element={<AdminRunDetailPage />} />
        <Route path=":uf/:cidade" element={<CityPage />} />
        <Route path=":uf/:cidade/eventos/:slug" element={<EventPage />} />
        <Route path="*" element={<NotFound />} />
      </Route>
    </Routes>
  );
}
