import { useTranslation } from 'react-i18next';
import { RecoveryForm } from '../components/RecoveryForm';
import { Recovery } from '../api/recoveryApi';
import { useState } from 'react';

export function RecoveriesPage() {
  const { t } = useTranslation('recovery');
  const [history, setHistory] = useState<Recovery[]>([]);
  const [tab, setTab] = useState<'form' | 'history'>('form');

  function handleCompleted(result: Recovery) {
    setHistory(prev => [result, ...prev]);
    setTab('history');
  }

  return (
    <div className="page-container">
      <div className="page-header">
        <h1>{t('title')}</h1>
      </div>
      <div className="page-tabs">
        <button className={tab === 'form' ? 'tab active' : 'tab'} onClick={() => setTab('form')}>{t('tabForm')}</button>
        <button className={tab === 'history' ? 'tab active' : 'tab'} onClick={() => setTab('history')}>
          {t('tabHistory')} {history.length > 0 && <span className="badge">{history.length}</span>}
        </button>
      </div>
      {tab === 'form' ? (
        <div className="page-content">
          <RecoveryForm onCompleted={handleCompleted} />
        </div>
      ) : (
        <div className="page-content">
          {history.length === 0 ? (
            <p style={{ color: '#666', textAlign: 'center', padding: 40 }}>{t('noHistory')}</p>
          ) : (
            <table className="data-table">
              <thead>
                <tr>
                  <th>{t('colCode')}</th>
                  <th>{t('colReturner')}</th>
                  <th>{t('colDate')}</th>
                  <th>{t('colLocation')}</th>
                  <th>{t('colAssets')}</th>
                </tr>
              </thead>
              <tbody>
                {history.map(r => (
                  <tr key={r.transactionId}>
                    <td><strong>{r.transactionCode}</strong></td>
                    <td>{r.returnerName}</td>
                    <td>{r.recoveryDate}</td>
                    <td>{r.receivingLocationName}</td>
                    <td>{r.lines?.length || 0}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>
      )}
    </div>
  );
}
