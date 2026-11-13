import CloseOutlinedIcon from '@mui/icons-material/CloseOutlined';
import WarningAmberOutlinedIcon from '@mui/icons-material/WarningAmberOutlined';
import { useTranslation } from 'react-i18next';
import React, { useState, useEffect, useRef } from 'react';
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
import { LicenseFields } from './LicenseFields';
import { LicenseInput } from '../types/asset.types';
import { categoryApi } from '@/features/catalogs/api/catalogApi';
import { AssetCategoryItem } from '@/features/catalogs/types/catalog.types';
import { assetApi } from '../api/assetApi';
import { changeStorageUnit, normalizeRam, normalizeStorage, StorageType, StorageUnit } from './assetCapacity';

interface AssetFormModalProps {
  isOpen: boolean;
  onClose: () => void;
  initialData: AssetDetail | null;
  onSubmit: (data: CreateHardwareAssetPayload | UpdateHardwareAssetPayload) => Promise<void>;
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
  const dialogRef = useRef<HTMLDialogElement>(null);
  const errorDialogRef = useRef<HTMLDialogElement>(null);
  const submitRef = useRef<HTMLButtonElement>(null);
  useEffect(() => {
    const dialog = dialogRef.current;
    if (!isOpen || !dialog) return;
    const opener = document.activeElement;
    dialog.showModal();
    return () => {
      dialog.close();
      if (opener instanceof HTMLElement && opener.isConnected) opener.focus();
    };
  }, [isOpen]);

  const [categories, setCategories] = useState<AssetCategoryItem[]>([]);
  const [license, setLicense] = useState<LicenseInput>({softwareCatalogId:0,assignmentTypeId:0,termTypeId:0,seatCount:1});
  const [catalogError, setCatalogError] = useState(false);
  useEffect(() => { if (!isOpen) return; let active=true; setCatalogError(false);
    categoryApi.getAll(0,100).then(r=>{if(active)setCategories(r.data.content);}).catch(()=>{if(active)setCatalogError(true);});
    return ()=>{active=false;};
  },[isOpen]);
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
  const [storageUnit, setStorageUnit] = useState<StorageUnit>('GB');
  const [storageType, setStorageType] = useState<StorageType>('');
  const [actualGraphicsCard, setActualGraphicsCard] = useState('');

  const category = categories.find(c=>c.categoryId===types.find(x=>x.typeId===typeId)?.categoryId)?.code;
  const isLicense = category==='LICENSE';

  // Validation warnings / errors
  const [formError, setFormError] = useState<string | null>(null);
  const [submitError, setSubmitError] = useState<string | null>(null);
  useEffect(() => {
    const dialog = errorDialogRef.current;
    if (!submitError || !dialog) return;
    const submitButton = submitRef.current;
    dialog.showModal();
    return () => {
      dialog.close();
      if (submitButton?.isConnected) submitButton.focus();
    };
  }, [submitError]);
  const [tagError, setTagError] = useState<string | null>(null);
  const [serialError, setSerialError] = useState<string | null>(null);

  // Selected model for previewing defaults
  const selectedModel = models.find((m) => m.modelId === Number(modelId));
  const selectedTypeId = Number(typeId || types[0]?.typeId || 0);
  const compatibleModels = models.filter((model) => model.typeId === selectedTypeId);

  useEffect(() => {
    if (models.length > 0 && modelId !== '' && !models.some((model) => model.modelId === modelId && model.typeId === selectedTypeId)) {
      setModelId('');
    }
  }, [modelId, models, selectedTypeId]);

  useEffect(() => {
    if (!isOpen) return;

    setLicense(initialData?.license || {softwareCatalogId:0,assignmentTypeId:0,termTypeId:0,seatCount:1});
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
      setStorageUnit(/\bTB\b/i.test(cfg?.actualStorage || '') ? 'TB' : 'GB');
      setStorageType('');
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
      setStorageUnit('GB');
      setStorageType('');
      setActualGraphicsCard('');
    }

    setFormError(null);
    setSubmitError(null);
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
      actualRam: normalizeRam(actualRam) || undefined,
      actualStorage: normalizeStorage(actualStorage, storageUnit, storageType) || undefined,
      actualGraphicsCard: actualGraphicsCard.trim() ? actualGraphicsCard.trim() : undefined,
    } as CreateHardwareAssetPayload | UpdateHardwareAssetPayload;

    if (isEdit) {
      (payload as UpdateHardwareAssetPayload).expectedVersion = initialData.version;
    } else {
      (payload as CreateHardwareAssetPayload).creationPurpose = 'BASELINE';
    }

