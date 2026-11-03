import { beforeEach, describe, expect, it, vi } from 'vitest';
import { handoverApi } from '@/features/handover/api/handoverApi';
import { recoveryApi } from '@/features/recovery/api/recoveryApi';
import { disposalApi } from '@/features/disposal/api/disposalApi';

const client = vi.hoisted(() => vi.fn());
vi.mock('@/shared/api/httpClient', () => ({ httpClient: client }));

describe('group workflow requests', () => {
  beforeEach(() => client.mockReset());

  it('confirms selected handover groups against the viewed version', async () => {
    await handoverApi.progress(5);
    await handoverApi.confirm(5, { expectedVersion: 7, groupIds: ['asset:1'] });
    await handoverApi.cancelRemaining(5, { expectedVersion: 8, groupIds: ['asset:2'], reason: 'Missing' });
    expect(client.mock.calls).toEqual([
      ['/v1/handovers/5/progress'],
      ['/v1/handovers/5/confirm', { method: 'POST', body: JSON.stringify({ expectedVersion: 7, groupIds: ['asset:1'] }) }],
      ['/v1/handovers/5/cancel-lines', { method: 'POST', headers: { 'X-Override-Reason': 'Missing' }, body: JSON.stringify({ expectedVersion: 8, groupIds: ['asset:2'], reason: 'Missing' }) }],
    ]);
  });

  it('carries recovery versions and a reason for partial cancellation', async () => {
    await recoveryApi.submit(5, 7);
    await recoveryApi.confirm(5, { expectedVersion: 8, groupIds: ['asset:1'] });
    await recoveryApi.cancelRemaining(5, { expectedVersion: 9, groupIds: ['asset:2'], reason: 'Missing' });
    expect(client.mock.calls).toEqual([
      ['/v1/recoveries/5/submit', { method: 'POST', headers: { 'X-Expected-Version': '7' } }],
      ['/v1/recoveries/5/confirm', { method: 'POST', body: JSON.stringify({ expectedVersion: 8, groupIds: ['asset:1'] }) }],
      ['/v1/recoveries/5/cancel-remaining', { method: 'POST', headers: { 'X-Override-Reason': 'Missing' }, body: JSON.stringify({ expectedVersion: 9, groupIds: ['asset:2'], reason: 'Missing' }) }],
    ]);
  });

  it('separates disposal approval, execution, and extension', async () => {
    await disposalApi.progress(5);
    await disposalApi.approve(5, 'old', { expectedVersion: 7, groupIds: ['asset:1'] });
    await disposalApi.execute(5, { expectedVersion: 8, groupIds: ['asset:1'], documentId: 21, executionDate: '2026-11-03' });
    await disposalApi.extend(5, { expectedVersion: 9, groupIds: ['asset:2'], dueDate: '2026-11-20', reason: 'Awaiting vendor' });
    expect(client.mock.calls).toEqual([
      ['/v1/disposals/5/progress'],
      ['/v1/disposals/5/approve-selected', { method: 'POST', body: JSON.stringify({ expectedVersion: 7, groupIds: ['asset:1'] }) }],
      ['/v1/disposals/5/execute', { method: 'POST', body: JSON.stringify({ expectedVersion: 8, groupIds: ['asset:1'], documentId: 21, executionDate: '2026-11-03' }) }],
      ['/v1/disposals/5/extend', { method: 'POST', body: JSON.stringify({ expectedVersion: 9, groupIds: ['asset:2'], dueDate: '2026-11-20', reason: 'Awaiting vendor' }) }],
    ]);
  });
});
