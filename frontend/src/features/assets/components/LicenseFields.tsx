import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { softwareCatalogApi, licenseAssignmentTypeApi, licenseTermTypeApi } from '@/features/catalogs/api/catalogApi';
import { SoftwareCatalogItem, LicenseAssignmentType, LicenseTermType } from '@/features/catalogs/types/catalog.types';
import { LicenseInput } from '../types/asset.types';

export function LicenseFields({ value, onChange }: {value: LicenseInput; onChange: (value: LicenseInput) => void}) {
  const { t } = useTranslation('assets');
  const [software, setSoftware] = useState<SoftwareCatalogItem[]>([]);
  const [assignments, setAssignments] = useState<LicenseAssignmentType[]>([]);
  const [terms, setTerms] = useState<LicenseTermType[]>([]);
  const [error, setError] = useState(false);
  const [loading, setLoading] = useState(true);
  const [retry, setRetry] = useState(0);
  useEffect(() => {
    let active = true; setError(false); setLoading(true);
    Promise.all([softwareCatalogApi.getAll(undefined,true,0,100),licenseAssignmentTypeApi.getAll(0,100),licenseTermTypeApi.getAll(0,100)])
      .then(([s,a,l]) => { if(active) {setSoftware(s.data.content);setAssignments(a.data.content);setTerms(l.data.content);} })
      .catch(()=>{if(active)setError(true);}).finally(()=>{if(active)setLoading(false);});
    return ()=>{active=false;};
  },[retry]);
  const patch = (data: Partial<LicenseInput>) => onChange({...value,...data});
  return <fieldset className="license-fields">
    <legend>{t('license.title')}</legend>
    {loading && <p role="status">{t('relationships.loading')}</p>}
    {error && <p role="alert">{t('relationships.loadError')} <button type="button" onClick={()=>setRetry(retry+1)}>{t('relationships.retry')}</button></p>}
    <label className="form-group">{t('license.software')}
      <select className="form-input" required value={value.softwareCatalogId || ''} onChange={e=>patch({softwareCatalogId:Number(e.target.value)})}>
        <option value="">{t('license.choose')}</option>{software.map(s=><option key={s.softwareCatalogId} value={s.softwareCatalogId}>{s.name} {s.version}</option>)}
      </select>
    </label>
    <label className="form-group">{t('license.assignment')}
      <select className="form-input" required value={value.assignmentTypeId || ''} onChange={e=>patch({assignmentTypeId:Number(e.target.value)})}>
        <option value="">{t('license.choose')}</option>{assignments.filter(a=>a.active).map(a=><option key={a.id} value={a.id}>{t(`license.${a.code}`)}</option>)}
      </select>
    </label>
    <label className="form-group">{t('license.term')}
      <select className="form-input" required value={value.termTypeId || ''} onChange={e=>patch({termTypeId:Number(e.target.value)})}>
        <option value="">{t('license.choose')}</option>{terms.filter(a=>a.active).map(a=><option key={a.id} value={a.id}>{t(`license.${a.code}`)}</option>)}
      </select>
    </label>
    <label className="form-group">{t('license.seats')}<input className="form-input" type="number" min="1" step="1" max="2147483647" required value={value.seatCount} onChange={e=>patch({seatCount:Number(e.target.value)})}/></label>
    <label className="form-group">{t('license.key')}<input className="form-input" type="password" maxLength={500} autoComplete="off" value={value.licenseKey || ''} onChange={e=>patch({licenseKey:e.target.value})}/></label>
    <label className="form-group">{t('license.expiry')}<input className="form-input" type="date" required={terms.find(x=>x.id===value.termTypeId)?.code==='SUBSCRIPTION'} value={value.expiryDate || ''} onChange={e=>patch({expiryDate:e.target.value || undefined})}/></label>
  </fieldset>;
}
