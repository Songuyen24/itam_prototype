import { describe, it, expect, beforeEach, vi } from 'vitest';
import { ApiError, httpClient } from '@/shared/api/httpClient';

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
  });
});
