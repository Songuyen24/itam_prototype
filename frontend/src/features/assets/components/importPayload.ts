import { ImportConfirmPayload, ImportPreviewData } from '../types/import.types';

export function importPayload(preview: ImportPreviewData, retryBatchId?: number): ImportConfirmPayload {
  return {
    fileName: preview.fileName,
    validRows: preview.rows.map(row => ({...row.rawData, rowNumber: row.rowNumber})),
    ...(retryBatchId ? {retryBatchId} : {}),
  };
}
