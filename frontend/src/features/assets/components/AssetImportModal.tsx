import React, { useState, useRef } from 'react';
import { useTranslation } from 'react-i18next';
import { importApi } from '../api/importApi';
import { receivingApi } from '@/features/receiving/api/receivingApi';
import { ImportBatch, ImportPreviewData, ValidationStatus } from '../types/import.types';
import { importPayload } from './importPayload';
import { ApiError } from '@/shared/api/httpClient';

interface AssetImportModalProps {
  isOpen: boolean;
  onClose: () => void;
  onImportSuccess: (importedCount: number) => void;
  receiving?: { id: number; version: number };
}

export const AssetImportModal: React.FC<AssetImportModalProps> = ({
  isOpen,
  onClose,
  onImportSuccess,
  receiving,
}) => {
  const { t } = useTranslation(['imports', 'common']);
  const [step, setStep] = useState<'upload' | 'preview' | 'completed'>('upload');
  const [selectedFile, setSelectedFile] = useState<File | null>(null);
  const [isUploading, setIsUploading] = useState(false);
  const [isImporting, setIsImporting] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [previewData, setPreviewData] = useState<ImportPreviewData | null>(null);
  const [activeFilter, setActiveFilter] = useState<'ALL' | 'VALID' | 'ERROR'>('ALL');
  const [importedCount, setImportedCount] = useState<number>(0);
  const [result, setResult] = useState<ImportBatch | null>(null);
  const [retryBatchId, setRetryBatchId] = useState<number | undefined>();
  const [receivingVersion, setReceivingVersion] = useState(receiving?.version);
  const [requestId, setRequestId] = useState<string>();
  const [previewPage, setPreviewPage] = useState(0);
  const [resultPage, setResultPage] = useState(0);

  const fileInputRef = useRef<HTMLInputElement>(null);

  if (!isOpen) return null;

  const handleReset = () => {
    setStep('upload');
    setSelectedFile(null);
    setIsUploading(false);
    setIsImporting(false);
    setErrorMessage(null);
    setPreviewData(null);
    setActiveFilter('ALL');
    setImportedCount(0);
    setResult(null);
    setRetryBatchId(undefined);
    setReceivingVersion(receiving?.version);
    setRequestId(undefined);
    setPreviewPage(0);
    setResultPage(0);
  };

  const handleClose = () => {
    handleReset();
    onClose();
  };

  const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    if (e.target.files && e.target.files.length > 0) {
      const file = e.target.files[0];
      if (!file.name.endsWith('.xlsx') && !file.name.endsWith('.xls')) {
        setErrorMessage(t('imports:errors.invalidFormat', 'Vui lòng chọn file định dạng Excel (.xlsx hoặc .xls)'));
        return;
      }
      setSelectedFile(file);
      setRequestId(crypto.randomUUID());
      setPreviewPage(0);
      setPreviewData(null);
      setResult(null);
      setErrorMessage(null);
    }
  };

  const handleDownloadTemplate = async () => {
    try {
      await (receiving ? receivingApi.downloadTemplate() : importApi.downloadTemplate());
    } catch (err: any) {
      setErrorMessage(err.message || t('imports:errors.downloadFailed', 'Không thể tải file template mẫu'));
    }
  };

  const handleUploadAndPreview = async () => {
    if (!selectedFile) {
      setErrorMessage(t('imports:errors.fileRequired', 'Vui lòng chọn một file Excel trước'));
      return;
    }

    setIsUploading(true);
    setErrorMessage(null);

    try {
      const res = await (receiving ? receivingApi.previewExcel(selectedFile) : importApi.preview(selectedFile));
      if (res.success && res.data) {
        setPreviewData(res.data);
        setStep('preview');
      } else {
        setErrorMessage(res.message || t('imports:errors.previewFailed'));
      }
    } catch (err: any) {
      setErrorMessage(err.message || t('imports:errors.previewFailed'));
    } finally {
      setIsUploading(false);
    }
  };

  const handleConfirmImport = async () => {
    if (!previewData) return;

    if (previewData.validRows === 0) {
      setErrorMessage(t('imports:errors.noValidRows', 'Không có dòng hợp lệ nào để import'));
      return;
    }

    setIsImporting(true);
    setErrorMessage(null);

    try {
      const payload = {...importPayload(previewData, retryBatchId), requestId: requestId ?? crypto.randomUUID()};
      const res = receiving
        ? await receivingApi.confirmExcel(receiving.id, receivingVersion ?? receiving.version, payload)
        : await importApi.confirm(payload);

      if (res.success && res.data) {
        const batch = receiving ? 'batch' in res.data ? res.data.batch : null : res.data as ImportBatch;
        if (!batch) throw new Error(t('imports:errors.importFailed'));
        setImportedCount(batch.importedRows);
        setResult(batch);
        setResultPage(0);
        setRetryBatchId(batch.importBatchId);
        if (receiving && 'transaction' in res.data) setReceivingVersion(res.data.transaction.expectedVersion);
        setStep('completed');
        onImportSuccess(batch.importedRows);
      } else {
        setErrorMessage(res.message || t('imports:errors.importFailed'));
      }
    } catch (err: any) {
      setErrorMessage(err.message || t('imports:errors.importFailed'));
      if (err instanceof ApiError && err.code === 'NO_VALID_ROWS' && Array.isArray(err.rows) && previewData) {
        const rows = err.rows as ImportPreviewData['rows'];
        setPreviewData({...previewData,rows,validRows:0,invalidRows:rows.filter(row=>row.validationStatus==='INVALID').length,duplicateRows:rows.filter(row=>row.validationStatus==='DUPLICATE').length});
        setActiveFilter('ALL');
        setPreviewPage(0);
      }
    } finally {
      setIsImporting(false);
    }
  };

  const filteredRows = (previewData?.rows || []).filter((row) => {
    if (activeFilter === 'VALID') return row.validationStatus === 'VALID';
    if (activeFilter === 'ERROR') return row.validationStatus === 'INVALID' || row.validationStatus === 'DUPLICATE';
    return true;
  });

  const renderStatusBadge = (status: ValidationStatus) => {
    switch (status) {
      case 'VALID':
        return (
          <span style={{
            display: 'inline-flex',
            alignItems: 'center',
            gap: '4px',
            backgroundColor: '#dcfce7',
            color: '#15803d',
            padding: '2px 8px',
            borderRadius: '9999px',
            fontSize: '11px',
            fontWeight: 600,
          }}>
            ✓ {t('imports:statusBadges.VALID', 'Hợp lệ')}
          </span>
        );
      case 'DUPLICATE':
        return (
          <span style={{
            display: 'inline-flex',
            alignItems: 'center',
            gap: '4px',
            backgroundColor: '#fef3c7',
            color: '#b45309',
            padding: '2px 8px',
            borderRadius: '9999px',
            fontSize: '11px',
            fontWeight: 600,
          }}>
            ⚠️ {t('imports:statusBadges.DUPLICATE', 'Trùng lặp')}
          </span>
        );
      case 'INVALID':
      default:
        return (
          <span style={{
            display: 'inline-flex',
            alignItems: 'center',
            gap: '4px',
            backgroundColor: '#fee2e2',
            color: '#b91c1c',
            padding: '2px 8px',
            borderRadius: '9999px',
            fontSize: '11px',
            fontWeight: 600,
          }}>
            ✕ {t('imports:statusBadges.INVALID', 'Lỗi')}
          </span>
        );
    }
  };

  return (
    <div
      style={{
        position: 'fixed',
        top: 0,
        left: 0,
        right: 0,
        bottom: 0,
        backgroundColor: 'rgba(15, 23, 42, 0.65)',
        backdropFilter: 'blur(4px)',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        zIndex: 9999,
        padding: '20px',
      }}
    >
      <div
        className="card"
        style={{
          width: '100%',
          maxWidth: step === 'preview' ? '1100px' : '650px',
          maxHeight: '90vh',
          display: 'flex',
          flexDirection: 'column',
          backgroundColor: '#ffffff',
          borderRadius: '12px',
          boxShadow: '0 20px 25px -5px rgba(0, 0, 0, 0.1), 0 10px 10px -5px rgba(0, 0, 0, 0.04)',
          overflow: 'hidden',
          transition: 'max-width 0.2s ease',
        }}
      >
        {/* Modal Header */}
        <div
          style={{
            padding: '16px 24px',
            borderBottom: '1px solid #e2e8f0',
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'center',
            backgroundColor: '#f8fafc',
          }}
        >
          <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
            <span style={{ fontSize: '20px' }}>📥</span>
            <div>
              <h2 style={{ fontSize: '18px', fontWeight: 700, color: '#0f172a', margin: 0 }}>
                {receiving ? t('imports:receivingTitle') : t('imports:title')}
              </h2>
              <div style={{ fontSize: '12px', color: '#64748b' }}>
                {step === 'upload' && t('imports:steps.upload', '1. Chọn file Excel')}
                {step === 'preview' && t('imports:steps.preview', '2. Xem trước & Kiểm tra')}
                {step === 'completed' && t('imports:steps.completed', '3. Kết quả Import')}
              </div>
            </div>
          </div>

          <button
            type="button"
            onClick={handleClose}
            style={{
              background: 'none',
              border: 'none',
              fontSize: '20px',
              cursor: 'pointer',
              color: '#64748b',
              padding: '4px 8px',
              borderRadius: '4px',
            }}
          >
            ✕
          </button>
        </div>

        <p style={{ padding: '0 24px' }}>{t(receiving ? 'imports:receivingHint' : 'imports:openingInventory')}</p>
        {/* Error Alert if any */}
        {errorMessage && (
          <div
            style={{
              margin: '16px 24px 0 24px',
              padding: '12px 16px',
              backgroundColor: '#fef2f2',
              border: '1px solid #fecaca',
              borderRadius: '8px',
              color: '#b91c1c',
              fontSize: '13px',
              display: 'flex',
              alignItems: 'center',
              gap: '8px',
            }}
          >
            <span>⚠️</span>
            <span>{errorMessage}</span>
          </div>
        )}

        {/* Modal Body */}
        <div style={{ padding: '24px', overflowY: 'auto', flex: 1 }}>
          {/* STEP 1: UPLOAD */}
          {step === 'upload' && (
            <div style={{ display: 'flex', flexDirection: 'column', gap: '20px' }}>
              {/* Template download card */}
              <div
                style={{
                  display: 'flex',
                  justifyContent: 'space-between',
                  alignItems: 'center',
                  padding: '16px',
                  backgroundColor: '#f0fdf4',
                  border: '1px solid #bbf7d0',
                  borderRadius: '8px',
                }}
              >
                <div>
                  <div style={{ fontWeight: 600, color: '#166534', fontSize: '14px' }}>
                    {t('imports:upload.downloadTemplate', 'Tải file Excel mẫu chuẩn')}
                  </div>
                  <div style={{ fontSize: '12px', color: '#15803d', marginTop: '2px' }}>
                    {t('imports:upload.downloadHelp', 'File mẫu đã có sẵn cấu trúc cột và hướng dẫn định dạng')}
                  </div>
                </div>
                <button
                  type="button"
                  className="btn btn-secondary"
                  onClick={handleDownloadTemplate}
                  style={{
                    backgroundColor: '#ffffff',
                    borderColor: '#86efac',
                    color: '#166534',
                    display: 'flex',
                    alignItems: 'center',
                    gap: '6px',
                    fontWeight: 600,
                  }}
                >
                  <span>📄</span>
                  <span>{t('imports:upload.downloadTemplate', 'Tải file mẫu (.xlsx)')}</span>
                </button>
              </div>

              <label className="form-label">{t('imports:upload.retryBatchId')}
                <input className="form-input" type="number" min="1" step="1" value={retryBatchId ?? ''} onChange={e=>setRetryBatchId(e.target.value ? Number(e.target.value) : undefined)} placeholder={t('imports:upload.retryBatchPlaceholder')}/>
              </label>
              {retryBatchId && <p>{t('imports:upload.keepRowOrder')}</p>}
              {/* Upload Dropzone */}
              <div
                onClick={() => fileInputRef.current?.click()}
                style={{
                  border: '2px dashed #cbd5e1',
                  borderRadius: '12px',
                  padding: '36px 20px',
                  textAlign: 'center',
                  backgroundColor: selectedFile ? '#f8fafc' : '#fcfcfd',
                  cursor: 'pointer',
                  transition: 'all 0.2s',
                }}
                onDragOver={(e) => e.preventDefault()}
                onDrop={(e) => {
                  e.preventDefault();
                  if (e.dataTransfer.files && e.dataTransfer.files.length > 0) {
                    const f = e.dataTransfer.files[0];
                    if (f.name.endsWith('.xlsx') || f.name.endsWith('.xls')) {
                      setSelectedFile(f);
                      setRequestId(crypto.randomUUID());
                      setPreviewPage(0);
                      setPreviewData(null);
                      setErrorMessage(null);
                    } else {
                      setErrorMessage(t('imports:errors.invalidFormat', 'Chỉ hỗ trợ file Excel (.xlsx, .xls)'));
                    }
                  }
                }}
              >
                <input
                  type="file"
                  ref={fileInputRef}
                  onChange={handleFileChange}
                  accept=".xlsx, .xls"
                  style={{ display: 'none' }}
                />

                <div style={{ fontSize: '42px', marginBottom: '12px' }}>📊</div>

                {selectedFile ? (
                  <div>
                    <div style={{ fontWeight: 700, color: '#0f172a', fontSize: '15px' }}>
                      {selectedFile.name}
                    </div>
                    <div style={{ fontSize: '12px', color: '#64748b', marginTop: '4px' }}>
                      {(selectedFile.size / 1024).toFixed(1)} KB — {t('imports:preview.btnBack', 'Nhấp để chọn file khác')}
                    </div>
                  </div>
                ) : (
                  <div>
                    <div style={{ fontWeight: 600, color: '#334155', fontSize: '14px' }}>
                      {t('imports:upload.dragDrop', 'Kéo thả file Excel vào đây hoặc')} {t('imports:upload.browseFile', 'Chọn file từ máy tính')}
                    </div>
                    <div style={{ fontSize: '12px', color: '#94a3b8', marginTop: '4px' }}>
                      {t('imports:upload.supports', 'Định dạng hỗ trợ: .xlsx, .xls (Tối đa 10MB, tối đa 1,000 dòng)')}
                    </div>
                  </div>
                )}
              </div>

              {/* Guide notes */}
              <div style={{ fontSize: '12px', color: '#64748b', backgroundColor: '#f8fafc', padding: '12px', borderRadius: '6px' }}>
                <div style={{ fontWeight: 600, marginBottom: '4px', color: '#475569' }}>{t('imports:guide.title', 'Quy tắc nhập liệu / Import Rules:')}</div>
                <ul style={{ margin: 0, paddingLeft: '18px', display: 'flex', flexDirection: 'column', gap: '4px' }}>
                  <li>{t('imports:guide.required', 'Cột Tên tài sản và Loại tài sản là bắt buộc; Mã tài sản có thể để trống và sẽ được tự sinh khi xác nhận.')}</li>
                  <li>{t('imports:guide.unique', 'Mã tài sản và Số Serial phải duy nhất, không trùng trong file hoặc cơ sở dữ liệu.')}</li>
                  {!receiving && <li>{t('imports:guide.status')}</li>}
                  <li>{t('imports:guide.license')}</li>
                  <li>{t('imports:guide.preview', 'Preview không ghi vào database cho đến khi bạn xác nhận.')}</li>
                </ul>
              </div>
            </div>
          )}

          {/* STEP 2: PREVIEW */}
          {step === 'preview' && previewData && (
            <div style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
              {/* Metric Summary Cards */}
              <div
                style={{
                  display: 'grid',
                  gridTemplateColumns: 'repeat(4, 1fr)',
                  gap: '12px',
                }}
              >
                <div
                  style={{
                    padding: '12px 16px',
                    borderRadius: '8px',
                    borderLeft: '4px solid #3b82f6',
                    backgroundColor: '#f8fafc',
                  }}
                >
                  <div style={{ fontSize: '11px', fontWeight: 600, color: '#64748b' }}>{t('imports:preview.totalRows', 'TỔNG SỐ DÒNG')}</div>
                  <div style={{ fontSize: '22px', fontWeight: 700, color: '#0f172a' }}>
                    {previewData.totalRows}
                  </div>
                </div>

                <div
                  style={{
                    padding: '12px 16px',
                    borderRadius: '8px',
                    borderLeft: '4px solid #10b981',
                    backgroundColor: '#f0fdf4',
                  }}
                >
                  <div style={{ fontSize: '11px', fontWeight: 600, color: '#166534' }}>{t('imports:preview.validRows', 'HỢP LỆ (SẴN SÀNG)')}</div>
                  <div style={{ fontSize: '22px', fontWeight: 700, color: '#16a34a' }}>
                    {previewData.validRows}
                  </div>
                </div>

                <div
                  style={{
                    padding: '12px 16px',
                    borderRadius: '8px',
                    borderLeft: '4px solid #f59e0b',
                    backgroundColor: '#fffbeb',
                  }}
                >
                  <div style={{ fontSize: '11px', fontWeight: 600, color: '#92400e' }}>{t('imports:preview.duplicateRows', 'TRÙNG LẶP')}</div>
                  <div style={{ fontSize: '22px', fontWeight: 700, color: '#d97706' }}>
                    {previewData.duplicateRows}
                  </div>
                </div>

                <div
                  style={{
                    padding: '12px 16px',
                    borderRadius: '8px',
                    borderLeft: '4px solid #ef4444',
                    backgroundColor: '#fef2f2',
                  }}
                >
                  <div style={{ fontSize: '11px', fontWeight: 600, color: '#991b1b' }}>{t('imports:preview.invalidRows', 'DÒNG LỖI')}</div>
                  <div style={{ fontSize: '22px', fontWeight: 700, color: '#dc2626' }}>
                    {previewData.invalidRows}
                  </div>
                </div>
              </div>

              {/* Filter Tabs */}
              <div style={{ display: 'flex', gap: '8px', borderBottom: '1px solid #e2e8f0', paddingBottom: '8px' }}>
                <button
                  type="button"
                  onClick={() => {setActiveFilter('ALL');setPreviewPage(0);}}
                  style={{
                    padding: '6px 12px',
                    borderRadius: '6px',
                    border: 'none',
                    fontSize: '12px',
                    fontWeight: 600,
                    cursor: 'pointer',
                    backgroundColor: activeFilter === 'ALL' ? '#0f172a' : '#f1f5f9',
                    color: activeFilter === 'ALL' ? '#ffffff' : '#475569',
                  }}
                >
                  {t('imports:preview.filterAll', 'Tất cả')} ({previewData.totalRows})
                </button>
                <button
                  type="button"
                  onClick={() => {setActiveFilter('VALID');setPreviewPage(0);}}
                  style={{
                    padding: '6px 12px',
                    borderRadius: '6px',
                    border: 'none',
                    fontSize: '12px',
                    fontWeight: 600,
                    cursor: 'pointer',
                    backgroundColor: activeFilter === 'VALID' ? '#16a34a' : '#f1f5f9',
                    color: activeFilter === 'VALID' ? '#ffffff' : '#475569',
                  }}
                >
                  {t('imports:preview.filterValid', 'Chỉ dòng hợp lệ')} ({previewData.validRows})
                </button>
                <button
                  type="button"
                  onClick={() => {setActiveFilter('ERROR');setPreviewPage(0);}}
                  style={{
                    padding: '6px 12px',
                    borderRadius: '6px',
                    border: 'none',
                    fontSize: '12px',
                    fontWeight: 600,
                    cursor: 'pointer',
                    backgroundColor: activeFilter === 'ERROR' ? '#dc2626' : '#f1f5f9',
                    color: activeFilter === 'ERROR' ? '#ffffff' : '#475569',
                  }}
                >
                  {t('imports:preview.filterError', 'Chỉ dòng lỗi & trùng')} ({previewData.invalidRows + previewData.duplicateRows})
                </button>
              </div>

              {/* Warning notice when invalid rows exist */}
              {(previewData.invalidRows > 0 || previewData.duplicateRows > 0) && (
                <div
                  style={{
                    padding: '10px 14px',
                    backgroundColor: '#fffbeb',
                    border: '1px solid #fde68a',
                    borderRadius: '6px',
                    fontSize: '12px',
                    color: '#92400e',
                    display: 'flex',
                    alignItems: 'center',
                    gap: '8px',
                  }}
                >
                  <span>ℹ️</span>
                  <span>
                    {t('imports:preview.warningSkip', 'Lưu ý: Chỉ các dòng hợp lệ mới được import vào cơ sở dữ liệu. Các dòng lỗi sẽ bị bỏ qua.')}
                  </span>
                </div>
              )}

              {/* Preview Table */}
              <div
                style={{
                  maxHeight: '380px',
                  overflowY: 'auto',
                  border: '1px solid #e2e8f0',
                  borderRadius: '8px',
                }}
              >
                <table className="data-table" style={{ width: '100%', fontSize: '12px' }}>
                  <thead style={{ position: 'sticky', top: 0, backgroundColor: '#f8fafc', zIndex: 1 }}>
                    <tr>
                      <th style={{ width: '60px', textAlign: 'center' }}>{t('imports:preview.colRow', 'Dòng')}</th>
                      <th style={{ width: '100px', textAlign: 'center' }}>{t('imports:preview.colValidation', 'Trạng thái')}</th>
                      <th style={{ width: '140px' }}>{t('imports:preview.colTag', 'Mã tài sản')}</th>
                      <th>{t('imports:preview.colName', 'Tên tài sản')}</th>
                      <th style={{ width: '100px' }}>{t('imports:preview.colType', 'Loại')}</th>
                      <th style={{ width: '120px' }}>{t('imports:preview.colSerial', 'Serial')}</th>
                      <th style={{ width: '220px' }}>{t('imports:preview.colError', 'Chi tiết lỗi / Ghi chú')}</th>
                    </tr>
                  </thead>
                  <tbody>
                    {filteredRows.length === 0 ? (
                      <tr>
                        <td colSpan={7} style={{ textAlign: 'center', padding: '24px', color: '#94a3b8' }}>
                          {t('common:labels.noData', 'Không có dữ liệu')}
                        </td>
                      </tr>
                    ) : (
                      filteredRows.slice(previewPage*20,(previewPage+1)*20).map((row) => {
                        const isError = row.validationStatus !== 'VALID';
                        return (
                          <tr
                            key={row.rowNumber}
                            style={{
                              backgroundColor: isError ? (row.validationStatus === 'DUPLICATE' ? '#fffbeb' : '#fef2f2') : 'inherit',
                            }}
                          >
                            <td style={{ textAlign: 'center', fontWeight: 600 }}>{row.rowNumber}</td>
                            <td style={{ textAlign: 'center' }}>{renderStatusBadge(row.validationStatus)}</td>
                            <td style={{ fontWeight: 600, fontFamily: 'monospace' }}>
                              {row.rawData?.assetTag || <span style={{ color: '#64748b' }}>{t('imports:preview.generatedTag')}</span>}
                            </td>
                            <td>{row.rawData?.name || <span style={{ color: '#ef4444' }}>{t('imports:preview.missing')}</span>}</td>
                            <td>{row.rawData?.type || '—'}</td>
                            <td style={{ fontFamily: 'monospace' }}>{row.rawData?.serialNumber || '—'}</td>
                            <td>
                              {row.errorMessage ? (
                                <div style={{ color: '#dc2626', fontSize: '11px', display: 'flex', flexDirection: 'column', gap: '2px' }}>
                                  {row.errors?.length ? row.errors.map((err, i) => (
                                    <div key={i}>• [{err.column}]: {err.message}</div>
                                  )) : row.errorMessage}
                                </div>
                              ) : (
                                <span style={{ color: '#16a34a' }}>✓ {t('imports:statusBadges.VALID', 'Sẵn sàng lưu kho')}</span>
                              )}
                            </td>
                          </tr>
                        );
                      })
                    )}
                  </tbody>
                </table>
              </div>
              {filteredRows.length>20 && <div className="receiving-actions" style={{justifyContent:'flex-end'}}>
                <button type="button" className="btn btn-secondary" disabled={previewPage===0} onClick={()=>setPreviewPage(v=>v-1)}>{t('imports:preview.previous')}</button>
                <span>{t('imports:preview.page',{page:previewPage+1,pages:Math.ceil(filteredRows.length/20)})}</span>
                <button type="button" className="btn btn-secondary" disabled={(previewPage+1)*20>=filteredRows.length} onClick={()=>setPreviewPage(v=>v+1)}>{t('imports:preview.next')}</button>
              </div>}
            </div>
          )}

          {/* STEP 3: COMPLETED */}
          {step === 'completed' && (
            <div style={{ textAlign: 'center', padding: '32px 16px' }}>
              <div style={{ fontSize: '56px', marginBottom: '16px' }}>🎉</div>
              <h3 style={{ fontSize: '20px', fontWeight: 700, color: '#0f172a', marginBottom: '8px' }}>
                {t('imports:completed.resultTitle')}
              </h3>
              <p style={{ fontSize: '14px', color: '#475569', maxWidth: '420px', margin: '0 auto 24px auto' }}>
                {t(receiving ? 'imports:completed.receivingMessage' : 'imports:completed.successMessage', { count: importedCount })}
              </p>
              {result && <p>{t('imports:completed.batchId',{id:result.importBatchId})}</p>}
              {result?.rows && <div style={{maxHeight:'35vh',overflow:'auto',textAlign:'left',marginBottom:16}}>
                <table className="data-table" style={{width:'100%'}}><thead><tr>
                  <th>{t('imports:preview.colRow')}</th><th>{t('imports:preview.colValidation')}</th><th>{t('imports:preview.colTag')}</th><th>{t('imports:preview.colError')}</th>
                </tr></thead><tbody>{result.rows.slice(resultPage*20,(resultPage+1)*20).map(row=><tr key={row.rowNumber}>
                  <td>{row.rowNumber}</td><td>{t(`imports:statusBadges.${row.validationStatus}`,row.validationStatus)}</td>
                  <td>{row.rawData?.assetTag || '—'}</td>
                  <td>{row.errors?.map(error=>error.message).join('; ') || row.errorMessage || '—'}</td>
                </tr>)}</tbody></table>
              </div>}
              {result?.rows && result.rows.length>20 && <div className="receiving-actions" style={{justifyContent:'center',marginBottom:12}}>
                <button type="button" className="btn btn-secondary" disabled={resultPage===0} onClick={()=>setResultPage(v=>v-1)}>{t('imports:preview.previous')}</button>
                <span>{t('imports:preview.page',{page:resultPage+1,pages:Math.ceil(result.rows.length/20)})}</span>
                <button type="button" className="btn btn-secondary" disabled={(resultPage+1)*20>=result.rows.length} onClick={()=>setResultPage(v=>v+1)}>{t('imports:preview.next')}</button>
              </div>}
              {result && result.importedRows < result.totalRows && <button type="button" className="btn btn-secondary" onClick={()=>{setStep('upload');setPreviewData(null);setSelectedFile(null);}} style={{marginRight:8}}>{t('imports:completed.retryFile')}</button>}
              <button
                type="button"
                className="btn btn-primary"
                onClick={handleClose}
                style={{ padding: '8px 24px', fontSize: '14px' }}
              >
                {t('imports:completed.btnFinish', 'Đóng & Xem danh sách tài sản')}
              </button>
            </div>
          )}
        </div>

        {/* Modal Footer */}
        {step !== 'completed' && (
          <div
            style={{
              padding: '16px 24px',
              borderTop: '1px solid #e2e8f0',
              display: 'flex',
              justifyContent: 'space-between',
              alignItems: 'center',
              backgroundColor: '#f8fafc',
            }}
          >
            {step === 'upload' ? (
              <>
                <button
                  type="button"
                  className="btn btn-secondary"
                  onClick={handleClose}
                  disabled={isUploading}
                >
                  {t('common:buttons.cancel', 'Hủy bỏ')}
                </button>

                <button
                  type="button"
                  className="btn btn-primary"
                  onClick={handleUploadAndPreview}
                  disabled={!selectedFile || isUploading}
                  style={{ display: 'flex', alignItems: 'center', gap: '6px' }}
                >
                  {isUploading ? (
                    <>
                      <span>⏳</span>
                      <span>{t('imports:upload.analyzing', 'Đang kiểm tra file...')}</span>
                    </>
                  ) : (
                    <>
                      <span>🔍</span>
                      <span>{t('imports:upload.btnPreview', 'Kiểm tra dữ liệu (Preview)')}</span>
                    </>
                  )}
                </button>
              </>
            ) : (
              <>
                <button
                  type="button"
                  className="btn btn-secondary"
                  onClick={() => setStep('upload')}
                  disabled={isImporting}
                >
                  {t('imports:preview.btnBack', '← Chọn lại file khác')}
                </button>

                <div style={{ display: 'flex', gap: '10px' }}>
                  <button
                    type="button"
                    className="btn btn-secondary"
                    onClick={handleClose}
                    disabled={isImporting}
                  >
                    {t('common:buttons.close', 'Đóng')}
                  </button>

                  <button
                    type="button"
                    className="btn btn-primary"
                    onClick={handleConfirmImport}
                    disabled={!previewData || previewData.validRows === 0 || isImporting}
                    style={{
                      display: 'flex',
                      alignItems: 'center',
                      gap: '6px',
                      backgroundColor: previewData?.validRows && previewData.validRows > 0 ? '#16a34a' : undefined,
                      borderColor: previewData?.validRows && previewData.validRows > 0 ? '#16a34a' : undefined,
                    }}
                  >
                    {isImporting ? (
                      <>
                        <span>⏳</span>
                        <span>{t('imports:preview.importing', 'Đang nhập dữ liệu...')}</span>
                      </>
                    ) : (
                      <>
                        <span>✓</span>
                        <span>
                          {t('imports:preview.btnConfirm', { count: previewData?.validRows || 0, defaultValue: `Xác nhận Import (${previewData?.validRows || 0} dòng)` })}
                        </span>
                      </>
                    )}
                  </button>
                </div>
              </>
            )}
          </div>
        )}
      </div>
    </div>
  );
};
