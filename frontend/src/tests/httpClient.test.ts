import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { ApiError, downloadFile, httpClient, invalidateSessionRequests, registerUnauthorizedHandler } from '@/shared/api/httpClient';

function deferred<T>() {
  let resolve!: (value: T) => void;
  const promise = new Promise<T>((resolvePromise) => { resolve = resolvePromise; });
  return { promise, resolve };
}

describe('httpClient Bearer token + 401 handler', () => {
  beforeEach(() => {
    let store: Record<string, string> = {};
    Object.defineProperty(globalThis, 'localStorage', {
      value: {
        getItem: (k: string) => store[k] ?? null,
        setItem: (k: string, v: string) => { store[k] = v; },
        removeItem: (k: string) => { delete store[k]; },
        clear: () => { store = {}; },
      },
      writable: true,
      configurable: true,
    });
    localStorage.clear();
    vi.restoreAllMocks();
    registerUnauthorizedHandler(null);
    invalidateSessionRequests();
  });

  afterEach(() => {
    registerUnauthorizedHandler(null);
    vi.unstubAllGlobals();
  });

  it('attaches Authorization header when token is set', async () => {
    localStorage.setItem('itam_auth_token', 'fake.jwt.token');
    const fetchSpy = vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(JSON.stringify({ success: true, data: { ok: 1 } }), {
        status: 200,
        headers: { 'content-type': 'application/json' },
      })
    );

    await httpClient<{ ok: number }>('/v1/auth/me', { method: 'GET' });

    expect(fetchSpy).toHaveBeenCalledTimes(1);
    const [, init] = fetchSpy.mock.calls[0] as [string, RequestInit];
    const headers = init.headers as Record<string, string>;
    expect(headers['Authorization']).toBe('Bearer fake.jwt.token');
  });

  it('does not attach Authorization when no token', async () => {
    const fetchSpy = vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(JSON.stringify({ success: true, data: { ok: 1 } }), {
        status: 200,
        headers: { 'content-type': 'application/json' },
      })
    );

    await httpClient<{ ok: number }>('/v1/auth/login', { method: 'POST' });

    const [, init] = fetchSpy.mock.calls[0] as [string, RequestInit];
    const headers = init.headers as Record<string, string>;
    expect(headers['Authorization']).toBeUndefined();
  });

  it('throws ApiError and clears token on 401', async () => {
    const onUnauthorized = vi.fn();
    registerUnauthorizedHandler(onUnauthorized);
    localStorage.setItem('itam_auth_token', 'expired.token');
    localStorage.setItem('itam_auth_user', JSON.stringify({ id: 1 }));
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(
        JSON.stringify({ success: false, code: 'UNAUTHORIZED', message: 'Token expired' }),
        { status: 401, headers: { 'content-type': 'application/json' } }
      )
    );

    await expect(
      httpClient<unknown>('/v1/auth/me', { method: 'GET' })
    ).rejects.toBeInstanceOf(ApiError);

    expect(localStorage.getItem('itam_auth_token')).toBeNull();
    expect(localStorage.getItem('itam_auth_user')).toBeNull();
    expect(onUnauthorized).toHaveBeenCalledOnce();
  });

  it('accepts a successful document detach with no response body', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response(null, { status: 204 }));
    await expect(httpClient<void>('/v1/documents/1?expectedVersion=2', { method: 'DELETE' })).resolves.toBeUndefined();
  });

  it.each([200, 401])('discards a late %s response from a previous account', async (status) => {
    localStorage.setItem('itam_auth_token', 'old.token');
    const onUnauthorized = vi.fn();
    registerUnauthorizedHandler(onUnauthorized);
    const response = deferred<Response>();
    vi.spyOn(globalThis, 'fetch').mockReturnValue(response.promise);
    const pending = httpClient('/v1/assets');
    const rejected = expect(pending).rejects.toMatchObject({ code: 'SESSION_CHANGED' });

    localStorage.setItem('itam_auth_token', 'new.token');
    localStorage.setItem('itam_auth_user', JSON.stringify({ id: 2, role: 'USER' }));
    response.resolve(new Response(JSON.stringify({ data: [{ assetId: 1 }] }), { status }));

    await rejected;
    expect(localStorage.getItem('itam_auth_token')).toBe('new.token');
    expect(JSON.parse(localStorage.getItem('itam_auth_user')!)).toEqual({ id: 2, role: 'USER' });
    expect(onUnauthorized).not.toHaveBeenCalled();
  });

  it.each([200, 401, 403])('discards an old %s response if the account changes during JSON parsing', async (status) => {
    localStorage.setItem('itam_auth_token', 'old.token');
    const onUnauthorized = vi.fn();
    registerUnauthorizedHandler(onUnauthorized);
    const parsed = deferred<unknown>();
    const json = vi.fn(() => parsed.promise);
    vi.spyOn(globalThis, 'fetch').mockResolvedValue({ ok: status === 200, status, json } as unknown as Response);
    const pending = httpClient('/v1/assets');
    const rejected = expect(pending).rejects.toMatchObject({ code: 'SESSION_CHANGED' });
    await vi.waitFor(() => expect(json).toHaveBeenCalledOnce());

    localStorage.setItem('itam_auth_token', 'new.token');
    parsed.resolve({ data: [{ assetId: 1 }], message: 'Old account response' });

    await rejected;
    expect(localStorage.getItem('itam_auth_token')).toBe('new.token');
    expect(onUnauthorized).not.toHaveBeenCalled();
  });

  it('discards requests after a session reset even if the token value is reused', async () => {
    localStorage.setItem('itam_auth_token', 'same.token');
    const response = deferred<Response>();
    vi.spyOn(globalThis, 'fetch').mockReturnValue(response.promise);
    const pending = httpClient('/v1/assets');
    const rejected = expect(pending).rejects.toMatchObject({ code: 'SESSION_CHANGED' });

    invalidateSessionRequests();
    response.resolve(new Response(JSON.stringify({ data: [{ assetId: 1 }] })));
    await rejected;
  });

  it.each([200, 401])('discards a late download %s without affecting the new session', async (status) => {
    localStorage.setItem('itam_auth_token', 'old.token');
    const onUnauthorized = vi.fn();
    registerUnauthorizedHandler(onUnauthorized);
    const response = deferred<Response>();
    vi.spyOn(globalThis, 'fetch').mockReturnValue(response.promise);
    const pending = downloadFile('/v1/assets/template', 'assets.xlsx');
    const rejected = expect(pending).rejects.toMatchObject({ code: 'SESSION_CHANGED' });

    localStorage.setItem('itam_auth_token', 'new.token');
    response.resolve(new Response('old account file', { status }));

    await rejected;
    expect(localStorage.getItem('itam_auth_token')).toBe('new.token');
    expect(onUnauthorized).not.toHaveBeenCalled();
  });

  it('does not create a download if the account changes while the file body loads', async () => {
    localStorage.setItem('itam_auth_token', 'old.token');
    const parsed = deferred<Blob>();
    const blob = vi.fn(() => parsed.promise);
    const createObjectURL = vi.fn();
    vi.stubGlobal('window', { URL: { createObjectURL } });
    vi.spyOn(globalThis, 'fetch').mockResolvedValue({ ok: true, status: 200, blob } as unknown as Response);
    const pending = downloadFile('/v1/assets/template', 'assets.xlsx');
    const rejected = expect(pending).rejects.toMatchObject({ code: 'SESSION_CHANGED' });
    await vi.waitFor(() => expect(blob).toHaveBeenCalledOnce());

    localStorage.setItem('itam_auth_token', 'new.token');
    parsed.resolve(new Blob(['old account file']));

    await rejected;
    expect(createObjectURL).not.toHaveBeenCalled();
  });
});
