import { PaginatedContent } from '@/shared/components/PaginatedContent';
import { ReactNode, RefObject, useEffect, useRef } from 'react';
import { useTranslation } from 'react-i18next';
import { PublicationPanel } from '@/features/documents/components/PublicationPanel';
import { WorkflowGroupList } from '@/shared/components/WorkflowGroupList';
import type { DocumentItem } from '@/features/documents/types/document.types';
import { Disposal, DisposalCheck } from '../api/disposalApi';

function DisposalDialog({ title, busy = false, restoreFocusRef, onClose, children, footer }: {
  title: string; busy?: boolean; restoreFocusRef?: RefObject<HTMLElement>; onClose: () => void; children: ReactNode; footer: ReactNode;
}) {
  const { t } = useTranslation('disposal');
  const ref = useRef<HTMLDialogElement>(null);
  const titleId = `disposal-dialog-${title.replace(/\W/g, '-').toLowerCase()}`;
  useEffect(() => {
    if (typeof document === 'undefined') return;
    const dialog = ref.current;
    const opener = restoreFocusRef?.current ?? (document.activeElement instanceof HTMLElement ? document.activeElement : null);
    if (!dialog) return;
    if (dialog.open) dialog.close();
    dialog.showModal();
    dialog.querySelector<HTMLElement>('button, input, textarea, select, [tabindex]:not([tabindex="-1"])')?.focus();
    return () => { if (dialog.open) dialog.close(); opener?.focus(); };
  }, [restoreFocusRef]);
  return <dialog ref={ref} className="modal-content modal-lg disposal-dialog" aria-labelledby={titleId}
    onCancel={event => { event.preventDefault(); if (!busy) onClose(); }}>
    <div className="modal-header"><h2 className="modal-title" id={titleId}>{title}</h2><button type="button" className="btn btn-secondary btn-sm" disabled={busy} onClick={onClose}>{t('close')}</button></div>
    <div className="modal-body">{children}</div>
    <div className="modal-footer">{footer}</div>
  </dialog>;
}

export function DisposalReview({ value, editing = false, saving, error, restoreFocusRef, onCancel, onConfirm, onSaveDraft }: {
  value: DisposalCheck; editing?: boolean; saving: boolean; error?: string; restoreFocusRef?: RefObject<HTMLElement>; onCancel: () => void; onConfirm: () => void; onSaveDraft?: () => void;
}) {
  const { t } = useTranslation('disposal');
  const warningText = (warning: string) => warning.startsWith('PER_USER_LINKED:')
    ? t('perUserWarning', { assetTag: warning.slice('PER_USER_LINKED:'.length) }) : warning;
  return <DisposalDialog title={t('reviewTitle')} busy={saving} restoreFocusRef={restoreFocusRef} onClose={onCancel} footer={<>
    <button className="btn btn-secondary" disabled={saving} onClick={onCancel}>{t('cancel')}</button>
    {!editing && onSaveDraft && <button className="btn btn-secondary" disabled={saving} onClick={onSaveDraft}>{t('saveDraft')}</button>}
    <button className="btn btn-primary" disabled={saving} onClick={onConfirm}>{saving ? t('saving') : t(editing?'saveDraft':'confirmCreate')}</button>
  </>}>
    {error && <div className="alert-banner alert-danger" role="alert">{error}</div>}
    {value.warnings.length > 0 && <div className="alert-banner alert-danger">{value.warnings.map(warning => <span key={warning}>{warningText(warning)}</span>)}</div>}
    <PaginatedContent items={value.assets}>{assets => <div className="candidate-list">{assets.map(asset => <div className="candidate-row" key={asset.assetId}><span><strong>{asset.assetTag}</strong><small>{asset.name} · {t(`categories.${asset.category}`, { defaultValue: asset.category })}{asset.autoAdded ? ` · ${t('autoAdded')}` : ''}</small></span></div>)}</div>}</PaginatedContent>
    {!!value.oemAllocations?.length && <AllocationList title={t('oemTitle')} rows={value.oemAllocations} />}
  </DisposalDialog>;
}

function AllocationList({ title, rows, perUser = false }: {
  title: string; rows: Array<Disposal['oemAllocations'][number] | Disposal['perUserLinks'][number]>; perUser?: boolean;
}) {
  const { t } = useTranslation('disposal');
  return <section className="disposal-allocations"><h3>{title}</h3><PaginatedContent items={rows}>{items => items.map(row => <div className="allocation-row" key={row.allocationId}>
    <strong>{row.assetTag}</strong><span>{t('seats', { count: row.seats })} · {row.deviceTag}</span>
    {perUser && 'userName' in row && <span>{row.userName}</span>}
  </div>)}</PaginatedContent></section>;
}

