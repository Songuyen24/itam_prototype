import React, { useState, useEffect, useCallback } from 'react';
import { useTranslation } from 'react-i18next';
import {
  CatalogTabKey,
  Supplier,
  SupplierContact,
  AssetCategoryItem,
  AssetTypeItem,
  ModelItem,
} from '../types/catalog.types';
import {
  departmentApi,
  locationApi,
  supplierApi,
  categoryApi,
  assetTypeApi,
  assetStatusApi,
  assetConditionApi,
  modelApi,
  softwareCatalogApi,
  licenseAssignmentTypeApi,
  licenseTermTypeApi,
} from '../api/catalogApi';
import { CatalogTable, TableColumn } from '../components/CatalogTable';
import { CatalogModal, CatalogFormData } from '../components/CatalogModal';
import { ModelModal } from '../components/ModelModal';
import { SupplierModal } from '../components/SupplierModal';
import { ConfirmDeleteModal } from '../components/ConfirmDeleteModal';
import { ApiError } from '@/shared/api/httpClient';
import { useAuth } from '@/features/auth/contexts/AuthContext';

const TABS: { key: CatalogTabKey; label: string }[] = [
  { key: 'departments', label: 'Phòng ban' },
  { key: 'locations', label: 'Vị trí' },
  { key: 'suppliers', label: 'Nhà cung cấp' },
  { key: 'categories', label: 'Nhóm tài sản' },
  { key: 'types', label: 'Loại tài sản' },
  { key: 'statuses', label: 'Trạng thái tài sản' },
  { key: 'conditions', label: 'Tình trạng tài sản' },
  { key: 'models', label: 'Model thiết bị' },
  { key: 'software', label: 'Phần mềm' },
  { key: 'license-assignments', label: 'Loại gán License' },
  { key: 'license-terms', label: 'Thời hạn License' },
];

