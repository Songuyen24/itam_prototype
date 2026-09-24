import { httpClient } from '@/shared/api/httpClient';
import { ApiResponse, PageResponse } from '@/features/catalogs/types/catalog.types';

export interface Candidate { assetId: number; assetTag: string; name: string; category: string; assignmentType: string | null; availableSeats: number | null }
export interface Recipient { id: number; fullName: string; email: string; accountStatus: string }
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
  recipientUserId: number; recipientName: string; recipientEmail: string; destinationLocationId: number;
  destinationLocationName: string; handoverDate: string; notes: string | null; fingerprint: string; lines: HandoverLine[];
}
export const handoverApi = {
  candidates: (keyword = '', page = 0) => httpClient<ApiResponse<PageResponse<Candidate>>>(`/v1/handovers/candidates?${new URLSearchParams({ keyword, page: String(page), size: '10' })}`),
  users: (keyword = '', page = 0) => httpClient<ApiResponse<PageResponse<Recipient>>>(`/v1/users?${new URLSearchParams({ keyword, page: String(page), size: '10' })}`),
  preview: (body: HandoverRequest) => httpClient<ApiResponse<Handover>>('/v1/handovers/preview', { method: 'POST', body: JSON.stringify(body) }),
  complete: (body: HandoverRequest) => httpClient<ApiResponse<Handover>>('/v1/handovers', { method: 'POST', body: JSON.stringify(body) }),
  detail: (id: number) => httpClient<ApiResponse<Handover>>(`/v1/handovers/${id}`),
};
