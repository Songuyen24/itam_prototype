import { ApiResponse } from '@/features/catalogs/types/catalog.types';
import { httpClient } from '@/shared/api/httpClient';

export interface PublicationStatus {
  transactionId: number;
  transactionCode: string;
  transactionType: string;
  pdf: { documentId: number | null; fileName: string | null; version: number | null; templateVersion: string | null; issuedAt: string | null; status: 'READY' | 'NOT_GENERATED' | 'FAILED'; errorMessage?: string | null };
  emails: Array<{ emailLogId: number; recipient: string; eventType: string; status: 'PENDING' | 'SENT' | 'FAILED'; errorMessage?: string | null; sentAt?: string | null; createdAt: string }>;
}

export const publicationApi = {
  status: (transactionId: number): Promise<ApiResponse<PublicationStatus>> => httpClient(`/v1/transactions/${transactionId}/publication`),
  emailLogs: (transactionId: number): Promise<ApiResponse<PublicationStatus['emails']>> => httpClient(`/v1/transactions/${transactionId}/email-logs`),
  regenerate: (transactionId: number): Promise<ApiResponse<PublicationStatus>> => httpClient(`/v1/transactions/${transactionId}/pdf/regenerate`, { method: 'POST' }),
  resend: (transactionId: number): Promise<ApiResponse<PublicationStatus>> => httpClient(`/v1/transactions/${transactionId}/email/resend`, { method: 'POST' }),
};
