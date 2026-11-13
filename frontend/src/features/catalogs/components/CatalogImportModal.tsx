import { useEffect, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { catalogImportApi, type CatalogImportRow, type ImportableCatalog } from '../api/catalogImportApi';
import './catalog-import.css';

export function CatalogImportModal({ kind, onClose, onImported }: { kind: ImportableCatalog; onClose: () => void; onImported: () => void }) {
  const { t } = useTranslation('catalogs');
  const [file,setFile]=useState<File|null>(null);
  const [rows,setRows]=useState<CatalogImportRow[]>([]);
  const [columns,setColumns]=useState<string[]>([]);
  const [previewed,setPreviewed]=useState(false);
  const [confirmed,setConfirmed]=useState(false);
  const [busy,setBusy]=useState(false);
  const [error,setError]=useState('');
  const [page,setPage]=useState(0);
  const dialog=useRef<HTMLDialogElement>(null);
  useEffect(()=>{if(typeof document==='undefined')return;const native=dialog.current;const opener=document.activeElement as HTMLElement|null;native?.showModal();return()=>{native?.close();opener?.focus();};},[]);
  const preview=async()=>{if(!file)return;setBusy(true);setError('');setRows([]);setPreviewed(false);try{const result=await catalogImportApi.preview(kind,file);setRows(result.data.rows);setColumns(result.data.columns);setPage(0);setPreviewed(true);}catch(cause){setError(cause instanceof Error?cause.message:t('import.error'));}finally{setBusy(false);}};
  const confirm=async()=>{if(!file)return;setBusy(true);setError('');try{const result=await catalogImportApi.confirm(kind,file);setRows(result.data.rows);setColumns(result.data.columns);setConfirmed(true);setPage(0);onImported();}catch(cause){setError(cause instanceof Error?cause.message:t('import.error'));}finally{setBusy(false);}};
  const template=async()=>{setError('');try{await catalogImportApi.template(kind);}catch(cause){setError(cause instanceof Error?cause.message:t('import.error'));}};
  const issue=(code:string)=>{
    if((code.startsWith('REQUIRED_')||code.startsWith('TOO_LONG_'))&&Number.isInteger(Number(code.slice(code.lastIndexOf('_')+1)))){
      const column=columns[Number(code.slice(code.lastIndexOf('_')+1))]||'';
      return t(code.startsWith('REQUIRED_')?'import.required':'import.tooLong',{column});
    }
    return t(`import.codes.${code}`,{defaultValue:t('import.error')});
  };
  return <dialog ref={dialog} className="catalog-import-dialog" aria-label={t('import.title')} onCancel={event=>{if(busy)event.preventDefault();else onClose();}}>
    <div className="catalog-import-heading"><h2>{t('import.title')}: {t(`tabs.${kind}`)}</h2><button type="button" className="btn btn-secondary" disabled={busy} onClick={onClose}>{t('import.close')}</button></div>
    <button type="button" className="btn btn-secondary" disabled={busy} onClick={()=>void template()}>{t('import.template')}</button>
    <label>{t('import.file')}<input className="form-input" type="file" accept=".xlsx" disabled={busy} onChange={event=>{setFile(event.target.files?.[0]??null);setRows([]);setColumns([]);setPreviewed(false);setConfirmed(false);setPage(0);setError('');}} /></label>
    <button type="button" className="btn btn-secondary" disabled={!file||busy} onClick={()=>void preview()}>{t('import.preview')}</button>
    {busy&&<p role="status">{t('import.loading')}</p>}{error&&<p role="alert">{error}</p>}
    {previewed&&rows.length===0&&<p>{t('import.empty')}</p>}
    {rows.length>0&&<><div className="table-container"><table className="data-table"><thead><tr><th>#</th>{columns.map(column=><th key={column}>{column}</th>)}<th>{t('import.status')}</th><th>{t('import.errors')}</th></tr></thead><tbody>{rows.slice(page*20,(page+1)*20).map(row=><tr key={row.row}><td>{row.row}</td>{row.values.map((value,index)=><td key={index}>{value||'—'}</td>)}<td>{t(`import.states.${row.status}`)}</td><td>{row.errors.map(issue).join(', ')||'—'}</td></tr>)}</tbody></table></div><div className="catalog-import-pages"><button type="button" className="btn btn-secondary" disabled={page===0} onClick={()=>setPage(value=>value-1)}>{t('import.previous')}</button><span>{page+1}/{Math.ceil(rows.length/20)}</span><button type="button" className="btn btn-secondary" disabled={(page+1)*20>=rows.length} onClick={()=>setPage(value=>value+1)}>{t('import.next')}</button></div></>}
    {file&&previewed&&!confirmed&&rows.some(row=>row.status==='VALID')&&<button type="button" className="btn btn-primary" disabled={busy} onClick={()=>void confirm()}>{t('import.confirm')}</button>}
    {confirmed&&<p role="status">{t('import.completed')}</p>}
  </dialog>;
}
