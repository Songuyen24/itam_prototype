import { useEffect, useState, type ReactNode } from 'react';
import { useTranslation } from 'react-i18next';
import { useAuth } from '@/features/auth/contexts/AuthContext';
import { documentApi } from '../api/documentApi';
import { DocumentTransaction, TransactionStatus, TransactionType } from '../types/document.types';
import { DocumentPagination } from './DocumentPagination';
import { ReceivingDateRangePicker } from './ReceivingDateRangePicker';

interface TransactionSelectorProps {
  receiving?: boolean;
  refresh?: number;
  actions?: ReactNode;
  selectedId: number | null;
  onSelect: (transactionId: number) => void;
}

export function TransactionSelector({ selectedId, onSelect, receiving=false, refresh=0, actions }: TransactionSelectorProps) {
  const { t, i18n } = useTranslation('documents');
  const { user } = useAuth();
  const [keyword, setKeyword] = useState('');
  const [type, setType] = useState<TransactionType | ''>('');
  const [tab, setTab] = useState<'DRAFT' | 'PENDING' | 'FINAL'>(receiving&&user?.role==='IT_STAFF'?'PENDING':'DRAFT');
  const [receivingStatus, setReceivingStatus] = useState<TransactionStatus | ''>('');
  const [from, setFrom] = useState('');
  const [to, setTo] = useState('');
  const [page, setPage] = useState(0);
  const [transactions, setTransactions] = useState<DocumentTransaction[]>([]);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [reload, setReload] = useState(0);
  const invalidRange = receiving && !!from && !!to && from > to;

  useEffect(() => {
    let active = true;
    setLoading(true);
    setError(null);
    setTransactions([]);
    if (invalidRange) { setTotalPages(0); setTotalElements(0); setLoading(false); return; }
    documentApi.getTransactions({ keyword, type: receiving || user?.role === 'PUR_STAFF' ? 'IMPORT' : type || undefined,
      ...(receiving ? { status: receivingStatus || undefined, from: from || undefined, to: to || undefined } : { statuses: tab === 'FINAL' ? ['COMPLETED', 'REJECTED', 'CANCELLED'] as TransactionStatus[] : [tab] }), page, size: receiving ? 20 : 8 })
      .then((response) => {
        if (!active) return;
        if (!response.success || !response.data) throw new Error(response.message || t('errors.transactions'));
        setTransactions(response.data.content);
        setTotalPages(response.data.totalPages); setPage(current => Math.min(current, Math.max(0, response.data.totalPages - 1)));
        setTotalElements(response.data.totalElements);
      })
      .catch((cause: unknown) => {
        if (active) setError(cause instanceof Error ? cause.message : t('errors.transactions'));
      })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [keyword, type, tab, receivingStatus, from, to, invalidRange, page, reload, refresh, user?.role, receiving, t]);

  if (receiving) return <section className="document-transactions receiving-transactions" aria-label={t('receiving.title')}>
    <div className="receiving-list-filters">
      <label>{t('fields.transactionCode')}<input className="form-input" type="search" maxLength={100} value={keyword} onChange={event => { setKeyword(event.target.value); setPage(0); }} /></label>
      <label>{t('fields.status')}<select className="form-select" value={receivingStatus} onChange={event => { setReceivingStatus(event.target.value as TransactionStatus | ''); setPage(0); }}><option value="">{t('filters.allStatuses')}</option>{(['DRAFT', 'PENDING', 'COMPLETED', 'REJECTED', 'CANCELLED'] as const).map(status => <option key={status} value={status}>{t(`statuses.${status}`)}</option>)}</select></label>
      <ReceivingDateRangePicker from={from} to={to} onChange={(start, end) => { setFrom(start); setTo(end); setPage(0); }} />
      {actions && <div className="receiving-list-actions">{actions}</div>}
    </div>
    {invalidRange && <p role="alert">{t('receiving.invalidDateRange')}</p>}
    <div aria-busy={loading} className="table-container">{loading ? <p role="status">{t('states.loading')}</p> : error ? <div className="document-error"><p role="alert">{error}</p><button type="button" className="btn btn-secondary" onClick={() => setReload(value => value + 1)}>{t('actions.retry')}</button></div> : !invalidRange ? <><table className="data-table"><thead><tr><th>{t('fields.transactionCode')}</th><th>{t('fields.status')}</th><th>{t('fields.createdAt')}</th><th>{t('receiving.createdBy')}</th><th>{t('fields.actions')}</th></tr></thead><tbody>{transactions.map(transaction => <tr key={transaction.transactionId}><td>{transaction.transactionCode}</td><td><span className={`document-status document-status-${transaction.status.toLowerCase()}`}>{t(`statuses.${transaction.status}`)}</span></td><td>{new Date(transaction.createdAt).toLocaleDateString(i18n.language)}</td><td>{transaction.requesterName || '—'}</td><td><button type="button" className="btn btn-secondary" onClick={() => onSelect(transaction.transactionId)}>{t('actions.openReceipt')}</button></td></tr>)}</tbody></table>{transactions.length === 0 && <p className="empty-state">{t('states.noTransactions')}</p>}</> : null}</div>
    {!error && <DocumentPagination page={page} totalPages={totalPages} totalElements={totalElements} loading={loading} onPageChange={setPage} />}
  </section>;

  return (
    <section className="document-transactions" aria-labelledby="document-transactions-title">
      <h2 id="document-transactions-title">{t('transactionsTitle')}</h2>
      <div className="document-tabs" role="tablist" aria-label={t('fields.status')}>
        {(['DRAFT', 'PENDING', 'FINAL'] as const).map((value) => <button key={value} type="button" role="tab"
          aria-selected={tab === value} className={tab === value ? 'is-active' : ''}
          onClick={() => { setTab(value); setPage(0); }}>{t(`tabs.${value}`)}</button>)}
      </div>
      <div className="document-filters">
        <label className="form-label" htmlFor="document-keyword">{t('fields.transactionCode')}</label>
        <input id="document-keyword" className="form-input" type="search" value={keyword}
          onChange={(event) => { setKeyword(event.target.value); setPage(0); }} />
        <div className="document-filter-pair">
          {!receiving && user?.role !== 'PUR_STAFF' && <label className="form-label">
            {t('fields.transactionType')}
            <select className="form-select" value={type} onChange={(event) => { setType(event.target.value as TransactionType | ''); setPage(0); }}>
              <option value="">{t('filters.allTypes')}</option>
              {(['IMPORT', 'HANDOVER', 'RECOVERY', 'DISPOSAL'] as const).map((value) => (
                <option key={value} value={value}>{t(`transactionTypes.${value}`)}</option>
              ))}
            </select>
          </label>}
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
                  <span>{t(`transactionTypes.${transaction.type}`)} / <span className={`document-status document-status-${transaction.status.toLowerCase()}`}>{t(`statusTags.${transaction.status}`)}</span></span>
                  <time dateTime={transaction.createdAt}>{new Date(transaction.createdAt).toLocaleDateString(i18n.language)}</time>
                </button>
              ))}
      </div>
      {!error && <DocumentPagination page={page} totalPages={totalPages} totalElements={totalElements} loading={loading} onPageChange={setPage} />}
    </section>
  );
}
