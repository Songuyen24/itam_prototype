import { useEffect, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import type { PDFDocumentProxy, RenderTask } from 'pdfjs-dist';
import workerUrl from 'pdfjs-dist/build/pdf.worker.min.mjs?url';

export function PdfCanvasPreview({ blob }: { blob: Blob }) {
  const { t } = useTranslation('documents');
  const canvas = useRef<HTMLCanvasElement>(null);
  const [pdf, setPdf] = useState<PDFDocumentProxy | null>(null);
  const [page, setPage] = useState(1);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    let active = true;
    setPdf(null); setPage(1); setError(''); setLoading(true);
    let document: PDFDocumentProxy | null = null;
    blob.arrayBuffer().then(async data => {
      const pdfjs = await import('pdfjs-dist');
      pdfjs.GlobalWorkerOptions.workerSrc = workerUrl;
      return pdfjs.getDocument({ data, isEvalSupported: false }).promise;
    }).then(value => {
      document = value;
      if (active) setPdf(value);
      else void value.destroy();
    }).catch(() => { if (active) { setError(t('errors.previewPdf')); setLoading(false); } });
    return () => { active = false; if (document) void document.destroy(); };
  }, [blob, t]);

  useEffect(() => {
    if (!pdf || !canvas.current) return;
    let active = true;
    let task: RenderTask | null = null;
    setLoading(true); setError('');
    pdf.getPage(page).then(pdfPage => {
      if (!active || !canvas.current) return;
      const base = pdfPage.getViewport({ scale: 1 });
      const scale = Math.min(1.5, 780 / base.width, 1600 / base.height);
      const viewport = pdfPage.getViewport({ scale });
      const target = canvas.current;
      target.width = Math.ceil(viewport.width);
      target.height = Math.ceil(viewport.height);
      const context = target.getContext('2d');
      if (!context) throw new Error('Canvas unavailable');
      task = pdfPage.render({ canvasContext: context, viewport });
      return task.promise;
    }).then(() => { if (active) setLoading(false); })
      .catch(() => { if (active) { setError(t('errors.previewPdf')); setLoading(false); } });
    return () => { active = false; task?.cancel(); };
  }, [pdf, page, t]);

  return <div className="pdf-canvas-preview">
    {error && <p role="alert">{error}</p>}
    {loading && <p role="status">{t('states.loading')}</p>}
    <canvas ref={canvas} role="img" aria-label={t('pdfPage', { page })} />
    {pdf && <div className="document-pagination"><button type="button" className="btn btn-secondary" aria-label={t('pagination.previous')} disabled={loading || page === 1} onClick={() => setPage(value => value - 1)}>←</button><span>{page} / {pdf.numPages}</span><button type="button" className="btn btn-secondary" aria-label={t('pagination.next')} disabled={loading || page >= pdf.numPages} onClick={() => setPage(value => value + 1)}>→</button></div>}
  </div>;
}
