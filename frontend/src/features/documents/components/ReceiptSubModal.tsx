import CloseOutlinedIcon from '@mui/icons-material/CloseOutlined';
import { useEffect, useRef } from 'react';
import { useTranslation } from 'react-i18next';

export function ReceiptSubModal({ title, onClose, children, busy=false, size="default" }: { title: string; onClose: () => void; children: React.ReactNode; busy?: boolean; size?: "default" | "confirmation" }) {
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
      if (event.defaultPrevented) return;
      const dialogs = document.querySelectorAll('.receiving-submodal');
      if (dialogs[dialogs.length - 1] !== modal.current) return;
      if (document.querySelector('.receiving-import-dialog, .document-preview-backdrop, .modal-overlay, .receiving-reject, dialog[open]')) return;
      if (event.key === 'Escape') {
        event.preventDefault(); event.stopImmediatePropagation();
        if (!saving.current && !modal.current?.querySelector('[aria-busy="true"]')) close.current();
      }
      if (event.key !== 'Tab') return;
      const focusable = [...(modal.current?.querySelectorAll<HTMLElement>('button:not(:disabled), input:not(:disabled), select:not(:disabled), textarea:not(:disabled), a[href], summary') ?? [])].filter(element => element.getClientRects().length > 0);
      if (!focusable.length) { event.preventDefault(); return; }
      const first = focusable[0], last = focusable[focusable.length - 1];
      if (event.shiftKey && (document.activeElement === first || document.activeElement === modal.current)) { event.preventDefault(); last.focus(); }
      else if (!event.shiftKey && (document.activeElement === last || document.activeElement === modal.current)) { event.preventDefault(); first.focus(); }
    };
    document.addEventListener('keydown', onKeyDown);
    return () => { document.removeEventListener('keydown', onKeyDown); previous?.focus(); };
  }, []);
  return <div className="receiving-submodal-backdrop" onMouseDown={event => { if (event.target === event.currentTarget && !busy && !modal.current?.querySelector('[aria-busy="true"]')) onClose(); }}>
    <div ref={modal} className={size === "confirmation" ? "receiving-submodal receiving-submodal--confirmation" : "receiving-submodal"} role="dialog" aria-modal="true" aria-label={title} tabIndex={-1}>
      <div className="receiving-submodal-heading"><h3>{title}</h3><button type="button" className="btn btn-secondary" aria-label={t('actions.close')} disabled={busy} onClick={() => { if (!modal.current?.querySelector('[aria-busy="true"]')) onClose(); }}><CloseOutlinedIcon fontSize="small" aria-hidden="true" /></button></div>
      <div className="receiving-submodal-body">{children}</div>
    </div>
  </div>;
}
