import { act, create, type ReactTestRenderer } from 'react-test-renderer';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { MemoryRouter } from 'react-router-dom';

const mocks = vi.hoisted(() => ({ getTransactions: vi.fn(), library: vi.fn(), createDraft: vi.fn(), user: { id: 2, role: 'PUR_STAFF' } }));
vi.mock('react-i18next', () => { const t = (key: string) => key; return { useTranslation: () => ({ t, i18n: { language: 'en' } }) }; });
vi.mock('@/features/auth/contexts/AuthContext', () => ({ useAuth: () => ({ user: mocks.user }) }));
vi.mock('@/features/documents/api/documentApi', () => ({ documentApi: { getTransactions: mocks.getTransactions, library: mocks.library } }));
vi.mock('@/features/receiving/api/receivingApi', () => ({ receivingApi: { create: mocks.createDraft } }));
vi.mock('@/features/documents/components/TransactionDocuments', () => ({ TransactionDocuments: ({ transactionId }: { transactionId: number | null }) => <output>{transactionId}</output> }));

import { DocumentsPage } from '@/features/documents/pages/DocumentsPage';
import { TransactionSelector } from '@/features/documents/components/TransactionSelector';
import { ReceivingDateRangePicker } from '@/features/documents/components/ReceivingDateRangePicker';

const rows = [
  { transactionId: 1, transactionCode: 'IMP-D', type: 'IMPORT', status: 'DRAFT', requesterName: 'Lan', createdAt: '2026-10-28T00:00:00Z' },
  { transactionId: 2, transactionCode: 'IMP-P', type: 'IMPORT', status: 'PENDING', requesterName: 'Mai', createdAt: '2026-10-27T00:00:00Z' },
  { transactionId: 3, transactionCode: 'IMP-C', type: 'IMPORT', status: 'COMPLETED', requesterName: 'An', createdAt: '2026-10-26T00:00:00Z' },
];
let tree: ReactTestRenderer | null = null;
afterEach(() => { if (tree) act(() => tree!.unmount()); tree = null; });
const response = (content = rows, page = 0) => ({ success: true, data: { content, page, totalPages: 3, totalElements: 42 } });
const buttons = () => tree!.root.findAllByType('button');

