import { useEffect, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useSearchParams } from 'react-router-dom';
import { useAuth } from '@/features/auth/contexts/AuthContext';
import { documentApi } from '../api/documentApi';
import { DocumentLibraryItem, DocumentTransaction, DocumentType, TransactionType } from '../types/document.types';
import { DocumentPagination } from '../components/DocumentPagination';
import { AdjustmentPanel } from '../components/AdjustmentPanel';
import { PdfCanvasPreview } from '../components/PdfCanvasPreview';
import './documents-library.css';

const documentTypes: DocumentType[] = ['INVOICE','PURCHASE_ORDER','IMPORT_RECEIPT','HANDOVER_REPORT','RECOVERY_REPORT','DISPOSAL_REPORT','CONTRACT','OTHER'];
const transactionTypes: TransactionType[] = ['IMPORT','HANDOVER','RECOVERY','DISPOSAL'];

export function DocumentsLibraryPage() {
  const { t, i18n } = useTranslation('documents');
  const { user } = useAuth();
  const [searchParams] = useSearchParams();
  const id = Number(searchParams.get('transactionId'));
  const transactionId = Number.isSafeInteger(id) && id > 0 ? id : null;
  const [keyword, setKeyword] = useState('');
  const [documentType, setDocumentType] = useState<DocumentType | ''>('');
  const [transactionType, setTransactionType] = useState<TransactionType | ''>('');
  const [page, setPage] = useState(0);
  const [pages, setPages] = useState(0);
  const [total, setTotal] = useState(0);
  const [rows, setRows] = useState<DocumentLibraryItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [revision, setRevision] = useState(0);
  const [preview, setPreview] = useState<DocumentLibraryItem | null>(null);
  const [previewUrl, setPreviewUrl] = useState('');
  const [previewBlob, setPreviewBlob] = useState<Blob | null>(null);
  const [previewLoading, setPreviewLoading] = useState(false);
  const [previewError, setPreviewError] = useState('');
  const [downloadError, setDownloadError] = useState('');
  const [sourceId, setSourceId] = useState<number | null>(transactionId);
  const [source, setSource] = useState<DocumentTransaction | null>(null);
  const [sourceError, setSourceError] = useState('');
  const [sourceLoading, setSourceLoading] = useState(false);
  const dialog = useRef<HTMLDialogElement>(null);
  const sourceDialog = useRef<HTMLDialogElement>(null);

  useEffect(() => { setPage(0); setSourceId(transactionId); }, [transactionId]);
  useEffect(() => {
    if (sourceId === null) return;
    let active = true; setSourceLoading(true); setSourceError(''); setSource(null);
    documentApi.getTransaction(sourceId).then(result => { if (active) setSource(result.data); })
      .catch(cause => { if (active) setSourceError(cause instanceof Error ? cause.message : t('errors.transaction')); })
      .finally(() => { if (active) setSourceLoading(false); });
    return () => { active = false; };
  }, [sourceId, t]);
  useEffect(() => {
    let active = true; setLoading(true); setError(''); setRows([]);
    documentApi.library({ keyword, documentType, transactionType, transactionId, page, size: 20 }).then(result => {
      if (!active) return;
      setRows(result.data.content); setPages(result.data.totalPages); setTotal(result.data.totalElements);
      setPage(current => Math.min(current, Math.max(0, result.data.totalPages - 1)));
    }).catch(cause => { if (active) setError(cause instanceof Error ? cause.message : t('errors.documents')); })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [keyword, documentType, transactionType, transactionId, page, revision, t]);
  useEffect(() => {
    if (!preview || typeof document === 'undefined') return;
    const native = dialog.current;
    const previous = document.activeElement as HTMLElement | null;
    native?.showModal(); native?.querySelector('button')?.focus();
    let active = true; let url = '';
    setPreviewUrl(''); setPreviewBlob(null); setPreviewError(''); setPreviewLoading(true);
    if (['application/pdf', 'image/png', 'image/jpeg'].includes(preview.mimeType ?? '')) {
      documentApi.preview(preview.documentId).then(blob => { if (active) { if (preview.mimeType === 'application/pdf') setPreviewBlob(blob); else { url = URL.createObjectURL(new Blob([blob], { type: preview.mimeType || blob.type })); setPreviewUrl(url); } } })
        .catch(cause => { if (active) setPreviewError(cause instanceof Error ? cause.message : t('errors.download')); })
        .finally(() => { if (active) setPreviewLoading(false); });
    } else setPreviewLoading(false);
    return () => { active = false; if (url) URL.revokeObjectURL(url); native?.close(); previous?.focus(); };
  }, [preview, t]);
  useEffect(() => {
    if (sourceId === null || typeof document === 'undefined') return;
    const native = sourceDialog.current;
    const previous = document.activeElement as HTMLElement | null;
    native?.showModal();native?.querySelector('button')?.focus();
    return () => { native?.close(); previous?.focus(); };
  }, [sourceId]);
  const closePreview = () => { setPreview(null); setPreviewUrl(''); setPreviewBlob(null); };
  const download = async (row: DocumentLibraryItem) => {
    setDownloadError('');
    try { await documentApi.download(row); }
    catch (cause) { setDownloadError(cause instanceof Error ? cause.message : t('errors.download')); }
  };

  return <section className="documents-library"><div className="page-header"><h1 className="page-title">{t('library.title')}</h1></div>
    {transactionId && <p>{t('library.sourceFilter', { id: transactionId })}</p>}
    <div className="documents-library-filters">
      <label>{t('library.search')}<input className="form-input" type="search" maxLength={100} value={keyword} onChange={event => { setKeyword(event.target.value); setPage(0); }} /></label>
      <label>{t('library.documentType')}<select className="form-select" value={documentType} onChange={event => { setDocumentType(event.target.value as DocumentType | ''); setPage(0); }}><option value="">{t('library.allDocumentTypes')}</option>{documentTypes.map(type => <option key={type} value={type}>{t(`documentTypes.${type}`)}</option>)}</select></label>
      {user?.role !== 'PUR_STAFF' && <label>{t('fields.transactionType')}<select className="form-select" value={transactionType} onChange={event => { setTransactionType(event.target.value as TransactionType | ''); setPage(0); }}><option value="">{t('filters.allTypes')}</option>{transactionTypes.map(type => <option key={type} value={type}>{t(`transactionTypes.${type}`)}</option>)}</select></label>}
    </div>
    {error && <p role="alert">{error} <button className="btn btn-secondary" onClick={() => setRevision(value => value + 1)}>{t('actions.retry')}</button></p>}
    {downloadError && <p role="alert">{downloadError}</p>}
    {loading ? <p role="status">{t('states.loading')}</p> : !error && <div className="table-container"><table className="data-table"><thead><tr><th>{t('fields.file')}</th><th>{t('library.documentType')}</th><th>{t('library.source')}</th><th>{t('fields.createdAt')}</th><th>{t('fields.uploadedBy')}</th><th>{t('fields.actions')}</th></tr></thead><tbody>{rows.map(row => <tr key={row.documentId}><td>{row.originalFileName}</td><td>{t(`documentTypes.${row.documentType}`)}</td><td><button className="btn btn-secondary btn-sm" onClick={() => setSourceId(row.transactionId)}>{row.transactionCode}</button> · {t(`transactionTypes.${row.transactionType}`)} · {t(`statuses.${row.transactionStatus}`)}</td><td>{new Date(row.createdAt).toLocaleDateString(i18n.language)}</td><td>{row.uploadedByName}</td><td><button className="btn btn-secondary btn-sm" onClick={() => { setPreview(row); setPreviewError(''); }}>{t('actions.preview')}</button><button className="btn btn-secondary btn-sm" onClick={() => void download(row)}>{t('actions.download')}</button></td></tr>)}</tbody></table>{rows.length === 0 && <p>{t('library.empty')}</p>}</div>}
    {!error && <DocumentPagination page={page} totalPages={pages} totalElements={total} loading={loading} onPageChange={setPage} />}
    {preview && <dialog ref={dialog} className="documents-library-preview" aria-label={preview.originalFileName} onCancel={event => { event.preventDefault(); closePreview(); }}><div className="document-preview-heading"><h2>{preview.originalFileName}</h2><button className="btn btn-secondary" onClick={closePreview}>{t('actions.close')}</button></div><p>{preview.transactionCode} · {t(`documentTypes.${preview.documentType}`)}</p>{previewError && <p role="alert">{previewError}</p>}{previewLoading ? <p role="status">{t('states.loading')}</p> : previewBlob ? <PdfCanvasPreview blob={previewBlob} /> : previewUrl ? <img src={previewUrl} alt={preview.originalFileName} /> : !previewError && <p>{t('states.previewUnavailable')}</p>}<button className="btn btn-secondary" onClick={() => void download(preview)}>{t('actions.download')}</button></dialog>}
    {sourceId !== null && <dialog ref={sourceDialog} className="documents-library-preview" aria-label={t('library.source')} onCancel={event => { event.preventDefault(); setSourceId(null); }}><div className="document-preview-heading"><h2>{t('library.source')}</h2><button className="btn btn-secondary" onClick={() => setSourceId(null)}>{t('actions.close')}</button></div>{sourceLoading ? <p role="status">{t('states.loading')}</p> : sourceError ? <p role="alert">{sourceError}</p> : source && <><p><strong>{source.transactionCode}</strong> · {t(`transactionTypes.${source.type}`)} · {t(`statuses.${source.status}`)}</p><p>{t('fields.createdAt')}: {new Date(source.createdAt).toLocaleString(i18n.language)}</p>{source.status === 'COMPLETED' && <AdjustmentPanel transactionId={source.transactionId} isAdmin={user?.role === 'ADMIN'} />}</>}</dialog>}
  </section>;
}
