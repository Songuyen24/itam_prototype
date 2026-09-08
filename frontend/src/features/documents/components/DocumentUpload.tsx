import { FormEvent, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { documentApi } from '../api/documentApi';
import { DocumentItem, DocumentType } from '../types/document.types';
import { ApiError } from '@/shared/api/httpClient';
import { useAuth } from '@/features/auth/contexts/AuthContext';

export const MAX_DOCUMENT_SIZE = 10 * 1024 * 1024;
const FILE_TYPES: Record<string, string> = {
  pdf: 'application/pdf',
  png: 'image/png',
  jpg: 'image/jpeg',
  jpeg: 'image/jpeg',
};

export function validateDocumentFile(file: Pick<File, 'name' | 'size' | 'type'>): string | null {
  if (!file.size) return 'validation.emptyFile';
  if (file.size > MAX_DOCUMENT_SIZE) return 'validation.fileTooLarge';
  const extension = file.name.split('.').pop()?.toLowerCase() ?? '';
  if (!Object.prototype.hasOwnProperty.call(FILE_TYPES, extension) || FILE_TYPES[extension] !== file.type) return 'validation.fileType';
  return null;
}

interface DocumentUploadProps {
  transactionId: number;
  documentsEditable: boolean;
  expectedVersion?: number;
  assetId?: number;
  onUploaded: (document: DocumentItem) => void;
}

export function DocumentUpload({ transactionId, documentsEditable, expectedVersion, assetId, onUploaded }: DocumentUploadProps) {
  const { t } = useTranslation('documents');
  const { user } = useAuth();
  const [file, setFile] = useState<File | null>(null);
  const [documentType, setDocumentType] = useState<DocumentType>('INVOICE');
  const [uploading, setUploading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const fileInput = useRef<HTMLInputElement>(null);

  // T14 supplies editable draft state and its version; missing integration stays read-only.
  const canUpload = documentsEditable && (user?.role === 'ADMIN' || user?.role === 'PUR_STAFF') &&
    expectedVersion !== undefined && Number.isSafeInteger(expectedVersion) && expectedVersion >= 0;
  if (!canUpload) return null;

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!canUpload || uploading) return;
    if (!file) {
      setError(t('validation.fileRequired'));
      return;
    }
    const validationError = validateDocumentFile(file);
    if (validationError) {
      setError(t(validationError));
      return;
    }
    setUploading(true);
    setError(null);
    try {
      const response = await documentApi.upload({ file, transactionId, documentType, assetId, expectedVersion });
      if (!response.success || !response.data) throw new Error(response.message || t('errors.upload'));
      setFile(null);
      if (fileInput.current) fileInput.current.value = '';
      onUploaded(response.data);
    } catch (cause) {
      setError(cause instanceof ApiError && cause.status === 409
        ? t('errors.conflict')
        : cause instanceof Error ? cause.message : t('errors.upload'));
    } finally {
      setUploading(false);
    }
  }

  return (
    <form onSubmit={submit} className="document-upload" aria-busy={uploading}>
      {error && <div role="alert" className="alert-banner alert-danger">{error}</div>}
      <div className="document-upload-fields">
        <label className="form-label">
          {t('fields.documentType')}
          <select className="form-select" value={documentType} disabled={uploading}
            onChange={(event) => setDocumentType(event.target.value as DocumentType)}>
            {(['INVOICE', 'PURCHASE_ORDER', 'CONTRACT', 'OTHER'] as const).map((type) => (
              <option key={type} value={type}>{t(`documentTypes.${type}`)}</option>
            ))}
          </select>
        </label>
        <label className="form-label">
          {t('fields.file')}
          <input ref={fileInput} className="form-input" type="file" accept=".pdf,.png,.jpg,.jpeg" disabled={uploading}
            onChange={(event) => {
              const selectedFile = event.target.files?.[0] ?? null;
              setFile(selectedFile);
              const validationError = selectedFile ? validateDocumentFile(selectedFile) : null;
              setError(validationError ? t(validationError) : null);
            }} />
        </label>
      </div>
      <button className="btn btn-primary" type="submit" disabled={uploading || !file}>
        {t(uploading ? 'actions.uploading' : 'actions.upload')}
      </button>
      {uploading && <span role="status" className="document-inline-status">{t('states.uploading')}</span>}
    </form>
  );
}
