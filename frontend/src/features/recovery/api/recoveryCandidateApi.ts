import { httpClient } from '@/shared/api/httpClient';
import { ApiResponse, PageResponse } from '@/features/catalogs/types/catalog.types';

export interface AssetCandidate {
  assetId: number;
  assetTag: string;
  name: string;
  category: string;
  licenseAssignmentTypeCode: string | null;
  assignedToUserId: number | null;
  assignedToFullName: string | null;
}

export const recoveryCandidateApi = {
  /** Get recovery candidates owned by / attached to the chosen user. */
  byUser: (userId: number, keyword = '', page = 0, size = 20) =>
    httpClient<ApiResponse<PageResponse<AssetCandidate>>>(
      `/v1/assets/recovery-candidates?userId=${userId}&keyword=${encodeURIComponent(keyword)}&page=${page}&size=${size}`
    ),
};
