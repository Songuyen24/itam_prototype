import { DocumentPagination } from './DocumentPagination';
import { PaginatedContent } from '@/shared/components/PaginatedContent';
import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { ApiError } from '@/shared/api/httpClient';
import { useAuth } from '@/features/auth/contexts/AuthContext';
import { DocumentItem, DocumentTransaction } from '../types/document.types';
import { documentApi } from '../api/documentApi';
import { receivingApi, Content, Revision, Event, References, Line, LineInput } from '@/features/receiving/api/receivingApi';
import { ReceivingAssetForm } from '@/features/receiving/components/ReceivingAssetForm';
import { ReceivingAssetDetails } from '@/features/receiving/components/ReceivingAssetDetails';
import { AssetImportModal } from '@/features/assets/components/AssetImportModal';
import { ReceiptSubModal } from './ReceiptSubModal';

export function ImportDraftPanel({ transaction, onChanged, onCreated, compact=false }: { transaction: DocumentTransaction; onChanged: () => void; onCreated?: (id:number)=>void; compact?: boolean }) {
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
  const [candidateTotal,setCandidateTotal]=useState(0);
  const [candidatePages,setCandidatePages]=useState(0);
  const [candidates,setCandidates]=useState<Line[]>([]);
  const [searched,setSearched]=useState(false);
  const [loading,setLoading]=useState(true);
  const [busy,setBusy]=useState(false);
  const [error,setError]=useState<string|null>(null);
  const [conflict,setConflict]=useState(false);
  const [reject,setReject]=useState(false);
  const [reason,setReason]=useState('');
  const [confirmation,setConfirmation]=useState<{message:string;run:()=>void}|null>(null);
  const [excelOpen,setExcelOpen]=useState(false);
  const [excelChanged,setExcelChanged]=useState(false);
  const [historyOpen,setHistoryOpen]=useState(false);
  const [assetDetail,setAssetDetail]=useState<Line|null>(null);
  const editor=user?.role==='ADMIN'||(user?.role==='PUR_STAFF' && user.id===transaction.requesterId);
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
  function confirmAction(message: string, run: () => void) {
    if (compact) setConfirmation({message, run});
    else if (window.confirm(message)) run();
  }
  async function mutate(operation:()=>Promise<unknown>) {
    setBusy(true);setError(null);
    try {await operation();setEdit(undefined);setReject(false);onChanged();}
    catch(cause){setError(cause instanceof Error?cause.message:t('errors.conflict'));setConflict(cause instanceof ApiError && ['TRANSACTION_VERSION_CONFLICT','DOCUMENT_LOCKED','IMPORT_ASSET_UNAVAILABLE'].includes(cause.code??''));}
    finally{setBusy(false);}
  }
  async function search(next:number) {
    setBusy(true);setError(null);
    try {let result=await receivingApi.candidates(keyword,next);const last=Math.max(0,result.data.totalPages-1);if(next>last){next=last;result=await receivingApi.candidates(keyword,next);}setCandidates(result.data.content);setCandidateTotal(result.data.totalElements);setCandidatePages(result.data.totalPages);setPage(next);setSearched(true);}
    catch(cause){setError(cause instanceof Error?cause.message:t('errors.transaction'));}
    finally{setBusy(false);}
  }
  async function download(document:DocumentItem) {
    setBusy(true);setError(null);
    try{await documentApi.download(document);}catch(cause){setError(cause instanceof Error?cause.message:t('errors.download'));}finally{setBusy(false);}
  }
  if(loading)return <p role="status">{t('states.loading')}</p>;
  return <div className="document-draft-panel" aria-busy={busy}>
    {error&&<div role="alert" className="alert-banner alert-danger">{error} <button className="btn btn-secondary" onClick={onChanged}>{t('receiving.reload')}</button></div>}
    <div className="receiving-summary"><span>{t('receiving.createdBy')}: {transaction.requesterName||'—'}</span><span>{t('draft.revision',{number:transaction.submittedRevision??0})}</span>
    {content?.lastEditedBy&&<span>{t('receiving.lastEditedBy')}: {content.lastEditedBy}</span>}
    {content?.sourceReceivingId&&<span>{t('receiving.sourceReceivingId')}: {content.sourceReceivingId}</span>}
    {transaction.processedByName&&<span>{t('receiving.processedBy')}: {transaction.processedByName}</span>}</div>
    {transaction.rejectionReason&&<p className="alert-banner alert-danger">{t('receiving.reason')}: {transaction.rejectionReason}</p>}
    {draft&&<section className="receiving-assets-section"><div className="receiving-assets-toolbar"><div><h3>{t('draft.assets')} ({content?.assets.length??0})</h3><p>{t('receiving.requirements')}</p></div><div className="receiving-actions">
      <button className="btn btn-secondary" disabled={disabled||!refs} onClick={()=>setEdit(null)}>{t('draft.addAsset')}</button>
      <button className="btn btn-secondary" disabled={disabled} onClick={()=>setExcelOpen(true)}>{t('receiving.importExcel')}</button>
    </div></div>
      <AssetImportModal isOpen={excelOpen} onClose={()=>{setExcelOpen(false);if(excelChanged){setExcelChanged(false);onChanged();}}} receiving={{id,version}} onImportSuccess={()=>setExcelChanged(true)}/>
      {edit!==undefined&&refs&&(compact ? <ReceiptSubModal title={t(edit?'receiving.editAsset':'draft.addAsset')} busy={disabled} onClose={()=>setEdit(undefined)}><ReceivingAssetForm key={edit?.assetId??'new'} initial={edit?.input} refs={refs} busy={disabled} onCancel={()=>setEdit(undefined)} onSave={(input:LineInput)=>mutate(()=>receivingApi.saveLine(id,version,input,edit?.assetId))}/></ReceiptSubModal> : <ReceivingAssetForm key={edit?.assetId??'new'} initial={edit?.input} refs={refs} busy={disabled} onCancel={()=>setEdit(undefined)} onSave={(input:LineInput)=>mutate(()=>receivingApi.saveLine(id,version,input,edit?.assetId))}/>)}

    </section>}
    {content&&<section className="receiving-assets-list">{!draft&&<h3>{t('draft.assets')} ({content.assets.length})</h3>}
      {content.assets.length===0&&<p>{t(draft ? 'draft.noAssets' : 'receiving.noRecordedAssets')}</p>}
      <PaginatedContent items={content.assets}>{rows => compact ? <div className="receiving-table-scroll"><table className="data-table receiving-assets-table"><thead><tr><th>{t('assets:fields.assetTag')}</th><th>{t('assets:fields.name')}</th><th>{t('receiving.serialNumber')}</th><th>{t('fields.actions')}</th></tr></thead><tbody>{rows.map(a=><tr key={a.assetId}><td><strong>{a.assetTag}</strong></td><td>{a.name}</td><td>{a.input?.serialNumber || String(a.hardware?.serial_number ?? '—')}</td><td><div className="receiving-actions"><button type="button" className="btn btn-secondary" onClick={()=>setAssetDetail(a)}>{t('receiving.viewAsset')}</button>{draft&&<><button className="btn btn-secondary" disabled={disabled||!a.input} onClick={()=>setEdit(a)}>{t('receiving.editAsset')}</button><button className="btn btn-secondary" disabled={disabled} onClick={()=>confirmAction(t('receiving.removeConfirm'),()=>void mutate(()=>receivingApi.remove(id,version,a.assetId)))}>{t('receiving.remove')}</button></>}</div></td></tr>)}</tbody></table></div> : rows.map(a=><article className="receiving-asset-item" key={a.assetId}><ReceivingAssetDetails line={a}/>{draft&&<div className="receiving-actions"><button className="btn btn-secondary" disabled={disabled||!a.input} onClick={()=>setEdit(a)}>{t('receiving.editAsset')}</button><button className="btn btn-secondary" disabled={disabled} onClick={()=>confirmAction(t('receiving.removeConfirm'),()=>void mutate(()=>receivingApi.remove(id,version,a.assetId)))}>{t('receiving.remove')}</button></div>}</article>)}</PaginatedContent>
      {compact&&assetDetail&&<ReceiptSubModal title={assetDetail.assetTag} onClose={()=>setAssetDetail(null)}><ReceivingAssetDetails line={assetDetail}/></ReceiptSubModal>}
    </section>}
    {draft&&<details className="receiving-reuse"><summary>{t('receiving.reuse')}</summary>
        <form onSubmit={e=>{e.preventDefault();void search(0);}} className="receiving-actions"><label>{t('receiving.search')}<input className="form-input" value={keyword} disabled={disabled} onChange={e=>{setKeyword(e.target.value);setPage(0);setSearched(false);setCandidates([]);}} maxLength={100}/></label><button className="btn btn-secondary" disabled={disabled}>{t('receiving.search')}</button></form>
        {searched&&candidates.length===0&&<p>{t('receiving.noCandidates')}</p>}
        {candidates.map(a=><div className="receiving-candidate-row" key={a.assetId}><strong>{a.assetTag}</strong><span>{a.name}</span><span>{a.input?.serialNumber || String(a.hardware?.serial_number ?? '—')}</span> <button className="btn btn-secondary" disabled={disabled||content?.assets.some(x=>x.assetId===a.assetId)} onClick={()=>void mutate(()=>receivingApi.reuse(id,version,a.assetId))}>{t('receiving.select')}</button></div>)}
        {searched&&(!compact||candidateTotal>0)&&<DocumentPagination page={page} totalPages={candidatePages} totalElements={candidateTotal} loading={disabled} onPageChange={next=>void search(next)}/>}
      </details>}
    {draft&&<section className="receiving-notes-section"><h3>{t('draft.notes')}</h3>
      <textarea className="form-input" aria-label={t('draft.notes')} value={notes} maxLength={4000} disabled={disabled} onChange={e=>setNotes(e.target.value)}/>
      <button className="btn btn-secondary" disabled={disabled} onClick={()=>void mutate(()=>receivingApi.notes(id,version,notes))}>{t('draft.saveNotes')}</button>
    </section>}
    {!draft&&content?.notes&&<section className="receiving-notes-section"><h3>{t('draft.notes')}</h3><p>{content.notes}</p></section>}
    {draft && <p id="receiving-submit-help" className="receiving-submit-help" role="status">{conflict ? t("errors.conflict") : !content?.assets.length ? t("receiving.submitNeedsAssets") : notes !== (content.notes ?? "") ? t("receiving.submitNeedsNotes") : t("receiving.submitReady")}</p>}
    <div className="receiving-actions receiving-workflow-actions">
      {(draft || (user?.role==='ADMIN' && transaction.status==='PENDING')) && <button className="btn btn-secondary" disabled={disabled} onClick={()=>{confirmAction(t('draft.cancelConfirm'),()=>void mutate(()=>receivingApi.action(transaction,'cancel')));}}>{t('draft.cancelTransaction')}</button>}
      {draft&&<button aria-describedby="receiving-submit-help" className="btn btn-primary" disabled={disabled||!content?.assets.length||notes!==(content.notes??'')} onClick={()=>void mutate(()=>receivingApi.action(transaction,'submit'))}>{t('draft.submit')}</button>}
      {editor&&transaction.status==='PENDING'&&<button className="btn btn-secondary" disabled={disabled} onClick={()=>{confirmAction(t('receiving.withdrawConfirm'),()=>void mutate(()=>receivingApi.action(transaction,'withdraw')));}}>{t('draft.withdraw')}</button>}
      {reviewer&&transaction.status==='PENDING'&&<>
        <button className="btn btn-primary" disabled={disabled||!content} onClick={()=>{confirmAction(t('receiving.approveConfirm'),()=>void mutate(()=>receivingApi.action(transaction,'approve')));}}>{t('receiving.approve')}</button>
        <button className="btn btn-secondary" disabled={disabled} onClick={()=>setReject(true)}>{t('receiving.reject')}</button>
      </>}
      {editor&&transaction.status==='REJECTED'&&<button className="btn btn-secondary" disabled={disabled} onClick={()=>void mutate(async()=>{const created=await receivingApi.create(id);onCreated?.(created.data.transactionId);})}>{t('draft.copyRejected')}</button>}
    </div>
    {confirmation&&<ReceiptSubModal size="confirmation" title={t('receiving.confirmAction')} busy={busy} onClose={()=>setConfirmation(null)}><p>{confirmation.message}</p><div className="receiving-actions"><button type="button" className="btn btn-secondary" onClick={()=>setConfirmation(null)}>{t('receiving.cancel')}</button><button type="button" className="btn btn-primary" onClick={()=>{const run=confirmation.run;setConfirmation(null);run();}}>{t('receiving.confirmAction')}</button></div></ReceiptSubModal>}
    {reject&&<ReceiptSubModal size="confirmation" title={t("receiving.reject")} busy={busy} onClose={()=>setReject(false)}><form onSubmit={e=>{e.preventDefault();if(reason.trim())void mutate(()=>receivingApi.action(transaction,'reject',reason.trim()));}}>
      <h3 id="reject-title">{t('receiving.reject')}</h3><label>{t('receiving.reason')}<textarea autoFocus className="form-input" required maxLength={4000} value={reason} disabled={disabled} onChange={e=>setReason(e.target.value)}/></label>
      <button className="btn btn-primary" disabled={disabled||!reason.trim()}>{t('receiving.reject')}</button><button type="button" className="btn btn-secondary" disabled={busy} onClick={()=>setReject(false)}>{t('receiving.cancel')}</button>
    </form></ReceiptSubModal>}
    {compact&&<button type="button" className="btn btn-secondary" onClick={()=>setHistoryOpen(true)}>{t('draft.history')}</button>}
    {(!compact||historyOpen)&&(compact?<ReceiptSubModal title={t('draft.history')} onClose={()=>setHistoryOpen(false)}><div className="receiving-history-content">{revisions.length===0&&<p>{t('draft.noHistory')}</p>}
    <PaginatedContent items={revisions}>{rows => rows.map(r=><details key={r.revision}><summary>{t('draft.revision',{number:r.revision})} · {r.submittedBy} · {new Date(r.submittedAt).toLocaleString(i18n.language)}</summary>
      <p>{r.snapshot.notes}</p><PaginatedContent items={r.snapshot.assets}>{assets => assets.map(a=><ReceivingAssetDetails key={a.assetId} line={a}/>)}</PaginatedContent>
      <PaginatedContent items={r.snapshot.documents}>{documents => <ul>{documents.map(d=><li key={d.documentId}>{d.originalFileName} · {t(`documentTypes.${d.documentType}`)} · {d.uploadedByName} <button className="btn btn-secondary btn-sm" disabled={busy} onClick={()=>void download(d)}>{t('actions.download')}</button></li>)}</ul>}</PaginatedContent>
      <ul>{events.filter(e=>e.revision===r.revision).map(e=><li key={e.action}>{t(`receiving.events.${e.action}`)} · {e.actor} · {new Date(e.time).toLocaleString(i18n.language)} {e.reason&&`— ${e.reason}`}</li>)}</ul>
    </details>)}</PaginatedContent></div></ReceiptSubModal> : <details className="receiving-history"><summary>{t('draft.history')}</summary>{revisions.length===0&&<p>{t('draft.noHistory')}</p>}
    <PaginatedContent items={revisions}>{rows => rows.map(r=><details key={r.revision}><summary>{t('draft.revision',{number:r.revision})} · {r.submittedBy} · {new Date(r.submittedAt).toLocaleString(i18n.language)}</summary>
      <p>{r.snapshot.notes}</p><PaginatedContent items={r.snapshot.assets}>{assets => assets.map(a=><ReceivingAssetDetails key={a.assetId} line={a}/>)}</PaginatedContent>
      <PaginatedContent items={r.snapshot.documents}>{documents => <ul>{documents.map(d=><li key={d.documentId}>{d.originalFileName} · {t(`documentTypes.${d.documentType}`)} · {d.uploadedByName} <button className="btn btn-secondary btn-sm" disabled={busy} onClick={()=>void download(d)}>{t('actions.download')}</button></li>)}</ul>}</PaginatedContent>
      <ul>{events.filter(e=>e.revision===r.revision).map(e=><li key={e.action}>{t(`receiving.events.${e.action}`)} · {e.actor} · {new Date(e.time).toLocaleString(i18n.language)} {e.reason&&`— ${e.reason}`}</li>)}</ul>
    </details>)}</PaginatedContent></details>)}
  </div>;
}
