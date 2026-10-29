import { useEffect, useRef } from 'react';
import { useTranslation } from 'react-i18next';

export function ReceiptSubModal({ title, onClose, children, busy=false }: { title: string; onClose: () => void; children: React.ReactNode; busy?: boolean }) {
  const { t } = useTranslation('documents');
  const modal = useRef<HTMLDivElement>(null);
  const close = useRef(onClose);
  const saving = useRef(busy);
  close.current = onClose;
  saving.current = busy;
  useEffect(() => {
    if (typeof document === 'undefined') return;
    const previous = document.activeElement as HTMLElement | null;
    modal.current?.focus();
    const onKeyDown = (event: KeyboardEvent) => {
      if (document.querySelector('.document-preview-backdrop, .modal-overlay, .receiving-reject, dialog[open]')) return;
      if (event.key === 'Escape' && !saving.current) close.current();
      if (event.key !== 'Tab') return;
      const focusable = [...(modal.current?.querySelectorAll<HTMLElement>('button:not(:disabled), input:not(:disabled), select:not(:disabled), textarea:not(:disabled), a[href]') ?? [])];
      if (!focusable.length) return;
      const first = focusable[0], last = focusable[focusable.length - 1];
      if (event.shiftKey && (document.activeElement === first || document.activeElement === modal.current)) { event.preventDefault(); last.focus(); }
      else if (!event.shiftKey && (document.activeElement === last || document.activeElement === modal.current)) { event.preventDefault(); first.focus(); }
    };
    document.addEventListener('keydown', onKeyDown);
    return () => { document.removeEventListener('keydown', onKeyDown); previous?.focus(); };
  }, []);
  return <div className="receiving-submodal-backdrop" onMouseDown={event => { if (event.target === event.currentTarget && !busy) onClose(); }}>
    <div ref={modal} className="receiving-submodal" role="dialog" aria-modal="true" aria-label={title} tabIndex={-1}>
      <div className="receiving-submodal-heading"><h3>{title}</h3><button type="button" className="btn btn-secondary" aria-label={t('actions.close')} disabled={busy} onClick={onClose}>×</button></div>
      {children}
    </div>
  </div>;
}
