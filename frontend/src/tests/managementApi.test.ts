import { beforeEach, describe, expect, it, vi } from 'vitest';
import { managementApi } from '@/features/auth/api/managementApi';
import { httpClient } from '@/shared/api/httpClient';

vi.mock('@/shared/api/httpClient', () => ({ httpClient: vi.fn() }));

describe('profile and account API contract', () => {
  beforeEach(() => { vi.mocked(httpClient).mockReset(); });

  it('reads profile holdings without using the handover route', async () => {
    await managementApi.profiles('NV-42', 2);
    await managementApi.holdings(42, 1);
    expect(httpClient).toHaveBeenNthCalledWith(1, '/v1/users?keyword=NV-42&page=2&size=20');
    expect(httpClient).toHaveBeenNthCalledWith(2, '/v1/users/42/holdings?keyword=&page=1&size=10');
  });

  it('creates an account on an existing profile and never sends a profile duplicate', async () => {
    await managementApi.createAccount({ userId: 42, username: 'nv42', password: 'Example123', roleCode: 'USER', accessGranted: true });
    expect(httpClient).toHaveBeenCalledWith('/v1/accounts', {
      method: 'POST',
      body: JSON.stringify({ userId: 42, username: 'nv42', password: 'Example123', roleCode: 'USER', accessGranted: true }),
    });
  });

  it('changes own password through the authenticated endpoint', async () => {
    await managementApi.changeOwnPassword('old-secret', 'new-secret', 'new-secret');
    expect(httpClient).toHaveBeenCalledWith('/v1/auth/password', {
      method: 'POST',
      body: JSON.stringify({ currentPassword: 'old-secret', newPassword: 'new-secret', confirmPassword: 'new-secret' }),
    });
  });
});
