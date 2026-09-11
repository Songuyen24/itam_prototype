import viHandover from './locales/vi/handover.json';
import enHandover from './locales/en/handover.json';
import i18n from 'i18next';
import { initReactI18next } from 'react-i18next';
import LanguageDetector from 'i18next-browser-languagedetector';

import viCommon from './locales/vi/common.json';
import viAuth from './locales/vi/auth.json';
import viCatalogs from './locales/vi/catalogs.json';
import viAssets from './locales/vi/assets.json';
import viImports from './locales/vi/imports.json';
import viDocuments from './locales/vi/documents.json';
import viRecovery from './locales/vi/recovery.json';

import enCommon from './locales/en/common.json';
import enAuth from './locales/en/auth.json';
import enCatalogs from './locales/en/catalogs.json';
import enAssets from './locales/en/assets.json';
import enImports from './locales/en/imports.json';
import enDocuments from './locales/en/documents.json';
import enRecovery from './locales/en/recovery.json';

export const resources = {
  vi: {
    common: viCommon,
    auth: viAuth,
    catalogs: viCatalogs,
    assets: viAssets,
    imports: viImports,
    documents: viDocuments,
    handover: viHandover,
    recovery: viRecovery,
  },
  en: {
    common: enCommon,
    auth: enAuth,
    catalogs: enCatalogs,
    assets: enAssets,
    imports: enImports,
    documents: enDocuments,
    handover: enHandover,
    recovery: enRecovery,
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
    ns: ['common', 'auth', 'catalogs', 'assets', 'imports', 'documents', 'handover', 'recovery'],
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
