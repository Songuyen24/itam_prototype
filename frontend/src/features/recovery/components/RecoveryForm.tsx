import CloseOutlinedIcon from '@mui/icons-material/CloseOutlined';
import { PaginatedContent } from '@/shared/components/PaginatedContent';
import { FormEvent, RefObject, useEffect, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { locationApi } from '@/features/catalogs/api/catalogApi';
import { LocationItem } from '@/features/catalogs/types/catalog.types';
import { buildRecoveryRequest, SmartCheckResponse, RecoveryRequest, Recovery, recoveryApi } from '../api/recoveryApi';
import { AssetCandidate, recoveryCandidateApi } from '../api/recoveryCandidateApi';
import { handoverApi } from '@/features/handover/api/handoverApi';
import { ApiError } from '@/shared/api/httpClient';
import ExpandMoreOutlinedIcon from '@mui/icons-material/ExpandMoreOutlined';

const loadLocations = (keyword: string, page: number) => locationApi.getAll(keyword, true, page, 10);
function localDate() { const d = new Date(); return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`; }

interface Recipient { id: number; fullName: string; email: string | null; accountStatus: string }

const categoryLabel = (t: (key: string, options?: Record<string, unknown>) => string, category: string) =>
  t(`category${category[0]}${category.slice(1).toLowerCase()}`);

function UserPicker({ onSelect, disabled, selected }: { onSelect: (r: Recipient) => void; disabled: boolean; selected: Recipient | null }) {
  const { t } = useTranslation('recovery');
  const detailsRef = useRef<HTMLDetailsElement>(null);
  const [keyword, setKeyword] = useState('');
  const [page, setPage] = useState(0);
  const [items, setItems] = useState<Recipient[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(false);
  const [retry, setRetry] = useState(0);

  useEffect(() => {
    let active = true;
    setLoading(true);
    setError(false);
    handoverApi.users(keyword, page)
      .then(r => { if (active) { setItems(r.data?.content || []); setTotal(r.data?.totalElements || 0); setPage(p => Math.min(p, Math.max(0, (r.data?.totalPages ?? 0) - 1))); } })
      .catch(() => { if (active) { setItems([]); setTotal(0); setError(true); } })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [keyword, page, retry]);

  const pages = Math.ceil(total / 10);
  return (
    <fieldset disabled={disabled} className="recovery-picker recovery-identity-picker">
      <legend>{t('searchAssets')}</legend>
      <details ref={detailsRef} className="recovery-picker-details"><summary><strong>{selected?.fullName || t('chooseReturner')}</strong>{selected?.email && <small>{selected.email}</small>}<ExpandMoreOutlinedIcon fontSize="small" aria-hidden="true" /></summary><div className="recovery-picker-popover">
      <input className="form-input" type="search" aria-label={t('searchAssets')} placeholder={t('searchPlaceholder')} value={keyword} onChange={e => { setKeyword(e.target.value); setPage(0); }} />
      {loading && <span>{t('loading')}</span>}
      {error && <p role="alert">{t('loadFailed')} <button type="button" onClick={() => setRetry(value => value + 1)}>{t('retry')}</button></p>}
      <div className="recovery-picker-list">{items.map(item => (
        <button type="button" key={item.id} disabled={disabled} className="recovery-picker-option" aria-label={`${item.fullName}${item.email ? `, ${item.email}` : ''}`} aria-pressed={selected?.id === item.id} onClick={() => { onSelect(item); if (detailsRef.current) { detailsRef.current.open = false; detailsRef.current.querySelector('summary')?.focus(); } }}>
          <span className="recovery-picker-option-main"><strong>{item.fullName}</strong>{selected?.id === item.id && <span className="recovery-picker-option-selected">{t('selectedOption')}</span>}</span>{item.email && <small>{item.email}</small>}
        </button>
      ))}</div>
      {pages > 1 && (
        <div className="recovery-picker-pagination">
          <button type="button" className="btn btn-secondary" disabled={page === 0} onClick={() => setPage(p => p - 1)}>{t('previous')}</button>
          <span>{t('documents:pagination.summary', { page: pages ? page + 1 : 0, pages, count: total })}</span>
          <button type="button" className="btn btn-secondary" disabled={page >= pages - 1} onClick={() => setPage(p => p + 1)}>{t('next')}</button>
        </div>
      )}</div></details>
    </fieldset>
  );
}

/** Lists recovery candidates owned by/attached to the chosen user. Groups items by category so the
 *  operator can immediately see Devices, Components, and Per-User Licenses belonging to that user. */
function UserAssetPicker({
  userId,
  selectedKeys,
  onToggle,
  disabled,
}: {
  userId: number | null;
  selectedKeys: string[];
  onToggle: (a: AssetCandidate) => void;
  disabled: boolean;
}) {
  const { t } = useTranslation('recovery');
  const [keyword, setKeyword] = useState('');
  const [page, setPage] = useState(0);
  const [items, setItems] = useState<AssetCandidate[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(false);
  const [retry, setRetry] = useState(0);

  useEffect(() => {
    if (!userId) {
      setItems([]);
      setTotal(0);
      return;
    }
    let active = true;
    setLoading(true);
    setError(false);
    recoveryCandidateApi
      .byUser(userId, keyword, page, 20)
      .then(r => {
        if (!active) return;
        setPage(p => Math.min(p, Math.max(0, (r.data?.totalPages ?? 0) - 1)));
        setItems(r.data?.content || []);
        setTotal(r.data?.totalElements || 0);
      })
      .catch(() => { if (active) { setItems([]); setTotal(0); setError(true); } })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [userId, keyword, page, retry]);

  const pages = Math.ceil(total / 20);
  const selectionKey = (item: AssetCandidate) => item.allocationId == null ? `asset-${item.assetId}` : `allocation-${item.allocationId}`;
  const isIn = (item: AssetCandidate) => selectedKeys.includes(selectionKey(item));

  const grouped = items.reduce<Record<string, AssetCandidate[]>>((acc, item) => {
    const cat = item.categoryCode || item.category || 'OTHER';
    (acc[cat] ||= []).push(item);
    return acc;
  }, {});

  return (
    <fieldset disabled={disabled} className="recovery-picker">
      <legend>{t('devicesTitle')}</legend>
      {!userId && <p className="recovery-muted">{t('selectReturnerFirst')}</p>}
      {userId && (
        <>
          <input
            className="form-input"
            type="search"
            aria-label={t('devicesTitle')}
            placeholder={t('searchDevicesPlaceholder')}
            value={keyword}
            onChange={e => {
              setKeyword(e.target.value);
              setPage(0);
            }}
          />
          {loading && <span>{t('loading')}</span>}
          {error && <p role="alert">{t('loadFailed')} <button type="button" onClick={() => setRetry(value => value + 1)}>{t('retry')}</button></p>}
          {!loading && items.length === 0 && <p className="recovery-muted">{t('noDevicesForUser')}</p>}
          <div className="recovery-candidate-list">{Object.entries(grouped).map(([cat, list]) => (
            <div key={cat} className="recovery-candidate-group">
              <div className="recovery-candidate-heading">
                {categoryLabel(t, cat)}
                {' '}<span className="recovery-candidate-count">({list.length})</span>
              </div>
              {list.map(item => (
                <label key={selectionKey(item)} className="recovery-asset-row">
                  <input type="checkbox" checked={isIn(item)} onChange={() => onToggle(item)} />
                  <span className="recovery-asset-identity">
                    <span className="recovery-asset-meta"><strong>{item.assetTag}</strong><span>{categoryLabel(t, item.categoryCode || item.category || 'OTHER')}</span>{item.licenseAssignmentTypeCode && <span>{item.licenseAssignmentTypeCode}</span>}</span>
                    <span className="recovery-asset-name">{item.name}</span>
                    {item.allocationId != null && <small>{t('allocation', { id: item.allocationId })}{item.seats != null ? ` · ${t('seats', { count: item.seats })}` : ''}{item.deviceTag ? ` · ${t('assignedToDevice', { deviceTag: item.deviceTag })}` : ''}</small>}
                  </span>
                </label>
              ))}
            </div>
          ))}</div>
          {pages > 1 && (
            <div className="recovery-picker-pagination">
              <button type="button" className="btn btn-secondary" disabled={page === 0} onClick={() => setPage(p => p - 1)}>{t('previous')}</button>
              <span style={{ margin: '0 8px' }}>{t('documents:pagination.summary', { page: pages ? page + 1 : 0, pages, count: total })}</span>
              <button type="button" className="btn btn-secondary" disabled={page >= pages - 1} onClick={() => setPage(p => p + 1)}>{t('next')}</button>
            </div>
          )}
        </>
      )}
    </fieldset>
  );
}

export function SmartCheckDialog({
  check, selectedAssetIds, selectedAllocationIds, reason, onConfirm, onCancel, busy, t, restoreFocusRef, initialRequest,
}: {
  check: SmartCheckResponse;
  selectedAssetIds: number[];
  selectedAllocationIds: number[];
  reason: string;
  onConfirm: (request: RecoveryRequest) => void;
  onCancel: () => void;
  busy: boolean;
  t: (key: string, options?: Record<string, unknown>) => string;
  restoreFocusRef?: RefObject<HTMLElement>;
  initialRequest?: RecoveryRequest | null;
}) {
  const dialogRef = useRef<HTMLDialogElement>(null);
  useEffect(() => {
    const dialog = dialogRef.current;
    if (!dialog) return;
    const opener = restoreFocusRef?.current ?? document.activeElement as HTMLElement | null;
    dialog.showModal();
    dialog.querySelector<HTMLElement>('button:not(:disabled), input:not(:disabled), textarea:not(:disabled), select:not(:disabled)')?.focus();
    return () => {
      if (dialog.open) dialog.close();
      opener?.focus();
    };
  }, [restoreFocusRef]);
  const [componentActions, setComponentActions] = useState<Record<number, string>>(() => {
    const init: Record<number, string> = {};
    check.componentDecisions?.forEach(c => { init[c.assetId] = initialRequest?.componentActions?.[c.assetId]?.componentAction || c.defaultAction || 'KEEP_ATTACHED'; });
    return init;
  });
  const [perUserActions, setPerUserActions] = useState<Record<number, boolean>>(() => {
    const init: Record<number, boolean> = {};
    check.optionalAssets?.forEach(a => { init[a.allocationId] = initialRequest?.perUserActions?.[a.allocationId] ?? (selectedAllocationIds.includes(a.allocationId) || a.selected); });
    return init;
  });

  // Separate required into device vs OEM for clearer display
  const requiredDevices = check.requiredAssets?.filter(a => a.category === 'DEVICE') ?? [];
  const requiredOem     = check.requiredAssets?.filter(a => a.category === 'LICENSE') ?? [];
  const requiredComponents = check.requiredAssets?.filter(a => a.category === 'COMPONENT') ?? [];

  // Scenario tags for blockedAssets
  const tagBlocked = (reason: string) => {
    const r = (reason || '').toLowerCase();
    if (r.includes('per-user')) return 'scenarioB';
    if (r.includes('component') || r.includes('linh kiện')) return 'scenarioC';
    if (r.includes('oem')) return 'scenarioD';
    return 'scenarioOther';
  };

  const hasBlocked = (check.blockedAssets?.length ?? 0) > 0;
  const hasOptionalPerUser = check.optionalAssets?.some(a => !selectedAllocationIds.includes(a.allocationId)) ?? false;

  const request = () => buildRecoveryRequest(check, selectedAssetIds, selectedAllocationIds, reason, componentActions, perUserActions);

  return (
    <dialog ref={dialogRef} className="recovery-dialog recovery-smart-check" aria-labelledby="recovery-smart-check-title"
      onCancel={event => { event.preventDefault(); if (!busy) onCancel(); }}
      >
      <div className="recovery-dialog-heading"><h2 id="recovery-smart-check-title">{t('smartCheckTitle')}</h2></div>
      <div className="recovery-dialog-body recovery-smart-check-body">

      {/* Blocked items — scenarios B, C, D when triggered */}
      {check.blockedAssets?.length ? (
        <div className="recovery-callout recovery-blocked">
          <h3>
            {t('blocked')} ({check.blockedAssets.length})
          </h3>
          <PaginatedContent items={check.blockedAssets ?? []}>{items => items.map(a => {
            return (
              <div key={a.assetId} className="recovery-review-row">
                <strong>{a.assetTag}</strong> · {a.name}
                <span className="recovery-review-meta">
                  {categoryLabel(t, a.category)} · {t(tagBlocked(a.reason))}
                </span>
                <p className="recovery-review-reason">{a.reason}</p>
              </div>
            );
          })}</PaginatedContent>
        </div>
      ) : null}

      {/* Warnings */}
      {check.warnings?.length ? (
        <div className="recovery-callout recovery-warning">
          {check.warnings.map((w, i) => <p key={i}>{w}</p>)}
        </div>
      ) : null}

      {/* Scenario A: Required devices */}
      {requiredDevices.length ? (
        <section className="recovery-review-group">
          <h3> {t('requiredDevices')} ({requiredDevices.length})</h3>
          <PaginatedContent items={requiredDevices ?? []}>{items => items.map(a => (
            <div key={a.assetId} className="recovery-review-row">
              <strong>{a.assetTag}</strong> · {a.name}
              {a.reason && <span className="recovery-review-meta">{a.reason}</span>}
            </div>
          ))}</PaginatedContent>
        </section>
      ) : null}

      {/* OEM licenses — always bundled (P08) */}
      {requiredOem.length ? (
        <section className="recovery-review-group">
          <h3> {t('oemBundled')} ({requiredOem.length})</h3>
          <PaginatedContent items={requiredOem ?? []}>{items => items.map(a => (
            <div key={a.assetId} className="recovery-review-row">
              <strong>{a.assetTag}</strong> · {a.name}
              <span className="recovery-review-meta">OEM</span>
              {a.reason && <p className="recovery-review-reason">{a.reason}</p>}
            </div>
          ))}</PaginatedContent>
        </section>
      ) : null}

      {requiredComponents.length ? (
        <section className="recovery-review-group">
          <h3> {t('requiredComponents')} ({requiredComponents.length})</h3>
          <PaginatedContent items={requiredComponents ?? []}>{items => items.map(a => <div key={a.assetId} className="recovery-review-row"><strong>{a.assetTag}</strong> · {a.name}</div>)}</PaginatedContent>
        </section>
      ) : null}

      {/* Components with KEEP/DETACH decisions */}
      {check.componentDecisions?.length ? (
        <section className="recovery-review-group">
          <h3> {t('componentDecisions')} ({check.componentDecisions.length})</h3>
          <PaginatedContent items={check.componentDecisions ?? []}>{items => items.map(c => (
            <div key={c.assetId} className="recovery-review-row">
              <p><strong>{c.assetTag}</strong> · {c.name}</p>
              {c.options.map(opt => (
                <label key={opt.action} className="recovery-review-choice">
                  <input type="radio" name={`comp-${c.assetId}`} value={opt.action}
                    checked={componentActions[c.assetId] === opt.action}
                    onChange={() => setComponentActions(prev => ({ ...prev, [c.assetId]: opt.action }))} />
                  <span><strong>{opt.label}</strong>: {opt.description}</span>
                </label>
              ))}
            </div>
          ))}</PaginatedContent>
        </section>
      ) : null}

      {/* Per-User Licenses — optional */}
      {check.optionalAssets?.length ? (
        <section className="recovery-review-group">
          <h3> {t(hasOptionalPerUser ? 'perUserLicenses' : 'perUserLicensesRequired')} ({check.optionalAssets.length})</h3>
          <PaginatedContent items={check.optionalAssets ?? []}>{items => items.map(a => (
            <label key={a.allocationId} className="recovery-review-choice">
              <input type="checkbox" disabled={selectedAllocationIds.includes(a.allocationId)} checked={perUserActions[a.allocationId] ?? false}
                onChange={e => setPerUserActions(prev => ({ ...prev, [a.allocationId]: e.target.checked }))} />
              <span>
                {a.assetTag} · {a.name} · {t('seats', { count: a.seats })}
                {selectedAllocationIds.includes(a.allocationId) && <span className="recovery-review-meta"> · {t('selectedStandalone')}</span>}
                {a.userName && <span className="recovery-review-meta"> · {a.userName}</span>}
                {a.deviceTag && <span className="recovery-review-meta"> · {t('assignedToDevice', { deviceTag: a.deviceTag })}</span>}
              </span>
            </label>
          ))}</PaginatedContent>
          <p className="recovery-review-meta">{t(hasOptionalPerUser ? 'perUserHint' : 'perUserRequiredHint')}</p>
        </section>
      ) : null}

      </div>
      <div className="recovery-dialog-footer">
        <button type="button" className="btn btn-secondary" disabled={busy} onClick={onCancel}>{t('back')}</button>
        <button type="button" className="btn btn-primary" disabled={busy || hasBlocked}
                title={hasBlocked ? t('cannotProceedBlocked') : undefined}
                onClick={() => onConfirm(request())}>
          {busy ? t('loading') : t('complete')}
        </button>
      </div>
    </dialog>
  );
}

export function RecoveryForm({ onCompleted, onConflict, initialValue, onBusyChange }: { onCompleted: (value: Recovery) => void; onConflict?: (error: ApiError) => void; initialValue?: Recovery; onBusyChange?: (busy: boolean) => void }) {
  const { t } = useTranslation('recovery');
  const [selected, setSelected] = useState<AssetCandidate[]>(() => {
    if (!initialValue?.request) return [];
    const ids = new Set(initialValue.request.assetIds);
    const allocations = new Set(initialValue.request.allocationIds);
    return initialValue.lines.flatMap(line => {
      const asset = ids.has(line.assetId) ? [{ assetId: line.assetId, assetTag: line.assetTag, name: line.name, category: line.category, licenseAssignmentTypeCode: null, assignedToUserId: initialValue.returnerUserId, assignedToFullName: initialValue.returnerName }] : [];
      const seats = line.allocations.filter(a => allocations.has(a.allocationId)).map(a => ({ assetId: line.assetId, assetTag: line.assetTag, name: line.name, category: line.category, allocationId: a.allocationId, seats: a.seats, licenseAssignmentTypeCode: a.assignmentType, assignedToUserId: initialValue.returnerUserId, assignedToFullName: initialValue.returnerName }));
      return [...asset, ...seats];
    });
  });
  const [returner, setReturner] = useState<Recipient | null>(initialValue ? { id: initialValue.returnerUserId, fullName: initialValue.returnerName, email: initialValue.returnerEmail, accountStatus: 'ACTIVE' } : null);
  const [location, setLocation] = useState<LocationItem | null>(initialValue ? { locationId: initialValue.receivingLocationId, name: initialValue.receivingLocationName } as LocationItem : null);
  const [date, setDate] = useState(initialValue?.recoveryDate || localDate);
  const [reason, setReason] = useState(initialValue?.reason || '');
  const [smartCheck, setSmartCheck] = useState<SmartCheckResponse | null>(null);
  const [liveCheck, setLiveCheck] = useState<SmartCheckResponse | null>(null);
  const [checking, setChecking] = useState(false);
  const [checkError, setCheckError] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const previewTrigger = useRef<HTMLElement | null>(null);
  const frozen = busy || smartCheck !== null;
  useEffect(() => { onBusyChange?.(frozen); return () => onBusyChange?.(false); }, [frozen, onBusyChange]);

  useEffect(() => {
    if (!returner || selected.length === 0) { setLiveCheck(null); setChecking(false); setCheckError(''); return; }
    let active = true;
    setLiveCheck(null);
    setChecking(true);
    setCheckError('');
    const timer = window.setTimeout(() => {
      recoveryApi.smartCheck({
        returnerUserId: returner.id,
        assetIds: selected.filter(a => a.allocationId == null).map(a => a.assetId),
        allocationIds: selected.flatMap(a => a.allocationId == null ? [] : [a.allocationId]),
        reason,
      }).then(result => { if (active) setLiveCheck(result.data); })
        .catch(e => { if (active) setCheckError(e instanceof Error ? e.message : t('error')); })
        .finally(() => { if (active) setChecking(false); });
    }, 180);
    return () => { active = false; window.clearTimeout(timer); };
  }, [returner, selected, reason, t]);

  function toggle(a: AssetCandidate) {
    if (selected.some(s => s.assetId === a.assetId && s.allocationId === a.allocationId)) {
      setSelected(items => items.filter(s => s.assetId !== a.assetId || s.allocationId !== a.allocationId));
    } else {
      setSelected(items => [...items, a]);
    }
  }

  function handleReturnerChange(r: Recipient | null) {
    // Changing the returner invalidates the previously picked assets (ownership/IN_USE check)
    if (r?.id === returner?.id) return;
    setReturner(r);
    setSelected([]);
  }

  async function review(e: FormEvent) {
    e.preventDefault();
    previewTrigger.current = (e.nativeEvent as SubmitEvent).submitter as HTMLElement | null;
    setError('');
    if (!returner || !location || selected.length === 0) { setError(t('required')); return; }
    if (!reason.trim()) { setError(t('reasonRequired')); return; }
    const body = {
      returnerUserId: returner.id,
      assetIds: selected.filter(a => a.allocationId == null).map(a => a.assetId),
      allocationIds: selected.flatMap(a => a.allocationId == null ? [] : [a.allocationId]),
      reason,
    };
    setBusy(true);
    try {
      const r = await recoveryApi.smartCheck(body);
      setSmartCheck(r.data);
    } catch (e) {
      setError(e instanceof Error ? e.message : t('error'));
    } finally {
      setBusy(false);
    }
  }

  async function confirm(request: RecoveryRequest) {
    if (!returner || !location) return;
    setBusy(true);
    try {
      const fullRequest = {
        ...request,
        returnerUserId: returner.id,
        receivingLocationId: location.locationId,
        recoveryDate: date,
      };
      const r = initialValue?.transactionId ? await recoveryApi.edit(initialValue.transactionId, fullRequest, initialValue.contentVersion!) : await recoveryApi.create(fullRequest);
      setSmartCheck(null);
      setSelected([]);
      onCompleted(r.data);
    } catch (e) {
      if (e instanceof ApiError && e.status === 409 && onConflict) onConflict(e);
      else setError(e instanceof Error ? e.message : t('error'));
    } finally {
      setBusy(false);
    }
  }

  // Count selected per category for the summary panel
  const selectedCounts = selected.reduce<Record<string, number>>((acc, a) => {
    const c = a.categoryCode || a.category || 'OTHER';
    acc[c] = (acc[c] || 0) + 1;
    return acc;
  }, {});

  return (
    <form onSubmit={review} className="recovery-form">
      {error && <p role="alert" className="document-error">{error}</p>}
      <section className="recovery-form-section" aria-labelledby="recovery-step-1"><h3 id="recovery-step-1"><span className="recovery-step-number">1</span>{t('stepReturner')}</h3><div className="recovery-form-grid">
        <UserPicker onSelect={handleReturnerChange} disabled={frozen} selected={returner} />
        <HandoverLocationPicker onSelect={setLocation} disabled={frozen} selected={location} />
      </div></section>
      <section className="recovery-form-section" aria-labelledby="recovery-step-2"><h3 id="recovery-step-2"><span className="recovery-step-number">2</span>{t('stepAssets')}</h3>
      <div className="recovery-form-assets-grid">
        <UserAssetPicker
          key={returner?.id ?? 'none'}
          userId={returner?.id || null}
          selectedKeys={selected.map(a => a.allocationId == null ? `asset-${a.assetId}` : `allocation-${a.allocationId}`)}
          onToggle={toggle}
          disabled={frozen}
        />
        <div className="recovery-selected-panel">
          <strong>{t('selected', { count: selected.length })}</strong>
          {Object.keys(selectedCounts).length > 0 && (
            <div className="recovery-selected-categories">
              {Object.entries(selectedCounts).map(([c, n]) => (
                <span key={c}>
                  {categoryLabel(t, c)}: {n}
                </span>
              ))}
            </div>
          )}
          {selected.length === 0 && <p className="recovery-selected-empty">{t('emptySelection')}</p>}
          <PaginatedContent items={selected}>{items => items.map(a => (
            <div key={a.allocationId == null ? `asset-${a.assetId}` : `allocation-${a.allocationId}`} className="recovery-selected-row">
              <span className="recovery-asset-identity">
                <span className="recovery-asset-meta"><strong>{a.assetTag}</strong><span>{categoryLabel(t, a.categoryCode || a.category || 'OTHER')}</span></span>
                <span className="recovery-asset-name">{a.name}</span>
              </span>
              <button type="button" className="recovery-remove" aria-label={t('removeAsset', { asset: `${a.assetTag} · ${a.name}` })} disabled={frozen} onClick={() => toggle(a)}><CloseOutlinedIcon fontSize="small" aria-hidden="true" /></button>
            </div>
          ))}</PaginatedContent>
          {selected.length > 0 && <div aria-live="polite" className="recovery-live-check">
            <strong>{t('smartCheckTitle')}</strong>
            {checking && <p role="status">{t('loading')}</p>}
            {checkError && <p role="alert">{t('loadFailed')} {checkError}</p>}
            {liveCheck && <>
              <div className="recovery-check-counts"><p><strong>{liveCheck.requiredAssets?.length ?? 0}</strong><span>{t('requiredAssets')}</span></p><p><strong>{liveCheck.componentDecisions?.length ?? 0}</strong><span>{t('componentDecisions')}</span></p><p><strong>{liveCheck.optionalAssets?.length ?? 0}</strong><span>{t('perUserLicenses')}</span></p></div>
              <PaginatedContent items={liveCheck.requiredAssets ?? []}>{items => items.map(a => <p className="recovery-check-row" key={a.assetId}><strong>{a.assetTag}</strong><span>{a.name}</span></p>)}</PaginatedContent>
              <PaginatedContent items={liveCheck.blockedAssets ?? []}>{items => items.map(a => <p key={a.assetId} role="alert" className="recovery-live-blocked">{t('blocked')}: <strong>{a.assetTag}</strong> {a.reason}</p>)}</PaginatedContent>
              <PaginatedContent items={liveCheck.warnings ?? []}>{items => items.map((warning, index) => <p key={index}>{warning}</p>)}</PaginatedContent>
            </>}
          </div>}
        </div>
      </div></section>
      <section className="recovery-form-section" aria-labelledby="recovery-step-3"><h3 id="recovery-step-3"><span className="recovery-step-number">3</span>{t('stepDetails')}</h3><div className="recovery-form-grid">
        <label className="form-label">{t('date')}<input className="form-input" type="date" required value={date} disabled={frozen} onChange={e => setDate(e.target.value)} /></label>
        <label className="form-label">{t('reason')}<textarea className="form-input" required value={reason} disabled={frozen} onChange={e => setReason(e.target.value)} style={{ minHeight: 60 }} /></label>
      </div></section>
      <div className="recovery-form-footer"><button className="btn btn-primary" type="submit" disabled={frozen || !returner || !location || selected.length === 0}>
        {busy ? t('loading') : t('preview')}
      </button></div>
      {smartCheck && (
        <SmartCheckDialog check={smartCheck}
          selectedAssetIds={selected.filter(a => a.allocationId == null).map(a => a.assetId)}
          selectedAllocationIds={selected.flatMap(a => a.allocationId == null ? [] : [a.allocationId])}
          reason={reason} onConfirm={confirm} onCancel={() => setSmartCheck(null)} busy={busy}
          restoreFocusRef={previewTrigger} initialRequest={initialValue?.request} t={t as unknown as (key: string, options?: Record<string, unknown>) => string} />
      )}
    </form>
  );
}

function HandoverLocationPicker({ onSelect, disabled, selected }: { onSelect: (l: LocationItem) => void; disabled: boolean; selected: LocationItem | null }) {
  const { t } = useTranslation('recovery');
  const detailsRef = useRef<HTMLDetailsElement>(null);
  const [keyword, setKeyword] = useState('');
  const [page, setPage] = useState(0);
  const [items, setItems] = useState<LocationItem[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(false);
  const [retry, setRetry] = useState(0);

  useEffect(() => {
    let active = true;
    setLoading(true);
    setError(false);
    loadLocations(keyword, page).then(r => { if (active) { setItems(r.data?.content || []); setTotal(r.data?.totalElements || 0); setPage(p => Math.min(p, Math.max(0, (r.data?.totalPages ?? 0) - 1))); } }).catch(() => { if (active) { setItems([]); setTotal(0); setError(true); } }).finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [keyword, page, retry]);

  const pages = Math.ceil(total / 10);
  return (
    <fieldset disabled={disabled} className="recovery-picker recovery-identity-picker">
      <legend>{t('searchLocations')}</legend>
      <details ref={detailsRef} className="recovery-picker-details"><summary><strong>{selected?.name || t('chooseLocation')}</strong><ExpandMoreOutlinedIcon fontSize="small" aria-hidden="true" /></summary><div className="recovery-picker-popover">
      <input className="form-input" type="search" aria-label={t('searchLocations')} placeholder={t('searchPlaceholder')} value={keyword} onChange={e => { setKeyword(e.target.value); setPage(0); }} />
      {loading && <span>{t('loading')}</span>}
      {error && <p role="alert">{t('loadFailed')} <button type="button" onClick={() => setRetry(value => value + 1)}>{t('retry')}</button></p>}
      <div className="recovery-picker-list">{items.map(item => (
        <button type="button" key={item.locationId} disabled={disabled} className="recovery-picker-option" aria-label={item.name} aria-pressed={selected?.locationId === item.locationId} onClick={() => { onSelect(item); if (detailsRef.current) { detailsRef.current.open = false; detailsRef.current.querySelector('summary')?.focus(); } }}>
          <span className="recovery-picker-option-main"><strong>{item.name}</strong>{selected?.locationId === item.locationId && <span className="recovery-picker-option-selected">{t('selectedOption')}</span>}</span>
        </button>
      ))}</div>
      {pages > 1 && (
        <div className="recovery-picker-pagination">
          <button type="button" className="btn btn-secondary" disabled={page === 0} onClick={() => setPage(p => p - 1)}>{t('previous')}</button>
          <span>{t('documents:pagination.summary', { page: pages ? page + 1 : 0, pages, count: total })}</span>
          <button type="button" className="btn btn-secondary" disabled={page >= pages - 1} onClick={() => setPage(p => p + 1)}>{t('next')}</button>
        </div>
      )}</div></details>
    </fieldset>
  );
}
