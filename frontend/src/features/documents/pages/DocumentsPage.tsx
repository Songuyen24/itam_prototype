import { useAuth } from '@/features/auth/contexts/AuthContext';
import { httpClient } from '@/shared/api/httpClient';
import { ApiResponse } from '@/features/catalogs/types/catalog.types';
import { DocumentTransaction } from '../types/document.types';
import { useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { TransactionSelector } from '../components/TransactionSelector';
import { TransactionDocuments } from '../components/TransactionDocuments';
import './documents.css';

export function DocumentsPage() {
  const { t } = useTranslation('documents');
  const [transactionId, setTransactionId] = useState<number | null>(null);
  const { user } = useAuth();
  const [refresh, setRefresh] = useState(0);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  async function createDraft() {
    setBusy(true); setError(null);
    try {
      const response = await httpClient<ApiResponse<DocumentTransaction>>('/v1/import-drafts', { method: 'POST', body: JSON.stringify({}) });
      setTransactionId(response.data.transactionId); setRefresh(v => v + 1);
    } catch (cause) { setError(cause instanceof Error ? cause.message : t('errors.transaction')); }
    finally { setBusy(false); }
  }
  const details = useRef<HTMLDivElement>(null);

  function selectTransaction(id: number) {
    setTransactionId(id);
    if (window.matchMedia('(max-width: 1100px)').matches) {
      details.current?.scrollIntoView({ behavior: 'smooth', block: 'start' });
    }
  }

  return (
    <div className="documents-page">
      <div className="page-header"><h1 className="page-title">{t('title')}</h1></div>
      {error && <p role="alert">{error}</p>}
      {(user?.role === 'ADMIN' || user?.role === 'PUR_STAFF') && <button className="btn btn-primary" disabled={busy} onClick={() => void createDraft()}>{t('draft.create')}</button>}
      <div className="documents-workspace">
        <TransactionSelector key={refresh} selectedId={transactionId} onSelect={selectTransaction} />
        <div ref={details} className="document-detail-container">
          <TransactionDocuments key={transactionId ?? 'none'} transactionId={transactionId} onUpdated={() => setRefresh(v => v + 1)} />
        </div>
      </div>
    </div>
  );
}
