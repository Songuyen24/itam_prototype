import { useAuth } from '@/features/auth/contexts/AuthContext';
import { receivingApi } from '@/features/receiving/api/receivingApi';
import { useEffect, useRef, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { TransactionSelector } from '../components/TransactionSelector';
import { TransactionDocuments } from '../components/TransactionDocuments';
import { DocumentsLibraryPage } from './DocumentsLibraryPage';
import './documents.css';

export function DocumentsPage({receiving=false}:{receiving?:boolean}) {
  const { t } = useTranslation('documents');
  const [searchParams] = useSearchParams();
  const requestedId = Number(searchParams.get('transactionId'));
  const deepLinkedId = Number.isSafeInteger(requestedId) && requestedId > 0 ? requestedId : null;
  const [transactionId, setTransactionId] = useState<number | null>(deepLinkedId);
  useEffect(() => { setTransactionId(deepLinkedId); }, [deepLinkedId]);
  const { user } = useAuth();
  const [refresh, setRefresh] = useState(0);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const modal = useRef<HTMLDivElement>(null);
  const openButton = useRef<HTMLElement | null>(null);
  useEffect(() => {
    if (!receiving || transactionId === null || typeof document === 'undefined') return;
    const previous = openButton.current ?? document.activeElement as HTMLElement | null;
    const overflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    modal.current?.focus();
    const onKeyDown = (event: KeyboardEvent) => {
      if (document.querySelector('.receiving-submodal-backdrop, .document-preview-backdrop, .modal-overlay, .receiving-reject, dialog[open]')) return;
      if (event.key === 'Escape') setTransactionId(null);
      if (event.key !== 'Tab') return;
      const focusable = [...(modal.current?.querySelectorAll<HTMLElement>('button:not(:disabled), input:not(:disabled), select:not(:disabled), textarea:not(:disabled), a[href]') ?? [])];
      if (!focusable.length) return;
      const first = focusable[0], last = focusable[focusable.length - 1];
      if (event.shiftKey && (document.activeElement === first || document.activeElement === modal.current)) { event.preventDefault(); last.focus(); }
      else if (!event.shiftKey && (document.activeElement === last || document.activeElement === modal.current)) { event.preventDefault(); first.focus(); }
    };
    document.addEventListener('keydown', onKeyDown);
    return () => { document.removeEventListener('keydown', onKeyDown); document.body.style.overflow = overflow; previous?.focus(); };
  }, [receiving, transactionId]);
  async function createDraft() {
    if (receiving && typeof document !== 'undefined') openButton.current = document.activeElement as HTMLElement | null;
    setBusy(true); setError(null);
    try {
      const response = await receivingApi.create();
      setTransactionId(response.data.transactionId); setRefresh(v => v + 1);
    } catch (cause) { setError(cause instanceof Error ? cause.message : t('errors.transaction')); }
    finally { setBusy(false); }
  }
  function selectTransaction(id: number) {
    if (transactionId === null) openButton.current = typeof document === 'undefined' ? null : document.activeElement as HTMLElement | null;
    setTransactionId(id);
  }

  if (receiving) return <div className="documents-page receiving-page">
    <div className="page-header"><h1 className="page-title">{t('receiving.title')}</h1></div>
    {error && <p role="alert">{error}</p>}
    <TransactionSelector receiving actions={(user?.role === 'ADMIN' || user?.role === 'PUR_STAFF') && <button className="btn btn-primary" disabled={busy} onClick={() => void createDraft()}>{t('draft.create')}</button>} selectedId={transactionId} refresh={refresh} onSelect={selectTransaction} />
    {transactionId !== null && <div className="receiving-modal-backdrop" onMouseDown={event => { if (event.target === event.currentTarget) setTransactionId(null); }}><div ref={modal} className="receiving-modal" role="dialog" aria-modal="true" aria-label={t('receiving.receiptModal')} tabIndex={-1}><div className="receiving-modal-heading"><button type="button" className="btn btn-secondary" onClick={() => setTransactionId(null)}>{t('actions.close')}</button></div><TransactionDocuments key={transactionId} transactionId={transactionId} compact onCreated={selectTransaction} onUpdated={() => setRefresh(value => value + 1)} /></div></div>}
  </div>;

  return <DocumentsLibraryPage />;
}
