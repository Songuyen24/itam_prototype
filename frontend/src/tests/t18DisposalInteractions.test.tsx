import { act, create, ReactTestRenderer } from 'react-test-renderer';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { DisposalsPage } from '@/features/disposal/pages/DisposalsPage';
import { DisposalReview } from '@/features/disposal/components/DisposalReview';
import { ApiError } from '@/shared/api/httpClient';

const client = vi.hoisted(() => vi.fn());
const t = vi.hoisted(() => (key: string) => key);
vi.mock('@/shared/api/httpClient', async importOriginal => ({ ...(await importOriginal<typeof import('@/shared/api/httpClient')>()), httpClient: client }));
vi.mock('react-i18next', () => ({ useTranslation: () => ({ t }) }));
vi.mock('@/features/auth/contexts/AuthContext', () => ({ useAuth: () => ({ user: { role: 'ADMIN' } }) }));
vi.mock('@/features/documents/components/PublicationPanel', () => ({ PublicationPanel: () => <section>publication</section> }));

const page = <T,>(content: T[], totalPages = 1) => ({ data: { content, pageNumber: 0, pageSize: 20, totalElements: content.length, totalPages, first: true, last: totalPages <= 1, empty: content.length === 0 } });
const asset = (id: number) => ({ assetId: id, assetTag: `AST-${id}`, name: `Asset ${id}`, category: 'DEVICE', autoAdded: false });
const summary = { transactionId: 9, transactionCode: 'DIS-09', status: 'PENDING', requesterName: 'Mai', createdAt: '2026-09-14T10:00:00Z' };
const detail = (fingerprint = 'viewed', status: 'PENDING' | 'COMPLETED' | 'REJECTED' = 'PENDING') => ({
  transactionId: 9, transactionCode: 'DIS-09', status, actorName: 'Mai', reason: 'Broken', disposalDate: '2026-09-15',
  createdAt: '2026-09-14T10:00:00Z', assets: [asset(1)], warnings: [], perUserLinks: [], oemAllocations: [], fingerprint,
});
const renderers: ReactTestRenderer[] = [];

async function renderPage() {
  let tree!: ReactTestRenderer;
  await act(async () => { tree = create(<DisposalsPage />); });
  renderers.push(tree);
  return tree;
}

