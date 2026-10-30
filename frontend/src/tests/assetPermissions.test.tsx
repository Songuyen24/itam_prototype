import { renderToStaticMarkup } from 'react-dom/server';
import { describe, expect, it, vi } from 'vitest';
import { AssetsPage } from '@/features/assets/pages/AssetsPage';
import { MemoryRouter } from 'react-router-dom';

let role = 'IT_STAFF';

vi.mock('@/features/auth/contexts/AuthContext', () => ({
  useAuth: () => ({
    user: { id: 1, role, email: 'it@example.test', fullName: 'IT Staff' },
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
    role = 'IT_STAFF';
    expect(renderToStaticMarkup(<MemoryRouter><AssetsPage /></MemoryRouter>)).toContain('actions.addBaselineAsset');
  });

  it('labels the filtered total separately from current page statuses', () => {
    role = 'ADMIN';
    const html = renderToStaticMarkup(<MemoryRouter><AssetsPage /></MemoryRouter>);
    expect(html).toContain('list.matchingTotal');
    expect(html).toContain('list.currentPageBreakdown');
    expect(html).toContain('list.otherStatusesLabel');
  });

  it('keeps personal assets free of catalog filters and management actions', () => {
    role = 'USER';
    const html = renderToStaticMarkup(<MemoryRouter><AssetsPage myAssets /></MemoryRouter>);
    expect(html).toContain('myAssetsTitle');
    expect(html).not.toContain('actions.addBaselineAsset');
    expect(html).not.toContain('actions.importExcel');
    expect(html).not.toContain('list.matchingTotal');
    expect(html).not.toContain('filters.allStatuses');
  });
});
