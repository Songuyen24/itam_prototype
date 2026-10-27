import { httpClient, downloadFile } from '@/shared/api/httpClient';
import type { ApiResponse, PageResponse } from '@/features/catalogs/types/catalog.types';
import type { HeldAsset, HolderDetail } from '@/features/handover/api/handoverApi';

export interface EmployeeProfile {
  id: number;
  fullName: string;
  employeeCode: string | null;
  email: string | null;
  departmentId: number | null;
  departmentName: string | null;
  accountStatus: string;
  roleCode: string;
  hasAccount: boolean;
  accessGranted: boolean;
  loginLocked: boolean;
  deviceCount?: number;
  componentCount?: number;
  licenseSeats?: number;
}

export interface ManagedAccount {
  id: number;
  fullName: string;
  employeeCode: string | null;
  email: string | null;
  roleCode: string;
  accessGranted: boolean;
  loginLocked: boolean;
}

export type ProfileInput = {
  fullName: string;
  employeeCode: string;
  email: string | null;
  departmentId: number | null;
  employeeCodeCorrectionReason?: string;
};

const query = (keyword: string, page: number, size = 20) => new URLSearchParams({ keyword, page: String(page), size: String(size) });

export const managementApi = {
  accountImportTemplate: () => downloadFile('/v1/users/import/template', 'account-import-template.xlsx'),
  accountImportPreview: (file: File) => { const body=new FormData(); body.append('file',file); return httpClient<ApiResponse<{ rows: AccountImportRow[]; valid: number; invalid: number; created: number }>>('/v1/users/import/preview',{method:'POST',body}); },
  accountImportConfirm: (file: File) => { const body=new FormData(); body.append('file',file); return httpClient<ApiResponse<{ rows: AccountImportRow[]; valid: number; invalid: number; created: number }>>('/v1/users/import/confirm',{method:'POST',body}); },
  profiles: (keyword = '', page = 0, withoutAccount = false) => httpClient<ApiResponse<PageResponse<EmployeeProfile>>>(`/v1/users?${query(keyword, page)}${withoutAccount ? '&withoutAccount=true' : ''}`),
  profile: (id: number) => httpClient<ApiResponse<EmployeeProfile>>(`/v1/users/${id}`),
  holdings: (id: number, page = 0) => httpClient<ApiResponse<HolderDetail>>(`/v1/users/${id}/holdings?${query('', page, 10)}`),
  createProfile: (body: ProfileInput & { password?: string; accessGranted?: boolean }) => httpClient<ApiResponse<EmployeeProfile>>('/v1/users', { method: 'POST', body: JSON.stringify(body) }),
  updateProfile: (id: number, body: ProfileInput) => httpClient<ApiResponse<EmployeeProfile>>(`/v1/users/${id}`, { method: 'PUT', body: JSON.stringify(body) }),
  setProfileStatus: (id: number, status: 'ACTIVE' | 'LOCKED') => httpClient<ApiResponse<EmployeeProfile>>(`/v1/users/${id}/status`, { method: 'PUT', body: JSON.stringify({ status }) }),
  accounts: (keyword = '', page = 0, reportEligible = false) => httpClient<ApiResponse<PageResponse<ManagedAccount>>>(`/v1/accounts?${query(keyword, page)}${reportEligible ? '&reportEligible=true' : ''}`),
  createAccount: (body: { userId: number; password: string; roleCode: string; accessGranted: boolean }) => httpClient<ApiResponse<ManagedAccount>>('/v1/accounts', { method: 'POST', body: JSON.stringify(body) }),
  updateAccount: (id: number, body: { roleCode: string; accessGranted: boolean; loginLocked: boolean }) => httpClient<ApiResponse<ManagedAccount>>(`/v1/accounts/${id}`, { method: 'PUT', body: JSON.stringify(body) }),
  resetPassword: (id: number, newPassword: string, confirmPassword: string) => httpClient<ApiResponse<unknown>>(`/v1/accounts/${id}/password`, { method: 'PUT', body: JSON.stringify({ newPassword, confirmPassword }) }),
  changeOwnPassword: (currentPassword: string, newPassword: string, confirmPassword: string) => httpClient<ApiResponse<unknown>>('/v1/auth/password', { method: 'POST', body: JSON.stringify({ currentPassword, newPassword, confirmPassword }) }),
};

export interface AccountImportRow { row:number; employeeCode:string|null; fullName:string|null; email:string|null; departmentCode:string|null; status:string; errors:string[]; id:number|null; }

export type { HeldAsset };
