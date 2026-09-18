import { ReactNode, RefObject, useEffect, useRef } from 'react';
import { useTranslation } from 'react-i18next';
import { PublicationPanel } from '@/features/documents/components/PublicationPanel';
import { Disposal, DisposalCheck } from '../api/disposalApi';

function DisposalDialog({ title, busy = false, restoreFocusRef, onClose, children, footer }: {
  title: string; busy?: boolean; restoreFocusRef?: RefObject<HTMLElement>; onClose: () => void; children: ReactNode; footer: ReactNode;
}) {
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
    <div className="modal-header"><h2 className="modal-title" id={titleId}>{title}</h2></div>
    <div className="modal-body">{children}</div>
    <div className="modal-footer">{footer}</div>
  </dialog>;
}

export function DisposalReview({ value, saving, error, restoreFocusRef, onCancel, onConfirm }: {
  value: DisposalCheck; saving: boolean; error?: string; restoreFocusRef?: RefObject<HTMLElement>; onCancel: () => void; onConfirm: () => void;
}) {
  const { t } = useTranslation('disposal');
  const warningText = (warning: string) => warning.startsWith('PER_USER_LINKED:')
    ? t('perUserWarning', { assetTag: warning.slice('PER_USER_LINKED:'.length) }) : warning;
  return <DisposalDialog title={t('reviewTitle')} busy={saving} restoreFocusRef={restoreFocusRef} onClose={onCancel} footer={<>
    <button className="btn btn-secondary" disabled={saving} onClick={onCancel}>{t('cancel')}</button>
    <button className="btn btn-primary" disabled={saving} onClick={onConfirm}>{saving ? t('saving') : t('confirmCreate')}</button>
  </>}>
    {error && <div className="alert-banner alert-danger" role="alert">{error}</div>}
    {value.warnings.length > 0 && <div className="alert-banner alert-danger">{value.warnings.map(warning => <span key={warning}>{warningText(warning)}</span>)}</div>}
    <div className="candidate-list">{value.assets.map(asset => <div className="candidate-row" key={asset.assetId}><span><strong>{asset.assetTag}</strong><small>{asset.name} · {t(`categories.${asset.category}`, { defaultValue: asset.category })}{asset.autoAdded ? ` · ${t('autoAdded')}` : ''}</small></span></div>)}</div>
    {!!value.oemAllocations?.length && <AllocationList title={t('oemTitle')} rows={value.oemAllocations} />}
  </DisposalDialog>;
}

function AllocationList({ title, rows, perUser = false }: {
  title: string; rows: Disposal['oemAllocations'] | Disposal['perUserLinks']; perUser?: boolean;
}) {
  const { t } = useTranslation('disposal');
  return <section className="disposal-allocations"><h3>{title}</h3>{rows.map(row => <div className="allocation-row" key={row.allocationId}>
    <strong>{row.assetTag}</strong><span>{t('seats', { count: row.seats })} · {row.deviceTag}</span>
    {perUser && 'userName' in row && <span>{row.userName}</span>}
  </div>)}</section>;
}

