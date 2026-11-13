import { act, create } from 'react-test-renderer';
import { renderToStaticMarkup } from 'react-dom/server';
import { describe, expect, it, vi } from 'vitest';
import { WorkflowGroupList } from '@/shared/components/WorkflowGroupList';
import { DisposalDetail } from '@/features/disposal/components/DisposalReview';

vi.mock('react-i18next', () => ({ useTranslation: () => ({ t: (key: string) => key }) }));
vi.mock('@/features/documents/components/PublicationPanel', () => ({ PublicationPanel: () => null }));

const groups = [
  { groupId: 'asset:1', status: 'PENDING' as const, assetIds: [1, 2], allocationIds: [9] },
  { groupId: 'allocation:10', status: 'PENDING' as const, assetIds: [3], allocationIds: [10] },
  { groupId: 'asset:4', status: 'COMPLETED' as const, assetIds: [4], allocationIds: [], reason: 'Received' },
];
const lines = [
  { assetId: 1, assetTag: 'LAP-1', name: 'Laptop' },
  { assetId: 2, assetTag: 'RAM-2', name: 'Memory' },
  { assetId: 3, assetTag: 'USR-3', name: 'License' },
  { assetId: 4, assetTag: 'LAP-4', name: 'Laptop' },
];

describe('workflow group selection', () => {
  it('selects an installed bundle as one group and leaves completed history read-only', () => {
    const selected: string[][] = [];
    const tree = create(<WorkflowGroupList groups={groups} lines={lines} selected={[]} onSelect={ids => selected.push(ids)} statusLabel={status => status} reasonLabel="Reason" />);
    const checkboxes = tree.root.findAllByType('input');
    expect(checkboxes).toHaveLength(2);
    expect(JSON.stringify(tree.toJSON())).toContain('LAP-1 Laptop, RAM-2 Memory');
    act(() => checkboxes[0].props.onChange());
    expect(selected).toEqual([['asset:1']]);
    expect(JSON.stringify(tree.toJSON())).toContain('Received');
    act(() => tree.unmount());
  });

  it('keeps pending approval and approved execution available on the same disposal', () => {
    const html = renderToStaticMarkup(<DisposalDetail value={{ transactionId: 5, transactionCode: 'D-5', status: 'APPROVED', actorName: 'Mai', reason: 'Old', disposalDate: '2026-11-03', createdAt: '2026-11-03', assets: lines.map(line => ({ ...line, category: 'DEVICE', autoAdded: false })), warnings: [], perUserLinks: [], oemAllocations: [], fingerprint: 'fp', groups: [{ ...groups[0] }, { ...groups[1], status: 'APPROVED' }] }} role="ADMIN" decisions={{}} saving={false} action={null} rejectReason="" onDecision={() => {}} onResolve={() => {}} onChooseAction={() => {}} onRejectReason={() => {}} onApprove={() => {}} onReject={() => {}} onClose={() => {}} />);
    expect(html).toContain('>approve</button>');
    expect(html).toContain('>execute</button>');
    expect(html).toContain('>cancelApproved</button>');
    const staffHtml = renderToStaticMarkup(<DisposalDetail value={{ transactionId: 5, transactionCode: 'D-5', status: 'APPROVED', actorName: 'Mai', reason: 'Old', disposalDate: '2026-11-03', createdAt: '2026-11-03', assets: [], warnings: [], perUserLinks: [], oemAllocations: [], fingerprint: 'fp', groups: [{ ...groups[1], status: 'APPROVED' }] }} role="IT_STAFF" decisions={{}} saving={false} action={null} rejectReason="" onDecision={() => {}} onResolve={() => {}} onChooseAction={() => {}} onRejectReason={() => {}} onApprove={() => {}} onReject={() => {}} onClose={() => {}} />);
    expect(staffHtml).toContain('>cancelApproved</button>');
  });

  it('requires selected groups and evidence before disposal execution', () => {
    const value = { transactionId: 5, transactionCode: 'D-5', status: 'APPROVED' as const, actorName: 'Mai', reason: 'Old', disposalDate: '2026-11-03', createdAt: '2026-11-03', assets: lines.map(line => ({ ...line, category: 'DEVICE', autoAdded: false })), warnings: [], perUserLinks: [], oemAllocations: [], fingerprint: 'fp', groups: [{ ...groups[0], status: 'APPROVED' as const }] };
    const props = { value, role: 'IT_STAFF', decisions: {}, saving: false, action: 'execute' as const, rejectReason: '', executionDate: '2026-11-03', onDecision: () => {}, onResolve: () => {}, onChooseAction: () => {}, onRejectReason: () => {}, onApprove: () => {}, onReject: () => {}, onClose: () => {} };
    const blocked = renderToStaticMarkup(<DisposalDetail {...props} selectedGroups={['asset:1']} documentId={0} />);
    expect(blocked).toMatch(/disabled=""[^>]*>confirmExecute<\/button>/);
    const ready = renderToStaticMarkup(<DisposalDetail {...props} selectedGroups={['asset:1']} documentId={21} />);
    expect(ready).toContain('>confirmExecute</button>');
    expect(ready).not.toMatch(/disabled=""[^>]*>confirmExecute<\/button>/);
  });

  it('shows actual execution date and evidence for a completed disposal group', () => {
    const html = renderToStaticMarkup(<DisposalDetail value={{ transactionId: 5, transactionCode: 'D-5', status: 'COMPLETED', actorName: 'Mai', reason: 'Old', disposalDate: '2026-11-01', createdAt: '2026-11-01', assets: lines.map(line => ({ ...line, category: 'DEVICE', autoAdded: false })), warnings: [], perUserLinks: [], oemAllocations: [], fingerprint: 'fp', groups: [{ ...groups[0], status: 'COMPLETED', executionDate: '2026-11-03', documentId: 21 }] }} role="ADMIN" decisions={{}} saving={false} action={null} rejectReason="" onDecision={() => {}} onResolve={() => {}} onChooseAction={() => {}} onRejectReason={() => {}} onApprove={() => {}} onReject={() => {}} onClose={() => {}} />);
    expect(html).toContain('executionDate: 2026-11-03');
    expect(html).toContain('evidenceReference: #21');
  });
});
