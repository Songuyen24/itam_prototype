import { useEffect, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { httpClient } from '@/shared/api/httpClient';
import './audit.css';

type Entry = { id: number; createdAt: string; actor: string; action: string; entityType: string;
  entityId: number; oldData: Record<string, unknown>; newData: Record<string, unknown>; details?: string; transactionId?: number; result: string };
type Page = { content: Entry[]; page: number; totalPages: number; totalElements: number };
type Filter = { assetId: string; actorId: string; action: string; from: string; to: string };
const initial: Filter = { assetId: '', actorId: '', action: '', from: '', to: '' };
const fields = ['assetId', 'actorId', 'action', 'from', 'to'] as const;
const labels = { assetId: 'asset', actorId: 'actor', action: 'action', from: 'from', to: 'to' };

export function AuditLogsPage() {
  const { t, i18n } = useTranslation('audit');
  const [form, setForm] = useState<Filter>(initial);
  const [filter, setFilter] = useState<Filter>(initial);
  const [page, setPage] = useState(0);
  const [data, setData] = useState<Page | null>(null);
  const [selected, setSelected] = useState<Entry | null>(null);
  const [error, setError] = useState(false);
  const [busy, setBusy] = useState(false);
  const [retry, setRetry] = useState(0);
  const dialog = useRef<HTMLDialogElement>(null);
  const opener = useRef<HTMLTableRowElement | null>(null);

  useEffect(() => {
    if (typeof window === 'undefined' || !window.matchMedia) return;
    const media = window.matchMedia('(max-width: 1200px)');
    const onResize = () => { if (!media.matches) dialog.current?.close(); };
    media.addEventListener('change', onResize);
    return () => media.removeEventListener('change', onResize);
  }, []);

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
    setBusy(true); setError(false); setData(null);
    httpClient<{ data: Page }>(`/v1/audit-logs?${params}`).then(response => {
      if (active) { setData(response.data); setPage(current => Math.min(current, Math.max(0, response.data.totalPages - 1))); }
    }).catch(() => { if (active) setError(true); }).finally(() => { if (active) setBusy(false); });
    return () => { active = false; };
  }, [filter, page, retry]);

  const closeDetail = () => { dialog.current?.close(); setSelected(null); opener.current?.focus(); };
  const clearSelection = () => { dialog.current?.close(); setSelected(null); };
  const showDetail = (row: Entry, tableRow: HTMLTableRowElement) => {
    opener.current = tableRow;
    setSelected(row);
    if (typeof window !== 'undefined' && window.matchMedia?.('(max-width: 1200px)').matches) dialog.current?.showModal();
  };
  const changePage = (next: number) => { clearSelection(); setPage(next); };
  const reset = () => { clearSelection(); setForm(initial); setPage(0); setFilter(initial); };
  const activeFilters = fields.filter(key => filter[key]);
  const detail = selected && <div className="audit-detail-content">
    <header><div><h2>{selected.actor || t('unknown')}</h2><p>{t(`actions.${selected.action}`, selected.action)} · {selected.action}</p></div>
      <button type="button" className="audit-close" onClick={closeDetail} aria-label={t('close')} title={t('close')}>×</button></header>
    <dl className="audit-meta">
      <div><dt>{t('time')}</dt><dd>{new Date(selected.createdAt).toLocaleString(i18n.language)}</dd></div>
      <div><dt>{t('result')}</dt><dd>{t(selected.result === 'FAILURE' ? 'failure' : 'success')}</dd></div>
      <div><dt>{t('target')}</dt><dd>{selected.entityType} #{selected.entityId}</dd></div>
      <div><dt>{t('transaction')}</dt><dd>{selected.transactionId ? `#${selected.transactionId}` : '—'}</dd></div>
      <div><dt>{t('eventId')}</dt><dd>#{selected.id}</dd></div>
    </dl>
    <h3>{t('before')}</h3><pre>{JSON.stringify(selected.oldData, null, 2) || '{}'}</pre>
    <h3>{t('after')}</h3><pre>{JSON.stringify(selected.newData, null, 2) || '{}'}</pre>
    <h3>{t('details')}</h3><pre>{selected.details || '—'}</pre>
    <details><summary>{t('raw')}</summary><pre>{JSON.stringify(selected, null, 2)}</pre></details>
  </div>;

  return <section className="audit-page">
    <h1>{t('title')}</h1>
    <form className="audit-filters" onSubmit={event => { event.preventDefault(); clearSelection(); setPage(0); setFilter({ ...form }); }}>
      {fields.map(key => <label key={key}>{t(labels[key])}
        <input type={key === 'from' || key === 'to' ? 'date' : key === 'action' ? 'text' : 'number'}
          min={key === 'assetId' || key === 'actorId' ? 1 : undefined}
          list={key === 'action' ? 'audit-actions' : undefined} value={form[key]}
          onChange={event => setForm(current => ({ ...current, [key]: event.target.value }))} />
      </label>)}
      <datalist id="audit-actions">{Object.keys(i18n.getResourceBundle?.(i18n.language, 'audit')?.actions || {}).map(action =>
        <option key={action} value={action}>{t(`actions.${action}`)}</option>)}</datalist>
      <button type="submit">{t('filter')}</button>
    </form>
    <div className="audit-applied" aria-live="polite">
      <span>{t('applied')}</span>
      {activeFilters.length ? activeFilters.map(key => <span className="audit-chip" key={key}>{t(labels[key])}: {filter[key]}</span>) : <span>{t('all')}</span>}
      {(activeFilters.length > 0 || fields.some(key => form[key])) && <button type="button" onClick={reset}>{t('reset')}</button>}
    </div>
    <div className="audit-layout">
      <div className="audit-list">
      {error && <div className="audit-state" role="alert">{t('error')} <button type="button" onClick={() => setRetry(value => value + 1)}>{t('retry')}</button></div>}
      {busy && <p className="audit-state" role="status">{t('loading')}</p>}
      {!busy && !error && data && <><div className="audit-table-wrap"><table className="audit-table">
        <thead><tr><th>{t('time')}</th><th>{t('actorName')}</th><th>{t('action')}</th><th>{t('target')}</th><th>{t('result')}</th></tr></thead>
        <tbody>{data.content.map(row => <tr key={row.id} className={selected?.id === row.id ? 'is-selected' : ''}
          tabIndex={0} aria-label={`${t('viewDetails')}: #${row.id}`} aria-selected={selected?.id === row.id}
          onClick={event => showDetail(row, event.currentTarget)}
          onKeyDown={event => {
            if (event.key === 'Enter' || event.key === ' ') {
              event.preventDefault();
              showDetail(row, event.currentTarget);
            }
          }}>
          <td>{new Date(row.createdAt).toLocaleString(i18n.language)}</td>
          <td>{row.actor || t('unknown')}</td><td>{t(`actions.${row.action}`, row.action)}<small>{row.action}</small></td>
          <td>{row.entityType} #{row.entityId}{row.transactionId ? <small>{t('transaction')} #{row.transactionId}</small> : null}</td>
          <td><span className={row.result === 'FAILURE' ? 'audit-result is-failure' : 'audit-result is-success'}>{t(row.result === 'FAILURE' ? 'failure' : 'success')}</span></td>
        </tr>)}</tbody>
      </table></div>
      {data.content.length === 0 && <p className="audit-state">{t('empty')}</p>}
      <div className="audit-pagination">
        <button type="button" disabled={page === 0} onClick={() => changePage(page - 1)}>{t('previous')}</button>
        <span>{t('page', { page: page + 1, total: Math.max(data.totalPages, 1), count: data.totalElements })}</span>
        <button type="button" disabled={page + 1 >= data.totalPages} onClick={() => changePage(page + 1)}>{t('next')}</button>
      </div></>}
      </div>
      <aside className="audit-pane" aria-label={t('details')}>{detail || <p className="audit-state">{t('selectEvent')}</p>}</aside>
    </div>
    <dialog ref={dialog} className="audit-dialog" onClose={() => { setSelected(null); opener.current?.focus(); }} aria-label={t('details')}>{detail}</dialog>
  </section>;
}
