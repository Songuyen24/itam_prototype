import { beforeEach, describe, expect, it, vi } from 'vitest';
import { renderToStaticMarkup } from 'react-dom/server';
import { disposalApi } from '@/features/disposal/api/disposalApi';
import { dashboardApi } from '@/features/dashboard/api/dashboardApi';
import { DisposalsPage } from '@/features/disposal/pages/DisposalsPage';
import { DashboardPage } from '@/features/dashboard/pages/DashboardPage';
import { DisposalDetail, DisposalReview } from '@/features/disposal/components/DisposalReview';
import { dashboardLabel } from '@/features/dashboard/pages/DashboardPage';
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
    await disposalApi.detail(9);
    await disposalApi.resolvePerUser(9, 'viewed', [{ allocationId: 77, release: true }]);
    await disposalApi.approve(9, 'refreshed');
    await disposalApi.reject(9, 'Repair first');
    expect(client.mock.calls).toEqual([
      ['/v1/disposals/smart-check', { method: 'POST', body: JSON.stringify({ assetIds: [7] }) }],
      ['/v1/disposals', { method: 'POST', body: JSON.stringify({ assetIds: [7], reason: 'Broken', disposalDate: '2026-09-15', expectedFingerprint: 'checked' }) }],
      ['/v1/disposals/9'],
      ['/v1/disposals/9/per-user', { method: 'POST', body: JSON.stringify({ expectedFingerprint: 'viewed', decisions: [{ allocationId: 77, release: true }] }) }],
      ['/v1/disposals/9/approve', { method: 'POST', body: JSON.stringify({ expectedFingerprint: 'refreshed' }) }],
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

  it('shows the viewed request facts, unresolved Per-User choices, and consumed OEM seats', () => {
    const html = renderToStaticMarkup(<DisposalDetail value={{
      transactionId: 9, transactionCode: 'DIS-09', status: 'PENDING', actorName: 'Nguyen Admin', reason: 'Broken',
      disposalDate: '2026-09-15', createdAt: '2026-09-14T10:00:00Z', fingerprint: 'viewed', warnings: [],
      assets: [{ assetId: 1, assetTag: 'LAP-01', name: 'Laptop', category: 'DEVICE', autoAdded: false, serialNumber: 'SN-01' }],
      perUserLinks: [{ allocationId: 77, licenseAssetId: 2, assetTag: 'USR-01', deviceId: 1, deviceTag: 'LAP-01', userName: 'Mai', seats: 2 }],
      oemAllocations: [{ allocationId: 88, licenseAssetId: 3, assetTag: 'OEM-01', deviceId: 1, deviceTag: 'LAP-01', seats: 1 }],
    }} role="ADMIN" decisions={{ 77: true }} saving={false} action={null} rejectReason=""
      onDecision={() => {}} onResolve={() => {}} onChooseAction={() => {}} onRejectReason={() => {}}
      onApprove={() => {}} onReject={() => {}} onClose={() => {}} />);
    expect(html).toContain('Nguyen Admin');
    expect(html).toContain('Broken');
    expect(html).toContain('SN-01');
    expect(html).toContain('USR-01');
    expect(html).toContain('checked');
    expect(html).toContain('OEM-01');
    expect(html).toMatch(/approve[^<]*<\/button>/);
    expect(html).toMatch(/button[^>]*disabled=""[^>]*>approve/);
  });

  it('translates only known dashboard system labels', () => {
    const t = (key: string, options?: Record<string, unknown>) => `${key}:${String(options?.defaultValue ?? '')}`;
    expect(dashboardLabel(t, 'byStatus', 'IN_STOCK')).toBe('common:status.IN_STOCK:IN_STOCK');
    expect(dashboardLabel(t, 'byDepartment', 'Research')).toBe('Research');
    expect(dashboardLabel(t, 'byLocation', '')).toBe('unassigned:');
    expect(dashboardLabel(t, 'byLocation', 'Unassigned')).toBe('Unassigned');
  });

  it('keeps Vietnamese and English keys aligned', () => {
    expect(Object.keys(viDisposal).sort()).toEqual(Object.keys(enDisposal).sort());
    expect(Object.keys(viDashboard).sort()).toEqual(Object.keys(enDashboard).sort());
    expect(enDashboard.error).toBeTruthy();
  });
});
