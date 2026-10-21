import { useState } from 'react';
import { Outlet, NavLink, useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { LanguageSwitcher } from '@/shared/components/LanguageSwitcher';
import { RoleSwitcher } from '@/shared/components/RoleSwitcher/RoleSwitcher';
import { useAuth } from '@/features/auth/contexts/AuthContext';
import { canManageInventory } from '@/features/auth/permissions';
import { DOCUMENT_ROLES } from '@/features/documents/types/document.types';
import { OverrideReasonDialog } from '@/features/auth/components/OverrideReasonDialog';
import { AccountModal } from '@/features/auth/pages/AccountPage';
import { demoLoginEnabled } from '@/features/auth/demo';

function MainLayout() {
  const { t } = useTranslation('common');
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const [accountOpen, setAccountOpen] = useState(false);
  const canViewInventory = canManageInventory(user?.role);

  return (
    <div className="app-container">
      <OverrideReasonDialog />
      <AccountModal open={accountOpen} onClose={() => setAccountOpen(false)} />
      {/* Sidebar */}
      <aside className="app-sidebar">
        <div className="sidebar-header">
          <div className="sidebar-logo-icon">IT</div>
          <div>
            <div className="sidebar-title">ITAM Pro</div>
            <div className="sidebar-subtitle">IT Asset Management</div>
          </div>
        </div>

        <nav className="sidebar-nav">
          {['ADMIN', 'IT_STAFF', 'PUR_STAFF'].includes(user?.role || '') && <NavLink to="/employee-profiles" className={({isActive})=>`nav-link ${isActive?'active':''}`}>{t('auth:management.profiles')}</NavLink>}
          {user?.role === 'ADMIN' && <NavLink to="/accounts" className={({isActive})=>`nav-link ${isActive?'active':''}`}>{t('auth:management.accounts')}</NavLink>}
          {canViewInventory && (
            <NavLink
              to="/catalogs"
              className={({ isActive }) => `nav-link ${isActive ? 'active' : ''}`}
            >
              <span>📁</span>
              <span>{t('nav.catalogs', 'Quản lý Danh mục')}</span>
            </NavLink>
          )}

          {canViewInventory && (
            <NavLink
              to="/assets"
              className={({ isActive }) => `nav-link ${isActive ? 'active' : ''}`}
            >
              <span>💻</span>
              <span>{t('nav.assets', 'Tài sản thiết bị')}</span>
            </NavLink>
          )}

          {user?.role === 'USER' && (
            <NavLink to="/my-assets" className={({ isActive }) => `nav-link ${isActive ? 'active' : ''}`}>
              <span>{t('nav.myAssets')}</span>
            </NavLink>
          )}
          {user?.role && DOCUMENT_ROLES.includes(user.role) && (
            <NavLink to="/documents" className={({ isActive }) => `nav-link ${isActive ? 'active' : ''}`}>
              <span>{t('documents:title')}</span>
            </NavLink>
          )}
          {user?.role && DOCUMENT_ROLES.includes(user.role) && <NavLink to="/receivings" className={({isActive})=>`nav-link ${isActive?'active':''}`}><span>{t('documents:receiving.title')}</span></NavLink>}
          {canViewInventory && <NavLink to="/handovers" className={({ isActive }) => `nav-link ${isActive ? 'active' : ''}`}><span>{t('nav.handover')}</span></NavLink>}
          {canViewInventory && <NavLink to="/recoveries" className={({ isActive }) => `nav-link ${isActive ? 'active' : ''}`}><span>{t('nav.recovery')}</span></NavLink>}
          {canViewInventory && <NavLink to="/disposals" className={({ isActive }) => `nav-link ${isActive ? 'active' : ''}`}><span>{t('nav.disposal')}</span></NavLink>}
          {canViewInventory && <NavLink to="/dashboard" className={({ isActive }) => `nav-link ${isActive ? 'active' : ''}`}><span>{t('nav.overview')}</span></NavLink>}
        </nav>

        <div className="sidebar-account"><button className="sidebar-account-button" type="button" onClick={() => setAccountOpen(true)}><span className="user-avatar" aria-hidden="true">{user?.fullName?.trim().slice(0, 1).toUpperCase() || '?'}</span><span className="sidebar-account-name"><strong>{user?.fullName}</strong><small>{t(`auth:roles.${user?.role}`, user?.role || '')}</small></span></button><button className="sidebar-logout" type="button" title={t('auth:logout')} aria-label={t('auth:logout')} onClick={() => { void logout().then(() => navigate('/login', { replace: true })); }}>↪</button></div>
        {demoLoginEnabled && <div className="sidebar-demo-switch"><RoleSwitcher /></div>}
        <div style={{ padding: '16px', borderTop: '1px solid rgba(255,255,255,0.1)', fontSize: '12px', color: '#94a3b8' }}>
          {t('version', 'Phiên bản: 1.0.0 (ITAM Bilingual)')}
        </div>
      </aside>

      {/* Main Content Area */}
      <div className="app-content-wrapper">
        <header className="app-navbar">
          <div style={{ fontSize: '14px', fontWeight: 600, color: 'var(--text-muted)' }}>
            {t('systemTitle', 'HỆ THỐNG QUẢN LÝ VÀ THEO DÕI VÒNG ĐỜI TÀI SẢN CNTT')}
          </div>

          <div style={{ display: 'flex', alignItems: 'center', gap: '16px' }}>
            <LanguageSwitcher />
          </div>
        </header>

        <main className="app-main">
          <Outlet />
        </main>
      </div>
    </div>
  );
}

export { MainLayout };
