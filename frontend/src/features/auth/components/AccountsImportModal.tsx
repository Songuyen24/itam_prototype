import { useEffect, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { managementApi, type AccountImportRow } from '../api/managementApi';

export function AccountsImportModal({ open, onClose, onDone }: { open: boolean; onClose: () => void; onDone: () => void }) {
  const { t } = useTranslation('auth');
  const [file, setFile] = useState<File | null>(null);
  const [rows, setRows] = useState<AccountImportRow[]>([]);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [confirmed, setConfirmed] = useState(false);
  const [previewed, setPreviewed] = useState(false);
  const [page, setPage] = useState(0);
  const dialogRef = useRef<HTMLDivElement>(null);
  const busyRef = useRef(false);
  const closeRef = useRef(onClose);
  busyRef.current = busy;
  closeRef.current = onClose;

  useEffect(() => {
    if (!open) { setFile(null); setRows([]); setError(''); setConfirmed(false); setPreviewed(false); setPage(0); return; }
    if (typeof document === 'undefined') return;
    const previous = document.activeElement as HTMLElement | null;
    const overflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    dialogRef.current?.focus();
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape' && !busyRef.current) closeRef.current();
      if (event.key !== 'Tab') return;
      const focusable = [...(dialogRef.current?.querySelectorAll<HTMLElement>('button:not(:disabled), input:not(:disabled)') ?? [])];
      if (!focusable.length) return;
      const first = focusable[0], last = focusable[focusable.length - 1];
      if (event.shiftKey && (document.activeElement === first || document.activeElement === dialogRef.current)) { event.preventDefault(); last.focus(); }
      else if (!event.shiftKey && (document.activeElement === last || document.activeElement === dialogRef.current)) { event.preventDefault(); first.focus(); }
    };
    document.addEventListener('keydown', onKeyDown);
    return () => { document.removeEventListener('keydown', onKeyDown); document.body.style.overflow = overflow; previous?.focus(); };
  }, [open]);

  if (!open) return null;
  const preview = async () => {
    if (!file) return;
    setRows([]); setError(''); setBusy(true); setPreviewed(false);
    try { const result = await managementApi.accountImportPreview(file); setRows(result.data.rows); setPage(0); setPreviewed(true); }
    catch (cause) { setError(cause instanceof Error ? cause.message : t('management.error')); }
    finally { setBusy(false); }
  };
  const confirm = async () => {
    if (!file) return;
    setBusy(true); setError('');
    try { const result = await managementApi.accountImportConfirm(file); setRows(result.data.rows); setConfirmed(true); onDone(); }
    catch (cause) { setError(cause instanceof Error ? cause.message : t('management.error')); }
    finally { setBusy(false); }
  };
  const download = async () => {
    setError('');
    try { await managementApi.accountImportTemplate(); }
    catch (cause) { setError(cause instanceof Error ? cause.message : t('management.error')); }
  };
  return <div className="modal-overlay" onMouseDown={event => { if (event.target === event.currentTarget && !busy) onClose(); }}>
    <div ref={dialogRef} className="modal-content modal-lg accounts-dialog" role="dialog" aria-modal="true" aria-label={t('management.importAccounts')} tabIndex={-1}>
      <div className="modal-header management-heading"><h2>{t('management.importAccounts')}</h2><button type="button" className="btn btn-secondary" disabled={busy} onClick={onClose}>{t('management.close')}</button></div>
      <div className="accounts-dialog-body">
        <p>{t('management.importHint')}</p>
        <button type="button" className="btn btn-secondary" disabled={busy} onClick={() => void download()}>{t('management.downloadImportTemplate')}</button>
        <label className="management-search">{t('management.importFile')}<input className="form-input" type="file" accept=".xlsx" disabled={busy} onChange={event => { setFile(event.target.files?.[0] ?? null); setRows([]); setConfirmed(false); setPreviewed(false); setError(''); setPage(0); }} /></label>
        <button type="button" className="btn btn-secondary" disabled={!file || busy} onClick={() => void preview()}>{t('management.previewImport')}</button>
        {busy && <p role="status">{t('management.loading')}</p>}
        {error && <p role="alert" className="alert-banner alert-danger">{error}</p>}
        {previewed && !rows.some(row => row.status === 'VALID') && !confirmed && <p role="status">{t('management.importNoValid')}</p>}
        {rows.length > 0 && <><p role="status">{t('management.importSummary', { valid: rows.filter(row => row.status === (confirmed ? 'CREATED' : 'VALID')).length, invalid: rows.filter(row => row.status === 'INVALID').length })}</p><div className="table-container"><table className="data-table"><thead><tr><th>#</th><th>{t('management.employeeCode')}</th><th>{t('account.fullName')}</th><th>{t('email')}</th><th>{t('management.importDepartment')}</th><th>{t('management.status')}</th><th>{t('management.error')}</th></tr></thead><tbody>{rows.slice(page * 20, (page + 1) * 20).map(row => <tr key={row.row}><td>{row.row}</td><td>{row.employeeCode || '—'}</td><td>{row.fullName || '—'}</td><td>{row.email || '—'}</td><td>{row.departmentCode || '—'}</td><td>{t(`management.importStatus.${row.status}`, row.status)}</td><td>{row.errors.map(code => t(`management.importErrors.${code}`, code)).join(', ') || '—'}</td></tr>)}</tbody></table></div>
          <div className="management-pagination"><span>{page + 1} / {Math.ceil(rows.length / 20)}</span><button type="button" className="btn btn-secondary" disabled={page === 0} onClick={() => setPage(value => value - 1)}>{t('management.previous')}</button><button type="button" className="btn btn-secondary" disabled={(page + 1) * 20 >= rows.length} onClick={() => setPage(value => value + 1)}>{t('management.next')}</button></div></>}
        {file && !confirmed && rows.some(row => row.status === 'VALID') && <button type="button" className="btn btn-primary" disabled={busy} onClick={() => void confirm()}>{t('management.confirmImport')}</button>}
        {confirmed && <p role="status">{t('management.importCompleted')}</p>}
      </div>
    </div>
  </div>;
}
