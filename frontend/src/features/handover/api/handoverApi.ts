import { httpClient } from '@/shared/api/httpClient';
import { ApiResponse, PageResponse } from '@/features/catalogs/types/catalog.types';

export interface Candidate { assetId: number; assetTag: string; name: string; category: string; assignmentType: string | null; availableSeats: number | null }
export interface Recipient { id: number; fullName: string; email: string; accountStatus: string }
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
  status: 'DRAFT' | 'PENDING' | 'COMPLETED' | 'CANCELLED' | null; requesterId: number | null; requesterName: string | null; request: HandoverRequest | null;
  recipientUserId: number; recipientName: string; recipientEmail: string; destinationLocationId: number;
  destinationLocationName: string; handoverDate: string; notes: string | null; fingerprint: string; lines: HandoverLine[];
}
export const handoverApi = {
  candidates: (keyword = '', page = 0) => httpClient<ApiResponse<PageResponse<Candidate>>>(`/v1/handovers/candidates?${new URLSearchParams({ keyword, page: String(page), size: '10' })}`),
  users: (keyword = '', page = 0) => httpClient<ApiResponse<PageResponse<Recipient>>>(`/v1/users?${new URLSearchParams({ keyword, page: String(page), size: '10' })}`),
  holders: (keyword = '', page = 0, heldOnly = true) => httpClient<ApiResponse<PageResponse<Holder>>>(`/v1/handovers/holders?${new URLSearchParams({ keyword, page: String(page), size: '10', heldOnly: String(heldOnly) })}`),
  holder: (id: number, page = 0) => httpClient<ApiResponse<HolderDetail>>(`/v1/handovers/holders/${id}?${new URLSearchParams({ page: String(page), size: '10' })}`),
  preview: (body: HandoverRequest) => httpClient<ApiResponse<Handover>>('/v1/handovers/preview', { method: 'POST', body: JSON.stringify(body) }),
  create: (body: HandoverRequest) => httpClient<ApiResponse<Handover>>('/v1/handovers', { method: 'POST', body: JSON.stringify(body) }),
  edit: (id: number, body: HandoverRequest, reason = '') => httpClient<ApiResponse<Handover>>(`/v1/handovers/${id}`, { method: 'PUT', headers: reason ? { 'X-Override-Reason': encodeURIComponent(reason.trim()) } : undefined, body: JSON.stringify(body) }),
  submit: (id: number) => httpClient<ApiResponse<Handover>>(`/v1/handovers/${id}/submit`, { method: 'POST' }),
  withdraw: (id: number, reason = '') => httpClient<ApiResponse<Handover>>(`/v1/handovers/${id}/withdraw`, { method: 'POST', headers: reason ? { 'X-Override-Reason': encodeURIComponent(reason.trim()) } : undefined }),
  cancel: (id: number, reason = '') => httpClient<ApiResponse<Handover>>(`/v1/handovers/${id}/cancel`, { method: 'POST', headers: reason ? { 'X-Override-Reason': encodeURIComponent(reason.trim()) } : undefined }),
  confirm: (id: number) => httpClient<ApiResponse<Handover>>(`/v1/handovers/${id}/confirm`, { method: 'POST' }),
  detail: (id: number) => httpClient<ApiResponse<Handover>>(`/v1/handovers/${id}`),
};
