import { useEffect, useRef, useState } from 'react';
import { Outlet, NavLink, useLocation, useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { LanguageSwitcher } from '@/shared/components/LanguageSwitcher';
import { useAuth } from '@/features/auth/contexts/AuthContext';
import { canManageInventory } from '@/features/auth/permissions';
import { httpClient } from '@/shared/api/httpClient';
import { DOCUMENT_ROLES } from '@/features/documents/types/document.types';
import { OverrideReasonDialog } from '@/features/auth/components/OverrideReasonDialog';
import { AccountModal } from '@/features/auth/pages/AccountPage';
import { GlobalSearch } from './GlobalSearch';
import DashboardOutlinedIcon from '@mui/icons-material/DashboardOutlined';
import Inventory2OutlinedIcon from '@mui/icons-material/Inventory2Outlined';
import FileDownloadOutlinedIcon from '@mui/icons-material/FileDownloadOutlined';
import OutboundOutlinedIcon from '@mui/icons-material/OutboundOutlined';
import AssignmentReturnOutlinedIcon from '@mui/icons-material/AssignmentReturnOutlined';
import DeleteOutlineOutlinedIcon from '@mui/icons-material/DeleteOutlineOutlined';
import AssessmentOutlinedIcon from '@mui/icons-material/AssessmentOutlined';
import TuneOutlinedIcon from '@mui/icons-material/TuneOutlined';
import PersonOutlineOutlinedIcon from '@mui/icons-material/PersonOutlineOutlined';
import DescriptionOutlinedIcon from '@mui/icons-material/DescriptionOutlined';
import CategoryOutlinedIcon from '@mui/icons-material/CategoryOutlined';
import AccountTreeOutlinedIcon from '@mui/icons-material/AccountTreeOutlined';
import FactCheckOutlinedIcon from '@mui/icons-material/FactCheckOutlined';
import MenuOutlinedIcon from '@mui/icons-material/MenuOutlined';
import CloseOutlinedIcon from '@mui/icons-material/CloseOutlined';
import ChevronRightOutlinedIcon from '@mui/icons-material/ChevronRightOutlined';
import LogoutOutlinedIcon from '@mui/icons-material/LogoutOutlined';

type IconName = 'overview' | 'assets' | 'receiving' | 'handover' | 'recovery' | 'disposal' | 'reports' | 'management' | 'accounts' | 'documents' | 'catalogs' | 'organization' | 'audit' | 'menu' | 'close' | 'chevron' | 'logout';

function Icon({ name }: { name: IconName }) {
  const icons: Record<IconName, React.ReactNode> = {
    overview: <DashboardOutlinedIcon />, assets: <Inventory2OutlinedIcon />,
    receiving: <FileDownloadOutlinedIcon />, handover: <OutboundOutlinedIcon />,
    recovery: <AssignmentReturnOutlinedIcon />, disposal: <DeleteOutlineOutlinedIcon />,
    reports: <AssessmentOutlinedIcon />, management: <TuneOutlinedIcon />,
    accounts: <PersonOutlineOutlinedIcon />, documents: <DescriptionOutlinedIcon />,
    catalogs: <CategoryOutlinedIcon />, organization: <AccountTreeOutlinedIcon />,
    audit: <FactCheckOutlinedIcon />, menu: <MenuOutlinedIcon />,
    close: <CloseOutlinedIcon />, chevron: <ChevronRightOutlinedIcon />,
    logout: <LogoutOutlinedIcon />,
  };
  return <span aria-hidden="true">{icons[name]}</span>;
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

  const [reportAccess, setReportAccess] = useState<{ userId: number; allowed: boolean } | null>(null);
  const canViewReports = user?.role === 'ADMIN' || (canViewProfiles && reportAccess?.userId === user?.id && reportAccess?.allowed === true);
  useEffect(() => {
    let active = true;
    setReportAccess(null);
    if (!user || !['IT_STAFF', 'PUR_STAFF'].includes(user.role)) return;
    const userId = user.id;
    httpClient<{ data: Record<string, { view: boolean }> }>('/v1/reports/permissions')
      .then(({ data }) => { if (active) setReportAccess({ userId, allowed: Object.values(data).some(permission => permission.view) }); })
      .catch(() => { if (active) setReportAccess({ userId, allowed: false }); });
    return () => { active = false; };
  }, [user]);

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
      {canViewReports && iconLink('/reports', 'reports', t('nav.reports'))}
      {canViewProfiles && <div className="sidebar-group">
        <button type="button" className={`nav-link sidebar-group-toggle ${managementActive ? 'has-active-child' : ''}`}
          onClick={() => { if (collapsed && !mobile) { setCollapsed(false); setManagementOpen(true); } else setManagementOpen(!managementOpen); }} aria-expanded={managementOpen}
          aria-controls="management-links" aria-label={t('nav.management')} title={t('nav.management')}>
          <span className="nav-icon"><Icon name="management" /></span><span className="nav-label">{t('nav.management')}</span>
          <span className="sidebar-chevron"><Icon name="chevron" /></span>
        </button>
        {managementOpen && <div id="management-links" className="sidebar-subnav">
          {user?.role !== 'PUR_STAFF' && iconLink('/accounts', 'accounts', t('auth:management.accounts'))}
          {user?.role && DOCUMENT_ROLES.includes(user.role) && iconLink('/documents', 'documents', t('documents:title'))}
          {user?.role === 'ADMIN' && iconLink('/catalogs', 'catalogs', t('nav.assetCatalogs'))}
          {user?.role === 'ADMIN' && iconLink('/organization-catalogs', 'organization', t('nav.organizationCatalogs'))}
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
