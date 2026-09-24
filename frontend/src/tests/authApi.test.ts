import { afterEach, describe, expect, it, vi } from 'vitest';
import { authApi } from '@/features/auth/api/authApi';

describe('authApi session validation', () => {
  afterEach(() => {
    vi.restoreAllMocks();
    vi.unstubAllGlobals();
  });

  it('maps the authenticated /me roleCode to the same user shape as login', async () => {
    vi.stubGlobal('localStorage', { getItem: (key: string) => key === 'itam_auth_token' ? 'user.token' : null });
    const fetchSpy = vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response(JSON.stringify({
      success: true,
      data: {
        id: 4,
        email: 'user@company.com',
        fullName: 'User',
        roleCode: 'USER',
        roleName: 'End User',
        departmentName: 'IT',
        accountStatus: 'ACTIVE',
      },
    })));

    const user = await authApi.me();

    expect(user).toEqual({
      id: 4,
      email: 'user@company.com',
      fullName: 'User',
      role: 'USER',
      roleName: 'End User',
      departmentName: 'IT',
      accountStatus: 'ACTIVE',
    });
    expect(fetchSpy).toHaveBeenCalledWith(expect.stringContaining('/v1/auth/me'), expect.objectContaining({
      headers: expect.objectContaining({ Authorization: 'Bearer user.token' }),
    }));
  });
});
