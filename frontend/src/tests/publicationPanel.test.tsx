import { renderToStaticMarkup } from 'react-dom/server';
import { act, create, ReactTestInstance, ReactTestRenderer } from 'react-test-renderer';
import { beforeEach, describe, expect, it, vi } from 'vitest';

const api = vi.hoisted(() => ({
  status: vi.fn(),
  regenerate: vi.fn(),
  resend: vi.fn(),
  download: vi.fn(),
  language: 'en',
  t: (key: string) => key,
  i18n: { get language() { return api.language; } },
}));

vi.mock('react-i18next', () => ({ useTranslation: () => ({ t: api.t, i18n: api.i18n }) }));
vi.mock('@/features/documents/api/publicationApi', () => ({ publicationApi: api }));
vi.mock('@/features/documents/api/documentApi', () => ({ documentApi: { download: api.download } }));

import { PublicationPanel, publicationPermissions } from '@/features/documents/components/PublicationPanel';

function deferred<T>() {
  let resolve!: (value: T) => void;
  let reject!: (reason: unknown) => void;
  const promise = new Promise<T>((yes, no) => { resolve = yes; reject = no; });
  return { promise, resolve, reject };
}

function status(transactionId: number, documentId: number) {
  return { data: {
    transactionId, transactionCode: `TX-${transactionId}`, transactionType: 'RECOVERY',
    pdf: { documentId, fileName: `TX-${transactionId}.pdf`, version: 1, templateVersion: 'v1', issuedAt: null, status: 'READY' as const },
    emails: [],
  } };
}

function button(root: ReactTestInstance, label: string) {
  return root.findAllByType('button').find(candidate => candidate.props.children === label)!;
}

async function mount(transactionId: number) {
  let renderer!: ReactTestRenderer;
  await act(async () => { renderer = create(<PublicationPanel transactionId={transactionId} canManage />); });
  return renderer;
}

describe('PublicationPanel request ownership', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    api.language = 'en';
  });

  it('keeps B selected when B status finishes before A', async () => {
    const a = deferred<ReturnType<typeof status>>();
    const b = deferred<ReturnType<typeof status>>();
    api.status.mockImplementation((id: number) => id === 1 ? a.promise : b.promise);
    const renderer = await mount(1);

    await act(async () => { renderer.update(<PublicationPanel transactionId={2} canManage />); });
    await act(async () => { b.resolve(status(2, 202)); await b.promise; });
    await act(async () => { a.resolve(status(1, 101)); await a.promise; });
    await act(async () => { button(renderer.root, 'publication.download').props.onClick(); });

    expect(api.download).toHaveBeenCalledWith({ documentId: 202, originalFileName: 'TX-2.pdf' });
    renderer.unmount();
  });

  it('disables stale download immediately and after B fails', async () => {
    api.status.mockResolvedValueOnce(status(1, 101));
    const renderer = await mount(1);
    expect(button(renderer.root, 'publication.download').props.disabled).toBe(false);

    const b = deferred<ReturnType<typeof status>>();
    api.status.mockReturnValueOnce(b.promise);
    renderer.update(<PublicationPanel transactionId={2} canManage />);
    expect(button(renderer.root, 'publication.download').props.disabled).toBe(true);
    await act(async () => { b.reject(new Error('B failed')); try { await b.promise; } catch { /* expected */ } });

    expect(renderer.root.findByProps({ role: 'alert' }).props.children).toBe('B failed');
    expect(button(renderer.root, 'publication.download').props.disabled).toBe(true);
    renderer.unmount();
  });

  it.each(['regenerate', 'resend'] as const)('ignores an old %s response after selecting B', async (actionName) => {
    const action = deferred<ReturnType<typeof status>>();
    api.status.mockImplementation((id: number) => Promise.resolve(status(id, id * 100)));
    api[actionName].mockReturnValue(action.promise);
    const renderer = await mount(1);
    await act(async () => { button(renderer.root, `publication.${actionName}`).props.onClick(); });
    await act(async () => { renderer.update(<PublicationPanel transactionId={2} canManage />); });
    await act(async () => { action.resolve(status(1, 999)); await action.promise; });
    await act(async () => { button(renderer.root, 'publication.download').props.onClick(); });

    expect(api.download).toHaveBeenLastCalledWith({ documentId: 200, originalFileName: 'TX-2.pdf' });
    renderer.unmount();
  });

  it('reloads status when the display language changes', async () => {
    api.status.mockResolvedValue(status(1, 101));
    const renderer = await mount(1);
    api.language = 'vi';
    await act(async () => { renderer.update(<PublicationPanel transactionId={1} canManage />); });
    expect(api.status).toHaveBeenCalledTimes(2);
    renderer.unmount();
  });
});

describe('publication action permissions', () => {
  it('shows only resend to managers for rejected imports', () => {
    const permissions = publicationPermissions('IMPORT', 'REJECTED', 'ADMIN');
    expect(permissions).toEqual({ canRegenerate: false, canResend: true });
    expect(publicationPermissions('IMPORT', 'REJECTED', 'IT_STAFF')).toEqual(permissions);
    const html = renderToStaticMarkup(<PublicationPanel transactionId={1} {...permissions} />);
    expect(html).toContain('publication.resend');
    expect(html).not.toContain('publication.regenerate');
  });

  it.each(['PUR_STAFF', 'USER'])('does not show retry actions to %s', (role) => {
    const permissions = publicationPermissions('IMPORT', 'REJECTED', role);
    expect(permissions).toEqual({ canRegenerate: false, canResend: false });
    const html = renderToStaticMarkup(<PublicationPanel transactionId={1} {...permissions} />);
    expect(html).not.toContain('publication.regenerate');
    expect(html).not.toContain('publication.resend');
  });

  it('keeps both actions for completed manager transactions', () => {
    expect(publicationPermissions('RECOVERY', 'COMPLETED', 'ADMIN')).toEqual({ canRegenerate: true, canResend: true });
  });
});
