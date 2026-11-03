import { httpClient } from '@/shared/api/httpClient';
import { ApiResponse, PageResponse } from '@/features/catalogs/types/catalog.types';
import type { GroupAction, WorkflowGroup } from '@/shared/types/workflowGroup';

export interface SmartCheckResponse {
  requiredAssets: { assetId: number; assetTag: string; name: string; category: string; reason: string | null }[];
  optionalAssets: { allocationId: number; assetId: number; assetTag: string; name: string; seats: number; userId: number | null; userName: string | null; deviceId: number | null; deviceTag: string | null; selected: boolean }[];
  blockedAssets: { assetId: number; assetTag: string; name: string; category: string; reason: string }[];
  componentDecisions: { assetId: number; assetTag: string; name: string; options: { action: string; label: string; description: string }[]; defaultAction: string }[];
  warnings: string[];
  fingerprint: string;
}

export interface RecoveryRequest {
  returnerUserId: number;
  receivingLocationId: number;
  recoveryDate: string;
  reason: string;
  assetIds: number[];
  allocationIds: number[];
  componentActions: Record<number, { componentAction: string; recoverPerUser: boolean }>;
  perUserActions: Record<number, boolean>;
  expectedFingerprint: string;
}

export function buildRecoveryRequest(
  check: SmartCheckResponse,
  assetIds: number[],
  allocationIds: number[],
  reason: string,
  componentActions: Record<number, string> = {},
  perUserActions: Record<number, boolean> = Object.fromEntries(allocationIds.map(id => [id, true])),
): RecoveryRequest {
  return {
    returnerUserId: 0,
    receivingLocationId: 0,
    recoveryDate: '',
    reason,
    assetIds,
    allocationIds,
    componentActions: Object.fromEntries(Object.entries(componentActions).map(([id, action]) => [Number(id), {
      componentAction: action,
      recoverPerUser: false,
    }])),
    perUserActions,
    expectedFingerprint: check.fingerprint,
  };
}

export interface RecoveryLine {
  assetId: number;
  assetTag: string;
  name: string;
  category: string;
  parentAssetId: number | null;
  seats: number;
  allocations: { allocationId: number; seats: number; action: string; assignmentType: string }[];
  details: Record<string, unknown>;
}

export interface Recovery {
  transactionId: number | null;
  transactionCode: string | null;
  completedAt: string | null;
  returnerUserId: number;
  returnerName: string;
  returnerEmail: string;
  receivingLocationId: number;
  receivingLocationName: string;
  recoveryDate: string;
  reason: string;
  fingerprint: string;
  lines: RecoveryLine[];
  proposalLines?: RecoveryLine[];
  status: 'DRAFT' | 'PENDING' | 'PARTIAL' | 'COMPLETED' | 'CANCELLED' | null;
  contentVersion?: number;
  groups?: WorkflowGroup[];
  creatorUserId: number | null;
  creatorName: string | null;
  request: RecoveryRequest | null;
}

export const recoveryApi = {
  smartCheck: (body: { returnerUserId: number; assetIds: number[]; allocationIds: number[]; reason: string }) =>
    httpClient<ApiResponse<SmartCheckResponse>>('/v1/recoveries/smart-check', { method: 'POST', body: JSON.stringify(body) }),
  create: (body: RecoveryRequest) =>
    httpClient<ApiResponse<Recovery>>('/v1/recoveries', { method: 'POST', body: JSON.stringify(body) }),
  edit: (id: number, body: RecoveryRequest, version: number, reason = '') =>
    httpClient<ApiResponse<Recovery>>(`/v1/recoveries/${id}`, { method: 'PUT', headers: { 'X-Expected-Version': String(version), ...(reason ? { 'X-Override-Reason': encodeURIComponent(reason.trim()) } : {}) }, body: JSON.stringify(body) }),
  submit: (id: number, version: number) => httpClient<ApiResponse<Recovery>>(`/v1/recoveries/${id}/submit`, { method: 'POST', headers: { 'X-Expected-Version': String(version) } }),
  withdraw: (id: number, version: number, reason = '') => httpClient<ApiResponse<Recovery>>(`/v1/recoveries/${id}/withdraw`, { method: 'POST', headers: { 'X-Expected-Version': String(version), ...(reason ? { 'X-Override-Reason': encodeURIComponent(reason.trim()) } : {}) } }),
  cancel: (id: number, version: number, reason = '') => httpClient<ApiResponse<Recovery>>(`/v1/recoveries/${id}/cancel`, { method: 'POST', headers: { 'X-Expected-Version': String(version), ...(reason ? { 'X-Override-Reason': encodeURIComponent(reason.trim()) } : {}) } }),
  confirm: (id: number, body: GroupAction) => httpClient<ApiResponse<Recovery>>(`/v1/recoveries/${id}/confirm`, { method: 'POST', body: JSON.stringify(body) }),
  cancelRemaining: (id: number, body: GroupAction) => httpClient<ApiResponse<Recovery>>(`/v1/recoveries/${id}/cancel-remaining`, { method: 'POST', headers: { 'X-Override-Reason': encodeURIComponent(body.reason!.trim()) }, body: JSON.stringify(body) }),
  list: (keyword = '', status = '', page = 0, size = 20) => httpClient<ApiResponse<PageResponse<Recovery>>>(`/v1/recoveries?${new URLSearchParams({ keyword, status, page: String(page), size: String(size) })}`),
  detail: (id: number) => httpClient<ApiResponse<Recovery>>(`/v1/recoveries/${id}`),
};
