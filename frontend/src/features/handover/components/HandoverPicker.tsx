import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { ApiResponse, PageResponse } from '@/features/catalogs/types/catalog.types';

export function HandoverPicker<T>({ title, load, identify, label, selected, choose, disabled, unavailable }: {
  title: string; load: (keyword: string, page: number) => Promise<ApiResponse<PageResponse<T>>>;
  identify: (item: T) => number; label: (item: T) => string; selected: number[]; choose: (item: T) => void;
  disabled: boolean; unavailable?: (item: T) => boolean;
}) {
  const { t } = useTranslation('handover');
  const [keyword, setKeyword] = useState('');
  const [page, setPage] = useState(0);
  const [items, setItems] = useState<T[]>([]);
  const [pages, setPages] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [reload, setReload] = useState(0);
  useEffect(() => {
    let active = true;
    setLoading(true); setError(''); setItems([]);
    load(keyword, page).then(r => {
      if (active) { setItems(r.data.content); setPages(r.data.totalPages); }
    }).catch((e: unknown) => { if (active) setError(e instanceof Error ? e.message : t('error')); })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [keyword, page, reload, load, t]);
  return <section className="handover-picker" aria-busy={loading}>
    <label className="form-label">{title}<input className="form-input" type="search" value={keyword} disabled={disabled}
      onChange={e => { setKeyword(e.target.value); setPage(0); }} /></label>
    {loading ? <p role="status">{t('loading')}</p> : error ? <p role="alert">{error} <button type="button" onClick={() => setReload(r => r + 1)}>{t('retry')}</button></p>
      : items.length === 0 ? <p>{t('empty')}</p> : <div className="handover-options">{items.map(item => <button type="button" key={identify(item)}
        disabled={disabled || unavailable?.(item)} aria-pressed={selected.includes(identify(item))} onClick={() => choose(item)}>
        {selected.includes(identify(item)) ? '✓ ' : ''}{label(item)}
      </button>)}</div>}
    <div className="handover-pagination">
      <button type="button" className="btn btn-secondary" disabled={disabled || loading || page === 0} onClick={() => setPage(p => p - 1)}>{t('previous')}</button>
      <span>{page + 1} / {Math.max(1, pages)}</span>
      <button type="button" className="btn btn-secondary" disabled={disabled || loading || page + 1 >= pages} onClick={() => setPage(p => p + 1)}>{t('next')}</button>
    </div>
  </section>;
}
