export type ValidationStatus = 'VALID' | 'INVALID' | 'DUPLICATE' | 'IMPORTED';

export type ImportBatchStatus = 'UPLOADED' | 'VALIDATED' | 'IMPORTING' | 'COMPLETED' | 'FAILED';

export interface ImportRowError {
  rowNumber: number;
  column: string;
  code: string;
  message: string;
}

export interface ImportRowDetail {
  rowNumber: number;
  validationStatus: ValidationStatus;
  errorMessage?: string;
  errors: ImportRowError[];
  rawData: Record<string, any>;
  assetId?: number;
}

export interface ImportPreviewData {
  fileName: string;
  totalRows: number;
  validRows: number;
  invalidRows: number;
  duplicateRows: number;
  rows: ImportRowDetail[];
}

export interface ImportConfirmPayload {
  fileName: string;
  validRows: Record<string, any>[];
}

export interface ImportBatch {
  importBatchId: number;
  fileName: string;
  status: ImportBatchStatus;
  totalRows: number;
  validRows: number;
  invalidRows: number;
  duplicateRows: number;
  importedRows: number;
  uploadedByUserId?: number;
  uploadedByFullName?: string;
  createdAt: string;
  completedAt?: string;
}

export interface ImportBatchDetail extends ImportBatch {
  rows: ImportRowDetail[];
}
