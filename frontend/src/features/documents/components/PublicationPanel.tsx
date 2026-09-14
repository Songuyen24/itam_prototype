import { useCallback, useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { documentApi } from '../api/documentApi';
import { publicationApi, PublicationStatus } from '../api/publicationApi';

export function PublicationPanel({ transactionId, canManage = false }: { transactionId: number; canManage?: boolean }) {
  const { t, i18n } = useTranslation('documents');
  const [value, setValue] = useState<PublicationStatus | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const load = useCallback(async () => {
    setError(null);
    try { setValue((await publicationApi.status(transactionId)).data); }
    catch (cause) { setError(cause instanceof Error ? cause.message : t('publication.loadError')); }
  }, [transactionId, t]);
  useEffect(() => { void load(); }, [load]);

  async function mutate(action: 'regenerate' | 'resend') {
    setBusy(true); setError(null);
    try { setValue((await publicationApi[action](transactionId)).data); }
    catch (cause) { setError(cause instanceof Error ? cause.message : t('publication.actionError')); }
    finally { setBusy(false); }
  }
  async function download() {
    if (!value?.pdf.documentId || !value.pdf.fileName) return;
    setBusy(true); setError(null);
    try { await documentApi.download({ documentId: value.pdf.documentId, originalFileName: value.pdf.fileName }); }
    catch (cause) { setError(cause instanceof Error ? cause.message : t('errors.download')); }
    finally { setBusy(false); }
  }
  const latestEmail = value?.emails[0];
  return <section className="publication-panel" aria-busy={busy}>
    <div className="document-list-heading"><h3>{t('publication.title')}</h3>
      <button type="button" className="btn btn-secondary btn-sm" disabled={busy} onClick={() => void load()}>{t('publication.refresh')}</button>
    </div>
    {error && <div className="alert-banner alert-danger" role="alert">{error}</div>}
    {!value ? <p role="status">{t('states.loading')}</p> : <>
      <dl className="document-transaction-metadata">
        <div><dt>{t('publication.pdf')}</dt><dd>{t(`publication.pdfStatus.${value.pdf.status}`)}</dd></div>
        <div><dt>{t('publication.version')}</dt><dd>{value.pdf.version ?? '-'}</dd></div>
        <div><dt>{t('publication.email')}</dt><dd>{latestEmail ? t(`publication.emailStatus.${latestEmail.status}`) : t('publication.noEmail')}</dd></div>
      </dl>
      {value.pdf.issuedAt && <p>{t('publication.issuedAt')}: {new Date(value.pdf.issuedAt).toLocaleString(i18n.language)}</p>}
      {value.pdf.errorMessage && <p className="document-error">{value.pdf.errorMessage}</p>}
      {latestEmail?.errorMessage && <p className="document-error">{latestEmail.errorMessage}</p>}
      <div className="receiving-actions">
        <button type="button" className="btn btn-primary" disabled={busy || !value.pdf.documentId} onClick={() => void download()}>{t('publication.download')}</button>
        {canManage && <button type="button" className="btn btn-secondary" disabled={busy} onClick={() => void mutate('regenerate')}>{t('publication.regenerate')}</button>}
        {canManage && <button type="button" className="btn btn-secondary" disabled={busy} onClick={() => void mutate('resend')}>{t('publication.resend')}</button>}
      </div>
      {latestEmail && <p className="document-file-meta">{latestEmail.recipient} · {t(`publication.events.${latestEmail.eventType}`, { defaultValue: latestEmail.eventType })}</p>}
      <p className="document-file-meta">{t('publication.independentHint')}</p>
    </>}
  </section>;
}
