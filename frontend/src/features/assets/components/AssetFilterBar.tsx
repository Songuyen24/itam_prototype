import React, { useId } from 'react';
import { useTranslation } from 'react-i18next';
import {
  Department,
  LocationItem,
  AssetTypeItem,
  AssetStatusItem,
  ModelItem,
} from '@/features/catalogs/types/catalog.types';

interface AssetFilterBarProps {
  showCatalogFilters?: boolean;
  keyword: string;
  onKeywordChange: (val: string) => void;
  statusId?: number;
  onStatusChange: (val?: number) => void;
  typeId?: number;
  onTypeChange: (val?: number) => void;
  modelId?: number;
  onModelChange: (val?: number) => void;
  departmentId?: number;
  onDepartmentChange: (val?: number) => void;
  locationId?: number;
  onLocationChange: (val?: number) => void;
  onReset: () => void;

  statuses: AssetStatusItem[];
  types: AssetTypeItem[];
  models: ModelItem[];
  departments: Department[];
  locations: LocationItem[];
}

export const AssetFilterBar: React.FC<AssetFilterBarProps> = ({
  showCatalogFilters = true,
  keyword,
  onKeywordChange,
  statusId,
  onStatusChange,
  typeId,
  onTypeChange,
  modelId,
  onModelChange,
  departmentId,
  onDepartmentChange,
  locationId,
  onLocationChange,
  onReset,
  statuses,
  types,
  models,
  departments,
  locations,
}) => {
  const { t } = useTranslation(['assets', 'common']);
  const id = useId();

  return (
    <div className="content-card asset-filters">
      {/* Row 1: Search input */}
      <div className="asset-search-row">
        <label className="form-label">
          {t('common:buttons.search')}
          <input
            type="text"
            className="form-input"
            placeholder={t('assets:filters.searchPlaceholder', 'Tìm kiếm theo Asset Tag, Serial Number, Tên thiết bị...')}
            value={keyword}
            onChange={(e) => onKeywordChange(e.target.value)}
          />
        </label>
        <button
          type="button"
          className="btn btn-secondary"
          onClick={onReset}
          title={t('assets:filters.resetFilter', 'Đặt lại tất cả bộ lọc')}
        >
          🔄 {t('common:buttons.reset', 'Đặt lại')}
        </button>
      </div>

      {/* Row 2: Filter selects */}
      {showCatalogFilters && <div className="asset-filter-grid">
        {/* Status filter */}
        <div>
          <label className="form-label" htmlFor={`${id}-status`}>
            {t('assets:fields.status', 'Trạng thái')}
          </label>
          <select
            className="form-select"
            id={`${id}-status`}
            value={statusId ?? ''}
            onChange={(e) => onStatusChange(e.target.value ? Number(e.target.value) : undefined)}
          >
            <option value="">{t('assets:filters.allStatuses', 'Tất cả trạng thái')}</option>
            {statuses.map((s) => (
              <option key={s.statusId} value={s.statusId}>
                {t(`common:status.${s.code}`, {defaultValue:s.name})}
              </option>
            ))}
          </select>
        </div>

        {/* Type filter */}
        <div>
          <label className="form-label" htmlFor={`${id}-type`}>
            {t('assets:fields.type', 'Loại thiết bị')}
          </label>
          <select
            className="form-select"
            id={`${id}-type`}
            value={typeId ?? ''}
            onChange={(e) => onTypeChange(e.target.value ? Number(e.target.value) : undefined)}
          >
            <option value="">{t('assets:filters.allTypes', 'Tất cả loại')}</option>
            {types.map((tItem) => (
              <option key={tItem.typeId} value={tItem.typeId}>
                {tItem.name}
              </option>
            ))}
          </select>
        </div>

        {/* Model filter */}
        <div>
          <label className="form-label" htmlFor={`${id}-model`}>
            {t('assets:fields.model', 'Model')}
          </label>
          <select
            className="form-select"
            id={`${id}-model`}
            value={modelId ?? ''}
            onChange={(e) => onModelChange(e.target.value ? Number(e.target.value) : undefined)}
          >
            <option value="">{t('assets:filters.allModels', 'Tất cả model')}</option>
            {models.map((m) => (
              <option key={m.modelId} value={m.modelId}>
                {m.brand} - {m.name}
              </option>
            ))}
          </select>
        </div>

        {/* Department filter */}
        <div>
          <label className="form-label" htmlFor={`${id}-department`}>
            {t('assets:fields.department', 'Phòng ban')}
          </label>
          <select
            className="form-select"
            id={`${id}-department`}
            value={departmentId ?? ''}
            onChange={(e) => onDepartmentChange(e.target.value ? Number(e.target.value) : undefined)}
          >
            <option value="">{t('assets:filters.allDepartments', 'Tất cả phòng ban')}</option>
            {departments.map((d) => (
              <option key={d.departmentId} value={d.departmentId}>
                {d.name}
              </option>
            ))}
          </select>
        </div>

        {/* Location filter */}
        <div>
          <label className="form-label" htmlFor={`${id}-location`}>
            {t('assets:fields.location', 'Vị trí')}
          </label>
          <select
            className="form-select"
            id={`${id}-location`}
            value={locationId ?? ''}
            onChange={(e) => onLocationChange(e.target.value ? Number(e.target.value) : undefined)}
          >
            <option value="">{t('assets:filters.allLocations', 'Tất cả vị trí')}</option>
            {locations.map((l) => (
              <option key={l.locationId} value={l.locationId}>
                {l.name}
              </option>
            ))}
          </select>
        </div>
      </div>}
    </div>
  );
};
