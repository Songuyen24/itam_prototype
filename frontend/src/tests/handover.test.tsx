import { beforeEach, describe, expect, it, vi } from 'vitest';
import { renderToStaticMarkup } from 'react-dom/server';
import { handoverApi, Handover, HandoverRequest } from '@/features/handover/api/handoverApi';
import { HandoverDetails } from '@/features/handover/components/HandoverDetails';
import { HandoverForm } from '@/features/handover/components/HandoverForm';
import en from '@/shared/i18n/locales/en/handover.json';
import vn from '@/shared/i18n/locales/vi/handover.json';

const client = vi.hoisted(() => vi.fn());
vi.mock('@/shared/api/httpClient', () => ({ httpClient: client }));
vi.mock('react-i18next', () => ({ useTranslation: () => ({ t: (key: string) => key }) }));
const req: HandoverRequest = { recipientUserId: 7, destinationLocationId: 2, handoverDate: '2026-09-10', notes: 'Demo', assetIds: [8], licenses: [{ assetId: 9, seats: 1, deviceId: 8 }] };

describe('T15 handover', () => {
  beforeEach(() => { client.mockReset(); });
  it('uses the server preview and sends the exact confirmed fingerprint on completion', async () => {
    await handoverApi.preview(req);
    await handoverApi.complete({ ...req, expectedFingerprint: 'reviewed-bundle' });
    expect(client.mock.calls).toEqual([
      ['/v1/handovers/preview', { method: 'POST', body: JSON.stringify(req) }],
      ['/v1/handovers', { method: 'POST', body: JSON.stringify({ ...req, expectedFingerprint: 'reviewed-bundle' }) }],
    ]);
  });
  it('uses restricted available inventory and paginates search safely', async () => {
    await handoverApi.candidates('RAM & OEM', 3);
    expect(client).toHaveBeenCalledWith('/v1/handovers/candidates?keyword=RAM+%26+OEM&page=3&size=10');
  });
  it('does not automatically retry a failed completion', async () => {
    client.mockRejectedValue(new Error('Conflict'));
    await expect(handoverApi.complete(req)).rejects.toThrow('Conflict');
    expect(client).toHaveBeenCalledTimes(1);
  });
  it('renders saved package allocation IDs and frozen hardware values', () => {
    const value: Handover = { transactionId: 1, transactionCode: 'HO-1', completedAt: '2026-09-10T10:00:00Z', recipientUserId: 7, recipientName: 'Original recipient', recipientEmail: 'demo@example.com', destinationLocationId: 2, destinationLocationName: 'Original office', handoverDate: '2026-09-10', notes: '', fingerprint: 'frozen', lines: [
      { assetId: 8, assetTag: 'LAP-01', name: 'Original laptop', category: 'DEVICE', parentAssetId: null, seats: 0, allocations: [], details: { defaultRam: '16 GB', actualRam: '32 GB', serialNumber: 'SN-01' } },
      { assetId: 9, assetTag: 'LIC-01', name: 'OEM package', category: 'LICENSE', parentAssetId: null, seats: 1, allocations: [{ allocationId: 77, deviceId: 8, seats: 1, assignmentType: 'OEM' }], details: {} },
    ] };
    const html = renderToStaticMarkup(<HandoverDetails value={value} />);
    expect(html).toContain('Original recipient'); expect(html).toContain('32 GB'); expect(html).toContain('77'); expect(html).toContain('LIC-01');
    expect(html).not.toContain('16 GB'); expect(client).not.toHaveBeenCalled();
  });
  it('requires a date, keeps the form empty and provides loading states before choosing assets', () => {
    const html = renderToStaticMarkup(<HandoverForm onCompleted={() => {}} />);
    expect(html).toMatch(/type="date" required/); expect(html).toContain('emptySelection'); expect(html).toContain('role="status"'); expect(html).not.toContain('role="dialog"');
  });
  it('provides matching Vietnamese and English keys', () => {
    expect(Object.keys(vn).sort()).toEqual(Object.keys(en).sort());
    expect(vn.confirmTitle).toBe('Xác nhận hoàn tất bàn giao');
    expect(en.confirmHint).toContain('every asset');
  });
});
