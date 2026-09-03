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
  title = 'Xác nhận xóa',
  itemName,
  itemTypeLabel,
  errorMessage,
  isDeleting,
  onConfirm,
  onClose,
}) => {
  if (!isOpen) return null;

  return (
    <div className="modal-overlay" onClick={onClose}>
      <div className="modal-content" onClick={(e) => e.stopPropagation()}>
        <div className="modal-header">
          <h3 className="modal-title">{title}</h3>
          <button className="alert-close-btn" onClick={onClose}>
            &times;
          </button>
        </div>

        <div className="modal-body">
          {errorMessage && (
            <div className="alert-banner alert-danger">
              <div>
                <strong>Lỗi không thể xóa:</strong>
                <div>{errorMessage}</div>
              </div>
            </div>
          )}

          <p style={{ fontSize: '15px', color: 'var(--text-main)' }}>
            Bạn có chắc chắn muốn xóa {itemTypeLabel}: <strong>{itemName}</strong>?
          </p>
          <p style={{ fontSize: '13px', color: 'var(--text-muted)', marginTop: '8px' }}>
            Hành động này không thể hoàn tác nếu danh mục chưa được sử dụng ở bất kỳ bản ghi nào.
          </p>
        </div>

        <div className="modal-footer">
          <button className="btn btn-secondary" onClick={onClose} disabled={isDeleting}>
            Hủy bỏ
          </button>
          <button className="btn btn-danger" onClick={onConfirm} disabled={isDeleting}>
            {isDeleting ? 'Đang xóa...' : 'Xác nhận xóa'}
          </button>
        </div>
      </div>
    </div>
  );
};
