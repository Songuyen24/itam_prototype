import { useTranslation } from 'react-i18next';
import React, { useState, useEffect } from 'react';
import { AssetTypeItem, ModelItem } from '../types/catalog.types';

interface ModelModalProps {
  isOpen: boolean;
  title: string;
  initialData?: ModelItem | null;
  assetTypes: AssetTypeItem[];
  isSaving: boolean;
  errorMessage?: string | null;
  onSave: (data: {
    modelId?: number;
    name: string;
    brand: string;
    typeId: number;
    defaultCpu?: string;
    defaultRam?: string;
    defaultStorage?: string;
    defaultGraphicsCard?: string;
    isActive: boolean;
  }) => void;
  onClose: () => void;
}

export const ModelModal: React.FC<ModelModalProps> = ({
  isOpen,
  title,
  initialData,
  assetTypes,
  isSaving,
  errorMessage,
  onSave,
  onClose,
}) => {
  const { t } = useTranslation('catalogs');
  const [formData, setFormData] = useState({
    name: '',
    brand: '',
    typeId: assetTypes.length > 0 ? assetTypes[0].typeId : 0,
    defaultCpu: '',
    defaultRam: '',
    defaultStorage: '',
    defaultGraphicsCard: '',
    isActive: true,
  });

  const [validationError, setValidationError] = useState<string | null>(null);

  useEffect(() => {
    if (initialData) {
      setFormData({
        name: initialData.name,
        brand: initialData.brand,
        typeId: initialData.typeId,
        defaultCpu: initialData.defaultCpu || '',
        defaultRam: initialData.defaultRam || '',
        defaultStorage: initialData.defaultStorage || '',
        defaultGraphicsCard: initialData.defaultGraphicsCard || '',
        isActive: initialData.isActive,
      });
    } else {
      setFormData({
        name: '',
        brand: '',
        typeId: assetTypes.length > 0 ? assetTypes[0].typeId : 0,
        defaultCpu: '',
        defaultRam: '',
        defaultStorage: '',
        defaultGraphicsCard: '',
        isActive: true,
      });
    }
    setValidationError(null);
  }, [initialData, isOpen, assetTypes]);

  if (!isOpen) return null;

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!formData.brand.trim()) {
      setValidationError(t('forms.brandRequired'));
      return;
    }
    if (!formData.name.trim()) {
      setValidationError(t('forms.modelRequired'));
      return;
    }
    if (!formData.typeId) {
      setValidationError(t('forms.typeRequired'));
      return;
    }

    setValidationError(null);
    onSave({
      modelId: initialData?.modelId,
      ...formData,
    });
  };

  return (
    <div className="modal-overlay" onClick={onClose}>
      <div className="modal-content modal-lg" onClick={(e) => e.stopPropagation()}>
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

            <div className="form-group">
              <label className="form-label required">{t('forms.type')}</label>
              <select
                className="form-select"
                value={formData.typeId}
                onChange={(e) => setFormData({ ...formData, typeId: Number(e.target.value) })}
              >
                <option value="">{t('forms.chooseType')}</option>
                {assetTypes.map((t) => (
                  <option key={t.typeId} value={t.typeId}>
                    {t.name} ({t.code})
                  </option>
                ))}
              </select>
            </div>

            <div className="form-grid-2">
              <div className="form-group">
                <label className="form-label required">{t('forms.brand')}</label>
                <input
                  type="text"
                  className="form-input"
                  value={formData.brand}
                  onChange={(e) => setFormData({ ...formData, brand: e.target.value })}
                  placeholder="VD: Dell, Lenovo, Apple, HP..."
                />
              </div>

              <div className="form-group">
                <label className="form-label required">{t('forms.modelName')}</label>
                <input
                  type="text"
                  className="form-input"
                  value={formData.name}
                  onChange={(e) => setFormData({ ...formData, name: e.target.value })}
                  placeholder="VD: Latitude 5420, ThinkPad T14..."
                />
              </div>
            </div>

            <div style={{ marginTop: '12px', marginBottom: '8px', fontSize: '13px', fontWeight: 600, color: 'var(--text-muted)' }}>
              {t('forms.defaultConfig')}
            </div>

            <div className="form-grid-2">
              <div className="form-group">
                <label className="form-label">{t('forms.defaultCpu')}</label>
                <input
                  type="text"
                  className="form-input"
                  value={formData.defaultCpu}
                  onChange={(e) => setFormData({ ...formData, defaultCpu: e.target.value })}
                  placeholder="VD: Intel Core i5-1135G7"
                />
              </div>

              <div className="form-group">
                <label className="form-label">{t('forms.defaultRam')}</label>
                <input
                  type="text"
                  className="form-input"
                  value={formData.defaultRam}
                  onChange={(e) => setFormData({ ...formData, defaultRam: e.target.value })}
                  placeholder="VD: 16GB DDR4"
                />
              </div>
            </div>

            <div className="form-grid-2">
              <div className="form-group">
                <label className="form-label">{t('forms.defaultStorage')}</label>
                <input
                  type="text"
                  className="form-input"
                  value={formData.defaultStorage}
                  onChange={(e) => setFormData({ ...formData, defaultStorage: e.target.value })}
                  placeholder="VD: 512GB NVMe SSD"
                />
              </div>

              <div className="form-group">
                <label className="form-label">{t('forms.gpu')}</label>
                <input
                  type="text"
                  className="form-input"
                  value={formData.defaultGraphicsCard}
                  onChange={(e) => setFormData({ ...formData, defaultGraphicsCard: e.target.value })}
                  placeholder="VD: Intel Iris Xe / RTX 3050"
                />
              </div>
            </div>

            <div className="form-group" style={{ marginTop: '12px' }}>
              <label className="form-checkbox-label">
                <input
                  type="checkbox"
                  checked={formData.isActive}
                  onChange={(e) => setFormData({ ...formData, isActive: e.target.checked })}
                />
                <span>{t('forms.modelActive')}</span>
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
