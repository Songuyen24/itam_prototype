import { DocumentPagination } from '@/features/documents/components/DocumentPagination';
import { useEffect, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Asset, AssetDetail } from '../types/asset.types';
import { assetApi } from '../api/assetApi';
import { relationshipApi, Relationship, Allocation, History } from '../api/relationshipApi';

export function AssetRelationships({asset,onDetailRefresh}: {asset:AssetDetail;onDetailRefresh?:(detail:AssetDetail)=>void}) {
  const {t,i18n}=useTranslation('assets');
  const [detail,setDetail]=useState(asset);
  const [rows,setRows]=useState<Relationship[]>([]);
  const [allocations,setAllocations]=useState<Allocation[]>([]);
  const [history,setHistory]=useState<History[]>([]);
  const [page,setPage]=useState(0);
  const [pages,setPages]=useState(1);
  const [historyPage,setHistoryPage]=useState(0);
  const [historyPages,setHistoryPages]=useState(1);
  const [allocationPage,setAllocationPage]=useState(0);
  const [allocationPages,setAllocationPages]=useState(1);
  const [totals,setTotals]=useState({relationships:0,history:0,allocations:0,candidates:0});
  const [revision,setRevision]=useState(0);
  const [error,setError]=useState('');
  const [loading,setLoading]=useState(true);
  const [busy,setBusy]=useState(false);
  const [keyword,setKeyword]=useState('');
  const [candidates,setCandidates]=useState<Asset[]>([]);
  const [candidatePage,setCandidatePage]=useState(0);
  const [candidatePages,setCandidatePages]=useState(1);
  const [candidateId,setCandidateId]=useState<number>();
  const [addOpen,setAddOpen]=useState(false);
  const [addError,setAddError]=useState('');
  const addDialogRef=useRef<HTMLDialogElement>(null);
  const addButtonRef=useRef<HTMLButtonElement>(null);
  const candidateRequest=useRef(0);
  const [confirmId,setConfirmId]=useState<number>();
  const isParent=asset.categoryCode==='DEVICE';
  useEffect(()=>{
    if(!addOpen)return;
    const dialog=addDialogRef.current;
    const addButton=addButtonRef.current;
    dialog?.showModal();
    return ()=>{dialog?.close();addButton?.focus();};
  },[addOpen]);
  useEffect(()=>()=>{candidateRequest.current++;},[asset.assetId]);
  useEffect(()=>{
    let active=true; setLoading(true); setError('');
    Promise.all([relationshipApi.list(asset.assetId,page),relationshipApi.history(asset.assetId,historyPage,5),
      asset.license?relationshipApi.allocations(asset.assetId,allocationPage):Promise.resolve(null),assetApi.getAssetById(asset.assetId)])
      .then(([r,h,a,d])=>{if(active){setTotals(v=>({...v,relationships:r.data.totalElements,history:h.data.totalElements,allocations:a?.data.totalElements || 0}));setRows(r.data.content);setHistory(h.data.content);setAllocations(a?.data.content || []);setDetail(d.data);onDetailRefresh?.(d.data);setPages(r.data.totalPages);setHistoryPages(h.data.totalPages);setAllocationPages(a?.data.totalPages || 0);setPage(p=>Math.min(p,Math.max(0,r.data.totalPages-1)));setHistoryPage(p=>Math.min(p,Math.max(0,h.data.totalPages-1)));setAllocationPage(p=>Math.min(p,Math.max(0,(a?.data.totalPages || 0)-1)));}})
      .catch(e=>{if(active)setError(e.message || t('relationships.loadError'));}).finally(()=>{if(active)setLoading(false);});
    return ()=>{active=false;};
  },[asset.assetId, asset.license, page, historyPage, allocationPage, revision, t, onDetailRefresh]);
  const alreadyLinked=asset.categoryCode==='COMPONENT' && rows.some(r=>r.child.assetId===asset.assetId);
  const noSeats=!!detail.license && detail.license.availableSeats<1;
  const canLink=!loading && !alreadyLinked && !noSeats && detail.statusCode==='IN_STOCK' && !detail.assignedToUserId && (!detail.license || detail.license.assignmentTypeCode==='OEM');
  const search=async(nextPage=0,term=keyword)=>{
    const request=++candidateRequest.current;
    setBusy(true);setAddError('');setCandidateId(undefined);
    try { let r=await assetApi.relationshipCandidates(asset.assetId,term,nextPage);
      const lastPage=Math.max(0,r.data.totalPages-1);
      if(nextPage>lastPage){nextPage=lastPage;r=await assetApi.relationshipCandidates(asset.assetId,term,nextPage);}
      if(request!==candidateRequest.current)return;
      setCandidates(r.data.content);setTotals(v=>({...v,candidates:r.data.totalElements}));
      setCandidatePage(nextPage);setCandidatePages(r.data.totalPages);
    } catch(e){if(request===candidateRequest.current)setAddError(e instanceof Error?e.message:t('relationships.loadError'));}finally{if(request===candidateRequest.current)setBusy(false);}
  };
  const closeAdd=()=>{candidateRequest.current++;setAddOpen(false);setBusy(false);};
  const openAdd=()=>{setKeyword('');setCandidates([]);setCandidatePage(0);setCandidatePages(0);setCandidateId(undefined);setAddError('');setTotals(v=>({...v,candidates:0}));setAddOpen(true);void search(0,'');};
  const write=async(action:()=>Promise<unknown>)=>{
    setBusy(true);setError('');try{await action();setConfirmId(undefined);setCandidateId(undefined);setRevision(x=>x+1);}
    catch(e){setError(e instanceof Error?e.message:t('relationships.loadError'));}finally{setBusy(false);}
  };
  const add=()=>{const other=candidates.find(x=>x.assetId===candidateId);if(!other)return;
    const child=isParent?other:asset;
    setBusy(true);setAddError('');
    void relationshipApi.create(isParent?asset.assetId:other.assetId,child.assetId,child.categoryCode==='COMPONENT'?'COMPONENT_OF':'INSTALLED_ON')
      .then(()=>{closeAdd();setCandidateId(undefined);setRevision(x=>x+1);})
      .catch(e=>setAddError(e instanceof Error?e.message:t('relationships.loadError')))
      .finally(()=>setBusy(false));
  };
  return <section className="asset-relationships" aria-busy={loading || busy}>
    {detail.license && <><h3>{t('license.allocations')}</h3>
      <div className="asset-allocation-table-wrap"><table className="data-table asset-allocation-table"><thead><tr>
        <th scope="col">{t('license.allocationId')}</th><th scope="col">{t('license.device')}</th><th scope="col">{t('license.user')}</th><th scope="col">{t('license.seats')}</th><th scope="col">{t('license.status')}</th><th scope="col">{t('license.createdAt')}</th><th scope="col">{t('license.releasedAt')}</th>
      </tr></thead><tbody>
        {loading ? <tr><td colSpan={7}>{t('relationships.loading')}</td></tr>
          : allocations.length===0 ? <tr><td colSpan={7}>{t('relationships.empty')}</td></tr>
            : allocations.map(a=><tr key={a.allocationId}><td>#{a.allocationId}</td><td>{a.deviceTag || '—'}</td><td>{a.userName || '—'}</td><td>{a.seats}</td><td>{t(`license.${a.status}`,{defaultValue:a.status})}</td><td>{new Date(a.createdAt).toLocaleString(i18n.language)}</td><td>{a.releasedAt?new Date(a.releasedAt).toLocaleString(i18n.language):'—'}</td></tr>)}
      </tbody></table></div>
      <DocumentPagination page={allocationPage} totalPages={allocationPages} totalElements={totals.allocations} loading={loading} onPageChange={setAllocationPage}/>
    </>}
    {(!asset.license || loading || error || totals.relationships>0 || addOpen) && <>
    <h3>{t('relationships.title')}</h3>
    {loading && <p role="status">{t('relationships.loading')}</p>}
    {error && <p role="alert">{error} <button type="button" onClick={()=>setRevision(x=>x+1)}>{t('relationships.retry')}</button></p>}
    {rows.map(r=><div className="relationship-row" key={r.relationshipId}>
      <strong>{r.parent.assetTag} → {r.child.assetTag}</strong><span>{r.child.name} · {t(`relationships.${r.type}`)}</span>
      <span>{t(`common:status.${r.child.status}`)} · {r.child.assignedToFullName || '—'}</span>
      {r.removable ? <button className="btn btn-secondary" type="button" disabled={busy} onClick={()=>setConfirmId(r.relationshipId)}>{t('relationships.remove')}</button>:r.blockedReason!=='ASSET_WORKFLOW_REQUIRED' && <small>{t(`relationships.${r.blockedReason}`)}</small>}
      {confirmId===r.relationshipId && <div className="relationship-confirm" role="alert">{t('relationships.confirmRemove')} <button type="button" disabled={busy} onClick={()=>void write(()=>relationshipApi.remove(r.relationshipId))}>{t('relationships.confirm')}</button> <button type="button" onClick={()=>setConfirmId(undefined)}>{t('form.cancel')}</button></div>}
    </div>)}
    {rows.length>0 && <DocumentPagination page={page} totalPages={pages} totalElements={totals.relationships} loading={loading} onPageChange={setPage}/>}
    {canLink ? <button ref={addButtonRef} className="btn btn-secondary" type="button" onClick={openAdd}>{t('relationships.openAdd')}</button>:!loading && (alreadyLinked || noSeats) && <p>{t(alreadyLinked?'relationships.alreadyLinked':'relationships.noSeats')}</p>}
    {addOpen && <dialog ref={addDialogRef} className="relationship-add-dialog" aria-label={t('relationships.openAdd')}
      onCancel={e=>{e.preventDefault();e.stopPropagation();if(!busy)closeAdd();}}>
      <div className="relationship-add-header"><h3>{t('relationships.openAdd')}</h3><button className="btn btn-secondary" type="button" disabled={busy} onClick={closeAdd}>{t('form.cancel')}</button></div>
      <div className="relationship-search">
      <p>{t('relationships.stockHint')}</p>
      {addError && <p role="alert">{addError}</p>}
      <label>{t(isParent?'relationships.findChild':'relationships.findParent')}<input className="form-input" value={keyword} disabled={busy} onChange={e=>{setKeyword(e.target.value);setCandidatePage(0);setCandidates([]);setCandidatePages(0);setCandidateId(undefined);setTotals(v=>({...v,candidates:0}));}}/></label>
      <button className="btn btn-secondary" type="button" disabled={busy} onClick={()=>void search()}>{t('relationships.search')}</button>
      <select disabled={busy} className="form-input" aria-label={t('relationships.choose')} value={candidateId || ''} onChange={e=>setCandidateId(Number(e.target.value)||undefined)}><option value="">{t('relationships.choose')}</option>{candidates.map(c=><option key={c.assetId} value={c.assetId}>{c.assetTag} · {c.name}</option>)}</select>
      <button className="btn btn-primary" type="button" disabled={busy||!candidateId} onClick={add}>{t('relationships.add')}</button>
      <DocumentPagination page={candidatePage} totalPages={candidatePages} totalElements={totals.candidates} loading={busy} onPageChange={next=>void search(next)}/>
      </div>
    </dialog>}
    </>}
    <h4>{t('relationships.history')}</h4>
    <div className="asset-history-table-wrap"><table className="data-table asset-history-table"><thead><tr>
      <th scope="col">{t('relationships.historyAction')}</th><th scope="col">{t('relationships.historyActor')}</th><th scope="col">{t('relationships.historyTime')}</th>
    </tr></thead><tbody>
      {loading ? <tr><td colSpan={3}>{t('relationships.loading')}</td></tr>
        : history.length===0 ? <tr><td colSpan={3}>{t('relationships.empty')}</td></tr>
          : history.map(h=><tr key={h.id}><td>{t(`relationships.actions.${h.action}`,{defaultValue:h.action})}</td><td>{h.actor}</td><td>{new Date(h.createdAt).toLocaleString(i18n.language)}</td></tr>)}
    </tbody></table></div>
    <DocumentPagination page={historyPage} totalPages={historyPages} totalElements={totals.history} loading={loading} onPageChange={setHistoryPage}/>
  </section>;
}
