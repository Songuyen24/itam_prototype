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
      setValidationError('Mã không được để trống');
      return;
    }
    if (!formData.name?.trim()) {
      setValidationError('Tên không được để trống');
      return;
    }
    if (showCategorySelect && !formData.categoryId) {
      setValidationError('Vui lòng chọn nhóm tài sản');
      return;
    }
    if (showSoftwareFields && !formData.manufacturer?.trim()) {
      setValidationError('Nhà sản xuất không được để trống');
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
            <h3 className="modal-title">{title}</h3>
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
                <label className="form-label required">Mã</label>
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
                <label className="form-label required">Nhóm tài sản</label>
                <select
                  className="form-select"
                  value={formData.categoryId || ''}
                  onChange={(e) =>
                    setFormData({ ...formData, categoryId: Number(e.target.value) })
                  }
                >
                  <option value="">-- Chọn nhóm tài sản --</option>
                  {categories.map((c) => (
                    <option key={c.categoryId} value={c.categoryId}>
                      {c.name} ({c.code})
                    </option>
                  ))}
                </select>
              </div>
            )}

            <div className="form-group">
              <label className="form-label required">Tên</label>
              <input
                type="text"
                className="form-input"
                value={formData.name || ''}
                onChange={(e) => setFormData({ ...formData, name: e.target.value })}
                placeholder="Nhập tên..."
              />
            </div>

            {showSoftwareFields && (
              <>
                <div className="form-group">
                  <label className="form-label required">Nhà sản xuất</label>
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
                  <label className="form-label">Phiên bản</label>
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
                <label className="form-label">Địa chỉ</label>
                <textarea
                  className="form-textarea"
                  value={formData.address || ''}
                  onChange={(e) => setFormData({ ...formData, address: e.target.value })}
                  placeholder="Nhập địa chỉ vị trí..."
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
                <span>Kích hoạt hoạt động</span>
              </label>
            </div>
          </div>

          <div className="modal-footer">
            <button type="button" className="btn btn-secondary" onClick={onClose} disabled={isSaving}>
              Hủy
            </button>
            <button type="submit" className="btn btn-primary" disabled={isSaving}>
              {isSaving ? 'Đang lưu...' : 'Lưu lại'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};