describe('receiving list and receipt modal', () => {
  it('selects a range across months with keyboard-accessible date buttons', async () => {
    const changed = vi.fn();
    await act(async () => { tree = create(<ReceivingDateRangePicker from="2026-10-01" to="2026-10-01" onChange={changed} />); });
    await act(async () => buttons().find(button => button.props['aria-expanded'] === false)!.props.onClick());
    const day = (value: string) => buttons().find(button => button.props['aria-label'] === value)!;
    await act(async () => day('10/31/2026').props.onClick());
    await act(async () => buttons().find(button => button.props['aria-label'] === 'receiving.nextMonth')!.props.onClick());
    await act(async () => day('11/02/2026').props.onClick());
    await act(async () => buttons().find(button => button.children.includes('receiving.applyRange'))!.props.onClick());
    expect(changed).toHaveBeenCalledWith('2026-10-31', '2026-11-02');
  });
  it('queries one server page for all statuses and resets pagination on status/search', async () => {
    mocks.getTransactions.mockReset().mockResolvedValue(response());
    mocks.library.mockResolvedValue({ data: { content: [], totalPages: 0, totalElements: 0 } });
    await act(async () => { tree = create(<TransactionSelector receiving selectedId={null} onSelect={vi.fn()} />); });
    expect(mocks.getTransactions).toHaveBeenCalledWith({ keyword: '', type: 'IMPORT', status: undefined, from: undefined, to: undefined, page: 0, size: 20 });
    expect(tree!.root.findAllByType('tr')).toHaveLength(4);
    expect(JSON.stringify(tree!.toJSON())).toContain('Lan');
    await act(async () => buttons().find(button => button.props['aria-label'] === 'pagination.next')!.props.onClick());
    expect(mocks.getTransactions).toHaveBeenLastCalledWith(expect.objectContaining({ page: 1 }));
    await act(async () => tree!.root.findByType('select').props.onChange({ target: { value: 'PENDING' } }));
    expect(mocks.getTransactions).toHaveBeenLastCalledWith(expect.objectContaining({ status: 'PENDING', page: 0 }));
    await act(async () => tree!.root.findAllByType('input').find(input => input.props.type === 'search')!.props.onChange({ target: { value: 'IMP-C' } }));
    expect(mocks.getTransactions).toHaveBeenLastCalledWith(expect.objectContaining({ keyword: 'IMP-C', status: 'PENDING', page: 0 }));
    await act(async () => buttons().find(button => button.props['aria-label'] === 'pagination.next')!.props.onClick());
    const date = (day: number) => `${new Date().getFullYear()}-${String(new Date().getMonth() + 1).padStart(2, '0')}-${String(day).padStart(2, '0')}`;
    const pick = (day: number) => tree!.root.findAllByType('button').find(button => button.props['aria-pressed'] !== undefined && button.children.includes(String(day)))!;
    await act(async () => buttons().find(button => button.children.includes('receiving.allDates'))!.props.onClick());
    expect(tree!.root.findAllByProps({ role: 'dialog' })).toHaveLength(1);
    await act(async () => pick(8).props.onClick());
    const beforeApply = mocks.getTransactions.mock.calls.length;
    expect(beforeApply).toBeGreaterThan(0);
    await act(async () => buttons().find(button => button.children.includes('receiving.applyRange'))!.props.onClick());
    expect(mocks.getTransactions).toHaveBeenLastCalledWith(expect.objectContaining({ from: date(8), to: date(8), page: 0 }));
    await act(async () => buttons().find(button => button.props['aria-expanded'] === false)!.props.onClick());
    await act(async () => pick(15).props.onClick());
    await act(async () => pick(5).props.onClick());
    await act(async () => buttons().find(button => button.children.includes('receiving.applyRange'))!.props.onClick());
    expect(mocks.getTransactions).toHaveBeenLastCalledWith(expect.objectContaining({ from: date(5), to: date(15), status: 'PENDING', keyword: 'IMP-C', page: 0 }));
    const beforeCancel = mocks.getTransactions.mock.calls.length;
    await act(async () => buttons().find(button => button.props['aria-expanded'] === false)!.props.onClick());
    await act(async () => pick(20).props.onClick());
    await act(async () => buttons().find(button => button.children.includes('receiving.cancel'))!.props.onClick());
    expect(mocks.getTransactions).toHaveBeenCalledTimes(beforeCancel);
    await act(async () => buttons().find(button => button.props['aria-expanded'] === false)!.props.onClick());
    await act(async () => buttons().find(button => button.children.includes('receiving.clearRange'))!.props.onClick());
    expect(mocks.getTransactions).toHaveBeenLastCalledWith(expect.objectContaining({ from: undefined, to: undefined, page: 0 }));
  });

  it('opens a receiving receipt in a modal while documents retains the split workspace', async () => {
    mocks.getTransactions.mockReset().mockResolvedValue(response());
    mocks.createDraft.mockReset().mockResolvedValue({ data: { transactionId: 25 } });
    await act(async () => { tree = create(<MemoryRouter><DocumentsPage receiving /></MemoryRouter>); });
    expect(tree!.root.findAllByProps({ role: 'tab' })).toHaveLength(0);
    expect(tree!.root.findAllByProps({ role: 'dialog' })).toHaveLength(0);
    await act(async () => buttons().find(button => button.children.includes('actions.openReceipt'))!.props.onClick());
    expect(tree!.root.findAllByProps({ role: 'dialog' })).toHaveLength(1);
    expect(tree!.root.findByType('output').children).toEqual(['1']);
    act(() => buttons().find(button => button.children.includes('actions.close'))!.props.onClick());
    expect(tree!.root.findAllByProps({ role: 'dialog' })).toHaveLength(0);
    await act(async () => buttons().find(button => button.children.includes('draft.create'))!.props.onClick());
    expect(mocks.createDraft).toHaveBeenCalledOnce();
    expect(tree!.root.findByType('output').children).toEqual(['25']);
    expect(tree!.root.findAllByProps({ role: 'dialog' })).toHaveLength(1);
    act(() => tree!.unmount()); tree = null;
    await act(async () => { tree = create(<MemoryRouter><DocumentsPage /></MemoryRouter>); });
    expect(tree!.root.findAllByProps({ role: 'tab' })).toHaveLength(0);
    expect(tree!.root.findAllByProps({ role: 'dialog' })).toHaveLength(0);
  });
});
