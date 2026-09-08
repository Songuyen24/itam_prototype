import { Outlet, NavLink } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { LanguageSwitcher } from '@/shared/components/LanguageSwitcher';
import { RoleSwitcher } from '@/shared/components/RoleSwitcher/RoleSwitcher';
import { useAuth } from '@/features/auth/contexts/AuthContext';
import { canManageInventory } from '@/features/auth/permissions';
import { DOCUMENT_ROLES } from '@/features/documents/types/document.types';

function MainLayout() {
  const { t } = useTranslation('common');
  const { user } = useAuth();
  const canViewInventory = canManageInventory(user?.role);

  return (
    <div className="app-container">
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
          <NavLink to="/account" className={({ isActive }) => `nav-link ${isActive ? 'active' : ''}`}>
            <span>{t('nav.account')}</span>
          </NavLink>
        </nav>

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
            <RoleSwitcher />
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
