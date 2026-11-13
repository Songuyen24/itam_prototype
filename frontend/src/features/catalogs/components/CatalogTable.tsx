import React from 'react';
import { useTranslation } from 'react-i18next';
import EditOutlinedIcon from '@mui/icons-material/EditOutlined';
import DeleteOutlineOutlinedIcon from '@mui/icons-material/DeleteOutlineOutlined';
import LockOutlinedIcon from '@mui/icons-material/LockOutlined';
import LockOpenOutlinedIcon from '@mui/icons-material/LockOpenOutlined';
import { TableActionButton } from '@/shared/components/TableActionButton';

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
                    <TableActionButton icon={<EditOutlinedIcon />} label={t('common:buttons.edit', 'Chỉnh sửa')} onClick={() => onEdit(row)} />
                    )}
                    {onToggleActive && (
                      <TableActionButton icon={isActive ? <LockOutlinedIcon /> : <LockOpenOutlinedIcon />} label={isActive ? t('catalogs:columns.lock', 'Vô hiệu hóa') : t('catalogs:columns.unlock', 'Kích hoạt')} onClick={() => onToggleActive(row)} />
                    )}
                    {onDelete && (
                    <TableActionButton className="btn-danger" icon={<DeleteOutlineOutlinedIcon />} label={t('common:buttons.delete', 'Xóa danh mục')} onClick={() => onDelete(row)} />
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
