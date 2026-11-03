import { httpClient } from '@/shared/api/httpClient';
import { ApiResponse, PageResponse } from '@/features/catalogs/types/catalog.types';
import type { GroupAction, WorkflowGroup } from '@/shared/types/workflowGroup';

export interface Candidate { assetId: number; assetTag: string; name: string; category: string; assignmentType: string | null; availableSeats: number | null }
export interface Recipient { id: number; fullName: string; email: string | null; accountStatus: string; employeeCode?: string | null }
export interface Holder extends Recipient { employeeCode: string | null; departmentName: string | null; deviceCount: number; componentCount: number; licenseSeats: number }
export interface HeldAsset { assetId: number; assetTag: string; name: string; category: string; allocationId: number | null; seats: number; status: string; serialNumber: string | null; deviceTag: string | null; deviceName: string | null }
export interface HolderDetail { holder: Holder; assets: PageResponse<HeldAsset> }
export interface HandoverRequest {
  recipientUserId: number; destinationLocationId: number; handoverDate: string; notes: string;
  assetIds: number[]; licenses: { assetId: number; seats: number; deviceId: number | null }[]; expectedFingerprint?: string;
}
export interface HandoverLine {
  assetId: number; assetTag: string; name: string; category: string; parentAssetId: number | null; seats: number;
  allocations: { allocationId: number | null; deviceId: number | null; seats: number; assignmentType: string; deviceTag?: string; deviceName?: string }[];
  details: Record<string, string | number | null>;
}
export interface Handover {
  transactionId: number | null; transactionCode: string | null; completedAt: string | null;
  status: 'DRAFT' | 'PENDING' | 'PARTIAL' | 'COMPLETED' | 'CANCELLED' | null; requesterId: number | null; requesterName: string | null; request: HandoverRequest | null;
  contentVersion?: number; groups?: WorkflowGroup[];
  recipientUserId: number; recipientName: string; recipientEmail: string | null; recipientEmployeeCode?: string | null; destinationLocationId: number;
  destinationLocationName: string; handoverDate: string; notes: string | null; fingerprint: string; lines: HandoverLine[];
  proposalLines?: HandoverLine[];
}
export const handoverApi = {
  candidates: (keyword = '', page = 0) => httpClient<ApiResponse<PageResponse<Candidate>>>(`/v1/handovers/candidates?${new URLSearchParams({ keyword, page: String(page), size: '10' })}`),
  users: (keyword = '', page = 0) => httpClient<ApiResponse<PageResponse<Recipient>>>(`/v1/users?${new URLSearchParams({ keyword, page: String(page), size: '10' })}`),
  holders: (keyword = '', page = 0, heldOnly = true) => httpClient<ApiResponse<PageResponse<Holder>>>(`/v1/handovers/holders?${new URLSearchParams({ keyword, page: String(page), size: '10', heldOnly: String(heldOnly) })}`),
  holder: (id: number, page = 0) => httpClient<ApiResponse<HolderDetail>>(`/v1/handovers/holders/${id}?${new URLSearchParams({ page: String(page), size: '10' })}`),
  preview: (body: HandoverRequest) => httpClient<ApiResponse<Handover>>('/v1/handovers/preview', { method: 'POST', body: JSON.stringify(body) }),
  create: (body: HandoverRequest) => httpClient<ApiResponse<Handover>>('/v1/handovers', { method: 'POST', body: JSON.stringify(body) }),
  edit: (id: number, body: HandoverRequest, version: number, reason = '') => httpClient<ApiResponse<Handover>>(`/v1/handovers/${id}`, { method: 'PUT', headers: { 'X-Expected-Version': String(version), ...(reason ? { 'X-Override-Reason': encodeURIComponent(reason.trim()) } : {}) }, body: JSON.stringify(body) }),
  submit: (id: number, version: number) => httpClient<ApiResponse<Handover>>(`/v1/handovers/${id}/submit`, { method: 'POST', headers: { 'X-Expected-Version': String(version) } }),
  withdraw: (id: number, version: number, reason = '') => httpClient<ApiResponse<Handover>>(`/v1/handovers/${id}/withdraw`, { method: 'POST', headers: { 'X-Expected-Version': String(version), ...(reason ? { 'X-Override-Reason': encodeURIComponent(reason.trim()) } : {}) } }),
  cancel: (id: number, version: number, reason = '') => httpClient<ApiResponse<Handover>>(`/v1/handovers/${id}/cancel`, { method: 'POST', headers: { 'X-Expected-Version': String(version), ...(reason ? { 'X-Override-Reason': encodeURIComponent(reason.trim()) } : {}) } }),
  confirm: (id: number, body: GroupAction) => httpClient<ApiResponse<Handover>>(`/v1/handovers/${id}/confirm`, { method: 'POST', body: JSON.stringify(body) }),
  cancelRemaining: (id: number, body: GroupAction) => httpClient<ApiResponse<Handover>>(`/v1/handovers/${id}/cancel-lines`, { method: 'POST', headers: { 'X-Override-Reason': encodeURIComponent(body.reason!.trim()) }, body: JSON.stringify(body) }),
  progress: (id: number) => httpClient<ApiResponse<{ contentVersion: number; groups: WorkflowGroup[] }>>(`/v1/handovers/${id}/progress`),
  detail: (id: number) => httpClient<ApiResponse<Handover>>(`/v1/handovers/${id}`),
};
