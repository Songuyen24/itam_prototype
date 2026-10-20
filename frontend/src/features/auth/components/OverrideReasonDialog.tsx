import { useEffect, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { registerOverrideReasonHandler } from '@/shared/api/httpClient';

export function OverrideReasonDialog() {
  const { t } = useTranslation('auth');
  const [open,setOpen] = useState(false);
  const [reason,setReason] = useState('');
  const pending = useRef<((reason:string|null)=>void)|null>(null);
  const dialog = useRef<HTMLDialogElement>(null);
  const opener = useRef<HTMLElement|null>(null);
  const finish=(value:string|null)=>{
    pending.current?.(value);pending.current=null;setOpen(false);
    dialog.current?.close();opener.current?.focus();
  };
  useEffect(()=>{
    registerOverrideReasonHandler(()=>new Promise(resolve=>{
      pending.current?.(null);pending.current=resolve;
      opener.current=document.activeElement instanceof HTMLElement?document.activeElement:null;
      setReason('');setOpen(true);
    }));
    return ()=>{registerOverrideReasonHandler(null);pending.current?.(null);pending.current=null;};
  },[]);
  useEffect(()=>{if(open){dialog.current?.showModal();dialog.current?.querySelector('textarea')?.focus();}},[open]);
  return <dialog ref={dialog} aria-labelledby="override-title" style={{width:'min(520px,90vw)',border:'1px solid var(--border-color)',borderRadius:8,padding:24}} onCancel={event=>{event.preventDefault();finish(null);}}>
    <form onSubmit={event=>{event.preventDefault();if(reason.trim())finish(reason.trim());}}>
      <h2 id="override-title">{t('access.overrideTitle')}</h2><p>{t('access.overrideHint')}</p>
      <label style={{display:'block'}}>{t('access.overrideReason')}<textarea className="form-input" rows={4} required maxLength={4000} value={reason} onChange={event=>setReason(event.target.value)}/></label>
      <div style={{display:'flex',gap:12,marginTop:16}}><button className="btn btn-primary" disabled={!reason.trim()}>{t('access.overrideConfirm')}</button><button className="btn btn-secondary" type="button" onClick={()=>finish(null)}>{t('access.cancel')}</button></div>
    </form>
  </dialog>;
}
