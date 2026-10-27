import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Link } from 'react-router-dom';
import { adjustmentApi, Adjustment, AdjustmentOverview } from '../api/adjustmentApi';

export function AdjustmentPanel({transactionId,isAdmin}:{transactionId:number;isAdmin:boolean}) {
  const {t,i18n}=useTranslation('documents');
  const [value,setValue]=useState<AdjustmentOverview|null>(null);
  const [page,setPage]=useState(0);
  const [error,setError]=useState('');
  const [busy,setBusy]=useState(false);
  const [kind,setKind]=useState('DESCRIPTION');
  const [decision,setDecision]=useState<Record<number,string>>({});
  useEffect(()=>{let active=true; setValue(null); adjustmentApi.list(transactionId,page).then(r=>{if(active)setValue(r.data);}).catch(cause=>{if(active)setError(cause instanceof Error?cause.message:t('adjustment.error'));}); return()=>{active=false;};},[transactionId,page,t]);
  async function reload(){setValue((await adjustmentApi.list(transactionId,page)).data);}
  async function submit(event:React.FormEvent<HTMLFormElement>){
    event.preventDefault(); const form=event.currentTarget; const body=new FormData(form);
    if(!body.get('correctiveTransactionId'))body.delete('correctiveTransactionId');
    const files=body.getAll('files').filter(f=>f instanceof File && f.size>0); body.delete('files'); files.forEach(f=>body.append('files',f));
    setBusy(true);setError('');
    try{await adjustmentApi.create(transactionId,body);form.reset();setKind('DESCRIPTION');await reload();}catch(cause){setError(cause instanceof Error?cause.message:t('adjustment.error'));}finally{setBusy(false);}
  }
  async function decide(request:Adjustment,approve:boolean){
    if(!value)return;setBusy(true);setError('');
    try{await adjustmentApi.decide(transactionId,request,approve,decision[request.id]||'',value.subsequentTransactions);await reload();}catch(cause){setError(cause instanceof Error?cause.message:t('adjustment.error')); await reload().catch(()=>{});}finally{setBusy(false);}
  }
  return <section className="adjustment-panel" aria-label={t('adjustment.title')}>
    <h3>{t('adjustment.title')}</h3><p>{t('adjustment.notice')}</p>
    {error&&<p role="alert">{error}</p>}
    {!value?<p>{error?<button onClick={()=>void reload().then(()=>setError('')).catch(cause=>setError(cause instanceof Error?cause.message:t('adjustment.error')))}>{t('adjustment.retry')}</button>:t('adjustment.loading')}</p>:<>
      <h4>{t('adjustment.impact')}</h4><p>{t('adjustment.impactNotice')}</p>
      {value.subsequentTransactions.length===0?<p>{t('adjustment.none')}</p>:<ul>{value.subsequentTransactions.map(i=><li key={i.transactionId}><Link to={`/documents?transactionId=${i.transactionId}`}>{i.transactionCode}</Link> — {t(`transactionTypes.${i.type}`,{defaultValue:i.type})} / {t(`statuses.${i.status}`,{defaultValue:i.status})}</li>)}</ul>}
      {value.canRequest&&<form onSubmit={submit}>
        <fieldset disabled={busy}><legend>{t('adjustment.new')}</legend>
          <label>{t('adjustment.kind')}<select className="form-input" name="kind" value={kind} onChange={e=>setKind(e.target.value)}>{['DESCRIPTION','DOCUMENT','WORKFLOW'].map(k=><option key={k} value={k}>{t(`adjustment.kinds.${k}`)}</option>)}</select></label>
          <label>{t('adjustment.reason')}<textarea className="form-textarea" name="reason" required maxLength={4000}/></label>
          <label>{t('adjustment.text')}<textarea className="form-textarea" name="proposedText" required maxLength={8000}/></label>
          {kind==='WORKFLOW'&&<><p>{t('adjustment.workflow')}</p><label>{t('adjustment.link')}<input className="form-input" name="correctiveTransactionId" type="number" min="1" step="1"/></label></>}
          <label>{t('adjustment.files')}<input name="files" type="file" multiple accept=".pdf,.png,.jpg,.jpeg" required={kind==='DOCUMENT'}/></label>
          <p>{t('adjustment.lock')}</p><button className="btn btn-primary" type="submit">{t('adjustment.submit')}</button>
        </fieldset>
      </form>}
      <nav aria-label={t('adjustment.title')}><button className="btn" disabled={busy||page===0} onClick={()=>setPage(page-1)}>←</button> {page+1} / {Math.max(1,Math.ceil(value.total/10))} <button className="btn" disabled={busy||(page+1)*10>=value.total} onClick={()=>setPage(page+1)}>→</button></nav>
      {value.requests.map(r=><article key={r.id} className="adjustment-request">
        <h4>{t('adjustment.version',{version:r.version})} — {t(`adjustment.states.${r.status}`)}</h4>
        <p>{t(`adjustment.kinds.${r.kind}`)} · {r.requesterName} · {new Date(r.createdAt).toLocaleString(i18n?.language)}</p>
        <p><strong>{t('adjustment.reason')}: </strong>{r.reason}</p><p className="adjustment-text">{r.proposedText}</p>
        {r.correctiveTransactionId&&<Link to={`/documents?transactionId=${r.correctiveTransactionId}`}>{t('adjustment.corrective')} #{r.correctiveTransactionId}</Link>}
        <ul>{r.files.map(f=><li key={f.fileId}><button disabled={busy} type="button" className="btn btn-secondary" onClick={()=>void adjustmentApi.download(transactionId,r.id,f).catch(()=>setError(t('adjustment.error')))}>{f.name}</button></li>)}</ul>
        {r.decisionReason&&<p>{r.deciderName} · {r.decidedAt&&new Date(r.decidedAt).toLocaleString(i18n?.language)} — {r.decisionReason}</p>}
        {r.impactSnapshot&&<details><summary>{t('adjustment.reviewed')}</summary><ul>{(JSON.parse(r.impactSnapshot) as {transactionId:number;transactionCode:string;status:string}[]).map(i=><li key={i.transactionId}>{i.transactionCode} — {t(`statuses.${i.status}`,{defaultValue:i.status})}</li>)}</ul></details>}
        {isAdmin&&r.status==='PENDING'&&<div><p>{t('adjustment.selfApproval')}</p><label>{t('adjustment.decision')}<textarea className="form-textarea" maxLength={4000} disabled={busy} value={decision[r.id]||''} onChange={e=>setDecision({...decision,[r.id]:e.target.value})}/></label>
          <button className="btn btn-primary" disabled={busy||!decision[r.id]?.trim()} onClick={()=>void decide(r,true)}>{t('adjustment.approve')}</button>
          <button className="btn btn-secondary" disabled={busy||!decision[r.id]?.trim()} onClick={()=>void decide(r,false)}>{t('adjustment.reject')}</button></div>}
      </article>)}
    </>}
  </section>;
}


