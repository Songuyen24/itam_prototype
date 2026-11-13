import { useTranslation } from 'react-i18next';
import { Line } from '../api/receivingApi';
export function ReceivingAssetDetails({line}:{line:Line}) {
  const {t,i18n}=useTranslation('documents'); const input=line.input;
  return <div className="receiving-line"><strong>{line.assetTag} — {line.name}</strong>
    {input ? <div className="receiving-detail-groups">
      <section><h3>{t('receiving.sections.identity')}</h3><dl className="receiving-grid">
        {Object.entries(line.labels??{}).filter(([key])=>!['supplier','location','department'].includes(key)).map(([key,value])=><div key={key}><dt>{t(`receiving.${key}`)}</dt><dd>{value}</dd></div>)}
        {(['serialNumber','warrantyExpiration'] as const).filter(k=>input[k]).map(k=><div key={k}><dt>{t(`receiving.${k}`)}</dt><dd>{input[k]}</dd></div>)}
      </dl></section>
      <section><h3>{t('receiving.sections.purchase')}</h3><dl className="receiving-grid">
        {Object.entries(line.labels??{}).filter(([key])=>['supplier','location','department'].includes(key)).map(([key,value])=><div key={key}><dt>{t(`receiving.${key}`)}</dt><dd>{value}</dd></div>)}
        {(['poNumber','purchaseDate'] as const).filter(k=>input[k]).map(k=><div key={k}><dt>{t(`receiving.${k}`)}</dt><dd>{input[k]}</dd></div>)}
        <div><dt>{t('receiving.purchaseCost')}</dt><dd>{new Intl.NumberFormat(i18n.language).format(input.purchaseCost??0)}</dd></div>
      </dl></section>
      {input.license && <section><h3>{t('receiving.sections.license')}</h3><dl className="receiving-grid"><div><dt>{t('receiving.seatCount')}</dt><dd>{input.license.seatCount}</dd></div><div><dt>{t('receiving.expiryDate')}</dt><dd>{input.license.expiryDate||'—'}</dd></div></dl></section>}
    </div> : <dl className="receiving-grid">{Object.entries({...line.assetData,...line.hardware,...line.license,...line.model}).filter(([k,v])=>v!=null&&!k.endsWith('_id')&&!['created_by','updated_by','assigned_to','license_key'].includes(k)).map(([k,v])=><div key={k}><dt>{t(`receiving.legacy.${k}`,{defaultValue:k})}</dt><dd>{String(v)}</dd></div>)}</dl>}
    {input && line.category!=='LICENSE' && <section className="receiving-detail-technical"><h3>{t('receiving.sections.configuration')}</h3><dl className="receiving-grid">
      {(['actualCpu','actualRam','actualStorage','actualGraphicsCard'] as const).filter(k=>input[k]).map(k=><div key={k}><dt>{t(`receiving.${k}`)}</dt><dd>{input[k]}</dd></div>)}
      {line.category!=='LICENSE' && <><div><dt>{t('receiving.defaultConfig')}</dt><dd>{['default_cpu','default_ram','default_storage','default_graphics_card'].map(k=>line.model?.[k]||'—').join(' / ')}</dd></div><div><dt>{t('receiving.effectiveConfig')}</dt><dd>{Object.values(line.effectiveHardware??{}).map(v=>v||'—').join(' / ')}</dd></div></>}
    </dl></section>}
  </div>;
}
