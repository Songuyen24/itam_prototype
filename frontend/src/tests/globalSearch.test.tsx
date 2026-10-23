import { act, create, type ReactTestRenderer } from 'react-test-renderer';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

const mocks = vi.hoisted(() => ({
  user: { id: 1, role: 'USER' },
  getAssets: vi.fn(), getMyAssets: vi.fn(), profiles: vi.fn(), navigate: vi.fn(),
}));
vi.mock('react-i18next', () => ({ useTranslation: () => ({ t: (key: string) => key }) }));
vi.mock('react-router-dom', () => ({ useNavigate: () => mocks.navigate }));
vi.mock('@/features/auth/contexts/AuthContext', () => ({ useAuth: () => ({ user: mocks.user }) }));
vi.mock('@/features/assets/api/assetApi', () => ({ assetApi: { getAssets: mocks.getAssets, getMyAssets: mocks.getMyAssets } }));
vi.mock('@/features/auth/api/managementApi', () => ({ managementApi: { profiles: mocks.profiles } }));

import { GlobalSearch } from '@/layouts/GlobalSearch';

const page = (content: unknown[], totalElements = content.length) => ({ data: { content, totalElements } });
function deferred() {
  let resolve!: (value: ReturnType<typeof page>) => void;
  const promise = new Promise<ReturnType<typeof page>>(ok => { resolve = ok; });
  return { promise, resolve };
}
let tree: ReactTestRenderer | null = null;
async function render() { await act(async () => { tree = create(<GlobalSearch />); }); return tree!; }
async function type(value: string) {
  await act(async () => { tree!.root.findByType('input').props.onChange({ target: { value } }); });
  await act(async () => { vi.advanceTimersByTime(251); });
}

describe('global search scope and race', () => {
  beforeEach(() => {
    vi.useFakeTimers();
    mocks.user = { id: 1, role: 'USER' };
    for (const fn of [mocks.getAssets, mocks.getMyAssets, mocks.profiles, mocks.navigate]) fn.mockReset();
  });
  afterEach(() => { if (tree) act(() => tree!.unmount()); tree = null; vi.useRealTimers(); });

  it('uses only the current USER asset endpoint, including results beyond the first page', async () => {
    mocks.getMyAssets.mockResolvedValue(page([{ assetId: 3, assetTag: 'OWN-1', name: 'Mine' }], 8));
    await render(); await type('own');
    expect(mocks.getMyAssets).toHaveBeenCalledWith({ keyword: 'own', page: 0, size: 5 });
    expect(mocks.getAssets).not.toHaveBeenCalled();
    expect(mocks.profiles).not.toHaveBeenCalled();
    const all = tree!.root.findAllByType('button').find(button => button.children.includes('search.allAssets'))!;
    act(() => all.props.onClick());
    expect(mocks.navigate).toHaveBeenCalledWith('/my-assets?keyword=own');
  });

  it('lets PUR find profiles without querying inventory', async () => {
    mocks.user = { id: 2, role: 'PUR_STAFF' };
    mocks.profiles.mockResolvedValue(page([{ id: 7, fullName: 'Unassigned', employeeCode: 'E-7', email: null }], 1));
    await render(); await type('E-7');
    expect(mocks.profiles).toHaveBeenCalledWith('E-7', 0);
    expect(mocks.getAssets).not.toHaveBeenCalled();
    expect(mocks.getMyAssets).not.toHaveBeenCalled();
    expect(JSON.stringify(tree!.toJSON())).toContain('Unassigned');
  });

  it('ignores a stale response after the query changes', async () => {
    const old = deferred(); const current = deferred();
    mocks.getMyAssets.mockReturnValueOnce(old.promise).mockReturnValueOnce(current.promise);
    await render(); await type('old'); await type('new');
    await act(async () => { current.resolve(page([{ assetId: 2, assetTag: 'NEW', name: 'Current' }])); });
    await act(async () => { old.resolve(page([{ assetId: 1, assetTag: 'OLD', name: 'Stale' }])); });
    expect(JSON.stringify(tree!.toJSON())).toContain('NEW');
    expect(JSON.stringify(tree!.toJSON())).not.toContain('OLD');
  });
});
