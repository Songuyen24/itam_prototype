import React from 'react';
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

  return (
    <div
      style={{
        backgroundColor: '#ffffff',
        borderRadius: '8px',
        padding: '16px',
        boxShadow: 'var(--shadow-sm)',
        marginBottom: '20px',
        display: 'flex',
        flexDirection: 'column',
        gap: '12px',
      }}
    >
      {/* Row 1: Search input */}
      <div style={{ display: 'flex', gap: '12px', alignItems: 'center' }}>
        <div style={{ position: 'relative', flexGrow: 1 }}>
          <span
            style={{
              position: 'absolute',
              left: '12px',
              top: '50%',
              transform: 'translateY(-50%)',
              color: 'var(--text-muted)',
              fontSize: '14px',
            }}
          >
            🔍
          </span>
          <input
            type="text"
            className="form-control"
            style={{ paddingLeft: '36px' }}
            placeholder={t('assets:filters.searchPlaceholder', 'Tìm kiếm theo Asset Tag, Serial Number, Tên thiết bị...')}
            value={keyword}
            onChange={(e) => onKeywordChange(e.target.value)}
          />
        </div>
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
      {showCatalogFilters && <div
        style={{
          display: 'grid',
          gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))',
          gap: '12px',
        }}
      >
        {/* Status filter */}
        <div>
          <label style={{ fontSize: '12px', fontWeight: 600, color: 'var(--text-muted)', marginBottom: '4px', display: 'block' }}>
            {t('assets:fields.status', 'Trạng thái')}
          </label>
          <select
            className="form-control"
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
          <label style={{ fontSize: '12px', fontWeight: 600, color: 'var(--text-muted)', marginBottom: '4px', display: 'block' }}>
            {t('assets:fields.type', 'Loại thiết bị')}
          </label>
          <select
            className="form-control"
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
          <label style={{ fontSize: '12px', fontWeight: 600, color: 'var(--text-muted)', marginBottom: '4px', display: 'block' }}>
            {t('assets:fields.model', 'Model')}
          </label>
          <select
            className="form-control"
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
          <label style={{ fontSize: '12px', fontWeight: 600, color: 'var(--text-muted)', marginBottom: '4px', display: 'block' }}>
            {t('assets:fields.department', 'Phòng ban')}
          </label>
          <select
            className="form-control"
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
          <label style={{ fontSize: '12px', fontWeight: 600, color: 'var(--text-muted)', marginBottom: '4px', display: 'block' }}>
            {t('assets:fields.location', 'Vị trí')}
          </label>
          <select
            className="form-control"
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
