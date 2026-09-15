import { beforeEach, describe, expect, it, vi } from 'vitest';
import { renderToStaticMarkup } from 'react-dom/server';
import { disposalApi } from '@/features/disposal/api/disposalApi';
import { dashboardApi } from '@/features/dashboard/api/dashboardApi';
import { DisposalsPage } from '@/features/disposal/pages/DisposalsPage';
import { DashboardPage } from '@/features/dashboard/pages/DashboardPage';
import { DisposalReview } from '@/features/disposal/components/DisposalReview';
import enDisposal from '@/shared/i18n/locales/en/disposal.json';
import viDisposal from '@/shared/i18n/locales/vi/disposal.json';
import enDashboard from '@/shared/i18n/locales/en/dashboard.json';
import viDashboard from '@/shared/i18n/locales/vi/dashboard.json';

const client = vi.hoisted(() => vi.fn());
const download = vi.hoisted(() => vi.fn());
vi.mock('@/shared/api/httpClient', () => ({ httpClient: client, downloadFile: download }));
vi.mock('react-i18next', () => ({ useTranslation: () => ({ t: (key: string) => key }) }));
vi.mock('@/features/auth/contexts/AuthContext', () => ({ useAuth: () => ({ user: { role: 'ADMIN' } }) }));

describe('T18 disposal and dashboard', () => {
  beforeEach(() => client.mockReset());

  it('uses server smart-check before creating a disposal and exposes admin decisions', async () => {
    await disposalApi.smartCheck([7]);
    await disposalApi.create({ assetIds: [7], reason: 'Broken', disposalDate: '2026-09-15', expectedFingerprint: 'checked' });
    await disposalApi.approve(9);
    await disposalApi.reject(9, 'Repair first');
    expect(client.mock.calls).toEqual([
      ['/v1/disposals/smart-check', { method: 'POST', body: JSON.stringify({ assetIds: [7] }) }],
      ['/v1/disposals', { method: 'POST', body: JSON.stringify({ assetIds: [7], reason: 'Broken', disposalDate: '2026-09-15', expectedFingerprint: 'checked' }) }],
      ['/v1/disposals/9/approve', { method: 'POST' }],
      ['/v1/disposals/9/reject', { method: 'POST', body: JSON.stringify({ reason: 'Repair first' }) }],
    ]);
  });

  it('loads the summary and exports through the protected report endpoint', async () => {
    await dashboardApi.summary();
    await dashboardApi.exportAssets();
    expect(client.mock.calls[0]).toEqual(['/v1/dashboard/summary']);
    expect(download).toHaveBeenCalledWith('/v1/reports/assets/export', 'itam-assets.xlsx');
  });

  it('renders disposal and dashboard loading, empty, and error surfaces', () => {
    const disposal = renderToStaticMarkup(<DisposalsPage />);
    const dashboard = renderToStaticMarkup(<DashboardPage />);
    expect(disposal).toContain('role="status"');
    expect(disposal).toContain('reason');
    expect(dashboard).toContain('role="status"');
    expect(dashboard).toContain('export');
    expect(enDisposal.empty).toBeTruthy();
    expect(enDisposal.error).toBeTruthy();
  });

  it('shows auto-added children and Per-User warnings before creation', () => {
    const html = renderToStaticMarkup(<DisposalReview value={{ fingerprint: 'checked', warnings: ['PER_USER_LINKED:LIC-01'], assets: [
      { assetId: 1, assetTag: 'LAP-01', name: 'Laptop', category: 'DEVICE', autoAdded: false },
      { assetId: 2, assetTag: 'RAM-01', name: 'RAM', category: 'COMPONENT', autoAdded: true },
    ] }} saving={false} onCancel={() => {}} onConfirm={() => {}} />);
    expect(html).toContain('RAM-01');
    expect(html).toContain('autoAdded');
    expect(html).toContain('perUserWarning');
    expect(html).toContain('confirmCreate');
  });

  it('keeps Vietnamese and English keys aligned', () => {
    expect(Object.keys(viDisposal).sort()).toEqual(Object.keys(enDisposal).sort());
    expect(Object.keys(viDashboard).sort()).toEqual(Object.keys(enDashboard).sort());
    expect(enDashboard.error).toBeTruthy();
  });
});
