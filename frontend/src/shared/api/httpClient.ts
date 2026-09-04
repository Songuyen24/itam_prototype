const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080/api';

const AUTH_TOKEN_KEY = 'itam_auth_token';
const AUTH_USER_KEY = 'itam_auth_user';

export class ApiError extends Error {
  status: number;
  code?: string;
  errors?: string[];

  constructor(message: string, status: number, code?: string, errors?: string[]) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.code = code;
    this.errors = errors;
  }
}

export function getCurrentLanguage(): string {
  try {
    const stored = localStorage.getItem('itam_language');
    if (stored && (stored.startsWith('en') || stored.startsWith('vi'))) {
      return stored.startsWith('en') ? 'en' : 'vi';
    }
  } catch {
    // Ignore localStorage errors in SSR or restricted test environments
  }
  return 'vi';
}

function getAuthToken(): string | null {
  try {
    return localStorage.getItem(AUTH_TOKEN_KEY);
  } catch {
    return null;
  }
}

/**
 * Được gọi bởi AuthProvider khi nhận 401: xóa session và chuyển hướng về /login.
 * Hàm này được truyền từ bên ngoài vào httpClient để tránh phụ thuộc vòng tròn.
 */
let onUnauthorized: (() => void) | null = null;
export function registerUnauthorizedHandler(handler: (() => void) | null): void {
  onUnauthorized = handler;
}

function clearLocalSession(): void {
  try {
    localStorage.removeItem(AUTH_TOKEN_KEY);
    localStorage.removeItem(AUTH_USER_KEY);
  } catch {
    // Ignore
  }
}

async function httpClient<T>(
  endpoint: string,
  options: RequestInit = {}
): Promise<T> {
  const url = `${API_BASE_URL}${endpoint}`;
  const currentLang = getCurrentLanguage();

  const isFormData = options.body instanceof FormData;
  const headers: Record<string, string> = {
    'Accept-Language': currentLang,
    ...((options.headers as Record<string, string>) || {}),
  };

  if (!isFormData && !headers['Content-Type']) {
    headers['Content-Type'] = 'application/json';
  }

  const token = getAuthToken();
  if (token && !headers['Authorization']) {
    headers['Authorization'] = `Bearer ${token}`;
  }

  const response = await fetch(url, {
    ...options,
    headers,
  });

  if (response.status === 401) {
    clearLocalSession();
    if (onUnauthorized) {
      onUnauthorized();
    }
    let errorMessage = currentLang === 'en' ? 'Unauthorized' : 'Chưa xác thực';
    try {
      const errorData = await response.json();
      if (errorData && errorData.message) {
        errorMessage = errorData.message;
      }
    } catch {
      // ignore parse errors
    }
    throw new ApiError(errorMessage, 401, 'UNAUTHORIZED');
  }

  if (!response.ok) {
    const errorData = await response.json().catch(() => ({ message: response.statusText }));
    throw new ApiError(
      errorData.message || (currentLang === 'en' ? 'An error occurred' : 'Có lỗi xảy ra'),
      response.status,
      errorData.code,
      errorData.errors
    );
  }

  return response.json();
}

async function downloadFile(endpoint: string, fallbackFilename: string): Promise<void> {
  const url = `${API_BASE_URL}${endpoint}`;
  const currentLang = getCurrentLanguage();

  const headers: Record<string, string> = { 'Accept-Language': currentLang };
  const token = getAuthToken();
  if (token) {
    headers['Authorization'] = `Bearer ${token}`;
  }

  const response = await fetch(url, { headers });

  if (response.status === 401) {
    clearLocalSession();
    if (onUnauthorized) {
      onUnauthorized();
    }
    throw new ApiError(
      currentLang === 'en' ? 'Unauthorized' : 'Chưa xác thực',
      401,
      'UNAUTHORIZED'
    );
  }

  if (!response.ok) {
    throw new ApiError(
      currentLang === 'en' ? 'Unable to download file' : 'Không thể tải file',
      response.status
    );
  }

  const blob = await response.blob();
  const downloadUrl = window.URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = downloadUrl;
  a.download = fallbackFilename;
  document.body.appendChild(a);
  a.click();
  a.remove();
  window.URL.revokeObjectURL(downloadUrl);
}

export { API_BASE_URL, AUTH_TOKEN_KEY, AUTH_USER_KEY };
export { httpClient, downloadFile };
