import { act, create, type ReactTestRenderer } from 'react-test-renderer';
import { describe, expect, it, vi } from 'vitest';
const mocks = vi.hoisted(() => ({ action: vi.fn(), content: vi.fn(), revisions: vi.fn(), events: vi.fn() }));
vi.mock('react-i18next', () => { const t = (key: string) => key; return { useTranslation: () => ({ t, i18n: { language: 'en' } }) }; });
vi.mock('@/features/auth/contexts/AuthContext', () => ({ useAuth: () => ({ user: { id: 1, role: 'IT_STAFF' } }) }));
vi.mock('@/features/receiving/api/receivingApi', () => ({ receivingApi: mocks }));
vi.mock('@/features/assets/components/AssetImportModal', () => ({ AssetImportModal: () => null }));
import { ReceiptSubModal } from '@/features/documents/components/ReceiptSubModal';
import { ImportDraftPanel } from '@/features/documents/components/ImportDraftPanel';

describe('receiving confirmation', () => {
  it('defers approval until confirmation and permits cancellation without an API mutation', async () => {
    mocks.content.mockResolvedValue({ data: { assets: [], notes: '' } });
    mocks.revisions.mockResolvedValue({ data: [] });
    mocks.events.mockResolvedValue({ data: [] });
    mocks.action.mockResolvedValue({});
    const changed = vi.fn();
    let tree!: ReactTestRenderer;
    await act(async () => { tree = create(<ImportDraftPanel compact onChanged={changed} transaction={{ transactionId: 8, transactionCode: 'IMP-8', type: 'IMPORT', status: 'PENDING', createdAt: '2026-01-01', documentsEditable: false, expectedVersion: 3 }} />); });
    const button = (label: string) => tree.root.findAllByType('button').find(node => node.children.includes(label))!;
    await act(async () => button('receiving.approve').props.onClick());
    expect(mocks.action).not.toHaveBeenCalled();
    expect(tree.root.findAllByProps({ role: 'dialog' })).toHaveLength(1);
    await act(async () => button('receiving.cancel').props.onClick());
    expect(mocks.action).not.toHaveBeenCalled();
    expect(tree.root.findAllByProps({ role: 'dialog' })).toHaveLength(0);
    await act(async () => button('receiving.approve').props.onClick());
    await act(async () => button('receiving.confirmAction').props.onClick());
    expect(mocks.action).toHaveBeenCalledTimes(1);
    expect(mocks.action.mock.calls[0][1]).toBe('approve');
    expect(changed).toHaveBeenCalledOnce();
    expect(JSON.stringify(tree.toJSON())).toContain('receiving.noRecordedAssets');
    act(() => tree.unmount());
  });
});

it('consumes child Escape before unmount, and never closes while busy', async () => {
  let handler: ((event: KeyboardEvent) => void) | undefined;
  const close = vi.fn();
  const node = { focus: vi.fn(), querySelector: vi.fn(() => null) };
  vi.stubGlobal('document', {
    activeElement: { focus: vi.fn() },
    querySelectorAll: () => [node],
    querySelector: () => null,
    addEventListener: (_name: string, callback: (event: KeyboardEvent) => void) => { handler = callback; },
    removeEventListener: vi.fn(),
  });
  let tree!: ReactTestRenderer;
  try {
    await act(async () => { tree = create(<ReceiptSubModal title="Confirm" busy onClose={close}>Body</ReceiptSubModal>, { createNodeMock: element => element.props.className === 'receiving-submodal' ? node : null }); });
    const event = { key: 'Escape', defaultPrevented: false, preventDefault: vi.fn(), stopImmediatePropagation: vi.fn() };
    act(() => handler!(event as unknown as KeyboardEvent));
    expect(event.preventDefault).toHaveBeenCalledOnce();
    expect(event.stopImmediatePropagation).toHaveBeenCalledOnce();
    expect(close).not.toHaveBeenCalled();
    await act(async () => tree.update(<ReceiptSubModal title="Confirm" onClose={close}>Body</ReceiptSubModal>));
    act(() => handler!(event as unknown as KeyboardEvent));
    expect(close).toHaveBeenCalledOnce();
  } finally {
    if (tree) act(() => tree.unmount());
    vi.unstubAllGlobals();
  }
});
