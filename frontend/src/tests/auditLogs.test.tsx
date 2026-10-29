import { act, create, type ReactTestRenderer } from 'react-test-renderer';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { AuditLogsPage } from '@/features/audit/AuditLogsPage';

const client = vi.hoisted(() => vi.fn());
vi.mock('@/shared/api/httpClient', () => ({ httpClient: client }));
vi.mock('react-i18next', () => ({ useTranslation: () => ({ t: (key: string) => key, i18n: { language: 'vi' } }) }));
let tree: ReactTestRenderer;
const page = (number: number) => ({ data: { content: [{ id: number + 1, createdAt: '2026-01-01T00:00:00Z', actor: 'Admin',
  action: 'UPDATED', entityType: 'ASSET', entityId: 2, oldData: {}, newData: {} }], page: number, totalPages: 2, totalElements: 2 } });

beforeEach(() => { client.mockReset(); client.mockImplementation((url: string) => Promise.resolve(page(url.includes('page=1') ? 1 : 0))); });
afterEach(() => { if (tree) act(() => tree.unmount()); });
describe('M8 audit log', () => {
  it('filters on the server and resets paging when filters change', async () => {
    await act(async () => { tree = create(<AuditLogsPage />); });
    await act(async () => tree.root.findAllByType('button').find(button => button.children.includes('next'))!.props.onClick());
    expect(client.mock.calls[client.mock.calls.length - 1]?.[0]).toContain('page=1');
    const asset = tree.root.findAllByType('input')[0];
    await act(async () => asset.props.onChange({ target: { value: '55' } }));
    await act(async () => tree.root.findByType('form').props.onSubmit({ preventDefault: () => {} }));
    expect(client.mock.calls[client.mock.calls.length - 1]?.[0]).toContain('assetId=55');
    expect(client.mock.calls[client.mock.calls.length - 1]?.[0]).toContain('page=0');
  });
  it('clears selected details when paging and resetting filters', async () => {
    await act(async () => { tree = create(<AuditLogsPage />); });
    const view = () => tree.root.findAllByType('tr').find(row => row.props.tabIndex === 0)!;
    await act(async () => view().props.onClick({ currentTarget: { focus: () => {} } }));
    expect(tree.root.findAllByType('h2').length).toBeGreaterThan(0);
    await act(async () => tree.root.findAllByType('button').find(button => button.children.includes('next'))!.props.onClick());
    expect(tree.root.findAllByType('h2')).toHaveLength(0);
    const asset = tree.root.findAllByType('input')[0];
    await act(async () => asset.props.onChange({ target: { value: '55' } }));
    await act(async () => tree.root.findByType('form').props.onSubmit({ preventDefault: () => {} }));
    await act(async () => view().props.onClick({ currentTarget: { focus: () => {} } }));
    await act(async () => tree.root.findAllByType('button').find(button => button.children.includes('reset'))!.props.onClick());
    expect(tree.root.findAllByType('h2')).toHaveLength(0);
    expect(client.mock.calls[client.mock.calls.length - 1]?.[0]).not.toContain('assetId=');
  });
  it('opens a row with Enter and Space and marks it selected', async () => {
    await act(async () => { tree = create(<AuditLogsPage />); });
    const row = () => tree.root.findAllByType('tr').find(item => item.props.tabIndex === 0)!;
    const focus = vi.fn();
    const preventDefault = vi.fn();
    await act(async () => row().props.onKeyDown({ key: 'Enter', preventDefault, currentTarget: { focus } }));
    expect(row().props['aria-selected']).toBe(true);
    expect(preventDefault).toHaveBeenCalledOnce();
    await act(async () => tree.root.findAllByType('button').find(button => button.props.className === 'audit-close')!.props.onClick());
    expect(focus).toHaveBeenCalled();
    expect(row().props['aria-selected']).toBe(false);
    await act(async () => row().props.onKeyDown({ key: ' ', preventDefault, currentTarget: { focus } }));
    expect(row().props['aria-selected']).toBe(true);
    expect(preventDefault).toHaveBeenCalledTimes(2);
  });
  it('retries a failed request', async () => {
    client.mockRejectedValueOnce(new Error('offline'));
    await act(async () => { tree = create(<AuditLogsPage />); });
    expect(tree.root.findAllByProps({ role: 'alert' })).toHaveLength(1);
    await act(async () => tree.root.findAllByType('button').find(button => button.children.includes('retry'))!.props.onClick());
    expect(client).toHaveBeenCalledTimes(2);
    expect(tree.root.findAllByProps({ role: 'alert' })).toHaveLength(0);
  });
});
