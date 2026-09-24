import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { documentApi } from '@/features/documents/api/documentApi';
import { DocumentTransaction } from '@/features/documents/types/document.types';
import { Handover, handoverApi } from '../api/handoverApi';
import { HandoverDetails } from '../components/HandoverDetails';
import { HandoverForm } from '../components/HandoverForm';
import './handover.css';

export function HandoversPage() {
  const { t } = useTranslation('handover');
  const [tab, setTab] = useState<'create' | 'list'>('create');
  const [keyword, setKeyword] = useState('');
  const [page, setPage] = useState(0);
  const [pages, setPages] = useState(0);
  const [items, setItems] = useState<DocumentTransaction[]>([]);
  const [detail, setDetail] = useState<Handover | null>(null);
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [error, setError] = useState('');
  const [detailError, setDetailError] = useState('');
  const [loading, setLoading] = useState(false);
  const [detailLoading, setDetailLoading] = useState(false);
  const [reload, setReload] = useState(0);
  useEffect(() => {
    if (tab !== 'list') return;
    let active = true; setLoading(true); setError('');
    documentApi.getTransactions({ type: 'HANDOVER', keyword, page, size: 10 }).then(r => {
      if (active) { setItems(r.data.content); setPages(r.data.totalPages); }
    }).catch((e: unknown) => { if (active) setError(e instanceof Error ? e.message : t('error')); })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [tab, keyword, page, reload, t]);
  useEffect(() => {
    if (selectedId == null) return;
    let active = true; setDetailLoading(true); setDetailError(''); setDetail(null);
    handoverApi.detail(selectedId).then(r => { if (active) setDetail(r.data); })
      .catch((e: unknown) => { if (active) setDetailError(e instanceof Error ? e.message : t('error')); })
      .finally(() => { if (active) setDetailLoading(false); });
    return () => { active = false; };
  }, [selectedId, reload, t]);
  return <div className="handover-page">
    <h1>{t('title')}</h1>
    <div className="handover-pagination">
      <button className="btn btn-secondary" aria-pressed={tab === 'create'} onClick={() => setTab('create')}>{t('create')}</button>
      <button className="btn btn-secondary" aria-pressed={tab === 'list'} onClick={() => setTab('list')}>{t('list')}</button>
    </div>
    {tab === 'create' ? <HandoverForm onCompleted={value => { setDetail(value); setSelectedId(value.transactionId); setTab('list'); setReload(r => r + 1); }} />
      : <div className="handover-history">
        <section><label className="form-label">{t('searchTransactions')}<input className="form-input" type="search" value={keyword} onChange={e => { setKeyword(e.target.value); setPage(0); }} /></label>
          {loading ? <p role="status">{t('loading')}</p> : error ? <p role="alert">{error}<button onClick={() => setReload(r => r + 1)}>{t('retry')}</button></p>
            : items.length === 0 ? <p>{t('empty')}</p> : items.map(item => <button key={item.transactionId} className="document-transaction-row" aria-pressed={item.transactionId === selectedId} onClick={() => setSelectedId(item.transactionId)}>
              <strong>{item.transactionCode}</strong><span>{t('completed')}</span><span>{item.processedByName}</span>
            </button>)}
          <div className="handover-pagination"><button className="btn btn-secondary" disabled={loading || page === 0} onClick={() => setPage(p => p - 1)}>{t('previous')}</button>
            <span>{page + 1} / {Math.max(1, pages)}</span><button className="btn btn-secondary" disabled={loading || page + 1 >= pages} onClick={() => setPage(p => p + 1)}>{t('next')}</button></div>
        </section>
        <section>{detailLoading ? <p role="status">{t('loading')}</p> : detailError ? <p role="alert">{detailError}<button onClick={() => setReload(r => r + 1)}>{t('retry')}</button></p>
          : detail ? <HandoverDetails value={detail} /> : <p>{t('selectTransaction')}</p>}</section>
      </div>}
  </div>;
}
