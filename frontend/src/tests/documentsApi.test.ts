import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { documentApi } from '@/features/documents/api/documentApi';
import * as client from '@/shared/api/httpClient';

describe('Document API contract', () => {
  beforeEach(() => {
    vi.stubGlobal('localStorage', {
      getItem: (key: string) => key === 'itam_auth_token' ? 'pur-token' : key === 'itam_language' ? 'en' : null,
    });
  });

  afterEach(() => {
    vi.restoreAllMocks();
    vi.unstubAllGlobals();
  });

  it('encodes transaction filters without accessing inventory or users', async () => {
    const fetch = vi.fn().mockResolvedValue({ ok: true, json: async () => ({ success: true, data: { content: [] } }) });
    vi.stubGlobal('fetch', fetch);
    await documentApi.getTransactions({ type: 'IMPORT', status: 'PENDING', keyword: ' IMP & 2026 ', page: 2, size: 8 });
    const [url, options] = fetch.mock.calls[0];
    const requestUrl = new URL(url);
    expect(requestUrl.pathname).toBe('/api/v1/transactions');
    expect(Object.fromEntries(requestUrl.searchParams)).toEqual({ page: '2', size: '8', type: 'IMPORT', status: 'PENDING', keyword: 'IMP & 2026' });
    expect(options.headers).toMatchObject({ Authorization: 'Bearer pur-token', 'Accept-Language': 'en' });
    expect(fetch).toHaveBeenCalledTimes(1);
  });

  it('scopes document pagination to the selected real transaction', async () => {
    const fetch = vi.fn().mockResolvedValue({ ok: true, json: async () => ({ success: true, data: { content: [] } }) });
    vi.stubGlobal('fetch', fetch);
    await documentApi.getDocuments(42, 3, 10);
    const requestUrl = new URL(fetch.mock.calls[0][0]);
    expect(requestUrl.pathname).toBe('/api/v1/documents');
    expect(Object.fromEntries(requestUrl.searchParams)).toEqual({ transactionId: '42', page: '3', size: '10' });
  });

  it('loads transaction metadata through the scoped detail endpoint', async () => {
    const fetch = vi.fn().mockResolvedValue({ ok: true, json: async () => ({ success: true, data: { transactionId: 42 } }) });
    vi.stubGlobal('fetch', fetch);
    const result = await documentApi.getTransaction(42);
    expect(new URL(fetch.mock.calls[0][0]).pathname).toBe('/api/v1/transactions/42');
    expect(result.data.transactionId).toBe(42);
  });

  it('sends file, transaction, optional asset and expected version as multipart fields', async () => {
    const fetch = vi.fn().mockResolvedValue({ ok: true, json: async () => ({ success: true, data: { documentId: 7 } }) });
    vi.stubGlobal('fetch', fetch);
    const file = new File(['sample'], 'sample.pdf', { type: 'application/pdf' });
    await documentApi.upload({ file, transactionId: 42, documentType: 'INVOICE', assetId: 11, expectedVersion: 0 });
    const [url, options] = fetch.mock.calls[0];
    expect(new URL(url).pathname).toBe('/api/v1/documents');
    expect(options.method).toBe('POST');
    expect(options.headers['Content-Type']).toBeUndefined();
    expect(options.headers.Authorization).toBe('Bearer pur-token');
    expect(options.body).toBeInstanceOf(FormData);
    expect(options.body.get('file')).toBe(file);
    expect(options.body.get('transactionId')).toBe('42');
    expect(options.body.get('documentType')).toBe('INVOICE');
    expect(options.body.get('assetId')).toBe('11');
    expect(options.body.get('expectedVersion')).toBe('0');
  });

  it.each([403, 409])('preserves server rejection %i for the UI', async (status) => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: false, status, json: async () => ({ message: 'Request rejected', code: 'DOCUMENT_LOCKED' }) }));
    await expect(documentApi.upload({ file: new File(['sample'], 'sample.pdf', { type: 'application/pdf' }), transactionId: 42, documentType: 'INVOICE', expectedVersion: 2 }))
      .rejects.toMatchObject({ status, code: 'DOCUMENT_LOCKED', message: 'Request rejected' });
  });

  it('downloads using the existing authenticated download helper', async () => {
    const download = vi.spyOn(client, 'downloadFile').mockResolvedValue();
    await documentApi.download({ documentId: 7, originalFileName: 'sample.pdf' });
    expect(download).toHaveBeenCalledWith('/v1/documents/7/download', 'sample.pdf');
  });
});
