import { beforeEach, describe, expect, it, vi } from 'vitest';
import { renderToStaticMarkup } from 'react-dom/server';
import { StaticRouter } from 'react-router-dom/server';
import { MemoryRouter } from 'react-router-dom';
import { act, create } from 'react-test-renderer';
import { AppRoutes } from '@/app/router';
import { MainLayout } from '@/layouts/MainLayout';
import { AuthUser } from '@/features/auth/types/auth.types';

const session = vi.hoisted(() => ({
  user: null as AuthUser | null,
  token: null as string | null,
  isAuthenticated: false,
  loading: false,
}));

vi.mock('@/features/auth/contexts/AuthContext', () => ({ useAuth: () => session }));
vi.mock('@/shared/components/RoleSwitcher/RoleSwitcher', () => ({ RoleSwitcher: () => null }));
vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string) => key,
    i18n: { language: 'en', changeLanguage: vi.fn() },
  }),
}));
vi.mock('react-router-dom', async (importOriginal) => ({
  ...await importOriginal<typeof import('react-router-dom')>(),
  Navigate: ({ to }: { to: string }) => <span data-redirect={to} />,
}));
vi.mock('@/features/catalogs/pages/CatalogsPage', () => ({
  CatalogsPage: ({ mode }: { mode?: string }) => <div data-page={mode === 'organization' ? 'organization-catalogs' : 'catalogs'} />,
}));
vi.mock('@/features/assets/pages/AssetsPage', () => ({
  AssetsPage: ({ myAssets }: { myAssets?: boolean }) => <div data-page={myAssets ? 'my-assets' : 'assets'} />,
}));

function signIn(role: string) {
  session.user = { id: 1, role, email: 'current@itam.example', fullName: 'Current Account' };
  session.token = `token-${role}`;
  session.isAuthenticated = true;
}

function renderRoute(path: string) {
  return renderToStaticMarkup(<StaticRouter location={path}><AppRoutes /></StaticRouter>);
}

describe('Role-specific routes and navigation', () => {
  it('lets staff open unified accounts while keeping USER out', () => {
    signIn('ADMIN');
    expect(renderRoute('/user-access')).toContain('data-redirect="/accounts"');
    expect(renderRoute('/accounts')).toContain('management.accounts');
    for (const role of ['IT_STAFF','PUR_STAFF']) {
      signIn(role);
      expect(renderRoute('/accounts')).toContain('management.accounts');
    }
    signIn('USER');
    expect(renderRoute('/accounts')).toContain('data-redirect="/my-assets"');
  });
  beforeEach(() => {
    session.user = null;
    session.token = null;
    session.isAuthenticated = false;
    session.loading = false;
  });

  it('redirects unauthenticated direct navigation to login', () => {
    for (const path of ['/assets', '/catalogs', '/my-assets', '/account', '/handovers', '/disposals', '/dashboard']) {
      const html = renderRoute(path);
      expect(html).toContain('data-redirect="/login"');
      expect(html).not.toContain('data-page=');
    }
  });

  it.each(['ADMIN', 'IT_STAFF'])('%s can open inventory and catalogs', (role) => {
    signIn(role);
    expect(renderRoute('/')).toContain('data-redirect="/assets"');
    expect(renderRoute('/assets')).toContain('data-page="assets"');
    expect(renderRoute('/catalogs')).toContain('data-page="catalogs"');
    expect(renderRoute('/organization-catalogs')).toContain('data-page="organization-catalogs"');
    const menu = renderRoute('/catalogs');
    expect(menu).toContain('href="/assets"');
    expect(menu).toContain('href="/catalogs"');
    expect(menu).toContain('href="/handovers"');
    expect(menu).toContain('href="/disposals"');
    expect(menu).toContain('href="/dashboard"');
    expect(renderRoute('/handovers')).toContain('handover-page');
    expect(menu).not.toContain('href="/users"');
  });

  it('orders management links by role and gives collapsed controls accessible names', () => {
    signIn('ADMIN');
    const admin = renderRoute('/catalogs');
    const paths = ['/accounts', '/catalogs', '/organization-catalogs'];
    expect(paths.map(path => admin.indexOf(`href="${path}"`))).toEqual([...paths.map(path => admin.indexOf(`href="${path}"`))].sort((a, b) => a - b));
    expect(paths.every(path => admin.includes(`href="${path}"`))).toBe(true);
    signIn('PUR_STAFF');
    const pur = renderRoute('/accounts');
    expect(pur).toContain('href="/accounts"');
    expect(pur).not.toContain('href="/catalogs"');
    expect(pur).not.toContain('management.addProfile');
    expect(pur).not.toContain('href="/organization-catalogs"');

    signIn('ADMIN');
    let tree!: ReturnType<typeof create>;
    act(() => { tree = create(<MemoryRouter initialEntries={['/catalogs']}><MainLayout /></MemoryRouter>); });
    const toggle = tree.root.findByProps({ 'aria-label': 'nav.collapseSidebar' });
    act(() => toggle.props.onClick());
    expect(tree.root.findByProps({ 'aria-label': 'nav.expandSidebar' })).toBeTruthy();
    expect(tree.root.findByProps({ 'aria-label': 'nav.management' }).props['aria-expanded']).toBe(true);
    expect(tree.root.findAllByType('a').filter(link => link.props['aria-label'] === 'nav.assetCatalogs')).toHaveLength(1);
    act(() => tree.unmount());
  });

  it.each(['PUR_STAFF', 'USER'])('%s cannot open shared inventory or catalogs directly', (role) => {
    signIn(role);
    const destination = role === 'USER' ? '/my-assets' : '/accounts';
    for (const path of ['/assets?assignedTo=1', '/catalogs', '/handovers', '/disposals', '/dashboard']) {
      const html = renderRoute(path);
      expect(html).toContain(`data-redirect="${destination}"`);
      expect(html).not.toContain('data-page=');
      expect(html).not.toContain('href="/assets"');
      expect(html).not.toContain('href="/catalogs"');
      expect(html).not.toContain('href="/users"');
    }
  });

  it('USER starts at a personal read-only asset route', () => {
    signIn('USER');
    expect(renderRoute('/')).toContain('data-redirect="/my-assets"');
    const html = renderRoute('/my-assets?assignedTo=99&userId=99');
    expect(html).toContain('data-page="my-assets"');
    expect(html).toContain('href="/my-assets"');
  });

  it('PUR can read accounts without editing', () => {
    signIn('PUR_STAFF');
    expect(renderRoute('/')).toContain('data-redirect="/accounts"');
    expect(renderRoute('/my-assets')).toContain('data-redirect="/accounts"');
    const html = renderRoute('/accounts');
    expect(html).toContain('management.accounts');
    expect(html).not.toContain('management.addProfile');
    expect(html).not.toContain('data-redirect=');
    expect(renderRoute('/employee-profiles?keyword=E-7')).toContain('data-redirect="/accounts?keyword=E-7"');
  });

  it('unknown roles are sent to login without opening management routes', () => {
    signIn('UNKNOWN');
    expect(renderRoute('/catalogs')).toContain('data-redirect="/login"');
    expect(renderRoute('/')).toContain('data-redirect="/login"');
    expect(renderRoute('/account')).toContain('data-redirect="/login"');
  });

  it('hides the previous account layout during authentication or switching', () => {
    signIn('ADMIN');
    session.loading = true;
    const html = renderRoute('/assets');
    expect(html).toContain('role="status"');
    expect(html).not.toContain('data-page=');
    expect(html).not.toContain('Current Account');
    expect(html).not.toContain('href="/catalogs"');
  });
});
