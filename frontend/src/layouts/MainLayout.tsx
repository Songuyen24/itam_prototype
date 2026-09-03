import { Outlet, NavLink } from 'react-router-dom';

function MainLayout() {
  return (
    <div className="app-container">
      {/* Sidebar */}
      <aside className="app-sidebar">
        <div className="sidebar-header">
          <div className="sidebar-logo-icon">IT</div>
          <div>
            <div className="sidebar-title">ITAM Pro</div>
            <div className="sidebar-subtitle">Quản lý tài sản CNTT</div>
          </div>
        </div>

        <nav className="sidebar-nav">
          <NavLink
            to="/"
            end
            className={({ isActive }) => `nav-link ${isActive ? 'active' : ''}`}
          >
            <span>📊</span>
            <span>Tổng quan</span>
          </NavLink>

          <NavLink
            to="/catalogs"
            className={({ isActive }) => `nav-link ${isActive ? 'active' : ''}`}
          >
            <span>📁</span>
            <span>Quản lý Danh mục</span>
          </NavLink>

          <NavLink
            to="/assets"
            className={({ isActive }) => `nav-link ${isActive ? 'active' : ''}`}
          >
            <span>💻</span>
            <span>Tài sản thiết bị</span>
          </NavLink>

          <NavLink
            to="/users"
            className={({ isActive }) => `nav-link ${isActive ? 'active' : ''}`}
          >
            <span>👥</span>
            <span>Người dùng</span>
          </NavLink>
        </nav>

        <div style={{ padding: '16px', borderTop: '1px solid rgba(255,255,255,0.1)', fontSize: '12px', color: '#94a3b8' }}>
          Phiên bản: 1.0.0 (T10-Catalog)
        </div>
      </aside>

      {/* Main Content Area */}
      <div className="app-content-wrapper">
        <header className="app-navbar">
          <div style={{ fontSize: '14px', fontWeight: 600, color: 'var(--text-muted)' }}>
            HỆ THỐNG QUẢN LÝ VÀ THEO DÕI VÒNG ĐỜI TÀI SẢN CNTT
          </div>

          <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
            <span className="badge badge-active">ADMIN</span>
            <div style={{ fontSize: '14px', fontWeight: 500 }}>Quản trị viên</div>
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
