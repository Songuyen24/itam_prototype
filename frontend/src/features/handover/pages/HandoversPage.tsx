import { useEffect, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useAuth } from '@/features/auth/contexts/AuthContext';
import { documentApi } from '@/features/documents/api/documentApi';
import { ReceivingDateRangePicker } from '@/features/documents/components/ReceivingDateRangePicker';
import { DocumentTransaction } from '@/features/documents/types/document.types';
import type { TransactionStatus } from '@/features/documents/types/document.types';
import { Holder, HolderDetail, Handover, handoverApi } from '../api/handoverApi';
import { HandoverDetails } from '../components/HandoverDetails';
import { HandoverForm } from '../components/HandoverForm';
import './handover.css';

export function HandoversPage() {
  const { t, i18n } = useTranslation('handover');
  const { user } = useAuth();
  const [heldOnly, setHeldOnly] = useState(true);
  const [holderKeyword, setHolderKeyword] = useState('');
  const [holderPage, setHolderPage] = useState(0);
  const [holderPages, setHolderPages] = useState(0);
  const [holderTotal, setHolderTotal] = useState(0);
  const [receiptKeyword, setReceiptKeyword] = useState('');
  const [receiptStatus, setReceiptStatus] = useState<TransactionStatus | ''>('');
  const [receiptFrom, setReceiptFrom] = useState('');
  const [receiptTo, setReceiptTo] = useState('');
  const [receiptPage, setReceiptPage] = useState(0);
  const [receiptPages, setReceiptPages] = useState(0);
  const [receiptTotal, setReceiptTotal] = useState(0);
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
  const [holderError, setHolderError] = useState('');
  const [receiptError, setReceiptError] = useState('');
  const [holderDetailError, setHolderDetailError] = useState('');
  const [receiptDetailError, setReceiptDetailError] = useState('');
  const [holderLoading, setHolderLoading] = useState(false);
  const [receiptLoading, setReceiptLoading] = useState(false);
  const [holderDetailLoading, setHolderDetailLoading] = useState(false);
  const [receiptDetailLoading, setReceiptDetailLoading] = useState(false);
  const [acting, setActing] = useState(false);
  const [formBusy, setFormBusy] = useState(false);
  const [holderReload, setHolderReload] = useState(0);
  const [receiptReload, setReceiptReload] = useState(0);
  const dialogRef = useRef<HTMLDialogElement>(null);
  const confirmRef = useRef<HTMLDialogElement>(null);
  const holderDialogRef = useRef<HTMLDialogElement>(null);
  const receiptDialogRef = useRef<HTMLDialogElement>(null);
  useEffect(() => {
    let active = true; setHolderLoading(true); setHolderError('');
    handoverApi.holders(holderKeyword, holderPage, heldOnly && !holderKeyword.trim()).then(r => {
      if (!active) return;
      setHolderPages(r.data.totalPages); setHolderTotal(r.data.totalElements); setHolderPage(current => Math.min(current, Math.max(0, r.data.totalPages - 1)));
      setHolders(r.data.content);
    }).catch((e: unknown) => { if (active) setHolderError(e instanceof Error ? e.message : t('error')); })
      .finally(() => { if (active) setHolderLoading(false); });
    return () => { active = false; };
  }, [holderKeyword, holderPage, heldOnly, holderReload, t]);
  useEffect(() => {
    let active = true; setReceiptLoading(true); setReceiptError('');
    documentApi.getTransactions({ type: 'HANDOVER', keyword: receiptKeyword, status: receiptStatus || undefined, from: receiptFrom || undefined, to: receiptTo || undefined, page: receiptPage, size: 10 }).then(r => {
      if (!active) return;
      setReceiptPages(r.data.totalPages); setReceiptTotal(r.data.totalElements); setReceiptPage(current => Math.min(current, Math.max(0, r.data.totalPages - 1)));
      setItems(r.data.content);
    }).catch((e: unknown) => { if (active) setReceiptError(e instanceof Error ? e.message : t('error')); })
      .finally(() => { if (active) setReceiptLoading(false); });
    return () => { active = false; };
  }, [receiptKeyword, receiptStatus, receiptFrom, receiptTo, receiptPage, receiptReload, t]);
  useEffect(() => {
    if (holderId == null) return;
    let active = true; setHolderDetailLoading(true); setHolderDetailError(''); setHolderDetail(null);
    handoverApi.holder(holderId, heldPage).then(r => { if (active) { setHolderDetail(r.data); setHeldPage(p => Math.min(p, Math.max(0, r.data.assets.totalPages - 1))); } })
      .catch(e => { if (active) setHolderDetailError(e instanceof Error ? e.message : t('error')); })
      .finally(() => { if (active) setHolderDetailLoading(false); });
    return () => { active = false; };
  }, [holderId, heldPage, holderReload, t]);
  useEffect(() => {
    if (selectedId == null) return;
    let active = true; setReceiptDetailLoading(true); setReceiptDetailError(''); setDetail(null);
    handoverApi.detail(selectedId).then(r => { if (active) setDetail(r.data); })
      .catch(e => { if (active) setReceiptDetailError(e instanceof Error ? e.message : t('error')); })
      .finally(() => { if (active) setReceiptDetailLoading(false); });
    return () => { active = false; };
  }, [selectedId, receiptReload, t]);
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
  useEffect(() => {
    const dialog = holderDialogRef.current;
    if (holderId == null || !dialog) return;
    const opener = document.activeElement;
    dialog.showModal();
    return () => { dialog.close(); if (opener instanceof HTMLElement && opener.isConnected) opener.focus(); };
  }, [holderId]);
  useEffect(() => {
    const dialog = receiptDialogRef.current;
    if (selectedId == null || !dialog) return;
    const opener = document.activeElement;
    dialog.showModal();
    return () => { dialog.close(); if (opener instanceof HTMLElement && opener.isConnected) opener.focus(); };
  }, [selectedId]);
  function saved(value: Handover) {
    setFormBusy(false); setCreating(false); setEditing(false); setDetail(value); setSelectedId(value.transactionId);
    setHolderId(null); setHolderDetail(null);
    setHolderReload(r => r + 1); setReceiptReload(r => r + 1);
  }
  async function act(action: 'submit' | 'withdraw' | 'cancel' | 'confirm') {
    if (!detail?.transactionId || acting) return;
    if (action === 'cancel' && !window.confirm(t('cancelConfirm'))) return;
    setActing(true); setReceiptDetailError('');
    try {
      const result = await (action === 'submit' ? handoverApi.submit(detail.transactionId) : action === 'withdraw'
        ? handoverApi.withdraw(detail.transactionId) : action === 'cancel'
          ? handoverApi.cancel(detail.transactionId) : handoverApi.confirm(detail.transactionId));
      setDetail(result.data); setConfirming(false); setHolderReload(r => r + 1); setReceiptReload(r => r + 1);
    } catch (e) { setReceiptDetailError(e instanceof Error ? e.message : t('error')); }
    finally { setActing(false); }
  }
  const owner = user?.id === detail?.requesterId;
  const admin = user?.role === 'ADMIN';
  return <div className="handover-page">
    <div className="handover-page-header"><h1>{t('title')}</h1><button className="btn btn-primary" onClick={() => setCreating(true)}>{t('create')}</button></div>
    <div className="handover-history">
      <section className="handover-list-section">
        <h2>{t('holders')}</h2>
        <div className="handover-list-filters"><label className="form-label">{t('searchHolders')}<input className="form-input" type="search" value={holderKeyword} onChange={e => { setHolderKeyword(e.target.value); setHolderPage(0); }} /></label>
          <label className="form-label">{t('holderFilter')}<select className="form-select" value={heldOnly ? 'held' : 'all'} onChange={e => { setHeldOnly(e.target.value === 'held'); setHolderPage(0); }}><option value="held">{t('heldOnly')}</option><option value="all">{t('allEmployees')}</option></select></label></div>
        {holderLoading ? <p role="status">{t('loading')}</p> : holderError ? <p role="alert">{holderError} <button onClick={() => setHolderReload(r => r + 1)}>{t('retry')}</button></p>
          : <div className="table-container"><table className="data-table"><thead><tr><th>{t('name')}</th><th>{t('employeeCode')}</th><th>{t('email')}</th><th>{t('devices')}</th><th>{t('components')}</th><th>{t('licenseSeats')}</th><th>{t('action')}</th></tr></thead><tbody>{holders.map(holder => <tr key={holder.id}><td title={holder.fullName}>{holder.fullName}</td><td title={holder.employeeCode || undefined}>{holder.employeeCode || '—'}</td><td title={holder.email || undefined}>{holder.email || '—'}</td><td>{holder.deviceCount}</td><td>{holder.componentCount}</td><td>{holder.licenseSeats}</td><td><button className="btn btn-secondary" onClick={() => { setSelectedId(null); setHolderId(holder.id); setHeldPage(0); }}>{t('view')}</button></td></tr>)}</tbody></table>{holders.length === 0 && <p>{t('empty')}</p>}</div>}
        <div className="handover-pagination"><button className="btn btn-secondary" disabled={holderLoading || holderPage === 0} onClick={() => setHolderPage(p => p - 1)}>{t('previous')}</button>
          <span>{t('documents:pagination.summary', { page: holderPages ? holderPage + 1 : 0, pages: holderPages, count: holderTotal })}</span><button className="btn btn-secondary" disabled={holderLoading || holderPage + 1 >= holderPages} onClick={() => setHolderPage(p => p + 1)}>{t('next')}</button></div>
      </section>
      <section className="handover-list-section">
        <h2>{t('list')}</h2>
        <div className="handover-list-filters"><label className="form-label">{t('searchTransactions')}<input className="form-input" type="search" value={receiptKeyword} onChange={e => { setReceiptKeyword(e.target.value); setReceiptPage(0); }} /></label>
          <label className="form-label">{t('statusLabel')}<select className="form-select" value={receiptStatus} onChange={e => { setReceiptStatus(e.target.value as TransactionStatus | ''); setReceiptPage(0); }}><option value="">{t('allStatuses')}</option>{(['DRAFT','PENDING','COMPLETED','CANCELLED','REJECTED'] as const).map(status => <option key={status} value={status}>{t(`status.${status}`)}</option>)}</select></label>
          <ReceivingDateRangePicker from={receiptFrom} to={receiptTo} label={t('createdRange')} onChange={(from,to) => { setReceiptFrom(from); setReceiptTo(to); setReceiptPage(0); }} /></div>
        {receiptLoading ? <p role="status">{t('loading')}</p> : receiptError ? <p role="alert">{receiptError} <button onClick={() => setReceiptReload(r => r + 1)}>{t('retry')}</button></p>
          : <div className="table-container"><table className="data-table"><thead><tr><th>{t('code')}</th><th>{t('statusLabel')}</th><th>{t('createdAt')}</th><th>{t('requester')}</th><th>{t('action')}</th></tr></thead><tbody>{items.map(item => <tr key={item.transactionId}><td>{item.transactionCode}</td><td>{t(`status.${item.status}`)}</td><td>{new Date(item.createdAt).toLocaleDateString(i18n.language)}</td><td title={item.requesterName || item.processedByName || undefined}>{item.requesterName || item.processedByName || '—'}</td><td><button className="btn btn-secondary" onClick={() => { setHolderId(null); setSelectedId(item.transactionId); }}>{t('view')}</button></td></tr>)}</tbody></table>{items.length === 0 && <p>{t('empty')}</p>}</div>}
        <div className="handover-pagination"><button className="btn btn-secondary" disabled={receiptLoading || receiptPage === 0} onClick={() => setReceiptPage(p => p - 1)}>{t('previous')}</button>
          <span>{t('documents:pagination.summary', { page: receiptPages ? receiptPage + 1 : 0, pages: receiptPages, count: receiptTotal })}</span><button className="btn btn-secondary" disabled={receiptLoading || receiptPage + 1 >= receiptPages} onClick={() => setReceiptPage(p => p + 1)}>{t('next')}</button></div>
      </section>
    </div>
    {holderId !== null && <dialog ref={holderDialogRef} className="handover-confirm handover-detail-dialog" aria-label={t('holders')} onCancel={event => { if (creating || formBusy) event.preventDefault(); else { setHolderId(null); setHolderDetail(null); } }}>
      <div className="handover-modal-header"><h2>{t('holders')}</h2><button className="btn btn-secondary" disabled={creating || formBusy} onClick={() => { setHolderId(null); setHolderDetail(null); }}>{t('close')}</button></div>
      <section>{holderDetailLoading ? <p role="status">{t('loading')}</p> : holderDetailError ? <p role="alert">{holderDetailError} <button onClick={() => setHolderReload(r => r + 1)}>{t('retry')}</button></p> : null}
        {!holderDetailLoading && holderDetail && <div className="handover-holder-detail">
          <h2>{holderDetail.holder.fullName}</h2><p>{holderDetail.holder.employeeCode || holderDetail.holder.email}</p>
          <p>{t('deviceCount', { count: holderDetail.holder.deviceCount })} · {t('componentCount', { count: holderDetail.holder.componentCount })} · {t('licenseSeatCount', { count: holderDetail.holder.licenseSeats })}</p>
          {holderDetail.assets.content.length === 0 ? <p>{t('noHoldings')}</p> : <div className="table-container"><table className="data-table"><thead><tr><th>{t('assetTag')}</th><th>{t('assetName')}</th><th>{t('category')}</th><th>{t('serial')}</th><th>{t('deviceTag')}</th><th>{t('seats')}</th></tr></thead><tbody>{holderDetail.assets.content.map(asset => <tr key={`${asset.category}-${asset.assetId}-${asset.allocationId ?? ''}`}><td>{asset.assetTag}</td><td>{asset.name}</td><td>{t(`categories.${asset.category}`)}</td><td>{asset.serialNumber || '—'}</td><td>{asset.deviceTag || '—'}</td><td>{asset.category === 'LICENSE' ? asset.seats : '—'}</td></tr>)}</tbody></table></div>}
          <div className="handover-pagination"><button className="btn btn-secondary" disabled={heldPage === 0} onClick={() => setHeldPage(p => p - 1)}>{t('previous')}</button>
            <span>{t('documents:pagination.summary', { page: holderDetail.assets.totalPages ? heldPage + 1 : 0, pages: holderDetail.assets.totalPages, count: holderDetail.assets.totalElements })}</span><button className="btn btn-secondary" disabled={heldPage + 1 >= holderDetail.assets.totalPages} onClick={() => setHeldPage(p => p + 1)}>{t('next')}</button></div>
          <button className="btn btn-primary" disabled={holderDetail.holder.accountStatus !== 'ACTIVE'} onClick={() => setCreating(true)}>{t('create')}</button>
        </div>}
      </section>
    </dialog>}
    {selectedId !== null && <dialog ref={receiptDialogRef} className="handover-confirm handover-detail-dialog" aria-label={t('list')} onCancel={event => { if (acting || editing || confirming || formBusy) event.preventDefault(); else { setSelectedId(null); setDetail(null); } }}>
      <div className="handover-modal-header"><h2>{t('list')}</h2><button className="btn btn-secondary" disabled={acting || editing || confirming || formBusy} onClick={() => { setSelectedId(null); setDetail(null); }}>{t('close')}</button></div>
      <section>{receiptDetailLoading ? <p role="status">{t('loading')}</p> : receiptDetailError ? <p role="alert">{receiptDetailError} <button onClick={() => setReceiptReload(r => r + 1)}>{t('retry')}</button></p> : null}
        {!receiptDetailLoading && detail && <>
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
      </section>
    </dialog>}
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
