import { useEffect, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useAuth } from '@/features/auth/contexts/AuthContext';
import { documentApi } from '@/features/documents/api/documentApi';
import { DocumentTransaction } from '@/features/documents/types/document.types';
import { Holder, HolderDetail, Handover, handoverApi } from '../api/handoverApi';
import { HandoverDetails } from '../components/HandoverDetails';
import { HandoverForm } from '../components/HandoverForm';
import './handover.css';

export function HandoversPage() {
  const { t } = useTranslation('handover');
  const { user } = useAuth();
  const [tab, setTab] = useState<'holders' | 'list'>('holders');
  const [heldOnly, setHeldOnly] = useState(true);
  const [keyword, setKeyword] = useState('');
  const [page, setPage] = useState(0);
  const [pages, setPages] = useState(0);
  const [total, setTotal] = useState(0);
  const [holders, setHolders] = useState<Holder[]>([]);
  const [items, setItems] = useState<DocumentTransaction[]>([]);
  const [holderId, setHolderId] = useState<number | null>(null);
  const [heldPage, setHeldPage] = useState(0);
  const [holderDetail, setHolderDetail] = useState<HolderDetail | null>(null);
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [detail, setDetail] = useState<Handover | null>(null);
  const [creating, setCreating] = useState(false);
  const [editing, setEditing] = useState(false);
  const [confirming, setConfirming] = useState(false);
  const [error, setError] = useState('');
  const [detailError, setDetailError] = useState('');
  const [loading, setLoading] = useState(false);
  const [detailLoading, setDetailLoading] = useState(false);
  const [acting, setActing] = useState(false);
  const [formBusy, setFormBusy] = useState(false);
  const [reload, setReload] = useState(0);
  const dialogRef = useRef<HTMLDialogElement>(null);
  const confirmRef = useRef<HTMLDialogElement>(null);
  useEffect(() => {
    let active = true; setLoading(true); setError('');
    const request = tab === 'holders' ? handoverApi.holders(keyword, page, heldOnly && !keyword.trim()) : documentApi.getTransactions({ type: 'HANDOVER', keyword, page, size: 10 });
    request.then(r => {
      if (!active) return;
      setPages(r.data.totalPages); setTotal(r.data.totalElements); setPage(current => Math.min(current, Math.max(0, r.data.totalPages - 1)));
      if (tab === 'holders') setHolders(r.data.content as Holder[]);
      else setItems(r.data.content as DocumentTransaction[]);
    }).catch((e: unknown) => { if (active) setError(e instanceof Error ? e.message : t('error')); })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [tab, keyword, page, heldOnly, reload, t]);
  useEffect(() => {
    const id = tab === 'holders' ? holderId : selectedId;
    if (id == null) return;
    let active = true; setDetailLoading(true); setDetailError('');
    if (tab === 'holders') setHolderDetail(null); else setDetail(null);
    const request = tab === 'holders' ? handoverApi.holder(id, heldPage) : handoverApi.detail(id);
    request.then(r => {
      if (!active) return;
      if (tab === 'holders') { setHolderDetail(r.data as HolderDetail); setHeldPage(p => Math.min(p, Math.max(0, (r.data as HolderDetail).assets.totalPages - 1))); }
      else setDetail(r.data as Handover);
    }).catch((e: unknown) => { if (active) setDetailError(e instanceof Error ? e.message : t('error')); })
      .finally(() => { if (active) setDetailLoading(false); });
    return () => { active = false; };
  }, [tab, holderId, heldPage, selectedId, reload, t]);
  useEffect(() => {
    const dialog = dialogRef.current;
    if ((!creating && !editing) || !dialog) return;
    const opener = document.activeElement;
    dialog.showModal();
    return () => { dialog.close(); if (opener instanceof HTMLElement && opener.isConnected) opener.focus(); };
  }, [creating, editing]);
  useEffect(() => {
    const dialog = confirmRef.current;
    if (!confirming || !dialog) return;
    const opener = document.activeElement;
    dialog.showModal();
    return () => { dialog.close(); if (opener instanceof HTMLElement && opener.isConnected) opener.focus(); };
  }, [confirming]);
  function changeTab(value: 'holders' | 'list') {
    setTab(value); setKeyword(''); setPage(0); setPages(0); setError(''); setDetailError('');
  }
  function saved(value: Handover) {
    setFormBusy(false); setCreating(false); setEditing(false); setDetail(value); setSelectedId(value.transactionId); changeTab('list'); setReload(r => r + 1);
  }
  async function act(action: 'submit' | 'withdraw' | 'cancel' | 'confirm') {
    if (!detail?.transactionId || acting) return;
    if (action === 'cancel' && !window.confirm(t('cancelConfirm'))) return;
    setActing(true); setDetailError('');
    try {
      const result = await (action === 'submit' ? handoverApi.submit(detail.transactionId) : action === 'withdraw'
        ? handoverApi.withdraw(detail.transactionId) : action === 'cancel'
          ? handoverApi.cancel(detail.transactionId) : handoverApi.confirm(detail.transactionId));
      setDetail(result.data); setConfirming(false); setReload(r => r + 1);
    } catch (e) { setDetailError(e instanceof Error ? e.message : t('error')); }
    finally { setActing(false); }
  }
  const owner = user?.id === detail?.requesterId;
  const admin = user?.role === 'ADMIN';
  return <div className="handover-page">
    <div className="handover-page-header"><h1>{t('title')}</h1><button className="btn btn-primary" onClick={() => setCreating(true)}>{t('create')}</button></div>
    <div className="handover-pagination" role="tablist" aria-label={t('title')}>
      <button role="tab" className="btn btn-secondary" aria-selected={tab === 'holders'} onClick={() => changeTab('holders')}>{t('holders')}</button>
      <button role="tab" className="btn btn-secondary" aria-selected={tab === 'list'} onClick={() => changeTab('list')}>{t('list')}</button>
    </div>
    <div className="handover-history">
      <section>
        {tab === 'holders' && <div className="handover-pagination" role="group" aria-label={t('holderFilter')}>
          <button className="btn btn-secondary" aria-pressed={heldOnly} onClick={() => { setHeldOnly(true); setPage(0); }}>{t('heldOnly')}</button>
          <button className="btn btn-secondary" aria-pressed={!heldOnly} onClick={() => { setHeldOnly(false); setPage(0); }}>{t('allEmployees')}</button>
        </div>}
        <label className="form-label">{t(tab === 'holders' ? 'searchHolders' : 'searchTransactions')}
          <input className="form-input" type="search" value={keyword} onChange={e => { setKeyword(e.target.value); setPage(0); }} /></label>
        {loading ? <p role="status">{t('loading')}</p> : error ? <p role="alert">{error} <button onClick={() => setReload(r => r + 1)}>{t('retry')}</button></p>
          : tab === 'holders' ? holders.length === 0 ? <p>{t('empty')}</p> : holders.map(holder => <button key={holder.id} className="handover-holder-row"
            aria-pressed={holder.id === holderId} onClick={() => { setHolderId(holder.id); setHeldPage(0); }}>
            <strong>{holder.fullName}</strong><span>{holder.employeeCode || holder.email}</span>
            <span>{t('deviceCount', { count: holder.deviceCount })} · {t('componentCount', { count: holder.componentCount })} · {t('licenseSeatCount', { count: holder.licenseSeats })}</span>
          </button>)
            : items.length === 0 ? <p>{t('empty')}</p> : items.map(item => <button key={item.transactionId} className="document-transaction-row" aria-pressed={item.transactionId === selectedId} onClick={() => setSelectedId(item.transactionId)}>
              <strong>{item.transactionCode}</strong><span>{t(`status.${item.status}`)}</span><span>{item.requesterName || item.processedByName}</span>
            </button>)}
        <div className="handover-pagination"><button className="btn btn-secondary" disabled={loading || page === 0} onClick={() => setPage(p => p - 1)}>{t('previous')}</button>
          <span>{t('documents:pagination.summary', { page: pages ? page + 1 : 0, pages, count: total })}</span><button className="btn btn-secondary" disabled={loading || page + 1 >= pages} onClick={() => setPage(p => p + 1)}>{t('next')}</button></div>
      </section>
      <section>{detailLoading ? <p role="status">{t('loading')}</p> : detailError ? <p role="alert">{detailError} <button onClick={() => setReload(r => r + 1)}>{t('retry')}</button></p> : null}
        {!detailLoading && tab === 'holders' && holderDetail && <div className="handover-holder-detail">
          <h2>{holderDetail.holder.fullName}</h2><p>{holderDetail.holder.employeeCode || holderDetail.holder.email}</p>
          <p>{t('deviceCount', { count: holderDetail.holder.deviceCount })} · {t('componentCount', { count: holderDetail.holder.componentCount })} · {t('licenseSeatCount', { count: holderDetail.holder.licenseSeats })}</p>
          {holderDetail.assets.content.length === 0 ? <p>{t('noHoldings')}</p> : <ul>{holderDetail.assets.content.map(asset => <li key={`${asset.category}-${asset.assetId}-${asset.allocationId ?? ''}`}>
            <strong>{asset.assetTag}</strong> · {asset.name} · {t(`categories.${asset.category}`)}{asset.serialNumber ? ` · ${t('serial')}: ${asset.serialNumber}` : ''}
            {asset.category === 'LICENSE' ? ` · ${t('licenseSeatCount', { count: asset.seats })}` : ''}{asset.deviceTag ? ` · ${t('withParent', { id: asset.deviceTag })}` : ''}
          </li>)}</ul>}
          <div className="handover-pagination"><button className="btn btn-secondary" disabled={heldPage === 0} onClick={() => setHeldPage(p => p - 1)}>{t('previous')}</button>
            <span>{t('documents:pagination.summary', { page: holderDetail.assets.totalPages ? heldPage + 1 : 0, pages: holderDetail.assets.totalPages, count: holderDetail.assets.totalElements })}</span><button className="btn btn-secondary" disabled={heldPage + 1 >= holderDetail.assets.totalPages} onClick={() => setHeldPage(p => p + 1)}>{t('next')}</button></div>
          <button className="btn btn-primary" disabled={holderDetail.holder.accountStatus !== 'ACTIVE'} onClick={() => setCreating(true)}>{t('create')}</button>
        </div>}
        {!detailLoading && tab === 'holders' && !holderDetail && !detailError && <p>{t('selectHolder')}</p>}
        {!detailLoading && tab === 'list' && detail && <>
          <p><strong>{t(`status.${detail.status}`)}</strong> · {detail.requesterName}</p><HandoverDetails value={detail} />
          <div className="handover-pagination">
            {detail.status === 'DRAFT' && (owner || admin) && <><button className="btn btn-secondary" disabled={acting} onClick={() => setEditing(true)}>{t('edit')}</button>
              <button className="btn btn-primary" disabled={acting} onClick={() => void act('submit')}>{t('submit')}</button>
              <button className="btn btn-secondary" disabled={acting} onClick={() => void act('cancel')}>{t('cancel')}</button></>}
            {detail.status === 'PENDING' && (owner || admin) && <button className="btn btn-secondary" disabled={acting} onClick={() => void act('withdraw')}>{t('withdraw')}</button>}
            {detail.status === 'PENDING' && admin && <button className="btn btn-secondary" disabled={acting} onClick={() => void act('cancel')}>{t('cancel')}</button>}
            {detail.status === 'PENDING' && (user?.role === 'IT_STAFF' || admin) && <button className="btn btn-primary" disabled={acting} onClick={() => setConfirming(true)}>{t('confirmPhysical')}</button>}
          </div>
          {acting && <p role="status">{t('loading')}</p>}
        </>}
        {!detailLoading && tab === 'list' && !detail && !detailError && <p>{t('selectTransaction')}</p>}
      </section>
    </div>
    {(creating || editing && detail) && <dialog ref={dialogRef} className="handover-confirm handover-create-dialog" aria-labelledby="handover-create-title" onCancel={event => { if (formBusy) event.preventDefault(); else { setCreating(false); setEditing(false); } }}>
      <div className="handover-modal-header"><h2 id="handover-create-title">{editing ? t('edit') : holderDetail?.holder.accountStatus === 'ACTIVE' ? t('createFor', { name: holderDetail.holder.fullName }) : t('create')}</h2>
        <button type="button" className="btn btn-secondary" disabled={formBusy} onClick={() => { setCreating(false); setEditing(false); }}>{t('close')}</button></div>
      <HandoverForm initialRecipient={editing && detail ? { id: detail.recipientUserId, fullName: detail.recipientName, email: detail.recipientEmail, employeeCode: detail.recipientEmployeeCode, accountStatus: 'ACTIVE' } : holderDetail?.holder.accountStatus === 'ACTIVE' ? holderDetail.holder : undefined}
        initialValue={editing ? detail ?? undefined : undefined} onCompleted={saved} onBusyChange={setFormBusy} />
    </dialog>}
    {confirming && detail && <dialog ref={confirmRef} className="handover-confirm" aria-labelledby="physical-confirm-title" onCancel={event => { if (acting) event.preventDefault(); else setConfirming(false); }}>
      <h2 id="physical-confirm-title">{t('confirmPhysical')}</h2><p>{t('physicalConfirmHint')}</p>
      <HandoverDetails value={detail} />
      <div className="handover-pagination"><button className="btn btn-secondary" disabled={acting} onClick={() => setConfirming(false)}>{t('back')}</button>
        <button className="btn btn-primary" disabled={acting} onClick={() => void act('confirm')}>{acting ? t('loading') : t('confirmPhysical')}</button></div>
    </dialog>}
  </div>;
}
