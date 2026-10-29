import { act, create, ReactTestRenderer } from 'react-test-renderer';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { HandoversPage } from '@/features/handover/pages/HandoversPage';

const client = vi.hoisted(() => vi.fn());
const translate = vi.hoisted(() => (key: string) => key);
const actor = vi.hoisted(() => ({ id: 1, role: 'IT_STAFF' }));
vi.mock('@/shared/api/httpClient', () => ({ httpClient: client }));
vi.mock('react-i18next', () => ({ useTranslation: () => ({ t: translate, i18n: { language: 'en' } }) }));
vi.mock('@/features/auth/contexts/AuthContext', () => ({ useAuth: () => ({ user: actor }) }));
vi.mock('@/features/documents/components/PublicationPanel', () => ({ PublicationPanel: () => <p>publication</p> }));

const holder = { id: 7, fullName: 'Mai', email: 'mai@example.com', employeeCode: null, departmentName: null,
  accountStatus: 'ACTIVE', deviceCount: 1, componentCount: 0, licenseSeats: 2 };
const line = { assetId: 8, assetTag: 'LAP-08', name: 'Laptop', category: 'DEVICE', parentAssetId: null, seats: 0, allocations: [], details: {} };
const handover = { transactionId: 11, transactionCode: 'HO-11', completedAt: null, status: 'PENDING', requesterId: 1, requesterName: 'ICT',
  request: null, recipientUserId: 7, recipientName: 'Mai', recipientEmail: 'mai@example.com', destinationLocationId: 2,
  destinationLocationName: 'Office', handoverDate: '2026-10-21', notes: null, fingerprint: 'fp', lines: [line] };
const page = (content: unknown[]) => ({ data: { content, pageNumber: 0, pageSize: 10, totalElements: content.length, totalPages: 1, last: true } });
const renderers: ReactTestRenderer[] = [];
async function renderPage() {
  let tree!: ReactTestRenderer;
  await act(async () => { tree = create(<HandoversPage />); });
  renderers.push(tree);
  return tree;
}
function button(tree: ReactTestRenderer, label: string) {
  return tree.root.findAllByType('button').find(item => item.children.includes(label))!;
}

