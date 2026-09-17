import { useCallback, useEffect, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { documentApi } from '../api/documentApi';
import { publicationApi, PublicationStatus } from '../api/publicationApi';
import { TransactionStatus, TransactionType } from '../types/document.types';

type PublicationPanelProps = {
  transactionId: number;
  canManage?: boolean;
  canRegenerate?: boolean;
  canResend?: boolean;
};

export function publicationPermissions(type: TransactionType, status: TransactionStatus, role?: string) {
  const manager = role === 'ADMIN' || role === 'IT_STAFF';
  return {
    canRegenerate: manager && status === 'COMPLETED',
    canResend: manager && (status === 'COMPLETED' || type === 'IMPORT' && status === 'REJECTED'),
  };
}

export function PublicationPanel({ transactionId, canManage = false, canRegenerate = canManage, canResend = canManage }: PublicationPanelProps) {
  const { t, i18n } = useTranslation('documents');
  const [value, setValue] = useState<PublicationStatus | null>(null);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const generation = useRef(0);
  const load = useCallback(async () => {
    const request = ++generation.current;
    setValue(null);
    setLoading(true);
    setError(null);
    try {
      const next = (await publicationApi.status(transactionId)).data;
      if (request === generation.current) setValue(next);
    } catch (cause) {
      if (request === generation.current) setError(cause instanceof Error ? cause.message : t('publication.loadError'));
    } finally {
      if (request === generation.current) setLoading(false);
    }
  }, [transactionId, i18n?.language, t]);
  useEffect(() => {
    setBusy(false);
    void load();
    return () => { generation.current += 1; };
  }, [load]);

  async function mutate(action: 'regenerate' | 'resend') {
    const request = ++generation.current;
    setBusy(true); setError(null);
    try {
      const next = (await publicationApi[action](transactionId)).data;
      if (request === generation.current) setValue(next);
    } catch (cause) {
      if (request === generation.current) setError(cause instanceof Error ? cause.message : t('publication.actionError'));
    } finally {
      if (request === generation.current) setBusy(false);
    }
  }
  async function download() {
    if (!currentValue?.pdf.documentId || !currentValue.pdf.fileName) return;
    const request = generation.current;
    setBusy(true); setError(null);
    try { await documentApi.download({ documentId: currentValue.pdf.documentId, originalFileName: currentValue.pdf.fileName }); }
    catch (cause) { if (request === generation.current) setError(cause instanceof Error ? cause.message : t('errors.download')); }
    finally { if (request === generation.current) setBusy(false); }
  }
  const currentValue = value?.transactionId === transactionId ? value : null;
  const latestEmail = currentValue?.emails[0];
  return <section className="publication-panel" aria-busy={busy || loading}>
    <div className="document-list-heading"><h3>{t('publication.title')}</h3>
      <button type="button" className="btn btn-secondary btn-sm" disabled={busy || loading} onClick={() => void load()}>{t('publication.refresh')}</button>
    </div>
    {error && <div className="alert-banner alert-danger" role="alert">{error}</div>}
    {loading && <p role="status">{t('states.loading')}</p>}
    {currentValue && <>
      <dl className="document-transaction-metadata">
        <div><dt>{t('publication.pdf')}</dt><dd>{t(`publication.pdfStatus.${currentValue.pdf.status}`)}</dd></div>
        <div><dt>{t('publication.version')}</dt><dd>{currentValue.pdf.version ?? '-'}</dd></div>
        <div><dt>{t('publication.email')}</dt><dd>{latestEmail ? t(`publication.emailStatus.${latestEmail.status}`) : t('publication.noEmail')}</dd></div>
      </dl>
      {currentValue.pdf.issuedAt && <p>{t('publication.issuedAt')}: {new Date(currentValue.pdf.issuedAt).toLocaleString(i18n?.language)}</p>}
      {currentValue.pdf.errorMessage && <p className="document-error">{currentValue.pdf.errorMessage}</p>}
      {latestEmail?.errorMessage && <p className="document-error">{latestEmail.errorMessage}</p>}
      {latestEmail && <p className="document-file-meta">{latestEmail.recipient} · {t(`publication.events.${latestEmail.eventType}`, { defaultValue: latestEmail.eventType })}</p>}
      <p className="document-file-meta">{t('publication.independentHint')}</p>
    </>}
    <div className="receiving-actions">
      <button type="button" className="btn btn-primary" disabled={busy || loading || !currentValue?.pdf.documentId} onClick={() => void download()}>{t('publication.download')}</button>
      {canRegenerate && <button type="button" className="btn btn-secondary" disabled={busy || loading} onClick={() => void mutate('regenerate')}>{t('publication.regenerate')}</button>}
      {canResend && <button type="button" className="btn btn-secondary" disabled={busy || loading} onClick={() => void mutate('resend')}>{t('publication.resend')}</button>}
    </div>
  </section>;
}
