import { ReactNode, useEffect } from 'react';
import { AuthProvider } from '@/features/auth/contexts/AuthContext';
import { registerUnauthorizedHandler } from '@/shared/api/httpClient';
import { useNavigate } from 'react-router-dom';

interface AppProvidersProps {
  children: ReactNode;
}

/**
 * Component nhỏ chỉ để đăng ký handler 401: khi nhận 401 sẽ
 * điều hướng về {@code /login} (và xóa session).
 */
function UnauthorizedBridge() {
  const navigate = useNavigate();
  useEffect(() => {
    registerUnauthorizedHandler(() => {
      navigate('/login', { replace: true });
    });
    return () => registerUnauthorizedHandler(null);
  }, [navigate]);
  return null;
}

function AppProviders({ children }: AppProvidersProps) {
  return (
    <AuthProvider>
      <UnauthorizedBridge />
      {children}
    </AuthProvider>
  );
}

export { AppProviders };
