import { FormEvent, useEffect, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { locationApi } from '@/features/catalogs/api/catalogApi';
import { LocationItem } from '@/features/catalogs/types/catalog.types';
import { Candidate, Handover, HandoverRequest, Recipient, handoverApi } from '../api/handoverApi';
import { HandoverPicker } from './HandoverPicker';
import { HandoverDetails } from './HandoverDetails';

const loadLocations = (keyword: string, page: number) => locationApi.getAll(keyword, true, page, 10);
function localDate() { const d = new Date(); return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`; }

export function HandoverForm({ onCompleted }: { onCompleted: (value: Handover) => void }) {
  const { t } = useTranslation('handover');
  const [selected, setSelected] = useState<Candidate[]>([]);
  const [recipient, setRecipient] = useState<Recipient | null>(null);
  const [location, setLocation] = useState<LocationItem | null>(null);
  const [licenses, setLicenses] = useState<Record<number, { seats: number; deviceId: number | null }>>({});
  const [date, setDate] = useState(localDate);
  const [notes, setNotes] = useState('');
  const [preview, setPreview] = useState<{ data: Handover; request: HandoverRequest } | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [generation, setGeneration] = useState(0);
  const dialogRef = useRef<HTMLDialogElement>(null);
  useEffect(() => {
    const dialog = dialogRef.current;
    if (!preview || !dialog) return;
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    dialog.showModal();
    return () => { dialog.close(); document.body.style.overflow = previousOverflow; };
  }, [preview]);
  const devices = selected.filter(a => a.category === 'DEVICE');
  const frozen = busy || preview !== null;
  function toggle(a: Candidate) {
    if (selected.some(s => s.assetId === a.assetId)) {
      setSelected(items => items.filter(s => s.assetId !== a.assetId));
      setLicenses(values => Object.fromEntries(Object.entries(values).filter(([key]) => Number(key) !== a.assetId)
        .map(([key, v]) => [key, { ...v, deviceId: v.deviceId === a.assetId ? null : v.deviceId }])));
    } else {
      setSelected(items => [...items, a]);
      if (a.category === 'LICENSE') setLicenses(v => ({ ...v, [a.assetId]: { seats: 1, deviceId: null } }));
    }
  }
  async function review(e: FormEvent) {
    e.preventDefault(); setError('');
    if (!recipient || !location || selected.length === 0) { setError(t('required')); return; }
    const request: HandoverRequest = { recipientUserId: recipient.id, destinationLocationId: location.locationId, handoverDate: date, notes,
      assetIds: selected.filter(a => a.category !== 'LICENSE').map(a => a.assetId),
      licenses: selected.filter(a => a.category === 'LICENSE').map(a => ({ assetId: a.assetId, ...licenses[a.assetId] })) };
    setBusy(true);
    try { const r = await handoverApi.preview(request); setPreview({ data: r.data, request }); }
    catch (e) { setError(e instanceof Error ? e.message : t('error')); }
    finally { setBusy(false); }
  }
  async function complete() {
    if (!preview || busy) return;
    setBusy(true); setError('');
    try {
      const r = await handoverApi.complete({ ...preview.request, expectedFingerprint: preview.data.fingerprint });
      setPreview(null); setSelected([]); setLicenses({}); setGeneration(v => v + 1); onCompleted(r.data);
    } catch (e) { setError(e instanceof Error ? e.message : t('error')); setPreview(null); setGeneration(v => v + 1); }
    finally { setBusy(false); }
  }
  return <form onSubmit={review} className="handover-form">
    <p>{t('hint')}</p>
    {error && <p role="alert" className="document-error">{error}</p>}
    <div className="handover-grid">
      <HandoverPicker key={generation} title={t('searchAssets')} load={handoverApi.candidates} identify={a => a.assetId}
        label={a => `${a.assetTag} · ${a.name}${a.availableSeats != null ? ` · ${t('available', { count: a.availableSeats })}` : ''}`}
        selected={selected.map(a => a.assetId)} choose={toggle} disabled={frozen} unavailable={a => a.availableSeats === 0} />
      <div>
        <HandoverPicker title={t('searchUsers')} load={handoverApi.users} identify={u => u.id} label={u => `${u.fullName} · ${u.email}`}
          selected={recipient ? [recipient.id] : []} choose={setRecipient} disabled={frozen} unavailable={u => u.accountStatus !== 'ACTIVE'} />
        {recipient && <p>{t('recipient')}: <strong>{recipient.fullName}</strong></p>}
        <HandoverPicker title={t('searchLocations')} load={loadLocations} identify={l => l.locationId} label={l => l.name}
          selected={location ? [location.locationId] : []} choose={setLocation} disabled={frozen} />
        {location && <p>{t('location')}: <strong>{location.name}</strong></p>}
      </div>
    </div>
    <h2>{t('selected', { count: selected.length })}</h2>
    {selected.length === 0 && <p>{t('emptySelection')}</p>}
    {selected.map(a => <div className="handover-selected" key={a.assetId}>
      <span><strong>{a.assetTag}</strong> · {a.name}</span>
      {a.category === 'LICENSE' && <>
        <label>{t('seats')}<input className="form-input" type="number" min={1} max={Math.min(100, a.availableSeats ?? 100)} required disabled={frozen}
          value={licenses[a.assetId]?.seats ?? 1} onChange={e => setLicenses(v => ({ ...v, [a.assetId]: { ...v[a.assetId], seats: Number(e.target.value), deviceId: Number(e.target.value) === 1 ? v[a.assetId].deviceId : null } }))} /></label>
        <label>{t('deviceOptional')}<select className="form-select" disabled={frozen || licenses[a.assetId]?.seats !== 1} value={licenses[a.assetId]?.deviceId ?? ''}
          onChange={e => setLicenses(v => ({ ...v, [a.assetId]: { ...v[a.assetId], deviceId: e.target.value ? Number(e.target.value) : null } }))}>
          <option value="">{t('toUser')}</option>{devices.map(d => <option key={d.assetId} value={d.assetId}>{d.assetTag} · {d.name}</option>)}
        </select></label>
      </>}
      <button type="button" className="btn btn-secondary" disabled={frozen} onClick={() => toggle(a)}>{t('remove')}</button>
    </div>)}
    <div className="handover-grid">
      <label className="form-label">{t('date')}<input className="form-input" type="date" required value={date} disabled={frozen} onChange={e => setDate(e.target.value)} /></label>
      <label className="form-label">{t('notes')}<textarea className="form-input" maxLength={4000} value={notes} disabled={frozen} onChange={e => setNotes(e.target.value)} /></label>
    </div>
    <button className="btn btn-primary" type="submit" disabled={frozen}>{busy ? t('loading') : t('preview')}</button>
    {preview && <dialog ref={dialogRef} className="handover-confirm" aria-labelledby="handover-confirm-title"
      onCancel={event => { if (busy) event.preventDefault(); else setPreview(null); }}>
      <h2 id="handover-confirm-title">{t('confirmTitle')}</h2><p>{t('confirmHint')}</p>
      <HandoverDetails value={preview.data} />
      <div className="handover-pagination">
        <button type="button" className="btn btn-secondary" disabled={busy} onClick={() => setPreview(null)}>{t('back')}</button>
        <button type="button" className="btn btn-primary" disabled={busy} onClick={complete}>{busy ? t('loading') : t('complete')}</button>
      </div>
    </dialog>}
  </form>;
}
