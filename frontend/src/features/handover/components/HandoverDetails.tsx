import { useTranslation } from 'react-i18next';
import { Handover } from '../api/handoverApi';

export function HandoverDetails({ value }: { value: Handover }) {
  const { t } = useTranslation('handover');
  return <section className="handover-details">
    <h2>{value.transactionCode ?? t('preview')}</h2>
    <p>{t('recipient')}: <strong>{value.recipientName}</strong> · {value.recipientEmail}</p>
    <p>{t('location')}: {value.destinationLocationName} · {t('date')}: {value.handoverDate}</p>
    {value.notes && <p>{t('notes')}: {value.notes}</p>}
    <div className="table-container"><table className="data-table">
      <thead><tr><th>{t('asset')}</th><th>{t('bundle')}</th><th>{t('seats')}</th><th>{t('configuration')}</th></tr></thead>
      <tbody>{value.lines.map(line => <tr key={line.assetId}>
        <td><strong>{line.assetTag}</strong><br />{line.name}</td>
        <td>{line.parentAssetId ? t('withParent', { id: value.lines.find(a => a.assetId === line.parentAssetId)?.assetTag ?? line.parentAssetId }) : t(`categories.${line.category}`)}
          {line.allocations.map((a, i) => <div key={i}>{a.assignmentType} · {a.deviceId ? t('withParent', { id: a.deviceTag ?? value.lines.find(d => d.assetId === a.deviceId)?.assetTag ?? a.deviceId }) : t('toUser')}
            {a.allocationId != null && <span> · {t('allocationId')}: {a.allocationId}</span>}</div>)}</td>
        <td>{line.category === 'LICENSE' ? line.seats : '—'}</td>
        <td>{line.details.serialNumber && <div>{t('serial')}: {line.details.serialNumber}</div>}
          {line.details.modelName && <div>{line.details.modelName}</div>}
          {(['Cpu', 'Ram', 'Storage', 'GraphicsCard'] as const).map(key => {
            const effective = line.details[`actual${key}`] ?? line.details[`default${key}`];
            return effective ? <div key={key}>{t(`hardware.${key}`)}: {effective}</div> : null;
          })}
          {line.details.softwareName && <div>{line.details.softwareName}</div>}
        </td>
      </tr>)}</tbody>
    </table></div>
  </section>;
}
