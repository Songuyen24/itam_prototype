import { FormEvent, useEffect, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { locationApi } from '@/features/catalogs/api/catalogApi';
import { LocationItem } from '@/features/catalogs/types/catalog.types';
import { Candidate, HolderDetail, Handover, HandoverRequest, Recipient, handoverApi } from '../api/handoverApi';
import { HandoverPicker } from './HandoverPicker';
import { HandoverDetails } from './HandoverDetails';

const loadLocations = (keyword: string, page: number) => locationApi.getAll(keyword, true, page, 10);
const loadRecipients = (keyword: string, page: number) => handoverApi.holders(keyword, page, false);
function localDate() { const d = new Date(); return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`; }

export function HandoverForm({ onCompleted, onBusyChange, initialRecipient, initialValue }: { onCompleted: (value: Handover) => void; onBusyChange?: (busy: boolean) => void; initialRecipient?: Recipient; initialValue?: Handover }) {
  const { t } = useTranslation('handover');
  const [selected, setSelected] = useState<Candidate[]>(() => initialValue?.lines.filter(line => initialValue.request?.assetIds.includes(line.assetId) || initialValue.request?.licenses.some(l => l.assetId === line.assetId))
    .map(line => ({ assetId: line.assetId, assetTag: line.assetTag, name: line.name, category: line.category, assignmentType: null, availableSeats: null })) ?? []);
  const [recipient, setRecipient] = useState<Recipient | null>(initialRecipient ?? null);
  const [recipientDetail, setRecipientDetail] = useState<HolderDetail | null>(null);
  const [recipientPage, setRecipientPage] = useState(0);
  const [recipientError, setRecipientError] = useState('');
  const [recipientLoading, setRecipientLoading] = useState(false);
  const [recipientReload, setRecipientReload] = useState(0);
  const [location, setLocation] = useState<LocationItem | null>(initialValue ? { locationId: initialValue.destinationLocationId, name: initialValue.destinationLocationName } as LocationItem : null);
  const [licenses, setLicenses] = useState<Record<number, { seats: number; deviceId: number | null }>>(() => Object.fromEntries(initialValue?.request?.licenses.map(l => [l.assetId, { seats: l.seats, deviceId: l.deviceId }]) ?? []));
  const [date, setDate] = useState(initialValue?.handoverDate ?? localDate);
  const [notes, setNotes] = useState(initialValue?.notes ?? '');
  const [preview, setPreview] = useState<{ data: Handover; request: HandoverRequest } | null>(null);
  const [busy, setBusy] = useState(false);
  useEffect(() => { onBusyChange?.(busy); }, [busy, onBusyChange]);
  const [error, setError] = useState('');
  const [generation, setGeneration] = useState(0);
  const dialogRef = useRef<HTMLDialogElement>(null);
  useEffect(() => {
    if (!recipient) { setRecipientDetail(null); return; }
    let active = true; setRecipientLoading(true); setRecipientError('');
    handoverApi.holder(recipient.id, recipientPage).then(r => { if (active) setRecipientDetail(r.data); })
      .catch((e: unknown) => { if (active) setRecipientError(e instanceof Error ? e.message : t('error')); })
      .finally(() => { if (active) setRecipientLoading(false); });
    return () => { active = false; };
  }, [recipient, recipientPage, recipientReload, t]);
  useEffect(() => {
    const dialog = dialogRef.current;
    if (!preview || !dialog) return;
    const opener = document.activeElement;
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    dialog.showModal();
    return () => { dialog.close(); document.body.style.overflow = previousOverflow; if (opener instanceof HTMLElement && opener.isConnected) opener.focus(); };
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
      const body = { ...preview.request, expectedFingerprint: preview.data.fingerprint };
      const r = initialValue?.transactionId ? await handoverApi.edit(initialValue.transactionId, body) : await handoverApi.create(body);
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
        <HandoverPicker title={t('searchUsers')} load={loadRecipients} identify={u => u.id}
          label={u => `${u.fullName} · ${u.employeeCode || u.email || ''} · ${t('deviceCount', { count: u.deviceCount })}, ${t('componentCount', { count: u.componentCount })}, ${t('licenseSeatCount', { count: u.licenseSeats })}`}
          selected={recipient ? [recipient.id] : []} choose={u => { setRecipient(u); setRecipientPage(0); setRecipientDetail(null); }} disabled={frozen} unavailable={u => u.accountStatus !== 'ACTIVE'} />
        {recipient && <p>{t('recipient')}: <strong>{recipient.fullName}</strong></p>}
        {recipientLoading ? <p role="status">{t('loading')}</p> : recipientError ? <p role="alert">{recipientError} <button type="button" onClick={() => setRecipientReload(v => v + 1)}>{t('retry')}</button></p>
          : recipientDetail && <div className="handover-recipient-detail">
            <p>{t('deviceCount', { count: recipientDetail.holder.deviceCount })} · {t('componentCount', { count: recipientDetail.holder.componentCount })} · {t('licenseSeatCount', { count: recipientDetail.holder.licenseSeats })}</p>
            {recipientDetail.assets.content.length === 0 ? <p>{t('noHoldings')}</p> : <ul>{recipientDetail.assets.content.map(item => <li key={`${item.assetId}-${item.allocationId ?? ''}`}>
              {item.assetTag} · {item.name}{item.serialNumber ? ` · ${t('serial')}: ${item.serialNumber}` : ''}{item.category === 'LICENSE' ? ` · ${t('licenseSeatCount', { count: item.seats })}` : ''}{item.deviceTag ? ` · ${t('withParent', { id: item.deviceTag })}` : ''}
            </li>)}</ul>}
            <div className="handover-pagination"><button type="button" className="btn btn-secondary" disabled={recipientPage === 0} onClick={() => setRecipientPage(p => p - 1)}>{t('previous')}</button>
              <span>{recipientPage + 1} / {Math.max(1, recipientDetail.assets.totalPages)}</span><button type="button" className="btn btn-secondary" disabled={recipientPage + 1 >= recipientDetail.assets.totalPages} onClick={() => setRecipientPage(p => p + 1)}>{t('next')}</button></div>
          </div>}
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
      <h2 id="handover-confirm-title">{t('draftConfirmTitle')}</h2><p>{t('draftConfirmHint')}</p>
      <HandoverDetails value={preview.data} />
      <div className="handover-pagination">
        <button type="button" className="btn btn-secondary" disabled={busy} onClick={() => setPreview(null)}>{t('back')}</button>
        <button type="button" className="btn btn-primary" disabled={busy} onClick={complete}>{busy ? t('loading') : t('saveDraft')}</button>
      </div>
    </dialog>}
  </form>;
}
