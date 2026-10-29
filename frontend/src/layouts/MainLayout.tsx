import { useEffect, useRef, useState } from 'react';
import { Outlet, NavLink, useLocation, useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { LanguageSwitcher } from '@/shared/components/LanguageSwitcher';
import { useAuth } from '@/features/auth/contexts/AuthContext';
import { canManageInventory } from '@/features/auth/permissions';
import { DOCUMENT_ROLES } from '@/features/documents/types/document.types';
import { OverrideReasonDialog } from '@/features/auth/components/OverrideReasonDialog';
import { AccountModal } from '@/features/auth/pages/AccountPage';
import { GlobalSearch } from './GlobalSearch';

type IconName = 'overview' | 'assets' | 'receiving' | 'handover' | 'recovery' | 'disposal' | 'reports' | 'management' | 'accounts' | 'documents' | 'catalogs' | 'organization' | 'audit' | 'menu' | 'close' | 'chevron' | 'logout';

function Icon({ name }: { name: IconName }) {
  const paths: Record<IconName, React.ReactNode> = {
    overview: <><rect x="3" y="3" width="7" height="7" rx="1" /><rect x="14" y="3" width="7" height="7" rx="1" /><rect x="3" y="14" width="7" height="7" rx="1" /><rect x="14" y="14" width="7" height="7" rx="1" /></>,
    assets: <><rect x="3" y="5" width="18" height="16" rx="2" /><path d="M8 5V3h8v2M3 11h18" /></>,
    receiving: <><path d="M12 3v12m-4-4 4 4 4-4M4 17v4h16v-4" /></>,
    handover: <><path d="M4 12h15m-5-5 5 5-5 5M4 5h5v14H4" /></>,
    recovery: <><path d="M20 12H5m5-5-5 5 5 5m5-12h5v14h-5" /></>,
    disposal: <><path d="M4 7h16M9 7V4h6v3m-9 0 1 14h10l1-14M10 11v6m4-6v6" /></>,
    reports: <><path d="M4 20V4h16v16zM8 16v-4m4 4V8m4 8v-6" /></>,
    management: <><path d="M4 5h16M4 12h16M4 19h16" /><circle cx="8" cy="5" r="2" fill="currentColor" /><circle cx="16" cy="12" r="2" fill="currentColor" /></>,
    accounts: <><circle cx="12" cy="8" r="4" /><path d="M4 21v-2a8 8 0 0 1 16 0v2" /></>,
    documents: <><path d="M5 3h10l4 4v14H5zM15 3v5h4M8 12h8M8 16h8" /></>,
    catalogs: <><rect x="3" y="3" width="8" height="8" rx="1" /><rect x="13" y="3" width="8" height="8" rx="1" /><rect x="3" y="13" width="8" height="8" rx="1" /><rect x="13" y="13" width="8" height="8" rx="1" /></>,
    organization: <><path d="M12 3v7M5 21v-6h14v6M5 15v-5h14v5" /><circle cx="12" cy="3" r="2" /><circle cx="5" cy="21" r="2" /><circle cx="19" cy="21" r="2" /></>,
    audit: <><path d="M5 3h14v18H5zM8 8h8M8 12h8M8 16h5" /></>,
    menu: <path d="M4 6h16M4 12h16M4 18h16" />,
    close: <path d="M5 5l14 14M19 5 5 19" />,
    chevron: <path d="m9 5 7 7-7 7" />,
    logout: <><path d="M10 4H4v16h6M14 7l5 5-5 5M8 12h11" /></>,
  };
  return <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">{paths[name]}</svg>;
}

function MainLayout() {
  const { t } = useTranslation('common');
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [accountOpen, setAccountOpen] = useState(false);
  const [collapsed, setCollapsed] = useState(false);
  const [mobile, setMobile] = useState(() => typeof window !== 'undefined' && Boolean(window.matchMedia?.('(max-width: 768px)').matches));
  const [drawerOpen, setDrawerOpen] = useState(false);
  const dialogRef = useRef<HTMLDialogElement>(null);
  const menuRef = useRef<HTMLButtonElement>(null);
  const managementActive = ['/catalogs', '/accounts', '/documents', '/organization-catalogs'].includes(location.pathname);
  const [managementOpen, setManagementOpen] = useState(managementActive);
  const canViewInventory = canManageInventory(user?.role);
  const canViewProfiles = ['ADMIN', 'IT_STAFF', 'PUR_STAFF'].includes(user?.role || '');

  useEffect(() => {
    if (typeof window === 'undefined' || !window.matchMedia) return;
    const query = window.matchMedia('(max-width: 768px)');
    const update = () => setMobile(query.matches);
    update();
    query.addEventListener('change', update);
    return () => query.removeEventListener('change', update);
  }, []);
  useEffect(() => {
    if (!mobile || !drawerOpen) return;
    const dialog = dialogRef.current;
    dialog?.showModal();
    return () => dialog?.close();
  }, [mobile, drawerOpen]);

  const closeDrawer = () => { setDrawerOpen(false); menuRef.current?.focus(); };
  const iconLink = (to: string, icon: IconName, label: string) => (
    <NavLink to={to} title={label} aria-label={collapsed && !mobile ? label : undefined} onClick={mobile ? closeDrawer : undefined}
      className={({ isActive }) => `nav-link ${isActive ? 'active' : ''}`}>
      <span className="nav-icon"><Icon name={icon} /></span><span className="nav-label">{label}</span>
    </NavLink>
  );
  const sidebar = <aside className={`app-sidebar ${collapsed && !mobile ? 'is-collapsed' : ''}`}>
    <div className="sidebar-header">
      <div className="sidebar-logo-icon" aria-hidden="true">IT</div>
      <div className="sidebar-title">ITAM Pro</div>
      {mobile ? <button type="button" className="sidebar-toggle" onClick={closeDrawer} aria-label={t('nav.closeMenu')} title={t('nav.closeMenu')}><Icon name="close" /></button>
        : <button type="button" className="sidebar-toggle" onClick={() => setCollapsed(!collapsed)}
          aria-label={t(collapsed ? 'nav.expandSidebar' : 'nav.collapseSidebar')}
          title={t(collapsed ? 'nav.expandSidebar' : 'nav.collapseSidebar')} aria-expanded={!collapsed}><Icon name="chevron" /></button>}
    </div>
    <nav className="sidebar-nav" aria-label={t('nav.mainMenu')}>
      {canViewInventory && iconLink('/dashboard', 'overview', t('nav.overview'))}
      {canViewInventory && iconLink('/assets', 'assets', t('nav.assets'))}
      {user?.role === 'USER' && iconLink('/my-assets', 'assets', t('nav.myAssets'))}
      {user?.role && DOCUMENT_ROLES.includes(user.role) && iconLink('/receivings', 'receiving', t('documents:receiving.title'))}
      {canViewInventory && iconLink('/handovers', 'handover', t('nav.handover'))}
      {canViewInventory && iconLink('/recoveries', 'recovery', t('nav.recovery'))}
      {canViewInventory && iconLink('/disposals', 'disposal', t('nav.disposal'))}
      {canViewProfiles && iconLink('/reports', 'reports', t('nav.reports'))}
      {canViewProfiles && <div className="sidebar-group">
        <button type="button" className={`nav-link sidebar-group-toggle ${managementActive ? 'has-active-child' : ''}`}
          onClick={() => { if (collapsed && !mobile) { setCollapsed(false); setManagementOpen(true); } else setManagementOpen(!managementOpen); }} aria-expanded={managementOpen}
          aria-controls="management-links" aria-label={t('nav.management')} title={t('nav.management')}>
          <span className="nav-icon"><Icon name="management" /></span><span className="nav-label">{t('nav.management')}</span>
          <span className="sidebar-chevron"><Icon name="chevron" /></span>
        </button>
        {managementOpen && <div id="management-links" className="sidebar-subnav">
          {iconLink('/accounts', 'accounts', t('auth:management.accounts'))}
          {user?.role && DOCUMENT_ROLES.includes(user.role) && iconLink('/documents', 'documents', t('documents:title'))}
          {canViewInventory && iconLink('/catalogs', 'catalogs', t('nav.assetCatalogs'))}
          {canViewInventory && iconLink('/organization-catalogs', 'organization', t('nav.organizationCatalogs'))}
        </div>}
      </div>}
      {user?.role === 'ADMIN' && iconLink('/audit-logs', 'audit', t('audit:title'))}
    </nav>
    <div className="sidebar-footer"><div className="sidebar-account">
      <button className="sidebar-account-button" type="button" onClick={() => { if (mobile) closeDrawer(); setAccountOpen(true); }}
        title={t('nav.account')} aria-label={`${t('nav.account')}: ${user?.fullName || ''}`}>
        <span className="user-avatar" aria-hidden="true">{user?.fullName?.trim().slice(0, 1).toUpperCase() || '?'}</span>
        <span className="sidebar-account-name"><strong>{user?.fullName}</strong><small>{t(`auth:roles.${user?.role}`, user?.role || '')}</small></span>
      </button>
      <button className="sidebar-logout" type="button" title={t('auth:logout')} aria-label={t('auth:logout')}
        onClick={() => { if (mobile) closeDrawer(); void logout().then(() => navigate('/login', { replace: true })); }}><Icon name="logout" /></button>
    </div></div>
  </aside>;

  return <div className="app-container">
    <OverrideReasonDialog />
    <AccountModal open={accountOpen} onClose={() => setAccountOpen(false)} />
    {mobile ? <dialog ref={dialogRef} className="sidebar-dialog" onClose={closeDrawer} onCancel={closeDrawer} onClick={(event) => { if (event.target === event.currentTarget) closeDrawer(); }}>{drawerOpen && sidebar}</dialog> : sidebar}
    <div className="app-content-wrapper">
      <header className="app-navbar">
        {mobile && <button ref={menuRef} type="button" className="mobile-menu-trigger" onClick={() => setDrawerOpen(true)} aria-label={t('nav.openMenu')} title={t('nav.openMenu')} aria-expanded={drawerOpen}><Icon name="menu" /></button>}
        <div className="navbar-actions"><GlobalSearch /><LanguageSwitcher /></div>
      </header>
      <main className="app-main"><Outlet /></main>
    </div>
  </div>;
}

export { MainLayout };
