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
      className="language-switcher"
      role="group"
      aria-label="Language selection"
    >
      <button
        type="button"
        onClick={() => handleLanguageChange('vi')}
        className="language-switcher-button"
        aria-pressed={currentLang === 'vi'}
        lang="vi"
        title="Tiếng Việt"
      >
        <span>VI</span>
      </button>

      <button
        type="button"
        onClick={() => handleLanguageChange('en')}
        className="language-switcher-button"
        aria-pressed={currentLang === 'en'}
        lang="en"
        title="English"
      >
        <span>EN</span>
      </button>
    </div>
  );
};
