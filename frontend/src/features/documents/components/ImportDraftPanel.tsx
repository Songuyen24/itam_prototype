import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useAuth } from '@/features/auth/contexts/AuthContext';
import { httpClient } from '@/shared/api/httpClient';
import { ApiResponse } from '@/features/catalogs/types/catalog.types';
import { DocumentItem, DocumentTransaction } from '../types/document.types';
import { documentApi } from '../api/documentApi';

type AssetLine = { assetId: number; assetTag: string; name: string };
type Content = { notes?: string; assets: AssetLine[]; documents: DocumentItem[] };
type Revision = { revision: number; submittedAt: string; snapshot: Content };
type Option = { id: number; name: string };

export function ImportDraftPanel({ transaction, onChanged }: { transaction: DocumentTransaction; onChanged: () => void }) {
  const { t, i18n } = useTranslation('documents');
  const { user } = useAuth();
  const [content, setContent] = useState<Content | null>(null);
  const [revisions, setRevisions] = useState<Revision[]>([]);
  const [options, setOptions] = useState<Option[]>([]);
  const [notes, setNotes] = useState('');
  const [name, setName] = useState('');
  const [assetTag, setAssetTag] = useState('');
  const [serial, setSerial] = useState('');
  const [typeId, setTypeId] = useState('');
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const editor = user?.role === 'ADMIN' || user?.role === 'PUR_STAFF';
  const draft = editor && transaction.status === 'DRAFT';
  const base = `/v1/import-drafts/${transaction.transactionId}`;

  useEffect(() => {
    let active = true; setLoading(true);
    Promise.all([
      httpClient<ApiResponse<Content>>(`${base}/content`),
      httpClient<ApiResponse<Revision[]>>(`${base}/revisions`),
      draft ? httpClient<ApiResponse<Option[]>>('/v1/import-drafts/options') : Promise.resolve(null),
    ]).then(([current, history, types]) => {
      if (!active) return;
      setContent(current.data); setNotes(current.data.notes ?? ''); setRevisions(history.data);
      setOptions(types?.data ?? []);
    }).catch(cause => { if (active) setError(cause instanceof Error ? cause.message : t('errors.transaction')); })
      .finally(() => { if(active) setLoading(false); });
    return () => { active = false; };
  }, [base, draft, transaction.expectedVersion, t]);

  async function change(action: string, body: unknown, method = 'POST') {
    setBusy(true); setError(null);
    try {
      await httpClient(base + action, { method, body: JSON.stringify(body) });
      onChanged();
    } catch (cause) { setError(cause instanceof Error ? cause.message : t('errors.conflict')); }
    finally { setBusy(false); }
  }

  async function download(document: DocumentItem) {
    setBusy(true); setError(null);
    try { await documentApi.download(document); }
    catch (cause) { setError(cause instanceof Error ? cause.message : t('errors.download')); }
    finally { setBusy(false); }
  }

  if (loading) return <p role="status">{t('states.loading')}</p>;
  return <div className="document-draft-panel">
    {error && <div role="alert" className="alert-banner alert-danger">{error}</div>}
    {draft && <>
      <label className="form-label">{t('draft.notes')}
        <textarea className="form-control" value={notes} maxLength={4000} disabled={busy} onChange={e => setNotes(e.target.value)} />
      </label>
      <button className="btn btn-secondary" disabled={busy} onClick={() => void change('', { expectedVersion: transaction.expectedVersion, notes }, 'PUT')}>{t('draft.saveNotes')}</button>
      <form onSubmit={e => { e.preventDefault(); void change(`/hardware?expectedVersion=${transaction.expectedVersion}`, { name, assetTag, serialNumber: serial || undefined, typeId: Number(typeId) }); }}>
        <h3>{t('draft.addAsset')}</h3>
        <label className="form-label">{t('draft.assetName')}<input className="form-control" value={name} maxLength={255} required disabled={busy} onChange={e => setName(e.target.value)} /></label>
        <label className="form-label">{t('draft.assetType')}<select className="form-control" value={typeId} required disabled={busy} onChange={e => setTypeId(e.target.value)}>
          <option value="">—</option>{options.map(o => <option key={o.id} value={o.id}>{o.name}</option>)}
        </select></label>
        <label className="form-label">{t('draft.assetTag')}<input className="form-control" value={assetTag} maxLength={100} disabled={busy} onChange={e => setAssetTag(e.target.value)} /></label>
        <label className="form-label">{t('draft.serial')}<input className="form-control" value={serial} maxLength={255} disabled={busy} onChange={e => setSerial(e.target.value)} /></label>
        <button className="btn btn-secondary" disabled={busy}>{t('draft.addAsset')}</button>
      </form>
    </>}
    {content && <div><h3>{t('draft.assets')}</h3>
      {content.assets.length ? <ul>{content.assets.map(a => <li key={a.assetId}>{a.assetTag} — {a.name}</li>)}</ul> : <p>{t('draft.noAssets')}</p>}
    </div>}
    {draft && <button className="btn btn-primary" disabled={busy || !content?.assets.length}
      onClick={() => void change('/submit', { expectedVersion: transaction.expectedVersion })}>{t('draft.submit')}</button>}
    {editor && transaction.status === 'PENDING' && <button className="btn btn-secondary" disabled={busy}
      onClick={() => void change('/withdraw', { expectedVersion: transaction.expectedVersion })}>{t('draft.withdraw')}</button>}
    {editor && transaction.status === 'REJECTED' && <button className="btn btn-secondary" disabled={busy} onClick={() => {
      setBusy(true); setError(null);
      void httpClient('/v1/import-drafts', {method:'POST',body:JSON.stringify({sourceId:transaction.transactionId})})
        .then(() => {onChanged();}).catch(cause => setError(cause.message)).finally(() => setBusy(false));
    }}>{t('draft.copyRejected')}</button>}
    <h3>{t('draft.history')}</h3>
    {revisions.length === 0 && <p>{t('draft.noHistory')}</p>}
    {revisions.map(r => <details key={r.revision}>
      <summary>{t('draft.revision', { number: r.revision })} · {new Date(r.submittedAt).toLocaleString(i18n.language)}</summary>
      <p>{r.snapshot.notes}</p>
      <ul>{r.snapshot.assets.map(a => <li key={a.assetId}>{a.assetTag} — {a.name}</li>)}</ul>
      <ul>{r.snapshot.documents.map(d => <li key={d.documentId}>
        {d.originalFileName} · {t(`documentTypes.${d.documentType}`)} · {d.uploadedByName}
        <button className="btn btn-secondary btn-sm" disabled={busy} onClick={() => void download(d)}>{t('actions.download')}</button>
      </li>)}</ul>
    </details>)}
  </div>;
}
