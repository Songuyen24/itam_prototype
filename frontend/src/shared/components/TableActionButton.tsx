import type { ButtonHTMLAttributes, ReactNode } from 'react';

type Props = Omit<ButtonHTMLAttributes<HTMLButtonElement>, 'children' | 'title'> & {
  icon: ReactNode;
  label: string;
};

export function TableActionButton({ icon, label, className = '', ...props }: Props) {
  return <button type="button" {...props} className={`btn table-action-button ${className.includes('btn-danger') ? '' : 'btn-secondary'} ${className}`} title={label} aria-label={label}>{icon}</button>;
}
