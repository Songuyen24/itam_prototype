import { DocumentPagination } from '@/features/documents/components/DocumentPagination';
import { PaginatedContent } from '@/shared/components/PaginatedContent';
import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { httpClient, downloadFile } from '@/shared/api/httpClient';
import { useAuth } from '@/features/auth/contexts/AuthContext';
import { managementApi, type ManagedAccount } from '@/features/auth/api/managementApi';
import type { ApiResponse } from '@/features/catalogs/types/catalog.types';
type Permission = { view: boolean; export: boolean };
type Group = 'ASSETS' | 'TRANSACTIONS' | 'ALLOCATIONS';
type Permissions = Record<Group, Permission>;
type Row = Record<string, string | number | null>;
type Result = { items: Row[]; summary: Row[]; total: number };
const groups: Group[] = ['ASSETS', 'TRANSACTIONS', 'ALLOCATIONS'];
export function ReportsPage() {
  const { t } = useTranslation('reports');
  const { user } = useAuth();
  const [group, setGroup] = useState<Group>('ASSETS');
  const [permissions, setPermissions] = useState<Permissions | null>(null);
  const [options, setOptions] = useState<Record<string,{id: string | number; name: string}[]>>({});
  const [filters, setFilters] = useState<Record<string, string>>({});
  const [page, setPage] = useState(0); const [revision, setRevision] = useState(0);
  const [data, setData] = useState<Result | null>(null); const [error, setError] = useState('');
  const [busy, setBusy] = useState(false); const [exporting, setExporting] = useState(false);
  const [keyword, setKeyword] = useState(''); const [accountPage, setAccountPage] = useState(0);
  const [accounts, setAccounts] = useState<ManagedAccount[]>([]); const [accountPages, setAccountPages] = useState(0);
  const [accountTotal, setAccountTotal] = useState(0);
  const [account, setAccount] = useState(''); const [grants, setGrants] = useState<Permissions | null>(null);
  const [saving, setSaving] = useState(false);
  const query = new URLSearchParams(Object.entries(filters).filter(([,v])=>v));
  query.set('page', String(page)); query.set('size','20'); const qs=query.toString();
  useEffect(()=>{let active=true; httpClient<ApiResponse<Permissions>>('/v1/reports/permissions').then(r=>{if(active)setPermissions(r.data);}).catch(e=>{if(active)setError(e.message);}); return ()=>{active=false;};},[revision]);
  useEffect(()=>{let active=true;setData(null);if(!permissions?.[group].view)return;
    setBusy(true);setError('');httpClient<ApiResponse<Result>>(`/v1/reports/${group.toLowerCase()}?${qs}`).then(r=>{if(active){setData(r.data);setPage(current=>Math.min(current,Math.max(0,Math.ceil(r.data.total/20)-1)));}}).catch(e=>{if(active)setError(e.message);}).finally(()=>{if(active)setBusy(false);});return ()=>{active=false;};
  },[group,qs,permissions,revision]);
  useEffect(()=>{let active=true;setOptions({});if(!permissions?.[group].view)return;httpClient<ApiResponse<Record<string,{id:string|number;name:string}[]>>>(`/v1/reports/${group.toLowerCase()}/options`).then(r=>{if(active)setOptions(r.data);}).catch(e=>{if(active)setError(e.message);});return ()=>{active=false;};},[group,permissions]);
  useEffect(()=>{if(user?.role!=='ADMIN')return;let active=true;managementApi.accounts(keyword,accountPage,true).then(r=>{if(active){setAccounts(r.data.content);setAccountPages(r.data.totalPages);setAccountTotal(r.data.totalElements);setAccountPage(current=>Math.min(current,Math.max(0,r.data.totalPages-1)));}}).catch(e=>{if(active)setError(e.message);});return ()=>{active=false;};},[user?.role,keyword,accountPage]);
  useEffect(()=>{let active=true;setGrants(null);if(account)httpClient<ApiResponse<Permissions>>(`/v1/reports/permissions/${account}`).then(r=>{if(active)setGrants(r.data);}).catch(e=>{if(active)setError(e.message);});return ()=>{active=false;};},[account]);
  const save=async(g: Group,p: Permission)=>{setSaving(true);setError('');try{const r=await httpClient<ApiResponse<Permissions>>(`/v1/reports/permissions/${account}/${g}`,{method:'PUT',body:JSON.stringify(p)});setGrants(r.data);}catch(e){setError(e instanceof Error?e.message:String(e));}finally{setSaving(false);}};
  const exportReport=async()=>{setExporting(true);setError('');try{await downloadFile(`/v1/reports/${group.toLowerCase()}/export?${qs}`,`itam-${group.toLowerCase()}.xlsx`);}catch(e){setError(e instanceof Error?e.message:String(e));}finally{setExporting(false);}};
  const label=(g: Group)=>g==='ASSETS'?t('assets'):g==='TRANSACTIONS'?t('transactions'):t('current_allocations');
  const title=(key: string)=>key==='count'&&group==='TRANSACTIONS'?t('transaction_count'):t('column_'+key, {defaultValue:key});
  const valueLabel=(key:string,value:string|number|null)=>value===null?'—': ['status','category','type'].includes(key)?t('value_'+value,{defaultValue:String(value)}):value;
  const hiddenColumns = new Set(['asset_id','department_id','location_id','assigned_to','transaction_id','requester_id','processed_by']);
  const table=(rows: Row[])=>{
    if(!rows.length)return <p>{t('no_data')}</p>;
    const priority=['transaction_code','asset_tag','name','requester_name','processor_name','assigned_user','license_holders','employee_seats'];
    const columns=Object.keys(rows[0]).filter(k=>!hiddenColumns.has(k)).sort((a,b)=>(priority.includes(a)?priority.indexOf(a):99)-(priority.includes(b)?priority.indexOf(b):99));
    return <div style={{overflowX:'auto'}}><table className="data-table"><thead><tr>{columns.map(k=><th key={k} style={{whiteSpace:'nowrap'}}>{title(k)}</th>)}</tr></thead><tbody>{rows.map((row,i)=><tr key={i}>{columns.map(k=><td key={k} style={{whiteSpace:'nowrap'}}>{valueLabel(k,row[k])}</td>)}</tr>)}</tbody></table></div>;
  };
  return <div className="t18-page"><h1>{t('reports')}</h1><p>{t('assets_and_allocations_show_current_state_not_historical_inventory')}</p>
    {error&&<div role="alert">{error}<button className="btn btn-secondary" onClick={()=>setRevision(r=>r+1)}>{t('retry')}</button></div>}
    <label>{t('group')}<select className="form-select" value={group} onChange={e=>{setGroup(e.target.value as Group);setFilters({});setPage(0);}}>{groups.map(g=><option key={g} value={g}>{label(g)}</option>)}</select></label><h2>{label(group)}</h2>
    {!permissions?<p role="status">{t('loading')}</p>:!permissions[group].view?<p>{t('view_permission_not_granted')}</p>:<><div style={{display:'flex',flexWrap:'wrap',gap:12}}>
    {(group==='TRANSACTIONS'?['type','status','actorId','from','to']:['type','status','departmentId','locationId','userId']).map(k=><label key={k}>{title(k)}{k==='from'||k==='to'?<input className="form-input" aria-label={title(k)} type="date" value={filters[k]??''} onChange={e=>{setFilters({...filters,[k]:e.target.value});setPage(0);}}/>:<select className="form-select" aria-label={title(k)} value={filters[k]??''} onChange={e=>{setFilters({...filters,[k]:e.target.value});setPage(0);}}><option value="">—</option>{(options[k]??[]).map(o=><option key={o.id} value={o.id}>{(k==='status'||k==='type')?t('value_'+o.id,{defaultValue:o.name}):o.name}</option>)}</select>}</label>)}
    {group==='TRANSACTIONS'&&<label>{t('date_basis')}<select className="form-select" value={filters.dateField??'created'} onChange={e=>{setFilters({...filters,dateField:e.target.value});setPage(0);}}><option value="created">{t('created')}</option><option value="completed">{t('actual_completion')}</option></select></label>}</div>
    <button className="btn btn-secondary" disabled={!permissions[group].export||exporting} onClick={exportReport}>{t('export_all_results_to_excel')}</button>
    {busy?<p role="status">{t('loading')}</p>:data&&<><p>{t('total')}: {data.total}</p><PaginatedContent key={group} items={data.summary}>{table}</PaginatedContent>{table(data.items)}<DocumentPagination page={page} totalPages={Math.ceil(data.total/20)} totalElements={data.total} loading={busy} onPageChange={setPage}/></>}</>}
    {user?.role==='ADMIN'&&<section><h2>{t('report_permissions_by_account')}</h2><input className="form-input" aria-label={t('find_account')} value={keyword} onChange={e=>{setKeyword(e.target.value);setAccountPage(0);}}/><select className="form-select" aria-label={t('account')} value={account} disabled={saving} onChange={e=>setAccount(e.target.value)}><option value="">—</option>{accounts.map(a=><option key={a.id} value={a.id}>{a.fullName} ({a.username||a.email})</option>)}</select><DocumentPagination page={accountPage} totalPages={accountPages} totalElements={accountTotal} loading={saving} onPageChange={setAccountPage}/>
    {grants&&groups.map(g=><div key={g}>{label(g)} <label><input type="checkbox" disabled={saving} checked={grants[g].view} onChange={e=>void save(g,{view:e.target.checked,export:e.target.checked&&grants[g].export})}/>{t('view')}</label><label><input type="checkbox" disabled={saving||!grants[g].view} checked={grants[g].export} onChange={e=>void save(g,{view:true,export:e.target.checked})}/>{t('export')}</label></div>)}</section>}
  </div>;
}
