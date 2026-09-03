import React, { useState, useEffect } from 'react';
import {
  Department,
  LocationItem,
  Supplier,
  AssetTypeItem,
  AssetStatusItem,
  AssetConditionItem,
  ModelItem,
} from '@/features/catalogs/types/catalog.types';
import {
  AssetDetail,
  CreateHardwareAssetPayload,
  UpdateHardwareAssetPayload,
} from '../types/asset.types';
import { assetApi } from '../api/assetApi';

interface AssetFormModalProps {
  isOpen: boolean;
  onClose: () => void;
  initialData: AssetDetail | null;
  onSubmit: (data: any) => Promise<void>;
  isSaving: boolean;

  types: AssetTypeItem[];
  statuses: AssetStatusItem[];
  conditions: AssetConditionItem[];
  models: ModelItem[];
  departments: Department[];
  locations: LocationItem[];
  suppliers: Supplier[];
}

export const AssetFormModal: React.FC<AssetFormModalProps> = ({
  isOpen,
  onClose,
  initialData,
  onSubmit,
  isSaving,
  types,
  statuses,
  conditions,
  models,
  departments,
  locations,
  suppliers,
}) => {
  const isEdit = !!initialData;

  // Form states
  const [assetTag, setAssetTag] = useState('');
  const [name, setName] = useState('');
  const [typeId, setTypeId] = useState<number | ''>('');
  const [statusId, setStatusId] = useState<number | ''>('');
  const [departmentId, setDepartmentId] = useState<number | ''>('');
  const [locationId, setLocationId] = useState<number | ''>('');
  const [supplierId, setSupplierId] = useState<number | ''>('');
  const [poNumber, setPoNumber] = useState('');
  const [purchaseDate, setPurchaseDate] = useState('');
  const [purchaseCost, setPurchaseCost] = useState<number | ''>('');

  // Hardware Details states
  const [serialNumber, setSerialNumber] = useState('');
  const [modelId, setModelId] = useState<number | ''>('');
  const [conditionId, setConditionId] = useState<number | ''>('');
  const [warrantyExpiration, setWarrantyExpiration] = useState('');
  const [actualCpu, setActualCpu] = useState('');
  const [actualRam, setActualRam] = useState('');
  const [actualStorage, setActualStorage] = useState('');
  const [actualGraphicsCard, setActualGraphicsCard] = useState('');

  // Validation warnings / errors
  const [formError, setFormError] = useState<string | null>(null);
  const [tagError, setTagError] = useState<string | null>(null);
  const [serialError, setSerialError] = useState<string | null>(null);

  // Selected model for previewing defaults
  const selectedModel = models.find((m) => m.modelId === Number(modelId));

  useEffect(() => {
    if (!isOpen) return;

    if (initialData) {
      setAssetTag(initialData.assetTag || '');
      setName(initialData.name || '');
      setTypeId(initialData.typeId ?? '');
      setStatusId(initialData.statusId ?? '');
      setDepartmentId(initialData.departmentId ?? '');
      setLocationId(initialData.locationId ?? '');
      setSupplierId(initialData.supplierId ?? '');
      setPoNumber(initialData.poNumber || '');
      setPurchaseDate(initialData.purchaseDate || '');
      setPurchaseCost(initialData.purchaseCost ?? '');

      setSerialNumber(initialData.serialNumber || '');
      setModelId(initialData.modelId ?? '');
      setConditionId(initialData.conditionId ?? '');
      setWarrantyExpiration(initialData.warrantyExpiration || '');

      const cfg = initialData.hardwareConfig;
      setActualCpu(cfg?.actualCpu || '');
      setActualRam(cfg?.actualRam || '');
      setActualStorage(cfg?.actualStorage || '');
      setActualGraphicsCard(cfg?.actualGraphicsCard || '');
    } else {
      // Defaults for create
      setAssetTag('');
      setName('');
      setTypeId(types.length > 0 ? types[0].typeId : '');
      const defaultStatus = statuses.find((s) => s.code === 'IN_STOCK');
      setStatusId(defaultStatus ? defaultStatus.statusId : '');
      setDepartmentId('');
      setLocationId('');
      setSupplierId('');
      setPoNumber('');
      setPurchaseDate('');
      setPurchaseCost('');

      setSerialNumber('');
      setModelId('');
      setConditionId(conditions.length > 0 ? conditions[0].conditionId : '');
      setWarrantyExpiration('');
      setActualCpu('');
      setActualRam('');
      setActualStorage('');
      setActualGraphicsCard('');
    }

    setFormError(null);
    setTagError(null);
    setSerialError(null);
  }, [isOpen, initialData, types, statuses, conditions]);

  // Validate uniqueness on blur
  const checkTagUniqueness = async () => {
    if (!assetTag.trim()) return;
    try {
      const res = await assetApi.validateUniqueness({
        assetTag: assetTag.trim(),
        excludeAssetId: initialData?.assetId,
      });
      if (res.success && !res.data.assetTagAvailable) {
        setTagError(res.data.assetTagMessage);
      } else {
        setTagError(null);
      }
    } catch {
      // ignore network check error
    }
  };

  const checkSerialUniqueness = async () => {
    if (!serialNumber.trim()) return;
    try {
      const res = await assetApi.validateUniqueness({
        serialNumber: serialNumber.trim(),
        excludeAssetId: initialData?.assetId,
      });
      if (res.success && !res.data.serialNumberAvailable) {
        setSerialError(res.data.serialNumberMessage);
      } else {
        setSerialError(null);
      }
    } catch {
      // ignore network check error
    }
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setFormError(null);

    if (!assetTag.trim()) {
      setFormError('Mã tài sản (Asset Tag) là bắt buộc');
      return;
    }
    if (!name.trim()) {
      setFormError('Tên tài sản là bắt buộc');
      return;
    }
    if (!typeId) {
      setFormError('Vui lòng chọn loại tài sản');
      return;
    }
    if (purchaseCost !== '' && Number(purchaseCost) < 0) {
      setFormError('Giá mua không được âm');
      return;
    }

    const payload: CreateHardwareAssetPayload | UpdateHardwareAssetPayload = {
      assetTag: assetTag.trim(),
      name: name.trim(),
      typeId: Number(typeId),
      statusId: statusId !== '' ? Number(statusId) : undefined,
      departmentId: departmentId !== '' ? Number(departmentId) : undefined,
      locationId: locationId !== '' ? Number(locationId) : undefined,
      supplierId: supplierId !== '' ? Number(supplierId) : undefined,
      poNumber: poNumber.trim() ? poNumber.trim() : undefined,
      purchaseDate: purchaseDate || undefined,
      purchaseCost: purchaseCost !== '' ? Number(purchaseCost) : undefined,

      serialNumber: serialNumber.trim() ? serialNumber.trim() : undefined,
      modelId: modelId !== '' ? Number(modelId) : undefined,
      conditionId: conditionId !== '' ? Number(conditionId) : undefined,
      warrantyExpiration: warrantyExpiration || undefined,
      actualCpu: actualCpu.trim() ? actualCpu.trim() : undefined,
      actualRam: actualRam.trim() ? actualRam.trim() : undefined,
      actualStorage: actualStorage.trim() ? actualStorage.trim() : undefined,
      actualGraphicsCard: actualGraphicsCard.trim() ? actualGraphicsCard.trim() : undefined,
    };

    try {
      await onSubmit(payload);
    } catch (err: any) {
      setFormError(err.message || 'Lỗi khi lưu tài sản');
    }
  };

  if (!isOpen) return null;

  return (
    <div className="modal-backdrop">
      <div className="modal-content" style={{ maxWidth: '850px', maxHeight: '90vh', display: 'flex', flexDirection: 'column' }}>
        {/* Header */}
        <div className="modal-header">
          <div className="modal-title">
            {isEdit ? `✏️ Cập nhật tài sản: ${initialData.assetTag}` : '➕ Thêm mới tài sản phần cứng'}
          </div>
          <button type="button" className="btn-close" onClick={onClose} disabled={isSaving}>
            ✕
          </button>
        </div>

        {/* Body */}
        <form onSubmit={handleSubmit} style={{ overflowY: 'auto', padding: '20px', display: 'flex', flexDirection: 'column', gap: '20px' }}>
          {formError && (
            <div style={{ backgroundColor: 'var(--danger-light)', color: 'var(--danger)', padding: '12px 16px', borderRadius: '6px', fontSize: '14px', fontWeight: 500 }}>
              ⚠️ {formError}
            </div>
          )}

          {/* Section 1: Basic Information */}
          <div>
            <div style={{ fontSize: '15px', fontWeight: 700, color: 'var(--primary)', marginBottom: '12px', borderBottom: '2px solid var(--primary-light)', paddingBottom: '6px' }}>
              1. Thông tin chung
            </div>

            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(2, 1fr)', gap: '16px' }}>
              {/* Asset Tag */}
              <div className="form-group">
                <label className="form-label">
                  Mã tài sản (Asset Tag) <span style={{ color: 'var(--danger)' }}>*</span>
                </label>
                <input
                  type="text"
                  className="form-control"
                  value={assetTag}
                  onChange={(e) => setAssetTag(e.target.value)}
                  onBlur={checkTagUniqueness}
                  placeholder="VD: AST-LAP-001"
                  required
                />
                {tagError && <div className="form-error">{tagError}</div>}
              </div>

              {/* Asset Name */}
              <div className="form-group">
                <label className="form-label">
                  Tên tài sản <span style={{ color: 'var(--danger)' }}>*</span>
                </label>
                <input
                  type="text"
                  className="form-control"
                  value={name}
                  onChange={(e) => setName(e.target.value)}
                  placeholder="VD: Laptop Lenovo ThinkPad T14 Gen 4"
                  required
                />
              </div>

              {/* Asset Type */}
              <div className="form-group">
                <label className="form-label">
                  Loại tài sản <span style={{ color: 'var(--danger)' }}>*</span>
                </label>
                <select
                  className="form-control"
                  value={typeId}
                  onChange={(e) => setTypeId(e.target.value ? Number(e.target.value) : '')}
                  required
                >
                  <option value="">-- Chọn loại tài sản --</option>
                  {types.map((t) => (
                    <option key={t.typeId} value={t.typeId}>
                      {t.name} ({t.categoryName || 'Device'})
                    </option>
                  ))}
                </select>
              </div>

              {/* Status */}
              <div className="form-group">
                <label className="form-label">Trạng thái tài sản</label>
                <select
                  className="form-control"
                  value={statusId}
                  onChange={(e) => setStatusId(e.target.value ? Number(e.target.value) : '')}
                >
                  {statuses.map((s) => (
                    <option key={s.statusId} value={s.statusId}>
                      {s.name}
                    </option>
                  ))}
                </select>
                <small style={{ color: 'var(--text-muted)', fontSize: '11px' }}>
                  Lưu ý: Tài sản In Stock không được gán người dùng.
                </small>
              </div>

              {/* Department */}
              <div className="form-group">
                <label className="form-label">Phòng ban quản lý</label>
                <select
                  className="form-control"
                  value={departmentId}
                  onChange={(e) => setDepartmentId(e.target.value ? Number(e.target.value) : '')}
                >
                  <option value="">-- Chọn phòng ban --</option>
                  {departments.map((d) => (
                    <option key={d.departmentId} value={d.departmentId}>
                      {d.name}
                    </option>
                  ))}
                </select>
              </div>

              {/* Location */}
              <div className="form-group">
                <label className="form-label">Vị trí lưu trữ / đặt máy</label>
                <select
                  className="form-control"
                  value={locationId}
                  onChange={(e) => setLocationId(e.target.value ? Number(e.target.value) : '')}
                >
                  <option value="">-- Chọn vị trí --</option>
                  {locations.map((l) => (
                    <option key={l.locationId} value={l.locationId}>
                      {l.name}
                    </option>
                  ))}
                </select>
              </div>

              {/* Supplier */}
              <div className="form-group">
                <label className="form-label">Nhà cung cấp</label>
                <select
                  className="form-control"
                  value={supplierId}
                  onChange={(e) => setSupplierId(e.target.value ? Number(e.target.value) : '')}
                >
                  <option value="">-- Chọn nhà cung cấp --</option>
                  {suppliers.map((s) => (
                    <option key={s.supplierId} value={s.supplierId}>
                      {s.name}
                    </option>
                  ))}
                </select>
              </div>

              {/* PO Number */}
              <div className="form-group">
                <label className="form-label">Số đơn hàng (PO)</label>
                <input
                  type="text"
                  className="form-control"
                  value={poNumber}
                  onChange={(e) => setPoNumber(e.target.value)}
                  placeholder="VD: PO-2026-001"
                />
              </div>

              {/* Purchase Date */}
              <div className="form-group">
                <label className="form-label">Ngày mua</label>
                <input
                  type="date"
                  className="form-control"
                  value={purchaseDate}
                  onChange={(e) => setPurchaseDate(e.target.value)}
                />
              </div>

              {/* Purchase Cost */}
              <div className="form-group">
                <label className="form-label">Giá mua (VNĐ)</label>
                <input
                  type="number"
                  min="0"
                  step="1000"
                  className="form-control"
                  value={purchaseCost}
                  onChange={(e) => setPurchaseCost(e.target.value ? Number(e.target.value) : '')}
                  placeholder="0"
                />
              </div>
            </div>
          </div>

          {/* Section 2: Hardware Details & Specs */}
          <div>
            <div style={{ fontSize: '15px', fontWeight: 700, color: 'var(--primary)', marginBottom: '12px', borderBottom: '2px solid var(--primary-light)', paddingBottom: '6px' }}>
              2. Chi tiết phần cứng & Cấu hình (Default vs Actual)
            </div>

            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(2, 1fr)', gap: '16px' }}>
              {/* Model */}
              <div className="form-group">
                <label className="form-label">Model thiết bị</label>
                <select
                  className="form-control"
                  value={modelId}
                  onChange={(e) => setModelId(e.target.value ? Number(e.target.value) : '')}
                >
                  <option value="">-- Chọn Model --</option>
                  {models.map((m) => (
                    <option key={m.modelId} value={m.modelId}>
                      {m.brand} - {m.name}
                    </option>
                  ))}
                </select>
              </div>

              {/* Serial Number */}
              <div className="form-group">
                <label className="form-label">Số Serial (Serial Number)</label>
                <input
                  type="text"
                  className="form-control"
                  value={serialNumber}
                  onChange={(e) => setSerialNumber(e.target.value)}
                  onBlur={checkSerialUniqueness}
                  placeholder="VD: PF4X89L1"
                />
                {serialError && <div className="form-error">{serialError}</div>}
              </div>

              {/* Condition */}
              <div className="form-group">
                <label className="form-label">Tình trạng vật lý</label>
                <select
                  className="form-control"
                  value={conditionId}
                  onChange={(e) => setConditionId(e.target.value ? Number(e.target.value) : '')}
                >
                  <option value="">-- Chọn tình trạng --</option>
                  {conditions.map((c) => (
                    <option key={c.conditionId} value={c.conditionId}>
                      {c.name}
                    </option>
                  ))}
                </select>
              </div>

              {/* Warranty Expiration */}
              <div className="form-group">
                <label className="form-label">Ngày hết hạn bảo hành</label>
                <input
                  type="date"
                  className="form-control"
                  value={warrantyExpiration}
                  onChange={(e) => setWarrantyExpiration(e.target.value)}
                />
              </div>
            </div>

            {/* Model Default Specs Preview Box */}
            {selectedModel && (
              <div
                style={{
                  backgroundColor: 'var(--primary-light)',
                  border: '1px solid #bfdbfe',
                  borderRadius: '8px',
                  padding: '14px 16px',
                  margin: '16px 0',
                }}
              >
                <div style={{ fontSize: '13px', fontWeight: 700, color: 'var(--primary-hover)', marginBottom: '8px' }}>
                  📌 Cấu hình mặc định của Model: {selectedModel.brand} {selectedModel.name}
                </div>
                <div style={{ display: 'grid', gridTemplateColumns: 'repeat(2, 1fr)', gap: '8px', fontSize: '12px', color: '#1e3a8a' }}>
                  <div>• <strong>CPU:</strong> {selectedModel.defaultCpu || 'Không có'}</div>
                  <div>• <strong>RAM:</strong> {selectedModel.defaultRam || 'Không có'}</div>
                  <div>• <strong>Ổ cứng:</strong> {selectedModel.defaultStorage || 'Không có'}</div>
                  <div>• <strong>GPU:</strong> {selectedModel.defaultGraphicsCard || 'Không có'}</div>
                </div>
                <div style={{ marginTop: '8px', fontSize: '11px', color: 'var(--text-muted)' }}>
                  * Nếu cấu hình thực tế để trống, hệ thống sẽ tự động dùng cấu hình mặc định trên.
                </div>
              </div>
            )}

            {/* Actual Specs Fields */}
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(2, 1fr)', gap: '16px', marginTop: '12px' }}>
              <div className="form-group">
                <label className="form-label">CPU Thực tế (Actual CPU)</label>
                <input
                  type="text"
                  className="form-control"
                  value={actualCpu}
                  onChange={(e) => setActualCpu(e.target.value)}
                  placeholder={selectedModel?.defaultCpu ? `Mặc định: ${selectedModel.defaultCpu}` : 'Nhập CPU thực tế'}
                />
              </div>

              <div className="form-group">
                <label className="form-label">RAM Thực tế (Actual RAM)</label>
                <input
                  type="text"
                  className="form-control"
                  value={actualRam}
                  onChange={(e) => setActualRam(e.target.value)}
                  placeholder={selectedModel?.defaultRam ? `Mặc định: ${selectedModel.defaultRam}` : 'Nhập RAM thực tế'}
                />
              </div>

              <div className="form-group">
                <label className="form-label">Ổ cứng Thực tế (Actual Storage)</label>
                <input
                  type="text"
                  className="form-control"
                  value={actualStorage}
                  onChange={(e) => setActualStorage(e.target.value)}
                  placeholder={selectedModel?.defaultStorage ? `Mặc định: ${selectedModel.defaultStorage}` : 'Nhập Ổ cứng thực tế'}
                />
              </div>

              <div className="form-group">
                <label className="form-label">Card màn hình (Actual GPU)</label>
                <input
                  type="text"
                  className="form-control"
                  value={actualGraphicsCard}
                  onChange={(e) => setActualGraphicsCard(e.target.value)}
                  placeholder={selectedModel?.defaultGraphicsCard ? `Mặc định: ${selectedModel.defaultGraphicsCard}` : 'Nhập GPU thực tế'}
                />
              </div>
            </div>
          </div>

          {/* Footer */}
          <div className="modal-footer" style={{ padding: '16px 0 0 0', marginTop: '10px' }}>
            <button type="button" className="btn btn-secondary" onClick={onClose} disabled={isSaving}>
              Hủy
            </button>
            <button type="submit" className="btn btn-primary" disabled={isSaving}>
              {isSaving ? 'Đang lưu...' : isEdit ? 'Lưu thay đổi' : 'Tạo tài sản'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};
