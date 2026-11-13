import SearchOutlinedIcon from '@mui/icons-material/SearchOutlined';
import { useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { assetApi } from '@/features/assets/api/assetApi';
import type { Asset } from '@/features/assets/types/asset.types';
import { managementApi, type EmployeeProfile } from '@/features/auth/api/managementApi';
import { useAuth } from '@/features/auth/contexts/AuthContext';

export function GlobalSearch() {
  const { t } = useTranslation('common');
  const { user } = useAuth();
  const navigate = useNavigate();
  const [query, setQuery] = useState('');
  const [assets, setAssets] = useState<Asset[]>([]);
  const [profiles, setProfiles] = useState<EmployeeProfile[]>([]);
  const [assetTotal, setAssetTotal] = useState(0);
  const [profileTotal, setProfileTotal] = useState(0);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(false);
  const [expanded, setExpanded] = useState(false);
  const [retry, setRetry] = useState(0);
  const root = useRef<HTMLDivElement>(null);
  const search = query.trim();
  const canSearchAssets = ['ADMIN', 'IT_STAFF', 'USER'].includes(user?.role ?? '');
  const canSearchProfiles = ['ADMIN', 'IT_STAFF', 'PUR_STAFF'].includes(user?.role ?? '');

  useEffect(() => {
    if (typeof document === 'undefined') return;
    const closeOutside = (event: Event) => {
      if (!root.current?.contains(event.target as Node)) setExpanded(false);
    };
    document.addEventListener('pointerdown', closeOutside);
    document.addEventListener('focusin', closeOutside);
    return () => {
      document.removeEventListener('pointerdown', closeOutside);
      document.removeEventListener('focusin', closeOutside);
    };
  }, []);

  useEffect(() => {
    let active = true;
    if (search.length < 2) {
      setAssets([]); setProfiles([]); setAssetTotal(0); setProfileTotal(0); setLoading(false); setError(false);
      return;
    }
    setAssets([]); setProfiles([]); setAssetTotal(0); setProfileTotal(0); setLoading(true); setError(false);
    const timer = setTimeout(async () => {
      try {
        const assetRequest = user?.role === 'USER'
          ? assetApi.getMyAssets({ keyword: search, page: 0, size: 5 })
          : user?.role === 'ADMIN' || user?.role === 'IT_STAFF'
            ? assetApi.getAssets({ keyword: search, page: 0, size: 5 }) : null;
        const profileRequest = ['ADMIN', 'IT_STAFF', 'PUR_STAFF'].includes(user?.role ?? '') ? managementApi.profiles(search, 0) : null;
        const [assetResult, profileResult] = await Promise.all([assetRequest, profileRequest]);
        if (!active) return;
        setAssets(assetResult?.data.content ?? []);
        setProfiles(profileResult?.data.content.slice(0, 5) ?? []);
        setAssetTotal(assetResult?.data.totalElements ?? 0);
        setProfileTotal(profileResult?.data.totalElements ?? 0);
      } catch {
        if (active) setError(true);
      } finally {
        if (active) setLoading(false);
      }
    }, 250);
    return () => { active = false; clearTimeout(timer); };
  }, [search, user?.role, user?.id, retry]);

  function open(path: string) {
    navigate(path);
    setQuery('');
    setExpanded(false);
  }

  const assetPath = user?.role === 'USER' ? '/my-assets' : '/assets';
  return <div className="global-search" ref={root}>
    <label className="global-search-label" htmlFor="global-search-input">{t('search.title')}</label>
    <SearchOutlinedIcon className="global-search-icon" aria-hidden="true" />
    <input id="global-search-input" className="form-input" type="search" maxLength={100}
      aria-controls={expanded && search.length >= 2 ? 'global-search-results' : undefined} aria-expanded={expanded && search.length >= 2}
      placeholder={t('search.placeholder')} value={query} onFocus={() => setExpanded(true)}
      onClick={() => setExpanded(true)}
      onChange={event => { setQuery(event.target.value); setExpanded(true); }}
      onKeyDown={event => {
        if (event.key === 'Escape') { setExpanded(false); return; }
        if (event.key === 'ArrowDown' && search.length >= 2) {
          if (!expanded) { setExpanded(true); return; }
          const first = root.current?.querySelector<HTMLButtonElement>('.global-search-results button');
          if (first) { event.preventDefault(); first.focus(); }
        }
      }} />
    {expanded && search.length >= 2 && <div id="global-search-results" className="global-search-results" role="region" aria-label={t('search.title')}
      onKeyDown={event => {
        if (event.key === 'Escape') { root.current?.querySelector('input')?.focus(); setExpanded(false); return; }
        if (event.key !== 'ArrowDown' && event.key !== 'ArrowUp') return;
        const buttons = [...(root.current?.querySelectorAll<HTMLButtonElement>('.global-search-results button') ?? [])];
        const index = buttons.indexOf(event.target as HTMLButtonElement);
        if (index < 0) return;
        event.preventDefault();
        const next = buttons[index + (event.key === 'ArrowDown' ? 1 : -1)];
        if (next) next.focus(); else if (event.key === 'ArrowUp') root.current?.querySelector('input')?.focus();
      }}>
      {loading ? <p role="status">{t('search.loading')}</p> : error ? <div className="global-search-message" role="alert">{t('search.error')} <button type="button" onClick={() => setRetry(value => value + 1)}>{t('search.retry')}</button></div> : <>
        {assets.length === 0 && profiles.length === 0 && <p>{t('search.empty')}</p>}
        {canSearchAssets && (assets.length > 0 || assetTotal > 0) && <section aria-label={t('search.assets')}>
          <h3>{t('search.assets')} <span>{assetTotal}</span></h3>
          {assets.map(asset => <button type="button" key={`a-${asset.assetId}`}
            onClick={() => open(`${assetPath}?keyword=${encodeURIComponent(asset.assetTag)}`)}>
            <strong title={asset.assetTag}>{asset.assetTag}</strong><span title={[asset.name, asset.serialNumber].filter(Boolean).join(' · ')}>{asset.name}{asset.serialNumber ? ` · ${asset.serialNumber}` : ''}</span>
          </button>)}
          {assetTotal > assets.length && <button className="global-search-all" type="button" onClick={() => open(`${assetPath}?keyword=${encodeURIComponent(search)}`)}>{t('search.allAssets', { count: assetTotal })}</button>}
        </section>}
        {canSearchProfiles && (profiles.length > 0 || profileTotal > 0) && <section aria-label={t('search.profiles')}>
          <h3>{t('search.profiles')} <span>{profileTotal}</span></h3>
          {profiles.map(profile => <button type="button" key={`p-${profile.id}`}
            onClick={() => open(`/accounts?keyword=${encodeURIComponent(profile.employeeCode || profile.fullName)}`)}>
            <strong title={profile.fullName}>{profile.fullName}</strong><span title={[profile.employeeCode, profile.email].filter(Boolean).join(' · ')}>{[profile.employeeCode, profile.email].filter(Boolean).join(' · ')}</span>
          </button>)}
          {profileTotal > profiles.length && <button className="global-search-all" type="button" onClick={() => open(`/accounts?keyword=${encodeURIComponent(search)}`)}>{t('search.allProfiles', { count: profileTotal })}</button>}
        </section>}
      </>}
    </div>}
  </div>;
}
