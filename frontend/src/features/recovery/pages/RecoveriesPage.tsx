import { PaginatedContent } from '@/shared/components/PaginatedContent';
import { useEffect, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useAuth } from '@/features/auth/contexts/AuthContext';
import { PublicationPanel } from '@/features/documents/components/PublicationPanel';
import { ReceivingDateRangePicker } from '@/features/documents/components/ReceivingDateRangePicker';
import { documentApi } from '@/features/documents/api/documentApi';
import type { DocumentTransaction, TransactionStatus } from '@/features/documents/types/document.types';
import { RecoveryForm } from '../components/RecoveryForm';
import { Recovery, recoveryApi } from '../api/recoveryApi';
import './recoveries.css';

export function RecoveriesPage() {
  const { t, i18n } = useTranslation('recovery');
  const { user } = useAuth();
  const [keyword, setKeyword] = useState('');
  const [status, setStatus] = useState<TransactionStatus | ''>('');
  const [from, setFrom] = useState('');
  const [to, setTo] = useState('');
  const [page, setPage] = useState(0);
  const [pages, setPages] = useState(0);
  const [total, setTotal] = useState(0);
  const [history, setHistory] = useState<DocumentTransaction[]>([]);
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [detail, setDetail] = useState<Recovery | null>(null);
  const [loading, setLoading] = useState(false);
  const [detailLoading, setDetailLoading] = useState(false);
  const [creating, setCreating] = useState(false);
  const [formBusy, setFormBusy] = useState(false);
  const [acting, setActing] = useState(false);
  const [editing, setEditing] = useState(false);
  const [confirming, setConfirming] = useState(false);
  const [error, setError] = useState('');
  const [detailError, setDetailError] = useState('');
  const [reload, setReload] = useState(0);
  const confirmRef = useRef<HTMLDialogElement>(null);
  const formRef = useRef<HTMLDialogElement>(null);
  const detailRef = useRef<HTMLDialogElement>(null);

  useEffect(() => {
    let active = true;
    setLoading(true); setError('');
    documentApi.getTransactions({ type: 'RECOVERY', keyword, status: status || undefined, from: from || undefined, to: to || undefined, page, size: 20 }).then(r => {
      if (active) { setHistory(r.data.content); setPages(r.data.totalPages); setTotal(r.data.totalElements); setPage(current => Math.min(current, Math.max(0, r.data.totalPages - 1))); }
    }).catch(e => { if (active) setError(e instanceof Error ? e.message : t('error')); })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [keyword, status, from, to, page, reload, t]);

  useEffect(() => {
    if (selectedId == null) return;
    let active = true;
    setDetail(null); setDetailError(''); setDetailLoading(true);
    recoveryApi.detail(selectedId).then(r => { if (active) setDetail(r.data); })
      .catch(e => { if (active) setDetailError(e instanceof Error ? e.message : t('error')); })
      .finally(() => { if (active) setDetailLoading(false); });
    return () => { active = false; };
  }, [selectedId, reload, t]);

  useEffect(() => {
    const dialog = confirmRef.current;
    if (!confirming || !dialog) return;
    const opener = document.activeElement;
    dialog.showModal();
    return () => { dialog.close(); if (opener instanceof HTMLElement && opener.isConnected) opener.focus(); };
  }, [confirming]);

  useEffect(() => {
    const dialog = formRef.current;
    if ((!creating && !editing) || !dialog) return;
    const opener = document.activeElement;
    dialog.showModal();
    return () => { dialog.close(); if (opener instanceof HTMLElement && opener.isConnected) opener.focus(); };
  }, [creating, editing]);
  useEffect(() => {
    const dialog = detailRef.current;
    if (selectedId == null || !dialog) return;
    const opener = document.activeElement;
    dialog.showModal();
    return () => { dialog.close(); if (opener instanceof HTMLElement && opener.isConnected) opener.focus(); };
  }, [selectedId]);

  function saved(result: Recovery) {
    setFormBusy(false); setCreating(false); setEditing(false); setSelectedId(result.transactionId); setDetail(result); setReload(n => n + 1);
  }

  async function act(action: 'submit' | 'withdraw' | 'cancel' | 'confirm') {
    if (!detail?.transactionId || acting) return;
    if (action === 'cancel' && !window.confirm(t('cancelConfirm'))) return;
    setActing(true); setDetailError('');
    try {
      const r = await (action === 'submit' ? recoveryApi.submit(detail.transactionId)
        : action === 'withdraw' ? recoveryApi.withdraw(detail.transactionId)
          : action === 'cancel' ? recoveryApi.cancel(detail.transactionId) : recoveryApi.confirm(detail.transactionId));
      setDetail(r.data); setConfirming(false); setReload(n => n + 1);
    } catch (e) { setDetailError(e instanceof Error ? e.message : t('error')); }
    finally { setActing(false); }
  }

  const owner = user?.id === detail?.creatorUserId;
  const admin = user?.role === 'ADMIN';
  return <div className="page-container recovery-page">
    <div className="page-header recovery-page-header"><h1>{t('title')}</h1><button type="button" className="btn btn-primary" onClick={() => setCreating(true)}>{t('tabForm')}</button></div>
    <div className="page-content">
      <div className="recovery-list-filters">
        <label className="form-label">{t('searchHistory')}<input className="form-input" type="search" value={keyword} onChange={e => { setKeyword(e.target.value); setPage(0); }} /></label>
        <label className="form-label">{t('filterStatus')}<select className="form-input" value={status} onChange={e => { setStatus(e.target.value as TransactionStatus | ''); setPage(0); }}>
          <option value="">{t('allStatuses')}</option>{['DRAFT', 'PENDING', 'COMPLETED', 'CANCELLED'].map(s => <option key={s} value={s}>{t(`status.${s}`)}</option>)}
        </select></label>
        <ReceivingDateRangePicker from={from} to={to} label={t('createdRange')} onChange={(nextFrom,nextTo) => { setFrom(nextFrom); setTo(nextTo); setPage(0); }} />
      </div>
      {loading ? <p role="status">{t('loading')}</p> : error ? <p role="alert">{error} <button type="button" onClick={() => setReload(n => n + 1)}>{t('retry')}</button></p>
        : <div className="table-container"><table className="data-table"><thead><tr>
          <th>{t('colCode')}</th><th>{t('creator')}</th><th>{t('createdAt')}</th><th>{t('filterStatus')}</th><th>{t('colActions')}</th>
        </tr></thead><tbody>{history.map(r => <tr key={r.transactionId}><td>{r.transactionCode}</td><td>{r.requesterName || '—'}</td><td>{new Date(r.createdAt).toLocaleDateString(i18n.language)}</td><td>{t(`status.${r.status}`)}</td>
          <td><button type="button" className="btn btn-secondary btn-sm" onClick={() => setSelectedId(r.transactionId)}>{t('details')}</button></td></tr>)}</tbody></table>{history.length === 0 && <p>{t('noHistory')}</p>}</div>}
      <div className="recovery-pagination"><button className="btn btn-secondary" disabled={loading || page === 0} onClick={() => setPage(n => n - 1)}>{t('previous')}</button>
        <span>{t('documents:pagination.summary', { page: pages ? page + 1 : 0, pages, count: total })}</span><button className="btn btn-secondary" disabled={loading || page + 1 >= pages} onClick={() => setPage(n => n + 1)}>{t('next')}</button></div>
      {selectedId !== null && <dialog ref={detailRef} className="recovery-dialog" aria-label={t('details')} onCancel={e => { if (acting || editing || confirming || formBusy) e.preventDefault(); else { setSelectedId(null); setDetail(null); } }}>
      <div className="recovery-dialog-heading"><h2>{t('details')}</h2><button type="button" className="btn btn-secondary" disabled={acting || editing || confirming || formBusy} onClick={() => { setSelectedId(null); setDetail(null); }}>{t('back')}</button></div>
      {detailLoading ? <p role="status">{t('loading')}</p> : detailError ? <p role="alert">{detailError} <button type="button" onClick={() => setReload(n => n + 1)}>{t('retry')}</button></p> : null}
      {detail && <section><h2>{detail.transactionCode} · {t(`status.${detail.status}`)}</h2>
        <p>{detail.returnerName} · {detail.receivingLocationName} · {detail.recoveryDate}</p><p>{detail.reason}</p>
        <PaginatedContent key={detail.transactionId} items={detail.lines ?? []}>{lines => <ul>{lines.map(line => <li key={line.assetId}>{line.assetTag} · {line.name} · {line.category}</li>)}</ul>}</PaginatedContent>
        <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
          {detail.status === 'DRAFT' && (owner || admin) && <><button className="btn btn-secondary" disabled={acting} onClick={() => setEditing(true)}>{t('edit')}</button><button className="btn btn-primary" disabled={acting} onClick={() => void act('submit')}>{t('submit')}</button><button className="btn btn-secondary" disabled={acting} onClick={() => void act('cancel')}>{t('cancel')}</button></>}
          {detail.status === 'PENDING' && (owner || admin) && <button className="btn btn-secondary" disabled={acting} onClick={() => void act('withdraw')}>{t('withdraw')}</button>}
          {detail.status === 'PENDING' && admin && <button className="btn btn-secondary" disabled={acting} onClick={() => void act('cancel')}>{t('cancel')}</button>}
          {detail.status === 'PENDING' && (admin || user?.role === 'IT_STAFF') && <button className="btn btn-primary" disabled={acting} onClick={() => setConfirming(true)}>{t('confirmPhysical')}</button>}
        </div>
        {detail.status === 'COMPLETED' && detail.transactionId != null && <PublicationPanel transactionId={detail.transactionId} canManage />}
      </section>}
      </dialog>}
      {(creating || editing && detail) && <dialog ref={formRef} className="recovery-dialog" aria-labelledby="recovery-edit-title" onCancel={e => { if (formBusy) e.preventDefault(); else { setCreating(false); setEditing(false); } }}>
        <div className="recovery-dialog-heading"><h2 id="recovery-edit-title">{t(editing ? 'edit' : 'tabForm')}</h2><button type="button" className="btn btn-secondary" disabled={formBusy} onClick={() => { setCreating(false); setEditing(false); }}>{t('back')}</button></div>
        <RecoveryForm initialValue={editing ? detail ?? undefined : undefined} onCompleted={saved} onBusyChange={setFormBusy} />
      </dialog>}
      {confirming && detail && <dialog ref={confirmRef} aria-labelledby="recovery-confirm-title" onCancel={e => { if (acting) e.preventDefault(); else setConfirming(false); }} style={{ margin: 'auto', padding: 24, width: 'min(700px, calc(100vw - 32px))' }}>
        <h2 id="recovery-confirm-title">{t('confirmPhysical')}</h2><p>{t('confirmPhysicalHint')}</p>
        <p>{detail.transactionCode} · {detail.returnerName} · {detail.receivingLocationName}</p>
        <PaginatedContent key={detail.transactionId} items={detail.lines ?? []}>{lines => <ul>{lines.map(line => <li key={line.assetId}>{line.assetTag} · {line.name}</li>)}</ul>}</PaginatedContent>
        <button className="btn btn-secondary" disabled={acting} onClick={() => setConfirming(false)}>{t('back')}</button>
        <button className="btn btn-primary" disabled={acting} onClick={() => void act('confirm')}>{acting ? t('loading') : t('confirmPhysical')}</button>
      </dialog>}
    </div>
  </div>;
}
