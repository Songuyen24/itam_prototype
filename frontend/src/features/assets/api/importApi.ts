import { httpClient, downloadFile } from '@/shared/api/httpClient';
import { ApiResponse, PageResponse } from '@/features/catalogs/types/catalog.types';
import {
  ImportPreviewData,
  ImportConfirmPayload,
  ImportBatch,
  ImportBatchDetail,
  ImportRowDetail,
} from '../types/import.types';

export const importApi = {
  downloadTemplate: async (): Promise<void> => {
    return downloadFile('/v1/asset-imports/template', 'itam_asset_import_template.xlsx');
  },

  preview: async (file: File): Promise<ApiResponse<ImportPreviewData>> => {
    const formData = new FormData();
    formData.append('file', file);
    return httpClient<ApiResponse<ImportPreviewData>>('/v1/asset-imports/preview', {
      method: 'POST',
      body: formData,
    });
  },

  confirm: async (payload: ImportConfirmPayload): Promise<ApiResponse<ImportBatch>> => {
    return httpClient<ApiResponse<ImportBatch>>('/v1/asset-imports/confirm', {
      method: 'POST',
      body: JSON.stringify(payload),
    });
  },

  getBatches: async (page = 0, size = 20): Promise<ApiResponse<PageResponse<ImportBatch>>> => {
    return httpClient<ApiResponse<PageResponse<ImportBatch>>>(
      `/v1/asset-imports?page=${page}&size=${size}&sort=createdAt,desc`
    );
  },

  getBatchById: async (id: number): Promise<ApiResponse<ImportBatchDetail>> => {
    return httpClient<ApiResponse<ImportBatchDetail>>(`/v1/asset-imports/${id}`);
  },

  getBatchErrors: async (id: number): Promise<ApiResponse<ImportRowDetail[]>> => {
    return httpClient<ApiResponse<ImportRowDetail[]>>(`/v1/asset-imports/${id}/errors`);
  },
};
