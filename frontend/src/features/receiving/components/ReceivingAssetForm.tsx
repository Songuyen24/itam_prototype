import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { LineInput, References } from '../api/receivingApi';
export function ReceivingAssetForm({initial, refs, busy, onSave, onCancel}:{initial?:LineInput;refs:References;busy:boolean;onSave:(value:LineInput)=>Promise<void>;onCancel:()=>void}) {
  const {t}=useTranslation('documents');
  const [value,setValue]=useState<LineInput>(initial??{name:'',typeId:0,purchaseCost:0});
  const category=refs.types.find(x=>x.id===value.typeId)?.category;
  const patch=(data:Partial<LineInput>)=>setValue(v=>({...v,...data}));
  const field=(key:keyof LineInput,type='text',required=false,maxLength=255)=><label className="form-group" key={key}>{t(`receiving.${key}`)}<input className="form-input" type={type} required={required} maxLength={maxLength} min={type==='number'?0:undefined} step={type==='number'?'0.01':undefined} value={String(value[key]??'')} onChange={e=>patch({[key]:e.target.value===''?undefined:type==='number'?Number(e.target.value):e.target.value})}/></label>;
  const select=(key:keyof LineInput,options:References['types'],required=false)=><label className="form-group" key={key}>{t(`receiving.${key}`)}<select className="form-input" required={required} value={Number(value[key])||''} onChange={e=>patch({[key]:e.target.value?Number(e.target.value):undefined})}><option value="">—</option>{options.map(o=><option key={o.id} value={o.id}>{o.name}</option>)}</select></label>;
  const model=refs.models.find(m=>m.id===value.modelId);
  return <form onSubmit={e=>{e.preventDefault();void onSave(value);}} className="receiving-form">
    <fieldset disabled={busy}><legend>{t(initial?'receiving.editAsset':'draft.addAsset')}</legend>
      <div className="receiving-grid">
        {field('name','text',true)}
        <label className="form-group">{t('receiving.typeId')}<select className="form-input" required disabled={!!initial} value={value.typeId||''} onChange={e=>setValue(v=>({...v,typeId:Number(e.target.value),modelId:undefined,conditionId:undefined,serialNumber:undefined,warrantyExpiration:undefined,actualCpu:undefined,actualRam:undefined,actualStorage:undefined,actualGraphicsCard:undefined,license:refs.types.find(x=>x.id===Number(e.target.value))?.category==='LICENSE'?{softwareCatalogId:0,assignmentTypeId:0,termTypeId:0,seatCount:1}:undefined}))}><option value="">—</option>{refs.types.map(o=><option key={o.id} value={o.id}>{o.name}</option>)}</select></label>
        {field('assetTag','text',false,100)}{field('poNumber','text',false,100)}{field('purchaseDate','date')}{field('purchaseCost','number')}
        {select('supplierId',refs.suppliers)}{select('locationId',refs.locations)}{select('departmentId',refs.departments)}
        {category!=='LICENSE' && <>
          {field('serialNumber')}{select('conditionId',refs.conditions,true)}{select('modelId',refs.models.filter(m=>m.typeId===value.typeId))}{field('warrantyExpiration','date')}
          {field('actualCpu')}{field('actualRam','text',false,100)}{field('actualStorage','text',false,100)}{field('actualGraphicsCard')}
        </>}
        {value.license && <>
          {(['softwareCatalogId','assignmentTypeId','termTypeId'] as const).map((key,i)=><label className="form-group" key={key}>{t(`receiving.${key}`)}<select className="form-input" required value={value.license![key]||''} onChange={e=>patch({license:{...value.license!,[key]:Number(e.target.value)}})}><option value="">—</option>{refs[(['software','assignments','terms'] as const)[i]].map(o=><option key={o.id} value={o.id}>{o.code?t(`receiving.${o.code}`,{defaultValue:o.name}):o.name}</option>)}</select></label>)}
          <label className="form-group">{t('receiving.seatCount')}<input className="form-input" required type="number" min="1" max="2147483647" step="1" value={value.license.seatCount} onChange={e=>patch({license:{...value.license!,seatCount:Number(e.target.value)}})}/></label>
          <label className="form-group">{t('receiving.expiryDate')}<input className="form-input" type="date" required={refs.terms.find(x=>x.id===value.license!.termTypeId)?.code==='SUBSCRIPTION'} value={value.license.expiryDate??''} onChange={e=>patch({license:{...value.license!,expiryDate:e.target.value||undefined}})}/></label>
        </>}
      </div>
      {model && <p>{t('receiving.defaultConfig')}: {model.default_cpu||'—'} / {model.default_ram||'—'} / {model.default_storage||'—'} / {model.default_graphics_card||'—'}</p>}
      <p>{t('receiving.assetHint')}</p>
      <div className="receiving-actions"><button className="btn btn-primary">{t('receiving.saveAsset')}</button><button type="button" className="btn btn-secondary" onClick={onCancel}>{t('receiving.cancel')}</button></div>
    </fieldset>
  </form>;
}
