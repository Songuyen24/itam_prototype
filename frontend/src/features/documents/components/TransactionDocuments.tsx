import { useEffect, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { documentApi } from '../api/documentApi';
import { DocumentItem, DocumentTransaction } from '../types/document.types';
import { DocumentPagination } from './DocumentPagination';
import { ImportDraftPanel } from './ImportDraftPanel';
import { DocumentUpload } from './DocumentUpload';
import { useAuth } from '@/features/auth/contexts/AuthContext';

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
  const [reload, setReload] = useState(0);
  const activeDownload = useRef(true);

  useEffect(() => {
    activeDownload.current = true;
    return () => { activeDownload.current = false; };
  }, []);

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
        setTotalPages(documentResponse.data.totalPages);
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
    (user?.role === 'ADMIN' || user?.role === 'PUR_STAFF');

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
      <DocumentUpload transactionId={transaction.transactionId} documentsEditable={canUpload} expectedVersion={transaction.expectedVersion}
        onUploaded={() => { setPage(0); refresh(); }} />
      {downloadError && <div className="alert-banner alert-danger" role="alert">{downloadError}</div>}
      <div className="document-list-heading">
        <h3>{t('listTitle')}</h3>
        {downloadingId !== null && <span role="status">{t('states.downloading')}</span>}
      </div>
      {documents.length === 0 ? <div className="empty-state">{t('states.noDocuments')}</div> : (
        <div className="table-responsive">
          <table className="data-table document-table">
            <thead><tr>
              <th scope="col">{t('fields.file')}</th>
              <th scope="col">{t('fields.documentType')}</th>
              <th scope="col">{t('fields.uploadedBy')}</th>
              <th scope="col">{t('fields.actions')}</th>
            </tr></thead>
            <tbody>{documents.map((document) => <tr key={document.documentId}>
              <td data-label={t('fields.file')}>
                <div className="document-filename">{document.originalFileName}</div>
                <div className="document-file-meta">{document.mimeType ?? t('states.notAvailable')} / {document.fileSize == null
                  ? t('states.notAvailable')
                  : `${new Intl.NumberFormat(i18n.language, { maximumFractionDigits: 1 }).format(document.fileSize / 1024)} KB`}</div>
                {document.assetId != null && <div className="document-file-meta">{t('fields.assetId')}: {document.assetId}</div>}
              </td>
              <td data-label={t('fields.documentType')}>{t(`documentTypes.${document.documentType}`)}
                {document.locked && <div className="document-file-meta">{t('states.locked')}</div>}
              </td>
              <td data-label={t('fields.uploadedBy')}><div>{document.uploadedByName || t('states.notAvailable')}</div>
                <time className="document-file-meta" dateTime={document.createdAt}>{new Date(document.createdAt).toLocaleString(i18n.language)}</time>
              </td>
              <td data-label={t('fields.actions')}><button type="button" className="btn btn-secondary btn-sm document-download" disabled={downloadingId !== null}
                aria-label={t('actions.downloadFile', { name: document.originalFileName })}
                onClick={() => void download(document)}>{t('actions.download')}</button>
                {canUpload && <button type="button" className="btn btn-secondary btn-sm" onClick={() => {
                  if (!window.confirm(t('draft.detachConfirm'))) return;
                  void documentApi.detach(document.documentId, transaction.transactionId, transaction.expectedVersion!)
                    .then(refresh).catch(cause => setDownloadError(cause.message));
                }}>{t('draft.detach')}</button>}</td>
            </tr>)}</tbody>
          </table>
        </div>
      )}
      <DocumentPagination page={page} totalPages={totalPages} totalElements={totalElements} loading={loading} onPageChange={setPage} />
    </section>
  );
}
