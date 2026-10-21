import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { managementApi, type EmployeeProfile, type ManagedAccount } from '../api/managementApi';
import { useAuth } from '../contexts/AuthContext';

const roles = ['ADMIN', 'IT_STAFF', 'PUR_STAFF', 'USER'];

export function AccountsPage() {
  const { t } = useTranslation('auth');
  const { user } = useAuth();
  const [accounts, setAccounts] = useState<ManagedAccount[]>([]);
  const [keyword, setKeyword] = useState('');
  const [page, setPage] = useState(0);
  const [pages, setPages] = useState(0);
  const [total, setTotal] = useState(0);
  const [revision, setRevision] = useState(0);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [editing, setEditing] = useState<ManagedAccount | 'new' | null>(null);
  const [profileKeyword, setProfileKeyword] = useState('');
  const [profileChoices, setProfileChoices] = useState<EmployeeProfile[]>([]);
  const [profilePage, setProfilePage] = useState(0);
  const [profilePages, setProfilePages] = useState(0);
  const [profileLoading, setProfileLoading] = useState(false);
  const [selectedProfile, setSelectedProfile] = useState<EmployeeProfile | null>(null);
  const [username, setUsername] = useState('');
  const [roleCode, setRoleCode] = useState('IT_STAFF');
  const [accessGranted, setAccessGranted] = useState(true);
  const [loginLocked, setLoginLocked] = useState(false);
  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [resetting, setResetting] = useState<ManagedAccount | null>(null);

  useEffect(() => {
    let active = true;
    setLoading(true); setError('');
    managementApi.accounts(keyword, page).then(({ data }) => {
      if (active) { setAccounts(data.content); setPages(data.totalPages); setTotal(data.totalElements); }
    }).catch((cause) => { if (active) setError(cause instanceof Error ? cause.message : t('management.error')); })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [keyword, page, revision, t]);

  useEffect(() => {
    if (editing !== 'new') return;
    let active = true;
    setProfileLoading(true);
    managementApi.profiles(profileKeyword, profilePage).then(({ data }) => {
      if (active) { setProfileChoices(data.content.filter((profile) => !profile.hasAccount)); setProfilePages(data.totalPages); }
    }).catch((cause) => { if (active) setError(cause instanceof Error ? cause.message : t('management.error')); })
      .finally(() => { if (active) setProfileLoading(false); });
    return () => { active = false; };
  }, [editing, profileKeyword, profilePage, t]);

  const clearSecrets = () => { setPassword(''); setConfirmPassword(''); };
  const close = () => { if (busy) return; clearSecrets(); setEditing(null); setResetting(null); setSelectedProfile(null); setError(''); };
  const openNew = () => { clearSecrets(); setError(''); setSelectedProfile(null); setUsername(''); setRoleCode('IT_STAFF'); setAccessGranted(true); setLoginLocked(false); setEditing('new'); };
  const openEdit = (account: ManagedAccount) => { clearSecrets(); setError(''); setEditing(account); setUsername(account.username || ''); setRoleCode(account.roleCode); setAccessGranted(account.accessGranted); setLoginLocked(account.loginLocked); };

  const save = async () => {
    if (!editing) return;
    if (editing === 'new' && (!selectedProfile || !username.trim() || password !== confirmPassword)) { setError(t('management.selectProfilePassword')); return; }
    setBusy(true); setError('');
    try {
      if (editing === 'new') await managementApi.createAccount({ userId: selectedProfile!.id, username: username.trim(), password, roleCode, accessGranted });
      else await managementApi.updateAccount(editing.id, { roleCode, accessGranted, loginLocked });
      clearSecrets(); setEditing(null); setSelectedProfile(null); setNotice(t('management.saved')); setRevision((v) => v + 1);
    } catch (cause) { setError(cause instanceof Error ? cause.message : t('management.error')); }
    finally { setBusy(false); }
  };

  const resetPassword = async () => {
    if (!resetting) return;
    if (password !== confirmPassword) { setError(t('management.passwordMismatch')); return; }
    setBusy(true); setError('');
    try { await managementApi.resetPassword(resetting.id, password, confirmPassword); clearSecrets(); setResetting(null); setNotice(t('management.passwordSaved')); setRevision((v) => v + 1); }
    catch (cause) { setError(cause instanceof Error ? cause.message : t('management.error')); }
    finally { setBusy(false); }
  };

  return <section className="management-page">
    <div className="page-header management-heading"><h1 className="page-title">{t('management.accounts')}</h1><button className="btn btn-primary" onClick={openNew}>{t('management.addAccount')}</button></div>
    {notice && <p role="status" className="alert-banner alert-success">{notice}</p>}
    {error && <p role="alert" className="alert-banner alert-danger">{error} <button className="btn btn-secondary" onClick={() => setRevision((v) => v + 1)}>{t('management.retry')}</button></p>}
    <label className="management-search">{t('management.searchAccount')}<input className="form-input" value={keyword} maxLength={100} onChange={(e) => { setKeyword(e.target.value); setPage(0); }} /></label>
    {editing && <form className="management-editor" onSubmit={(e) => { e.preventDefault(); void save(); }}>
      <div className="management-heading"><h2>{editing === 'new' ? t('management.addAccount') : t('management.editAccount')}</h2><button type="button" className="btn btn-secondary" disabled={busy} onClick={close}>{t('management.close')}</button></div>
      {editing === 'new' && <div className="management-profile-picker"><label>{t('management.findProfile')}<input className="form-input" value={profileKeyword} onChange={(e) => { setProfileKeyword(e.target.value); setProfilePage(0); }} /></label>{selectedProfile && <p>{t('management.selectedProfile')}: <strong>{selectedProfile.fullName} · {selectedProfile.employeeCode || '—'}</strong></p>}{profileLoading ? <p role="status">{t('management.loading')}</p> : <div className="management-choices">{profileChoices.map((profile) => <button type="button" key={profile.id} className="btn btn-secondary" onClick={() => setSelectedProfile(profile)}>{profile.fullName} · {profile.employeeCode || '—'}</button>)}</div>}<div className="management-pagination"><button type="button" className="btn btn-secondary" disabled={profilePage === 0} onClick={() => setProfilePage((v) => v - 1)}>{t('management.previous')}</button><span>{profilePage + 1} / {Math.max(1, profilePages)}</span><button type="button" className="btn btn-secondary" disabled={profilePage + 1 >= profilePages} onClick={() => setProfilePage((v) => v + 1)}>{t('management.next')}</button></div></div>}
      <div className="management-fields"><label>{t('management.username')}{editing === 'new' && ' *'}<input className="form-input" maxLength={255} required={editing === 'new'} readOnly={editing !== 'new'} value={username} onChange={(e) => setUsername(e.target.value)} /></label><label>{t('account.role')}<select className="form-input" value={roleCode} onChange={(e) => setRoleCode(e.target.value)}>{roles.map((role) => <option key={role} value={role}>{t(`roles.${role}`)}</option>)}</select></label>{roleCode === 'USER' && <label className="management-check"><input type="checkbox" checked={accessGranted} onChange={(e) => setAccessGranted(e.target.checked)} />{t('access.grant')}</label>}{editing !== 'new' && <label className="management-check"><input type="checkbox" checked={loginLocked} onChange={(e) => setLoginLocked(e.target.checked)} />{t('access.lock')}</label>}{editing === 'new' && <><label>{t('password')} *<input className="form-input" type="password" autoComplete="new-password" required minLength={8} maxLength={72} value={password} onChange={(e) => setPassword(e.target.value)} /></label><label>{t('management.confirmPassword')} *<input className="form-input" type="password" autoComplete="new-password" required minLength={8} maxLength={72} value={confirmPassword} onChange={(e) => setConfirmPassword(e.target.value)} /></label></>}</div>
      <div className="management-actions"><button className="btn btn-primary" disabled={busy}>{t('management.save')}</button></div>
    </form>}
    {resetting && <form className="management-editor" onSubmit={(e) => { e.preventDefault(); void resetPassword(); }}><div className="management-heading"><h2>{t('management.resetPassword')}: {resetting.fullName}</h2><button type="button" className="btn btn-secondary" disabled={busy} onClick={close}>{t('management.close')}</button></div><div className="management-fields"><label>{t('management.newPassword')}<input className="form-input" type="password" autoComplete="new-password" minLength={8} maxLength={72} required value={password} onChange={(e) => setPassword(e.target.value)} /></label><label>{t('management.confirmPassword')}<input className="form-input" type="password" autoComplete="new-password" minLength={8} maxLength={72} required value={confirmPassword} onChange={(e) => setConfirmPassword(e.target.value)} /></label></div><button className="btn btn-primary" disabled={busy}>{t('management.save')}</button></form>}
    {loading ? <p role="status">{t('management.loading')}</p> : <div className="table-container"><table className="data-table"><thead><tr><th>{t('account.fullName')}</th><th>{t('management.username')}</th><th>{t('email')}</th><th>{t('account.role')}</th><th>{t('management.status')}</th><th>{t('management.actions')}</th></tr></thead><tbody>{accounts.map((account) => <tr key={account.id}><td>{account.fullName}</td><td>{account.username || '—'}</td><td>{account.email || '—'}</td><td>{t(`roles.${account.roleCode}`)}</td><td>{account.loginLocked ? t('management.lockedStatus') : account.roleCode === 'USER' && !account.accessGranted ? t('access.notGranted') : t('management.active')}</td><td className="management-row-actions">{account.id !== user?.id && <><button className="btn btn-secondary" onClick={() => openEdit(account)}>{t('management.edit')}</button><button className="btn btn-secondary" onClick={() => { clearSecrets(); setResetting(account); setError(''); }}>{t('management.resetPassword')}</button></>}</td></tr>)}</tbody></table>{accounts.length === 0 && <p className="management-empty">{t('management.emptyAccounts')}</p>}</div>}
    <div className="management-pagination"><span>{t('management.total', { count: total })}</span><button className="btn btn-secondary" disabled={loading || page === 0} onClick={() => setPage((v) => v - 1)}>{t('management.previous')}</button><span>{page + 1} / {Math.max(1, pages)}</span><button className="btn btn-secondary" disabled={loading || page + 1 >= pages} onClick={() => setPage((v) => v + 1)}>{t('management.next')}</button></div>
  </section>;
}
