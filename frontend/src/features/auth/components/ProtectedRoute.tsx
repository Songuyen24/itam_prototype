import React, { useEffect } from 'react';
import { Navigate, useLocation } from 'react-router-dom';
import { useAuth } from '../contexts/AuthContext';

/**
 * Route guard: nếu chưa đăng nhập thì chuyển hướng về {@code /login}.
 * Dùng để bọc các route yêu cầu xác thực trong cấu hình router.
 */
export const ProtectedRoute: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const { isAuthenticated } = useAuth();
  const location = useLocation();

  useEffect(() => {
    // Không cần xử lý thêm: render Redirect ngay dưới đây sẽ kích hoạt
  }, [isAuthenticated]);

  if (!isAuthenticated) {
    return <Navigate to="/login" replace state={{ from: location.pathname }} />;
  }

  return <>{children}</>;
};
