import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { ApiError } from '@/shared/api/httpClient';
import { useAuth } from '@/features/auth/contexts/AuthContext';
import { DocumentItem, DocumentTransaction } from '../types/document.types';
import { documentApi } from '../api/documentApi';
import { receivingApi, Content, Revision, Event, References, Line, LineInput } from '@/features/receiving/api/receivingApi';
import { ReceivingAssetForm } from '@/features/receiving/components/ReceivingAssetForm';
import { ReceivingAssetDetails } from '@/features/receiving/components/ReceivingAssetDetails';

export function ImportDraftPanel({ transaction, onChanged, onCreated }: { transaction: DocumentTransaction; onChanged: () => void; onCreated?: (id:number)=>void }) {
  const { t, i18n } = useTranslation('documents');
  const { user } = useAuth();
  const [content,setContent]=useState<Content|null>(null);
  const [revisions,setRevisions]=useState<Revision[]>([]);
  const [events,setEvents]=useState<Event[]>([]);
  const [refs,setRefs]=useState<References|null>(null);
  const [notes,setNotes]=useState('');
  const [edit,setEdit]=useState<Line|null|undefined>(undefined);
  const [keyword,setKeyword]=useState('');
  const [page,setPage]=useState(0);
  const [candidates,setCandidates]=useState<Line[]>([]);
  const [searched,setSearched]=useState(false);
  const [loading,setLoading]=useState(true);
  const [busy,setBusy]=useState(false);
  const [error,setError]=useState<string|null>(null);
  const [conflict,setConflict]=useState(false);
  const [reject,setReject]=useState(false);
  const [reason,setReason]=useState('');
  const editor=user?.role==='ADMIN'||user?.role==='PUR_STAFF';
  const reviewer=user?.role==='ADMIN'||user?.role==='IT_STAFF';
  const draft=editor&&transaction.status==='DRAFT';
  const id=transaction.transactionId;
  const version=transaction.expectedVersion!;
  const disabled=busy||conflict;
  useEffect(()=>{
    let active=true;setLoading(true);setError(null);setConflict(false);
    Promise.all([receivingApi.content(id),receivingApi.revisions(id),receivingApi.events(id),draft?receivingApi.references():Promise.resolve(null)])
      .then(([c,r,e,ref])=>{if(active){setContent(c.data);setNotes(c.data.notes??'');setRevisions(r.data);setEvents(e.data);setRefs(ref?.data??null);}})
      .catch(cause=>{if(active)setError(cause instanceof Error?cause.message:t('errors.transaction'));})
      .finally(()=>{if(active)setLoading(false);});
    return()=>{active=false;};
  },[id,version,draft,t]);
  async function mutate(operation:()=>Promise<unknown>) {
    setBusy(true);setError(null);
    try {await operation();setEdit(undefined);setReject(false);onChanged();}
    catch(cause){setError(cause instanceof Error?cause.message:t('errors.conflict'));setConflict(cause instanceof ApiError && ['TRANSACTION_VERSION_CONFLICT','DOCUMENT_LOCKED','IMPORT_ASSET_UNAVAILABLE'].includes(cause.code??''));}
    finally{setBusy(false);}
  }
  async function search(next:number) {
    setBusy(true);setError(null);
    try {const result=await receivingApi.candidates(keyword,next);setCandidates(result.data);setPage(next);setSearched(true);}
    catch(cause){setError(cause instanceof Error?cause.message:t('errors.transaction'));}
    finally{setBusy(false);}
  }
  async function download(document:DocumentItem) {
    setBusy(true);setError(null);
    try{await documentApi.download(document);}catch(cause){setError(cause instanceof Error?cause.message:t('errors.download'));}finally{setBusy(false);}
  }
  if(loading)return <p role="status">{t('states.loading')}</p>;
  return <div className="document-draft-panel">
    {error&&<div role="alert" className="alert-banner alert-danger">{error} <button className="btn btn-secondary" onClick={onChanged}>{t('receiving.reload')}</button></div>}
    <p>{t('receiving.createdBy')}: {transaction.requesterName||'—'} · {t('draft.revision',{number:transaction.submittedRevision??0})}</p>
    {content?.lastEditedBy&&<p>{t('receiving.lastEditedBy')}: {content.lastEditedBy}</p>}
    {content?.sourceReceivingId&&<p>{t('receiving.sourceReceivingId')}: {content.sourceReceivingId}</p>}
    {transaction.processedByName&&<p>{t('receiving.processedBy')}: {transaction.processedByName}</p>}
    {transaction.rejectionReason&&<p className="alert-banner alert-danger">{t('receiving.reason')}: {transaction.rejectionReason}</p>}
    {draft&&<>
      <label className="form-label">{t('draft.notes')}<textarea className="form-input" value={notes} maxLength={4000} disabled={disabled} onChange={e=>setNotes(e.target.value)}/></label>
      <button className="btn btn-secondary" disabled={disabled} onClick={()=>void mutate(()=>receivingApi.notes(id,version,notes))}>{t('draft.saveNotes')}</button>
      <p>{t('receiving.requirements')}</p>
      <button className="btn btn-secondary" disabled={disabled||!refs} onClick={()=>setEdit(null)}>{t('draft.addAsset')}</button>
      {edit!==undefined&&refs&&<ReceivingAssetForm key={edit?.assetId??'new'} initial={edit?.input} refs={refs} busy={disabled} onCancel={()=>setEdit(undefined)} onSave={(input:LineInput)=>mutate(()=>receivingApi.saveLine(id,version,input,edit?.assetId))}/>}
      <details><summary>{t('receiving.reuse')}</summary>
        <form onSubmit={e=>{e.preventDefault();void search(0);}} className="receiving-actions"><label>{t('receiving.search')}<input className="form-input" value={keyword} onChange={e=>setKeyword(e.target.value)} maxLength={100}/></label><button className="btn btn-secondary" disabled={disabled}>{t('receiving.search')}</button></form>
        {searched&&candidates.length===0&&<p>{t('receiving.noCandidates')}</p>}
        {candidates.map(a=><div key={a.assetId}>{a.assetTag} — {a.name} <button className="btn btn-secondary" disabled={disabled||content?.assets.some(x=>x.assetId===a.assetId)} onClick={()=>void mutate(()=>receivingApi.reuse(id,version,a.assetId))}>{t('receiving.select')}</button></div>)}
        {searched&&<div className="receiving-actions"><button className="btn btn-secondary" disabled={disabled||page===0} onClick={()=>void search(page-1)}>{t('pagination.previous')}</button><button className="btn btn-secondary" disabled={disabled||candidates.length<20} onClick={()=>void search(page+1)}>{t('pagination.next')}</button></div>}
      </details>
    </>}
    {!draft&&content?.notes&&<p>{content.notes}</p>}
    {content&&<div><h3>{t('draft.assets')} ({content.assets.length})</h3>
      {content.assets.length===0&&<p>{t('draft.noAssets')}</p>}
      {content.assets.map(a=><div key={a.assetId}><ReceivingAssetDetails line={a}/>{draft&&<div className="receiving-actions">
        <button className="btn btn-secondary" disabled={disabled||!a.input} onClick={()=>setEdit(a)}>{t('receiving.editAsset')}</button>
        <button className="btn btn-secondary" disabled={disabled} onClick={()=>{if(window.confirm(t('receiving.removeConfirm')))void mutate(()=>receivingApi.remove(id,version,a.assetId));}}>{t('receiving.remove')}</button>
      </div>}</div>)}
    </div>}
    <div className="receiving-actions">
      {draft&&<button className="btn btn-primary" disabled={disabled||!content?.assets.length||!content.documents.length||notes!==(content.notes??'')} onClick={()=>void mutate(()=>receivingApi.action(transaction,'submit'))}>{t('draft.submit')}</button>}
      {editor&&transaction.status==='PENDING'&&<button className="btn btn-secondary" disabled={disabled} onClick={()=>{if(window.confirm(t('receiving.withdrawConfirm')))void mutate(()=>receivingApi.action(transaction,'withdraw'));}}>{t('draft.withdraw')}</button>}
      {reviewer&&transaction.status==='PENDING'&&<>
        <button className="btn btn-primary" disabled={disabled||!content} onClick={()=>{if(window.confirm(t('receiving.approveConfirm')))void mutate(()=>receivingApi.action(transaction,'approve'));}}>{t('receiving.approve')}</button>
        <button className="btn btn-secondary" disabled={disabled} onClick={()=>setReject(true)}>{t('receiving.reject')}</button>
      </>}
      {editor&&transaction.status==='REJECTED'&&<button className="btn btn-secondary" disabled={disabled} onClick={()=>void mutate(async()=>{const created=await receivingApi.create(id);onCreated?.(created.data.transactionId);})}>{t('draft.copyRejected')}</button>}
    </div>
    {reject&&<div role="dialog" aria-modal="true" aria-labelledby="reject-title" className="receiving-reject"><form onSubmit={e=>{e.preventDefault();if(reason.trim())void mutate(()=>receivingApi.action(transaction,'reject',reason.trim()));}}>
      <h3 id="reject-title">{t('receiving.reject')}</h3><label>{t('receiving.reason')}<textarea autoFocus className="form-input" required maxLength={4000} value={reason} disabled={disabled} onChange={e=>setReason(e.target.value)}/></label>
      <button className="btn btn-primary" disabled={disabled||!reason.trim()}>{t('receiving.reject')}</button><button type="button" className="btn btn-secondary" disabled={busy} onClick={()=>setReject(false)}>{t('receiving.cancel')}</button>
    </form></div>}
    <h3>{t('draft.history')}</h3>{revisions.length===0&&<p>{t('draft.noHistory')}</p>}
    {revisions.map(r=><details key={r.revision}><summary>{t('draft.revision',{number:r.revision})} · {r.submittedBy} · {new Date(r.submittedAt).toLocaleString(i18n.language)}</summary>
      <p>{r.snapshot.notes}</p>{r.snapshot.assets.map(a=><ReceivingAssetDetails key={a.assetId} line={a}/>)}
      <ul>{r.snapshot.documents.map(d=><li key={d.documentId}>{d.originalFileName} · {t(`documentTypes.${d.documentType}`)} · {d.uploadedByName} <button className="btn btn-secondary btn-sm" disabled={busy} onClick={()=>void download(d)}>{t('actions.download')}</button></li>)}</ul>
      <ul>{events.filter(e=>e.revision===r.revision).map(e=><li key={e.action}>{t(`receiving.events.${e.action}`)} · {e.actor} · {new Date(e.time).toLocaleString(i18n.language)} {e.reason&&`— ${e.reason}`}</li>)}</ul>
    </details>)}
  </div>;
}
