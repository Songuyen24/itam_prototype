import React from 'react';
import { AssetDetail } from '../types/asset.types';

interface AssetDetailModalProps {
  isOpen: boolean;
  onClose: () => void;
  asset: AssetDetail | null;
  onEdit: (asset: AssetDetail) => void;
}

export const AssetDetailModal: React.FC<AssetDetailModalProps> = ({
  isOpen,
  onClose,
  asset,
  onEdit,
}) => {
  if (!isOpen || !asset) return null;

  const cfg = asset.hardwareConfig || {};

  const formatCurrency = (val?: number) => {
    if (val === undefined || val === null) return '0 VNĐ';
    return new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' }).format(val);
  };

  const formatDate = (val?: string) => {
    if (!val) return '—';
    try {
      return new Date(val).toLocaleDateString('vi-VN');
    } catch {
      return val;
    }
  };

  const getStatusBadgeClass = (code?: string) => {
    switch (code) {
      case 'IN_STOCK':
        return 'badge-active';
      case 'IN_USE':
        return 'badge-info';
      case 'IN_REPAIR':
        return 'badge-warning';
      case 'DAMAGED':
      case 'RETIRED':
        return 'badge-inactive';
      default:
        return 'badge-active';
    }
  };

  return (
    <div className="modal-backdrop">
      <div
        className="modal-content"
        style={{ maxWidth: '850px', maxHeight: '90vh', display: 'flex', flexDirection: 'column' }}
      >
        {/* Modal Header */}
        <div className="modal-header">
          <div>
            <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
              <span style={{ fontSize: '20px', fontWeight: 700, color: 'var(--text-main)' }}>
                {asset.assetTag}
              </span>
              <span className={`badge ${getStatusBadgeClass(asset.statusCode)}`}>
                {asset.statusName || asset.statusCode || 'N/A'}
              </span>
            </div>
            <div style={{ fontSize: '13px', color: 'var(--text-muted)', marginTop: '2px' }}>
              {asset.name}
            </div>
          </div>
          <button type="button" className="btn-close" onClick={onClose}>
            ✕
          </button>
        </div>

        {/* Modal Body */}
        <div style={{ overflowY: 'auto', padding: '20px', display: 'flex', flexDirection: 'column', gap: '24px' }}>
          {/* Section 1: Overview Grid */}
          <div
            style={{
              display: 'grid',
              gridTemplateColumns: 'repeat(3, 1fr)',
              gap: '16px',
              backgroundColor: '#f8fafc',
              padding: '16px',
              borderRadius: '8px',
              border: '1px solid var(--border-color)',
            }}
          >
            <div>
              <div style={{ fontSize: '11px', color: 'var(--text-muted)', fontWeight: 600 }}>LOẠI THIẾT BỊ</div>
              <div style={{ fontSize: '14px', fontWeight: 600, marginTop: '2px' }}>
                {asset.typeName || '—'} ({asset.categoryName || 'Device'})
              </div>
            </div>

            <div>
              <div style={{ fontSize: '11px', color: 'var(--text-muted)', fontWeight: 600 }}>MODEL & HÃNG</div>
              <div style={{ fontSize: '14px', fontWeight: 600, marginTop: '2px' }}>
                {asset.modelBrand ? `${asset.modelBrand} ` : ''}{asset.modelName || '—'}
              </div>
            </div>

            <div>
              <div style={{ fontSize: '11px', color: 'var(--text-muted)', fontWeight: 600 }}>SỐ SERIAL (S/N)</div>
              <div style={{ fontSize: '14px', fontWeight: 600, marginTop: '2px', fontFamily: 'monospace' }}>
                {asset.serialNumber || '—'}
              </div>
            </div>

            <div>
              <div style={{ fontSize: '11px', color: 'var(--text-muted)', fontWeight: 600 }}>TÌNH TRẠNG</div>
              <div style={{ fontSize: '14px', fontWeight: 500, marginTop: '2px' }}>
                {asset.conditionName || '—'}
              </div>
            </div>

            <div>
              <div style={{ fontSize: '11px', color: 'var(--text-muted)', fontWeight: 600 }}>HẠN BẢO HÀNH</div>
              <div style={{ fontSize: '14px', fontWeight: 500, marginTop: '2px' }}>
                {formatDate(asset.warrantyExpiration)}
              </div>
            </div>

            <div>
              <div style={{ fontSize: '11px', color: 'var(--text-muted)', fontWeight: 600 }}>NGƯỜI ĐANG SỬ DỤNG</div>
              <div style={{ fontSize: '14px', fontWeight: 600, color: asset.assignedToFullName ? 'var(--primary)' : 'var(--text-muted)', marginTop: '2px' }}>
                {asset.assignedToFullName ? `👤 ${asset.assignedToFullName}` : 'Chưa bàn giao'}
              </div>
            </div>

            <div>
              <div style={{ fontSize: '11px', color: 'var(--text-muted)', fontWeight: 600 }}>PHÒNG BAN</div>
              <div style={{ fontSize: '14px', fontWeight: 500, marginTop: '2px' }}>
                {asset.departmentName || '—'}
              </div>
            </div>

            <div>
              <div style={{ fontSize: '11px', color: 'var(--text-muted)', fontWeight: 600 }}>VỊ TRÍ LƯU TRỮ</div>
              <div style={{ fontSize: '14px', fontWeight: 500, marginTop: '2px' }}>
                {asset.locationName || '—'}
              </div>
            </div>

            <div>
              <div style={{ fontSize: '11px', color: 'var(--text-muted)', fontWeight: 600 }}>NHÀ CUNG CẤP</div>
              <div style={{ fontSize: '14px', fontWeight: 500, marginTop: '2px' }}>
                {asset.supplierName || '—'}
              </div>
            </div>

            <div>
              <div style={{ fontSize: '11px', color: 'var(--text-muted)', fontWeight: 600 }}>SỐ PO</div>
              <div style={{ fontSize: '14px', fontWeight: 500, marginTop: '2px' }}>
                {asset.poNumber || '—'}
              </div>
            </div>

            <div>
              <div style={{ fontSize: '11px', color: 'var(--text-muted)', fontWeight: 600 }}>NGÀY MUA</div>
              <div style={{ fontSize: '14px', fontWeight: 500, marginTop: '2px' }}>
                {formatDate(asset.purchaseDate)}
              </div>
            </div>

            <div>
              <div style={{ fontSize: '11px', color: 'var(--text-muted)', fontWeight: 600 }}>GIÁ MUA</div>
              <div style={{ fontSize: '14px', fontWeight: 600, color: '#16a34a', marginTop: '2px' }}>
                {formatCurrency(asset.purchaseCost)}
              </div>
            </div>
          </div>

          {/* Section 2: Hardware Specs Breakdown (3 Columns Table) */}
          <div>
            <div style={{ fontSize: '15px', fontWeight: 700, color: 'var(--text-main)', marginBottom: '8px' }}>
              ⚙️ Chi tiết cấu hình phần cứng (Default vs Actual vs Effective)
            </div>
            <div style={{ fontSize: '12px', color: 'var(--text-muted)', marginBottom: '12px' }}>
              Khi cấu hình thực tế để trống, hệ thống tự động kế thừa cấu hình mặc định của Model thiết bị.
            </div>

            <div className="table-container" style={{ border: '1px solid var(--border-color)', borderRadius: '8px' }}>
              <table className="data-table">
                <thead>
                  <tr>
                    <th style={{ width: '20%' }}>Thành phần</th>
                    <th style={{ width: '26%' }}>Cấu hình mặc định (Model)</th>
                    <th style={{ width: '26%' }}>Cấu hình thực tế (Actual)</th>
                    <th style={{ width: '28%', backgroundColor: '#eff6ff', color: '#1e3a8a' }}>
                      Cấu hình hiệu lực (Effective)
                    </th>
                  </tr>
                </thead>
                <tbody>
                  <tr>
                    <td><strong>Bộ vi xử lý (CPU)</strong></td>
                    <td style={{ color: 'var(--text-muted)' }}>{cfg.defaultCpu || '—'}</td>
                    <td>{cfg.actualCpu ? <span>{cfg.actualCpu}</span> : <span style={{ color: '#94a3b8', fontStyle: 'italic' }}>Theo mặc định</span>}</td>
                    <td style={{ backgroundColor: '#eff6ff', fontWeight: 600, color: '#1d4ed8' }}>
                      {cfg.effectiveCpu || '—'}
                    </td>
                  </tr>
                  <tr>
                    <td><strong>Bộ nhớ (RAM)</strong></td>
                    <td style={{ color: 'var(--text-muted)' }}>{cfg.defaultRam || '—'}</td>
                    <td>{cfg.actualRam ? <span>{cfg.actualRam}</span> : <span style={{ color: '#94a3b8', fontStyle: 'italic' }}>Theo mặc định</span>}</td>
                    <td style={{ backgroundColor: '#eff6ff', fontWeight: 600, color: '#1d4ed8' }}>
                      {cfg.effectiveRam || '—'}
                    </td>
                  </tr>
                  <tr>
                    <td><strong>Ổ cứng lưu trữ</strong></td>
                    <td style={{ color: 'var(--text-muted)' }}>{cfg.defaultStorage || '—'}</td>
                    <td>{cfg.actualStorage ? <span>{cfg.actualStorage}</span> : <span style={{ color: '#94a3b8', fontStyle: 'italic' }}>Theo mặc định</span>}</td>
                    <td style={{ backgroundColor: '#eff6ff', fontWeight: 600, color: '#1d4ed8' }}>
                      {cfg.effectiveStorage || '—'}
                    </td>
                  </tr>
                  <tr>
                    <td><strong>Card màn hình (GPU)</strong></td>
                    <td style={{ color: 'var(--text-muted)' }}>{cfg.defaultGraphicsCard || '—'}</td>
                    <td>{cfg.actualGraphicsCard ? <span>{cfg.actualGraphicsCard}</span> : <span style={{ color: '#94a3b8', fontStyle: 'italic' }}>Theo mặc định</span>}</td>
                    <td style={{ backgroundColor: '#eff6ff', fontWeight: 600, color: '#1d4ed8' }}>
                      {cfg.effectiveGraphicsCard || '—'}
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
          </div>

          {/* Section 3: Audit Information */}
          <div
            style={{
              display: 'flex',
              justifyContent: 'space-between',
              fontSize: '12px',
              color: 'var(--text-muted)',
              borderTop: '1px solid var(--border-color)',
              paddingTop: '12px',
            }}
          >
            <div>
              Tạo bởi: <strong>{asset.createdByFullName || 'Hệ thống'}</strong> ({formatDate(asset.createdAt)})
            </div>
            {asset.updatedAt && (
              <div>
                Cập nhật lần cuối: <strong>{asset.updatedByFullName || 'Hệ thống'}</strong> ({formatDate(asset.updatedAt)})
              </div>
            )}
          </div>
        </div>

        {/* Modal Footer */}
        <div className="modal-footer">
          <button type="button" className="btn btn-secondary" onClick={onClose}>
            Đóng
          </button>
          <button
            type="button"
            className="btn btn-primary"
            onClick={() => {
              onClose();
              onEdit(asset);
            }}
          >
            ✏️ Chỉnh sửa tài sản
          </button>
        </div>
      </div>
    </div>
  );
};
