import { FormEvent, useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useAuth } from '@/features/auth/contexts/AuthContext';
import { disposalApi, DisposalAsset, DisposalCheck, TransactionSummary } from '../api/disposalApi';
import { DisposalReview } from '../components/DisposalReview';
import './disposal.css';

export function DisposalsPage() {
  const { t } = useTranslation('disposal');
  const { user } = useAuth();
  const [assets, setAssets] = useState<DisposalAsset[]>([]);
  const [selected, setSelected] = useState<number[]>([]);
  const [pending, setPending] = useState<TransactionSummary[]>([]);
  const [reason, setReason] = useState('');
  const [date, setDate] = useState(new Date().toISOString().slice(0, 10));
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [rejecting, setRejecting] = useState<TransactionSummary | null>(null);
  const [rejectReason, setRejectReason] = useState('');
  const [review, setReview] = useState<DisposalCheck | null>(null);

  const load = async () => {
    setLoading(true); setError('');
    try {
      const [candidateResult, pendingResult] = await Promise.all([disposalApi.candidates(), disposalApi.listPending()]);
      setAssets(candidateResult.data.content); setPending(pendingResult.data.content);
    } catch (e) { setError(e instanceof Error ? e.message : t('error')); }
    finally { setLoading(false); }
  };
  useEffect(() => { void load(); }, []);

  const submit = async (event: FormEvent) => {
    event.preventDefault(); setSaving(true); setError(''); setNotice('');
    try {
      const check = await disposalApi.smartCheck(selected);
      setReview(check.data);
    } catch (e) { setError(e instanceof Error ? e.message : t('error')); }
    finally { setSaving(false); }
  };

  const create = async () => {
    if (!review) return;
    setSaving(true); setError('');
    try {
      await disposalApi.create({ assetIds: selected, reason, disposalDate: date, expectedFingerprint: review.fingerprint });
      setNotice(t('created')); setReview(null); setSelected([]); setReason(''); await load();
    } catch (e) { setError(e instanceof Error ? e.message : t('error')); }
    finally { setSaving(false); }
  };

  const approve = async (id: number) => { setSaving(true); setError(''); try { await disposalApi.approve(id); await load(); } catch (e) { setError(e instanceof Error ? e.message : t('error')); } finally { setSaving(false); } };
  const reject = async () => {
    if (!rejecting || !rejectReason.trim()) return;
    setSaving(true); setError('');
    try { await disposalApi.reject(rejecting.transactionId, rejectReason.trim()); setRejecting(null); setRejectReason(''); await load(); }
    catch (e) { setError(e instanceof Error ? e.message : t('error')); } finally { setSaving(false); }
  };

  return <div className="t18-page">
    <header className="page-header"><h1 className="page-title">{t('title')}</h1><p className="page-description">{t('description')}</p></header>
    <p role="status" className="sr-status">{loading ? t('loading') : t('ready')}</p>
    {error && <div className="alert-banner alert-danger" role="alert">{error}</div>}
    {notice && <div className="alert-banner alert-success">{notice}</div>}
    <div className="t18-columns">
      <section className="content-card t18-section">
        <h2>{t('createTitle')}</h2>
        <form onSubmit={submit}>
          <div className="candidate-list" aria-label={t('assets')}>
            {!loading && assets.length === 0 && <p className="empty-state">{t('empty')}</p>}
            {assets.map(asset => <label className="candidate-row" key={asset.assetId}>
              <input type="checkbox" checked={selected.includes(asset.assetId)} onChange={() => setSelected(current => current.includes(asset.assetId) ? current.filter(id => id !== asset.assetId) : [...current, asset.assetId])} />
              <span><strong>{asset.assetTag}</strong><small>{asset.name} · {asset.category}</small></span>
            </label>)}
          </div>
          <label className="form-group"><span className="form-label required">{t('reason')}</span><textarea className="form-textarea" required value={reason} onChange={e => setReason(e.target.value)} /></label>
          <label className="form-group"><span className="form-label required">{t('date')}</span><input className="form-input" type="date" required value={date} onChange={e => setDate(e.target.value)} /></label>
          <button className="btn btn-primary" disabled={saving || selected.length === 0}>{saving ? t('saving') : t('smartCheck')}</button>
        </form>
      </section>
      <section className="content-card t18-section">
        <h2>{t('pendingTitle')}</h2>
        {!loading && pending.length === 0 && <p className="empty-state">{t('noPending')}</p>}
        {pending.map(item => <div className="pending-row" key={item.transactionId}><div><strong>{item.transactionCode}</strong><small>{item.requesterName}</small></div>
          {user?.role === 'ADMIN' && <div className="pending-actions"><button className="btn btn-primary btn-sm" disabled={saving} onClick={() => approve(item.transactionId)}>{t('approve')}</button><button className="btn btn-secondary btn-sm" disabled={saving} onClick={() => setRejecting(item)}>{t('reject')}</button></div>}
        </div>)}
      </section>
    </div>
    {rejecting && <div className="modal-overlay" role="dialog" aria-modal="true"><div className="modal-content"><div className="modal-header"><h2 className="modal-title">{t('rejectTitle')}</h2></div><div className="modal-body"><label className="form-group"><span className="form-label required">{t('rejectReason')}</span><textarea className="form-textarea" required value={rejectReason} onChange={e => setRejectReason(e.target.value)} /></label></div><div className="modal-footer"><button className="btn btn-secondary" onClick={() => setRejecting(null)}>{t('cancel')}</button><button className="btn btn-danger" disabled={!rejectReason.trim() || saving} onClick={reject}>{t('confirmReject')}</button></div></div></div>}
    {review && <DisposalReview value={review} saving={saving} onCancel={() => setReview(null)} onConfirm={create} />}
  </div>;
}
