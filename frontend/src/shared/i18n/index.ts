import i18n from 'i18next';
import { initReactI18next } from 'react-i18next';
import LanguageDetector from 'i18next-browser-languagedetector';

import viCommon from './locales/vi/common.json';
import viCatalogs from './locales/vi/catalogs.json';
import viAssets from './locales/vi/assets.json';
import viImports from './locales/vi/imports.json';

import enCommon from './locales/en/common.json';
import enCatalogs from './locales/en/catalogs.json';
import enAssets from './locales/en/assets.json';
import enImports from './locales/en/imports.json';

export const resources = {
  vi: {
    common: viCommon,
    catalogs: viCatalogs,
    assets: viAssets,
    imports: viImports,
  },
  en: {
    common: enCommon,
    catalogs: enCatalogs,
    assets: enAssets,
    imports: enImports,
  },
} as const;

export const DEFAULT_LANGUAGE = 'vi';
export const SUPPORTED_LANGUAGES = ['vi', 'en'] as const;
export type SupportedLanguage = (typeof SUPPORTED_LANGUAGES)[number];

if (typeof window !== 'undefined') {
  i18n.use(LanguageDetector);
}

i18n
  .use(initReactI18next)
  .init({
    resources,
    fallbackLng: DEFAULT_LANGUAGE,
    defaultNS: 'common',
    ns: ['common', 'catalogs', 'assets', 'imports'],
    detection: {
      order: ['localStorage', 'navigator'],
      lookupLocalStorage: 'itam_language',
      caches: ['localStorage'],
    },
    interpolation: {
      escapeValue: false, // React already escapes values
    },
  });

export default i18n;
