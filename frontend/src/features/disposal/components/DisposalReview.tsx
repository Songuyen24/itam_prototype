import { useTranslation } from 'react-i18next';
import { DisposalCheck } from '../api/disposalApi';

export function DisposalReview({ value, saving, onCancel, onConfirm }: {
  value: DisposalCheck; saving: boolean; onCancel: () => void; onConfirm: () => void;
}) {
  const { t } = useTranslation('disposal');
  const warningText = (warning: string) => warning.startsWith('PER_USER_LINKED:')
    ? t('perUserWarning', { assetTag: warning.slice('PER_USER_LINKED:'.length) }) : warning;
  return <div className="modal-overlay" role="dialog" aria-modal="true">
    <div className="modal-content modal-lg"><div className="modal-header"><h2 className="modal-title">{t('reviewTitle')}</h2></div>
      <div className="modal-body">
        {value.warnings.length > 0 && <div className="alert-banner alert-danger">{value.warnings.map(warning => <span key={warning}>{warningText(warning)}</span>)}</div>}
        <div className="candidate-list">{value.assets.map(asset => <div className="candidate-row" key={asset.assetId}><span><strong>{asset.assetTag}</strong><small>{asset.name} · {asset.category}{asset.autoAdded ? ` · ${t('autoAdded')}` : ''}</small></span></div>)}</div>
      </div>
      <div className="modal-footer"><button className="btn btn-secondary" onClick={onCancel}>{t('cancel')}</button><button className="btn btn-primary" disabled={saving} onClick={onConfirm}>{saving ? t('saving') : t('confirmCreate')}</button></div>
    </div>
  </div>;
}
