import { useEffect, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { documentApi } from '../api/documentApi';
import { DocumentItem, DocumentTransaction } from '../types/document.types';
import { DocumentPagination } from './DocumentPagination';
import { ImportDraftPanel } from './ImportDraftPanel';
import { DocumentUpload } from './DocumentUpload';
import { useAuth } from '@/features/auth/contexts/AuthContext';
import { PublicationPanel, publicationPermissions } from './PublicationPanel';

export function TransactionDocuments({ transactionId, onUpdated, onCreated }: { transactionId: number | null; onUpdated?: () => void; onCreated?: (id:number)=>void }) {
  const { t, i18n } = useTranslation('documents');
  const { user } = useAuth();
  const [transaction, setTransaction] = useState<DocumentTransaction | null>(null);
  const [documents, setDocuments] = useState<DocumentItem[]>([]);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [loading, setLoading] = useState(transactionId !== null);
  const [error, setError] = useState<string | null>(null);
  const [downloadError, setDownloadError] = useState<string | null>(null);
  const [downloadingId, setDownloadingId] = useState<number | null>(null);
  const [preview, setPreview] = useState<DocumentItem | null>(null);
  const [previewUrl, setPreviewUrl] = useState<string | null>(null);
  const [previewLoading, setPreviewLoading] = useState(false);
  const [reload, setReload] = useState(0);
  const activeDownload = useRef(true);
  const previewDialog = useRef<HTMLDivElement>(null);

  useEffect(() => {
    activeDownload.current = true;
    return () => { activeDownload.current = false; };
  }, []);

  useEffect(() => { setPreview(null); }, [transactionId]);

  useEffect(() => {
    if (!preview) return;
    const previousFocus = document.activeElement instanceof HTMLElement ? document.activeElement : null;
    previewDialog.current?.querySelector('button')?.focus();
    const closeOnEscape = (event: KeyboardEvent) => { if (event.key === 'Escape') setPreview(null); };
    const keepFocus = (event: KeyboardEvent) => {
      if (event.key !== 'Tab' || !previewDialog.current) return;
      const buttons = [...previewDialog.current.querySelectorAll<HTMLButtonElement>('button:not(:disabled)')];
      if (!buttons.length) return;
      if (event.shiftKey && document.activeElement === buttons[0]) { event.preventDefault(); buttons[buttons.length - 1].focus(); }
      else if (!event.shiftKey && document.activeElement === buttons[buttons.length - 1]) { event.preventDefault(); buttons[0].focus(); }
    };
    window.addEventListener('keydown', closeOnEscape);
    window.addEventListener('keydown', keepFocus);
    let active = true;
    let url: string | null = null;
    setPreviewLoading(true);
    setPreviewUrl(null);
    setDownloadError(null);
    documentApi.preview(preview.documentId).then(blob => {
      if (!active) return;
      url = URL.createObjectURL(blob);
      setPreviewUrl(url);
    }).catch(cause => {
      if (active) setDownloadError(cause instanceof Error ? cause.message : t('errors.download'));
    }).finally(() => { if (active) setPreviewLoading(false); });
    return () => { active = false; if (url) URL.revokeObjectURL(url); window.removeEventListener('keydown', closeOnEscape); window.removeEventListener('keydown', keepFocus); previousFocus?.focus(); };
  }, [preview, t]);

  useEffect(() => {
    if (transactionId === null) return;
    let active = true;
    setLoading(true);
    setError(null);
    setDownloadError(null);
    setTransaction(null);
    setDocuments([]);
    Promise.all([documentApi.getTransaction(transactionId), documentApi.getDocuments(transactionId, page, 10)])
      .then(([transactionResponse, documentResponse]) => {
        if (!active) return;
        if (!transactionResponse.success || !transactionResponse.data) {
          throw new Error(transactionResponse.message || t('errors.transaction'));
        }
        if (!documentResponse.success || !documentResponse.data) {
          throw new Error(documentResponse.message || t('errors.documents'));
        }
        setTransaction(transactionResponse.data);
        setDocuments(documentResponse.data.content);
        setTotalPages(documentResponse.data.totalPages); setPage(current => Math.min(current, Math.max(0, documentResponse.data.totalPages - 1)));
        setTotalElements(documentResponse.data.totalElements);
      })
      .catch((cause: unknown) => {
        if (active) setError(cause instanceof Error ? cause.message : t('errors.documents'));
      })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [transactionId, page, reload, t]);

  function refresh() { setReload(v => v + 1); onUpdated?.(); }

  async function download(document: DocumentItem) {
    if (downloadingId !== null) return;
    setDownloadingId(document.documentId);
    setDownloadError(null);
    try {
      await documentApi.download(document);
    } catch (cause) {
      if (activeDownload.current) setDownloadError(cause instanceof Error ? cause.message : t('errors.download'));
    } finally {
      if (activeDownload.current) setDownloadingId(null);
    }
  }

  if (transactionId === null) return <div className="empty-state document-selection-empty">{t('states.selectTransaction')}</div>;
  if (loading) return <div className="empty-state" role="status">{t('states.loading')}</div>;
  if (error) return <div className="document-error">
    <p role="alert">{error}</p>
    <button type="button" className="btn btn-secondary" onClick={() => setReload((value) => value + 1)}>{t('actions.retry')}</button>
  </div>;
  if (!transaction) return null;

  const canUpload = transaction.documentsEditable && transaction.type === 'IMPORT' &&
    (user?.role === 'ADMIN' || (user?.role === 'PUR_STAFF' && user.id === transaction.requesterId));
  const publicationActions = publicationPermissions(transaction.type, transaction.status, user?.role);

  return (
    <section className="document-detail" aria-labelledby="document-detail-title">
      <div className="document-detail-heading">
        <h2 id="document-detail-title">{transaction.transactionCode}</h2>
        {!transaction.documentsEditable && <span className="badge badge-inactive">{t('states.readOnly')}</span>}
      </div>
      <dl className="document-transaction-metadata">
        <div><dt>{t('fields.transactionType')}</dt><dd>{t(`transactionTypes.${transaction.type}`)}</dd></div>
        <div><dt>{t('fields.status')}</dt><dd>{t(`statuses.${transaction.status}`)}</dd></div>
        <div><dt>{t('fields.createdAt')}</dt><dd>{new Date(transaction.createdAt).toLocaleString(i18n.language)}</dd></div>
      </dl>
      {transaction.type === 'IMPORT' && <ImportDraftPanel transaction={transaction} onChanged={refresh} onCreated={onCreated} />}
      {transaction.status !== 'DRAFT' && <PublicationPanel transactionId={transaction.transactionId}
        {...publicationActions} />}
      <DocumentUpload transactionId={transaction.transactionId} documentsEditable={canUpload} expectedVersion={transaction.expectedVersion}
        onUploaded={() => { setPage(0); refresh(); }} />
      {downloadError && <div className="alert-banner alert-danger" role="alert">{downloadError}</div>}
      <div className="document-list-heading">
        <h3>{t('listTitle')}</h3>
        {downloadingId !== null && <span role="status">{t('states.downloading')}</span>}
      </div>
      {documents.length === 0 ? <div className="empty-state">{t('states.noDocuments')}</div> :
        <div className="document-cards">{documents.map(document => <article className="document-card" key={document.documentId}>
          <strong className="document-filename">{document.originalFileName}</strong>
          <span>{t(`documentTypes.${document.documentType}`)}{document.locked ? ` · ${t('states.locked')}` : ''}</span>
          <span className="document-file-meta">{document.mimeType ?? t('states.notAvailable')} · {document.fileSize == null ? t('states.notAvailable') : `${new Intl.NumberFormat(i18n.language, { maximumFractionDigits: 1 }).format(document.fileSize / 1024)} KB`}</span>
          {document.assetId != null && <span className="document-file-meta">{t('fields.assetId')}: {document.assetId}</span>}
          <span className="document-file-meta">{document.uploadedByName || t('states.notAvailable')} · {new Date(document.createdAt).toLocaleString(i18n.language)}</span>
          <div className="document-card-actions">
            <button type="button" className="btn btn-secondary btn-sm" onClick={() => setPreview(document)}>{t('actions.preview')}</button>
            <button type="button" className="btn btn-secondary btn-sm" disabled={downloadingId !== null}
              aria-label={t('actions.downloadFile', { name: document.originalFileName })} onClick={() => void download(document)}>{t('actions.download')}</button>
            {canUpload && <button type="button" className="btn btn-secondary btn-sm" onClick={() => {
              if (!window.confirm(t('draft.detachConfirm'))) return;
              void documentApi.detach(document.documentId, transaction.transactionId, transaction.expectedVersion!)
                .then(refresh).catch(cause => setDownloadError(cause.message));
            }}>{t('draft.detach')}</button>}
          </div>
        </article>)}</div>}
      {preview && <div className="document-preview-backdrop" onClick={() => setPreview(null)}>
        <div ref={previewDialog} role="dialog" aria-modal="true" aria-label={preview.originalFileName} className="document-preview" onClick={event => event.stopPropagation()}>
          <div className="document-preview-heading"><strong>{preview.originalFileName}</strong><button type="button" className="btn btn-secondary btn-sm" onClick={() => setPreview(null)}>{t('actions.close')}</button></div>
          <p>{t(`documentTypes.${preview.documentType}`)} · {preview.uploadedByName || t('states.notAvailable')} · {new Date(preview.createdAt).toLocaleString(i18n.language)}</p>
          {downloadError && <p role="alert">{downloadError}</p>}
          {previewLoading ? <p role="status">{t('states.loading')}</p> : previewUrl && (preview.mimeType === 'application/pdf'
            ? <iframe title={preview.originalFileName} src={previewUrl} />
            : preview.mimeType?.startsWith('image/') ? <img src={previewUrl} alt={preview.originalFileName} /> : <p>{t('states.previewUnavailable')}</p>)}
          <button type="button" className="btn btn-secondary btn-sm" onClick={() => void download(preview)}>{t('actions.download')}</button>
        </div>
      </div>}
      <DocumentPagination page={page} totalPages={totalPages} totalElements={totalElements} loading={loading} onPageChange={setPage} />
    </section>
  );
}
