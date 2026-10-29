import { FormEvent, useEffect, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useAuth } from '@/features/auth/contexts/AuthContext';
import { ApiError } from '@/shared/api/httpClient';
import { documentApi } from '@/features/documents/api/documentApi';
import type { DocumentTransaction, TransactionStatus } from '@/features/documents/types/document.types';
import { disposalApi, Disposal, DisposalAsset, DisposalCheck } from '../api/disposalApi';
import { DisposalDetail, DisposalReview } from '../components/DisposalReview';
import './disposal.css';

export function DisposalsPage() {
  const { t } = useTranslation('disposal');
  const { user } = useAuth();
  const [assets, setAssets] = useState<DisposalAsset[]>([]);
  const [creating, setCreating] = useState(false);
  const [assetKeyword, setAssetKeyword] = useState('');
  const [assetPage, setAssetPage] = useState(0);
  const [assetPages, setAssetPages] = useState(0);
  const [assetTotal, setAssetTotal] = useState(0);
  const [selected, setSelected] = useState<number[]>([]);
  const [editingId,setEditingId] = useState<number|null>(null);
  const [editingFingerprint,setEditingFingerprint] = useState('');
  const [reload, setReload] = useState(0);
  const [reason, setReason] = useState('');
  const [date, setDate] = useState(new Date().toISOString().slice(0, 10));
  const [assetLoading, setAssetLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [createReview, setCreateReview] = useState<DisposalCheck | null>(null);
  const [detail, setDetail] = useState<Disposal | null>(null);
  const [detailLoading, setDetailLoading] = useState(false);
  const [decisions, setDecisions] = useState<Record<number, boolean>>({});
  const [action, setAction] = useState<'approve' | 'reject' | null>(null);
  const [rejectReason, setRejectReason] = useState('');
  const detailRequest = useRef(0);
  const createTrigger = useRef<HTMLElement | null>(null);
  const detailTrigger = useRef<HTMLElement | null>(null);
  const formTrigger = useRef<HTMLElement | null>(null);
  const formDialog = useRef<HTMLDialogElement>(null);

  useEffect(() => {
    if (!creating) return;
    let active = true;
    setAssetLoading(true); setAssets([]);
    disposalApi.candidates(assetKeyword, assetPage).then(result => {
      if (!active) return;
      const lastPage = Math.max(0, result.data.totalPages - 1);
      setAssetPages(result.data.totalPages); setAssetTotal(result.data.totalElements);
      if (assetPage > lastPage) { setAssetPage(lastPage); return; }
      setAssets(result.data.content);
    }).catch(cause => { if (active) setError(cause instanceof Error ? cause.message : t('error')); })
      .finally(() => { if (active) setAssetLoading(false); });
    return () => { active = false; };
  }, [creating, assetKeyword, assetPage, reload, t]);

  useEffect(() => {
    const dialog = formDialog.current;
    if (!creating || !dialog || typeof document === 'undefined') return;
    const opener = formTrigger.current ?? (document.activeElement instanceof HTMLElement ? document.activeElement : null);
    dialog.showModal();
    return () => { dialog.close(); if (opener?.isConnected) opener.focus(); };
  }, [creating]);

  const closeForm = () => {
    if (saving || createReview) return;
    setCreating(false); setEditingId(null); setEditingFingerprint(''); setSelected([]); setReason(''); setDate(new Date().toISOString().slice(0, 10)); setAssetKeyword(''); setAssetPage(0); setError('');
  };

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    createTrigger.current = ((event.nativeEvent as SubmitEvent | undefined)?.submitter as HTMLElement | null) ?? null;
    setSaving(true); setError(''); setNotice('');
    try { setCreateReview((await disposalApi.smartCheck(selected)).data); }
    catch (cause) { setError(cause instanceof Error ? cause.message : t('error')); }
    finally { setSaving(false); }
  };

  const create = async (draft = false) => {
    if (!createReview) return;
    setSaving(true); setError('');
    try {
      const body={ assetIds: selected, reason, disposalDate: date, expectedFingerprint: createReview.fingerprint, expectedTransactionFingerprint: editingFingerprint };
      if(editingId) await disposalApi.edit(editingId,body); else if (draft) await disposalApi.createDraft(body); else await disposalApi.create(body);
      setNotice(t(editingId || draft ? 'savedDraft' : 'created')); setCreateReview(null); setCreating(false); setEditingId(null); setEditingFingerprint(''); setSelected([]); setReason(''); setDate(new Date().toISOString().slice(0, 10)); setAssetKeyword(''); setAssetPage(0); setReload(value => value + 1);
    } catch (cause) { setError(cause instanceof Error ? cause.message : t('error')); }
    finally { setSaving(false); }
  };

  const showDetail = async (id: number, preserveError = false, opener?: HTMLElement) => {
    if (opener) detailTrigger.current = opener;
    const request = ++detailRequest.current;
    setDetailLoading(true); if (!preserveError) setError(''); setAction(null); setRejectReason('');
    try {
      const next = (await disposalApi.detail(id)).data;
      if (request === detailRequest.current) {
        setDetail(next);
        setDecisions(Object.fromEntries(next.perUserLinks.map(link => [link.allocationId, true])));
      }
    } catch (cause) { if (request === detailRequest.current) setError(cause instanceof Error ? cause.message : t('error')); }
    finally { if (request === detailRequest.current) setDetailLoading(false); }
  };

  const mutateDetail = async (mutation: (value: Disposal) => Promise<Disposal>) => {
    if (!detail || !['PENDING','DRAFT'].includes(detail.status)) return;
    setSaving(true); setError('');
    try {
      const next = await mutation(detail);
      if (next.status === 'PENDING' || next.status === 'DRAFT') {
        setDetail(next);
        setDecisions(Object.fromEntries(next.perUserLinks.map(link => [link.allocationId, true])));
        setAction(null); setRejectReason('');
        setReload(count=>count+1);
      } else {
        setDetail(null);
        setReload(count => count + 1);
      }
    } catch (cause) {
      setError(cause instanceof ApiError && cause.code === 'DISPOSAL_CHANGED' ? t('changed') : cause instanceof Error ? cause.message : t('error'));
      if (cause instanceof ApiError && cause.code === 'DISPOSAL_CHANGED') await showDetail(detail.transactionId, true);
    } finally { setSaving(false); }
  };

  const resolvePerUser = () => mutateDetail(async value => (await disposalApi.resolvePerUser(value.transactionId, value.fingerprint,
    value.perUserLinks.map(link => ({ allocationId: link.allocationId, release: decisions[link.allocationId] !== false })))).data);
  const approve = () => mutateDetail(async value => {
    return (await disposalApi.approve(value.transactionId, value.fingerprint)).data;
  });
  const reject = () => mutateDetail(async value => {
    return (await disposalApi.reject(value.transactionId, rejectReason.trim())).data;
  });
  const closeDetail = () => { detailRequest.current += 1; setDetail(null); setAction(null); };

  const loading = creating && assetLoading;
  return <div className="t18-page disposal-page">
    <header className="page-header disposal-page-header"><div><h1 className="page-title">{t('title')}</h1><p className="page-description">{t('description')}</p></div>{user?.role === 'IT_STAFF' && <button type="button" className="btn btn-primary" onClick={event => { formTrigger.current = event.currentTarget; setCreating(true); }}>{t('createTitle')}</button>}</header>
    <p role="status" className="sr-status">{loading || detailLoading ? t('loading') : t('ready')}</p>
    {error && !creating && <div className="alert-banner alert-danger" role="alert">{error}</div>}
    {notice && <div className="alert-banner alert-success">{notice}</div>}
    <div className="t18-columns">
      {creating && <dialog ref={formDialog} className="disposal-form-dialog" aria-label={t(editingId ? 'editDraft' : 'createTitle')} onCancel={event => { if (saving || createReview) event.preventDefault(); else closeForm(); }}>
        <div className="disposal-form-heading"><h2>{t(editingId ? 'editDraft' : 'createTitle')}{editingId ? ` #${editingId}` : ''}</h2><button type="button" className="btn btn-secondary" disabled={saving || !!createReview} onClick={closeForm}>{t('close')}</button></div>
        {error && <div className="alert-banner alert-danger" role="alert">{error}</div>}
        <form onSubmit={submit}>
          <label className="form-group"><span className="form-label">{t('searchAssets')}</span><input className="form-input" type="search" value={assetKeyword} onChange={event => { setAssetKeyword(event.target.value); setAssetPage(0); }} /></label>
          <div className="candidate-list" aria-label={t('assets')} aria-busy={assetLoading}>
            {!assetLoading && assets.length === 0 && <p className="empty-state">{t('empty')}</p>}
            {assets.map(asset => <label className="candidate-row" key={asset.assetId}>
              <input type="checkbox" checked={selected.includes(asset.assetId)} onChange={() => setSelected(current => current.includes(asset.assetId) ? current.filter(id => id !== asset.assetId) : [...current, asset.assetId])} />
              <span><strong>{asset.assetTag}</strong><small>{asset.name} · {t(`categories.${asset.category}`, { defaultValue: asset.category })}</small></span>
            </label>)}
          </div>
          <p>{t('documents:pagination.summary', { page: assetPages ? assetPage + 1 : 0, pages: assetPages, count: assetTotal })}</p><Pagination page={assetPage} pages={assetPages} loading={assetLoading} previous={t('previous')} next={t('next')} onPage={setAssetPage} />
          <p className="selection-count">{t('selectedCount', { count: selected.length })}</p>
          <label className="form-group"><span className="form-label required">{t('reason')}</span><textarea className="form-textarea" required value={reason} onChange={event => setReason(event.target.value)} /></label>
          <label className="form-group"><span className="form-label required">{t('date')}</span><input className="form-input" type="date" required value={date} onChange={event => setDate(event.target.value)} /></label>
          <button className="btn btn-primary" disabled={saving || selected.length === 0}>{saving ? t('saving') : t('smartCheck')}</button>
        </form>
      </dialog>}
      <div className="disposal-receipt-groups">
        <DisposalReceiptTable group="PENDING" reload={reload} detailLoading={detailLoading} onView={showDetail} />
        <DisposalReceiptTable group="DRAFT" reload={reload} detailLoading={detailLoading} onView={showDetail} />
        <DisposalReceiptTable group="FINAL" reload={reload} detailLoading={detailLoading} onView={showDetail} />
      </div>
    </div>
    {createReview && <DisposalReview value={createReview} editing={!!editingId} saving={saving} error={error} restoreFocusRef={createTrigger} onCancel={() => setCreateReview(null)} onConfirm={() => void create()} onSaveDraft={() => void create(true)} />}
    {detail && <DisposalDetail value={detail} role={user?.role} userId={user?.id} decisions={decisions} saving={saving} error={error} restoreFocusRef={detailTrigger} action={action} rejectReason={rejectReason}
      onTransition={action=>void mutateDetail(async value=>(await disposalApi.transition(value,action)).data)}
      onEdit={()=>{formTrigger.current=detailTrigger.current;setEditingId(detail.transactionId);setEditingFingerprint(detail.fingerprint);setSelected(detail.assets.filter(a=>!a.autoAdded).map(a=>a.assetId));setReason(detail.reason);setDate(detail.disposalDate);closeDetail();setCreating(true);}}
      onDecision={(id, release) => setDecisions(current => ({ ...current, [id]: release }))} onResolve={() => void resolvePerUser()}
      onChooseAction={setAction} onRejectReason={setRejectReason} onApprove={() => void approve()} onReject={() => void reject()} onClose={closeDetail} />}
  </div>;
}

