import { ReportsPage } from '@/features/reports/ReportsPage';
import { HandoversPage } from '@/features/handover/pages/HandoversPage';
import { Routes, Route, Navigate, useLocation } from 'react-router-dom';
import { MainLayout } from '@/layouts/MainLayout';
import { CatalogsPage } from '@/features/catalogs/pages/CatalogsPage';
import { AssetsPage } from '@/features/assets/pages/AssetsPage';
import LoginPage from '@/features/auth/pages/LoginPage';
import { ProtectedRoute } from '@/features/auth/components/ProtectedRoute';
import { useAuth } from '@/features/auth/contexts/AuthContext';
import { AccountsPage } from '@/features/auth/pages/AccountsPage';
import { RecoveriesPage } from '@/features/recovery/pages/RecoveriesPage';
import { getHomePath, INVENTORY_ROLES } from '@/features/auth/permissions';
import { DocumentsPage } from '@/features/documents/pages/DocumentsPage';
import { DOCUMENT_ROLES } from '@/features/documents/types/document.types';
import { DisposalsPage } from '@/features/disposal/pages/DisposalsPage';
import { DashboardPage } from '@/features/dashboard/pages/DashboardPage';
import { AuditLogsPage } from '@/features/audit/AuditLogsPage';

function LegacyProfilesRedirect() {
  const { search } = useLocation();
  return <Navigate to={`/accounts${search}`} replace />;
}

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
              <CatalogsPage key="asset-catalogs" />
            </ProtectedRoute>
          }
        />
        <Route path="organization-catalogs" element={<ProtectedRoute allowedRoles={INVENTORY_ROLES}><CatalogsPage key="organization-catalogs" mode="organization" /></ProtectedRoute>} />
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
        <Route path="receivings" element={<ProtectedRoute allowedRoles={DOCUMENT_ROLES}><DocumentsPage receiving /></ProtectedRoute>} />
        <Route path="handovers" element={<ProtectedRoute allowedRoles={INVENTORY_ROLES}><HandoversPage /></ProtectedRoute>} />
        <Route path="recoveries" element={<ProtectedRoute allowedRoles={INVENTORY_ROLES}><RecoveriesPage /></ProtectedRoute>} />
        <Route path="disposals" element={<ProtectedRoute allowedRoles={INVENTORY_ROLES}><DisposalsPage /></ProtectedRoute>} />
        <Route path="reports" element={<ProtectedRoute allowedRoles={['ADMIN', 'IT_STAFF', 'PUR_STAFF']}><ReportsPage /></ProtectedRoute>} />
        <Route path="dashboard" element={<ProtectedRoute allowedRoles={INVENTORY_ROLES}><DashboardPage /></ProtectedRoute>} />
        <Route path="account" element={<Navigate to={homePath} replace />} />
        <Route path="employee-profiles" element={<ProtectedRoute allowedRoles={['ADMIN', 'IT_STAFF', 'PUR_STAFF']}><LegacyProfilesRedirect /></ProtectedRoute>} />
        <Route path="accounts" element={<ProtectedRoute allowedRoles={['ADMIN', 'IT_STAFF', 'PUR_STAFF']}><AccountsPage /></ProtectedRoute>} />
        <Route path="audit-logs" element={<ProtectedRoute allowedRoles={['ADMIN']}><AuditLogsPage /></ProtectedRoute>} />
        <Route path="user-access" element={<ProtectedRoute allowedRoles={['ADMIN']}><Navigate to="/accounts" replace /></ProtectedRoute>} />
        <Route path="*" element={<Navigate to={homePath} replace />} />
      </Route>
    </Routes>
  );
}

export { AppRoutes };
