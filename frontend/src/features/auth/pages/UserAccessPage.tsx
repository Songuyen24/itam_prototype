import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { httpClient } from '@/shared/api/httpClient';
import { ApiResponse, PageResponse } from '@/features/catalogs/types/catalog.types';

type Person = { id: number; fullName: string; email: string; roleCode: string; accountStatus: string; accessGranted: boolean; hasAccount: boolean; loginLocked: boolean };
export function UserAccessPage() {
  const { t } = useTranslation('auth');
  const [people,setPeople] = useState<Person[]>([]);
  const [keyword,setKeyword] = useState('');
  const [page,setPage] = useState(0);
  const [pages,setPages] = useState(0);
  const [selected,setSelected] = useState<Person|null>(null);
  const [password,setPassword] = useState('');
  const [granted,setGranted] = useState(false);
  const [locked,setLocked] = useState(false);
  const [loading,setLoading] = useState(true);
  const [busy,setBusy] = useState(false);
  const [error,setError] = useState('');
  const [notice,setNotice] = useState('');
  const [revision,setRevision] = useState(0);
  useEffect(()=>{
    let active=true; setLoading(true); setError('');
    httpClient<ApiResponse<PageResponse<Person>>>(`/v1/users?${new URLSearchParams({keyword,page:String(page),size:'20'})}`)
      .then(result=>{if(active){setPeople(result.data.content);setPages(result.data.totalPages);setPage(current=>Math.min(current,Math.max(0,result.data.totalPages-1)));}})
      .catch(cause=>{if(active)setError(cause instanceof Error?cause.message:t('access.error'));})
      .finally(()=>{if(active)setLoading(false);});
    return ()=>{active=false;};
  },[keyword,page,revision,t]);
  const select=(person:Person)=>{setSelected(person);setPassword('');setGranted(person.accessGranted);setLocked(person.loginLocked);setNotice('');};
  const save=async()=>{
    if(!selected)return; setBusy(true);setError('');
    try {
      await httpClient(`/v1/users/${selected.id}/access`,{method:'PUT',body:JSON.stringify({granted,status:locked?'LOCKED':'ACTIVE',password:password||undefined})});
      setPassword('');setSelected(null);setNotice(t('access.saved'));setRevision(v=>v+1);
    } catch(cause){setError(cause instanceof Error?cause.message:t('access.error'));}
    finally{setBusy(false);}
  };
  return <section>
    <h1 className="page-title">{t('access.title')}</h1><p>{t('access.description')}</p>
    {error&&<p role="alert" className="alert-banner alert-danger">{error}</p>}
    {notice&&<p role="status">{notice}</p>}
    <label>{t('access.search')}<input className="form-input" value={keyword} maxLength={100} onChange={e=>{setKeyword(e.target.value);setPage(0);}}/></label>
    {loading?<p role="status">{t('access.loading')}</p>:<div className="table-container"><table className="data-table"><thead><tr>
      <th>{t('access.person')}</th><th>{t('access.account')}</th><th>{t('access.permission')}</th><th>{t('access.action')}</th>
    </tr></thead><tbody>{people.map(person=><tr key={person.id}><td>{person.fullName}<br/>{person.email}</td>
      <td>{person.hasAccount?person.roleCode:t('access.noAccount')}</td>
      <td>{person.roleCode!=='USER'?t('access.staff'):person.loginLocked?t('access.locked'):person.accessGranted?t('access.granted'):t('access.notGranted')}</td>
      <td>{person.roleCode==='USER'&&<button className="btn btn-secondary" disabled={busy} onClick={()=>select(person)}>{t('access.manage')}</button>}</td>
    </tr>)}</tbody></table>{!people.length&&<p>{t('access.empty')}</p>}</div>}
    <div className="receiving-actions"><button className="btn btn-secondary" disabled={loading||page===0} onClick={()=>setPage(v=>v-1)}>{t('access.previous')}</button>
      <span>{page+1} / {Math.max(1,pages)}</span><button className="btn btn-secondary" disabled={loading||page+1>=pages} onClick={()=>setPage(v=>v+1)}>{t('access.next')}</button></div>
    {selected&&<form className="card" style={{padding:20,maxWidth:600,marginTop:20}} onSubmit={e=>{e.preventDefault();void save();}}>
      <h2>{selected.fullName}</h2><p>{t('access.linkHint')}</p>
      <label style={{display:'flex',gap:8,marginBottom:12}}><input type="checkbox" checked={granted} disabled={busy} onChange={e=>setGranted(e.target.checked)}/>{t('access.grant')}</label>
      <label style={{display:'flex',gap:8,marginBottom:12}}><input type="checkbox" checked={locked} disabled={busy} onChange={e=>setLocked(e.target.checked)}/>{t('access.lock')}</label>
      <div className="form-group"><label style={{display:'block'}}>{t('access.password')}<input className="form-input" type="password" autoComplete="new-password" minLength={8} maxLength={128} required={granted&&!selected.hasAccount} disabled={busy} value={password} onChange={e=>setPassword(e.target.value)}/></label></div>
      <div className="receiving-actions"><button className="btn btn-primary" disabled={busy}>{t('access.save')}</button><button className="btn btn-secondary" type="button" disabled={busy} onClick={()=>{setSelected(null);setPassword('');}}>{t('access.cancel')}</button></div>
    </form>}
  </section>;
}
