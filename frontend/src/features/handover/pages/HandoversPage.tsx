import VisibilityOutlinedIcon from '@mui/icons-material/VisibilityOutlined';
import { TableActionButton } from '@/shared/components/TableActionButton';
import { useEffect, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useAuth } from '@/features/auth/contexts/AuthContext';
import { ApiError } from '@/shared/api/httpClient';
import { WorkflowGroupList } from '@/shared/components/WorkflowGroupList';
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
  const [selectedGroups, setSelectedGroups] = useState<string[]>([]);
  const [cancelReason, setCancelReason] = useState('');
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
    Promise.all([handoverApi.detail(selectedId), handoverApi.progress(selectedId)]).then(([r, p]) => { if (active) setDetail({ ...r.data, ...p.data }); })
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
  async function editConflict(error: ApiError) {
    setEditing(false);
    setReceiptDetailError(`${t('changed')} ${error.message}`);
    if (selectedId == null) return;
    try {
      const [current, progress] = await Promise.all([handoverApi.detail(selectedId), handoverApi.progress(selectedId)]);
      setDetail({ ...current.data, ...progress.data });
    } catch { /* Keep the conflict visible. */ }
  }
  async function act(action: 'submit' | 'withdraw' | 'cancel' | 'confirm' | 'cancelRemaining') {
    if (!detail?.transactionId || acting) return;
    if (action === 'cancel' && !window.confirm(t('cancelConfirm'))) return;
    if (action === 'confirm' && detail.groups?.length && !selectedGroups.length) return;
    if (action === 'cancelRemaining' && (!cancelReason.trim() || !selectedGroups.length)) return;
    setActing(true); setReceiptDetailError('');
    try {
      const result = await (action === 'submit' ? handoverApi.submit(detail.transactionId, detail.contentVersion!) : action === 'withdraw'
        ? handoverApi.withdraw(detail.transactionId, detail.contentVersion!) : action === 'cancel'
          ? handoverApi.cancel(detail.transactionId, detail.contentVersion!) : action === 'cancelRemaining'
            ? handoverApi.cancelRemaining(detail.transactionId, { expectedVersion: detail.contentVersion!, groupIds: selectedGroups, reason: cancelReason.trim() })
            : handoverApi.confirm(detail.transactionId, { expectedVersion: detail.contentVersion!, groupIds: selectedGroups }));
      const progress = await handoverApi.progress(detail.transactionId);
      setDetail({ ...result.data, ...progress.data }); setConfirming(false); setSelectedGroups([]); setCancelReason(''); setHolderReload(r => r + 1); setReceiptReload(r => r + 1);
    } catch (e) {
      setReceiptDetailError(e instanceof ApiError && e.status === 409 ? `${t('changed')} ${e.message}` : e instanceof Error ? e.message : t('error'));
      if (e instanceof ApiError && e.status === 409) {
        try {
          const [current, progress] = await Promise.all([handoverApi.detail(detail.transactionId), handoverApi.progress(detail.transactionId)]);
          setDetail({ ...current.data, ...progress.data }); setSelectedGroups([]);
        } catch { /* Keep the conflict visible. */ }
      }
    }
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
          <label className="form-label">{t('holderFilter')}<select className="form-select" value={heldOnly ? 'held' : 'all'} onChange={e => { setHeldOnly(e.target.value === 'held'); setHolderPage(0); }}><option value="held">{t('heldOnly')}</option><option value="all">{t('allEmployees')}</option></select></label><button className="btn btn-secondary" onClick={() => { setHolderKeyword(''); setHeldOnly(true); setHolderPage(0); }}>{t('resetFilters')}</button></div>
        {holderKeyword.trim() && <p className="handover-filter-hint">{t('holderSearchScope')}</p>}
        <div className="table-container">{holderLoading ? <p className="handover-list-state" role="status">{t('loading')}</p> : holderError ? <p className="handover-list-state" role="alert">{holderError} <button className="btn btn-secondary" onClick={() => setHolderReload(r => r + 1)}>{t('retry')}</button></p>
          : <><table className="data-table handover-holders-table"><thead><tr><th>{t('name')}</th><th>{t('employeeCode')}</th><th>{t('email')}</th><th>{t('devices')}</th><th>{t('components')}</th><th>{t('licenseSeats')}</th><th>{t('action')}</th></tr></thead><tbody>{holders.map(holder => <tr key={holder.id}><td title={holder.fullName}><button className="handover-row-link" onClick={() => { setSelectedId(null); setHolderId(holder.id); setHeldPage(0); }}>{holder.fullName}</button></td><td title={holder.employeeCode || undefined}>{holder.employeeCode || '—'}</td><td title={holder.email || undefined}>{holder.email || '—'}</td><td>{holder.deviceCount}</td><td>{holder.componentCount}</td><td>{holder.licenseSeats}</td><td><TableActionButton icon={<VisibilityOutlinedIcon />} label={t('view')} onClick={() => { setSelectedId(null); setHolderId(holder.id); setHeldPage(0); }}/></td></tr>)}</tbody></table>{holders.length === 0 && <p className="handover-list-state">{t('empty')}</p>}</>}</div>
        {!holderLoading && !holderError && <div className="handover-pagination"><button className="btn btn-secondary" disabled={holderPage === 0} onClick={() => setHolderPage(p => p - 1)}>{t('previous')}</button>
          <span>{t('documents:pagination.summary', { page: holderPages ? holderPage + 1 : 0, pages: holderPages, count: holderTotal })}</span><button className="btn btn-secondary" disabled={holderPage + 1 >= holderPages} onClick={() => setHolderPage(p => p + 1)}>{t('next')}</button></div>}
      </section>
      <section className="handover-list-section">
        <h2>{t('list')}</h2>
        <div className="handover-list-filters"><label className="form-label">{t('searchTransactions')}<input className="form-input" type="search" value={receiptKeyword} onChange={e => { setReceiptKeyword(e.target.value); setReceiptPage(0); }} /></label>
          <label className="form-label">{t('statusLabel')}<select className="form-select" value={receiptStatus} onChange={e => { setReceiptStatus(e.target.value as TransactionStatus | ''); setReceiptPage(0); }}><option value="">{t('allStatuses')}</option>{(['DRAFT','PENDING','PARTIAL','COMPLETED','CANCELLED','REJECTED'] as const).map(status => <option key={status} value={status}>{t(`status.${status}`)}</option>)}</select></label>
          <ReceivingDateRangePicker from={receiptFrom} to={receiptTo} label={t('createdRange')} onChange={(from,to) => { setReceiptFrom(from); setReceiptTo(to); setReceiptPage(0); }} /><button className="btn btn-secondary" onClick={() => { setReceiptKeyword(''); setReceiptStatus(''); setReceiptFrom(''); setReceiptTo(''); setReceiptPage(0); }}>{t('resetFilters')}</button></div>
        <div className="table-container">{receiptLoading ? <p className="handover-list-state" role="status">{t('loading')}</p> : receiptError ? <p className="handover-list-state" role="alert">{receiptError} <button className="btn btn-secondary" onClick={() => setReceiptReload(r => r + 1)}>{t('retry')}</button></p>
          : <><table className="data-table"><thead><tr><th>{t('code')}</th><th>{t('statusLabel')}</th><th>{t('createdAt')}</th><th>{t('requester')}</th><th>{t('action')}</th></tr></thead><tbody>{items.map(item => <tr key={item.transactionId}><td><button className="handover-row-link" onClick={() => { setHolderId(null); setSelectedId(item.transactionId); }}>{item.transactionCode}</button></td><td><span className={`handover-status handover-status-${item.status.toLowerCase()}`}>{t(`status.${item.status}`)}</span></td><td>{new Date(item.createdAt).toLocaleDateString(i18n.language)}</td><td title={item.requesterName || item.processedByName || undefined}>{item.requesterName || item.processedByName || '—'}</td><td><TableActionButton icon={<VisibilityOutlinedIcon />} label={t('view')} onClick={() => { setHolderId(null); setSelectedId(item.transactionId); }}/></td></tr>)}</tbody></table>{items.length === 0 && <p className="handover-list-state">{t('empty')}</p>}</>}</div>
        {!receiptLoading && !receiptError && <div className="handover-pagination"><button className="btn btn-secondary" disabled={receiptPage === 0} onClick={() => setReceiptPage(p => p - 1)}>{t('previous')}</button>
          <span>{t('documents:pagination.summary', { page: receiptPages ? receiptPage + 1 : 0, pages: receiptPages, count: receiptTotal })}</span><button className="btn btn-secondary" disabled={receiptPage + 1 >= receiptPages} onClick={() => setReceiptPage(p => p + 1)}>{t('next')}</button></div>}
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
            {detail.status === 'PENDING' && (owner || admin) && detail.groups?.every(group => group.status === 'PENDING') && <button className="btn btn-secondary" disabled={acting} onClick={() => void act('withdraw')}>{t('withdraw')}</button>}
            {detail.status === 'PENDING' && admin && detail.groups?.every(group => group.status === 'PENDING') && <button className="btn btn-secondary" disabled={acting} onClick={() => void act('cancel')}>{t('cancel')}</button>}
            {(detail.status === 'PENDING' || detail.status === 'PARTIAL') && detail.groups?.some(group => group.status === 'PENDING') && (user?.role === 'IT_STAFF' || admin) && <button className="btn btn-primary" disabled={acting} onClick={() => { setSelectedGroups([]); setConfirming(true); }}>{t('confirmPhysical')}</button>}
          </div>
          {acting && <p role="status">{t('loading')}</p>}
        </>}
      </section>
    </dialog>}
    {(creating || editing && detail) && <dialog ref={dialogRef} className="handover-confirm handover-create-dialog" aria-labelledby="handover-create-title" onCancel={event => { if (formBusy) event.preventDefault(); else { setCreating(false); setEditing(false); } }}>
      <div className="handover-modal-header"><h2 id="handover-create-title">{editing ? t('edit') : holderDetail?.holder.accountStatus === 'ACTIVE' ? t('createFor', { name: holderDetail.holder.fullName }) : t('create')}</h2>
        <button type="button" className="btn btn-secondary" disabled={formBusy} onClick={() => { setCreating(false); setEditing(false); }}>{t('close')}</button></div>
      <HandoverForm initialRecipient={editing && detail ? { id: detail.recipientUserId, fullName: detail.recipientName, email: detail.recipientEmail, employeeCode: detail.recipientEmployeeCode, accountStatus: 'ACTIVE' } : holderDetail?.holder.accountStatus === 'ACTIVE' ? holderDetail.holder : undefined}
        initialValue={editing ? detail ?? undefined : undefined} onCompleted={saved} onConflict={error => void editConflict(error)} onBusyChange={setFormBusy} />
    </dialog>}
    {confirming && detail && <dialog ref={confirmRef} className="handover-confirm" aria-labelledby="physical-confirm-title" onCancel={event => { if (acting) event.preventDefault(); else setConfirming(false); }}>
      <h2 id="physical-confirm-title">{t('confirmPhysical')}</h2><p>{t('physicalConfirmHint')}</p>
      <WorkflowGroupList groups={detail.groups ?? []} lines={detail.proposalLines ?? detail.lines} selected={selectedGroups} onSelect={setSelectedGroups} statusLabel={status => t(`groupStatus.${status}`)} reasonLabel={t('groupReason')} />
      {admin && <label className="form-label">{t('cancelRemainingReason')}<textarea className="form-textarea" value={cancelReason} onChange={event => setCancelReason(event.target.value)} /></label>}
      <div className="handover-pagination"><button className="btn btn-secondary" disabled={acting} onClick={() => setConfirming(false)}>{t('back')}</button>
        {admin && <button className="btn btn-secondary" disabled={acting || !selectedGroups.length || !cancelReason.trim()} onClick={() => void act('cancelRemaining')}>{t('cancelRemaining')}</button>}
        <button className="btn btn-primary" disabled={acting || !selectedGroups.length} onClick={() => void act('confirm')}>{acting ? t('loading') : t('confirmPhysical')}</button></div>
    </dialog>}
  </div>;
}
