import { describe, it, expect, beforeEach, beforeAll } from 'vitest';
import i18n from '@/shared/i18n';

describe('Auth i18n translations', () => {
  beforeAll(() => {
    let store: Record<string, string> = {};
    const storageMock = {
      getItem: (key: string) => store[key] ?? null,
      setItem: (key: string, value: string) => {
        store[key] = value.toString();
      },
      removeItem: (key: string) => {
        delete store[key];
      },
      clear: () => {
        store = {};
      },
    };
    Object.defineProperty(globalThis, 'localStorage', {
      value: storageMock,
      writable: true,
      configurable: true,
    });
  });

  beforeEach(async () => {
    localStorage.clear();
    await i18n.changeLanguage('vi');
  });

  it('exposes Vietnamese auth labels', () => {
    expect(i18n.t('auth:title')).toBe('Đăng nhập ITAM Pro');
    expect(i18n.t('auth:submit')).toBe('Đăng nhập');
    expect(i18n.t('auth:quickLoginHeader')).toBe('Đăng nhập nhanh theo vai trò');
    expect(i18n.t('auth:logout')).toBe('Đăng xuất');
  });

  it('exposes English auth labels when language switches', async () => {
    await i18n.changeLanguage('en');
    expect(i18n.t('auth:title')).toBe('Sign in to ITAM Pro');
    expect(i18n.t('auth:submit')).toBe('Sign in');
    expect(i18n.t('auth:logout')).toBe('Sign out');
  });
});
