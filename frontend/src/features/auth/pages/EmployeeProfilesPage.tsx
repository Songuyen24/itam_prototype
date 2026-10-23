import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useAuth } from '../contexts/AuthContext';
import { managementApi, type EmployeeProfile, type ProfileInput } from '../api/managementApi';
import type { HeldAsset } from '../api/managementApi';
import type { Department } from '@/features/catalogs/types/catalog.types';
import { departmentApi } from '@/features/catalogs/api/catalogApi';
import { useSearchParams } from 'react-router-dom';

const emptyForm: ProfileInput = { fullName: '', employeeCode: '', email: null, departmentId: null };

export function EmployeeProfilesPage() {
  const { t } = useTranslation('auth');
  const { user } = useAuth();
  const [searchParams] = useSearchParams();
  const canEdit = user?.role === 'ADMIN' || user?.role === 'IT_STAFF';
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
  const [departments, setDepartments] = useState<Department[]>([]);
  const [departmentError, setDepartmentError] = useState('');
  const [departmentRevision, setDepartmentRevision] = useState(0);
  const [selected, setSelected] = useState<EmployeeProfile | null>(null);
  const [assets, setAssets] = useState<HeldAsset[]>([]);
  const [assetPage, setAssetPage] = useState(0);
  const [assetPages, setAssetPages] = useState(0);
  const [assetLoading, setAssetLoading] = useState(false);
  const [assetError, setAssetError] = useState('');
  const [assetRevision, setAssetRevision] = useState(0);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    let active = true;
    setLoading(true); setError('');
    managementApi.profiles(keyword, page).then(({ data }) => {
      if (active) { setProfiles(data.content); setTotalPages(data.totalPages); setTotal(data.totalElements); }
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
      if (active) { setAssets(data.assets.content); setAssetPages(data.assets.totalPages); }
    }).catch((cause) => { if (active) setAssetError(cause instanceof Error ? cause.message : t('management.error')); })
      .finally(() => { if (active) setAssetLoading(false); });
    return () => { active = false; };
  }, [selected, assetPage, assetRevision, revision, t]);

  const openForm = (profile: EmployeeProfile | 'new') => {
    setEditing(profile); setError('');
    setForm(profile === 'new' ? emptyForm : { fullName: profile.fullName, employeeCode: profile.employeeCode ?? '', email: profile.email || null, departmentId: profile.departmentId ?? null });
  };

  const save = async () => {
    if (!editing) return;
    setBusy(true); setError('');
    try {
      const input = { ...form, fullName: form.fullName.trim(), employeeCode: form.employeeCode.trim(), email: form.email?.trim() || null };
      if (editing === 'new') await managementApi.createProfile(input);
      else await managementApi.updateProfile(editing.id, input);
      setEditing(null); setNotice(t('management.saved')); setRevision((v) => v + 1);
    } catch (cause) { setError(cause instanceof Error ? cause.message : t('management.error')); }
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
    <div className="page-header management-heading"><h1 className="page-title">{t('management.profiles')}</h1>{canEdit && <button className="btn btn-primary" onClick={() => openForm('new')}>{t('management.addProfile')}</button>}</div>
    {notice && <p role="status" className="alert-banner alert-success">{notice}</p>}
    {departmentError && <p role="alert" className="alert-banner alert-danger">{departmentError} <button className="btn btn-secondary" onClick={() => setDepartmentRevision((v) => v + 1)}>{t('management.retry')}</button></p>}
    {error && <p role="alert" className="alert-banner alert-danger">{error} <button className="btn btn-secondary" onClick={() => setRevision((v) => v + 1)}>{t('management.retry')}</button></p>}
    <label className="management-search">{t('management.search')}<input className="form-input" value={keyword} maxLength={100} onChange={(e) => { setKeyword(e.target.value); setPage(0); }} /></label>
    {editing && <form className="management-editor" onSubmit={(e) => { e.preventDefault(); void save(); }}>
      <h2>{editing === 'new' ? t('management.addProfile') : t('management.editProfile')}</h2>
      <div className="management-fields">
        <label>{t('account.fullName')} *<input className="form-input" required maxLength={255} value={form.fullName} onChange={(e) => setForm({ ...form, fullName: e.target.value })} /></label>
        <label>{t('management.employeeCode')}{(editing === 'new' || !!editing.employeeCode) && ' *'}<input className="form-input" required={editing === 'new' || !!editing.employeeCode} maxLength={100} value={form.employeeCode} onChange={(e) => setForm({ ...form, employeeCode: e.target.value })} /></label>
        <label>{t('email')}<input className="form-input" type="email" maxLength={255} value={form.email ?? ''} onChange={(e) => setForm({ ...form, email: e.target.value })} /></label>
        <label>{t('account.department')}<select className="form-input" value={form.departmentId ?? ''} onChange={(e) => setForm({ ...form, departmentId: e.target.value ? Number(e.target.value) : null })}><option value="">{t('management.none')}</option>{departments.map((d) => <option key={d.departmentId} value={d.departmentId}>{d.name}</option>)}</select></label>
        {editing !== 'new' && editing.employeeCode && editing.employeeCode !== form.employeeCode && <label>{t('management.correctionReason')} *<input className="form-input" required maxLength={1000} value={form.employeeCodeCorrectionReason ?? ''} onChange={(e) => setForm({ ...form, employeeCodeCorrectionReason: e.target.value })} /></label>}
      </div>
      <div className="management-actions"><button className="btn btn-primary" disabled={busy}>{t('management.save')}</button><button type="button" className="btn btn-secondary" disabled={busy} onClick={() => setEditing(null)}>{t('management.cancel')}</button></div>
    </form>}
    {loading ? <p role="status">{t('management.loading')}</p> : <div className="table-container"><table className="data-table"><thead><tr><th>{t('account.fullName')}</th><th>{t('management.employeeCode')}</th><th>{t('email')}</th><th>{t('account.department')}</th><th>{t('management.devices')}</th><th>{t('management.seats')}</th><th>{t('management.status')}</th><th>{t('management.actions')}</th></tr></thead><tbody>{profiles.map((p) => <tr key={p.id}><td>{p.fullName}</td><td>{p.employeeCode || t('management.needsCode')}</td><td>{p.email || '—'}</td><td>{p.departmentName || '—'}</td><td>{p.deviceCount ?? '—'}</td><td>{p.licenseSeats ?? '—'}</td><td>{p.accountStatus === 'LOCKED' ? t('management.lockedStatus') : t('management.active')}</td><td className="management-row-actions"><button className="btn btn-secondary" onClick={() => { setSelected(p); setAssetPage(0); }}>{t('management.holdings')}</button>{canEdit && <button className="btn btn-secondary" onClick={() => openForm(p)}>{t('management.edit')}</button>}{canEdit && <button className="btn btn-secondary" disabled={busy} onClick={() => void changeStatus(p)}>{p.accountStatus === 'LOCKED' ? t('management.unlock') : t('management.lock')}</button>}</td></tr>)}</tbody></table>{profiles.length === 0 && <p className="management-empty">{t('management.emptyProfiles')}</p>}</div>}
    <div className="management-pagination"><span>{t('management.total', { count: total })}</span><button className="btn btn-secondary" disabled={loading || page === 0} onClick={() => setPage((v) => v - 1)}>{t('management.previous')}</button><span>{page + 1} / {Math.max(1, totalPages)}</span><button className="btn btn-secondary" disabled={loading || page + 1 >= totalPages} onClick={() => setPage((v) => v + 1)}>{t('management.next')}</button></div>
    {selected && <section className="management-details"><div className="management-heading"><h2>{selected.fullName} · {selected.employeeCode || t('management.needsCode')}</h2><button className="btn btn-secondary" onClick={() => setSelected(null)}>{t('management.close')}</button></div>{assetError ? <p role="alert" className="alert-banner alert-danger">{assetError} <button className="btn btn-secondary" onClick={() => setAssetRevision((v) => v + 1)}>{t('management.retry')}</button></p> : assetLoading ? <p role="status">{t('management.loading')}</p> : <>{assets.length ? <div className="table-container"><table className="data-table"><thead><tr><th>{t('management.assetTag')}</th><th>{t('management.assetName')}</th><th>{t('management.assetType')}</th><th>{t('management.seats')}</th></tr></thead><tbody>{assets.map((a, i) => <tr key={`${a.assetId}-${a.allocationId}-${i}`}><td>{a.assetTag}</td><td>{a.name}{a.serialNumber && <small className="management-asset-meta">{t('management.serialNumber')}: {a.serialNumber}</small>}{a.deviceTag && <small className="management-asset-meta">{t('management.deviceTag')}: {a.deviceTag}</small>}</td><td>{t(`handover:categories.${a.category}`, a.category)}</td><td>{a.seats}</td></tr>)}</tbody></table></div> : <p>{t('management.noHoldings')}</p>}<div className="management-pagination"><button className="btn btn-secondary" disabled={assetPage === 0} onClick={() => setAssetPage((v) => v - 1)}>{t('management.previous')}</button><span>{assetPage + 1} / {Math.max(1, assetPages)}</span><button className="btn btn-secondary" disabled={assetPage + 1 >= assetPages} onClick={() => setAssetPage((v) => v + 1)}>{t('management.next')}</button></div></>}</section>}
  </section>;
}
