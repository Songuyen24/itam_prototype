import { useState } from 'react';
import { Outlet, NavLink, useLocation, useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { LanguageSwitcher } from '@/shared/components/LanguageSwitcher';
import { RoleSwitcher } from '@/shared/components/RoleSwitcher/RoleSwitcher';
import { useAuth } from '@/features/auth/contexts/AuthContext';
import { canManageInventory } from '@/features/auth/permissions';
import { DOCUMENT_ROLES } from '@/features/documents/types/document.types';
import { OverrideReasonDialog } from '@/features/auth/components/OverrideReasonDialog';
import { AccountModal } from '@/features/auth/pages/AccountPage';
import { demoLoginEnabled } from '@/features/auth/demo';
import { GlobalSearch } from './GlobalSearch';

function MainLayout() {
  const { t } = useTranslation('common');
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [accountOpen, setAccountOpen] = useState(false);
  const [collapsed, setCollapsed] = useState(false);
  const managementActive = ['/employee-profiles', '/catalogs', '/accounts', '/organization-catalogs'].includes(location.pathname);
  const [managementOpen, setManagementOpen] = useState(managementActive);
  const canViewInventory = canManageInventory(user?.role);
  const canViewProfiles = ['ADMIN', 'IT_STAFF', 'PUR_STAFF'].includes(user?.role || '');
  const iconLink = (to: string, icon: string, label: string) => (
    <NavLink to={to} title={collapsed ? label : undefined} aria-label={collapsed ? label : undefined}
      className={({ isActive }) => `nav-link ${isActive ? 'active' : ''}`}>
      <span className="nav-icon" aria-hidden="true">{icon}</span><span className="nav-label">{label}</span>
    </NavLink>
  );

  return (
    <div className="app-container">
      <OverrideReasonDialog />
      <AccountModal open={accountOpen} onClose={() => setAccountOpen(false)} />
      <aside className={`app-sidebar ${collapsed ? 'is-collapsed' : ''}`}>
        <div className="sidebar-header">
          <div className="sidebar-logo-icon" aria-hidden="true">IT</div>
          <div className="sidebar-title">ITAM Pro</div>
          <button type="button" className="sidebar-toggle" onClick={() => setCollapsed(!collapsed)}
            aria-label={t(collapsed ? 'nav.expandSidebar' : 'nav.collapseSidebar')}
            title={t(collapsed ? 'nav.expandSidebar' : 'nav.collapseSidebar')}
            aria-expanded={!collapsed}>{collapsed ? '›' : '‹'}</button>
        </div>

        <nav className="sidebar-nav" aria-label={t('nav.mainMenu')}>
          {canViewInventory && iconLink('/dashboard', '▦', t('nav.overview'))}
          {canViewProfiles && iconLink('/reports', '▤', t('nav.reports'))}
          {canViewProfiles && <div className="sidebar-group">
            <button type="button" className={`nav-link sidebar-group-toggle ${managementActive ? 'active' : ''}`}
              onClick={() => setManagementOpen(!managementOpen)} aria-expanded={managementOpen}
              aria-controls="management-links" aria-label={t('nav.management')}
              title={collapsed ? t('nav.management') : undefined}>
              <span className="nav-icon" aria-hidden="true">☷</span><span className="nav-label">{t('nav.management')}</span>
              <span className="sidebar-chevron" aria-hidden="true">{managementOpen ? '⌄' : '›'}</span>
            </button>
            {managementOpen && <div id="management-links" className="sidebar-subnav">
              {iconLink('/employee-profiles', '◉', t('auth:management.profiles'))}
              {canViewInventory && iconLink('/catalogs', '▣', t('nav.assetCatalogs'))}
              {user?.role === 'ADMIN' && iconLink('/accounts', '♙', t('auth:management.accounts'))}
              {canViewInventory && iconLink('/organization-catalogs', '⌘', t('nav.organizationCatalogs'))}
            </div>}
          </div>}
          {canViewInventory && iconLink('/assets', '▧', t('nav.assets'))}
          {user?.role === 'USER' && iconLink('/my-assets', '▧', t('nav.myAssets'))}
          {user?.role && DOCUMENT_ROLES.includes(user.role) && iconLink('/documents', '▤', t('documents:title'))}
          {user?.role && DOCUMENT_ROLES.includes(user.role) && iconLink('/receivings', '↓', t('documents:receiving.title'))}
          {canViewInventory && iconLink('/handovers', '→', t('nav.handover'))}
          {canViewInventory && iconLink('/recoveries', '←', t('nav.recovery'))}
          {canViewInventory && iconLink('/disposals', '×', t('nav.disposal'))}
          {user?.role === 'ADMIN' && iconLink('/audit-logs', '≡', t('audit:title'))}
        </nav>

        <div className="sidebar-footer">
          <div className="sidebar-account">
            <button className="sidebar-account-button" type="button" onClick={() => setAccountOpen(true)}
              title={t('nav.account')} aria-label={`${t('nav.account')}: ${user?.fullName || ''}`}>
              <span className="user-avatar" aria-hidden="true">{user?.fullName?.trim().slice(0, 1).toUpperCase() || '?'}</span>
              <span className="sidebar-account-name"><strong>{user?.fullName}</strong><small>{t(`auth:roles.${user?.role}`, user?.role || '')}</small></span>
            </button>
            <button className="sidebar-logout" type="button" title={t('auth:logout')} aria-label={t('auth:logout')}
              onClick={() => { void logout().then(() => navigate('/login', { replace: true })); }}>↪</button>
          </div>
          {demoLoginEnabled && <div className="sidebar-demo-switch"><RoleSwitcher /></div>}
        </div>
      </aside>

      <div className="app-content-wrapper">
        <header className="app-navbar"><div className="navbar-actions"><GlobalSearch /><LanguageSwitcher /></div></header>
        <main className="app-main"><Outlet /></main>
      </div>
    </div>
  );
}

export { MainLayout };
