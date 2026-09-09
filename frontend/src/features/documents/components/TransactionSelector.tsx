import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useAuth } from '@/features/auth/contexts/AuthContext';
import { documentApi } from '../api/documentApi';
import { DocumentTransaction, TransactionStatus, TransactionType } from '../types/document.types';
import { DocumentPagination } from './DocumentPagination';

interface TransactionSelectorProps {
  selectedId: number | null;
  onSelect: (transactionId: number) => void;
}

export function TransactionSelector({ selectedId, onSelect }: TransactionSelectorProps) {
  const { t, i18n } = useTranslation('documents');
  const { user } = useAuth();
  const [keyword, setKeyword] = useState('');
  const [type, setType] = useState<TransactionType | ''>('');
  const [status, setStatus] = useState<TransactionStatus | ''>('');
  const [page, setPage] = useState(0);
  const [transactions, setTransactions] = useState<DocumentTransaction[]>([]);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [reload, setReload] = useState(0);

  useEffect(() => {
    let active = true;
    setLoading(true);
    setError(null);
    setTransactions([]);
    documentApi.getTransactions({ keyword, type: user?.role === 'PUR_STAFF' ? 'IMPORT' : type || undefined, status: status || undefined, page, size: 8 })
      .then((response) => {
        if (!active) return;
        if (!response.success || !response.data) throw new Error(response.message || t('errors.transactions'));
        setTransactions(response.data.content);
        setTotalPages(response.data.totalPages);
        setTotalElements(response.data.totalElements);
      })
      .catch((cause: unknown) => {
        if (active) setError(cause instanceof Error ? cause.message : t('errors.transactions'));
      })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [keyword, type, status, page, reload, user?.role, t]);

  return (
    <section className="document-transactions" aria-labelledby="document-transactions-title">
      <h2 id="document-transactions-title">{t('transactionsTitle')}</h2>
      <div className="document-filters">
        <label className="form-label" htmlFor="document-keyword">{t('fields.transactionCode')}</label>
        <input id="document-keyword" className="form-input" type="search" value={keyword}
          onChange={(event) => { setKeyword(event.target.value); setPage(0); }} />
        <div className="document-filter-pair">
          {user?.role !== 'PUR_STAFF' && <label className="form-label">
            {t('fields.transactionType')}
            <select className="form-select" value={type} onChange={(event) => { setType(event.target.value as TransactionType | ''); setPage(0); }}>
              <option value="">{t('filters.allTypes')}</option>
              {(['IMPORT', 'HANDOVER', 'RECOVERY', 'DISPOSAL'] as const).map((value) => (
                <option key={value} value={value}>{t(`transactionTypes.${value}`)}</option>
              ))}
            </select>
          </label>}
          <label className="form-label">
            {t('fields.status')}
            <select className="form-select" value={status} onChange={(event) => { setStatus(event.target.value as TransactionStatus | ''); setPage(0); }}>
              <option value="">{t('filters.allStatuses')}</option>
              {(['DRAFT', 'PENDING', 'COMPLETED', 'REJECTED'] as const).map((value) => (
                <option key={value} value={value}>{t(`statuses.${value}`)}</option>
              ))}
            </select>
          </label>
        </div>
      </div>
      <div aria-busy={loading} className="document-transaction-list">
        {loading ? <div role="status" className="empty-state">{t('states.loading')}</div>
          : error ? <div className="document-error">
            <p role="alert">{error}</p>
            <button type="button" className="btn btn-secondary" onClick={() => setReload((value) => value + 1)}>{t('actions.retry')}</button>
          </div>
            : transactions.length === 0 ? <div className="empty-state">{t('states.noTransactions')}</div>
              : transactions.map((transaction) => (
                <button key={transaction.transactionId} type="button" className={`document-transaction-row ${transaction.transactionId === selectedId ? 'is-selected' : ''}`}
                  aria-pressed={transaction.transactionId === selectedId} onClick={() => onSelect(transaction.transactionId)}>
                  <span className="document-transaction-code">{transaction.transactionCode}</span>
                  <span>{t(`transactionTypes.${transaction.type}`)} / {t(`statuses.${transaction.status}`)}</span>
                  <time dateTime={transaction.createdAt}>{new Date(transaction.createdAt).toLocaleDateString(i18n.language)}</time>
                </button>
              ))}
      </div>
      {!error && <DocumentPagination page={page} totalPages={totalPages} totalElements={totalElements} loading={loading} onPageChange={setPage} />}
    </section>
  );
}
