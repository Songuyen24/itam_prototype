import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { httpClient } from '@/shared/api/httpClient';

type Entry = { id: number; createdAt: string; actor: string; action: string; entityType: string;
  entityId: number; oldData: Record<string, unknown>; newData: Record<string, unknown>; details?: string; transactionId?: number; result: string };
type Page = { content: Entry[]; page: number; totalPages: number; totalElements: number };
type Filter = { assetId: string; actorId: string; action: string; from: string; to: string };
const initial: Filter = { assetId: '', actorId: '', action: '', from: '', to: '' };

export function AuditLogsPage() {
  const { t, i18n } = useTranslation('audit');
  const [form, setForm] = useState<Filter>(initial);
  const [filter, setFilter] = useState<Filter>(initial);
  const [page, setPage] = useState(0);
  const [data, setData] = useState<Page | null>(null);
  const [error, setError] = useState(false);
  const [busy, setBusy] = useState(false);
  useEffect(() => {
    let active = true;
    const params = new URLSearchParams({ page: String(page), size: '20' });
    for (const key of ['assetId', 'actorId', 'action'] as const) if (filter[key]) params.set(key, filter[key]);
    if (filter.from) params.set('from', new Date(`${filter.from}T00:00:00`).toISOString());
    if (filter.to) {
      const end = new Date(`${filter.to}T00:00:00`);
      end.setDate(end.getDate() + 1);
      params.set('to', end.toISOString());
    }
    setBusy(true); setError(false);
    httpClient<{ data: Page }>(`/v1/audit-logs?${params}`).then(response => {
      if (active) { setData(response.data); setPage(current => Math.min(current, Math.max(0, response.data.totalPages - 1))); }
    }).catch(() => { if (active) setError(true); }).finally(() => { if (active) setBusy(false); });
    return () => { active = false; };
  }, [filter, page]);
  const update = (key: keyof Filter, value: string) => setForm(current => ({ ...current, [key]: value }));
  return <section style={{ maxWidth: 1440, margin: '0 auto' }}>
    <h1>{t('title')}</h1>
    <form onSubmit={event => { event.preventDefault(); setPage(0); setFilter({ ...form }); }}
      style={{ display: 'flex', flexWrap: 'wrap', gap: 12, alignItems: 'end', marginBottom: 20 }}>
      {(['assetId', 'actorId', 'action', 'from', 'to'] as const).map(key =>
        <label key={key} style={{ display: 'grid', gap: 4 }}>
          {t(({ assetId: 'asset', actorId: 'actor', action: 'action', from: 'from', to: 'to' })[key])}
          <input type={key === 'from' || key === 'to' ? 'date' : key === 'action' ? 'text' : 'number'}
            min={key === 'assetId' || key === 'actorId' ? 1 : undefined} value={form[key]}
            onChange={event => update(key, event.target.value)} />
        </label>)}
      <button type="submit">{t('filter')}</button>
    </form>
    {error && <p role="alert">{t('error')}</p>}
    {busy && <p role="status">...</p>}
    {!busy && !error && data && <>
      <div style={{ overflowX: 'auto' }}><table style={{ width: '100%', minWidth: 850 }}>
        <thead><tr><th>{t('time')}</th><th>{t('actor')}</th><th>{t('action')}</th><th>{t('target')}</th><th>{t('result')}</th><th>{t('changes')}</th></tr></thead>
        <tbody>{data.content.map(row => <tr key={row.id}>
          <td>{new Date(row.createdAt).toLocaleString(i18n.language)}</td>
          <td>{row.actor || t('unknown')}</td><td>{t(`actions.${row.action}`, row.action)} <small>({row.action})</small></td>
          <td>{row.entityType} #{row.entityId}{row.transactionId ? ` / #${row.transactionId}` : ''}</td>
          <td>{t(row.result === 'FAILURE' ? 'failure' : 'success')}</td>
          <td><details><summary>{t('changes')}</summary><pre style={{ whiteSpace: 'pre-wrap', overflowWrap: 'anywhere' }}>{JSON.stringify({ before: row.oldData, after: row.newData, details: row.details }, null, 2)}</pre></details></td>
        </tr>)}</tbody>
      </table></div>
      {data.content.length === 0 && <p>{t('empty')}</p>}
      <div style={{ display: 'flex', alignItems: 'center', gap: 12, marginTop: 16 }}>
        <button type="button" disabled={page === 0} onClick={() => setPage(page - 1)}>{t('previous')}</button>
        <span>{t('page', { page: page + 1, total: Math.max(data.totalPages, 1), count: data.totalElements })}</span>
        <button type="button" disabled={page + 1 >= data.totalPages} onClick={() => setPage(page + 1)}>{t('next')}</button>
      </div>
    </>}
  </section>;
}
