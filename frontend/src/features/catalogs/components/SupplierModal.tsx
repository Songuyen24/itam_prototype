import React, { useState, useEffect } from 'react';
import { Supplier, SupplierContact } from '../types/catalog.types';

interface SupplierModalProps {
  isOpen: boolean;
  title: string;
  initialData?: Supplier | null;
  isSaving: boolean;
  errorMessage?: string | null;
  onSave: (data: Partial<Supplier>, contacts: SupplierContact[]) => void;
  onClose: () => void;
}

export const SupplierModal: React.FC<SupplierModalProps> = ({
  isOpen,
  title,
  initialData,
  isSaving,
  errorMessage,
  onSave,
  onClose,
}) => {
  const [formData, setFormData] = useState<Partial<Supplier>>({
    code: '',
    name: '',
    taxCode: '',
    address: '',
    phone: '',
    email: '',
    isActive: true,
  });

  const [contacts, setContacts] = useState<SupplierContact[]>([]);
  const [newContact, setNewContact] = useState<SupplierContact>({
    name: '',
    position: '',
    phone: '',
    email: '',
  });

  const [validationError, setValidationError] = useState<string | null>(null);

  useEffect(() => {
    if (initialData) {
      setFormData({
        supplierId: initialData.supplierId,
        code: initialData.code,
        name: initialData.name,
        taxCode: initialData.taxCode || '',
        address: initialData.address || '',
        phone: initialData.phone || '',
        email: initialData.email || '',
        isActive: initialData.isActive,
      });
      setContacts(initialData.contacts || []);
    } else {
      setFormData({
        code: '',
        name: '',
        taxCode: '',
        address: '',
        phone: '',
        email: '',
        isActive: true,
      });
      setContacts([]);
    }
    setNewContact({ name: '', position: '', phone: '', email: '' });
    setValidationError(null);
  }, [initialData, isOpen]);

  if (!isOpen) return null;

  const handleAddContact = () => {
    if (!newContact.name.trim()) {
      alert('Vui lòng nhập tên người liên hệ');
      return;
    }
    setContacts([...contacts, { ...newContact }]);
    setNewContact({ name: '', position: '', phone: '', email: '' });
  };

  const handleRemoveContact = (index: number) => {
    setContacts(contacts.filter((_, i) => i !== index));
  };

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!formData.code?.trim()) {
      setValidationError('Mã nhà cung cấp không được để trống');
      return;
    }
    if (!formData.name?.trim()) {
      setValidationError('Tên nhà cung cấp không được để trống');
      return;
    }

    setValidationError(null);
    onSave(formData, contacts);
  };

  return (
    <div className="modal-overlay" onClick={onClose}>
      <div className="modal-content modal-lg" onClick={(e) => e.stopPropagation()}>
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

            <div className="form-grid-2">
              <div className="form-group">
                <label className="form-label required">Mã nhà cung cấp</label>
                <input
                  type="text"
                  className="form-input"
                  value={formData.code || ''}
                  onChange={(e) => setFormData({ ...formData, code: e.target.value })}
                  placeholder="VD: SUP_DELL..."
                  disabled={Boolean(initialData)}
                />
              </div>

              <div className="form-group">
                <label className="form-label required">Tên nhà cung cấp</label>
                <input
                  type="text"
                  className="form-input"
                  value={formData.name || ''}
                  onChange={(e) => setFormData({ ...formData, name: e.target.value })}
                  placeholder="VD: Dell Vietnam Ltd."
                />
              </div>
            </div>

            <div className="form-grid-2">
              <div className="form-group">
                <label className="form-label">Mã số thuế</label>
                <input
                  type="text"
                  className="form-input"
                  value={formData.taxCode || ''}
                  onChange={(e) => setFormData({ ...formData, taxCode: e.target.value })}
                  placeholder="VD: 0101234567"
                />
              </div>

              <div className="form-group">
                <label className="form-label">Số điện thoại</label>
                <input
                  type="text"
                  className="form-input"
                  value={formData.phone || ''}
                  onChange={(e) => setFormData({ ...formData, phone: e.target.value })}
                  placeholder="VD: 028 3822 0000"
                />
              </div>
            </div>

            <div className="form-grid-2">
              <div className="form-group">
                <label className="form-label">Email liên hệ</label>
                <input
                  type="email"
                  className="form-input"
                  value={formData.email || ''}
                  onChange={(e) => setFormData({ ...formData, email: e.target.value })}
                  placeholder="VD: contact@dell.com"
                />
              </div>

              <div className="form-group">
                <label className="form-label">Địa chỉ</label>
                <input
                  type="text"
                  className="form-input"
                  value={formData.address || ''}
                  onChange={(e) => setFormData({ ...formData, address: e.target.value })}
                  placeholder="Nhập địa chỉ trụ sở..."
                />
              </div>
            </div>

            <div className="form-group">
              <label className="form-checkbox-label">
                <input
                  type="checkbox"
                  checked={formData.isActive}
                  onChange={(e) => setFormData({ ...formData, isActive: e.target.checked })}
                />
                <span>Kích hoạt nhà cung cấp</span>
              </label>
            </div>

            {/* Contacts Section */}
            <div style={{ marginTop: '20px', borderTop: '1px solid var(--border-color)', paddingTop: '16px' }}>
              <div style={{ fontSize: '14px', fontWeight: 600, marginBottom: '12px', color: 'var(--text-main)' }}>
                DANH SÁCH NGƯỜI LIÊN HỆ
              </div>

              {contacts.length > 0 ? (
                <div style={{ marginBottom: '16px' }}>
                  <table className="data-table" style={{ border: '1px solid var(--border-color)' }}>
                    <thead>
                      <tr>
                        <th>Họ tên</th>
                        <th>Chức vụ</th>
                        <th>Điện thoại</th>
                        <th>Email</th>
                        <th>Thao tác</th>
                      </tr>
                    </thead>
                    <tbody>
                      {contacts.map((contact, idx) => (
                        <tr key={contact.contactId || idx}>
                          <td>{contact.name}</td>
                          <td>{contact.position || '-'}</td>
                          <td>{contact.phone || '-'}</td>
                          <td>{contact.email || '-'}</td>
                          <td>
                            <button
                              type="button"
                              className="btn btn-sm btn-danger"
                              onClick={() => handleRemoveContact(idx)}
                            >
                              Xóa
                            </button>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              ) : (
                <p style={{ fontSize: '13px', color: 'var(--text-muted)', marginBottom: '12px' }}>
                  Chưa có người liên hệ nào.
                </p>
              )}

              {/* Add contact mini-form */}
              <div
                style={{
                  padding: '12px',
                  backgroundColor: '#f8fafc',
                  border: '1px dashed var(--border-color)',
                  borderRadius: 'var(--radius)',
                }}
              >
                <div style={{ fontSize: '13px', fontWeight: 600, marginBottom: '8px' }}>
                  + Thêm người liên hệ
                </div>
                <div className="form-grid-2" style={{ marginBottom: '8px' }}>
                  <input
                    type="text"
                    className="form-input"
                    placeholder="Họ tên *"
                    value={newContact.name}
                    onChange={(e) => setNewContact({ ...newContact, name: e.target.value })}
                  />
                  <input
                    type="text"
                    className="form-input"
                    placeholder="Chức vụ"
                    value={newContact.position || ''}
                    onChange={(e) => setNewContact({ ...newContact, position: e.target.value })}
                  />
                </div>
                <div className="form-grid-2" style={{ marginBottom: '8px' }}>
                  <input
                    type="text"
                    className="form-input"
                    placeholder="Số điện thoại"
                    value={newContact.phone || ''}
                    onChange={(e) => setNewContact({ ...newContact, phone: e.target.value })}
                  />
                  <input
                    type="email"
                    className="form-input"
                    placeholder="Email"
                    value={newContact.email || ''}
                    onChange={(e) => setNewContact({ ...newContact, email: e.target.value })}
                  />
                </div>
                <button
                  type="button"
                  className="btn btn-sm btn-secondary"
                  onClick={handleAddContact}
                >
                  Thêm vào danh sách
                </button>
              </div>
            </div>
          </div>

          <div className="modal-footer">
            <button type="button" className="btn btn-secondary" onClick={onClose} disabled={isSaving}>
              Hủy
            </button>
            <button type="submit" className="btn btn-primary" disabled={isSaving}>
              {isSaving ? 'Đang lưu...' : 'Lưu nhà cung cấp'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};
