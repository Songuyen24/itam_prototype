import { useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { useAuth } from '../contexts/AuthContext';
import { managementApi } from '../api/managementApi';

export function AccountModal({ open, onClose }: { open: boolean; onClose: () => void }) {
  const { user, logout } = useAuth();
  const { t } = useTranslation('auth');
  const navigate = useNavigate();
  const dialog = useRef<HTMLDialogElement>(null);
  const opener = useRef<HTMLElement | null>(null);
  const firstInput = useRef<HTMLInputElement>(null);
  const [currentPassword, setCurrentPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    const node = dialog.current;
    if (!node) return;
    if (open && !node.open) {
      opener.current = document.activeElement as HTMLElement;
      node.showModal();
      firstInput.current?.focus();
    } else if (!open && node.open) node.close();
  }, [open]);

  const clear = () => { setCurrentPassword(''); setNewPassword(''); setConfirmPassword(''); setError(''); };
  const close = () => { clear(); onClose(); opener.current?.focus(); };

  const submit = async () => {
    if (newPassword !== confirmPassword) { setError(t('management.passwordMismatch')); return; }
    setBusy(true); setError('');
    try {
      await managementApi.changeOwnPassword(currentPassword, newPassword, confirmPassword);
      clear();
      onClose();
      await logout();
      navigate('/login', { replace: true });
    } catch (cause) { setError(cause instanceof Error ? cause.message : t('management.error')); }
    finally { setBusy(false); }
  };

  return <dialog ref={dialog} className="modal-content catalog-dialog" aria-labelledby="my-account-title" onCancel={(event) => { if (busy) event.preventDefault(); }} onClose={close}>
    <div className="modal-header"><h2 className="modal-title" id="my-account-title">{t('account.title')}</h2><button type="button" className="btn btn-secondary" disabled={busy} onClick={() => dialog.current?.close()} aria-label={t('management.close')}>×</button></div>
    {user && <div className="modal-body"><dl className="account-info"><dt>{t('account.fullName')}</dt><dd>{user.fullName}</dd><dt>{t('management.username')}</dt><dd>{user.username || '—'}</dd><dt>{t('email')}</dt><dd>{user.email || '—'}</dd><dt>{t('account.role')}</dt><dd>{t(`roles.${user.role}`, user.role)}</dd></dl>
      <form onSubmit={(e) => { e.preventDefault(); void submit(); }}><h3 className="management-subtitle">{t('management.changePassword')}</h3>{error && <p role="alert" className="alert-banner alert-danger">{error}</p>}<div className="management-fields"><label>{t('management.currentPassword')}<input ref={firstInput} className="form-input" type="password" autoComplete="current-password" required value={currentPassword} disabled={busy} onChange={(e) => setCurrentPassword(e.target.value)} /></label><label>{t('management.newPassword')}<input className="form-input" type="password" autoComplete="new-password" required minLength={8} maxLength={72} value={newPassword} disabled={busy} onChange={(e) => setNewPassword(e.target.value)} /></label><label>{t('management.confirmPassword')}<input className="form-input" type="password" autoComplete="new-password" required minLength={8} maxLength={72} value={confirmPassword} disabled={busy} onChange={(e) => setConfirmPassword(e.target.value)} /></label></div><div className="management-actions"><button className="btn btn-primary" disabled={busy}>{t('management.save')}</button><button type="button" className="btn btn-secondary" disabled={busy} onClick={() => dialog.current?.close()}>{t('management.cancel')}</button></div></form>
    </div>}
  </dialog>;
}
