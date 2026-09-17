import { renderToStaticMarkup } from 'react-dom/server';
import { describe, expect, it, vi } from 'vitest';
import { AssetsPage } from '@/features/assets/pages/AssetsPage';

vi.mock('@/features/auth/contexts/AuthContext', () => ({
  useAuth: () => ({
    user: { id: 1, role: 'IT_STAFF', email: 'it@example.test', fullName: 'IT Staff' },
  }),
}));

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string) => key,
    i18n: { language: 'en' },
  }),
}));

describe('Asset baseline permissions', () => {
  it('shows the baseline asset action to IT Staff', () => {
    expect(renderToStaticMarkup(<AssetsPage />)).toContain('actions.addBaselineAsset');
  });
});
