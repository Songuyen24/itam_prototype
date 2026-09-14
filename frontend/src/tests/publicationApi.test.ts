import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { publicationApi } from '@/features/documents/api/publicationApi';
import { documentApi } from '@/features/documents/api/documentApi';
import * as client from '@/shared/api/httpClient';

describe('T17 publication API', () => {
  beforeEach(() => { vi.stubGlobal('localStorage', { getItem: (key: string) => key === 'itam_auth_token' ? 'it-token' : null }); });
  afterEach(() => { vi.restoreAllMocks(); vi.unstubAllGlobals(); });

  it.each([
    ['status', 'GET', '/api/v1/transactions/42/publication'],
    ['emailLogs', 'GET', '/api/v1/transactions/42/email-logs'],
    ['regenerate', 'POST', '/api/v1/transactions/42/pdf/regenerate'],
    ['resend', 'POST', '/api/v1/transactions/42/email/resend'],
  ] as const)('calls %s contract', async (action, method, path) => {
    const fetch = vi.fn().mockResolvedValue({ ok: true, json: async () => ({ success: true, data: { emails: [] } }) });
    vi.stubGlobal('fetch', fetch);
    await publicationApi[action](42);
    expect(new URL(fetch.mock.calls[0][0]).pathname).toBe(path);
    expect(fetch.mock.calls[0][1].method ?? 'GET').toBe(method);
    expect(fetch.mock.calls[0][1].headers.Authorization).toBe('Bearer it-token');
  });

  it('downloads a generated report through the authenticated document endpoint', async () => {
    const download = vi.spyOn(client, 'downloadFile').mockResolvedValue();
    await documentApi.download({ documentId: 91, originalFileName: 'HO-001-v2.pdf' });
    expect(download).toHaveBeenCalledWith('/v1/documents/91/download', 'HO-001-v2.pdf');
  });
});
