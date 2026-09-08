import React, { useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { useAuth } from '@/features/auth/contexts/AuthContext';

interface Preset {
  email: string;
  role: string;
  label: string;
}

const QUICK_PRESETS: Preset[] = [
  { email: 'admin@itam.example', role: 'ADMIN', label: 'Administrator' },
  { email: 'it01@itam.example', role: 'IT_STAFF', label: 'IT Staff' },
  { email: 'pur01@itam.example', role: 'PUR_STAFF', label: 'Purchasing Staff' },
  { email: 'user01@itam.example', role: 'USER', label: 'End User' },
];

function initials(name?: string): string {
  if (!name) return '?';
  const parts = name.trim().split(/\s+/);
  if (parts.length === 1) return parts[0].slice(0, 2).toUpperCase();
  return (parts[0][0] + parts[parts.length - 1][0]).toUpperCase();
}

export const RoleSwitcher: React.FC = () => {
  const { t } = useTranslation(['auth', 'common']);
  const { user, switchRole, logout } = useAuth();
  const navigate = useNavigate();
  const [open, setOpen] = useState(false);
  const [busy, setBusy] = useState(false);
  const containerRef = useRef<HTMLDivElement | null>(null);

  useEffect(() => {
    const handler = (ev: MouseEvent) => {
      if (!containerRef.current) return;
      if (!containerRef.current.contains(ev.target as Node)) {
        setOpen(false);
      }
    };
    document.addEventListener('mousedown', handler);
    return () => document.removeEventListener('mousedown', handler);
  }, []);

  async function handleSwitch(email: string) {
    setBusy(true);
    try {
      await switchRole(email);
      setOpen(false);
      // Sau khi đổi role quay về trang chủ để đảm bảo dữ liệu reload đúng quyền
      navigate('/', { replace: true });
    } catch {
      navigate('/login', { replace: true });
    } finally {
      setBusy(false);
    }
  }

  async function handleLogout() {
    setBusy(true);
    try {
      await logout();
      setOpen(false);
      navigate('/login', { replace: true });
    } finally {
      setBusy(false);
    }
  }

  if (!user) return null;

  const role = user.role || 'USER';

  return (
    <div
      ref={containerRef}
      style={{ position: 'relative', display: 'inline-block' }}
    >
      <button
        type="button"
        onClick={() => setOpen((v) => !v)}
        disabled={busy}
        className="role-switcher-trigger"
        aria-haspopup="menu"
        aria-expanded={open}
        title={t('auth:switchRoleTooltip', 'Chuyển vai trò / đăng xuất')}
      >
        <span className="user-avatar" aria-hidden>
          {initials(user.fullName)}
        </span>
        <span className="role-switcher-info">
          <span className="role-switcher-name">{user.fullName}</span>
          <span className={`role-badge role-${role}`}>{role}</span>
        </span>
        <span aria-hidden style={{ marginLeft: 6, fontSize: 10 }}>▾</span>
      </button>

      {open && (
        <div className="role-switcher-menu" role="menu">
          <div className="role-switcher-header">
            <strong>{user.email}</strong>
            <small>{t('auth:switchAccountHeader', 'Chuyển vai trò test nhanh')}</small>
          </div>

          {QUICK_PRESETS.map((preset) => {
            const isCurrent = preset.email === user.email;
            return (
              <button
                type="button"
                key={preset.email}
                disabled={busy || isCurrent}
                onClick={() => handleSwitch(preset.email)}
                className={`role-switcher-item ${isCurrent ? 'is-current' : ''}`}
                role="menuitem"
              >
                <span className={`role-badge role-${preset.role}`}>{preset.role}</span>
                <span className="role-switcher-item-info">
                  <span>{preset.label}</span>
                  <small>{preset.email}</small>
                </span>
                {isCurrent && (
                  <span className="role-switcher-current-mark" aria-hidden>✓</span>
                )}
              </button>
            );
          })}

          <div className="role-switcher-divider" />

          <button
            type="button"
            className="role-switcher-item role-switcher-logout"
            onClick={handleLogout}
            disabled={busy}
            role="menuitem"
          >
            <span aria-hidden>🚪</span>
            <span className="role-switcher-item-info">
              <strong>{t('auth:logout', 'Đăng xuất')}</strong>
              <small>{t('auth:logoutHint', 'Xóa phiên hiện tại')}</small>
            </span>
          </button>
        </div>
      )}
    </div>
  );
};
