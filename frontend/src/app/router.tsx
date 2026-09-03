import { Routes, Route, Navigate } from 'react-router-dom';
import { MainLayout } from '@/layouts/MainLayout';
import { CatalogsPage } from '@/features/catalogs/pages/CatalogsPage';
import { AssetsPage } from '@/features/assets/pages/AssetsPage';

function AppRoutes() {
  return (
    <Routes>
      <Route path="/" element={<MainLayout />}>
        <Route index element={<Navigate to="/catalogs" replace />} />
        <Route path="catalogs" element={<CatalogsPage />} />
        <Route path="assets" element={<AssetsPage />} />
        <Route path="*" element={<Navigate to="/catalogs" replace />} />
      </Route>
    </Routes>
  );
}

export { AppRoutes };
