import { httpClient } from '@/shared/api/httpClient';
import { ApiResponse, PageResponse } from '@/features/catalogs/types/catalog.types';
import {
  Asset,
  AssetDetail,
  AssetFilterParams,
  CreateHardwareAssetPayload,
  UpdateHardwareAssetPayload,
  UniquenessValidationResult,
} from '../types/asset.types';

export const assetApi = {
  getAssets: (params: AssetFilterParams = {}) => {
    const query = new URLSearchParams();
    if (params.page !== undefined) query.append('page', String(params.page));
    if (params.size !== undefined) query.append('size', String(params.size));
    if (params.keyword) query.append('keyword', params.keyword);
    if (params.assetTag) query.append('assetTag', params.assetTag);
    if (params.serialNumber) query.append('serialNumber', params.serialNumber);
    if (params.typeId) query.append('typeId', String(params.typeId));
    if (params.categoryId) query.append('categoryId', String(params.categoryId));
    if (params.statusId) query.append('statusId', String(params.statusId));
    if (params.conditionId) query.append('conditionId', String(params.conditionId));
    if (params.modelId) query.append('modelId', String(params.modelId));
    if (params.departmentId) query.append('departmentId', String(params.departmentId));
    if (params.locationId) query.append('locationId', String(params.locationId));
    if (params.supplierId) query.append('supplierId', String(params.supplierId));
    if (params.assignedTo) query.append('assignedTo', String(params.assignedTo));

    return httpClient<ApiResponse<PageResponse<Asset>>>(`/v1/assets?${query.toString()}`);
  },

  getAssetById: (id: number) => httpClient<ApiResponse<AssetDetail>>(`/v1/assets/${id}`),

  createAsset: (payload: CreateHardwareAssetPayload) =>
    httpClient<ApiResponse<AssetDetail>>('/v1/assets', {
      method: 'POST',
      body: JSON.stringify(payload),
    }),

  updateAsset: (id: number, payload: UpdateHardwareAssetPayload) =>
    httpClient<ApiResponse<AssetDetail>>(`/v1/assets/${id}`, {
      method: 'PUT',
      body: JSON.stringify(payload),
    }),

  validateUniqueness: (params: { assetTag?: string; serialNumber?: string; excludeAssetId?: number }) =>
    httpClient<ApiResponse<UniquenessValidationResult>>('/v1/assets/validate-uniqueness', {
      method: 'POST',
      body: JSON.stringify(params),
    }),
};
