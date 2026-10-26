import { act, create, ReactTestRenderer } from 'react-test-renderer';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { MemoryRouter, useNavigate } from 'react-router-dom';
import { PaginatedContent } from '@/shared/components/PaginatedContent';
import { SmartCheckDialog } from '@/features/recovery/components/RecoveryForm';
import { DocumentsPage } from '@/features/documents/pages/DocumentsPage';

const t = (key: string) => key;
vi.mock('react-i18next', () => ({ useTranslation: () => ({ t }) }));
vi.mock('@/features/auth/contexts/AuthContext', () => ({ useAuth: () => ({ user: { role: 'IT_STAFF' } }) }));
vi.mock('@/features/documents/components/TransactionSelector', () => ({ TransactionSelector: () => null }));
vi.mock('@/features/documents/components/TransactionDocuments', () => ({ TransactionDocuments: ({ transactionId }: { transactionId: number | null }) => <output>{transactionId}</output> }));
let tree: ReactTestRenderer;
afterEach(() => { if (tree) act(() => tree.unmount()); });

describe('desktop pagination and document links', () => {
  it('pages rendering, preserves full selection, and clamps a removed last page', () => {
    const selected = new Set<number>();
    const rows = Array.from({ length: 41 }, (_, index) => index + 1);
    const render = (items: number[]) => <PaginatedContent items={items}>{page => <ul>{page.map(id => <li key={id}><input type="checkbox" onChange={() => selected.add(id)} />{id}</li>)}</ul>}</PaginatedContent>;
    act(() => { tree = create(render(rows)); });
    expect(tree.root.findAllByType('li')).toHaveLength(20);
    act(() => tree.root.findAllByType('input')[0].props.onChange());
    act(() => tree.root.findByProps({ 'aria-label': 'pagination.next' }).props.onClick());
    expect(tree.root.findAllByType('li')[0].children[1]).toBe('21');
    act(() => tree.root.findAllByType('input')[0].props.onChange());
    expect([...selected]).toEqual([1, 21]);
    act(() => tree.root.findByProps({ 'aria-label': 'pagination.next' }).props.onClick());
    expect(tree.root.findAllByType('li')).toHaveLength(1);
    act(() => tree.update(render(rows.slice(0, 21))));
    expect(tree.root.findAllByType('li')[0].children[1]).toBe('21');
    act(() => tree.update(render([])));
    expect(tree.root.findAllByType('li')).toHaveLength(0);
    expect(tree.root.findAllByType('button')).toHaveLength(0);
    expect(rows).toHaveLength(41);
  });
  it('submits recovery decisions from every page after editing page two', () => {
    const decisions = Array.from({ length: 21 }, (_, index) => ({ assetId: index + 1, assetTag: `C-${index + 1}`, name: 'Component', defaultAction: 'KEEP_ATTACHED', options: [{ action: 'KEEP_ATTACHED', label: 'keep', description: '' }, { action: 'DETACH', label: 'detach', description: '' }] }));
    const confirm = vi.fn();
    act(() => { tree = create(<SmartCheckDialog check={{ requiredAssets: [], optionalAssets: [], blockedAssets: [], componentDecisions: decisions, warnings: [], fingerprint: 'all-items' }} selectedAssetIds={[101]} selectedAllocationIds={[]} reason="Return" onConfirm={confirm} onCancel={() => {}} busy={false} t={t} />); });
    expect(tree.root.findAllByProps({ type: 'radio' })).toHaveLength(40);
    act(() => tree.root.findByProps({ 'aria-label': 'pagination.next' }).props.onClick());
    act(() => tree.root.findByProps({ name: 'comp-21', value: 'DETACH' }).props.onChange());
    act(() => tree.root.findAllByType('button').find(button => button.children.includes('complete'))!.props.onClick());
    expect(Object.keys(confirm.mock.calls[0][0].componentActions)).toHaveLength(21);
    expect(confirm.mock.calls[0][0].componentActions[1].componentAction).toBe('KEEP_ATTACHED');
    expect(confirm.mock.calls[0][0].componentActions[21].componentAction).toBe('DETACH');
    expect(confirm.mock.calls[0][0].assetIds).toEqual([101]);
  });
  it('opens the requested document and follows URL changes without retaining the previous selection', () => {
    function Navigate() { const navigate = useNavigate(); return <button onClick={() => navigate('/documents?transactionId=23')}>navigate</button>; }
    act(() => { tree = create(<MemoryRouter initialEntries={['/documents?transactionId=7']}><Navigate /><DocumentsPage /></MemoryRouter>); });
    expect(tree.root.findByType('output').children).toEqual(['7']);
    act(() => tree.root.findByType('button').props.onClick());
    expect(tree.root.findByType('output').children).toEqual(['23']);
  });
  it.each(['-1', 'abc', '1.5', '9007199254740992'])('ignores invalid document id %s', id => {
    act(() => { tree = create(<MemoryRouter initialEntries={[`/documents?transactionId=${id}`]}><DocumentsPage /></MemoryRouter>); });
    expect(tree.root.findByType('output').children).toEqual([]);
  });
});
