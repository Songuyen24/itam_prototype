import { httpClient } from '@/shared/api/httpClient';
import { ApiResponse, PageResponse } from '@/features/catalogs/types/catalog.types';
import type { GroupAction, WorkflowGroup } from '@/shared/types/workflowGroup';

export interface DisposalAsset { assetId: number; assetTag: string; name: string; category: string; autoAdded: boolean; serialNumber?: string | null; }
export interface OemAllocation { allocationId: number; licenseAssetId: number; assetTag: string; deviceId: number; deviceTag: string; seats: number; }
export interface PerUserLink extends OemAllocation { userName: string; }
export interface DisposalCheck { assets: DisposalAsset[]; warnings: string[]; fingerprint: string; oemAllocations?: OemAllocation[]; }
export interface Disposal {
  transactionId: number; transactionCode: string; status: 'DRAFT' | 'PENDING' | 'PARTIAL' | 'APPROVED' | 'COMPLETED' | 'REJECTED' | 'CANCELLED'; requesterId?: number;
  contentVersion?: number; groups?: WorkflowGroup[];
  rejectionReason?: string | null; actorName: string; reason: string; disposalDate: string; createdAt: string; assets: DisposalAsset[]; warnings: string[];
  perUserLinks: PerUserLink[]; oemAllocations: OemAllocation[]; fingerprint: string;
}
export interface DisposalRequest { assetIds: number[]; reason: string; disposalDate: string; expectedFingerprint: string; expectedTransactionFingerprint?: string; }
export interface TransactionSummary { transactionId: number; transactionCode: string; status: string; requesterName: string; createdAt: string; }

export const disposalApi = {
  candidates: (keyword = '', page = 0) => httpClient<ApiResponse<PageResponse<DisposalAsset>>>(`/v1/disposals/candidates?keyword=${encodeURIComponent(keyword)}&page=${page}&size=20`),
  listPending: (page = 0) => httpClient<ApiResponse<PageResponse<TransactionSummary>>>(`/v1/transactions?type=DISPOSAL&status=PENDING&page=${page}&size=50`),
  smartCheck: (assetIds: number[]) => httpClient<ApiResponse<DisposalCheck>>('/v1/disposals/smart-check', { method: 'POST', body: JSON.stringify({ assetIds }) }),
  create: (body: DisposalRequest) => httpClient<ApiResponse<Disposal>>('/v1/disposals', { method: 'POST', body: JSON.stringify(body) }),
  createDraft: (body: DisposalRequest) => httpClient<ApiResponse<Disposal>>('/v1/disposals/draft', { method: 'POST', body: JSON.stringify(body) }),
  detail: (id: number) => httpClient<ApiResponse<Disposal>>(`/v1/disposals/${id}`),
  progress: (id: number) => httpClient<ApiResponse<{ contentVersion: number; groups: WorkflowGroup[] }>>(`/v1/disposals/${id}/progress`),
  edit: (id: number, body: DisposalRequest) => httpClient<ApiResponse<Disposal>>(`/v1/disposals/${id}`, { method: 'PUT', body: JSON.stringify(body) }),
  transition: (value: Disposal, action: 'withdraw'|'submit'|'cancel') => httpClient<ApiResponse<Disposal>>(`/v1/disposals/${value.transactionId}/${action}`, { method: 'POST', body: JSON.stringify({expectedFingerprint:value.fingerprint}) }),
  list: (page = 0, status = 'PENDING') => httpClient<ApiResponse<PageResponse<TransactionSummary>>>(`/v1/transactions?type=DISPOSAL&status=${status}&page=${page}&size=50`),
  resolvePerUser: (id: number, expectedFingerprint: string, decisions: { allocationId: number; release: boolean }[]) =>
    httpClient<ApiResponse<Disposal>>(`/v1/disposals/${id}/per-user`, { method: 'POST', body: JSON.stringify({ expectedFingerprint, decisions }) }),
  approve: (id: number, expectedFingerprint: string, body?: GroupAction) => httpClient<ApiResponse<Disposal>>(`/v1/disposals/${id}/${body ? 'approve-selected' : 'approve'}`, { method: 'POST', body: JSON.stringify(body ?? { expectedFingerprint }) }),
  reject: (id: number, reason: string, body?: GroupAction) => httpClient<ApiResponse<Disposal>>(`/v1/disposals/${id}/${body ? 'reject-selected' : 'reject'}`, { method: 'POST', body: JSON.stringify(body ?? { reason }) }),
  execute: (id: number, body: GroupAction & { documentId: number; executionDate: string }) => httpClient<ApiResponse<Disposal>>(`/v1/disposals/${id}/execute`, { method: 'POST', body: JSON.stringify(body) }),
  extend: (id: number, body: GroupAction & { dueDate: string }) => httpClient<ApiResponse<{ contentVersion: number; groups: WorkflowGroup[] }>>(`/v1/disposals/${id}/extend`, { method: 'POST', body: JSON.stringify(body) }),
  cancelApproved: (id: number, body: GroupAction) => httpClient<ApiResponse<Disposal>>(`/v1/disposals/${id}/cancel-lines`, { method: 'POST', body: JSON.stringify(body) }),
};
