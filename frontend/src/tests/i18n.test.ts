import { describe, it, expect, beforeEach, beforeAll } from 'vitest';
import i18n from '@/shared/i18n';
import { getCurrentLanguage } from '@/shared/api/httpClient';

describe('i18n Bilingual Framework', () => {
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

  it('should initialize with default language "vi"', () => {
    expect(i18n.language).toContain('vi');
  });

  it('should resolve Vietnamese translations correctly across namespaces', () => {
    expect(i18n.t('common:systemTitle')).toBe('HỆ THỐNG QUẢN LÝ VÀ THEO DÕI VÒNG ĐỜI TÀI SẢN CNTT');
    expect(i18n.t('catalogs:tabs.departments')).toBe('Phòng ban');
    expect(i18n.t('assets:fields.assetTag')).toBe('Mã tài sản');
    expect(i18n.t('imports:steps.upload')).toBe('1. Chọn file Excel');
  });

  it('should switch language to English and resolve English translations', async () => {
    await i18n.changeLanguage('en');
    expect(i18n.language).toBe('en');

    expect(i18n.t('common:systemTitle')).toBe('IT ASSET LIFECYCLE MANAGEMENT SYSTEM');
    expect(i18n.t('catalogs:tabs.departments')).toBe('Departments');
    expect(i18n.t('assets:fields.assetTag')).toBe('Asset Tag');
    expect(i18n.t('imports:steps.upload')).toBe('1. Select Excel File');
  });

  it('getCurrentLanguage should read from localStorage and fallback to "vi"', () => {
    expect(getCurrentLanguage()).toBe('vi');

    localStorage.setItem('itam_language', 'en');
    expect(getCurrentLanguage()).toBe('en');

    localStorage.setItem('itam_language', 'vi');
    expect(getCurrentLanguage()).toBe('vi');
  });
});
