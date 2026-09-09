import { useTranslation } from 'react-i18next';
import React, { useState, useEffect } from 'react';
import { AssetCategoryItem } from '../types/catalog.types';

export interface CatalogFormData {
  id?: number;
  code?: string;
  name: string;
  address?: string;
  categoryId?: number;
  manufacturer?: string;
  version?: string;
  isActive: boolean;
}

interface CatalogModalProps {
  isOpen: boolean;
  title: string;
  initialData?: CatalogFormData | null;
  categories?: AssetCategoryItem[]; // For Asset Type form
  showCodeField?: boolean;
  codeDisabled?: boolean;
  showAddressField?: boolean;
  showCategorySelect?: boolean;
  showSoftwareFields?: boolean;
  isSaving: boolean;
  errorMessage?: string | null;
  onSave: (data: CatalogFormData) => void;
  onClose: () => void;
}

export const CatalogModal: React.FC<CatalogModalProps> = ({
  isOpen,
  title,
  initialData,
  categories = [],
  showCodeField = true,
  codeDisabled = false,
  showAddressField = false,
  showCategorySelect = false,
  showSoftwareFields = false,
  isSaving,
  errorMessage,
  onSave,
  onClose,
}) => {
  const { t } = useTranslation('catalogs');
  const [formData, setFormData] = useState<CatalogFormData>({
    code: '',
    name: '',
    address: '',
    categoryId: categories.length > 0 ? categories[0].categoryId : undefined,
    manufacturer: '',
    version: '',
    isActive: true,
  });

  const [validationError, setValidationError] = useState<string | null>(null);

  useEffect(() => {
    if (initialData) {
      setFormData(initialData);
    } else {
      setFormData({
        code: '',
        name: '',
        address: '',
        categoryId: categories.length > 0 ? categories[0].categoryId : undefined,
        manufacturer: '',
        version: '',
        isActive: true,
      });
    }
    setValidationError(null);
  }, [initialData, isOpen, categories]);

  if (!isOpen) return null;

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (showCodeField && !formData.code?.trim()) {
      setValidationError(t('forms.codeRequired'));
      return;
    }
    if (!formData.name?.trim()) {
      setValidationError(t('forms.nameRequired'));
      return;
    }
    if (showCategorySelect && !formData.categoryId) {
      setValidationError(t('forms.categoryRequired'));
      return;
    }
    if (showSoftwareFields && !formData.manufacturer?.trim()) {
      setValidationError(t('forms.manufacturerRequired'));
      return;
    }

    setValidationError(null);
    onSave(formData);
  };

  return (
    <div className="modal-overlay" onClick={onClose}>
      <div className="modal-content" onClick={(e) => e.stopPropagation()}>
        <form onSubmit={handleSubmit}>
          <div className="modal-header">
            <h3 className="modal-title">{title ?? t('forms.deleteTitle')}</h3>
            <button type="button" className="alert-close-btn" onClick={onClose}>
              &times;
            </button>
          </div>

          <div className="modal-body">
            {(errorMessage || validationError) && (
              <div className="alert-banner alert-danger">
                <div>{validationError || errorMessage}</div>
              </div>
            )}

            {showCodeField && (
              <div className="form-group">
                <label className="form-label required">{t('forms.code')}</label>
                <input
                  type="text"
                  className="form-input"
                  value={formData.code || ''}
                  onChange={(e) => setFormData({ ...formData, code: e.target.value })}
                  placeholder="VD: IT_DEPT, LAPTOP..."
                  disabled={codeDisabled}
                />
              </div>
            )}

            {showCategorySelect && (
              <div className="form-group">
                <label className="form-label required">{t('forms.category')}</label>
                <select
                  className="form-select"
                  value={formData.categoryId || ''}
                  onChange={(e) =>
                    setFormData({ ...formData, categoryId: Number(e.target.value) })
                  }
                >
                  <option value="">{t('forms.chooseCategory')}</option>
                  {categories.map((c) => (
                    <option key={c.categoryId} value={c.categoryId}>
                      {c.name} ({c.code})
                    </option>
                  ))}
                </select>
              </div>
            )}

            <div className="form-group">
              <label className="form-label required">{t('forms.name')}</label>
              <input
                type="text"
                className="form-input"
                value={formData.name || ''}
                onChange={(e) => setFormData({ ...formData, name: e.target.value })}
                placeholder={t('forms.namePlaceholder')}
              />
            </div>

            {showSoftwareFields && (
              <>
                <div className="form-group">
                  <label className="form-label required">{t('forms.manufacturer')}</label>
                  <input
                    type="text"
                    className="form-input"
                    value={formData.manufacturer || ''}
                    onChange={(e) =>
                      setFormData({ ...formData, manufacturer: e.target.value })
                    }
                    placeholder="VD: Microsoft, JetBrains, Adobe..."
                  />
                </div>
                <div className="form-group">
                  <label className="form-label">{t('forms.version')}</label>
                  <input
                    type="text"
                    className="form-input"
                    value={formData.version || ''}
                    onChange={(e) => setFormData({ ...formData, version: e.target.value })}
                    placeholder="VD: 2024, 11 Pro, 3.2..."
                  />
                </div>
              </>
            )}

            {showAddressField && (
              <div className="form-group">
                <label className="form-label">{t('forms.address')}</label>
                <textarea
                  className="form-textarea"
                  value={formData.address || ''}
                  onChange={(e) => setFormData({ ...formData, address: e.target.value })}
                  placeholder={t('forms.locationAddress')}
                />
              </div>
            )}

            <div className="form-group">
              <label className="form-checkbox-label">
                <input
                  type="checkbox"
                  checked={formData.isActive}
                  onChange={(e) =>
                    setFormData({ ...formData, isActive: e.target.checked })
                  }
                />
                <span>{t('forms.active')}</span>
              </label>
            </div>
          </div>

          <div className="modal-footer">
            <button type="button" className="btn btn-secondary" onClick={onClose} disabled={isSaving}>
              {t('forms.cancel')}
            </button>
            <button type="submit" className="btn btn-primary" disabled={isSaving}>
              {isSaving ? t('forms.saving') : t('forms.save')}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};
