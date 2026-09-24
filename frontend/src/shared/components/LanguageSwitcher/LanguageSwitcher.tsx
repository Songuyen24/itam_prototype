import React from 'react';
import { useTranslation } from 'react-i18next';

export const LanguageSwitcher: React.FC = () => {
  const { i18n } = useTranslation();

  const currentLang = i18n.language?.startsWith('en') ? 'en' : 'vi';

  const handleLanguageChange = (lng: 'vi' | 'en') => {
    if (currentLang !== lng) {
      i18n.changeLanguage(lng);
      localStorage.setItem('itam_language', lng);
    }
  };

  return (
    <div
      style={{
        display: 'inline-flex',
        alignItems: 'center',
        background: 'var(--bg-secondary, #f1f5f9)',
        border: '1px solid var(--border-color, #cbd5e1)',
        borderRadius: '6px',
        padding: '2px',
        gap: '2px',
      }}
      role="group"
      aria-label="Language selection"
    >
      <button
        type="button"
        onClick={() => handleLanguageChange('vi')}
        style={{
          display: 'flex',
          alignItems: 'center',
          gap: '4px',
          padding: '4px 8px',
          fontSize: '12px',
          fontWeight: currentLang === 'vi' ? 700 : 500,
          border: 'none',
          borderRadius: '4px',
          background: currentLang === 'vi' ? 'var(--primary-color, #2563eb)' : 'transparent',
          color: currentLang === 'vi' ? '#ffffff' : 'var(--text-muted, #64748b)',
          cursor: 'pointer',
          transition: 'all 0.15s ease',
        }}
        title="Tiếng Việt"
      >
        <span>🇻🇳</span>
        <span>VI</span>
      </button>

      <button
        type="button"
        onClick={() => handleLanguageChange('en')}
        style={{
          display: 'flex',
          alignItems: 'center',
          gap: '4px',
          padding: '4px 8px',
          fontSize: '12px',
          fontWeight: currentLang === 'en' ? 700 : 500,
          border: 'none',
          borderRadius: '4px',
          background: currentLang === 'en' ? 'var(--primary-color, #2563eb)' : 'transparent',
          color: currentLang === 'en' ? '#ffffff' : 'var(--text-muted, #64748b)',
          cursor: 'pointer',
          transition: 'all 0.15s ease',
        }}
        title="English"
      >
        <span>🇬🇧</span>
        <span>EN</span>
      </button>
    </div>
  );
};
