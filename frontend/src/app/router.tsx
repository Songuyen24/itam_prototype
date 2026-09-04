import { Routes, Route, Navigate } from 'react-router-dom';
import { MainLayout } from '@/layouts/MainLayout';
import { CatalogsPage } from '@/features/catalogs/pages/CatalogsPage';
import { AssetsPage } from '@/features/assets/pages/AssetsPage';
import LoginPage from '@/features/auth/pages/LoginPage';
import { ProtectedRoute } from '@/features/auth/components/ProtectedRoute';

function AppRoutes() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route
        path="/"
        element={
          <ProtectedRoute>
            <MainLayout />
          </ProtectedRoute>
        }
      >
        <Route index element={<Navigate to="/catalogs" replace />} />
        <Route path="catalogs" element={<CatalogsPage />} />
        <Route path="assets" element={<AssetsPage />} />
        <Route path="*" element={<Navigate to="/catalogs" replace />} />
      </Route>
    </Routes>
  );
}

export { AppRoutes };
