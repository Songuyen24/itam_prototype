import { PaginatedContent } from '@/shared/components/PaginatedContent';
import { useEffect, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useAuth } from '@/features/auth/contexts/AuthContext';
import { PublicationPanel } from '@/features/documents/components/PublicationPanel';
import { RecoveryForm } from '../components/RecoveryForm';
import { Recovery, recoveryApi } from '../api/recoveryApi';

export function RecoveriesPage() {
  const { t } = useTranslation('recovery');
  const { user } = useAuth();
  const [tab, setTab] = useState<'form' | 'history'>('form');
  const [keyword, setKeyword] = useState('');
  const [status, setStatus] = useState('');
  const [page, setPage] = useState(0);
  const [pages, setPages] = useState(0);
  const [total, setTotal] = useState(0);
  const [history, setHistory] = useState<Recovery[]>([]);
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [detail, setDetail] = useState<Recovery | null>(null);
  const [loading, setLoading] = useState(false);
  const [acting, setActing] = useState(false);
  const [editing, setEditing] = useState(false);
  const [confirming, setConfirming] = useState(false);
  const [error, setError] = useState('');
  const [detailError, setDetailError] = useState('');
  const [reload, setReload] = useState(0);
  const confirmRef = useRef<HTMLDialogElement>(null);

  useEffect(() => {
    if (tab !== 'history') return;
    let active = true;
    setLoading(true); setError('');
    recoveryApi.list(keyword, status, page).then(r => {
      if (active) { setHistory(r.data.content); setPages(r.data.totalPages); setTotal(r.data.totalElements); setPage(current => Math.min(current, Math.max(0, r.data.totalPages - 1))); }
    }).catch(e => { if (active) setError(e instanceof Error ? e.message : t('error')); })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [tab, keyword, status, page, reload, t]);

  useEffect(() => {
    if (selectedId == null) return;
    let active = true;
    setDetail(null); setDetailError('');
    recoveryApi.detail(selectedId).then(r => { if (active) setDetail(r.data); })
      .catch(e => { if (active) setDetailError(e instanceof Error ? e.message : t('error')); });
    return () => { active = false; };
  }, [selectedId, reload, t]);

  useEffect(() => {
    const dialog = confirmRef.current;
    if (!confirming || !dialog) return;
    const opener = document.activeElement;
    dialog.showModal();
    return () => { dialog.close(); if (opener instanceof HTMLElement && opener.isConnected) opener.focus(); };
  }, [confirming]);

  function saved(result: Recovery) {
    setEditing(false); setSelectedId(result.transactionId); setDetail(result); setTab('history'); setReload(n => n + 1);
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
  return <div className="page-container">
    <div className="page-header"><h1>{t('title')}</h1></div>
    <div className="page-tabs">
      <button className={tab === 'form' ? 'tab active' : 'tab'} onClick={() => { setTab('form'); setEditing(false); }}>{t('tabForm')}</button>
      <button className={tab === 'history' ? 'tab active' : 'tab'} onClick={() => setTab('history')}>{t('tabHistory')}</button>
    </div>
    {tab === 'form' ? <div className="page-content"><RecoveryForm onCompleted={saved} /></div> : <div className="page-content">
      <div style={{ display: 'flex', gap: 12, marginBottom: 16 }}>
        <label className="form-label">{t('searchHistory')}<input className="form-input" type="search" value={keyword} onChange={e => { setKeyword(e.target.value); setPage(0); }} /></label>
        <label className="form-label">{t('filterStatus')}<select className="form-input" value={status} onChange={e => { setStatus(e.target.value); setPage(0); }}>
          <option value="">{t('allStatuses')}</option>{['DRAFT', 'PENDING', 'COMPLETED', 'CANCELLED'].map(s => <option key={s} value={s}>{t(`status.${s}`)}</option>)}
        </select></label>
      </div>
      {loading ? <p role="status">{t('loading')}</p> : error ? <p role="alert">{error} <button type="button" onClick={() => setReload(n => n + 1)}>{t('retry')}</button></p>
        : history.length === 0 ? <p>{t('noHistory')}</p> : <table className="data-table"><thead><tr>
          <th>{t('colCode')}</th><th>{t('colReturner')}</th><th>{t('colDate')}</th><th>{t('filterStatus')}</th><th>{t('colActions')}</th>
        </tr></thead><tbody>{history.map(r => <tr key={r.transactionId}><td>{r.transactionCode}</td><td>{r.returnerName}</td><td>{r.recoveryDate}</td><td>{t(`status.${r.status}`)}</td>
          <td><button type="button" className="btn btn-secondary btn-sm" onClick={() => setSelectedId(r.transactionId)}>{t('details')}</button></td></tr>)}</tbody></table>}
      <div style={{ display: 'flex', gap: 8, alignItems: 'center' }}><button className="btn btn-secondary" disabled={loading || page === 0} onClick={() => setPage(n => n - 1)}>{t('previous')}</button>
        <span>{t('documents:pagination.summary', { page: pages ? page + 1 : 0, pages, count: total })}</span><button className="btn btn-secondary" disabled={loading || page + 1 >= pages} onClick={() => setPage(n => n + 1)}>{t('next')}</button></div>
      {detailError && <p role="alert">{detailError} <button type="button" onClick={() => setReload(n => n + 1)}>{t('retry')}</button></p>}
      {detail && <section style={{ marginTop: 20 }}><h2>{detail.transactionCode} · {t(`status.${detail.status}`)}</h2>
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
      {editing && detail && <dialog ref={el => { if (el && !el.open) el.showModal(); }} aria-labelledby="recovery-edit-title" onCancel={e => { e.preventDefault(); setEditing(false); }} style={{ margin: 'auto', width: 'min(1000px, calc(100vw - 32px))', maxHeight: 'calc(100vh - 32px)', overflowY: 'auto' }}>
        <h2 id="recovery-edit-title">{t('edit')}</h2><button type="button" className="btn btn-secondary" onClick={() => setEditing(false)}>{t('back')}</button>
        <RecoveryForm initialValue={detail} onCompleted={saved} />
      </dialog>}
      {confirming && detail && <dialog ref={confirmRef} aria-labelledby="recovery-confirm-title" onCancel={e => { if (acting) e.preventDefault(); else setConfirming(false); }} style={{ margin: 'auto', padding: 24, width: 'min(700px, calc(100vw - 32px))' }}>
        <h2 id="recovery-confirm-title">{t('confirmPhysical')}</h2><p>{t('confirmPhysicalHint')}</p>
        <p>{detail.transactionCode} · {detail.returnerName} · {detail.receivingLocationName}</p>
        <PaginatedContent key={detail.transactionId} items={detail.lines ?? []}>{lines => <ul>{lines.map(line => <li key={line.assetId}>{line.assetTag} · {line.name}</li>)}</ul>}</PaginatedContent>
        <button className="btn btn-secondary" disabled={acting} onClick={() => setConfirming(false)}>{t('back')}</button>
        <button className="btn btn-primary" disabled={acting} onClick={() => void act('confirm')}>{acting ? t('loading') : t('confirmPhysical')}</button>
      </dialog>}
    </div>}
  </div>;
}
