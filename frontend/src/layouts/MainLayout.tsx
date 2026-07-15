import { Outlet } from 'react-router-dom';

function MainLayout() {
  return (
    <div>
      <header>
        <h1>ITAM - IT Asset Management</h1>
      </header>
      <main>
        <Outlet />
      </main>
      <footer>
        <p>&copy; 2026 ITAM</p>
      </footer>
    </div>
  );
}

export { MainLayout };