describe('T18 disposal interactions', () => {
  beforeEach(() => { client.mockReset(); });
  afterEach(() => { while (renderers.length) act(() => renderers.pop()!.unmount()); });

  it('restores focus to the trigger captured before asynchronous work', () => {
    const focus = vi.fn();
    const trigger = { current: { focus } as unknown as HTMLElement };
    vi.stubGlobal('document', {});
    let tree!: ReactTestRenderer;
    act(() => {
      tree = create(<DisposalReview value={{ assets: [], warnings: [], fingerprint: 'checked' }} saving={false}
        restoreFocusRef={trigger} onCancel={() => {}} onConfirm={() => {}} />, { createNodeMock: element => element.type === 'dialog' ? {
          open: false, showModal() { this.open = true; }, close() { this.open = false; }, querySelector: () => ({ focus() {} }),
        } : null });
    });
    act(() => tree.unmount());
    expect(focus).toHaveBeenCalledOnce();
    vi.unstubAllGlobals();
  });

  it('preserves selected asset IDs while paging candidates', async () => {
    client.mockImplementation((url?: string) => String(url).includes('/candidates')
      ? Promise.resolve(page([asset(String(url).includes('page=1') ? 2 : 1)], 2))
      : Promise.resolve(page([])));
    const tree = await renderPage();
    const checkbox = tree.root.findByProps({ type: 'checkbox' });
    act(() => checkbox.props.onChange());
    const next = tree.root.findAllByType('button').find(button => button.children.includes('next'))!;
    await act(async () => next.props.onClick());
    expect(tree.root.findAllByType('strong').some(node => node.children.includes('AST-2'))).toBe(true);
    const previous = tree.root.findAllByType('button').find(button => button.children.includes('previous'))!;
    await act(async () => previous.props.onClick());
    expect(tree.root.findByProps({ type: 'checkbox' }).props.checked).toBe(true);
  });

  it('ignores a stale candidate search response', async () => {
    let resolveOld!: (value: unknown) => void;
    client.mockImplementation((url?: string) => {
      const endpoint = String(url);
      if (!endpoint.includes('/candidates')) return Promise.resolve(page([]));
      if (endpoint.includes('keyword=old')) return new Promise(resolve => { resolveOld = resolve; });
      if (endpoint.includes('keyword=new')) return Promise.resolve(page([{ ...asset(3), assetTag: 'NEW' }]));
      return Promise.resolve(page([asset(1)]));
    });
    const tree = await renderPage();
    const search = tree.root.findByProps({ type: 'search' });
    await act(async () => search.props.onChange({ target: { value: 'old' } }));
    await act(async () => search.props.onChange({ target: { value: 'new' } }));
    await act(async () => resolveOld(page([{ ...asset(4), assetTag: 'OLD' }])));
    const tags = tree.root.findAllByType('strong').flatMap(node => node.children);
    expect(tags).toContain('NEW');
    expect(tags).not.toContain('OLD');
  });

  it('returns candidate and pending lists to their last valid page when totals shrink', async () => {
    let created = false;
    const candidateCalls: string[] = [];
    const first = { ...summary, transactionId: 8, transactionCode: 'DIS-08' };
    client.mockImplementation((url?: string) => {
      const endpoint = String(url);
      if (endpoint.includes('/candidates')) {
        candidateCalls.push(endpoint);
        if (endpoint.includes('page=1')) return Promise.resolve(page(created ? [] : [asset(2)], created ? 1 : 2));
        return Promise.resolve(page([asset(1)], created ? 1 : 2));
      }
      if (endpoint.startsWith('/v1/transactions')) {
        if (endpoint.includes('page=1')) return Promise.resolve(page(created ? [] : [summary], created ? 1 : 2));
        return Promise.resolve(page([first], created ? 1 : 2));
      }
      if (endpoint.endsWith('/smart-check')) return Promise.resolve({ data: { assets: [asset(2)], warnings: [], fingerprint: 'checked' } });
      if (endpoint === '/v1/disposals') { created = true; return Promise.resolve({ data: detail('created') }); }
      throw new Error(`Unexpected ${endpoint}`);
    });
    const tree = await renderPage();
    await act(async () => tree.root.findAllByType('button').filter(button => button.children.includes('next')).forEach(button => button.props.onClick()));
    act(() => tree.root.findByProps({ type: 'checkbox' }).props.onChange());
    act(() => tree.root.findByType('textarea').props.onChange({ target: { value: 'Broken' } }));
    await act(async () => tree.root.findByType('form').props.onSubmit({ preventDefault() {} }));
    await act(async () => tree.root.findAllByType('button').find(button => button.children.includes('confirmCreate'))!.props.onClick());
    expect(candidateCalls.filter(url => url.includes('page=0'))).toHaveLength(2);
    expect(tree.root.findAllByType('strong').some(node => node.children.includes('AST-1'))).toBe(true);
    expect(tree.root.findAllByType('strong').some(node => node.children.includes('DIS-08'))).toBe(true);
  });

  it('returns to the last valid pending page after processing its final row', async () => {
    let completed = false;
    const first = { ...summary, transactionId: 8, transactionCode: 'DIS-08' };
    client.mockImplementation((url?: string) => {
      const endpoint = String(url);
      if (endpoint.includes('/candidates')) return Promise.resolve(page([]));
      if (endpoint.startsWith('/v1/transactions')) {
        if (endpoint.includes('page=1')) return Promise.resolve(page(completed ? [] : [summary], completed ? 1 : 2));
        return Promise.resolve(page([first], completed ? 1 : 2));
      }
      if (endpoint === '/v1/disposals/9') return Promise.resolve({ data: detail() });
      if (endpoint.endsWith('/approve')) { completed = true; return Promise.resolve({ data: detail('done', 'COMPLETED') }); }
      throw new Error(`Unexpected ${endpoint}`);
    });
    const tree = await renderPage();
    await act(async () => tree.root.findAllByType('button').find(button => button.children.includes('next'))!.props.onClick());
    await act(async () => tree.root.findAllByType('button').find(button => button.children.includes('review'))!.props.onClick({ currentTarget: {} }));
    act(() => tree.root.findAllByType('button').find(button => button.children.includes('approve'))!.props.onClick());
    await act(async () => tree.root.findAllByType('button').find(button => button.children.includes('confirmApprove'))!.props.onClick());
    expect(tree.root.findAllByType('strong').some(node => node.children.includes('DIS-08'))).toBe(true);
  });

  it('closes a terminal detail loaded from a stale pending row without exposing mutations', async () => {
    client.mockImplementation((url?: string) => {
      const endpoint = String(url);
      if (endpoint.includes('/candidates')) return Promise.resolve(page([]));
      if (endpoint.startsWith('/v1/transactions')) return Promise.resolve(page([summary]));
      if (endpoint === '/v1/disposals/9') return Promise.resolve({ data: detail('done', 'COMPLETED') });
      throw new Error(`Unexpected mutation ${endpoint}`);
    });
    const tree = await renderPage();
    await act(async () => tree.root.findAllByType('button').find(button => button.children.includes('review'))!.props.onClick({ currentTarget: {} }));
    expect(tree.root.findAllByType('dialog')).toHaveLength(0);
    expect(client.mock.calls.some(call => /\/(approve|reject|per-user)$/.test(String(call[0])))).toBe(false);
  });

  it('reviews before approval, refreshes a conflict without retrying, then closes on terminal success', async () => {
    let viewed = 0;
    let approvals = 0;
    client.mockImplementation((url?: string) => {
      const endpoint = String(url);
      if (endpoint.includes('/candidates')) return Promise.resolve(page([]));
      if (endpoint.startsWith('/v1/transactions')) return Promise.resolve(page([summary]));
      if (endpoint === '/v1/disposals/9') return Promise.resolve({ data: detail(viewed++ === 0 ? 'viewed' : 'refreshed') });
      if (endpoint.endsWith('/approve')) {
        approvals += 1;
        if (approvals === 1) return Promise.reject(new ApiError('Changed', 409, 'DISPOSAL_CHANGED'));
        return Promise.resolve({ data: detail('done', 'COMPLETED') });
      }
      throw new Error(`Unexpected ${endpoint}`);
    });
    const tree = await renderPage();
    const review = tree.root.findAllByType('button').find(button => button.children.includes('review'))!;
    await act(async () => review.props.onClick({ currentTarget: {} }));
    expect(client.mock.calls.filter(call => String(call[0]).endsWith('/approve'))).toHaveLength(0);

    const chooseApprove = () => tree.root.findAllByType('button').find(button => button.children.includes('approve'))!;
    act(() => chooseApprove().props.onClick());
    await act(async () => tree.root.findAllByType('button').find(button => button.children.includes('confirmApprove'))!.props.onClick());
    expect(client.mock.calls.filter(call => String(call[0]).endsWith('/approve'))).toHaveLength(1);
    expect(client).toHaveBeenCalledWith('/v1/disposals/9/approve', { method: 'POST', body: JSON.stringify({ expectedFingerprint: 'viewed' }) });
    expect(tree.root.findAllByProps({ role: 'alert' }).some(node => node.children.includes('changed'))).toBe(true);

    act(() => chooseApprove().props.onClick());
    await act(async () => tree.root.findAllByType('button').find(button => button.children.includes('confirmApprove'))!.props.onClick());
    expect(client).toHaveBeenCalledWith('/v1/disposals/9/approve', { method: 'POST', body: JSON.stringify({ expectedFingerprint: 'refreshed' }) });
    expect(tree.root.findAllByType('dialog')).toHaveLength(0);
  });
});
