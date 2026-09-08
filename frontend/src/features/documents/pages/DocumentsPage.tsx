import { useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { TransactionSelector } from '../components/TransactionSelector';
import { TransactionDocuments } from '../components/TransactionDocuments';
import './documents.css';

export function DocumentsPage() {
  const { t } = useTranslation('documents');
  const [transactionId, setTransactionId] = useState<number | null>(null);
  const details = useRef<HTMLDivElement>(null);

  function selectTransaction(id: number) {
    setTransactionId(id);
    if (window.matchMedia('(max-width: 1100px)').matches) {
      details.current?.scrollIntoView({ behavior: 'smooth', block: 'start' });
    }
  }

  return (
    <div className="documents-page">
      <div className="page-header"><h1 className="page-title">{t('title')}</h1></div>
      <div className="documents-workspace">
        <TransactionSelector selectedId={transactionId} onSelect={selectTransaction} />
        <div ref={details} className="document-detail-container">
          <TransactionDocuments key={transactionId ?? 'none'} transactionId={transactionId} />
        </div>
      </div>
    </div>
  );
}
