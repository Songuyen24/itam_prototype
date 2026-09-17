import { httpClient } from '@/shared/api/httpClient';
import { ApiResponse, PageResponse } from '@/features/catalogs/types/catalog.types';

export interface DisposalAsset { assetId: number; assetTag: string; name: string; category: string; autoAdded: boolean; serialNumber?: string | null; }
export interface OemAllocation { allocationId: number; licenseAssetId: number; assetTag: string; deviceId: number; deviceTag: string; seats: number; }
export interface PerUserLink extends OemAllocation { userName: string; }
export interface DisposalCheck { assets: DisposalAsset[]; warnings: string[]; fingerprint: string; oemAllocations?: OemAllocation[]; }
export interface Disposal {
  transactionId: number; transactionCode: string; status: 'PENDING' | 'COMPLETED' | 'REJECTED';
  actorName: string; reason: string; disposalDate: string; createdAt: string; assets: DisposalAsset[]; warnings: string[];
  perUserLinks: PerUserLink[]; oemAllocations: OemAllocation[]; fingerprint: string;
}
export interface DisposalRequest { assetIds: number[]; reason: string; disposalDate: string; expectedFingerprint: string; }
export interface TransactionSummary { transactionId: number; transactionCode: string; status: string; requesterName: string; createdAt: string; }

export const disposalApi = {
  candidates: (keyword = '', page = 0) => httpClient<ApiResponse<PageResponse<DisposalAsset>>>(`/v1/disposals/candidates?keyword=${encodeURIComponent(keyword)}&page=${page}&size=20`),
  listPending: (page = 0) => httpClient<ApiResponse<PageResponse<TransactionSummary>>>(`/v1/transactions?type=DISPOSAL&status=PENDING&page=${page}&size=50`),
  smartCheck: (assetIds: number[]) => httpClient<ApiResponse<DisposalCheck>>('/v1/disposals/smart-check', { method: 'POST', body: JSON.stringify({ assetIds }) }),
  create: (body: DisposalRequest) => httpClient<ApiResponse<Disposal>>('/v1/disposals', { method: 'POST', body: JSON.stringify(body) }),
  detail: (id: number) => httpClient<ApiResponse<Disposal>>(`/v1/disposals/${id}`),
  resolvePerUser: (id: number, expectedFingerprint: string, decisions: { allocationId: number; release: boolean }[]) =>
    httpClient<ApiResponse<Disposal>>(`/v1/disposals/${id}/per-user`, { method: 'POST', body: JSON.stringify({ expectedFingerprint, decisions }) }),
  approve: (id: number, expectedFingerprint: string) => httpClient<ApiResponse<Disposal>>(`/v1/disposals/${id}/approve`, { method: 'POST', body: JSON.stringify({ expectedFingerprint }) }),
  reject: (id: number, reason: string) => httpClient<ApiResponse<Disposal>>(`/v1/disposals/${id}/reject`, { method: 'POST', body: JSON.stringify({ reason }) }),
};
