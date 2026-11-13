import VisibilityOutlinedIcon from '@mui/icons-material/VisibilityOutlined';
import { TableActionButton } from '@/shared/components/TableActionButton';
import { PaginatedContent } from '@/shared/components/PaginatedContent';
import { useEffect, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useAuth } from '@/features/auth/contexts/AuthContext';
import { ApiError } from '@/shared/api/httpClient';
import { WorkflowGroupList } from '@/shared/components/WorkflowGroupList';
import { PublicationPanel } from '@/features/documents/components/PublicationPanel';
import { ReceivingDateRangePicker } from '@/features/documents/components/ReceivingDateRangePicker';
import { documentApi } from '@/features/documents/api/documentApi';
import type { DocumentTransaction, TransactionStatus } from '@/features/documents/types/document.types';
import { RecoveryForm } from '../components/RecoveryForm';
import { Recovery, recoveryApi } from '../api/recoveryApi';
import './recoveries.css';

const categoryLabel = (t: (key: string) => string, category: string) =>
  t(`category${category[0]}${category.slice(1).toLowerCase()}`);

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
  const [selectedGroups, setSelectedGroups] = useState<string[]>([]);
  const [cancelReason, setCancelReason] = useState('');
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
  async function editConflict(error: ApiError) {
    setEditing(false);
    setDetailError(`${t('changed')} ${error.message}`);
    if (selectedId == null) return;
    try { setDetail((await recoveryApi.detail(selectedId)).data); } catch { /* Keep the conflict visible. */ }
  }

  async function act(action: 'submit' | 'withdraw' | 'cancel' | 'confirm' | 'cancelRemaining') {
    if (!detail?.transactionId || acting) return;
    if (action === 'cancel' && !window.confirm(t('cancelConfirm'))) return;
    if (action === 'confirm' && detail.groups?.length && !selectedGroups.length) return;
    if (action === 'cancelRemaining' && (!cancelReason.trim() || !selectedGroups.length)) return;
    setActing(true); setDetailError('');
    try {
      const r = await (action === 'submit' ? recoveryApi.submit(detail.transactionId, detail.contentVersion!)
        : action === 'withdraw' ? recoveryApi.withdraw(detail.transactionId, detail.contentVersion!)
          : action === 'cancel' ? recoveryApi.cancel(detail.transactionId, detail.contentVersion!)
            : action === 'cancelRemaining' ? recoveryApi.cancelRemaining(detail.transactionId, { expectedVersion: detail.contentVersion!, groupIds: selectedGroups, reason: cancelReason.trim() })
              : recoveryApi.confirm(detail.transactionId, { expectedVersion: detail.contentVersion!, groupIds: selectedGroups }));
      setDetail(r.data); setConfirming(false); setSelectedGroups([]); setCancelReason(''); setReload(n => n + 1);
    } catch (e) {
      setDetailError(e instanceof ApiError && e.status === 409 ? `${t('changed')} ${e.message}` : e instanceof Error ? e.message : t('error'));
      if (e instanceof ApiError && e.status === 409) {
        try { setDetail((await recoveryApi.detail(detail.transactionId)).data); setSelectedGroups([]); } catch { /* Keep the conflict visible. */ }
      }
    }
    finally { setActing(false); }
  }

  const owner = user?.id === detail?.creatorUserId;
  const admin = user?.role === 'ADMIN';
  return <div className="page-container recovery-page">
    <div className="page-header recovery-page-header"><h1>{t('title')}</h1><button type="button" className="btn btn-primary" onClick={() => setCreating(true)}>{t('tabForm')}</button></div>
    <div className="page-content">
      <div className="recovery-list-filters">
        <label className="form-label">{t('searchHistory')}<input className="form-input" type="search" value={keyword} onChange={e => { setKeyword(e.target.value); setPage(0); }} /></label>
        <label className="form-label">{t('filterStatus')}<select className="form-select" value={status} onChange={e => { setStatus(e.target.value as TransactionStatus | ''); setPage(0); }}>
          <option value="">{t('allStatuses')}</option>{['DRAFT', 'PENDING', 'PARTIAL', 'COMPLETED', 'CANCELLED'].map(s => <option key={s} value={s}>{t(`status.${s}`)}</option>)}
        </select></label>
        <ReceivingDateRangePicker from={from} to={to} label={t('createdRange')} onChange={(nextFrom,nextTo) => { setFrom(nextFrom); setTo(nextTo); setPage(0); }} />
        <button type="button" className="btn btn-secondary recovery-reset" onClick={() => { setKeyword(''); setStatus(''); setFrom(''); setTo(''); setPage(0); }}>{t('resetFilters')}</button>
      </div>
      {loading ? <div className="recovery-list-state" role="status">{t('loading')}</div> : error ? <div className="recovery-list-state" role="alert">{error} <button type="button" className="btn btn-secondary btn-sm" onClick={() => setReload(n => n + 1)}>{t('retry')}</button></div>
        : <div className="table-container recovery-table-wrap"><table className="data-table recovery-table"><thead><tr>
          <th>{t('colCode')}</th><th>{t('creator')}</th><th>{t('createdAt')}</th><th>{t('filterStatus')}</th><th>{t('colActions')}</th>
        </tr></thead><tbody>{history.map(r => <tr key={r.transactionId}><td><button type="button" className="recovery-code" onClick={() => setSelectedId(r.transactionId)}>{r.transactionCode}</button></td><td>{r.requesterName || '—'}</td><td>{new Date(r.createdAt).toLocaleDateString(i18n.language)}</td><td><span className={`recovery-status recovery-status-${r.status.toLowerCase()}`}>{t(`status.${r.status}`)}</span></td>
          <td><TableActionButton icon={<VisibilityOutlinedIcon />} label={t('details')} onClick={() => setSelectedId(r.transactionId)}/></td></tr>)}</tbody></table>{history.length === 0 && <div className="recovery-list-state">{t('noHistory')}</div>}</div>}
      {!loading && !error && pages > 0 && <div className="recovery-pagination"><button className="btn btn-secondary" disabled={page === 0} onClick={() => setPage(n => n - 1)}>{t('previous')}</button>
        <span>{t('documents:pagination.summary', { page: pages ? page + 1 : 0, pages, count: total })}</span><button className="btn btn-secondary" disabled={loading || page + 1 >= pages} onClick={() => setPage(n => n + 1)}>{t('next')}</button></div>
      }
      {selectedId !== null && <dialog ref={detailRef} className="recovery-dialog" aria-label={t('details')} onCancel={e => { if (acting || editing || confirming || formBusy) e.preventDefault(); else { setSelectedId(null); setDetail(null); } }}>
      <div className="recovery-dialog-heading"><h2>{t('details')}</h2><button type="button" className="btn btn-secondary" disabled={acting || editing || confirming || formBusy} onClick={() => { setSelectedId(null); setDetail(null); }}>{t('back')}</button></div><div className="recovery-dialog-body">
      {detailLoading ? <p role="status">{t('loading')}</p> : detailError ? <p role="alert">{detailError} <button type="button" onClick={() => setReload(n => n + 1)}>{t('retry')}</button></p> : null}
      {detail && <section><div className="recovery-detail-title"><h2>{detail.transactionCode}</h2>{detail.status && <span className={`recovery-status recovery-status-${detail.status.toLowerCase()}`}>{t(`status.${detail.status}`)}</span>}</div>
        <div className="recovery-detail-summary"><p><strong>{t('colReturner')}</strong><span>{detail.returnerName}</span></p><p><strong>{t('colLocation')}</strong><span>{detail.receivingLocationName}</span></p><p><strong>{t('colDate')}</strong><span>{detail.recoveryDate}</span></p></div><div className="recovery-detail-reason"><strong>{t('reason')}</strong><p>{detail.reason}</p></div>
        <div className="recovery-detail-lines"><h3>{t('assetsTitle')}</h3><PaginatedContent key={detail.transactionId} items={detail.lines ?? []}>{lines => <div className="recovery-detail-table-wrap"><table className="data-table recovery-detail-table"><thead><tr><th>{t('assetTag')}</th><th>{t('assetName')}</th><th>{t('assetCategory')}</th></tr></thead><tbody>{lines.map(line => <tr key={line.assetId}><td>{line.assetTag}</td><td>{line.name}</td><td>{categoryLabel(t, line.category)}</td></tr>)}</tbody></table></div>}</PaginatedContent>
          {!!detail.groups?.length && <WorkflowGroupList groups={detail.groups} lines={detail.proposalLines ?? detail.lines} selected={[]} onSelect={() => {}} selectable={null} statusLabel={status => t(`groupStatus.${status}`)} reasonLabel={t('groupReason')} />}</div>
        </section>}
      {detail?.status === 'COMPLETED' && detail.transactionId != null && <PublicationPanel transactionId={detail.transactionId} canManage />}
      </div>
      {detail && (detail.status === 'DRAFT' && (owner || admin) || (detail.status === 'PENDING' || detail.status === 'PARTIAL') && (owner || admin || user?.role === 'IT_STAFF')) &&
        <div className="recovery-dialog-footer">
          {detail.status === 'DRAFT' && (owner || admin) && <><button className="btn btn-secondary" disabled={acting} onClick={() => setEditing(true)}>{t('edit')}</button><button className="btn btn-primary" disabled={acting} onClick={() => void act('submit')}>{t('submit')}</button><button className="btn btn-secondary" disabled={acting} onClick={() => void act('cancel')}>{t('cancel')}</button></>}
          {detail.status === 'PENDING' && (owner || admin) && detail.groups?.every(group => group.status === 'PENDING') && <button className="btn btn-secondary" disabled={acting} onClick={() => void act('withdraw')}>{t('withdraw')}</button>}
          {detail.status === 'PENDING' && admin && detail.groups?.every(group => group.status === 'PENDING') && <button className="btn btn-secondary" disabled={acting} onClick={() => void act('cancel')}>{t('cancel')}</button>}
          {(detail.status === 'PENDING' || detail.status === 'PARTIAL') && detail.groups?.some(group => group.status === 'PENDING') && (admin || user?.role === 'IT_STAFF') && <button className="btn btn-primary" disabled={acting} onClick={() => { setSelectedGroups([]); setConfirming(true); }}>{t('confirmPhysical')}</button>}
        </div>}
      </dialog>}
      {(creating || editing && detail) && <dialog ref={formRef} className="recovery-dialog" aria-labelledby="recovery-edit-title" onCancel={e => { if (formBusy) e.preventDefault(); else { setCreating(false); setEditing(false); } }}>
        <div className="recovery-dialog-heading"><h2 id="recovery-edit-title">{t(editing ? 'edit' : 'tabForm')}</h2><button type="button" className="btn btn-secondary" disabled={formBusy} onClick={() => { setCreating(false); setEditing(false); }}>{t('back')}</button></div>
        <div className="recovery-dialog-body"><RecoveryForm initialValue={editing ? detail ?? undefined : undefined} onCompleted={saved} onConflict={error => void editConflict(error)} onBusyChange={setFormBusy} /></div>
      </dialog>}
      {confirming && detail && <dialog ref={confirmRef} className="recovery-dialog recovery-confirm-dialog" aria-labelledby="recovery-confirm-title" onCancel={e => { if (acting) e.preventDefault(); else setConfirming(false); }}>
        <div className="recovery-dialog-heading"><h2 id="recovery-confirm-title">{t('confirmPhysical')}</h2></div>
        <div className="recovery-dialog-body recovery-confirm-body"><p>{t('confirmPhysicalHint')}</p>
        <p className="recovery-confirm-context">{detail.transactionCode} · {detail.returnerName} · {detail.receivingLocationName}</p>
        <WorkflowGroupList groups={detail.groups ?? []} lines={detail.proposalLines ?? detail.lines} selected={selectedGroups} onSelect={setSelectedGroups} statusLabel={status => t(`groupStatus.${status}`)} reasonLabel={t('groupReason')} />
        {admin && <label className="form-label">{t('cancelRemainingReason')}<textarea className="form-textarea" value={cancelReason} onChange={event => setCancelReason(event.target.value)} /></label>}</div>
        <div className="recovery-dialog-footer"><button className="btn btn-secondary" disabled={acting} onClick={() => setConfirming(false)}>{t('back')}</button>
        {admin && <button className="btn btn-secondary" disabled={acting || !selectedGroups.length || !cancelReason.trim()} onClick={() => void act('cancelRemaining')}>{t('cancelRemaining')}</button>}
        <button className="btn btn-primary" disabled={acting || !selectedGroups.length} onClick={() => void act('confirm')}>{acting ? t('loading') : t('confirmPhysical')}</button></div>
      </dialog>}
    </div>
  </div>;
}