export function DisposalDetail({ value, role, userId, onTransition, onEdit, decisions, saving, error, restoreFocusRef, action, rejectReason, onDecision, onResolve,
  onChooseAction, onRejectReason, onApprove, onReject, onClose, selectedGroups = [], onSelectGroups = () => {}, executionDate = '', onExecutionDate = () => {}, documentId = 0, onDocumentId = () => {}, documents = [], dueDate = '', onDueDate = () => {}, onExecute = () => {}, onExtend = () => {}, onCancelApproved = () => {}, evidenceFile = null, onEvidenceFile = () => {}, onUploadEvidence = () => {} }: {
  value: Disposal; role?: string; decisions: Record<number, boolean>; saving: boolean; error?: string; restoreFocusRef?: RefObject<HTMLElement>; action: 'approve' | 'reject' | 'execute' | 'extend' | 'cancelApproved' | null;
  rejectReason: string; onDecision: (id: number, release: boolean) => void; onResolve: () => void;
  onChooseAction: (action: 'approve' | 'reject' | 'execute' | 'extend' | 'cancelApproved' | null) => void; onRejectReason: (reason: string) => void;
  onApprove: () => void; onReject: () => void; onClose: () => void;
  userId?: number; onTransition?: (action:'withdraw'|'submit'|'cancel')=>void; onEdit?: ()=>void;
  selectedGroups?: string[]; onSelectGroups?: (ids: string[]) => void; executionDate?: string; onExecutionDate?: (value: string) => void;
  documentId?: number; onDocumentId?: (id: number) => void; documents?: DocumentItem[]; dueDate?: string; onDueDate?: (value: string) => void;
  onExecute?: () => void; onExtend?: () => void; onCancelApproved?: () => void;
  evidenceFile?: File | null; onEvidenceFile?: (file: File | null) => void; onUploadEvidence?: () => void;
}) {
  const { t } = useTranslation('disposal');
  const pending = value.status === 'PENDING' || value.status === 'PARTIAL' || value.status === 'APPROVED';
  const editor = role==='ADMIN' || (role==='IT_STAFF' && userId===value.requesterId);
  const canResolve = value.status==='DRAFT' && editor;
  const canDecide = pending && role === 'ADMIN' && !!value.groups?.some(group => group.status === 'PENDING');
  const canExecute = (role === 'ADMIN' || role === 'IT_STAFF') && !!value.groups?.some(group => group.status === 'APPROVED');
  const today = new Date(Date.now() - new Date().getTimezoneOffset() * 60000).toISOString().slice(0, 10);
  const latestDueDate = value.groups?.filter(group => selectedGroups.includes(group.groupId)).reduce((latest, group) => group.dueDate && group.dueDate > latest ? group.dueDate : latest, today) ?? today;
  const minDueDate = new Date(Date.parse(`${latestDueDate}T00:00:00Z`) + 86400000).toISOString().slice(0, 10);
  const warningText = (warning: string) => warning.startsWith('PER_USER_LINKED:')
    ? t('perUserWarning', { assetTag: warning.slice('PER_USER_LINKED:'.length) }) : warning;
  return <DisposalDialog title={t('detailTitle')} busy={saving} restoreFocusRef={restoreFocusRef} onClose={onClose} footer={<>
    <button className="btn btn-secondary" disabled={saving} onClick={onClose}>{t('close')}</button>
    {editor&&value.status==='PENDING'&&!value.groups?.some(group => group.status !== 'PENDING')&&<button className="btn btn-secondary" disabled={saving} onClick={()=>onTransition?.('withdraw')}>{t('withdraw')}</button>}
    {editor&&value.status==='DRAFT'&&<><button className="btn btn-secondary" disabled={saving} onClick={onEdit}>{t('editDraft')}</button><button className="btn btn-primary" disabled={saving} onClick={()=>onTransition?.('submit')}>{t('submitDraft')}</button></>}
    {((editor&&value.status==='DRAFT')||(role==='ADMIN'&&value.status==='PENDING'&&!value.groups?.some(group => group.status !== 'PENDING')))&&<button className="btn btn-danger" disabled={saving} onClick={()=>{if(window.confirm(t('cancelTransactionConfirm')))onTransition?.('cancel');}}>{t('cancelTransaction')}</button>}
    {canDecide && <button className="btn btn-secondary" disabled={saving} onClick={() => onChooseAction('reject')}>{t('reject')}</button>}
    {canDecide && <button className="btn btn-primary" disabled={saving || value.perUserLinks.length > 0} onClick={() => onChooseAction('approve')}>{t('approve')}</button>}
    {canExecute && <><button className="btn btn-secondary" disabled={saving} onClick={() => onChooseAction('extend')}>{t('extend')}</button><button className="btn btn-danger" disabled={saving} onClick={() => onChooseAction('cancelApproved')}>{t('cancelApproved')}</button><button className="btn btn-primary" disabled={saving} onClick={() => onChooseAction('execute')}>{t('execute')}</button></>}
  </>}>
    {error && <div className="alert-banner alert-danger" role="alert">{error}</div>}
    <dl className="disposal-metadata">
      <div><dt>{t('state')}</dt><dd><span className={`disposal-status disposal-status-${value.status.toLowerCase()}`}>{t(`common:status.${value.status}`)}</span></dd></div>
      <div><dt>{t('code')}</dt><dd>{value.transactionCode}</dd></div>
      <div><dt>{t('requester')}</dt><dd>{value.actorName}</dd></div>
      <div><dt>{t('reason')}</dt><dd>{value.reason}</dd></div>
      {value.rejectionReason && <div><dt>{t('rejectReason')}</dt><dd>{value.rejectionReason}</dd></div>}
      <div><dt>{t('date')}</dt><dd>{value.disposalDate}</dd></div>
      <div><dt>{t('createdAt')}</dt><dd>{value.createdAt}</dd></div>
    </dl>
    {value.warnings.length > 0 && <div className="alert-banner alert-danger">{value.warnings.map(warning => <span key={warning}>{warningText(warning)}</span>)}</div>}
    {!!value.groups?.length && <WorkflowGroupList groups={value.groups} lines={value.assets} selected={selectedGroups} onSelect={onSelectGroups} selectable={!action ? null : action === 'execute' || action === 'extend' || action === 'cancelApproved' ? 'APPROVED' : 'PENDING'} statusLabel={status => t(`groupStatus.${status}`)} reasonLabel={t('groupReason')} />}
    {value.groups?.some(group => group.status === 'APPROVED' && group.dueDate && group.dueDate < today) && <p className="alert-banner alert-danger" role="alert">{t('overdue')}</p>}
    {value.groups?.filter(group => group.dueDate).map(group => <p key={group.groupId}>{t('dueDate')}: {group.dueDate} · {group.assetIds.map(id => value.assets.find(asset => asset.assetId === id)?.assetTag ?? id).join(', ')}</p>)}
    {value.groups?.filter(group => group.status === 'COMPLETED' && group.executionDate).map(group => <p key={`execution-${group.groupId}`}>{t('executionDate')}: {group.executionDate}{group.documentId != null && <> · {t('evidenceReference')}: #{group.documentId}</>} · {group.assetIds.map(id => value.assets.find(asset => asset.assetId === id)?.assetTag ?? id).join(', ')}</p>)}
    <PaginatedContent key={value.transactionId} items={value.assets}>{assets => <div className="table-container"><table className="data-table"><thead><tr><th>{t('asset')}</th><th>{t('category')}</th><th>{t('serial')}</th></tr></thead>
      <tbody>{assets.map(asset => <tr key={asset.assetId}><td><strong>{asset.assetTag}</strong><br />{asset.name}{asset.autoAdded && <> · {t('autoAdded')}</>}</td><td>{t(`categories.${asset.category}`, { defaultValue: asset.category })}</td><td>{asset.serialNumber || '-'}</td></tr>)}</tbody>
    </table></div>}</PaginatedContent>
    {!!value.oemAllocations.length && <><p className="disposal-hint">{t('oemHint')}</p><AllocationList title={t('oemTitle')} rows={value.oemAllocations} /></>}
    {!!value.perUserLinks.length && <section className="disposal-allocations"><h3>{t('perUserTitle')}</h3><p className="disposal-hint">{t('perUserDecisionHint')}</p>
      <PaginatedContent items={value.perUserLinks}>{links => links.map(link => <fieldset className="allocation-row" key={link.allocationId} disabled={!canResolve || saving}>
        <legend><strong>{link.assetTag}</strong> · {link.userName} · {link.deviceTag} · {t('seats', { count: link.seats })}</legend>
        <label><input type="radio" name={`allocation-${link.allocationId}`} checked={decisions[link.allocationId] !== false} onChange={() => onDecision(link.allocationId, true)} /> {t('releaseSeats')}</label>
        <label><input type="radio" name={`allocation-${link.allocationId}`} checked={decisions[link.allocationId] === false} onChange={() => onDecision(link.allocationId, false)} /> {t('keepActive')}</label>
      </fieldset>)}</PaginatedContent>
      {canResolve && <button className="btn btn-primary" disabled={saving} onClick={onResolve}>{t('resolvePerUser')}</button>}
    </section>}
    {action === 'approve' && <section className="decision-confirm" role="alertdialog" aria-labelledby="approve-confirm-title"><h3 id="approve-confirm-title">{t('approveTitle')}</h3><p>{t('approveHint')}</p><div className="pending-actions"><button className="btn btn-secondary" disabled={saving} onClick={() => onChooseAction(null)}>{t('cancel')}</button><button className="btn btn-primary" disabled={saving || !selectedGroups.length} onClick={onApprove}>{t('confirmApprove')}</button></div></section>}
    {action === 'reject' && <section className="decision-confirm" role="alertdialog" aria-labelledby="reject-confirm-title"><h3 id="reject-confirm-title">{t('rejectTitle')}</h3><label className="form-group"><span className="form-label required">{t('rejectReason')}</span><textarea className="form-textarea" required value={rejectReason} onChange={event => onRejectReason(event.target.value)} /></label><div className="pending-actions"><button className="btn btn-secondary" disabled={saving} onClick={() => onChooseAction(null)}>{t('cancel')}</button><button className="btn btn-danger" disabled={saving || !rejectReason.trim() || !selectedGroups.length} onClick={onReject}>{t('confirmReject')}</button></div></section>}
    {action === 'execute' && <section className="decision-confirm"><h3>{t('execute')}</h3><label className="form-group"><span className="form-label required">{t('executionDate')}</span><input className="form-input" type="date" value={executionDate} onChange={event => onExecutionDate(event.target.value)} /></label><label className="form-group"><span className="form-label required">{t('evidenceDocument')}</span><select className="form-select" value={documentId} onChange={event => onDocumentId(Number(event.target.value))}><option value={0}>{t('chooseDocument')}</option>{documents.map(document => <option key={document.documentId} value={document.documentId}>{document.originalFileName}</option>)}</select></label><label className="form-group"><span className="form-label">{t('uploadEvidence')}</span><input className="form-input" type="file" accept="application/pdf,image/*" onChange={event => onEvidenceFile(event.target.files?.[0] ?? null)} /></label><button className="btn btn-secondary" disabled={saving || !evidenceFile} onClick={onUploadEvidence}>{t('uploadEvidence')}</button><div className="pending-actions"><button className="btn btn-secondary" disabled={saving} onClick={() => onChooseAction(null)}>{t('cancel')}</button><button className="btn btn-primary" disabled={saving || !selectedGroups.length || !documentId || !executionDate} onClick={onExecute}>{t('confirmExecute')}</button></div></section>}
    {action === 'extend' && <section className="decision-confirm"><h3>{t('extend')}</h3><label className="form-group"><span className="form-label required">{t('dueDate')}</span><input className="form-input" type="date" min={minDueDate} value={dueDate} onChange={event => onDueDate(event.target.value)} /></label><label className="form-group"><span className="form-label required">{t('extensionReason')}</span><textarea className="form-textarea" value={rejectReason} onChange={event => onRejectReason(event.target.value)} /></label><div className="pending-actions"><button className="btn btn-secondary" disabled={saving} onClick={() => onChooseAction(null)}>{t('cancel')}</button><button className="btn btn-primary" disabled={saving || !selectedGroups.length || dueDate < minDueDate || !rejectReason.trim()} onClick={onExtend}>{t('confirmExtend')}</button></div></section>}
    {action === 'cancelApproved' && <section className="decision-confirm"><h3>{t('cancelApproved')}</h3><label className="form-group"><span className="form-label required">{t('cancelApprovedReason')}</span><textarea className="form-textarea" value={rejectReason} onChange={event => onRejectReason(event.target.value)} /></label><div className="pending-actions"><button className="btn btn-secondary" disabled={saving} onClick={() => onChooseAction(null)}>{t('cancel')}</button><button className="btn btn-danger" disabled={saving || !selectedGroups.length || !rejectReason.trim()} onClick={onCancelApproved}>{t('confirmCancelApproved')}</button></div></section>}
    {value.status === 'COMPLETED' && <PublicationPanel transactionId={value.transactionId} />}
  </DisposalDialog>;
}
