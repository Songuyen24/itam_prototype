import { useEffect, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useAuth } from '../contexts/AuthContext';
import { managementApi, type EmployeeProfile, type ProfileInput } from '../api/managementApi';
import type { HeldAsset } from '../api/managementApi';
import type { Department } from '@/features/catalogs/types/catalog.types';
import { departmentApi } from '@/features/catalogs/api/catalogApi';
import { useSearchParams } from 'react-router-dom';
import { httpClient } from '@/shared/api/httpClient';
import type { ApiResponse } from '@/features/catalogs/types/catalog.types';
import './accounts.css';
import { AccountsImportModal } from '../components/AccountsImportModal';

type Group = 'ASSETS' | 'TRANSACTIONS' | 'ALLOCATIONS';
type Permission = { view: boolean; export: boolean };
type Permissions = Record<Group, Permission>;
const groups: Group[] = ['ASSETS', 'TRANSACTIONS', 'ALLOCATIONS'];
const roles = ['USER', 'IT_STAFF', 'PUR_STAFF', 'ADMIN'];

const emptyForm: ProfileInput = { fullName: '', employeeCode: '', email: null, departmentId: null };

export function AccountsPage() {
  const { t } = useTranslation('auth');
  const { user } = useAuth();
  const [searchParams] = useSearchParams();
  const admin = user?.role === 'ADMIN';
  const canEdit = admin || user?.role === 'IT_STAFF';
  const [keyword, setKeyword] = useState(searchParams.get('keyword') ?? '');
  useEffect(() => { setKeyword(searchParams.get('keyword') ?? ''); setPage(0); }, [searchParams]);
  const [page, setPage] = useState(0);
  const [revision, setRevision] = useState(0);
  const [profiles, setProfiles] = useState<EmployeeProfile[]>([]);
  const [totalPages, setTotalPages] = useState(0);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [editing, setEditing] = useState<EmployeeProfile | 'new' | null>(null);
  const [form, setForm] = useState<ProfileInput>(emptyForm);
  const [roleCode, setRoleCode] = useState('USER');
  const [accessGranted, setAccessGranted] = useState(false);
  const [loginLocked, setLoginLocked] = useState(false);
  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [permissions, setPermissions] = useState<Permissions | null>(null);
  const [permissionError, setPermissionError] = useState('');
  const [permissionRevision, setPermissionRevision] = useState(0);
  const modalRef = useRef<HTMLDivElement>(null);
  const busyRef = useRef(false);
  const [departments, setDepartments] = useState<Department[]>([]);
  const [departmentError, setDepartmentError] = useState('');
  const [departmentRevision, setDepartmentRevision] = useState(0);
  const [selected, setSelected] = useState<EmployeeProfile | null>(null);
  const [assets, setAssets] = useState<HeldAsset[]>([]);
  const [assetPage, setAssetPage] = useState(0);
  const [assetPages, setAssetPages] = useState(0);
  const [assetTotal, setAssetTotal] = useState(0);
  const [assetLoading, setAssetLoading] = useState(false);
  const [assetError, setAssetError] = useState('');
  const [assetRevision, setAssetRevision] = useState(0);
  const [busy, setBusy] = useState(false);
  const [importOpen, setImportOpen] = useState(false);
  busyRef.current = busy;
  const modalOpen = !!editing || !!selected;

  useEffect(() => {
    let active = true;
    setLoading(true); setError('');
    managementApi.profiles(keyword, page).then(({ data }) => {
      if (active) { setProfiles(data.content); setTotalPages(data.totalPages); setPage(current => Math.min(current, Math.max(0, data.totalPages - 1))); setTotal(data.totalElements); }
    }).catch((cause) => { if (active) setError(cause instanceof Error ? cause.message : t('management.error')); })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [keyword, page, revision, t]);

  useEffect(() => {
    if (!canEdit) return;
    let active = true;
    setDepartmentError('');
    departmentApi.getAll(undefined, true, 0, 100).then(async (result) => {
      const all = [...result.data.content];
      for (let index = 1; index < result.data.totalPages; index++) all.push(...(await departmentApi.getAll(undefined, true, index, 100)).data.content);
      if (active) setDepartments(all);
    }).catch((cause) => { if (active) setDepartmentError(cause instanceof Error ? cause.message : t('management.error')); });
    return () => { active = false; };
  }, [canEdit, departmentRevision, t]);

  useEffect(() => {
    if (!selected) return;
    let active = true;
    setAssetLoading(true); setAssetError('');
    managementApi.holdings(selected.id, assetPage).then(({ data }) => {
      if (active) { setAssets(data.assets.content); setAssetPages(data.assets.totalPages); setAssetTotal(data.assets.totalElements); setAssetPage(current => Math.min(current, Math.max(0, data.assets.totalPages - 1))); }
    }).catch((cause) => { if (active) setAssetError(cause instanceof Error ? cause.message : t('management.error')); })
      .finally(() => { if (active) setAssetLoading(false); });
    return () => { active = false; };
  }, [selected, assetPage, assetRevision, revision, t]);

  useEffect(() => {
    if (!editing || editing === 'new' || !admin || !editing.hasAccount || !['IT_STAFF', 'PUR_STAFF'].includes(editing.roleCode)) { setPermissions(null); return; }
    let active = true;
    setPermissions(null); setPermissionError('');
    httpClient<ApiResponse<Permissions>>(`/v1/reports/permissions/${editing.id}`).then(({ data }) => { if (active) setPermissions(data); })
      .catch((cause) => { if (active) setPermissionError(cause instanceof Error ? cause.message : t('management.error')); });
    return () => { active = false; };
  }, [editing, admin, permissionRevision, t]);

  useEffect(() => {
    if (!modalOpen || typeof document === 'undefined') return;
    const previous = document.activeElement as HTMLElement | null;
    const overflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    modalRef.current?.focus();
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape' && !busyRef.current) { setEditing(null); setSelected(null); setPassword(''); setConfirmPassword(''); setError(''); }
      if (event.key !== 'Tab') return;
      const focusable = [...(modalRef.current?.querySelectorAll<HTMLElement>('button:not(:disabled), input:not(:disabled), select:not(:disabled), [tabindex="0"]') ?? [])];
      if (!focusable.length) return;
      const first = focusable[0], last = focusable[focusable.length - 1];
      if (event.shiftKey && (document.activeElement === first || document.activeElement === modalRef.current)) { event.preventDefault(); last.focus(); }
      else if (!event.shiftKey && document.activeElement === modalRef.current) { event.preventDefault(); first.focus(); }
      else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first.focus(); }
    };
    document.addEventListener('keydown', onKeyDown);
    return () => { document.removeEventListener('keydown', onKeyDown); document.body.style.overflow = overflow; previous?.focus(); };
  }, [modalOpen]);

  const message = (cause: unknown) => cause instanceof Error ? cause.message : t('management.error');
  const close = () => { if (busy) return; setEditing(null); setSelected(null); setPassword(''); setConfirmPassword(''); setError(''); };

  const openForm = (profile: EmployeeProfile | 'new') => {
    setEditing(profile); setError('');
    setForm(profile === 'new' ? emptyForm : { fullName: profile.fullName, employeeCode: profile.employeeCode ?? '', email: profile.email || null, departmentId: profile.departmentId ?? null });
    setRoleCode(profile === 'new' ? 'USER' : profile.roleCode);
    setAccessGranted(profile === 'new' ? false : profile.accessGranted);
    setLoginLocked(profile === 'new' ? false : profile.loginLocked);
    setPassword(''); setConfirmPassword(''); setPermissions(null); setNotice('');
  };

  const save = async () => {
    if (!editing) return;
    setBusy(true); setError('');
    try {
      const input = { ...form, fullName: form.fullName.trim(), employeeCode: form.employeeCode.trim(), email: form.email?.trim() || null };
      const { data } = editing === 'new'
        ? await managementApi.createProfile({ ...input, password: password || undefined, accessGranted: admin && accessGranted })
        : await managementApi.updateProfile(editing.id, input);
      setForm({ fullName: data.fullName, employeeCode: data.employeeCode ?? '', email: data.email, departmentId: data.departmentId });
      setPassword(''); setConfirmPassword(''); setAccessGranted(data.accessGranted); setEditing(data); setNotice(t('management.profileSaved')); setRevision((v) => v + 1);
    } catch (cause) { setError(cause instanceof Error ? cause.message : t('management.error')); }
    finally { setBusy(false); }
  };

  const saveAccount = async () => {
    if (!admin || !editing || editing === 'new' || editing.id === user?.id) return;
    if (!editing.hasAccount && password !== confirmPassword) { setError(t('management.passwordMismatch')); return; }
    setBusy(true); setError('');
    try {
      const { data } = editing.hasAccount
        ? await managementApi.updateAccount(editing.id, { roleCode, accessGranted, loginLocked })
        : await managementApi.createAccount({ userId: editing.id, password, roleCode, accessGranted });
      setEditing({ ...editing, ...data, hasAccount: true }); setPassword(''); setConfirmPassword(''); setNotice(t('management.accountSaved')); setRevision(v => v + 1);
    } catch (cause) { setError(message(cause)); }
    finally { setBusy(false); }
  };
  const resetPassword = async () => {
    if (!admin || !editing || editing === 'new' || !editing.hasAccount || editing.id === user?.id) return;
    if (password !== confirmPassword) { setError(t('management.passwordMismatch')); return; }
    setBusy(true); setError('');
    try { await managementApi.resetPassword(editing.id, password, confirmPassword); setPassword(''); setConfirmPassword(''); setNotice(t('management.passwordSaved')); }
    catch (cause) { setError(message(cause)); }
    finally { setBusy(false); }
  };
  const changeLoginLock = async (profile: EmployeeProfile) => {
    if (!admin || !profile.hasAccount || profile.id === user?.id || !window.confirm(t(profile.loginLocked ? 'management.unlockLoginConfirm' : 'management.lockLoginConfirm', { name: profile.fullName }))) return;
    setBusy(true); setError('');
    try { await managementApi.updateAccount(profile.id, { roleCode: profile.roleCode, accessGranted: profile.accessGranted, loginLocked: !profile.loginLocked }); setNotice(t('management.saved')); setRevision(v => v + 1); }
    catch (cause) { setError(message(cause)); }
    finally { setBusy(false); }
  };
  const changePermission = async (group: Group, permission: Permission) => {
    if (!admin || !editing || editing === 'new' || !permissions || !['IT_STAFF', 'PUR_STAFF'].includes(editing.roleCode)) return;
    setBusy(true); setError('');
    try {
      const { data } = await httpClient<ApiResponse<Permissions>>(`/v1/reports/permissions/${editing.id}/${group}`, { method: 'PUT', body: JSON.stringify(permission) });
      setPermissions(data); setNotice(t('management.reportSaved'));
    } catch (cause) { setError(message(cause)); }
    finally { setBusy(false); }
  };

  const changeStatus = async (profile: EmployeeProfile) => {
    if (!window.confirm(t(profile.accountStatus === 'LOCKED' ? 'management.unlockConfirm' : 'management.lockConfirm', { name: profile.fullName }))) return;
    setBusy(true); setError('');
    try { await managementApi.setProfileStatus(profile.id, profile.accountStatus === 'LOCKED' ? 'ACTIVE' : 'LOCKED'); setNotice(t('management.saved')); setRevision((v) => v + 1); }
    catch (cause) { setError(cause instanceof Error ? cause.message : t('management.error')); }
    finally { setBusy(false); }
  };

  return <section className="management-page">
    <div className="page-header management-heading"><h1 className="page-title">{t('management.accounts')}</h1>{canEdit && <div className="management-actions"><button className="btn btn-secondary" onClick={() => setImportOpen(true)}>{t('management.importAccounts')}</button><button className="btn btn-primary" onClick={() => openForm('new')}>{t('management.addProfile')}</button></div>}</div>
    {notice && <p role="status" className="alert-banner alert-success">{notice}</p>}
    {departmentError && <p role="alert" className="alert-banner alert-danger">{departmentError} <button className="btn btn-secondary" onClick={() => setDepartmentRevision((v) => v + 1)}>{t('management.retry')}</button></p>}
    {error && !editing && <p role="alert" className="alert-banner alert-danger">{error} <button className="btn btn-secondary" onClick={() => setRevision((v) => v + 1)}>{t('management.retry')}</button></p>}
    <label className="management-search">{t('management.search')}<input className="form-input" value={keyword} maxLength={100} onChange={(e) => { setKeyword(e.target.value); setPage(0); }} /></label>
    {editing && <div className="modal-overlay" onMouseDown={e => { if (e.target === e.currentTarget) close(); }}><div ref={modalRef} className="modal-content modal-lg accounts-dialog" role="dialog" aria-modal="true" aria-label={editing === 'new' ? t('management.addProfile') : t('management.editAccount')} tabIndex={-1}>
      <div className="modal-header management-heading"><h2>{editing === 'new' ? t('management.addProfile') : `${t('management.editAccount')}: ${editing.fullName}`}</h2><button type="button" className="btn btn-secondary" disabled={busy} onClick={close}>{t('management.close')}</button></div>
      <div className="accounts-dialog-body">{error && <p role="alert" className="alert-banner alert-danger">{error}</p>}{notice && <p role="status" className="alert-banner alert-success">{notice}</p>}
      <form onSubmit={(e) => { e.preventDefault(); void save(); }}><h3>{t('management.profileInformation')}</h3>
      <div className="management-fields">
        <label>{t('account.fullName')} *<input className="form-input" required maxLength={255} value={form.fullName} onChange={(e) => setForm({ ...form, fullName: e.target.value })} /></label>
        <label>{t('management.employeeCode')}{(editing === 'new' || !!editing.employeeCode) && ' *'}<input className="form-input" required={editing === 'new' || !!editing.employeeCode} maxLength={100} value={form.employeeCode} onChange={(e) => setForm({ ...form, employeeCode: e.target.value })} /></label>
        <label>{t('email')}<input className="form-input" type="email" maxLength={255} value={form.email ?? ''} onChange={(e) => setForm({ ...form, email: e.target.value })} /></label>
        <label>{t('account.department')}<select className="form-input" value={form.departmentId ?? ''} onChange={(e) => setForm({ ...form, departmentId: e.target.value ? Number(e.target.value) : null })}><option value="">{t('management.none')}</option>{departments.map((d) => <option key={d.departmentId} value={d.departmentId}>{d.name}</option>)}</select></label>
        {editing !== 'new' && editing.employeeCode && editing.employeeCode !== form.employeeCode && <label>{t('management.correctionReason')} *<input className="form-input" required maxLength={1000} value={form.employeeCodeCorrectionReason ?? ''} onChange={(e) => setForm({ ...form, employeeCodeCorrectionReason: e.target.value })} /></label>}
        {editing === 'new' && <label>{t('management.newPassword')}<input className="form-input" type="password" autoComplete="new-password" minLength={8} maxLength={72} value={password} onChange={e => setPassword(e.target.value)} /><small>{t('management.defaultPasswordHint')}</small></label>}
        {editing === 'new' && admin && <label className="management-check"><input type="checkbox" checked={accessGranted} onChange={e => setAccessGranted(e.target.checked)} />{t('access.grant')}</label>}
      </div>
      <button className="btn btn-primary" disabled={busy}>{t('management.saveProfile')}</button></form>
      {admin && editing !== 'new' && editing.id !== user?.id && <><form onSubmit={e => { e.preventDefault(); void saveAccount(); }}><h3>{editing.hasAccount ? t('management.loginAccount') : t('management.createLogin')}</h3><div className="management-fields">
        <label>{t('account.role')}<select className="form-input" value={roleCode} onChange={e => { setRoleCode(e.target.value); if (e.target.value === 'USER') setAccessGranted(false); }}>{roles.map(role => <option key={role} value={role}>{t(`roles.${role}`)}</option>)}</select></label>
        {roleCode === 'USER' && <label className="management-check"><input type="checkbox" checked={accessGranted} onChange={e => setAccessGranted(e.target.checked)} />{t('access.grant')}</label>}
        {editing.hasAccount && <label className="management-check"><input type="checkbox" checked={loginLocked} onChange={e => setLoginLocked(e.target.checked)} />{t('management.lockLogin')}</label>}
        {!editing.hasAccount && <><label>{t('management.newPassword')} *<input className="form-input" type="password" autoComplete="new-password" required minLength={8} maxLength={72} value={password} onChange={e => setPassword(e.target.value)} /></label><label>{t('management.confirmPassword')} *<input className="form-input" type="password" autoComplete="new-password" required minLength={8} maxLength={72} value={confirmPassword} onChange={e => setConfirmPassword(e.target.value)} /></label></>}
      </div><button className="btn btn-primary" disabled={busy}>{editing.hasAccount ? t('management.saveAccount') : t('management.createLogin')}</button></form>
      {editing.hasAccount && <form onSubmit={e => { e.preventDefault(); void resetPassword(); }}><h3>{t('management.resetPassword')}</h3><div className="management-fields"><label>{t('management.newPassword')} *<input className="form-input" type="password" autoComplete="new-password" required minLength={8} maxLength={72} value={password} onChange={e => setPassword(e.target.value)} /></label><label>{t('management.confirmPassword')} *<input className="form-input" type="password" autoComplete="new-password" required minLength={8} maxLength={72} value={confirmPassword} onChange={e => setConfirmPassword(e.target.value)} /></label></div><button className="btn btn-secondary" disabled={busy}>{t('management.resetPassword')}</button></form>}
      {editing.hasAccount && ['IT_STAFF', 'PUR_STAFF'].includes(editing.roleCode) && <section><h3>{t('management.reportPermissions')}</h3>{permissions ? <div className="accounts-permissions">{groups.map(group => <div key={group} className="accounts-permission-row"><strong>{t(group === 'ALLOCATIONS' ? 'reports:current_allocations' : `reports:${group.toLowerCase()}`)}</strong><label><input type="checkbox" disabled={busy} checked={permissions[group].view} onChange={e => void changePermission(group, { view: e.target.checked, export: e.target.checked ? permissions[group].export : false })} />{t('reports:view')}</label><label><input type="checkbox" disabled={busy || !permissions[group].view} checked={permissions[group].export} onChange={e => void changePermission(group, { view: true, export: e.target.checked })} />{t('reports:export')}</label></div>)}</div> : permissionError ? <p role="alert" className="alert-banner alert-danger">{permissionError} <button type="button" className="btn btn-secondary" onClick={() => setPermissionRevision(v => v + 1)}>{t('management.retry')}</button></p> : <p role="status">{t('management.loading')}</p>}</section>}</>}
      </div></div></div>}
    {loading ? <p role="status">{t('management.loading')}</p> : <div className="table-container"><table className="data-table"><thead><tr>
      <th>{t('account.fullName')}</th><th>{t('management.employeeCode')}</th><th>{t('account.role')}</th><th>{t('email')}</th><th>{t('account.department')}</th><th>{t('management.profileStatus')}</th><th>{t('management.loginStatus')}</th><th>{t('management.actions')}</th>
    </tr></thead><tbody>{profiles.map((p) => <tr key={p.id}>
      <td>{p.fullName}</td><td>{p.employeeCode || t('management.needsCode')}</td><td>{t(`roles.${p.roleCode}`)}</td><td>{p.email || '—'}</td><td>{p.departmentName || '—'}</td>
      <td>{p.accountStatus === 'LOCKED' ? t('management.inactiveProfile') : t('management.active')}</td>
      <td>{!p.hasAccount ? t('access.noAccount') : p.loginLocked ? t('management.lockedStatus') : p.roleCode === 'USER' && !p.accessGranted ? t('access.notGranted') : t('management.active')}</td>
      <td className="management-row-actions"><button className="btn btn-secondary" onClick={() => { setSelected(p); setAssetPage(0); }}>{t('management.holdings')}</button>{canEdit && <button className="btn btn-secondary" onClick={() => openForm(p)}>{t('management.edit')}</button>}{canEdit && <button className="btn btn-secondary" disabled={busy} onClick={() => void changeStatus(p)}>{p.accountStatus === 'LOCKED' ? t('management.activateProfile') : t('management.deactivateProfile')}</button>}{admin && p.hasAccount && p.id !== user?.id && <button className="btn btn-secondary" disabled={busy} onClick={() => void changeLoginLock(p)}>{p.loginLocked ? t('management.unlockLogin') : t('management.lockLogin')}</button>}</td>
    </tr>)}</tbody></table>{profiles.length === 0 && <p className="management-empty">{t('management.emptyProfiles')}</p>}</div>}
    <div className="management-pagination"><span>{t('management.total', { count: total })}</span><button className="btn btn-secondary" disabled={loading || page === 0} onClick={() => setPage((v) => v - 1)}>{t('management.previous')}</button><span>{page + 1} / {Math.max(1, totalPages)}</span><button className="btn btn-secondary" disabled={loading || page + 1 >= totalPages} onClick={() => setPage((v) => v + 1)}>{t('management.next')}</button></div>
    {selected && <div className="modal-overlay" onMouseDown={e => { if (e.target === e.currentTarget) close(); }}><div ref={modalRef} className="modal-content modal-lg accounts-dialog" role="dialog" aria-modal="true" aria-label={t('management.holdings')} tabIndex={-1}><div className="modal-header management-heading"><h2>{selected.fullName} · {selected.employeeCode || t('management.needsCode')}</h2><button className="btn btn-secondary" onClick={close}>{t('management.close')}</button></div><div className="accounts-dialog-body">{assetError ? <p role="alert" className="alert-banner alert-danger">{assetError} <button className="btn btn-secondary" onClick={() => setAssetRevision((v) => v + 1)}>{t('management.retry')}</button></p> : assetLoading ? <p role="status">{t('management.loading')}</p> : <>{assets.length ? <div className="table-container"><table className="data-table"><thead><tr><th>{t('management.assetTag')}</th><th>{t('management.assetName')}</th><th>{t('management.assetType')}</th><th>{t('management.seats')}</th></tr></thead><tbody>{assets.map((a, i) => <tr key={`${a.assetId}-${a.allocationId}-${i}`}><td>{a.assetTag}</td><td>{a.name}{a.serialNumber && <small className="management-asset-meta">{t('management.serialNumber')}: {a.serialNumber}</small>}{a.deviceTag && <small className="management-asset-meta">{t('management.deviceTag')}: {a.deviceTag}</small>}</td><td>{t(`handover:categories.${a.category}`, a.category)}</td><td>{a.seats}</td></tr>)}</tbody></table></div> : <p>{t('management.noHoldings')}</p>}<div className="management-pagination"><button className="btn btn-secondary" disabled={assetPage === 0} onClick={() => setAssetPage((v) => v - 1)}>{t('management.previous')}</button><span>{t('documents:pagination.summary', { page: assetPages ? assetPage + 1 : 0, pages: assetPages, count: assetTotal })}</span><button className="btn btn-secondary" disabled={assetPage + 1 >= assetPages} onClick={() => setAssetPage((v) => v + 1)}>{t('management.next')}</button></div></>}</div></div></div>}
    <AccountsImportModal open={importOpen} onClose={() => setImportOpen(false)} onDone={() => setRevision(v => v + 1)} />
  </section>;
}
