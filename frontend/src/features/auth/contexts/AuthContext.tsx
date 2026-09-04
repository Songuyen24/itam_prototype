import React, {
  createContext,
  useContext,
  useEffect,
  useMemo,
  useState,
  useCallback,
} from 'react';
import { AuthUser, LoginResponse } from '../types/auth.types';
import { authApi } from '../api/authApi';

const TOKEN_STORAGE_KEY = 'itam_auth_token';
const USER_STORAGE_KEY = 'itam_auth_user';

interface AuthContextValue {
  user: AuthUser | null;
  token: string | null;
  isAuthenticated: boolean;
  loading: boolean;
  login: (payload: { email: string; password: string }) => Promise<void>;
  logout: () => Promise<void>;
  /** Đổi sang role khác bằng cách đăng nhập lại với mật khẩu mặc định của seed user */
  switchRole: (email: string) => Promise<void>;
  /** Cập nhật user/token từ phản hồi login */
  applyLoginResponse: (res: LoginResponse) => void;
}

const AuthContext = createContext<AuthContextValue | undefined>(undefined);

function readUser(): AuthUser | null {
  try {
    const raw = localStorage.getItem(USER_STORAGE_KEY);
    if (!raw) return null;
    return JSON.parse(raw) as AuthUser;
  } catch {
    return null;
  }
}

function persistToken(token: string | null, user: AuthUser | null) {
  if (token === null || user === null) {
    localStorage.removeItem(TOKEN_STORAGE_KEY);
    localStorage.removeItem(USER_STORAGE_KEY);
  } else {
    localStorage.setItem(TOKEN_STORAGE_KEY, token);
    localStorage.setItem(USER_STORAGE_KEY, JSON.stringify(user));
  }
}

export const AuthProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [user, setUser] = useState<AuthUser | null>(() => readUser());
  const [token, setToken] = useState<string | null>(() =>
    localStorage.getItem(TOKEN_STORAGE_KEY)
  );
  const [loading, setLoading] = useState(false);

  const applyLoginResponse = useCallback((res: LoginResponse) => {
    setUser(res.user);
    setToken(res.token);
    persistToken(res.token, res.user);
  }, []);

  const login = useCallback(async (payload: { email: string; password: string }) => {
    setLoading(true);
    try {
      const res = await authApi.login(payload);
      applyLoginResponse(res);
    } finally {
      setLoading(false);
    }
  }, [applyLoginResponse]);

  const logout = useCallback(async () => {
    setLoading(true);
    try {
      try {
        await authApi.logout();
      } catch {
        // Bỏ qua lỗi server để vẫn xóa session phía client
      }
      setUser(null);
      setToken(null);
      persistToken(null, null);
    } finally {
      setLoading(false);
    }
  }, []);

  const switchRole = useCallback(async (email: string) => {
    await login({ email, password: 'Password@123' });
  }, [login]);

  useEffect(() => {
    // Đồng bộ giữa tab: nếu token bị xóa thì cập nhật state
    const onStorage = (ev: StorageEvent) => {
      if (ev.key === TOKEN_STORAGE_KEY) {
        setToken(ev.newValue);
        setUser(readUser());
      }
    };
    window.addEventListener('storage', onStorage);
    return () => window.removeEventListener('storage', onStorage);
  }, []);

  const value = useMemo<AuthContextValue>(
    () => ({
      user,
      token,
      isAuthenticated: !!token && !!user,
      loading,
      login,
      logout,
      switchRole,
      applyLoginResponse,
    }),
    [user, token, loading, login, logout, switchRole, applyLoginResponse]
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
};

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (!ctx) {
    throw new Error('useAuth must be used within AuthProvider');
  }
  return ctx;
}

export const AUTH_TOKEN_STORAGE_KEY = TOKEN_STORAGE_KEY;
export const AUTH_USER_STORAGE_KEY = USER_STORAGE_KEY;
