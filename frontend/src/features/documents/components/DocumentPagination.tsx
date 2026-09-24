import { useTranslation } from 'react-i18next';

interface DocumentPaginationProps {
  page: number;
  totalPages: number;
  totalElements: number;
  loading: boolean;
  onPageChange: (page: number) => void;
}

export function DocumentPagination({ page, totalPages, totalElements, loading, onPageChange }: DocumentPaginationProps) {
  const { t } = useTranslation('documents');
  return (
    <div className="document-pagination">
      <span>{t('pagination.summary', { page: totalPages ? page + 1 : 0, pages: totalPages, count: totalElements })}</span>
      <div className="pagination-controls">
        <button type="button" className="btn btn-secondary document-page-button" disabled={loading || page === 0}
          title={t('pagination.previous')} aria-label={t('pagination.previous')} onClick={() => onPageChange(page - 1)}>&larr;</button>
        <button type="button" className="btn btn-secondary document-page-button" disabled={loading || page + 1 >= totalPages}
          title={t('pagination.next')} aria-label={t('pagination.next')} onClick={() => onPageChange(page + 1)}>&rarr;</button>
      </div>
    </div>
  );
}