function DisposalReceiptTable({ group, reload, detailLoading, onView }: {
  group: 'PENDING' | 'DRAFT' | 'FINAL'; reload: number; detailLoading: boolean;
  onView: (id: number, preserveError?: boolean, opener?: HTMLElement) => Promise<void>;
}) {
  const { t, i18n } = useTranslation('disposal');
  const [page, setPage] = useState(0);
  const [pages, setPages] = useState(0);
  const [total, setTotal] = useState(0);
  const [rows, setRows] = useState<DocumentTransaction[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [retry, setRetry] = useState(0);
  useEffect(() => {
    let active = true; setLoading(true); setError(''); setRows([]);
    documentApi.getTransactions({ type: 'DISPOSAL', ...(group === 'FINAL' ? { statuses: ['COMPLETED', 'REJECTED', 'CANCELLED'] as TransactionStatus[] } : { status: group }), page, size: 20 })
      .then(result => {
        if (!active) return;
        setPages(result.data.totalPages); setTotal(result.data.totalElements);
        const last = Math.max(0, result.data.totalPages - 1);
        if (page > last) { setPage(last); return; }
        setRows(result.data.content);
      }).catch(cause => { if (active) setError(cause instanceof Error ? cause.message : t('error')); })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [group, page, reload, retry, t]);
  return <section className="content-card t18-section disposal-receipt-table" aria-label={t(`groups.${group}`)}>
    <h2>{t(`groups.${group}`)}</h2>
    {loading ? <p role="status">{t('loading')}</p> : error ? <p role="alert">{error} <button type="button" className="btn btn-secondary" onClick={() => setRetry(value => value + 1)}>{t('retry')}</button></p> : <div className="table-container"><table className="data-table"><thead><tr><th>{t('code')}</th><th>{t('state')}</th><th>{t('createdAt')}</th><th>{t('requester')}</th><th>{t('actions')}</th></tr></thead><tbody>{rows.map(item => <tr key={item.transactionId}><td>{item.transactionCode}</td><td><span className={`disposal-status disposal-status-${item.status.toLowerCase()}`}>{t(`statuses.${item.status}`)}</span></td><td>{new Date(item.createdAt).toLocaleDateString(i18n.language)}</td><td>{item.requesterName || '—'}</td><td><button type="button" className="btn btn-secondary btn-sm" disabled={detailLoading} onClick={event => void onView(item.transactionId, false, event.currentTarget)}>{t('review')}</button></td></tr>)}</tbody></table>{rows.length === 0 && <p className="empty-state">{t('noRows')}</p>}</div>}
    <p>{t('documents:pagination.summary', { page: pages ? page + 1 : 0, pages, count: total })}</p>
    <Pagination page={page} pages={pages} loading={loading} previous={t('previous')} next={t('next')} onPage={setPage} />
  </section>;
}

function Pagination({ page, pages, loading, previous, next, onPage }: {
  page: number; pages: number; loading: boolean; previous: string; next: string; onPage: (page: number) => void;
}) {
  if (pages <= 1) return null;
  return <nav className="t18-pagination" aria-label={`${page + 1} / ${pages}`}>
    <button type="button" className="btn btn-secondary btn-sm" disabled={loading || page === 0} onClick={() => onPage(page - 1)}>{previous}</button>
    <span>{page + 1} / {pages}</span>
    <button type="button" className="btn btn-secondary btn-sm" disabled={loading || page + 1 >= pages} onClick={() => onPage(page + 1)}>{next}</button>
  </nav>;
}