export const CatalogsPage: React.FC = () => {
  const { t } = useTranslation(['catalogs', 'common']);
  const { user } = useAuth();
  const canManageCatalogs = user?.role === 'ADMIN' || user?.role === 'IT_STAFF';
  const [activeTab, setActiveTab] = useState<CatalogTabKey>('departments');
  const [search, setSearch] = useState('');
  const [statusFilter, setStatusFilter] = useState<'ALL' | 'ACTIVE' | 'INACTIVE'>('ALL');
  const [currentPage, setCurrentPage] = useState(0);
  const [totalPages, setTotalPages] = useState(1);
  const [totalElements, setTotalElements] = useState(0);
  const [pageSize] = useState(20);

  // Data states
  const [items, setItems] = useState<any[]>([]);
  const [isLoading, setIsLoading] = useState(false);
  const [toastMessage, setToastMessage] = useState<{ type: 'success' | 'error'; text: string } | null>(null);

  // Reference data for dropdowns
  const [categories, setCategories] = useState<AssetCategoryItem[]>([]);
  const [types, setTypes] = useState<AssetTypeItem[]>([]);

  // Modal states
  const [isGenericModalOpen, setIsGenericModalOpen] = useState(false);
  const [isModelModalOpen, setIsModelModalOpen] = useState(false);
  const [isSupplierModalOpen, setIsSupplierModalOpen] = useState(false);
  const [isDeleteModalOpen, setIsDeleteModalOpen] = useState(false);

  const [selectedItem, setSelectedItem] = useState<any | null>(null);
  const [modalErrorMessage, setModalErrorMessage] = useState<string | null>(null);
  const [isSaving, setIsSaving] = useState(false);
  const [isDeleting, setIsDeleting] = useState(false);

  // Load auxiliary data (categories & types)
  useEffect(() => {
    categoryApi.getAll(0, 100).then((res) => {
      if (res.success && res.data) setCategories(res.data.content);
    }).catch(() => {});

    assetTypeApi.getAll(undefined, true, 0, 100).then((res) => {
      if (res.success && res.data) setTypes(res.data.content);
    }).catch(() => {});
  }, []);

  // Fetch items for current tab
  const fetchTabItems = useCallback(async () => {
    setIsLoading(true);
    setToastMessage(null);

    const activeParam =
      statusFilter === 'ACTIVE' ? true : statusFilter === 'INACTIVE' ? false : undefined;
    const searchParam = search.trim() ? search.trim() : undefined;

    try {
      let res: any;
      switch (activeTab) {
        case 'departments':
          res = await departmentApi.getAll(searchParam, activeParam, currentPage, pageSize);
          break;
        case 'locations':
          res = await locationApi.getAll(searchParam, activeParam, currentPage, pageSize);
          break;
        case 'suppliers':
          res = await supplierApi.getAll(searchParam, activeParam, currentPage, pageSize);
          break;
        case 'categories':
          res = await categoryApi.getAll(currentPage, pageSize);
          break;
        case 'types':
          res = await assetTypeApi.getAll(searchParam, activeParam, currentPage, pageSize);
          break;
        case 'statuses':
          res = await assetStatusApi.getAll(currentPage, pageSize);
          break;
        case 'conditions':
          res = await assetConditionApi.getAll(currentPage, pageSize);
          break;
        case 'models':
          res = await modelApi.getAll(searchParam, activeParam, currentPage, pageSize);
          break;
        case 'software':
          res = await softwareCatalogApi.getAll(searchParam, activeParam, currentPage, pageSize);
          break;
        case 'license-assignments':
          res = await licenseAssignmentTypeApi.getAll(currentPage, pageSize);
          break;
        case 'license-terms':
          res = await licenseTermTypeApi.getAll(currentPage, pageSize);
          break;
      }

      if (res && res.success && res.data) {
        setItems(res.data.content || []);
        setTotalPages(res.data.totalPages || 1);
        setTotalElements(res.data.totalElements || 0);
      }
    } catch (err: any) {
      setToastMessage({
        type: 'error',
        text: err.message || t('catalogs:feedback.loadError'),
      });
      setItems([]);
    } finally {
      setIsLoading(false);
    }
  }, [activeTab, search, statusFilter, currentPage, pageSize, user?.id]);

  useEffect(() => {
    fetchTabItems();
  }, [fetchTabItems]);

  const handleTabChange = (tab: CatalogTabKey) => {
    setActiveTab(tab);
    setSearch('');
    setStatusFilter('ALL');
    setCurrentPage(0);
  };

  // ==========================================
  // ADD / EDIT HANDLERS
  // ==========================================
  const handleOpenAdd = () => {
    setSelectedItem(null);
    setModalErrorMessage(null);

    if (activeTab === 'models') {
      setIsModelModalOpen(true);
    } else if (activeTab === 'suppliers') {
      setIsSupplierModalOpen(true);
    } else {
      setIsGenericModalOpen(true);
    }
  };

  const handleOpenEdit = (item: any) => {
    setSelectedItem(item);
    setModalErrorMessage(null);

    if (activeTab === 'models') {
      setIsModelModalOpen(true);
    } else if (activeTab === 'suppliers') {
      setIsSupplierModalOpen(true);
    } else {
      setIsGenericModalOpen(true);
    }
  };

  // Save Generic Modal
  const handleSaveGeneric = async (data: CatalogFormData) => {
    setIsSaving(true);
    setModalErrorMessage(null);

    try {
      if (selectedItem) {
        // UPDATE
        const id = data.id!;
        switch (activeTab) {
          case 'departments':
            await departmentApi.update(id, { code: data.code!, name: data.name, isActive: data.isActive });
            break;
          case 'locations':
            await locationApi.update(id, { code: data.code!, name: data.name, address: data.address, isActive: data.isActive });
            break;
          case 'categories':
            await categoryApi.update(id, { code: data.code!, name: data.name, isActive: data.isActive });
            break;
          case 'types':
            await assetTypeApi.update(id, { code: data.code!, categoryId: data.categoryId!, name: data.name, isActive: data.isActive });
            break;
          case 'statuses':
            await assetStatusApi.update(id, { code: data.code!, name: data.name, isActive: data.isActive });
            break;
          case 'conditions':
            await assetConditionApi.update(id, { code: data.code!, name: data.name, isActive: data.isActive });
            break;
          case 'software':
            await softwareCatalogApi.update(id, { name: data.name, manufacturer: data.manufacturer!, version: data.version, isActive: data.isActive });
            break;
          case 'license-assignments':
            await licenseAssignmentTypeApi.update(id, { code: data.code!, name: data.name, active: data.isActive });
            break;
          case 'license-terms':
            await licenseTermTypeApi.update(id, { code: data.code!, name: data.name, active: data.isActive });
            break;
        }
        setToastMessage({ type: 'success', text: t('catalogs:feedback.updated') });
      } else {
        // CREATE
        switch (activeTab) {
          case 'departments':
            await departmentApi.create({ code: data.code!, name: data.name, isActive: data.isActive });
            break;
          case 'locations':
            await locationApi.create({ code: data.code!, name: data.name, address: data.address, isActive: data.isActive });
            break;
          case 'categories':
            await categoryApi.create({ code: data.code!, name: data.name, isActive: data.isActive });
            break;
          case 'types':
            await assetTypeApi.create({ code: data.code!, categoryId: data.categoryId!, name: data.name, isActive: data.isActive });
            break;
          case 'statuses':
            await assetStatusApi.create({ code: data.code!, name: data.name, isActive: data.isActive });
            break;
          case 'conditions':
            await assetConditionApi.create({ code: data.code!, name: data.name, isActive: data.isActive });
            break;
          case 'software':
            await softwareCatalogApi.create({ name: data.name, manufacturer: data.manufacturer!, version: data.version, isActive: data.isActive });
            break;
          case 'license-assignments':
            await licenseAssignmentTypeApi.create({ code: data.code!, name: data.name, active: data.isActive });
            break;
          case 'license-terms':
            await licenseTermTypeApi.create({ code: data.code!, name: data.name, active: data.isActive });
            break;
        }
        setToastMessage({ type: 'success', text: t('catalogs:feedback.created') });
      }

      setIsGenericModalOpen(false);
      fetchTabItems();
    } catch (err: any) {
      setModalErrorMessage(err.message || t('catalogs:feedback.saveError'));
    } finally {
      setIsSaving(false);
    }
  };

  // Save Model Modal
  const handleSaveModel = async (data: any) => {
    setIsSaving(true);
    setModalErrorMessage(null);
    try {
      if (selectedItem) {
        await modelApi.update(selectedItem.modelId, data);
        setToastMessage({ type: 'success', text: t('catalogs:feedback.modelUpdated') });
      } else {
        await modelApi.create(data);
        setToastMessage({ type: 'success', text: t('catalogs:feedback.modelCreated') });
      }
      setIsModelModalOpen(false);
      fetchTabItems();
    } catch (err: any) {
      setModalErrorMessage(err.message || t('catalogs:feedback.modelError'));
    } finally {
      setIsSaving(false);
    }
  };

  // Save Supplier Modal
  const handleSaveSupplier = async (data: Partial<Supplier>, contacts: SupplierContact[]) => {
    setIsSaving(true);
    setModalErrorMessage(null);
    try {
      let savedSupplier: Supplier;
      if (selectedItem) {
        const res = await supplierApi.update(selectedItem.supplierId, data);
        savedSupplier = res.data;
        // Sync contacts if needed
        setToastMessage({ type: 'success', text: t('catalogs:feedback.supplierUpdated') });
      } else {
        const res = await supplierApi.create(data);
        savedSupplier = res.data;
        // Add attached contacts
        if (contacts.length > 0 && savedSupplier.supplierId) {
          for (const c of contacts) {
            await supplierApi.addContact(savedSupplier.supplierId, c);
          }
        }
        setToastMessage({ type: 'success', text: t('catalogs:feedback.supplierCreated') });
      }
      setIsSupplierModalOpen(false);
      fetchTabItems();
    } catch (err: any) {
      setModalErrorMessage(err.message || t('catalogs:feedback.supplierError'));
    } finally {
      setIsSaving(false);
    }
  };

  // Toggle Active Handler
  const handleToggleActive = async (item: any) => {
    try {
      const currentActive = item.isActive ?? item.active ?? true;
      const targetActive = !currentActive;

      switch (activeTab) {
        case 'departments':
          await departmentApi.toggleActive(item.departmentId, targetActive);
          break;
        case 'locations':
          await locationApi.toggleActive(item.locationId, targetActive);
          break;
        case 'suppliers':
          await supplierApi.toggleActive(item.supplierId, targetActive);
          break;
        case 'categories':
          await categoryApi.toggleActive(item.categoryId, targetActive);
          break;
        case 'types':
          await assetTypeApi.toggleActive(item.typeId, targetActive);
          break;
        case 'statuses':
          await assetStatusApi.toggleActive(item.statusId, targetActive);
          break;
        case 'conditions':
          await assetConditionApi.toggleActive(item.conditionId, targetActive);
          break;
        case 'models':
          await modelApi.toggleActive(item.modelId, targetActive);
          break;
        case 'software':
          await softwareCatalogApi.toggleActive(item.softwareCatalogId, targetActive);
          break;
        case 'license-assignments':
          await licenseAssignmentTypeApi.toggleActive(item.id, targetActive);
          break;
        case 'license-terms':
          await licenseTermTypeApi.toggleActive(item.id, targetActive);
          break;
      }

      setToastMessage({
        type: 'success',
        text: t('catalogs:feedback.statusChanged'),
      });
      fetchTabItems();
    } catch (err: any) {
      setToastMessage({
        type: 'error',
        text: err.message || t('catalogs:feedback.statusError'),
      });
    }
  };

  // Delete Handlers
  const handleOpenDelete = (item: any) => {
    setSelectedItem(item);
    setModalErrorMessage(null);
    setIsDeleteModalOpen(true);
  };

  const handleConfirmDelete = async () => {
    if (!selectedItem) return;
    setIsDeleting(true);
    setModalErrorMessage(null);

    try {
      switch (activeTab) {
        case 'departments':
          await departmentApi.delete(selectedItem.departmentId);
          break;
        case 'locations':
          await locationApi.delete(selectedItem.locationId);
          break;
        case 'suppliers':
          await supplierApi.delete(selectedItem.supplierId);
          break;
        case 'categories':
          await categoryApi.delete(selectedItem.categoryId);
          break;
        case 'types':
          await assetTypeApi.delete(selectedItem.typeId);
          break;
        case 'statuses':
          await assetStatusApi.delete(selectedItem.statusId);
          break;
        case 'conditions':
          await assetConditionApi.delete(selectedItem.conditionId);
          break;
        case 'models':
          await modelApi.delete(selectedItem.modelId);
          break;
        case 'software':
          await softwareCatalogApi.delete(selectedItem.softwareCatalogId);
          break;
        case 'license-assignments':
          await licenseAssignmentTypeApi.delete(selectedItem.id);
          break;
        case 'license-terms':
          await licenseTermTypeApi.delete(selectedItem.id);
          break;
      }

      setIsDeleteModalOpen(false);
      setToastMessage({ type: 'success', text: t('catalogs:feedback.deleted') });
      fetchTabItems();
    } catch (err: any) {
      if (err instanceof ApiError && err.code === 'CATALOG_IN_USE') {
        setModalErrorMessage(
          t('catalogs:feedback.inUse')
        );
      } else {
        setModalErrorMessage(err.message || t('catalogs:feedback.deleteError'));
      }
    } finally {
      setIsDeleting(false);
    }
  };

  // Define Table Columns dynamically per tab
  const getColumns = (): TableColumn<any>[] => {
    const renderActiveBadge = (row: any) => {
      const active = row.isActive ?? row.active ?? true;
      return (
        <span className={`badge ${active ? 'badge-active' : 'badge-inactive'}`}>
          {active ? t('catalogs:columns.active', 'Hoạt động') : t('catalogs:columns.inactive', 'Tạm khóa')}
        </span>
      );
    };

    switch (activeTab) {
      case 'departments':
        return [
          { header: t('catalogs:columns.departmentCode', 'Mã phòng ban'), accessor: 'code', width: '20%' },
          { header: t('catalogs:columns.departmentName', 'Tên phòng ban'), accessor: 'name', width: '50%' },
          { header: t('catalogs:columns.status', 'Trạng thái'), render: renderActiveBadge, width: '15%' },
        ];
      case 'locations':
        return [
          { header: t('catalogs:columns.locationCode', 'Mã vị trí'), accessor: 'code', width: '20%' },
          { header: t('catalogs:columns.locationName', 'Tên vị trí'), accessor: 'name', width: '30%' },
          { header: t('catalogs:columns.address', 'Địa chỉ'), accessor: 'address', width: '35%' },
          { header: t('catalogs:columns.status', 'Trạng thái'), render: renderActiveBadge, width: '15%' },
        ];
      case 'suppliers':
        return [
          { header: t('catalogs:columns.supplierCode', 'Mã NCC'), accessor: 'code', width: '15%' },
          { header: t('catalogs:columns.supplierName', 'Tên nhà cung cấp'), accessor: 'name', width: '30%' },
          { header: t('catalogs:columns.phone', 'Điện thoại'), accessor: 'phone', width: '15%' },
          { header: t('catalogs:columns.email', 'Email'), accessor: 'email', width: '20%' },
          { header: t('catalogs:columns.status', 'Trạng thái'), render: renderActiveBadge, width: '10%' },
        ];
      case 'categories':
        return [
          { header: t('catalogs:columns.categoryCode', 'Mã nhóm'), accessor: 'code', width: '25%' },
          { header: t('catalogs:columns.categoryName', 'Tên nhóm tài sản'), accessor: 'name', width: '50%' },
          { header: t('catalogs:columns.status', 'Trạng thái'), render: renderActiveBadge, width: '15%' },
        ];
      case 'types':
        return [
          { header: t('catalogs:columns.typeCode', 'Mã loại'), accessor: 'code', width: '20%' },
          { header: t('catalogs:columns.typeName', 'Tên loại tài sản'), accessor: 'name', width: '30%' },
          {
            header: t('catalogs:columns.category', 'Nhóm tài sản'),
            render: (row: AssetTypeItem) => (
              <span className="badge badge-blue">{row.categoryName || row.categoryId}</span>
            ),
            width: '25%',
          },
          { header: t('catalogs:columns.status', 'Trạng thái'), render: renderActiveBadge, width: '15%' },
        ];
      case 'statuses':
        return [
          { header: t('catalogs:columns.statusCode', 'Mã trạng thái'), accessor: 'code', width: '30%' },
          { header: t('catalogs:columns.statusName', 'Tên trạng thái'), accessor: 'name', width: '45%' },
          { header: t('catalogs:columns.status', 'Trạng thái'), render: renderActiveBadge, width: '15%' },
        ];
      case 'conditions':
        return [
          { header: t('catalogs:columns.conditionCode', 'Mã tình trạng'), accessor: 'code', width: '30%' },
          { header: t('catalogs:columns.conditionName', 'Tên tình trạng'), accessor: 'name', width: '45%' },
          { header: t('catalogs:columns.status', 'Trạng thái'), render: renderActiveBadge, width: '15%' },
        ];
      case 'models':
        return [
          { header: t('catalogs:columns.model', 'Model'), accessor: 'name', width: '25%' },
          { header: t('catalogs:columns.brand', 'Thương hiệu'), accessor: 'brand', width: '20%' },
          {
            header: t('catalogs:columns.type', 'Loại tài sản'),
            render: (row: ModelItem) => (
              <span className="badge badge-blue">{row.typeName || row.typeId}</span>
            ),
            width: '20%',
          },
          {
            header: t('catalogs:columns.defaultSpecs', 'Cấu hình mặc định'),
            render: (row: ModelItem) => (
              <span style={{ fontSize: '12px', color: 'var(--text-muted)' }}>
                {[row.defaultCpu, row.defaultRam, row.defaultStorage].filter(Boolean).join(' | ') || '-'}
              </span>
            ),
            width: '25%',
          },
          { header: t('catalogs:columns.status', 'Trạng thái'), render: renderActiveBadge, width: '10%' },
        ];
      case 'software':
        return [
          { header: t('catalogs:columns.softwareName', 'Tên phần mềm'), accessor: 'name', width: '35%' },
          { header: t('catalogs:columns.manufacturer', 'Nhà sản xuất'), accessor: 'manufacturer', width: '30%' },
          { header: t('catalogs:columns.version', 'Phiên bản'), accessor: 'version', width: '15%' },
          { header: t('catalogs:columns.status', 'Trạng thái'), render: renderActiveBadge, width: '10%' },
        ];
      case 'license-assignments':
      case 'license-terms':
        return [
          { header: t('catalogs:columns.code', 'Mã'), accessor: 'code', width: '25%' },
          { header: t('catalogs:columns.classificationName', 'Tên phân loại'), accessor: 'name', width: '50%' },
          { header: t('catalogs:columns.status', 'Trạng thái'), render: renderActiveBadge, width: '15%' },
        ];
    }
  };

  const getKeyExtractor = (row: any) => {
    return (
      row.departmentId ??
      row.locationId ??
      row.supplierId ??
      row.categoryId ??
      row.typeId ??
      row.statusId ??
      row.conditionId ??
      row.modelId ??
      row.softwareCatalogId ??
      row.id ??
      row.code
    );
  };

  const getTabLabel = (key: CatalogTabKey) => {
    switch (key) {
      case 'departments': return t('catalogs:tabs.departments', 'Phòng ban');
      case 'locations': return t('catalogs:tabs.locations', 'Vị trí');
      case 'suppliers': return t('catalogs:tabs.suppliers', 'Nhà cung cấp');
      case 'categories': return t('catalogs:tabs.categories', 'Nhóm tài sản');
      case 'types': return t('catalogs:tabs.types', 'Loại tài sản');
      case 'statuses': return t('catalogs:tabs.statuses', 'Trạng thái tài sản');
      case 'conditions': return t('catalogs:tabs.conditions', 'Tình trạng tài sản');
      case 'models': return t('catalogs:tabs.models', 'Model thiết bị');
      case 'software': return t('catalogs:tabs.software', 'Phần mềm');
      case 'license-assignments': return t('catalogs:tabs.licenseAssignments', 'Loại gán License');
      case 'license-terms': return t('catalogs:tabs.licenseTerms', 'Thời hạn License');
      default: return '';
    }
  };

  const getActiveTabLabel = () => getTabLabel(activeTab);

  // Data mapping for generic modal
  const getInitialGenericData = (): CatalogFormData | null => {
    if (!selectedItem) return null;
    return {
      id:
        selectedItem.departmentId ??
        selectedItem.locationId ??
        selectedItem.categoryId ??
        selectedItem.typeId ??
        selectedItem.statusId ??
        selectedItem.conditionId ??
        selectedItem.softwareCatalogId ??
        selectedItem.id,
      code: selectedItem.code,
      name: selectedItem.name,
      address: selectedItem.address,
      categoryId: selectedItem.categoryId,
      manufacturer: selectedItem.manufacturer,
      version: selectedItem.version,
      isActive: selectedItem.isActive ?? selectedItem.active ?? true,
    };
  };

  return (
    <div>
      <div className="page-header" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <div>
          <h1 className="page-title">{t('catalogs:title', 'Quản lý Danh mục')}</h1>
          <p className="page-description">
            {t('catalogs:subtitle', 'Quản lý tất cả danh mục dùng chung cho tài sản, thiết bị, phần mềm và nhà cung cấp.')}
          </p>
        </div>
        {canManageCatalogs && (
          <button className="btn btn-primary" onClick={handleOpenAdd}>
            + {t('common:buttons.add', 'Thêm')} {getActiveTabLabel()}
          </button>
        )}
      </div>

      {toastMessage && (
        <div className={`alert-banner ${toastMessage.type === 'success' ? 'alert-success' : 'alert-danger'}`}>
          <span>{toastMessage.text}</span>
          <button className="alert-close-btn" onClick={() => setToastMessage(null)}>
            &times;
          </button>
        </div>
      )}

      {/* Tabs */}
      <div className="tabs-container">
        {TABS.map((tab) => (
          <button
            key={tab.key}
            className={`tab-button ${activeTab === tab.key ? 'active' : ''}`}
            onClick={() => handleTabChange(tab.key)}
          >
            {getTabLabel(tab.key)}
          </button>
        ))}
      </div>

      {/* Card with Filters and Table */}
      <div className="content-card">
        <div className="card-toolbar">
          <div className="filter-group">
            <div className="search-input-wrapper">
              <input
                type="text"
                className="search-input"
                placeholder={`${t('common:labels.search', 'Tìm kiếm')} ${getActiveTabLabel().toLowerCase()}...`}
                value={search}
                onChange={(e) => setSearch(e.target.value)}
              />
            </div>

            <select
              className="select-filter"
              value={statusFilter}
              onChange={(e) => setStatusFilter(e.target.value as any)}
            >
              <option value="ALL">{t('common:labels.all', 'Tất cả trạng thái')}</option>
              <option value="ACTIVE">{t('common:labels.active', 'Đang hoạt động')}</option>
              <option value="INACTIVE">{t('common:labels.inactive', 'Ngừng hoạt động')}</option>
            </select>
          </div>

          <div style={{ fontSize: '13px', color: 'var(--text-muted)' }}>
            {t('common:pagination.showing', 'Tổng số')}: <strong>{totalElements}</strong> {t('common:pagination.items', 'mục')}
          </div>
        </div>

        <CatalogTable
          columns={getColumns()}
          data={items}
          isLoading={isLoading}
          onEdit={canManageCatalogs ? handleOpenEdit : undefined}
          onDelete={canManageCatalogs ? handleOpenDelete : undefined}
          onToggleActive={canManageCatalogs ? handleToggleActive : undefined}
          keyExtractor={getKeyExtractor}
        />

        {/* Pagination */}
        <div className="pagination-wrapper">
          <div>
            Trang <strong>{currentPage + 1}</strong> / {totalPages || 1}
          </div>
          <div className="pagination-controls">
            <button
              className="btn btn-sm btn-secondary"
              disabled={currentPage === 0 || isLoading}
              onClick={() => setCurrentPage((prev) => Math.max(0, prev - 1))}
            >
              &larr; {t('common:pagination.previous')}
            </button>
            <button
              className="btn btn-sm btn-secondary"
              disabled={currentPage + 1 >= totalPages || isLoading}
              onClick={() => setCurrentPage((prev) => prev + 1)}
            >
              {t('common:pagination.next')} &rarr;
            </button>
          </div>
        </div>
      </div>

      {/* Modals */}
      <CatalogModal
        isOpen={isGenericModalOpen}
        title={`${selectedItem ? t('common:buttons.edit') : t('common:buttons.add')} ${getActiveTabLabel()}`}
        initialData={getInitialGenericData()}
        categories={categories}
        showCodeField={activeTab !== 'software'}
        codeDisabled={Boolean(selectedItem)}
        showAddressField={activeTab === 'locations'}
        showCategorySelect={activeTab === 'types'}
        showSoftwareFields={activeTab === 'software'}
        isSaving={isSaving}
        errorMessage={modalErrorMessage}
        onSave={handleSaveGeneric}
        onClose={() => setIsGenericModalOpen(false)}
      />

      <ModelModal
        isOpen={isModelModalOpen}
        title={`${selectedItem ? t('common:buttons.edit') : t('common:buttons.add')} ${t('catalogs:tabs.models')}`}
        initialData={selectedItem}
        assetTypes={types}
        isSaving={isSaving}
        errorMessage={modalErrorMessage}
        onSave={handleSaveModel}
        onClose={() => setIsModelModalOpen(false)}
      />

      <SupplierModal
        isOpen={isSupplierModalOpen}
        title={`${selectedItem ? t('common:buttons.edit') : t('common:buttons.add')} ${t('catalogs:tabs.suppliers')}`}
        initialData={selectedItem}
        isSaving={isSaving}
        errorMessage={modalErrorMessage}
        onSave={handleSaveSupplier}
        onClose={() => setIsSupplierModalOpen(false)}
      />

      <ConfirmDeleteModal
        isOpen={isDeleteModalOpen}
        itemName={selectedItem?.name || selectedItem?.code || ''}
        itemTypeLabel={getActiveTabLabel().toLowerCase()}
        errorMessage={modalErrorMessage}
        isDeleting={isDeleting}
        onConfirm={handleConfirmDelete}
        onClose={() => setIsDeleteModalOpen(false)}
      />
    </div>
  );
};
