import { downloadFile, httpClient } from '@/shared/api/httpClient';
import { ApiResponse } from '@/features/catalogs/types/catalog.types';

export interface DashboardSummary {
  byStatus: Record<string, number>; byType: Record<string, number>; byLocation: Record<string, number>;
  byDepartment: Record<string, number>; pendingReceivings: number; pendingDisposals: number;
}

export const dashboardApi = {
  summary: () => httpClient<ApiResponse<DashboardSummary>>('/v1/dashboard/summary'),
  exportAssets: () => downloadFile('/v1/reports/assets/export', 'itam-assets.xlsx'),
};
