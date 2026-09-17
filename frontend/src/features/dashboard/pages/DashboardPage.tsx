import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useAuth } from '@/features/auth/contexts/AuthContext';
import { dashboardApi, DashboardSummary } from '../api/dashboardApi';
import './dashboard.css';

type Translate = (key: string, options?: Record<string, unknown>) => string;
export function dashboardLabel(t: Translate, group: string, label: string) {
  if (group === 'byStatus') return t(`common:status.${label}`, { defaultValue: label });
  if ((group === 'byLocation' || group === 'byDepartment') && !label) return t('unassigned', { defaultValue: label });
  return label;
}

export function DashboardPage() {
  const { t } = useTranslation('dashboard');
  const { user } = useAuth();
  const [data, setData] = useState<DashboardSummary | null>(null);
  const [loading, setLoading] = useState(true);
  const [exporting, setExporting] = useState(false);
  const [error, setError] = useState('');
  const load = async () => { setLoading(true); setError(''); try { setData((await dashboardApi.summary()).data); } catch (e) { setError(e instanceof Error ? e.message : t('error')); } finally { setLoading(false); } };
  useEffect(() => { void load(); }, []);
  const exportReport = async () => { setExporting(true); setError(''); try { await dashboardApi.exportAssets(); } catch (e) { setError(e instanceof Error ? e.message : t('exportError')); } finally { setExporting(false); } };
  const groups: [string, string, Record<string, number> | undefined][] = [['byStatus', t('byStatus'), data?.byStatus], ['byType', t('byType'), data?.byType], ['byLocation', t('byLocation'), data?.byLocation], ['byDepartment', t('byDepartment'), data?.byDepartment]];

  return <div className="t18-page"><header className="page-header dashboard-header"><div><h1 className="page-title">{t('title')}</h1><p className="page-description">{t('description')}</p></div>{user?.role === 'ADMIN' && <button className="btn btn-primary" onClick={exportReport} disabled={exporting}>{exporting ? t('exporting') : t('export')}</button>}</header>
    <p role="status" className="sr-status">{loading ? t('loading') : t('ready')}</p>
    {error && <div className="alert-banner alert-danger" role="alert">{error}<button className="btn btn-secondary btn-sm" onClick={load}>{t('retry')}</button></div>}
    {!loading && data && <><div className="metric-strip"><div><span>{t('pendingReceivings')}</span><strong>{data.pendingReceivings}</strong></div><div><span>{t('pendingDisposals')}</span><strong>{data.pendingDisposals}</strong></div></div>
      <div className="dashboard-grid">{groups.map(([group, title, values]) => <section className="content-card metric-group" key={group}><h2>{title}</h2>{Object.entries(values ?? {}).length === 0 ? <p className="empty-state">{t('empty')}</p> : Object.entries(values ?? {}).map(([label, count]) => <div className="metric-row" key={label}><span>{dashboardLabel(t, group, label)}</span><strong>{count}</strong></div>)}</section>)}</div></>}
  </div>;
}
