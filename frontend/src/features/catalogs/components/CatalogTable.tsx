import React from 'react';
import { useTranslation } from 'react-i18next';

export interface TableColumn<T> {
  header: string;
  accessor?: keyof T;
  render?: (row: T) => React.ReactNode;
  width?: string;
}

interface CatalogTableProps<T> {
  columns: TableColumn<T>[];
  data: T[];
  isLoading: boolean;
  onEdit?: (row: T) => void;
  onDelete?: (row: T) => void;
  onToggleActive?: (row: T) => void;
  keyExtractor: (row: T) => string | number;
}

export function CatalogTable<T>({
  columns,
  data,
  isLoading,
  onEdit,
  onDelete,
  onToggleActive,
  keyExtractor,
}: CatalogTableProps<T>) {
  const { t } = useTranslation(['catalogs', 'common']);

  if (isLoading) {
    return (
      <div className="empty-state">
        <p>{t('common:labels.loading', 'Đang tải dữ liệu danh mục...')}</p>
      </div>
    );
  }

  if (data.length === 0) {
    return (
      <div className="empty-state">
        <p>{t('common:labels.noData', 'Không có dữ liệu phù hợp với tìm kiếm hoặc bộ lọc.')}</p>
      </div>
    );
  }

  return (
    <div className="table-responsive">
      <table className="data-table">
        <thead>
          <tr>
            {columns.map((col, index) => (
              <th key={index} style={{ width: col.width }}>
                {col.header}
              </th>
            ))}
            {(onEdit || onDelete || onToggleActive) && (
              <th style={{ width: '180px', textAlign: 'center' }}>
                {t('common:labels.action', 'Thao tác')}
              </th>
            )}
          </tr>
        </thead>
        <tbody>
          {data.map((row) => {
            const key = keyExtractor(row);
            const isActive = (row as unknown as { isActive?: boolean; active?: boolean }).isActive ??
              (row as unknown as { isActive?: boolean; active?: boolean }).active ?? true;

            return (
              <tr key={key}>
                {columns.map((col, cIdx) => (
                  <td key={cIdx}>
                    {col.render
                      ? col.render(row)
                      : col.accessor
                      ? String(row[col.accessor] ?? '-')
                      : '-'}
                  </td>
                ))}
                {(onEdit || onDelete || onToggleActive) && (
                  <td style={{ textAlign: 'center' }}>
                    <div style={{ display: 'inline-flex', gap: '6px' }}>
                    {onEdit && (
                    <button
                      type="button"
                      className="btn btn-sm btn-secondary"
                      onClick={() => onEdit(row)}
                      title={t('common:buttons.edit', 'Chỉnh sửa')}
                    >
                      {t('common:buttons.edit', 'Sửa')}
                    </button>
                    )}
                    {onToggleActive && (
                      <button
                        type="button"
                        className="btn btn-sm btn-secondary"
                        onClick={() => onToggleActive(row)}
                        title={isActive ? t('catalogs:columns.lock', 'Vô hiệu hóa') : t('catalogs:columns.unlock', 'Kích hoạt')}
                      >
                        {isActive ? t('catalogs:columns.lock', 'Khóa') : t('catalogs:columns.unlock', 'Mở')}
                      </button>
                    )}
                    {onDelete && (
                    <button
                      type="button"
                      className="btn btn-sm btn-danger"
                      onClick={() => onDelete(row)}
                      title={t('common:buttons.delete', 'Xóa danh mục')}
                    >
                      {t('common:buttons.delete', 'Xóa')}
                    </button>
                    )}
                  </div>
                  </td>
                )}
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}
