import { ReactNode } from 'react';
import { AuthProvider } from '@/features/auth/contexts/AuthContext';

interface AppProvidersProps {
  children: ReactNode;
}

function AppProviders({ children }: AppProvidersProps) {
  return (
    <AuthProvider>
      {children}
    </AuthProvider>
  );
}

export { AppProviders };
