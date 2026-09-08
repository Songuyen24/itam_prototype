import { beforeEach, describe, expect, it, vi } from 'vitest';
import { renderToStaticMarkup } from 'react-dom/server';
import { StaticRouter } from 'react-router-dom/server';
import { AppRoutes } from '@/app/router';
import { AuthUser } from '@/features/auth/types/auth.types';

const session = vi.hoisted(() => ({
  user: null as AuthUser | null,
  token: null as string | null,
  isAuthenticated: false,
  loading: false,
}));

vi.mock('@/features/auth/contexts/AuthContext', () => ({ useAuth: () => session }));
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
  CatalogsPage: () => <div data-page="catalogs" />,
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
  beforeEach(() => {
    session.user = null;
    session.token = null;
    session.isAuthenticated = false;
    session.loading = false;
  });

  it('redirects unauthenticated direct navigation to login', () => {
    for (const path of ['/assets', '/catalogs', '/my-assets', '/account']) {
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
    const menu = renderRoute('/account');
    expect(menu).toContain('href="/assets"');
    expect(menu).toContain('href="/catalogs"');
    expect(menu).not.toContain('href="/users"');
  });

  it.each(['PUR_STAFF', 'USER'])('%s cannot open shared inventory or catalogs directly', (role) => {
    signIn(role);
    const destination = role === 'USER' ? '/my-assets' : '/account';
    for (const path of ['/assets?assignedTo=1', '/catalogs']) {
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

  it('PUR has an account destination without opening an unimplemented workflow', () => {
    signIn('PUR_STAFF');
    expect(renderRoute('/')).toContain('data-redirect="/account"');
    expect(renderRoute('/my-assets')).toContain('data-redirect="/account"');
    const html = renderRoute('/account');
    expect(html).toContain('current@itam.example');
    expect(html).not.toContain('data-redirect=');
  });

  it('unknown roles land on an allowed account page without redirect loops', () => {
    signIn('UNKNOWN');
    expect(renderRoute('/catalogs')).toContain('data-redirect="/account"');
    expect(renderRoute('/')).toContain('data-redirect="/account"');
    expect(renderRoute('/account')).not.toContain('data-redirect=');
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
