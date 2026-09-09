import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Asset, AssetDetail } from '../types/asset.types';
import { assetApi } from '../api/assetApi';
import { relationshipApi, Relationship, Allocation, History } from '../api/relationshipApi';

export function AssetRelationships({asset}: {asset:AssetDetail}) {
  const {t,i18n}=useTranslation('assets');
  const [detail,setDetail]=useState(asset);
  const [rows,setRows]=useState<Relationship[]>([]);
  const [allocations,setAllocations]=useState<Allocation[]>([]);
  const [history,setHistory]=useState<History[]>([]);
  const [page,setPage]=useState(0);
  const [pages,setPages]=useState(1);
  const [revision,setRevision]=useState(0);
  const [error,setError]=useState('');
  const [loading,setLoading]=useState(true);
  const [busy,setBusy]=useState(false);
  const [keyword,setKeyword]=useState('');
  const [candidates,setCandidates]=useState<Asset[]>([]);
  const [candidatePage,setCandidatePage]=useState(0);
  const [candidatePages,setCandidatePages]=useState(1);
  const [candidateId,setCandidateId]=useState<number>();
  const [confirmId,setConfirmId]=useState<number>();
  const isParent=asset.categoryCode==='DEVICE';
  useEffect(()=>{
    let active=true; setLoading(true); setError('');
    Promise.all([relationshipApi.list(asset.assetId,page),relationshipApi.history(asset.assetId,page),
      asset.license?relationshipApi.allocations(asset.assetId,page):Promise.resolve(null),assetApi.getAssetById(asset.assetId)])
      .then(([r,h,a,d])=>{if(active){setRows(r.data.content);setHistory(h.data.content);setAllocations(a?.data.content || []);setDetail(d.data);setPages(Math.max(r.data.totalPages,h.data.totalPages,a?.data.totalPages || 0,1));}})
      .catch(e=>{if(active)setError(e.message || t('relationships.loadError'));}).finally(()=>{if(active)setLoading(false);});
    return ()=>{active=false;};
  },[asset.assetId,page,revision,t]);
  const alreadyLinked=asset.categoryCode==='COMPONENT' && rows.some(r=>r.child.assetId===asset.assetId);
  const noSeats=!!detail.license && detail.license.availableSeats<1;
  const canLink=!loading && !alreadyLinked && !noSeats && detail.statusCode==='IN_STOCK' && !detail.assignedToUserId && (!detail.license || detail.license.assignmentTypeCode==='OEM');
  const search=async(nextPage=0)=>{
    setBusy(true);setError('');setCandidateId(undefined);
    try { const r=await assetApi.getAssets({keyword,page:nextPage,size:20});
      setCandidates(r.data.content.filter(a=>a.assetId!==asset.assetId && a.statusCode==='IN_STOCK' && !a.assignedToUserId && (isParent?a.categoryCode==='COMPONENT'||(a.categoryCode==='LICENSE' && a.licenseAssignmentTypeCode==='OEM'):a.categoryCode==='DEVICE')));
      setCandidatePage(nextPage);setCandidatePages(r.data.totalPages);
    } catch(e){setError(e instanceof Error?e.message:t('relationships.loadError'));}finally{setBusy(false);}
  };
  const write=async(action:()=>Promise<unknown>)=>{
    setBusy(true);setError('');try{await action();setConfirmId(undefined);setCandidateId(undefined);setRevision(x=>x+1);}
    catch(e){setError(e instanceof Error?e.message:t('relationships.loadError'));}finally{setBusy(false);}
  };
  const add=()=>{const other=candidates.find(x=>x.assetId===candidateId);if(!other)return;
    const child=isParent?other:asset;
    void write(()=>relationshipApi.create(isParent?asset.assetId:other.assetId,child.assetId,child.categoryCode==='COMPONENT'?'COMPONENT_OF':'INSTALLED_ON'));
  };
  return <section className="asset-relationships">
    {detail.license && <><h3>{t('license.title')}</h3><p>{detail.license.softwareName} · {t(`license.${detail.license.assignmentTypeCode}`)} · {t(`license.${detail.license.termTypeCode}`)}</p>
      <p>{t('license.summary',{total:detail.license.seatCount,used:detail.license.allocatedSeats,available:detail.license.availableSeats})}</p>
      <p>{t('license.expiry')}: {detail.license.expiryDate || '—'}</p>{detail.license.assignmentTypeCode==='OEM' && <p>{t('license.reservedHint')}</p>}
      <h4>{t('license.allocations')}</h4>{!loading && allocations.length===0 && <p>{t('relationships.empty')}</p>}
      {allocations.map(a=><div className="relationship-row" key={a.allocationId}>#{a.allocationId} · {a.deviceTag || a.userName} · {a.userName || '—'} · {t('license.seatQuantity',{count:a.seats})} · {t(`license.${a.status}`)}</div>)}
    </>}
    <h3>{t('relationships.title')}</h3>
    {loading && <p role="status">{t('relationships.loading')}</p>}
    {error && <p role="alert">{error} <button type="button" onClick={()=>setRevision(x=>x+1)}>{t('relationships.retry')}</button></p>}
    {!loading && !rows.length && <p>{t('relationships.empty')}</p>}
    {rows.map(r=><div className="relationship-row" key={r.relationshipId}>
      <strong>{r.parent.assetTag} → {r.child.assetTag}</strong><span>{r.child.name} · {t(`relationships.${r.type}`)}</span>
      <span>{t(`common:status.${r.child.status}`)} · {r.child.assignedToFullName || '—'}</span>
      {r.removable ? <button className="btn btn-secondary" type="button" disabled={busy} onClick={()=>setConfirmId(r.relationshipId)}>{t('relationships.remove')}</button>:<small>{t(`relationships.${r.blockedReason}`)}</small>}
      {confirmId===r.relationshipId && <div role="alert">{t('relationships.confirmRemove')} <button type="button" disabled={busy} onClick={()=>void write(()=>relationshipApi.remove(r.relationshipId))}>{t('relationships.confirm')}</button> <button type="button" onClick={()=>setConfirmId(undefined)}>{t('form.cancel')}</button></div>}
    </div>)}
    {canLink ? <div className="relationship-search">
      <p>{t('relationships.stockHint')}</p>
      <label>{t(isParent?'relationships.findChild':'relationships.findParent')}<input className="form-input" value={keyword} onChange={e=>setKeyword(e.target.value)}/></label>
      <button className="btn btn-secondary" type="button" disabled={busy} onClick={()=>void search()}>{t('relationships.search')}</button>
      <select className="form-input" aria-label={t('relationships.choose')} value={candidateId || ''} onChange={e=>setCandidateId(Number(e.target.value)||undefined)}><option value="">{t('relationships.choose')}</option>{candidates.map(c=><option key={c.assetId} value={c.assetId}>{c.assetTag} · {c.name}</option>)}</select>
      <button className="btn btn-primary" type="button" disabled={busy||!candidateId} onClick={add}>{t('relationships.add')}</button>
      {candidatePages>1 && <div><button type="button" disabled={busy||candidatePage===0} onClick={()=>void search(candidatePage-1)}>{t('relationships.previous')}</button><span>{candidatePage+1}/{candidatePages}</span><button type="button" disabled={busy||candidatePage+1>=candidatePages} onClick={()=>void search(candidatePage+1)}>{t('relationships.next')}</button></div>}
    </div>:!loading && <p>{t(alreadyLinked?'relationships.alreadyLinked':noSeats?'relationships.noSeats':'relationships.ASSET_WORKFLOW_REQUIRED')}</p>}
    <h4>{t('relationships.history')}</h4>{history.map(h=><p key={h.id}>{t(`relationships.actions.${h.action}`,{defaultValue:h.action})} · {h.actor} · {new Date(h.createdAt).toLocaleString(i18n.language)}</p>)}
    {pages>1 && <div><button type="button" disabled={loading||page===0} onClick={()=>setPage(page-1)}>{t('relationships.previous')}</button><span>{page+1}/{pages}</span><button type="button" disabled={loading||page+1>=pages} onClick={()=>setPage(page+1)}>{t('relationships.next')}</button></div>}
  </section>;
}
