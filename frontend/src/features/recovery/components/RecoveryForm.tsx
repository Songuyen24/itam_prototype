import { FormEvent, RefObject, useEffect, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { locationApi } from '@/features/catalogs/api/catalogApi';
import { LocationItem } from '@/features/catalogs/types/catalog.types';
import { buildRecoveryRequest, SmartCheckResponse, RecoveryRequest, Recovery, recoveryApi } from '../api/recoveryApi';
import { AssetCandidate, recoveryCandidateApi } from '../api/recoveryCandidateApi';
import { handoverApi } from '@/features/handover/api/handoverApi';

const loadLocations = (keyword: string, page: number) => locationApi.getAll(keyword, true, page, 10);
function localDate() { const d = new Date(); return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`; }

interface Recipient { id: number; fullName: string; email: string; accountStatus: string }

const CATEGORY_ICON: Record<string, string> = { DEVICE: '💻', COMPONENT: '🔧', LICENSE: '🪪' };
const categoryLabel = (t: (key: string, options?: Record<string, unknown>) => string, category: string) =>
  t(`category${category[0]}${category.slice(1).toLowerCase()}`);

function UserPicker({ onSelect, disabled }: { onSelect: (r: Recipient) => void; disabled: boolean }) {
  const { t } = useTranslation('recovery');
  const [keyword, setKeyword] = useState('');
  const [page, setPage] = useState(0);
  const [items, setItems] = useState<Recipient[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(false);
  const [retry, setRetry] = useState(0);

  useEffect(() => {
    setLoading(true);
    setError(false);
    handoverApi.users(keyword, page)
      .then(r => { setItems(r.data?.content || []); setTotal(r.data?.totalElements || 0); })
      .catch(() => { setItems([]); setTotal(0); setError(true); })
      .finally(() => setLoading(false));
  }, [keyword, page, retry]);

  const pages = Math.ceil(total / 10);
  return (
    <fieldset disabled={disabled} style={{ border: '1px solid #ccc', padding: 12, margin: 8 }}>
      <legend>{t('searchAssets')}</legend>
      <input className="form-input" placeholder={t('searchPlaceholder')} value={keyword} onChange={e => { setKeyword(e.target.value); setPage(0); }} />
      {loading && <span>{t('loading')}</span>}
      {error && <p role="alert">{t('loadFailed')} <button type="button" onClick={() => setRetry(value => value + 1)}>{t('retry')}</button></p>}
      {items.map(item => (
        <button type="button" key={item.id} disabled={disabled} style={{ display: 'block', padding: 4, cursor: 'pointer' }} onClick={() => onSelect(item)}>
          <strong>{item.fullName}</strong> · {item.email}
        </button>
      ))}
      {pages > 1 && (
        <div style={{ marginTop: 8 }}>
          <button type="button" className="btn btn-secondary" disabled={page === 0} onClick={() => setPage(p => p - 1)}>{t('previous')}</button>
          <span style={{ margin: '0 8px' }}>{page + 1} / {pages}</span>
          <button type="button" className="btn btn-secondary" disabled={page >= pages - 1} onClick={() => setPage(p => p + 1)}>{t('next')}</button>
        </div>
      )}
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
    setLoading(true);
    setError(false);
    recoveryCandidateApi
      .byUser(userId, keyword, page, 20)
      .then(r => {
        setItems(r.data?.content || []);
        setTotal(r.data?.totalElements || 0);
      })
      .catch(() => { setItems([]); setTotal(0); setError(true); })
      .finally(() => setLoading(false));
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
    <fieldset disabled={disabled} style={{ border: '1px solid #ccc', padding: 12, margin: 8 }}>
      <legend>{t('devicesTitle')}</legend>
      {!userId && <p style={{ color: '#999', margin: 4 }}>{t('selectReturnerFirst')}</p>}
      {userId && (
        <>
          <input
            className="form-input"
            placeholder={t('searchDevicesPlaceholder')}
            value={keyword}
            onChange={e => {
              setKeyword(e.target.value);
              setPage(0);
            }}
          />
          {loading && <span>{t('loading')}</span>}
          {error && <p role="alert">{t('loadFailed')} <button type="button" onClick={() => setRetry(value => value + 1)}>{t('retry')}</button></p>}
          {!loading && items.length === 0 && <p style={{ color: '#999' }}>{t('noDevicesForUser')}</p>}
          {Object.entries(grouped).map(([cat, list]) => (
            <div key={cat} style={{ marginTop: 8 }}>
              <div style={{ fontWeight: 600, fontSize: 13, color: '#555', marginBottom: 4 }}>
                {CATEGORY_ICON[cat] ?? '•'} {categoryLabel(t, cat)}
                {' '}<span style={{ color: '#999', fontWeight: 400 }}>({list.length})</span>
              </div>
              {list.map(item => (
                <label
                  key={selectionKey(item)}
                  style={{
                    display: 'flex',
                    alignItems: 'center',
                    gap: 8,
                    padding: 4,
                    cursor: 'pointer',
                    borderBottom: '1px solid #f0f0f0',
                  }}
                >
                  <input type="checkbox" checked={isIn(item)} onChange={() => onToggle(item)} />
                  <span>
                    <strong>{item.assetTag}</strong> · {item.name}
                    {item.allocationId != null && <span> · {t('allocation', { id: item.allocationId })}{item.seats != null ? ` · ${t('seats', { count: item.seats })}` : ''}{item.deviceTag ? ` · ${t('assignedToDevice', { deviceTag: item.deviceTag })}` : ''}</span>}
                    {item.licenseAssignmentTypeCode && (
                      <span style={{
                        marginLeft: 8,
                        fontSize: 11,
                        background: item.licenseAssignmentTypeCode === 'OEM' ? '#fff3cd' : '#d1ecf1',
                        padding: '1px 6px',
                        borderRadius: 3,
                      }}>
                        {item.licenseAssignmentTypeCode}
                      </span>
                    )}
                  </span>
                </label>
              ))}
            </div>
          ))}
          {pages > 1 && (
            <div style={{ marginTop: 8 }}>
              <button type="button" className="btn btn-secondary" disabled={page === 0} onClick={() => setPage(p => p - 1)}>{t('previous')}</button>
              <span style={{ margin: '0 8px' }}>{page + 1} / {pages}</span>
              <button type="button" className="btn btn-secondary" disabled={page >= pages - 1} onClick={() => setPage(p => p + 1)}>{t('next')}</button>
            </div>
          )}
        </>
      )}
    </fieldset>
  );
}

export function SmartCheckDialog({
  check, selectedAssetIds, selectedAllocationIds, reason, onConfirm, onCancel, busy, t, restoreFocusRef,
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
    check.componentDecisions?.forEach(c => { init[c.assetId] = c.defaultAction || 'KEEP_ATTACHED'; });
    return init;
  });
  const [perUserActions, setPerUserActions] = useState<Record<number, boolean>>(() => {
    const init: Record<number, boolean> = {};
    check.optionalAssets?.forEach(a => { init[a.allocationId] = selectedAllocationIds.includes(a.allocationId) || a.selected; });
    return init;
  });

  // Separate required into device vs OEM for clearer display
  const requiredDevices = check.requiredAssets?.filter(a => a.category === 'DEVICE') ?? [];
  const requiredOem     = check.requiredAssets?.filter(a => a.category === 'LICENSE') ?? [];
  const requiredComponents = check.requiredAssets?.filter(a => a.category === 'COMPONENT') ?? [];

  // Scenario tags for blockedAssets
  const tagBlocked = (reason: string) => {
    const r = (reason || '').toLowerCase();
    if (r.includes('per-user')) return { key: 'scenarioB', color: '#f8d7da' };
    if (r.includes('component') || r.includes('linh kiện')) return { key: 'scenarioC', color: '#f8d7da' };
    if (r.includes('oem')) return { key: 'scenarioD', color: '#f8d7da' };
    return { key: 'scenarioOther', color: '#f8d7da' };
  };

  const hasBlocked = (check.blockedAssets?.length ?? 0) > 0;
  const hasOptionalPerUser = check.optionalAssets?.some(a => !selectedAllocationIds.includes(a.allocationId)) ?? false;

  const request = () => buildRecoveryRequest(check, selectedAssetIds, selectedAllocationIds, reason, componentActions, perUserActions);

  return (
    <dialog ref={dialogRef} aria-labelledby="recovery-smart-check-title"
      onCancel={event => { event.preventDefault(); if (!busy) onCancel(); }}
      style={{ margin: 'auto', padding: 24, width: 'min(900px, calc(100vw - 32px))', maxHeight: 'calc(100vh - 32px)', overflowY: 'auto' }}>
      <h2 id="recovery-smart-check-title">{t('smartCheckTitle')}</h2>

      {/* Blocked items — scenarios B, C, D when triggered */}
      {check.blockedAssets?.length ? (
        <div style={{ background: '#f8d7da', padding: 12, marginBottom: 16, borderRadius: 4, border: '1px solid #f5c6cb' }}>
          <h3 style={{ color: '#721c24', margin: '0 0 8px' }}>
            ⛔ {t('blocked')} ({check.blockedAssets.length})
          </h3>
          {check.blockedAssets.map(a => {
            const tag = tagBlocked(a.reason);
            return (
              <div key={a.assetId} style={{ padding: '6px 0', borderBottom: '1px solid #f5c6cb' }}>
                <strong style={{ color: '#721c24' }}>{a.assetTag}</strong> · {a.name}
                <span style={{ marginLeft: 8, fontSize: 11, background: '#fff', padding: '1px 6px', borderRadius: 3 }}>
                  {categoryLabel(t, a.category)} · {t(tag.key)}
                </span>
                <p style={{ margin: '4px 0', color: '#721c24', fontSize: 13 }}>→ {a.reason}</p>
              </div>
            );
          })}
        </div>
      ) : null}

      {/* Warnings */}
      {check.warnings?.length ? (
        <div style={{ background: hasBlocked ? '#fff3cd' : '#fff8e1', padding: 12, marginBottom: 16, borderRadius: 4 }}>
          {check.warnings.map((w, i) => <p key={i} style={{ margin: '4px 0' }}>{w}</p>)}
        </div>
      ) : null}

      {/* Scenario A: Required devices */}
      {requiredDevices.length ? (
        <div style={{ marginBottom: 16 }}>
          <h3>💻 {t('requiredDevices')} ({requiredDevices.length})</h3>
          {requiredDevices.map(a => (
            <div key={a.assetId} style={{ padding: '4px 8px', background: '#d4edda', marginBottom: 4, borderRadius: 4 }}>
              <strong>{a.assetTag}</strong> · {a.name}
              {a.reason && <span style={{ color: '#155724', marginLeft: 8, fontSize: 12 }}>({a.reason})</span>}
            </div>
          ))}
        </div>
      ) : null}

      {/* OEM licenses — always bundled (P08) */}
      {requiredOem.length ? (
        <div style={{ marginBottom: 16 }}>
          <h3>🪪 {t('oemBundled')} ({requiredOem.length})</h3>
          {requiredOem.map(a => (
            <div key={a.assetId} style={{ padding: '4px 8px', background: '#fff3cd', marginBottom: 4, borderRadius: 4, border: '1px solid #ffeeba' }}>
              <strong>{a.assetTag}</strong> · {a.name}
              <span style={{ marginLeft: 8, fontSize: 11, background: '#856404', color: '#fff', padding: '1px 6px', borderRadius: 3 }}>🔒 OEM</span>
              {a.reason && <p style={{ margin: '4px 0', color: '#856404', fontSize: 12 }}>{a.reason}</p>}
            </div>
          ))}
        </div>
      ) : null}

      {requiredComponents.length ? (
        <div style={{ marginBottom: 16 }}>
          <h3>🔧 {t('requiredComponents')} ({requiredComponents.length})</h3>
          {requiredComponents.map(a => <div key={a.assetId} style={{ padding: '4px 8px', background: '#d4edda', marginBottom: 4, borderRadius: 4 }}><strong>{a.assetTag}</strong> · {a.name}</div>)}
        </div>
      ) : null}

      {/* Components with KEEP/DETACH decisions */}
      {check.componentDecisions?.length ? (
        <div style={{ marginBottom: 16 }}>
          <h3>🔧 {t('componentDecisions')} ({check.componentDecisions.length})</h3>
          {check.componentDecisions.map(c => (
            <div key={c.assetId} style={{ padding: 8, background: '#e9ecef', marginBottom: 8, borderRadius: 4 }}>
              <p><strong>{c.assetTag}</strong> · {c.name}</p>
              {c.options.map(opt => (
                <label key={opt.action} style={{ display: 'block', padding: 4 }}>
                  <input type="radio" name={`comp-${c.assetId}`} value={opt.action}
                    checked={componentActions[c.assetId] === opt.action}
                    onChange={() => setComponentActions(prev => ({ ...prev, [c.assetId]: opt.action }))} />
                  <span style={{ marginLeft: 8 }}><strong>{opt.label}</strong>: {opt.description}</span>
                </label>
              ))}
            </div>
          ))}
        </div>
      ) : null}

      {/* Per-User Licenses — optional */}
      {check.optionalAssets?.length ? (
        <div style={{ marginBottom: 16 }}>
          <h3>👤 {t(hasOptionalPerUser ? 'perUserLicenses' : 'perUserLicensesRequired')} ({check.optionalAssets.length})</h3>
          {check.optionalAssets.map(a => (
            <label key={a.allocationId} style={{ display: 'block', padding: 4 }}>
              <input type="checkbox" disabled={selectedAllocationIds.includes(a.allocationId)} checked={perUserActions[a.allocationId] ?? false}
                onChange={e => setPerUserActions(prev => ({ ...prev, [a.allocationId]: e.target.checked }))} />
              <span style={{ marginLeft: 8 }}>
                {a.assetTag} · {a.name} · {t('seats', { count: a.seats })}
                {selectedAllocationIds.includes(a.allocationId) && <span style={{ color: '#666', fontSize: 12 }}> · {t('selectedStandalone')}</span>}
                {a.userName && <span style={{ color: '#666' }}> · {a.userName}</span>}
                {a.deviceTag && <span style={{ color: '#666', fontSize: 12 }}> · {t('assignedToDevice', { deviceTag: a.deviceTag })}</span>}
              </span>
            </label>
          ))}
          <p style={{ fontSize: 12, color: '#666', marginTop: 4 }}>{t(hasOptionalPerUser ? 'perUserHint' : 'perUserRequiredHint')}</p>
        </div>
      ) : null}

      <div style={{ display: 'flex', gap: 8, justifyContent: 'flex-end' }}>
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

export function RecoveryForm({ onCompleted }: { onCompleted: (value: Recovery) => void }) {
  const { t } = useTranslation('recovery');
  const [selected, setSelected] = useState<AssetCandidate[]>([]);
  const [returner, setReturner] = useState<Recipient | null>(null);
  const [location, setLocation] = useState<LocationItem | null>(null);
  const [date, setDate] = useState(localDate);
  const [reason, setReason] = useState('');
  const [smartCheck, setSmartCheck] = useState<SmartCheckResponse | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const previewTrigger = useRef<HTMLElement | null>(null);
  const frozen = busy || smartCheck !== null;

  function toggle(a: AssetCandidate) {
    if (selected.some(s => s.assetId === a.assetId && s.allocationId === a.allocationId)) {
      setSelected(items => items.filter(s => s.assetId !== a.assetId || s.allocationId !== a.allocationId));
    } else {
      setSelected(items => [...items, a]);
    }
  }

  function handleReturnerChange(r: Recipient | null) {
    // Changing the returner invalidates the previously picked assets (ownership/IN_USE check)
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
      const r = await recoveryApi.complete(fullRequest);
      setSmartCheck(null);
      setSelected([]);
      onCompleted(r.data);
    } catch (e) {
      setError(e instanceof Error ? e.message : t('error'));
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
    <form onSubmit={review} style={{ padding: 16 }}>
      <p>{t('hint')}</p>
      {error && <p role="alert" className="document-error">{error}</p>}
      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 16 }}>
        <UserPicker onSelect={handleReturnerChange} disabled={frozen} />
        <HandoverLocationPicker onSelect={setLocation} disabled={frozen} />
      </div>
      <h2>{t('selectDevices')}</h2>
      <p style={{ fontSize: 12, color: '#666', marginBottom: 8 }}>{t('selectDevicesHint')}</p>
      <div style={{ display: 'grid', gridTemplateColumns: '2fr 1fr', gap: 16 }}>
        <UserAssetPicker
          key={returner?.id ?? 'none'}
          userId={returner?.id || null}
          selectedKeys={selected.map(a => a.allocationId == null ? `asset-${a.assetId}` : `allocation-${a.allocationId}`)}
          onToggle={toggle}
          disabled={frozen}
        />
        <div style={{ border: '1px solid #ccc', padding: 12, margin: 8 }}>
          <strong>{t('selected', { count: selected.length })}</strong>
          {Object.keys(selectedCounts).length > 0 && (
            <div style={{ fontSize: 11, color: '#666', marginTop: 4 }}>
              {Object.entries(selectedCounts).map(([c, n]) => (
                <span key={c} style={{ marginRight: 8 }}>
                  {CATEGORY_ICON[c]} {categoryLabel(t, c)}: {n}
                </span>
              ))}
            </div>
          )}
          {selected.length === 0 && <p style={{ color: '#999', marginTop: 4 }}>{t('noDevices')}</p>}
          {selected.map(a => (
            <div key={a.allocationId == null ? `asset-${a.assetId}` : `allocation-${a.allocationId}`} style={{ padding: '4px 0', borderBottom: '1px solid #eee', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <span>
                <strong>{a.assetTag}</strong> · {a.name}
                <span style={{ marginLeft: 4, fontSize: 11, color: '#888' }}>{categoryLabel(t, a.categoryCode || a.category || 'OTHER')}</span>
              </span>
              <button type="button" className="btn btn-secondary" style={{ padding: '2px 8px' }} disabled={frozen} onClick={() => toggle(a)}>×</button>
            </div>
          ))}
        </div>
      </div>
      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 16 }}>
        <label className="form-label">{t('date')}<input className="form-input" type="date" required value={date} disabled={frozen} onChange={e => setDate(e.target.value)} /></label>
        <label className="form-label">{t('reason')}<textarea className="form-input" required value={reason} disabled={frozen} onChange={e => setReason(e.target.value)} style={{ minHeight: 60 }} /></label>
      </div>
      <button className="btn btn-primary" type="submit" disabled={frozen || !returner || !location || selected.length === 0}>
        {busy ? t('loading') : t('preview')}
      </button>
      {smartCheck && (
        <SmartCheckDialog check={smartCheck}
          selectedAssetIds={selected.filter(a => a.allocationId == null).map(a => a.assetId)}
          selectedAllocationIds={selected.flatMap(a => a.allocationId == null ? [] : [a.allocationId])}
          reason={reason} onConfirm={confirm} onCancel={() => setSmartCheck(null)} busy={busy}
          restoreFocusRef={previewTrigger} t={t as unknown as (key: string, options?: Record<string, unknown>) => string} />
      )}
    </form>
  );
}

function HandoverLocationPicker({ onSelect, disabled }: { onSelect: (l: LocationItem) => void; disabled: boolean }) {
  const { t } = useTranslation('recovery');
  const [keyword, setKeyword] = useState('');
  const [page, setPage] = useState(0);
  const [items, setItems] = useState<LocationItem[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(false);
  const [retry, setRetry] = useState(0);

  useEffect(() => {
    setLoading(true);
    setError(false);
    loadLocations(keyword, page).then(r => { setItems(r.data?.content || []); setTotal(r.data?.totalElements || 0); }).catch(() => { setItems([]); setTotal(0); setError(true); }).finally(() => setLoading(false));
  }, [keyword, page, retry]);

  const pages = Math.ceil(total / 10);
  return (
    <fieldset disabled={disabled} style={{ border: '1px solid #ccc', padding: 12, margin: 8 }}>
      <legend>{t('searchLocations')}</legend>
      <input className="form-input" placeholder={t('searchPlaceholder')} value={keyword} onChange={e => { setKeyword(e.target.value); setPage(0); }} />
      {loading && <span>{t('loading')}</span>}
      {error && <p role="alert">{t('loadFailed')} <button type="button" onClick={() => setRetry(value => value + 1)}>{t('retry')}</button></p>}
      {items.map(item => (
        <button type="button" key={item.locationId} disabled={disabled} style={{ display: 'block', padding: 4, cursor: 'pointer' }} onClick={() => onSelect(item)}>
          {item.name}
        </button>
      ))}
      {pages > 1 && (
        <div style={{ marginTop: 8 }}>
          <button type="button" className="btn btn-secondary" disabled={page === 0} onClick={() => setPage(p => p - 1)}>{t('previous')}</button>
          <span style={{ margin: '0 8px' }}>{page + 1} / {pages}</span>
          <button type="button" className="btn btn-secondary" disabled={page >= pages - 1} onClick={() => setPage(p => p + 1)}>{t('next')}</button>
        </div>
      )}
    </fieldset>
  );
}