describe('handover page interactions', () => {
  beforeEach(() => {
    client.mockReset();
    actor.id = 1; actor.role = 'IT_STAFF';
    client.mockImplementation((url: string) => {
      if (url.startsWith('/v1/handovers/holders/')) return Promise.resolve({ data: { holder, assets: page([]).data } });
      if (url.startsWith('/v1/handovers/holders?')) return Promise.resolve(page([holder]));
      if (url.startsWith('/v1/transactions?')) return Promise.resolve(page([{ transactionId: 11, transactionCode: 'HO-11', status: 'PENDING', requesterName: 'ICT' }]));
      if (url === '/v1/handovers/11') return Promise.resolve({ data: handover });
      return Promise.resolve(page([]));
    });
  });
  afterEach(() => { while (renderers.length) act(() => renderers.pop()!.unmount()); });

  it('shows current holders by default and can include employees without holdings', async () => {
    const tree = await renderPage();
    expect(client.mock.calls.some(call => String(call[0]).includes('heldOnly=true'))).toBe(true);
    await act(async () => tree.root.findAllByType('select')[0].props.onChange({ target: { value: 'all' } }));
    expect(client.mock.calls.some(call => String(call[0]).includes('heldOnly=false'))).toBe(true);
    expect(JSON.stringify(tree.toJSON())).toContain('Mai');
    expect(JSON.stringify(tree.toJSON())).toContain('HO-11');
    expect(tree.root.findAllByProps({ role: 'tab' })).toHaveLength(0);
    expect(tree.root.findAllByType('select')).toHaveLength(2);
  });
  it('keeps holder and receipt search/page independent', async () => {
    const tree = await renderPage();
    const inputs = tree.root.findAllByType('input').filter(input => input.props.type === 'search');
    expect(inputs).toHaveLength(2);
    await act(async () => inputs[0].props.onChange({ target: { value: 'Mai' } }));
    expect(client.mock.calls.some(call => String(call[0]).includes('/holders?keyword=Mai'))).toBe(true);
    expect(client.mock.calls.some(call => String(call[0]).includes('/transactions?') && String(call[0]).includes('keyword=Mai'))).toBe(false);
    await act(async () => inputs[1].props.onChange({ target: { value: 'HO-11' } }));
    expect(client.mock.calls.some(call => String(call[0]).includes('/transactions?') && String(call[0]).includes('keyword=HO-11'))).toBe(true);
    expect(tree.root.findAllByType('input').filter(input => input.props.type === 'search').map(input => input.props.value)).toEqual(['Mai', 'HO-11']);
    const holderCalls = client.mock.calls.filter(call => String(call[0]).includes('/holders?')).length;
    await act(async () => tree.root.findAllByType('select')[1].props.onChange({ target: { value: 'PENDING' } }));
    expect(client.mock.calls.some(call => String(call[0]).includes('/transactions?') && String(call[0]).includes('status=PENDING'))).toBe(true);
    expect(client.mock.calls.filter(call => String(call[0]).includes('/holders?'))).toHaveLength(holderCalls);
    const holderRow = tree.root.findAllByType('tr').find(row => row.findAllByType('td').some(cell => cell.children.includes('Mai')))!
    await act(async () => holderRow.findByType('button').props.onClick());
    expect(client.mock.calls.some(call => String(call[0]).startsWith('/v1/handovers/holders/7?'))).toBe(true);
    expect(tree.root.findAllByProps({ className: 'handover-confirm handover-detail-dialog' })).toHaveLength(1);
    await act(async () => tree.root.findAllByProps({ className: 'handover-confirm handover-detail-dialog' })[0].findAllByType('button').find(item => item.children.includes('close'))!.props.onClick());
    expect(tree.root.findAllByProps({ className: 'handover-confirm handover-detail-dialog' })).toHaveLength(0);
    const receiptRow = tree.root.findAllByType('tr').find(row => row.findAllByType('td').some(cell => cell.children.includes('HO-11')))!
    await act(async () => receiptRow.findByType('button').props.onClick());
    expect(tree.root.findAllByProps({ className: 'handover-confirm handover-detail-dialog' })).toHaveLength(1);
    await act(async () => tree.root.findAllByProps({ className: 'handover-confirm handover-detail-dialog' })[0].findAllByType('button').find(item => item.children.includes('close'))!.props.onClick());
    expect(tree.root.findAllByProps({ className: 'handover-confirm handover-detail-dialog' })).toHaveLength(0);
    expect(tree.root.findAllByType('input').filter(input => input.props.type === 'search').map(input => input.props.value)).toEqual(['Mai', 'HO-11']);
    expect(tree.root.findAllByType('select')[1].props.value).toBe('PENDING');
    expect(JSON.stringify(tree.toJSON())).toContain('HO-11');
  });
  it('filters receipts by created date without changing the holder query and shows holdings as a table', async () => {
    client.mockImplementation((url: string) => {
      if (url.startsWith('/v1/handovers/holders/')) return Promise.resolve({ data: { holder, assets: page([{ assetId: 8, assetTag: 'LAP-08', name: 'Laptop', category: 'DEVICE', allocationId: null, seats: 0, serialNumber: 'SER-8', deviceTag: null }]).data } });
      if (url.startsWith('/v1/handovers/holders?')) return Promise.resolve(page([holder]));
      if (url.startsWith('/v1/transactions?')) return Promise.resolve(page([{ transactionId: 11, transactionCode: 'HO-11', status: 'PENDING', requesterName: 'ICT' }]));
      return Promise.resolve(page([]));
    });
    const tree = await renderPage();
    const holderCalls = client.mock.calls.filter(call => String(call[0]).includes('/holders?')).length;
    await act(async () => tree.root.findAllByType('button').find(item => item.props['aria-label'] === 'createdRange')!.props.onClick());
    const day = tree.root.findAllByType('button').find(item => item.props['aria-pressed'] !== undefined && item.children.includes('8'))!;
    await act(async () => day.props.onClick());
    await act(async () => button(tree, 'receiving.applyRange').props.onClick());
    expect(client.mock.calls.some(call => String(call[0]).includes('/transactions?') && String(call[0]).includes('from=') && String(call[0]).includes('to='))).toBe(true);
    expect(client.mock.calls.filter(call => String(call[0]).includes('/holders?'))).toHaveLength(holderCalls);
    await act(async () => tree.root.findAllByType('tr').find(row => row.findAllByType('td').some(cell => cell.children.includes('Mai')))!.findByType('button').props.onClick());
    const dialog = tree.root.findAllByProps({ className: 'handover-confirm handover-detail-dialog' })[0];
    expect(dialog.findAllByType('th').map(th => th.children[0])).toEqual(['assetTag','assetName','category','serial','deviceTag','seats']);
    expect(tree.root.findAllByType('td').some(cell => cell.children.includes('SER-8'))).toBe(true);
  });

  it('requires the review dialog before confirming a pending physical handover', async () => {
    const tree = await renderPage();
    await act(async () => tree.root.findAllByType('tr').find(row => row.findAllByType('td').some(cell => cell.children.includes('HO-11')))!.findByType('button').props.onClick());
    expect(tree.root.findAllByProps({ className: 'handover-confirm handover-detail-dialog' })).toHaveLength(1);
    expect(client.mock.calls.some(call => String(call[0]).endsWith('/confirm'))).toBe(false);
    await act(async () => button(tree, 'confirmPhysical').props.onClick());
    expect(tree.root.findAllByProps({ id: 'physical-confirm-title' })).toHaveLength(1);
    expect(tree.root.findAllByType('strong').some(item => item.children.includes('LAP-08'))).toBe(true);
    expect(client.mock.calls.some(call => String(call[0]).endsWith('/confirm'))).toBe(false);
    expect(tree.root.findAllByType('p').some(item => item.children.includes('publication'))).toBe(false);
  });

  it('offers pending cancellation only to Admin, while the creator may withdraw', async () => {
    const tree = await renderPage();
    await act(async () => tree.root.findAllByType('tr').find(row => row.findAllByType('td').some(cell => cell.children.includes('HO-11')))!.findByType('button').props.onClick());
    expect(button(tree, 'withdraw')).toBeTruthy();
    expect(button(tree, 'cancel')).toBeUndefined();
    actor.id = 2; actor.role = 'ADMIN';
    await act(async () => tree.update(<HandoversPage />));
    expect(button(tree, 'cancel')).toBeTruthy();
  });
});
