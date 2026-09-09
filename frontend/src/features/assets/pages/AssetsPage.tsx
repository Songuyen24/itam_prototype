import React, { useState, useEffect, useCallback } from 'react';
import { useTranslation } from 'react-i18next';
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
  departmentApi,
  locationApi,
  supplierApi,
  assetTypeApi,
  assetStatusApi,
  assetConditionApi,
  modelApi,
} from '@/features/catalogs/api/catalogApi';
import { Asset, AssetDetail } from '../types/asset.types';
import { assetApi } from '../api/assetApi';
import { AssetFilterBar } from '../components/AssetFilterBar';
import { AssetFormModal } from '../components/AssetFormModal';
import { AssetDetailModal } from '../components/AssetDetailModal';
import { AssetImportModal } from '../components/AssetImportModal';
import { useAuth } from '@/features/auth/contexts/AuthContext';
import { canManageInventory } from '@/features/auth/permissions';

export const AssetsPage: React.FC<{ myAssets?: boolean }> = ({ myAssets = false }) => {
  const { t } = useTranslation(['assets', 'common']);
  const { user } = useAuth();
  const canManageAssets = !myAssets && canManageInventory(user?.role);
  // Filters & Pagination
  const [keyword, setKeyword] = useState('');
  const [statusId, setStatusId] = useState<number | undefined>();
  const [typeId, setTypeId] = useState<number | undefined>();
  const [modelId, setModelId] = useState<number | undefined>();
  const [departmentId, setDepartmentId] = useState<number | undefined>();
  const [locationId, setLocationId] = useState<number | undefined>();

  const [currentPage, setCurrentPage] = useState(0);
  const [pageSize] = useState(20);
  const [totalPages, setTotalPages] = useState(1);
  const [totalElements, setTotalElements] = useState(0);

  // Data
  const [assets, setAssets] = useState<Asset[]>([]);
  const [isLoading, setIsLoading] = useState(false);
  const [toastMessage, setToastMessage] = useState<{ type: 'success' | 'error'; text: string } | null>(null);

  // Auxiliary catalogs
  const [types, setTypes] = useState<AssetTypeItem[]>([]);
  const [statuses, setStatuses] = useState<AssetStatusItem[]>([]);
  const [conditions, setConditions] = useState<AssetConditionItem[]>([]);
  const [models, setModels] = useState<ModelItem[]>([]);
  const [departments, setDepartments] = useState<Department[]>([]);
  const [locations, setLocations] = useState<LocationItem[]>([]);
  const [suppliers, setSuppliers] = useState<Supplier[]>([]);

  // Modals
  const [isFormModalOpen, setIsFormModalOpen] = useState(false);
  const [isDetailModalOpen, setIsDetailModalOpen] = useState(false);
  const [isImportModalOpen, setIsImportModalOpen] = useState(false);
  const [selectedAssetDetail, setSelectedAssetDetail] = useState<AssetDetail | null>(null);
  const [isSaving, setIsSaving] = useState(false);

  // Load auxiliary catalog data only for roles allowed to manage the inventory.
  useEffect(() => {
    if (!canManageAssets) {
      setTypes([]);
      setStatuses([]);
      setConditions([]);
      setModels([]);
      setDepartments([]);
      setLocations([]);
      setSuppliers([]);
      return;
    }
    Promise.all([
      assetTypeApi.getAll(undefined, true, 0, 100),
      assetStatusApi.getAll(0, 100),
      assetConditionApi.getAll(0, 100),
      modelApi.getAll(undefined, true, 0, 100),
      departmentApi.getAll(undefined, true, 0, 100),
      locationApi.getAll(undefined, true, 0, 100),
      supplierApi.getAll(undefined, true, 0, 100),
    ]).then(([typesRes, statusesRes, conditionsRes, modelsRes, deptsRes, locsRes, suppRes]) => {
      if (typesRes.success && typesRes.data) setTypes(typesRes.data.content);
      if (statusesRes.success && statusesRes.data) setStatuses(statusesRes.data.content);
      if (conditionsRes.success && conditionsRes.data) setConditions(conditionsRes.data.content);
      if (modelsRes.success && modelsRes.data) setModels(modelsRes.data.content);
      if (deptsRes.success && deptsRes.data) setDepartments(deptsRes.data.content);
      if (locsRes.success && locsRes.data) setLocations(locsRes.data.content);
      if (suppRes.success && suppRes.data) setSuppliers(suppRes.data.content);
    }).catch((err) => {
      console.error('Error loading auxiliary catalogs:', err);
    });
  }, [canManageAssets, user?.id]);

  useEffect(() => {
    setAssets([]);
    setSelectedAssetDetail(null);
    setIsFormModalOpen(false);
    setIsDetailModalOpen(false);
    setCurrentPage(0);
  }, [user?.id]);

  // Fetch asset list
  const fetchAssets = useCallback(async () => {
    setIsLoading(true);
    setToastMessage(null);
    try {
      const personalFilters = {
        keyword: keyword.trim() ? keyword.trim() : undefined,
        page: currentPage,
        size: pageSize,
      };
      const res = await (myAssets ? assetApi.getMyAssets(personalFilters) : assetApi.getAssets({
        ...personalFilters,
        statusId,
        typeId,
        modelId,
        departmentId,
        locationId,
      }));

      if (res.success && res.data) {
        setAssets(res.data.content);
        setTotalPages(res.data.totalPages);
        setTotalElements(res.data.totalElements);
      }
    } catch (err: any) {
      setToastMessage({ type: 'error', text: err.message || t('assets:toasts.loadListError') });
    } finally {
      setIsLoading(false);
    }
  }, [keyword, statusId, typeId, modelId, departmentId, locationId, currentPage, pageSize, user?.id, myAssets, t]);

  useEffect(() => {
    fetchAssets();
  }, [fetchAssets]);

  // Handlers
  const handleResetFilters = () => {
    setKeyword('');
    setStatusId(undefined);
    setTypeId(undefined);
    setModelId(undefined);
    setDepartmentId(undefined);
    setLocationId(undefined);
    setCurrentPage(0);
  };

  const handleOpenCreate = () => {
    setSelectedAssetDetail(null);
    setIsFormModalOpen(true);
  };

  const handleOpenDetail = async (assetId: number) => {
    try {
      const res = await assetApi.getAssetById(assetId);
      if (res.success && res.data) {
        setSelectedAssetDetail(res.data);
        setIsDetailModalOpen(true);
      }
    } catch (err: any) {
      setToastMessage({ type: 'error', text: err.message || t('assets:toasts.loadDetailError') });
    }
  };

  const handleOpenEdit = async (asset: Asset | AssetDetail) => {
    try {
      const res = await assetApi.getAssetById(asset.assetId);
      if (res.success && res.data) {
        setSelectedAssetDetail(res.data);
        setIsFormModalOpen(true);
      }
    } catch (err: any) {
      setToastMessage({ type: 'error', text: err.message || t('assets:toasts.loadDetailError') });
    }
  };

  const handleFormSubmit = async (payload: any) => {
    setIsSaving(true);
    try {
      if (selectedAssetDetail) {
        // Edit mode
        await assetApi.updateAsset(selectedAssetDetail.assetId, payload);
        setToastMessage({ type: 'success', text: t('assets:toasts.updateSuccess') });
      } else {
        // Create mode
        await assetApi.createAsset(payload);
        setToastMessage({ type: 'success', text: t('assets:toasts.createSuccess') });
      }
      setIsFormModalOpen(false);
      fetchAssets();
    } finally {
      setIsSaving(false);
    }
  };

  const getStatusBadge = (code?: string, name?: string) => {
    let badgeClass = 'badge-active';
    if (code === 'IN_USE') badgeClass = 'badge-info';
    else if (code === 'IN_REPAIR') badgeClass = 'badge-warning';
    else if (code === 'DAMAGED' || code === 'RETIRED') badgeClass = 'badge-inactive';

    return <span className={`badge ${badgeClass}`}>{code ? t(`common:status.${code}`, {defaultValue: name || code}) : '—'}</span>;
  };

  // Stats calculation
  const inStockCount = assets.filter((a) => a.statusCode === 'IN_STOCK').length;
  const inUseCount = assets.filter((a) => a.statusCode === 'IN_USE').length;
  const otherCount = assets.filter((a) => a.statusCode !== 'IN_STOCK' && a.statusCode !== 'IN_USE').length;

  return (
    <div>
      {/* Toast alert */}
      {toastMessage && (
        <div
          style={{
            position: 'fixed',
            top: '20px',
            right: '20px',
            zIndex: 9999,
            padding: '12px 20px',
            borderRadius: '8px',
            backgroundColor: toastMessage.type === 'success' ? '#10b981' : '#ef4444',
            color: '#ffffff',
            fontWeight: 500,
            boxShadow: 'var(--shadow-lg)',
            display: 'flex',
            alignItems: 'center',
            gap: '8px',
          }}
        >
          <span>{toastMessage.type === 'success' ? '✅' : '❌'}</span>
          <span>{toastMessage.text}</span>
          <button
            onClick={() => setToastMessage(null)}
            style={{ background: 'transparent', border: 'none', color: '#fff', cursor: 'pointer', marginLeft: '12px', fontSize: '14px' }}
          >
            ✕
          </button>
        </div>
      )}

      {/* Page Header */}
      <div className="page-header" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
        <div>
          <h1 className="page-title">💻 {t(myAssets ? 'assets:myAssetsTitle' : 'assets:title')}</h1>
          {!myAssets && <p className="page-subtitle">
            {t('assets:subtitle', 'Theo dõi danh sách thiết bị, mã Asset Tag, Serial Number, cấu hình Default/Actual và trạng thái sử dụng')}
          </p>}
        </div>

        {canManageAssets && <div style={{ display: 'flex', gap: '10px' }}>
          <button
            type="button"
            className="btn btn-secondary"
            onClick={() => setIsImportModalOpen(true)}
            style={{ display: 'flex', alignItems: 'center', gap: '8px', fontWeight: 600 }}
          >
            <span>📥</span>
            <span>{t('assets:actions.importExcel', 'Import Excel')}</span>
          </button>

          <button type="button" className="btn btn-primary" onClick={handleOpenCreate} style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
            <span>➕</span>
            <span>{t('assets:actions.addAsset', 'Thêm tài sản')}</span>
          </button>
        </div>}
      </div>

      {/* Quick Stats Metric Cards */}
      {!myAssets && <div
        style={{
          display: 'grid',
          gridTemplateColumns: 'repeat(4, 1fr)',
          gap: '16px',
          marginBottom: '24px',
        }}
      >
        <div className="card" style={{ padding: '16px', borderLeft: '4px solid var(--primary)' }}>
          <div style={{ fontSize: '12px', fontWeight: 600, color: 'var(--text-muted)' }}>{t('common:labels.all', 'TỔNG SỐ TÀI SẢN').toUpperCase()}</div>
          <div style={{ fontSize: '24px', fontWeight: 700, color: 'var(--text-main)', marginTop: '4px' }}>
            {totalElements}
          </div>
        </div>

        <div className="card" style={{ padding: '16px', borderLeft: '4px solid #10b981' }}>
          <div style={{ fontSize: '12px', fontWeight: 600, color: 'var(--text-muted)' }}>{t('common:status.IN_STOCK', 'ĐANG LƯU KHO')} (IN STOCK)</div>
          <div style={{ fontSize: '24px', fontWeight: 700, color: '#10b981', marginTop: '4px' }}>
            {inStockCount} <span style={{ fontSize: '12px', color: 'var(--text-muted)', fontWeight: 400 }}>({t('common:pagination.page', 'trang này')})</span>
          </div>
        </div>

        <div className="card" style={{ padding: '16px', borderLeft: '4px solid #3b82f6' }}>
          <div style={{ fontSize: '12px', fontWeight: 600, color: 'var(--text-muted)' }}>{t('common:status.IN_USE', 'ĐANG SỬ DỤNG')} (IN USE)</div>
          <div style={{ fontSize: '24px', fontWeight: 700, color: '#3b82f6', marginTop: '4px' }}>
            {inUseCount} <span style={{ fontSize: '12px', color: 'var(--text-muted)', fontWeight: 400 }}>({t('common:pagination.page', 'trang này')})</span>
          </div>
        </div>

        <div className="card" style={{ padding: '16px', borderLeft: '4px solid #f59e0b' }}>
          <div style={{ fontSize: '12px', fontWeight: 600, color: 'var(--text-muted)' }}>{t('assets:list.otherStatuses')}</div>
          <div style={{ fontSize: '24px', fontWeight: 700, color: '#f59e0b', marginTop: '4px' }}>
            {otherCount} <span style={{ fontSize: '12px', color: 'var(--text-muted)', fontWeight: 400 }}>({t('common:pagination.page', 'trang này')})</span>
          </div>
        </div>
      </div>}

      {/* Filter Bar */}
      <AssetFilterBar
        showCatalogFilters={!myAssets}
        keyword={keyword}
        onKeywordChange={(val) => { setKeyword(val); setCurrentPage(0); }}
        statusId={statusId}
        onStatusChange={(val) => { setStatusId(val); setCurrentPage(0); }}
        typeId={typeId}
        onTypeChange={(val) => { setTypeId(val); setCurrentPage(0); }}
        modelId={modelId}
        onModelChange={(val) => { setModelId(val); setCurrentPage(0); }}
        departmentId={departmentId}
        onDepartmentChange={(val) => { setDepartmentId(val); setCurrentPage(0); }}
        locationId={locationId}
        onLocationChange={(val) => { setLocationId(val); setCurrentPage(0); }}
        onReset={handleResetFilters}
        statuses={statuses}
        types={types}
        models={models}
        departments={departments}
        locations={locations}
      />

      {/* Data Table */}
      <div className="card" style={{ padding: '0', overflow: 'hidden' }}>
        <div className="table-container">
          <table className="data-table">
            <thead>
              <tr>
                <th style={{ width: '130px' }}>{t('assets:fields.assetTag', 'Asset Tag')}</th>
                <th>{t('assets:fields.name', 'Tên thiết bị')}</th>
                <th style={{ width: '140px' }}>{t('assets:fields.serialNumber', 'Số Serial')}</th>
                <th style={{ width: '160px' }}>{t('assets:fields.type', 'Loại & Model')}</th>
                <th>{t('assets:fields.specs', 'Cấu hình hiệu lực (Effective)')}</th>
                <th style={{ width: '120px' }}>{t('assets:fields.status', 'Trạng thái')}</th>
                <th style={{ width: '160px' }}>{t('assets:fields.assignedTo', 'Người dùng / Vị trí')}</th>
                <th style={{ width: '140px', textAlign: 'center' }}>{t('common:labels.action', 'Thao tác')}</th>
              </tr>
            </thead>
            <tbody>
              {isLoading ? (
                <tr>
                  <td colSpan={8} style={{ textAlign: 'center', padding: '40px', color: 'var(--text-muted)' }}>
                    ⏳ {t('common:labels.loading', 'Đang tải dữ liệu tài sản...')}
                  </td>
                </tr>
              ) : assets.length === 0 ? (
                <tr>
                  <td colSpan={8} style={{ textAlign: 'center', padding: '40px', color: 'var(--text-muted)' }}>
                    🔍 {t('common:labels.noData', 'Không tìm thấy tài sản phần cứng nào phù hợp.')}
                  </td>
                </tr>
              ) : (
                assets.map((asset) => (
                  <tr key={asset.assetId}>
                    {/* Asset Tag */}
                    <td>
                      <span
                        onClick={() => handleOpenDetail(asset.assetId)}
                        style={{
                          fontWeight: 700,
                          color: 'var(--primary)',
                          cursor: 'pointer',
                          textDecoration: 'underline',
                        }}
                        title={t('assets:modal.detailTitle')}
                      >
                        {asset.assetTag}
                      </span>
                    </td>

                    {/* Name */}
                    <td>
                      <div style={{ fontWeight: 600, color: 'var(--text-main)' }}>{asset.name}</div>
                      {asset.conditionName && (
                        <span style={{ fontSize: '11px', color: 'var(--text-muted)' }}>
                          {t('assets:fields.condition')}: {asset.conditionName}
                        </span>
                      )}
                    </td>

                    {/* Serial Number */}
                    <td style={{ fontFamily: 'monospace', fontSize: '12px', color: '#475569' }}>
                      {asset.serialNumber || '—'}
                    </td>

                    {/* Type & Model */}
                    <td>
                      <div style={{ fontSize: '13px', fontWeight: 500 }}>{asset.typeName || '—'}</div>
                      <div style={{ fontSize: '11px', color: 'var(--text-muted)' }}>
                        {asset.categoryCode==='LICENSE' ? t('assets:license.title') : <>{asset.modelBrand ? `${asset.modelBrand} ` : ''}{asset.modelName || t('assets:list.noModel')}</>}
                      </div>
                    </td>

                    {/* Effective Specs summary */}
                    <td style={{ fontSize: '12px' }}>
                      {asset.categoryCode==='LICENSE' ? t('assets:license.detailHint') : <>
                      <div>💻 {asset.effectiveCpu || '—'}</div>
                      <div style={{ color: 'var(--text-muted)' }}>
                        RAM: {asset.effectiveRam || '—'} | {t('assets:fields.storage')}: {asset.effectiveStorage || '—'}
                      </div></>}
                    </td>

                    {/* Status */}
                    <td>{getStatusBadge(asset.statusCode, asset.statusName)}</td>

                    {/* Assignment / Location */}
                    <td style={{ fontSize: '12px' }}>
                      {asset.assignedToFullName ? (
                        <div style={{ fontWeight: 600, color: 'var(--primary)' }}>
                          👤 {asset.assignedToFullName}
                        </div>
                      ) : (
                        <div style={{ color: 'var(--text-muted)', fontStyle: 'italic' }}>{t(asset.categoryCode==='LICENSE'?'assets:license.allocations':'assets:detail.unassigned')}</div>
                      )}
                      <div style={{ color: 'var(--text-muted)', fontSize: '11px' }}>
                        📍 {asset.locationName || asset.departmentName || '—'}
                      </div>
                    </td>

                    {/* Actions */}
                    <td style={{ textAlign: 'center' }}>
                      <div style={{ display: 'flex', gap: '6px', justifyContent: 'center' }}>
                        <button
                          type="button"
                          className="btn btn-secondary"
                          style={{ padding: '4px 8px', fontSize: '12px' }}
                          onClick={() => handleOpenDetail(asset.assetId)}
                          title={t('assets:modal.detailTitle')}
                        >
                          👁️
                        </button>
                        {canManageAssets && asset.statusCode !== 'PENDING_IMPORT' && (
                          <button
                            type="button"
                            className="btn btn-secondary"
                            style={{ padding: '4px 8px', fontSize: '12px' }}
                            onClick={() => handleOpenEdit(asset)}
                            title={t('assets:modal.editTitle')}
                          >
                            ✏️
                          </button>
                        )}
                      </div>
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>

        {/* Pagination Bar */}
        {totalPages > 1 && (
          <div className="pagination-container" style={{ padding: '16px', borderTop: '1px solid var(--border-color)', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <div style={{ fontSize: '13px', color: 'var(--text-muted)' }}>
              {t('assets:list.showing', { count: assets.length, total: totalElements })}
            </div>

            <div style={{ display: 'flex', gap: '6px' }}>
              <button
                type="button"
                className="btn btn-secondary"
                disabled={currentPage === 0 || isLoading}
                onClick={() => setCurrentPage((p) => Math.max(0, p - 1))}
              >
                ◀ {t('common:pagination.previous')}
              </button>

              {Array.from({ length: totalPages }, (_, i) => i).map((pageIdx) => {
                // Display limited page numbers around current page
                if (
                  pageIdx === 0 ||
                  pageIdx === totalPages - 1 ||
                  Math.abs(pageIdx - currentPage) <= 2
                ) {
                  return (
                    <button
                      key={pageIdx}
                      type="button"
                      className={`btn ${currentPage === pageIdx ? 'btn-primary' : 'btn-secondary'}`}
                      style={{ minWidth: '36px' }}
                      onClick={() => setCurrentPage(pageIdx)}
                    >
                      {pageIdx + 1}
                    </button>
                  );
                }
                if (
                  (pageIdx === 1 && currentPage > 3) ||
                  (pageIdx === totalPages - 2 && currentPage < totalPages - 4)
                ) {
                  return <span key={pageIdx} style={{ padding: '6px 4px', color: 'var(--text-muted)' }}>...</span>;
                }
                return null;
              })}

              <button
                type="button"
                className="btn btn-secondary"
                disabled={currentPage >= totalPages - 1 || isLoading}
                onClick={() => setCurrentPage((p) => Math.min(totalPages - 1, p + 1))}
              >
                {t('common:pagination.next')} ▶
              </button>
            </div>
          </div>
        )}
      </div>

      {/* Modals */}
      {canManageAssets && <AssetFormModal
        isOpen={isFormModalOpen}
        onClose={() => setIsFormModalOpen(false)}
        initialData={selectedAssetDetail}
        onSubmit={handleFormSubmit}
        isSaving={isSaving}
        types={types}
        statuses={statuses}
        conditions={conditions}
        models={models}
        departments={departments}
        locations={locations}
        suppliers={suppliers}
      />}

      <AssetDetailModal
        isOpen={isDetailModalOpen}
        onClose={() => setIsDetailModalOpen(false)}
        asset={selectedAssetDetail}
        onEdit={canManageAssets && selectedAssetDetail?.statusCode !== 'PENDING_IMPORT' ? (asset) => {
          setSelectedAssetDetail(asset);
          setIsFormModalOpen(true);
        } : undefined}
      />

      {canManageAssets && <AssetImportModal
        isOpen={isImportModalOpen}
        onClose={() => setIsImportModalOpen(false)}
        onImportSuccess={(count) => {
          setToastMessage({ type: 'success', text: t('assets:list.imported', {count}) });
          fetchAssets();
        }}
      />}
    </div>
  );
};
