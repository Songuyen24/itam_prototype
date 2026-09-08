import React, {
  createContext,
  useContext,
  useEffect,
  useMemo,
  useState,
  useCallback,
  useRef,
} from 'react';
import { AuthUser, LoginResponse } from '../types/auth.types';
import { authApi } from '../api/authApi';
import { invalidateSessionRequests, registerUnauthorizedHandler } from '@/shared/api/httpClient';

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
  const [user, setUser] = useState<AuthUser | null>(null);
  const [token, setToken] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const operation = useRef(0);

  const clearSession = useCallback(() => {
    operation.current += 1;
    invalidateSessionRequests();
    persistToken(null, null);
    setUser(null);
    setToken(null);
    setLoading(false);
  }, []);

  const applyLoginResponse = useCallback((res: LoginResponse) => {
    operation.current += 1;
    invalidateSessionRequests();
    persistToken(res.token, res.user);
    setUser(res.user);
    setToken(res.token);
    setLoading(false);
  }, []);

  const login = useCallback(async (payload: { email: string; password: string }) => {
    clearSession();
    const currentOperation = operation.current;
    setLoading(true);
    try {
      const res = await authApi.login(payload);
      if (operation.current === currentOperation && localStorage.getItem(TOKEN_STORAGE_KEY) === null) {
        applyLoginResponse(res);
      }
    } finally {
      if (operation.current === currentOperation) setLoading(false);
    }
  }, [applyLoginResponse, clearSession]);

  const logout = useCallback(async () => {
    const pendingLogout = authApi.logout();
    clearSession();
    const currentOperation = operation.current;
    setLoading(true);
    try {
      await pendingLogout;
    } catch {
      // Local logout takes effect even if the old request fails.
    } finally {
      if (operation.current === currentOperation) setLoading(false);
    }
  }, [clearSession]);

  const switchRole = useCallback(async (email: string) => {
    await login({ email, password: 'Password@123' });
  }, [login]);

  useEffect(() => {
    registerUnauthorizedHandler(clearSession);

    const restoreSession = async () => {
      const storedToken = localStorage.getItem(TOKEN_STORAGE_KEY);
      if (!storedToken) {
        clearSession();
        return;
      }

      const currentOperation = ++operation.current;
      invalidateSessionRequests();
      setUser(null);
      setToken(null);
      setLoading(true);
      try {
        const currentUser = await authApi.me();
        if (operation.current === currentOperation && localStorage.getItem(TOKEN_STORAGE_KEY) === storedToken) {
          applyLoginResponse({ token: storedToken, type: 'Bearer', user: currentUser });
        }
      } catch {
        if (operation.current === currentOperation && localStorage.getItem(TOKEN_STORAGE_KEY) === storedToken) {
          clearSession();
        }
      }
    };

    void restoreSession();
    const onStorage = (ev: StorageEvent) => {
      if (ev.key === TOKEN_STORAGE_KEY || ev.key === null) {
        void restoreSession();
      }
    };
    window.addEventListener('storage', onStorage);
    return () => {
      operation.current += 1;
      invalidateSessionRequests();
      registerUnauthorizedHandler(null);
      window.removeEventListener('storage', onStorage);
    };
  }, [applyLoginResponse, clearSession]);

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
