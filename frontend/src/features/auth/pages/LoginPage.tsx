import React, { useState } from 'react';
import { Navigate, useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { useAuth } from '../contexts/AuthContext';
import { LanguageSwitcher } from '@/shared/components/LanguageSwitcher/LanguageSwitcher';
import { ApiError } from '@/shared/api/httpClient';
import { RolePreset } from '../types/auth.types';
import { getHomePath } from '../permissions';

const QUICK_LOGIN_PRESETS: RolePreset[] = [
  {
    email: 'admin@itam.example',
    label: 'Administrator',
    description: 'Quản trị viên hệ thống — toàn quyền',
    role: 'ADMIN',
  },
  {
    email: 'it01@itam.example',
    label: 'IT Staff',
    description: 'IT Support — nhập kho, bàn giao, thu hồi',
    role: 'IT_STAFF',
  },
  {
    email: 'pur01@itam.example',
    label: 'Purchasing Staff',
    description: 'Mua sắm — tạo phiếu nhập kho',
    role: 'PUR_STAFF',
  },
  {
    email: 'user01@itam.example',
    label: 'End User',
    description: 'Người dùng cuối — xem tài sản được cấp',
    role: 'USER',
  },
];

const DEFAULT_PASSWORD = 'Password@123';

const LoginPage: React.FC = () => {
  const { t } = useTranslation(['auth', 'common']);
  const { login, isAuthenticated, loading, user } = useAuth();
  const navigate = useNavigate();

  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  async function doLogin(loginEmail: string, loginPassword: string) {
    setError(null);
    setSubmitting(true);
    try {
      await login({ email: loginEmail, password: loginPassword });
      navigate('/', { replace: true });
    } catch (err) {
      if (err instanceof ApiError) {
        setError(err.message);
      } else {
        setError(t('auth:errors.unknown', 'Đăng nhập thất bại, vui lòng thử lại'));
      }
    } finally {
      setSubmitting(false);
    }
  }

  function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (!email.trim() || !password) {
      setError(t('auth:errors.required', 'Vui lòng nhập email và mật khẩu'));
      return;
    }
    void doLogin(email.trim(), password);
  }

  async function handleQuickLogin(preset: RolePreset) {
    setEmail(preset.email);
    setPassword(DEFAULT_PASSWORD);
    await doLogin(preset.email, DEFAULT_PASSWORD);
  }

  if (isAuthenticated && !loading) {
    return <Navigate to={getHomePath(user?.role)} replace />;
  }

  return (
    <div className="login-page">
      <div className="login-card">
        <div className="login-header">
          <div className="sidebar-logo-icon" style={{ marginRight: 12 }}>IT</div>
          <div>
            <h1 className="login-title">{t('auth:title', 'Đăng nhập ITAM Pro')}</h1>
            <p className="login-subtitle">
              {t('auth:subtitle', 'Hệ thống quản lý và theo dõi vòng đời tài sản CNTT')}
            </p>
          </div>
          <div style={{ marginLeft: 'auto' }}>
            <LanguageSwitcher />
          </div>
        </div>

        <form className="login-form" onSubmit={handleSubmit}>
          <label className="form-label">
            <span>{t('auth:email', 'Email')}</span>
            <input
              type="email"
              className="form-input"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              placeholder="admin@itam.example"
              autoComplete="username"
              disabled={submitting}
              required
            />
          </label>

          <label className="form-label">
            <span>{t('auth:password', 'Mật khẩu')}</span>
            <input
              type="password"
              className="form-input"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              placeholder="••••••••"
              autoComplete="current-password"
              disabled={submitting}
              required
            />
          </label>

          {error && (
            <div className="alert-banner alert-danger" role="alert">
              {error}
            </div>
          )}

          <button
            type="submit"
            className="btn btn-primary btn-block"
            disabled={submitting}
          >
            {submitting
              ? t('auth:submitting', 'Đang đăng nhập...')
              : t('auth:submit', 'Đăng nhập')}
          </button>
        </form>

        <div className="login-divider">
          <span>{t('auth:quickLoginHeader', 'Đăng nhập nhanh theo vai trò')}</span>
        </div>

        <div className="quick-login-grid">
          {QUICK_LOGIN_PRESETS.map((preset) => (
            <button
              key={preset.email}
              type="button"
              className="quick-login-tile"
              onClick={() => handleQuickLogin(preset)}
              disabled={submitting}
            >
              <span className={`role-badge role-${preset.role}`}>{preset.role}</span>
              <strong>{preset.label}</strong>
              <small>{preset.description}</small>
              <code>{preset.email}</code>
            </button>
          ))}
        </div>

        <p className="login-footer">
          {t('auth:defaultPasswordHint', 'Mật khẩu mặc định cho tài khoản seed: ')}
          <code>Password@123</code>
        </p>
      </div>
    </div>
  );
};

export default LoginPage;
