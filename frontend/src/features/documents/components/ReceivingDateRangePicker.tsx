import { useCallback, useEffect, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';

const iso = (date: Date) => `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;

export function ReceivingDateRangePicker({ from, to, onChange, label }: { from: string; to: string; onChange: (from: string, to: string) => void; label?: string }) {
  const { t, i18n } = useTranslation('documents');
  const [open, setOpen] = useState(false);
  const [draftFrom, setDraftFrom] = useState(from);
  const [draftTo, setDraftTo] = useState(to);
  const [awaitingEnd, setAwaitingEnd] = useState(false);
  const [month, setMonth] = useState(() => from ? new Date(`${from}T12:00:00`) : new Date());
  const root = useRef<HTMLDivElement>(null);
  const trigger = useRef<HTMLButtonElement>(null);
  const calendar = useRef<HTMLDivElement>(null);
  const close = useCallback(() => { setOpen(false); trigger.current?.focus(); }, []);

  useEffect(() => {
    if (!open || typeof document === 'undefined') return;
    calendar.current?.querySelector('button')?.focus();
    const onKeyDown = (event: KeyboardEvent) => { if (event.key === 'Escape') { event.preventDefault(); close(); } };
    const onPointerDown = (event: PointerEvent) => { if (!root.current?.contains(event.target as Node)) close(); };
    document.addEventListener('keydown', onKeyDown);
    document.addEventListener('pointerdown', onPointerDown);
    return () => { document.removeEventListener('keydown', onKeyDown); document.removeEventListener('pointerdown', onPointerDown); };
  }, [open, close]);

  const locale = i18n.language.startsWith('vi') ? 'vi-VN' : 'en-US';
  const format = (value: string) => new Intl.DateTimeFormat(locale, { day: '2-digit', month: '2-digit', year: 'numeric' }).format(new Date(`${value}T12:00:00`));
  const firstDay = new Date(month.getFullYear(), month.getMonth(), 1);
  const offset = (firstDay.getDay() + 6) % 7;
  const count = new Date(month.getFullYear(), month.getMonth() + 1, 0).getDate();
  const weekdays = Array.from({ length: 7 }, (_, index) => new Intl.DateTimeFormat(locale, { weekday: 'short' }).format(new Date(2026, 5, index + 1)));
  const select = (value: string) => {
    if (!awaitingEnd) { setDraftFrom(value); setDraftTo(value); setAwaitingEnd(true); }
    else { setDraftFrom(value < draftFrom ? value : draftFrom); setDraftTo(value < draftFrom ? draftFrom : value); setAwaitingEnd(false); }
  };
  return <div ref={root} className="receiving-date-range">
    <span className="receiving-date-label">{label ?? t('receiving.createdRange')}</span>
    <button ref={trigger} type="button" className="form-input receiving-date-trigger" aria-label={label ?? t('receiving.createdRange')} aria-expanded={open} onClick={() => {
      if (open) { close(); return; }
      setDraftFrom(from); setDraftTo(to); setAwaitingEnd(false);
      setMonth(from ? new Date(`${from}T12:00:00`) : new Date()); setOpen(true);
    }}>{from ? from === to || !to ? format(from) : `${format(from)} - ${format(to)}` : t('receiving.allDates')}</button>
    {open && <div ref={calendar} className="receiving-calendar" role="dialog" aria-label={label ?? t('receiving.createdRange')}>
      <div className="receiving-calendar-heading"><button type="button" aria-label={t('receiving.previousMonth')} onClick={() => setMonth(new Date(month.getFullYear(), month.getMonth() - 1, 1))}>‹</button><strong>{new Intl.DateTimeFormat(locale, { month: 'long', year: 'numeric' }).format(month)}</strong><button type="button" aria-label={t('receiving.nextMonth')} onClick={() => setMonth(new Date(month.getFullYear(), month.getMonth() + 1, 1))}>›</button></div>
      <div className="receiving-calendar-grid">{weekdays.map((day, index) => <span key={index} aria-hidden="true">{day}</span>)}{Array.from({ length: offset }, (_, index) => <span key={`blank-${index}`} />)}{Array.from({ length: count }, (_, index) => {
        const value = iso(new Date(month.getFullYear(), month.getMonth(), index + 1));
        return <button key={value} type="button" className={value === draftFrom || value === draftTo ? 'is-edge' : value > draftFrom && value < draftTo ? 'is-in-range' : ''} aria-label={format(value)} aria-pressed={value >= draftFrom && value <= draftTo} onClick={() => select(value)}>{index + 1}</button>;
      })}</div>
      <p className="receiving-calendar-selection">{draftFrom ? draftFrom === draftTo || !draftTo ? format(draftFrom) : `${format(draftFrom)} - ${format(draftTo)}` : t('receiving.allDates')}</p>
      <div className="receiving-calendar-actions"><button type="button" className="btn btn-secondary" onClick={() => { onChange('', ''); close(); }}>{t('receiving.clearRange')}</button><button type="button" className="btn btn-secondary" onClick={close}>{t('receiving.cancel')}</button><button type="button" className="btn btn-primary" disabled={!draftFrom} onClick={() => { onChange(draftFrom, draftTo || draftFrom); close(); }}>{t('receiving.applyRange')}</button></div>
    </div>}
  </div>;
}