    if (isLicense) {
      payload.license = license;
      payload.statusId = statuses.find(s => s.code === 'IN_STOCK')?.statusId;
      for (const key of ['serialNumber', 'modelId', 'conditionId', 'warrantyExpiration',
        'actualCpu', 'actualRam', 'actualStorage', 'actualGraphicsCard'] as const) {
        delete payload[key];
      }
    }
    try {
      await onSubmit(payload);
    } catch (err: any) {
      setSubmitError(err.message || t('form.saveError'));
    }
  };

  if (!isOpen) return null;

  return (
    <><dialog ref={dialogRef} className="catalog-dialog modal-content asset-form-dialog" aria-labelledby="asset-form-title"
      onCancel={event => { event.preventDefault(); if (!isSaving) onClose(); }}>
      <div className="asset-form-frame">
        {/* Header */}
        <div className="modal-header">
          <div className="modal-title" id="asset-form-title">
            {isEdit ? `${t('modal.editTitle')}: ${initialData.assetTag}` : t('modal.createTitle')}
          </div>
          <button type="button" className="btn-close" aria-label={t('common:buttons.close')} onClick={onClose} disabled={isSaving}>
            <CloseOutlinedIcon fontSize="small" aria-hidden="true" />
          </button>
        </div>

        {/* Body */}
        <form onSubmit={handleSubmit} className="asset-form-body" aria-busy={isSaving}>
          <p className="text-muted">{t('form.opening')}</p>
          {formError && (
            <div style={{ backgroundColor: 'var(--danger-light)', color: 'var(--danger)', padding: '12px 16px', borderRadius: '6px', fontSize: '14px', fontWeight: 500 }}>
              <WarningAmberOutlinedIcon fontSize="small" aria-hidden="true" /> {formError}
            </div>
          )}

          {/* Section 1: Basic Information */}
          <div>
            <div className="asset-form-section-title">
              {t('form.basic')}
            </div>

            <div className="asset-form-grid">
              {/* Asset Tag */}
              <div className="form-group">
                <label className="form-label">
                  {t('form.autoTag')}
                </label>
                <input
                  type="text"
                  className="form-input"
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
                  {t('fields.name')} <span aria-hidden="true" style={{ color: 'var(--danger)' }}>*</span>
                </label>
                <input
                  type="text"
                  className="form-input"
                  value={name}
                  onChange={(e) => setName(e.target.value)}
                  placeholder="VD: Laptop Lenovo ThinkPad T14 Gen 4"
                  required
                />
              </div>

              {/* Asset Type */}
              <div className="form-group">
                <label className="form-label">
                  {t('fields.type')} <span aria-hidden="true" style={{ color: 'var(--danger)' }}>*</span>
                </label>
                <select
                  className="form-input"
                  value={typeId}
                  disabled={isEdit}
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
                  className="form-input"
                  value={statusId}
                  disabled
                  onChange={(e) => setStatusId(e.target.value ? Number(e.target.value) : '')}
                >
                  {statuses.filter(s => isEdit ? s.statusId === statusId : s.code === 'IN_STOCK').map((s) => (
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
                  className="form-input"
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
                  className="form-input"
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

            </div>
          </div>

          <div className="asset-form-section">
            <div className="asset-form-section-title">{t('form.procurement', { defaultValue: 'Procurement' })}</div>
            <div className="asset-form-grid">
              {/* Supplier */}
              <div className="form-group">
                <label className="form-label">{t('fields.supplier')}</label>
                <select
                  className="form-input"
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
                  className="form-input"
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
                  className="form-input"
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
                  className="form-input"
                  value={purchaseCost}
                  onChange={(e) => setPurchaseCost(e.target.value ? Number(e.target.value) : '')}
                  placeholder="0"
                />
              </div>
            </div>
          </div>

          {catalogError && <p role="alert">{t('relationships.loadError')}</p>}
          {isLicense ? <LicenseFields value={license} onChange={setLicense} /> : <>
          {/* Section 2: Hardware Details & Specs */}
          <div className="asset-form-section">
            <div className="asset-form-section-title">
              {t('form.hardware')}
            </div>

            <div className="asset-form-grid">
              {/* Model */}
              <div className="form-group">
                <label className="form-label">{t('fields.model')}</label>
                <select
                  className="form-input"
                  value={modelId}
                  onChange={(e) => setModelId(e.target.value ? Number(e.target.value) : '')}
                >
                  <option value="">{t('form.chooseModel')}</option>
                  {compatibleModels.map((m) => (
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
                  className="form-input"
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
                  className="form-input"
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
                  className="form-input"
                  value={warrantyExpiration}
                  onChange={(e) => setWarrantyExpiration(e.target.value)}
                />
              </div>
            </div>

            {/* Model Default Specs Preview Box */}
            {selectedModel && (
              <div className="asset-model-defaults">
                <div className="asset-model-defaults-title">
                  {t('form.defaultTitle')} {selectedModel.brand} {selectedModel.name}
                </div>
                <div className="asset-form-grid asset-defaults-grid">
                  <div>• <strong>CPU:</strong> {selectedModel.defaultCpu || t('form.none')}</div>
                  <div>• <strong>RAM:</strong> {selectedModel.defaultRam || t('form.none')}</div>
                  <div>• <strong>{t('fields.storage')}</strong> {selectedModel.defaultStorage || t('form.none')}</div>
                  <div>• <strong>GPU:</strong> {selectedModel.defaultGraphicsCard || t('form.none')}</div>
                </div>
                <div className="asset-defaults-hint">
                  {t('form.defaultHint')}
                </div>
              </div>
            )}

            {/* Actual Specs Fields */}
            <div className="asset-form-grid asset-actual-grid">
              <div className="form-group">
                <label className="form-label">{t('fields.cpu')}</label>
                <input
                  type="text"
                  className="form-input"
                  value={actualCpu}
                  onChange={(e) => setActualCpu(e.target.value)}
                  placeholder={selectedModel?.defaultCpu ? t('form.defaultValue', { value: selectedModel.defaultCpu }) : t('form.cpu')}
                />
              </div>

              <div className="form-group">
                <label className="form-label">{t('fields.ram')}</label>
                <input
                  type="text"
                  className="form-input"
                  value={actualRam}
                  onChange={(e) => setActualRam(e.target.value)}
                  onBlur={() => setActualRam(normalizeRam(actualRam))}
                  placeholder={selectedModel?.defaultRam ? t('form.defaultValue', { value: selectedModel.defaultRam }) : t('form.ram')}
                />
                <small className="asset-capacity-hint">{t('form.ramUnitHint')}</small>
              </div>

              <div className="form-group asset-storage-fields">
                <label className="form-label">{t('fields.storage')}
                <input
                  type="text"
                  className="form-input"
                  value={actualStorage}
                  onChange={(e) => setActualStorage(e.target.value)}
                  onBlur={() => setActualStorage(normalizeStorage(actualStorage, storageUnit, storageType))}
                  placeholder={selectedModel?.defaultStorage ? t('form.defaultValue', { value: selectedModel.defaultStorage }) : t('form.storage')}
                />
                </label>
                <div className="asset-storage-options">
                  <label>{t('form.storageUnit')}
                    <select className="form-input" value={storageUnit} onChange={(e) => {
                      const next = e.target.value as StorageUnit;
                      setActualStorage(changeStorageUnit(actualStorage, storageUnit, next));
                      setStorageUnit(next);
                    }}>
                      <option value="GB">GB</option><option value="TB">TB</option>
                    </select>
                  </label>
                  <label>{t('form.storageType')}
                    <select className="form-input" value={storageType} onChange={(e) => {
                      const next = e.target.value as StorageType;
                      setStorageType(next);
                      setActualStorage(normalizeStorage(actualStorage, storageUnit, next));
                    }}>
                      <option value="">{t('form.storageTypeUnspecified')}</option>
                      <option value="HDD">HDD</option><option value="SSD">SSD</option>
                    </select>
                  </label>
                </div>
              </div>

              <div className="form-group">
                <label className="form-label">{t('fields.gpu')}</label>
                <input
                  type="text"
                  className="form-input"
                  value={actualGraphicsCard}
                  onChange={(e) => setActualGraphicsCard(e.target.value)}
                  placeholder={selectedModel?.defaultGraphicsCard ? t('form.defaultValue', { value: selectedModel.defaultGraphicsCard }) : t('form.gpu')}
                />
              </div>
            </div>
          </div>

          </>}
          {/* Footer */}
          <div className="modal-footer asset-form-footer">
            <button type="button" className="btn btn-secondary" onClick={onClose} disabled={isSaving}>
              {t('form.cancel')}
            </button>
            <button ref={submitRef} type="submit" className="btn btn-primary" disabled={isSaving || catalogError || !category}>
              {isSaving ? t('form.saving') : isEdit ? t('form.save') : t('form.create')}
            </button>
          </div>
        </form>
      </div>
    </dialog>
    {submitError && <dialog ref={errorDialogRef} className="catalog-dialog modal-content asset-submit-error-dialog"
      aria-labelledby="asset-submit-error-title" aria-describedby="asset-submit-error-message"
      onCancel={event => { event.preventDefault(); event.stopPropagation(); setSubmitError(null); }}>
      <div className="modal-header"><div className="modal-title" id="asset-submit-error-title">{t('form.saveError')}</div></div>
      <p id="asset-submit-error-message">{submitError}</p>
      <div className="modal-footer"><button type="button" className="btn btn-primary" onClick={() => setSubmitError(null)}>{t('common:buttons.close')}</button></div>
    </dialog>}</>
  );
};
