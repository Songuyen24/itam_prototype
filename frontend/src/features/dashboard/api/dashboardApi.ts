import { downloadFile, httpClient } from '@/shared/api/httpClient';
import { ApiResponse } from '@/features/catalogs/types/catalog.types';

export interface DashboardPeriod { period: 'thisMonth' | 'lastMonth' | 'custom'; from?: string; to?: string; }
export interface Movement { type: string; transactions: number; devices: number; components: number; packages: number; seats: number | null; recipients: number; }
export interface RecentTransaction extends Omit<Movement, 'transactions' | 'recipients'> { transaction_id: number; transaction_code: string; created_at: string; completed_at: string; actor: string; recipient: string; status: string; }
export interface DashboardSummary {
  from: string; to: string; damagedDevices: number;
  byStatus: Record<string, number>; byType: Record<string, number>; byLocation: Record<string, number>;
  byDepartment: Record<string, number>; pendingReceivings: number; pendingDisposals: number;
  inventory: {category: string; count: number; total_seats: number; active_seats: number; reserved_seats: number; available_seats: number}[];
  pending: Record<string, number>; drafts: Record<string, number>; failedEmails: number; failedPdfs: number;
  movements: Movement[]; daily: {day: string; type: string; transactions: number}[]; recent: RecentTransaction[];
}

export const dashboardApi = {
  summary: (filter?: DashboardPeriod) => {
    const params = filter ? new URLSearchParams(Object.entries(filter).filter(([,value]) => Boolean(value))) : null;
    return httpClient<ApiResponse<DashboardSummary>>(`/v1/dashboard/summary${params ? `?${params}` : ''}`);
  },
  exportAssets: () => downloadFile('/v1/reports/assets/export', 'itam-assets.xlsx'),
};
