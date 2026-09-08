export const DOCUMENT_ROLES = ['ADMIN', 'IT_STAFF', 'PUR_STAFF'];

export type TransactionType = 'IMPORT' | 'HANDOVER' | 'RECOVERY' | 'DISPOSAL';
export type TransactionStatus = 'PENDING' | 'COMPLETED' | 'REJECTED';
export type DocumentType =
  | 'INVOICE'
  | 'PURCHASE_ORDER'
  | 'IMPORT_RECEIPT'
  | 'HANDOVER_REPORT'
  | 'RECOVERY_REPORT'
  | 'DISPOSAL_REPORT'
  | 'CONTRACT'
  | 'OTHER';

export interface DocumentTransaction {
  transactionId: number;
  transactionCode: string;
  type: TransactionType;
  status: TransactionStatus;
  createdAt: string;
  documentsEditable: boolean;
  editBlockedReason?: string;
  expectedVersion?: number;
}

export interface DocumentItem {
  documentId: number;
  transactionId: number;
  assetId?: number | null;
  documentType: DocumentType;
  originalFileName: string;
  mimeType: string | null;
  fileSize: number | null;
  uploadedByName: string;
  createdAt: string;
  locked: boolean;
}

export interface TransactionFilters {
  type?: TransactionType;
  status?: TransactionStatus;
  keyword?: string;
  page?: number;
  size?: number;
}

export interface DocumentUploadPayload {
  file: File;
  transactionId: number;
  documentType: DocumentType;
  assetId?: number;
  expectedVersion?: number;
}
