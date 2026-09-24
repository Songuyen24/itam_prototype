import { FormEvent, useEffect, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useAuth } from '@/features/auth/contexts/AuthContext';
import { ApiError } from '@/shared/api/httpClient';
import { disposalApi, Disposal, DisposalAsset, DisposalCheck, TransactionSummary } from '../api/disposalApi';
import { DisposalDetail, DisposalReview } from '../components/DisposalReview';
import './disposal.css';

export function DisposalsPage() {
  const { t } = useTranslation('disposal');
  const { user } = useAuth();
  const [assets, setAssets] = useState<DisposalAsset[]>([]);
  const [assetKeyword, setAssetKeyword] = useState('');
  const [assetPage, setAssetPage] = useState(0);
  const [assetPages, setAssetPages] = useState(0);
  const [selected, setSelected] = useState<number[]>([]);
  const [pending, setPending] = useState<TransactionSummary[]>([]);
  const [pendingPage, setPendingPage] = useState(0);
  const [pendingPages, setPendingPages] = useState(0);
  const [reload, setReload] = useState(0);
  const [reason, setReason] = useState('');
  const [date, setDate] = useState(new Date().toISOString().slice(0, 10));
  const [assetLoading, setAssetLoading] = useState(true);
  const [pendingLoading, setPendingLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [createReview, setCreateReview] = useState<DisposalCheck | null>(null);
  const [detail, setDetail] = useState<Disposal | null>(null);
  const [detailLoading, setDetailLoading] = useState(false);
  const [decisions, setDecisions] = useState<Record<number, boolean>>({});
  const [action, setAction] = useState<'approve' | 'reject' | null>(null);
  const [rejectReason, setRejectReason] = useState('');
  const detailRequest = useRef(0);
  const createTrigger = useRef<HTMLElement | null>(null);
  const detailTrigger = useRef<HTMLElement | null>(null);

  useEffect(() => {
    let active = true;
    setAssetLoading(true);
    disposalApi.candidates(assetKeyword, assetPage).then(result => {
      if (!active) return;
      const lastPage = Math.max(0, result.data.totalPages - 1);
      setAssetPages(result.data.totalPages);
      if (assetPage > lastPage) { setAssetPage(lastPage); return; }
      setAssets(result.data.content);
    }).catch(cause => { if (active) setError(cause instanceof Error ? cause.message : t('error')); })
      .finally(() => { if (active) setAssetLoading(false); });
    return () => { active = false; };
  }, [assetKeyword, assetPage, reload, t]);

  useEffect(() => {
    let active = true;
    setPendingLoading(true);
    disposalApi.listPending(pendingPage).then(result => {
      if (!active) return;
      const lastPage = Math.max(0, result.data.totalPages - 1);
      setPendingPages(result.data.totalPages);
      if (pendingPage > lastPage) { setPendingPage(lastPage); return; }
      setPending(result.data.content);
    }).catch(cause => { if (active) setError(cause instanceof Error ? cause.message : t('error')); })
      .finally(() => { if (active) setPendingLoading(false); });
    return () => { active = false; };
  }, [pendingPage, reload, t]);

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    createTrigger.current = ((event.nativeEvent as SubmitEvent | undefined)?.submitter as HTMLElement | null) ?? null;
    setSaving(true); setError(''); setNotice('');
    try { setCreateReview((await disposalApi.smartCheck(selected)).data); }
    catch (cause) { setError(cause instanceof Error ? cause.message : t('error')); }
    finally { setSaving(false); }
  };

  const create = async () => {
    if (!createReview) return;
    setSaving(true); setError('');
    try {
      await disposalApi.create({ assetIds: selected, reason, disposalDate: date, expectedFingerprint: createReview.fingerprint });
      setNotice(t('created')); setCreateReview(null); setSelected([]); setReason(''); setReload(value => value + 1);
    } catch (cause) { setError(cause instanceof Error ? cause.message : t('error')); }
    finally { setSaving(false); }
  };

  const showDetail = async (id: number, preserveError = false, opener?: HTMLElement) => {
    if (opener) detailTrigger.current = opener;
    const request = ++detailRequest.current;
    setDetailLoading(true); if (!preserveError) setError(''); setAction(null); setRejectReason('');
    try {
      const next = (await disposalApi.detail(id)).data;
      if (request === detailRequest.current) {
        if (next.status === 'PENDING') {
          setDetail(next);
          setDecisions(Object.fromEntries(next.perUserLinks.map(link => [link.allocationId, true])));
        } else {
          setDetail(null);
          if (pending.length === 1 && pendingPage > 0) setPendingPage(page => page - 1);
          else setReload(count => count + 1);
        }
      }
    } catch (cause) { if (request === detailRequest.current) setError(cause instanceof Error ? cause.message : t('error')); }
    finally { if (request === detailRequest.current) setDetailLoading(false); }
  };

  const mutateDetail = async (mutation: (value: Disposal) => Promise<Disposal>) => {
    if (!detail || detail.status !== 'PENDING') return;
    setSaving(true); setError('');
    try {
      const next = await mutation(detail);
      if (next.status === 'PENDING') {
        setDetail(next);
        setDecisions(Object.fromEntries(next.perUserLinks.map(link => [link.allocationId, true])));
        setAction(null); setRejectReason('');
      } else {
        setDetail(null);
        if (pending.length === 1 && pendingPage > 0) setPendingPage(page => page - 1);
        else setReload(count => count + 1);
      }
    } catch (cause) {
      setError(cause instanceof ApiError && cause.code === 'DISPOSAL_CHANGED' ? t('changed') : cause instanceof Error ? cause.message : t('error'));
      if (cause instanceof ApiError && cause.code === 'DISPOSAL_CHANGED') await showDetail(detail.transactionId, true);
    } finally { setSaving(false); }
  };

  const resolvePerUser = () => mutateDetail(async value => (await disposalApi.resolvePerUser(value.transactionId, value.fingerprint,
    value.perUserLinks.map(link => ({ allocationId: link.allocationId, release: decisions[link.allocationId] !== false })))).data);
  const approve = () => mutateDetail(async value => {
    return (await disposalApi.approve(value.transactionId, value.fingerprint)).data;
  });
  const reject = () => mutateDetail(async value => {
    return (await disposalApi.reject(value.transactionId, rejectReason.trim())).data;
  });
  const closeDetail = () => { detailRequest.current += 1; setDetail(null); setAction(null); };

  const loading = assetLoading || pendingLoading;
  return <div className="t18-page">
    <header className="page-header"><h1 className="page-title">{t('title')}</h1><p className="page-description">{t('description')}</p></header>
    <p role="status" className="sr-status">{loading || detailLoading ? t('loading') : t('ready')}</p>
    {error && <div className="alert-banner alert-danger" role="alert">{error}</div>}
    {notice && <div className="alert-banner alert-success">{notice}</div>}
    <div className="t18-columns">
      <section className="content-card t18-section">
        <h2>{t('createTitle')}</h2>
        <form onSubmit={submit}>
          <label className="form-group"><span className="form-label">{t('searchAssets')}</span><input className="form-input" type="search" value={assetKeyword} onChange={event => { setAssetKeyword(event.target.value); setAssetPage(0); }} /></label>
          <div className="candidate-list" aria-label={t('assets')} aria-busy={assetLoading}>
            {!assetLoading && assets.length === 0 && <p className="empty-state">{t('empty')}</p>}
            {assets.map(asset => <label className="candidate-row" key={asset.assetId}>
              <input type="checkbox" checked={selected.includes(asset.assetId)} onChange={() => setSelected(current => current.includes(asset.assetId) ? current.filter(id => id !== asset.assetId) : [...current, asset.assetId])} />
              <span><strong>{asset.assetTag}</strong><small>{asset.name} · {t(`categories.${asset.category}`, { defaultValue: asset.category })}</small></span>
            </label>)}
          </div>
          <Pagination page={assetPage} pages={assetPages} loading={assetLoading} previous={t('previous')} next={t('next')} onPage={setAssetPage} />
          <p className="selection-count">{t('selectedCount', { count: selected.length })}</p>
          <label className="form-group"><span className="form-label required">{t('reason')}</span><textarea className="form-textarea" required value={reason} onChange={event => setReason(event.target.value)} /></label>
          <label className="form-group"><span className="form-label required">{t('date')}</span><input className="form-input" type="date" required value={date} onChange={event => setDate(event.target.value)} /></label>
          <button className="btn btn-primary" disabled={saving || selected.length === 0}>{saving ? t('saving') : t('smartCheck')}</button>
        </form>
      </section>
      <section className="content-card t18-section">
        <h2>{t('pendingTitle')}</h2>
        {!pendingLoading && pending.length === 0 && <p className="empty-state">{t('noPending')}</p>}
        {pending.map(item => <div className="pending-row" key={item.transactionId}><div><strong>{item.transactionCode}</strong><small>{item.requesterName} · {item.createdAt}</small></div>
          <button className="btn btn-secondary btn-sm" disabled={detailLoading} onClick={event => void showDetail(item.transactionId, false, event.currentTarget)}>{t('review')}</button>
        </div>)}
        <Pagination page={pendingPage} pages={pendingPages} loading={pendingLoading} previous={t('previous')} next={t('next')} onPage={setPendingPage} />
      </section>
    </div>
    {createReview && <DisposalReview value={createReview} saving={saving} error={error} restoreFocusRef={createTrigger} onCancel={() => setCreateReview(null)} onConfirm={create} />}
    {detail && <DisposalDetail value={detail} role={user?.role} decisions={decisions} saving={saving} error={error} restoreFocusRef={detailTrigger} action={action} rejectReason={rejectReason}
      onDecision={(id, release) => setDecisions(current => ({ ...current, [id]: release }))} onResolve={() => void resolvePerUser()}
      onChooseAction={setAction} onRejectReason={setRejectReason} onApprove={() => void approve()} onReject={() => void reject()} onClose={closeDetail} />}
  </div>;
}

function Pagination({ page, pages, loading, previous, next, onPage }: {
  page: number; pages: number; loading: boolean; previous: string; next: string; onPage: (page: number) => void;
}) {
  if (pages <= 1) return null;
  return <nav className="t18-pagination" aria-label={`${page + 1} / ${pages}`}>
    <button type="button" className="btn btn-secondary btn-sm" disabled={loading || page === 0} onClick={() => onPage(page - 1)}>{previous}</button>
    <span>{page + 1} / {pages}</span>
    <button type="button" className="btn btn-secondary btn-sm" disabled={loading || page + 1 >= pages} onClick={() => onPage(page + 1)}>{next}</button>
  </nav>;
}