export function DisposalDetail({ value, role, decisions, saving, error, restoreFocusRef, action, rejectReason, onDecision, onResolve,
  onChooseAction, onRejectReason, onApprove, onReject, onClose }: {
  value: Disposal; role?: string; decisions: Record<number, boolean>; saving: boolean; error?: string; restoreFocusRef?: RefObject<HTMLElement>; action: 'approve' | 'reject' | null;
  rejectReason: string; onDecision: (id: number, release: boolean) => void; onResolve: () => void;
  onChooseAction: (action: 'approve' | 'reject' | null) => void; onRejectReason: (reason: string) => void;
  onApprove: () => void; onReject: () => void; onClose: () => void;
}) {
  const { t } = useTranslation('disposal');
  const pending = value.status === 'PENDING';
  const canResolve = pending && (role === 'ADMIN' || role === 'IT_STAFF');
  const canDecide = pending && role === 'ADMIN';
  const warningText = (warning: string) => warning.startsWith('PER_USER_LINKED:')
    ? t('perUserWarning', { assetTag: warning.slice('PER_USER_LINKED:'.length) }) : warning;
  return <DisposalDialog title={t('detailTitle')} busy={saving} restoreFocusRef={restoreFocusRef} onClose={onClose} footer={<>
    <button className="btn btn-secondary" disabled={saving} onClick={onClose}>{t('close')}</button>
    {canDecide && <button className="btn btn-secondary" disabled={saving} onClick={() => onChooseAction('reject')}>{t('reject')}</button>}
    {canDecide && <button className="btn btn-primary" disabled={saving || value.perUserLinks.length > 0} onClick={() => onChooseAction('approve')}>{t('approve')}</button>}
  </>}>
    {error && <div className="alert-banner alert-danger" role="alert">{error}</div>}
    <dl className="disposal-metadata">
      <div><dt>{t('code')}</dt><dd>{value.transactionCode}</dd></div>
      <div><dt>{t('requester')}</dt><dd>{value.actorName}</dd></div>
      <div><dt>{t('reason')}</dt><dd>{value.reason}</dd></div>
      <div><dt>{t('date')}</dt><dd>{value.disposalDate}</dd></div>
      <div><dt>{t('createdAt')}</dt><dd>{value.createdAt}</dd></div>
    </dl>
    {value.warnings.length > 0 && <div className="alert-banner alert-danger">{value.warnings.map(warning => <span key={warning}>{warningText(warning)}</span>)}</div>}
    <div className="table-container"><table className="data-table"><thead><tr><th>{t('asset')}</th><th>{t('category')}</th><th>{t('serial')}</th></tr></thead>
      <tbody>{value.assets.map(asset => <tr key={asset.assetId}><td><strong>{asset.assetTag}</strong><br />{asset.name}{asset.autoAdded && <> · {t('autoAdded')}</>}</td><td>{t(`categories.${asset.category}`, { defaultValue: asset.category })}</td><td>{asset.serialNumber || '-'}</td></tr>)}</tbody>
    </table></div>
    {!!value.oemAllocations.length && <><p className="disposal-hint">{t('oemHint')}</p><AllocationList title={t('oemTitle')} rows={value.oemAllocations} /></>}
    {!!value.perUserLinks.length && <section className="disposal-allocations"><h3>{t('perUserTitle')}</h3><p className="disposal-hint">{t('perUserDecisionHint')}</p>
      {value.perUserLinks.map(link => <fieldset className="allocation-row" key={link.allocationId} disabled={!canResolve || saving}>
        <legend><strong>{link.assetTag}</strong> · {link.userName} · {link.deviceTag} · {t('seats', { count: link.seats })}</legend>
        <label><input type="radio" name={`allocation-${link.allocationId}`} checked={decisions[link.allocationId] !== false} onChange={() => onDecision(link.allocationId, true)} /> {t('releaseSeats')}</label>
        <label><input type="radio" name={`allocation-${link.allocationId}`} checked={decisions[link.allocationId] === false} onChange={() => onDecision(link.allocationId, false)} /> {t('keepActive')}</label>
      </fieldset>)}
      {canResolve && <button className="btn btn-primary" disabled={saving} onClick={onResolve}>{t('resolvePerUser')}</button>}
    </section>}
    {action === 'approve' && <section className="decision-confirm" role="alertdialog" aria-labelledby="approve-confirm-title"><h3 id="approve-confirm-title">{t('approveTitle')}</h3><p>{t('approveHint')}</p><div className="pending-actions"><button className="btn btn-secondary" disabled={saving} onClick={() => onChooseAction(null)}>{t('cancel')}</button><button className="btn btn-primary" disabled={saving} onClick={onApprove}>{t('confirmApprove')}</button></div></section>}
    {action === 'reject' && <section className="decision-confirm" role="alertdialog" aria-labelledby="reject-confirm-title"><h3 id="reject-confirm-title">{t('rejectTitle')}</h3><label className="form-group"><span className="form-label required">{t('rejectReason')}</span><textarea className="form-textarea" required value={rejectReason} onChange={event => onRejectReason(event.target.value)} /></label><div className="pending-actions"><button className="btn btn-secondary" disabled={saving} onClick={() => onChooseAction(null)}>{t('cancel')}</button><button className="btn btn-danger" disabled={saving || !rejectReason.trim()} onClick={onReject}>{t('confirmReject')}</button></div></section>}
    <PublicationPanel transactionId={value.transactionId} />
  </DisposalDialog>;
}
