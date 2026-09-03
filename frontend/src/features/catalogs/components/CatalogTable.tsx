import React from 'react';

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
  onEdit: (row: T) => void;
  onDelete: (row: T) => void;
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
  if (isLoading) {
    return (
      <div className="empty-state">
        <p>Đang tải dữ liệu danh mục...</p>
      </div>
    );
  }

  if (data.length === 0) {
    return (
      <div className="empty-state">
        <p>Không có dữ liệu phù hợp với tìm kiếm hoặc bộ lọc.</p>
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
            <th style={{ width: '180px', textAlign: 'center' }}>Thao tác</th>
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
                <td style={{ textAlign: 'center' }}>
                  <div style={{ display: 'inline-flex', gap: '6px' }}>
                    <button
                      type="button"
                      className="btn btn-sm btn-secondary"
                      onClick={() => onEdit(row)}
                      title="Chỉnh sửa"
                    >
                      Sửa
                    </button>
                    {onToggleActive && (
                      <button
                        type="button"
                        className="btn btn-sm btn-secondary"
                        onClick={() => onToggleActive(row)}
                        title={isActive ? 'Vô hiệu hóa' : 'Kích hoạt'}
                      >
                        {isActive ? 'Khóa' : 'Mở'}
                      </button>
                    )}
                    <button
                      type="button"
                      className="btn btn-sm btn-danger"
                      onClick={() => onDelete(row)}
                      title="Xóa danh mục"
                    >
                      Xóa
                    </button>
                  </div>
                </td>
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}
