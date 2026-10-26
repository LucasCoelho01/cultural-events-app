import { Route, Routes } from 'react-router';
import { CityPage } from './components/CityPage';
import { Layout } from './components/Layout';
import { NotFound } from './components/NotFound';
import { RedirectToDefaultCity } from './components/RedirectToDefaultCity';

export function App() {
  return (
    <Routes>
      <Route element={<Layout />}>
        <Route index element={<RedirectToDefaultCity />} />
        <Route path=":uf/:cidade" element={<CityPage />} />
        <Route path="*" element={<NotFound />} />
      </Route>
    </Routes>
  );
}
