import { httpClient } from '@/shared/api/httpClient';
import { ApiResponse } from '@/features/catalogs/types/catalog.types';

export interface SmartCheckResponse {
  requiredAssets: { assetId: number; assetTag: string; name: string; category: string; reason: string | null }[];
  optionalAssets: { allocationId: number; assetId: number; assetTag: string; name: string; seats: number; userId: number | null; userName: string | null; deviceId: number | null; deviceTag: string | null; selected: boolean }[];
  blockedAssets: { assetId: number; assetTag: string; name: string; category: string; reason: string }[];
  componentDecisions: { assetId: number; assetTag: string; name: string; options: { action: string; label: string; description: string }[]; defaultAction: string }[];
  warnings: string[];
  fingerprint: string;
}

export interface RecoveryRequest {
  returnerUserId: number;
  receivingLocationId: number;
  recoveryDate: string;
  reason: string;
  assetIds: number[];
  allocationIds: number[];
  componentActions: Record<number, { componentAction: string; recoverPerUser: boolean }>;
  perUserActions: Record<number, boolean>;
  expectedFingerprint: string;
}

export function buildRecoveryRequest(
  check: SmartCheckResponse,
  assetIds: number[],
  allocationIds: number[],
  reason: string,
  componentActions: Record<number, string> = {},
  perUserActions: Record<number, boolean> = Object.fromEntries(allocationIds.map(id => [id, true])),
): RecoveryRequest {
  return {
    returnerUserId: 0,
    receivingLocationId: 0,
    recoveryDate: '',
    reason,
    assetIds,
    allocationIds,
    componentActions: Object.fromEntries(Object.entries(componentActions).map(([id, action]) => [Number(id), {
      componentAction: action,
      recoverPerUser: false,
    }])),
    perUserActions,
    expectedFingerprint: check.fingerprint,
  };
}

export interface RecoveryLine {
  assetId: number;
  assetTag: string;
  name: string;
  category: string;
  parentAssetId: number | null;
  seats: number;
  allocations: { allocationId: number; seats: number; action: string; assignmentType: string }[];
  details: Record<string, unknown>;
}

export interface Recovery {
  transactionId: number | null;
  transactionCode: string | null;
  completedAt: string | null;
  returnerUserId: number;
  returnerName: string;
  returnerEmail: string;
  receivingLocationId: number;
  receivingLocationName: string;
  recoveryDate: string;
  reason: string;
  fingerprint: string;
  lines: RecoveryLine[];
}

export const recoveryApi = {
  smartCheck: (body: { returnerUserId: number; assetIds: number[]; allocationIds: number[]; reason: string }) =>
    httpClient<ApiResponse<SmartCheckResponse>>('/v1/recoveries/smart-check', { method: 'POST', body: JSON.stringify(body) }),
  complete: (body: RecoveryRequest) =>
    httpClient<ApiResponse<Recovery>>('/v1/recoveries', { method: 'POST', body: JSON.stringify(body) }),
};
