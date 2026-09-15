import { httpClient } from '@/shared/api/httpClient';
import { ApiResponse, PageResponse } from '@/features/catalogs/types/catalog.types';

export interface DisposalAsset { assetId: number; assetTag: string; name: string; category: string; autoAdded: boolean; }
export interface DisposalCheck { assets: DisposalAsset[]; warnings: string[]; fingerprint: string; }
export interface Disposal {
  transactionId: number; transactionCode: string; status: 'PENDING' | 'COMPLETED' | 'REJECTED';
  reason: string; disposalDate: string; createdAt: string; assets: DisposalAsset[]; warnings: string[]; fingerprint: string;
}
export interface DisposalRequest { assetIds: number[]; reason: string; disposalDate: string; expectedFingerprint: string; }
export interface TransactionSummary { transactionId: number; transactionCode: string; status: string; requesterName: string; createdAt: string; }

export const disposalApi = {
  candidates: (keyword = '', page = 0) => httpClient<ApiResponse<PageResponse<DisposalAsset>>>(`/v1/disposals/candidates?keyword=${encodeURIComponent(keyword)}&page=${page}&size=20`),
  listPending: () => httpClient<ApiResponse<PageResponse<TransactionSummary>>>('/v1/transactions?type=DISPOSAL&status=PENDING&page=0&size=50'),
  smartCheck: (assetIds: number[]) => httpClient<ApiResponse<DisposalCheck>>('/v1/disposals/smart-check', { method: 'POST', body: JSON.stringify({ assetIds }) }),
  create: (body: DisposalRequest) => httpClient<ApiResponse<Disposal>>('/v1/disposals', { method: 'POST', body: JSON.stringify(body) }),
  approve: (id: number) => httpClient<ApiResponse<Disposal>>(`/v1/disposals/${id}/approve`, { method: 'POST' }),
  reject: (id: number, reason: string) => httpClient<ApiResponse<Disposal>>(`/v1/disposals/${id}/reject`, { method: 'POST', body: JSON.stringify({ reason }) }),
};
