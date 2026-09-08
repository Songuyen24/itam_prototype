import { Routes, Route, Navigate } from 'react-router-dom';
import { MainLayout } from '@/layouts/MainLayout';
import { CatalogsPage } from '@/features/catalogs/pages/CatalogsPage';
import { AssetsPage } from '@/features/assets/pages/AssetsPage';
import LoginPage from '@/features/auth/pages/LoginPage';
import { ProtectedRoute } from '@/features/auth/components/ProtectedRoute';
import { useAuth } from '@/features/auth/contexts/AuthContext';
import { AccountPage } from '@/features/auth/pages/AccountPage';
import { getHomePath, INVENTORY_ROLES } from '@/features/auth/permissions';
import { DocumentsPage } from '@/features/documents/pages/DocumentsPage';
import { DOCUMENT_ROLES } from '@/features/documents/types/document.types';

function AppRoutes() {
  const { user, token } = useAuth();
  const homePath = getHomePath(user?.role);

  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route
        path="/"
        element={
          <ProtectedRoute>
            <MainLayout key={`${token}:${user?.id}:${user?.role}`} />
          </ProtectedRoute>
        }
      >
        <Route index element={<Navigate to={homePath} replace />} />
        <Route
          path="catalogs"
          element={
            <ProtectedRoute allowedRoles={INVENTORY_ROLES}>
              <CatalogsPage />
            </ProtectedRoute>
          }
        />
        <Route
          path="assets"
          element={
            <ProtectedRoute allowedRoles={INVENTORY_ROLES}>
              <AssetsPage />
            </ProtectedRoute>
          }
        />
        <Route
          path="my-assets"
          element={
            <ProtectedRoute allowedRoles={['USER']}>
              <AssetsPage myAssets />
            </ProtectedRoute>
          }
        />
        <Route
          path="documents"
          element={
            <ProtectedRoute allowedRoles={DOCUMENT_ROLES}>
              <DocumentsPage />
            </ProtectedRoute>
          }
        />
        <Route path="account" element={<AccountPage />} />
        <Route path="*" element={<Navigate to={homePath} replace />} />
      </Route>
    </Routes>
  );
}

export { AppRoutes };
