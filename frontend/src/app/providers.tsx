import { ReactNode } from 'react';

interface AppProvidersProps {
  children: ReactNode;
}

function AppProviders({ children }: AppProvidersProps) {
  return <>{children}</>;
}

export { AppProviders };
