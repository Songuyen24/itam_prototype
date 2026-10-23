import { useEffect, useState } from 'react';
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
  const search = query.trim();

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
  }, [search, user?.role, user?.id]);

  function open(path: string) {
    navigate(path);
    setQuery('');
  }

  return <div className="global-search">
    <label className="sr-only" htmlFor="global-search-input">{t('search.title')}</label>
    <input id="global-search-input" className="form-input" type="search" maxLength={100}
      placeholder={t('search.placeholder')} value={query} onChange={event => setQuery(event.target.value)}
      onKeyDown={event => { if (event.key === 'Escape') setQuery(''); }} />
    {search.length >= 2 && <div className="global-search-results" role="region" aria-label={t('search.title')}>
      {loading ? <p role="status">{t('search.loading')}</p> : error ? <p role="alert">{t('search.error')}</p> : <>
        {assets.length === 0 && profiles.length === 0 && <p>{t('search.empty')}</p>}
        {assets.map(asset => <button type="button" key={`a-${asset.assetId}`}
          onClick={() => open(`${user?.role === 'USER' ? '/my-assets' : '/assets'}?keyword=${encodeURIComponent(asset.assetTag)}`)}>
          <strong>{asset.assetTag}</strong><span>{asset.name}{asset.serialNumber ? ` · ${asset.serialNumber}` : ''}</span>
        </button>)}
        {assetTotal > assets.length && <button type="button" onClick={() => open(`${user?.role === 'USER' ? '/my-assets' : '/assets'}?keyword=${encodeURIComponent(search)}`)}>{t('search.allAssets', { count: assetTotal })}</button>}
        {profiles.map(profile => <button type="button" key={`p-${profile.id}`}
          onClick={() => open(`/employee-profiles?keyword=${encodeURIComponent(profile.employeeCode || profile.fullName)}`)}>
          <strong>{profile.fullName}</strong><span>{[profile.employeeCode, profile.email].filter(Boolean).join(' · ')}</span>
        </button>)}
        {profileTotal > profiles.length && <button type="button" onClick={() => open(`/employee-profiles?keyword=${encodeURIComponent(search)}`)}>{t('search.allProfiles', { count: profileTotal })}</button>}
      </>}
    </div>}
  </div>;
}
