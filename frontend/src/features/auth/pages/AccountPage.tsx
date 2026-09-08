import { useTranslation } from 'react-i18next';
import { useAuth } from '../contexts/AuthContext';

export function AccountPage() {
  const { user } = useAuth();
  const { t } = useTranslation('auth');

  if (!user) return null;

  return (
    <section>
      <div className="page-header">
        <h1 className="page-title">{t('account.title')}</h1>
      </div>
      <dl style={{ display: 'grid', gridTemplateColumns: 'minmax(100px, 160px) minmax(0, 1fr)', gap: '16px', maxWidth: '640px', overflowWrap: 'anywhere' }}>
        <dt>{t('account.fullName')}</dt>
        <dd style={{ margin: 0 }}>{user.fullName}</dd>
        <dt>{t('email')}</dt>
        <dd style={{ margin: 0 }}>{user.email}</dd>
        <dt>{t('account.role')}</dt>
        <dd style={{ margin: 0 }}>{t(`roles.${user.role}`, user.role)}</dd>
        {user.departmentName && <>
          <dt>{t('account.department')}</dt>
          <dd style={{ margin: 0 }}>{user.departmentName}</dd>
        </>}
      </dl>
    </section>
  );
}
