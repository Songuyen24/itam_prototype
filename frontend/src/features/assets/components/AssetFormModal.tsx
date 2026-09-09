import { useTranslation } from 'react-i18next';
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
  const { t } = useTranslation('assets');
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

    if (!name.trim()) {
      setFormError(t('form.nameRequired'));
      return;
    }
    if (!typeId) {
      setFormError(t('form.typeRequired'));
      return;
    }
    if (purchaseCost !== '' && Number(purchaseCost) < 0) {
      setFormError(t('form.costInvalid'));
      return;
    }

    const payload: CreateHardwareAssetPayload | UpdateHardwareAssetPayload = {
      assetTag: assetTag.trim(),
      name: name.trim(),
      typeId: Number(typeId),
      statusId: statusId !== '' ? Number(statusId) : undefined,
      assignedToUserId: initialData?.assignedToUserId,
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
      setFormError(err.message || t('form.saveError'));
    }
  };

  if (!isOpen) return null;

  return (
    <div className="modal-backdrop">
      <div className="modal-content" style={{ maxWidth: '850px', maxHeight: '90vh', display: 'flex', flexDirection: 'column' }}>
        {/* Header */}
        <div className="modal-header">
          <div className="modal-title">
            {isEdit ? `${t('modal.editTitle')}: ${initialData.assetTag}` : t('modal.createTitle')}
          </div>
          <button type="button" className="btn-close" onClick={onClose} disabled={isSaving}>
            ✕
          </button>
        </div>

        {/* Body */}
        <form onSubmit={handleSubmit} style={{ overflowY: 'auto', padding: '20px', display: 'flex', flexDirection: 'column', gap: '20px' }}>
          <p className="text-muted">{t('form.opening')}</p>
          {formError && (
            <div style={{ backgroundColor: 'var(--danger-light)', color: 'var(--danger)', padding: '12px 16px', borderRadius: '6px', fontSize: '14px', fontWeight: 500 }}>
              ⚠️ {formError}
            </div>
          )}

          {/* Section 1: Basic Information */}
          <div>
            <div style={{ fontSize: '15px', fontWeight: 700, color: 'var(--primary)', marginBottom: '12px', borderBottom: '2px solid var(--primary-light)', paddingBottom: '6px' }}>
              {t('form.basic')}
            </div>

            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(2, 1fr)', gap: '16px' }}>
              {/* Asset Tag */}
              <div className="form-group">
                <label className="form-label">
                  {t('form.autoTag')}
                </label>
                <input
                  type="text"
                  className="form-control"
                  value={assetTag}
                  onChange={(e) => setAssetTag(e.target.value)}
                  onBlur={checkTagUniqueness}
                  placeholder="AST-…"
                  disabled={isEdit}
                />
                {tagError && <div className="form-error">{tagError}</div>}
              </div>

              {/* Asset Name */}
              <div className="form-group">
                <label className="form-label">
                  {t('fields.name')} <span style={{ color: 'var(--danger)' }}>*</span>
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
                  {t('fields.type')} <span style={{ color: 'var(--danger)' }}>*</span>
                </label>
                <select
                  className="form-control"
                  value={typeId}
                  onChange={(e) => setTypeId(e.target.value ? Number(e.target.value) : '')}
                  required
                >
                  <option value="">{t('form.chooseType')}</option>
                  {types.map((t) => (
                    <option key={t.typeId} value={t.typeId}>
                      {t.name} ({t.categoryName || 'Device'})
                    </option>
                  ))}
                </select>
              </div>

              {/* Status */}
              <div className="form-group">
                <label className="form-label">{t('fields.status')}</label>
                <select
                  className="form-control"
                  value={statusId}
                  disabled={!!initialData?.assignedToUserId}
                  onChange={(e) => setStatusId(e.target.value ? Number(e.target.value) : '')}
                >
                  {statuses.filter(s => s.code !== 'PENDING_IMPORT' && (s.code !== 'IN_USE' || initialData?.statusCode === 'IN_USE')).map((s) => (
                    <option key={s.statusId} value={s.statusId}>
                      {t(`common:status.${s.code}`, {defaultValue:s.name})}
                    </option>
                  ))}
                </select>
                <small style={{ color: 'var(--text-muted)', fontSize: '11px' }}>
                  {t('form.inStockHint')}
                </small>
              </div>

              {/* Department */}
              <div className="form-group">
                <label className="form-label">{t('fields.department')}</label>
                <select
                  className="form-control"
                  value={departmentId}
                  onChange={(e) => setDepartmentId(e.target.value ? Number(e.target.value) : '')}
                >
                  <option value="">{t('form.chooseDepartment')}</option>
                  {departments.map((d) => (
                    <option key={d.departmentId} value={d.departmentId}>
                      {d.name}
                    </option>
                  ))}
                </select>
              </div>

              {/* Location */}
              <div className="form-group">
                <label className="form-label">{t('fields.location')}</label>
                <select
                  className="form-control"
                  value={locationId}
                  onChange={(e) => setLocationId(e.target.value ? Number(e.target.value) : '')}
                >
                  <option value="">{t('form.chooseLocation')}</option>
                  {locations.map((l) => (
                    <option key={l.locationId} value={l.locationId}>
                      {l.name}
                    </option>
                  ))}
                </select>
              </div>

              {/* Supplier */}
              <div className="form-group">
                <label className="form-label">{t('fields.supplier')}</label>
                <select
                  className="form-control"
                  value={supplierId}
                  onChange={(e) => setSupplierId(e.target.value ? Number(e.target.value) : '')}
                >
                  <option value="">{t('form.chooseSupplier')}</option>
                  {suppliers.map((s) => (
                    <option key={s.supplierId} value={s.supplierId}>
                      {s.name}
                    </option>
                  ))}
                </select>
              </div>

              {/* PO Number */}
              <div className="form-group">
                <label className="form-label">{t('fields.poNumber')}</label>
                <input
                  type="text"
                  className="form-control"
                  value={poNumber}
                  onChange={(e) => setPoNumber(e.target.value)}
                  placeholder="PO-2026-001"
                />
              </div>

              {/* Purchase Date */}
              <div className="form-group">
                <label className="form-label">{t('fields.purchaseDate')}</label>
                <input
                  type="date"
                  className="form-control"
                  value={purchaseDate}
                  onChange={(e) => setPurchaseDate(e.target.value)}
                />
              </div>

              {/* Purchase Cost */}
              <div className="form-group">
                <label className="form-label">{t('fields.purchaseCost')}</label>
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
              {t('form.hardware')}
            </div>

            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(2, 1fr)', gap: '16px' }}>
              {/* Model */}
              <div className="form-group">
                <label className="form-label">{t('fields.model')}</label>
                <select
                  className="form-control"
                  value={modelId}
                  onChange={(e) => setModelId(e.target.value ? Number(e.target.value) : '')}
                >
                  <option value="">{t('form.chooseModel')}</option>
                  {models.map((m) => (
                    <option key={m.modelId} value={m.modelId}>
                      {m.brand} - {m.name}
                    </option>
                  ))}
                </select>
              </div>

              {/* Serial Number */}
              <div className="form-group">
                <label className="form-label">{t('fields.serialNumber')}</label>
                <input
                  type="text"
                  className="form-control"
                  value={serialNumber}
                  onChange={(e) => setSerialNumber(e.target.value)}
                  onBlur={checkSerialUniqueness}
                  placeholder="PF4X89L1"
                />
                {serialError && <div className="form-error">{serialError}</div>}
              </div>

              {/* Condition */}
              <div className="form-group">
                <label className="form-label">{t('fields.condition')}</label>
                <select
                  className="form-control"
                  value={conditionId}
                  onChange={(e) => setConditionId(e.target.value ? Number(e.target.value) : '')}
                >
                  <option value="">{t('form.chooseCondition')}</option>
                  {conditions.map((c) => (
                    <option key={c.conditionId} value={c.conditionId}>
                      {c.name}
                    </option>
                  ))}
                </select>
              </div>

              {/* Warranty Expiration */}
              <div className="form-group">
                <label className="form-label">{t('fields.warrantyExpiration')}</label>
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
                  {t('form.defaultTitle')} {selectedModel.brand} {selectedModel.name}
                </div>
                <div style={{ display: 'grid', gridTemplateColumns: 'repeat(2, 1fr)', gap: '8px', fontSize: '12px', color: '#1e3a8a' }}>
                  <div>• <strong>CPU:</strong> {selectedModel.defaultCpu || t('form.none')}</div>
                  <div>• <strong>RAM:</strong> {selectedModel.defaultRam || t('form.none')}</div>
                  <div>• <strong>{t('fields.storage')}</strong> {selectedModel.defaultStorage || t('form.none')}</div>
                  <div>• <strong>GPU:</strong> {selectedModel.defaultGraphicsCard || t('form.none')}</div>
                </div>
                <div style={{ marginTop: '8px', fontSize: '11px', color: 'var(--text-muted)' }}>
                  {t('form.defaultHint')}
                </div>
              </div>
            )}

            {/* Actual Specs Fields */}
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(2, 1fr)', gap: '16px', marginTop: '12px' }}>
              <div className="form-group">
                <label className="form-label">{t('fields.cpu')}</label>
                <input
                  type="text"
                  className="form-control"
                  value={actualCpu}
                  onChange={(e) => setActualCpu(e.target.value)}
                  placeholder={selectedModel?.defaultCpu ? t('form.defaultValue', { value: selectedModel.defaultCpu }) : t('form.cpu')}
                />
              </div>

              <div className="form-group">
                <label className="form-label">{t('fields.ram')}</label>
                <input
                  type="text"
                  className="form-control"
                  value={actualRam}
                  onChange={(e) => setActualRam(e.target.value)}
                  placeholder={selectedModel?.defaultRam ? t('form.defaultValue', { value: selectedModel.defaultRam }) : t('form.ram')}
                />
              </div>

              <div className="form-group">
                <label className="form-label">{t('fields.storage')}</label>
                <input
                  type="text"
                  className="form-control"
                  value={actualStorage}
                  onChange={(e) => setActualStorage(e.target.value)}
                  placeholder={selectedModel?.defaultStorage ? t('form.defaultValue', { value: selectedModel.defaultStorage }) : t('form.storage')}
                />
              </div>

              <div className="form-group">
                <label className="form-label">{t('fields.gpu')}</label>
                <input
                  type="text"
                  className="form-control"
                  value={actualGraphicsCard}
                  onChange={(e) => setActualGraphicsCard(e.target.value)}
                  placeholder={selectedModel?.defaultGraphicsCard ? t('form.defaultValue', { value: selectedModel.defaultGraphicsCard }) : t('form.gpu')}
                />
              </div>
            </div>
          </div>

          {/* Footer */}
          <div className="modal-footer" style={{ padding: '16px 0 0 0', marginTop: '10px' }}>
            <button type="button" className="btn btn-secondary" onClick={onClose} disabled={isSaving}>
              {t('form.cancel')}
            </button>
            <button type="submit" className="btn btn-primary" disabled={isSaving}>
              {isSaving ? t('form.saving') : isEdit ? t('form.save') : t('form.create')}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};
