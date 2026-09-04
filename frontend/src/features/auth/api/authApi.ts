import { httpClient } from '@/shared/api/httpClient';
import { LoginPayload, LoginResponse, CurrentUserResponse } from '../types/auth.types';

export const authApi = {
  async login(payload: LoginPayload): Promise<LoginResponse> {
    const res = await httpClient<{ success: boolean; data: LoginResponse }>(
      '/v1/auth/login',
      {
        method: 'POST',
        body: JSON.stringify(payload),
      }
    );
    return (res as { success: boolean; data: LoginResponse }).data;
  },

  async me(): Promise<CurrentUserResponse> {
    const res = await httpClient<{ success: boolean; data: CurrentUserResponse }>(
      '/v1/auth/me',
      { method: 'GET' }
    );
    return (res as { success: boolean; data: CurrentUserResponse }).data;
  },

  async logout(): Promise<void> {
    await httpClient<{ success: boolean }>('/v1/auth/logout', { method: 'POST' });
  },
};
