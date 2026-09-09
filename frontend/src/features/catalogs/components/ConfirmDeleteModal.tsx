import { useTranslation } from 'react-i18next';
import React from 'react';

interface ConfirmDeleteModalProps {
  isOpen: boolean;
  title?: string;
  itemName: string;
  itemTypeLabel: string;
  errorMessage?: string | null;
  isDeleting: boolean;
  onConfirm: () => void;
  onClose: () => void;
}

export const ConfirmDeleteModal: React.FC<ConfirmDeleteModalProps> = ({
  isOpen,
  title,
  itemName,
  itemTypeLabel,
  errorMessage,
  isDeleting,
  onConfirm,
  onClose,
}) => {
  const { t } = useTranslation('catalogs');
  if (!isOpen) return null;

  return (
    <div className="modal-overlay" onClick={onClose}>
      <div className="modal-content" onClick={(e) => e.stopPropagation()}>
        <div className="modal-header">
          <h3 className="modal-title">{title ?? t('forms.deleteTitle')}</h3>
          <button className="alert-close-btn" onClick={onClose}>
            &times;
          </button>
        </div>

        <div className="modal-body">
          {errorMessage && (
            <div className="alert-banner alert-danger">
              <div>
                <strong>{t('forms.deleteError')}</strong>
                <div>{errorMessage}</div>
              </div>
            </div>
          )}

          <p style={{ fontSize: '15px', color: 'var(--text-main)' }}>
            {t('forms.deletePrompt', { kind: itemTypeLabel })} <strong>{itemName}</strong>?
          </p>
          <p style={{ fontSize: '13px', color: 'var(--text-muted)', marginTop: '8px' }}>
            {t('forms.deleteHint')}
          </p>
        </div>

        <div className="modal-footer">
          <button className="btn btn-secondary" onClick={onClose} disabled={isDeleting}>
            {t('forms.cancelDelete')}
          </button>
          <button className="btn btn-danger" onClick={onConfirm} disabled={isDeleting}>
            {isDeleting ? t('forms.deleting') : t('forms.deleteTitle')}
          </button>
        </div>
      </div>
    </div>
  );
};
