import { useCallback, useEffect, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useAuth } from '@/features/auth/contexts/AuthContext';
import { dashboardApi, DashboardPeriod, DashboardSummary } from '../api/dashboardApi';
import './dashboard.css';

type Translate = (key: string, options?: Record<string, unknown>) => string;
export function dashboardLabel(t: Translate, group: string, label: string) {
  if (group === 'byStatus') return t(`common:status.${label}`, { defaultValue: label });
  if ((group === 'byLocation' || group === 'byDepartment') && !label) return t('unassigned', { defaultValue: label });
  return label;
}
const types = ['IMPORT','HANDOVER','RECOVERY','DISPOSAL'];
export function DashboardPage() {
  const { t, i18n } = useTranslation('dashboard');
  const { user } = useAuth();
  const [data, setData] = useState<DashboardSummary | null>(null);
  const [filter, setFilter] = useState<DashboardPeriod>({period:'thisMonth'});
  const [applied, setApplied] = useState<DashboardPeriod>({period:'thisMonth'});
  const [filterOpen, setFilterOpen] = useState(false);
  const dialogRef = useRef<HTMLDialogElement>(null);
  const [loading, setLoading] = useState(true);
  const [exporting, setExporting] = useState(false);
  const [error, setError] = useState('');
  const request = useRef(0);
  useEffect(() => {
    const dialog = dialogRef.current;
    if (!filterOpen || !dialog) return;
    const opener = document.activeElement;
    dialog.showModal();
    return () => {
      dialog.close();
      if (opener instanceof HTMLElement && opener.isConnected) opener.focus();
    };
  }, [filterOpen]);
  const load = useCallback(async () => {
    const id = ++request.current; setLoading(true); setError('');
    try { const response=await dashboardApi.summary(applied); if(id===request.current) setData(response.data); }
    catch(e) { if(id===request.current) setError(e instanceof Error ? e.message : t('error')); }
    finally { if(id===request.current) setLoading(false); }
  }, [applied,t]);
  useEffect(() => { void load(); }, [load]);
  const exportReport = async () => { setExporting(true); setError(''); try { await dashboardApi.exportAssets(); } catch (e) { setError(e instanceof Error ? e.message : t('exportError')); } finally { setExporting(false); } };
  const date = (value:string) => new Date(value).toLocaleString(i18n?.language ?? 'vi');
  const max = Math.max(1,...(data?.daily ?? []).map(row=>Number(row.transactions)));
  const days = [...new Set((data?.daily ?? []).map(row=>row.day))];
  const units = ['devices','components','packages','seats'] as const;
  return <div className="t18-page dashboard-page">
    <header className="page-header dashboard-header"><h1 className="page-title">{t('title')}</h1><div className="dashboard-header-actions"><button className="btn btn-secondary dashboard-period-trigger" type="button" aria-haspopup="dialog" aria-expanded={filterOpen} onClick={()=>{setFilter({...applied});setFilterOpen(true);}}><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" aria-hidden="true"><rect x="3" y="5" width="18" height="16" rx="2"/><path d="M16 3v4M8 3v4M3 11h18"/></svg>{applied.period==='custom' ? `${applied.from} – ${applied.to}` : t(applied.period)}</button>{user?.role === 'ADMIN' && <button className="btn btn-primary" onClick={exportReport} disabled={exporting}>{exporting ? t('exporting') : t('export')}</button>}</div></header>
    {filterOpen && <dialog ref={dialogRef} className="dashboard-period-dialog" aria-labelledby="dashboard-period-title" onCancel={()=>setFilterOpen(false)}><h2 id="dashboard-period-title">{t('period')}</h2><form className="dashboard-filters" onSubmit={e=>{e.preventDefault();setApplied({...filter});setFilterOpen(false);}}>
      <label>{t('period')}<select value={filter.period} onChange={e=>setFilter({...filter,period:e.target.value as DashboardPeriod['period']})}>{['thisMonth','lastMonth','custom'].map(value=><option value={value} key={value}>{t(value)}</option>)}</select></label>
      {filter.period==='custom' && <><label>{t('from')}<input type="date" required value={filter.from??''} max={filter.to} onChange={e=>setFilter({...filter,from:e.target.value})}/></label><label>{t('to')}<input type="date" required min={filter.from} value={filter.to??''} onChange={e=>setFilter({...filter,to:e.target.value})}/></label></>}
      <div className="dashboard-header-actions"><button className="btn btn-secondary" type="button" onClick={()=>setFilterOpen(false)}>{t('common:buttons.cancel')}</button><button className="btn btn-primary" disabled={loading}>{t('apply')}</button></div>
    </form></dialog>}
    <p role="status" className="sr-status">{loading ? t('loading') : t('ready')}</p>
    {error && <div className="alert-banner alert-danger" role="alert">{error}<button className="btn btn-secondary btn-sm" onClick={load}>{t('retry')}</button></div>}
    {!loading && data && <>
      <section aria-labelledby="dashboard-current"><h2 id="dashboard-current">{t('current')}</h2>
        <div className="dashboard-inventory">{data.inventory.map(row=><article className="content-card metric-group" key={row.category}><h3>{t(`category.${row.category}`)}</h3><strong className="dashboard-total">{row.count}</strong></article>)}</div>
        {data.inventory.filter(row=>row.category==='LICENSE').map(row=><div className="content-card dashboard-license-summary" key={row.category}><dl className="dashboard-seats">{(['total_seats','active_seats','reserved_seats','available_seats'] as const).map(key=><div key={key}><dt>{t(key)}</dt><dd>{row[key]}</dd></div>)}</dl></div>)}
        <div className="content-card metric-group dashboard-status-card"><h3>{t('byStatus')}</h3><p>{t('damagedCondition')}: <strong>{data.damagedDevices}</strong></p><div className="dashboard-statuses">{Object.entries(data.byStatus).map(([label,count])=><div className="metric-row" key={label}><span>{dashboardLabel(t,'byStatus',label)}</span><strong>{count}</strong></div>)}</div></div>
      </section>
      <section aria-labelledby="dashboard-period"><div className="dashboard-section-heading"><h2 id="dashboard-period">{t('movements')}</h2><span className="dashboard-date-range">{data.from} – {data.to}</span></div>
        <div className="dashboard-inventory dashboard-movements">{types.map(type=>{const row=data.movements.find(r=>r.type===type);return <article className="content-card metric-group" key={type}><h3>{t(`type.${type}`)}</h3><div className="dashboard-voucher-total"><strong className="dashboard-total">{row?.transactions??0}</strong><span>{t('transactions')}</span></div><dl className="dashboard-seats">{units.map(unit=><div key={unit}><dt>{t(unit)}</dt><dd>{row ? row[unit] ?? t('unavailable') : 0}</dd></div>)}{type==='HANDOVER' && <div><dt>{t('recipients')}</dt><dd>{row?.recipients??0}</dd></div>}</dl></article>;})}</div>
      </section>
      <div className="dashboard-grid">
        <section className="content-card metric-group"><h2>{t('daily')}</h2><div className="dashboard-legend">{types.map(type=><span key={type}><i className={`dashboard-color dashboard-${type}`}/>{t(`type.${type}`)}</span>)}</div>
          {days.length===0 ? <p className="dashboard-empty">{t('empty')}</p> : <div className="dashboard-chart" role="img" aria-label={t('daily')}><div className="dashboard-chart-days">{days.map(day=><div className="dashboard-day" key={day}><div className="dashboard-bars">{types.map(type=>{const count=Number(data.daily.find(row=>row.day===day&&row.type===type)?.transactions??0);return <div key={type} className="dashboard-bar-slot"><span>{count}</span><div title={`${day} · ${t(`type.${type}`)}: ${count}`} className={`dashboard-bar dashboard-${type}`} style={{height:`${count/max*140}px`}}/></div>;})}</div><small>{day}</small></div>)}</div></div>}
        </section>
        <section className="content-card metric-group dashboard-attention"><h2>{t('actions')}</h2>{(['pending','drafts'] as const).map(group=><div key={group}><h3>{t(group)}</h3>{Object.entries(data[group]).length===0 ? <p className="dashboard-empty">{t('none')}</p> : Object.entries(data[group]).map(([type,count])=><div className="metric-row" key={type}><span>{t(`type.${type}`)}</span><strong>{count}</strong></div>)}</div>)}<div className="metric-row"><span>{t('failedEmails')}</span><strong>{data.failedEmails}</strong></div><div className="metric-row"><span>{t('failedPdfs')}</span><strong>{data.failedPdfs}</strong></div></section>
      </div>
      <section className="content-card metric-group"><h2>{t('recent')}</h2>{data.recent.length===0 ? <p className="dashboard-empty">{t('empty')}</p> : <div className="dashboard-table-wrap"><table className="data-table"><thead><tr>{['code','completed','operation','actor','recipient','status',...units].map(key=><th key={key}>{t(key)}</th>)}</tr></thead><tbody>{data.recent.map(row=><tr key={row.transaction_id}><td><a href={`/documents?transactionId=${row.transaction_id}`}>{row.transaction_code}</a></td><td>{date(row.completed_at)}</td><td>{t(`type.${row.type}`)}</td><td>{row.actor||'—'}</td><td>{row.recipient||'—'}</td><td>{t(`common:status.${row.status}`,{defaultValue:row.status})}</td>{units.map(unit=><td key={unit}>{row[unit]??t('unavailable')}</td>)}</tr>)}</tbody></table></div>}</section>
    </>}
  </div>;
}
