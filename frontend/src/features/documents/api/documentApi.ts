import { ApiResponse, PageResponse } from '@/features/catalogs/types/catalog.types';
import { downloadFile, httpClient } from '@/shared/api/httpClient';
import { DocumentItem, DocumentLibraryItem, DocumentTransaction, DocumentUploadPayload, TransactionFilters, DocumentType, TransactionType } from '../types/document.types';

export const documentApi = {
  library: (filters: { keyword?: string; documentType?: DocumentType | ''; transactionType?: TransactionType | ''; transactionId?: number | null; page?: number; size?: number } = {}): Promise<ApiResponse<PageResponse<DocumentLibraryItem>>> => {
    const params = new URLSearchParams({ page: String(filters.page ?? 0), size: String(filters.size ?? 20) });
    if (filters.keyword?.trim()) params.set('keyword', filters.keyword.trim());
    if (filters.documentType) params.set('documentType', filters.documentType);
    if (filters.transactionType) params.set('transactionType', filters.transactionType);
    if (filters.transactionId) params.set('transactionId', String(filters.transactionId));
    return httpClient(`/v1/documents/library?${params}`);
  },
  getTransactions: (filters: TransactionFilters = {}): Promise<ApiResponse<PageResponse<DocumentTransaction>>> => {
    const params = new URLSearchParams({ page: String(filters.page ?? 0), size: String(filters.size ?? 10) });
    if (filters.type) params.set('type', filters.type);
    if (filters.status) params.set('status', filters.status);
    if (filters.statuses?.length) params.set('statuses', filters.statuses.join(','));
    if (filters.keyword?.trim()) params.set('keyword', filters.keyword.trim());
    if (filters.from) params.set('from', filters.from);
    if (filters.to) params.set('to', filters.to);
    return httpClient(`/v1/transactions?${params}`);
  },

  getTransaction: (transactionId: number): Promise<ApiResponse<DocumentTransaction>> =>
    httpClient(`/v1/transactions/${transactionId}`),

  getDocuments: (transactionId: number, page = 0, size = 10): Promise<ApiResponse<PageResponse<DocumentItem>>> => {
    const params = new URLSearchParams({ transactionId: String(transactionId), page: String(page), size: String(size) });
    return httpClient(`/v1/documents?${params}`);
  },

  upload: (payload: DocumentUploadPayload): Promise<ApiResponse<DocumentItem>> => {
    const body = new FormData();
    body.append('file', payload.file);
    body.append('transactionId', String(payload.transactionId));
    body.append('documentType', payload.documentType);
    if (payload.assetId !== undefined) body.append('assetId', String(payload.assetId));
    if (payload.expectedVersion !== undefined) body.append('expectedVersion', String(payload.expectedVersion));
    return httpClient('/v1/documents', { method: 'POST', body });
  },

  detach: (id: number, transactionId: number, expectedVersion: number): Promise<void> =>
    httpClient(`/v1/documents/${id}?transactionId=${transactionId}&expectedVersion=${expectedVersion}`, { method: 'DELETE' }),

  download: (document: Pick<DocumentItem, 'documentId' | 'originalFileName'>): Promise<void> =>
    downloadFile(`/v1/documents/${document.documentId}/download`, document.originalFileName),
  preview: async (documentId: number): Promise<Blob> => {
    const result = await httpClient<ApiResponse<{ mimeType: string; contentBase64: string }>>(`/v1/documents/${documentId}/preview`);
    const bytes = Uint8Array.from(atob(result.data.contentBase64), character => character.charCodeAt(0));
    return new Blob([bytes], { type: result.data.mimeType });
  },
};
