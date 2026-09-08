import { ApiResponse, PageResponse } from '@/features/catalogs/types/catalog.types';
import { downloadFile, httpClient } from '@/shared/api/httpClient';
import { DocumentItem, DocumentTransaction, DocumentUploadPayload, TransactionFilters } from '../types/document.types';

export const documentApi = {
  getTransactions: (filters: TransactionFilters = {}): Promise<ApiResponse<PageResponse<DocumentTransaction>>> => {
    const params = new URLSearchParams({ page: String(filters.page ?? 0), size: String(filters.size ?? 10) });
    if (filters.type) params.set('type', filters.type);
    if (filters.status) params.set('status', filters.status);
    if (filters.keyword?.trim()) params.set('keyword', filters.keyword.trim());
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

  download: (document: Pick<DocumentItem, 'documentId' | 'originalFileName'>): Promise<void> =>
    downloadFile(`/v1/documents/${document.documentId}/download`, document.originalFileName),
};
