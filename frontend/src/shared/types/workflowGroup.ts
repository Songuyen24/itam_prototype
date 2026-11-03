export interface WorkflowGroup {
  groupId: string;
  status: 'PENDING' | 'APPROVED' | 'COMPLETED' | 'REJECTED' | 'CANCELLED';
  assetIds: number[];
  allocationIds: number[];
  reason?: string | null;
  approvedAt?: string | null;
  dueDate?: string | null;
  executionDate?: string | null;
  documentId?: number | null;
}

export interface GroupAction {
  expectedVersion: number;
  groupIds: string[];
  reason?: string;
}
