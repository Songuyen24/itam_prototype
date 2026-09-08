import React from 'react';
import { Navigate, useLocation } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { useAuth } from '../contexts/AuthContext';
import { getHomePath } from '../permissions';

/**
 * Route guard: nếu chưa đăng nhập thì chuyển hướng về {@code /login}.
 * Dùng để bọc các route yêu cầu xác thực trong cấu hình router.
 */
interface ProtectedRouteProps {
  children: React.ReactNode;
  allowedRoles?: string[];
  redirectTo?: string;
}

export const ProtectedRoute: React.FC<ProtectedRouteProps> = ({
  children,
  allowedRoles,
  redirectTo,
}) => {
  const { isAuthenticated, user, loading } = useAuth();
  const { t } = useTranslation('common');
  const location = useLocation();

  if (loading) {
    return <div role="status">{t('labels.loading')}</div>;
  }

  if (!isAuthenticated) {
    return <Navigate to="/login" replace state={{ from: location.pathname }} />;
  }

  if (allowedRoles && (!user || !allowedRoles.includes(user.role))) {
    return <Navigate to={redirectTo || getHomePath(user?.role)} replace />;
  }

  return <>{children}</>;
};
