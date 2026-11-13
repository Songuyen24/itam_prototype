import { DocumentPagination } from '@/features/documents/components/DocumentPagination';
import { PaginatedContent } from '@/shared/components/PaginatedContent';
import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { httpClient, downloadFile } from '@/shared/api/httpClient';
import type { ApiResponse } from '@/features/catalogs/types/catalog.types';
import './reports.css';
type Permission = { view: boolean; export: boolean };
type Group = 'ASSETS' | 'TRANSACTIONS' | 'ALLOCATIONS';
type Permissions = Record<Group, Permission>;
type Row = Record<string, string | number | null>;
type Result = { items: Row[]; summary: Row[]; total: number };
const groups: Group[] = ['ASSETS', 'TRANSACTIONS', 'ALLOCATIONS'];
export function ReportsPage() {
  const { t } = useTranslation('reports');
  const [group, setGroup] = useState<Group>('ASSETS');
  const [permissions, setPermissions] = useState<Permissions | null>(null);
  const [options, setOptions] = useState<Record<string,{id: string | number; name: string}[]>>({});
  const [filters, setFilters] = useState<Record<string, string>>({});
  const [page, setPage] = useState(0); const [revision, setRevision] = useState(0);
  const [data, setData] = useState<Result | null>(null); const [error, setError] = useState('');
  const [busy, setBusy] = useState(false); const [exporting, setExporting] = useState(false);
  const query = new URLSearchParams(Object.entries(filters).filter(([,v])=>v));
  query.set('page', String(page)); query.set('size','20'); const qs=query.toString();
  useEffect(()=>{let active=true; httpClient<ApiResponse<Permissions>>('/v1/reports/permissions').then(r=>{if(active)setPermissions(r.data);}).catch(e=>{if(active)setError(e.message);}); return ()=>{active=false;};},[revision]);
  useEffect(()=>{let active=true;setData(null);if(!permissions?.[group].view)return;
    setBusy(true);setError('');httpClient<ApiResponse<Result>>(`/v1/reports/${group.toLowerCase()}?${qs}`).then(r=>{if(active){setData(r.data);setPage(current=>Math.min(current,Math.max(0,Math.ceil(r.data.total/20)-1)));}}).catch(e=>{if(active)setError(e.message);}).finally(()=>{if(active)setBusy(false);});return ()=>{active=false;};
  },[group,qs,permissions,revision]);
  useEffect(()=>{let active=true;setOptions({});if(!permissions?.[group].view)return;httpClient<ApiResponse<Record<string,{id:string|number;name:string}[]>>>(`/v1/reports/${group.toLowerCase()}/options`).then(r=>{if(active)setOptions(r.data);}).catch(e=>{if(active)setError(e.message);});return ()=>{active=false;};},[group,permissions]);
  const exportReport=async()=>{setExporting(true);setError('');try{await downloadFile(`/v1/reports/${group.toLowerCase()}/export?${qs}`,`itam-${group.toLowerCase()}.xlsx`);}catch(e){setError(e instanceof Error?e.message:String(e));}finally{setExporting(false);}};
  const label=(g: Group)=>g==='ASSETS'?t('assets'):g==='TRANSACTIONS'?t('transactions'):t('current_allocations');
  const title=(key: string)=>key==='count'&&group==='TRANSACTIONS'?t('transaction_count'):t('column_'+key, {defaultValue:key});
  const valueLabel=(key:string,value:string|number|null)=>value===null?'—': ['status','category','type'].includes(key)?t('value_'+value,{defaultValue:String(value)}):value;
  const hiddenColumns = new Set(['asset_id','department_id','location_id','assigned_to','transaction_id','requester_id','processed_by']);
  const table=(rows: Row[])=>{
    if(!rows.length)return <p className="reports-message">{t('no_data')}</p>;
    const priority=['transaction_code','asset_tag','name','requester_name','processor_name','assigned_user','license_holders','employee_seats'];
    const columns=Object.keys(rows[0]).filter(k=>!hiddenColumns.has(k)).sort((a,b)=>(priority.includes(a)?priority.indexOf(a):99)-(priority.includes(b)?priority.indexOf(b):99));
    return <div className="reports-table-wrap content-card"><table className="data-table"><thead><tr>{columns.map(k=><th key={k} style={{whiteSpace:'nowrap'}}>{title(k)}</th>)}</tr></thead><tbody>{rows.map((row,i)=><tr key={i}>{columns.map(k=><td key={k} style={{whiteSpace:'nowrap'}}>{valueLabel(k,row[k])}</td>)}</tr>)}</tbody></table></div>;
  };
  return <div className="t18-page reports-page"><header className="page-header"><h1 className="page-title">{t('reports')}</h1><p className="page-description">{t('assets_and_allocations_show_current_state_not_historical_inventory')}</p></header>
    {error&&<div className="reports-alert" role="alert">{error}<button className="btn btn-secondary" onClick={()=>setRevision(r=>r+1)}>{t('retry')}</button></div>}
    <div className="reports-group"><label>{t('group')}<select className="form-select" value={group} onChange={e=>{setGroup(e.target.value as Group);setFilters({});setPage(0);}}>{groups.map(g=><option key={g} value={g}>{label(g)}</option>)}</select></label></div><h2>{label(group)}</h2>
    {!permissions?<p className="reports-message" role="status">{t('loading')}</p>:!permissions[group].view?<p className="reports-message">{t('view_permission_not_granted')}</p>:<><div className="reports-filters">
    {(group==='TRANSACTIONS'?['type','status','actorId','from','to']:['type','status','departmentId','locationId','userId']).map(k=><label key={k}>{title(k)}{k==='from'||k==='to'?<input className="form-input" aria-label={title(k)} type="date" value={filters[k]??''} onChange={e=>{setFilters({...filters,[k]:e.target.value});setPage(0);}}/>:<select className="form-select" aria-label={title(k)} value={filters[k]??''} onChange={e=>{setFilters({...filters,[k]:e.target.value});setPage(0);}}><option value="">—</option>{(options[k]??[]).map(o=><option key={o.id} value={o.id}>{(k==='status'||k==='type')?t('value_'+o.id,{defaultValue:o.name}):o.name}</option>)}</select>}</label>)}
    {group==='TRANSACTIONS'&&<label>{t('date_basis')}<select className="form-select" value={filters.dateField??'created'} onChange={e=>{setFilters({...filters,dateField:e.target.value});setPage(0);}}><option value="created">{t('created')}</option><option value="completed">{t('actual_completion')}</option></select></label>}</div>
    <button className="btn btn-secondary reports-export" disabled={!permissions[group].export||exporting} onClick={exportReport}>{t('export_all_results_to_excel')}</button>
    {busy?<p className="reports-message" role="status">{t('loading')}</p>:data&&<div className="reports-results"><p>{t('total')}: {data.total}</p><PaginatedContent key={group} items={data.summary}>{table}</PaginatedContent>{table(data.items)}<DocumentPagination page={page} totalPages={Math.ceil(data.total/20)} totalElements={data.total} loading={busy} onPageChange={setPage}/></div>}</>}

  </div>;
}
