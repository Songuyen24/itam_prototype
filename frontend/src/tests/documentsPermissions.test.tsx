import { beforeEach, describe, expect, it, vi } from 'vitest';
import { renderToStaticMarkup } from 'react-dom/server';
import { StaticRouter } from 'react-router-dom/server';
import { AppRoutes } from '@/app/router';
import { AuthUser } from '@/features/auth/types/auth.types';
import { DocumentUpload, MAX_DOCUMENT_SIZE, validateDocumentFile } from '@/features/documents/components/DocumentUpload';
import { TransactionSelector } from '@/features/documents/components/TransactionSelector';
import { TransactionDocuments } from '@/features/documents/components/TransactionDocuments';

const session = vi.hoisted(() => ({ user: null as AuthUser | null, token: null as string | null, isAuthenticated: false, loading: false }));
vi.mock('@/features/auth/contexts/AuthContext', () => ({ useAuth: () => session }));
vi.mock('react-i18next', () => ({ useTranslation: () => ({ t: (key: string) => key, i18n: { language: 'en', changeLanguage: vi.fn() } }) }));
vi.mock('react-router-dom', async (importOriginal) => ({
  ...await importOriginal<typeof import('react-router-dom')>(),
  Navigate: ({ to }: { to: string }) => <span data-redirect={to} />,
}));
vi.mock('@/features/documents/pages/DocumentsPage', () => ({ DocumentsPage: () => <div data-page="documents" /> }));

function signIn(role: string) {
  session.user = { id: 1, role, email: 'current@itam.example', fullName: 'Current Account' };
  session.token = `token-${role}`;
  session.isAuthenticated = true;
}

function renderRoute() {
  return renderToStaticMarkup(<StaticRouter location="/documents"><AppRoutes /></StaticRouter>);
}

describe('Document permissions and state', () => {
  beforeEach(() => {
    session.user = null;
    session.token = null;
    session.isAuthenticated = false;
    session.loading = false;
  });

  it('requires authentication for direct document navigation', () => {
    expect(renderRoute()).toContain('data-redirect="/login"');
    expect(renderRoute()).not.toContain('data-page="documents"');
  });

  it.each(['ADMIN', 'IT_STAFF', 'PUR_STAFF'])('%s can browse documents', (role) => {
    signIn(role);
    const html = renderRoute();
    expect(html).toContain('data-page="documents"');
    expect(html).toContain('href="/documents"');
    if (role === 'PUR_STAFF') {
      expect(html).not.toContain('href="/assets"');
      expect(html).not.toContain('href="/catalogs"');
      expect(html).not.toContain('href="/users"');
    }
  });

  it.each(['USER', 'UNKNOWN'])('%s cannot open the document route or menu', (role) => {
    signIn(role);
    const html = renderRoute();
    expect(html).not.toContain('data-page="documents"');
    expect(html).not.toContain('href="/documents"');
    expect(html).toContain('data-redirect=');
  });

  it.each(['ADMIN', 'IT_STAFF', 'PUR_STAFF', 'USER'])('hides upload for read-only %s transactions', (role) => {
    signIn(role);
    expect(renderToStaticMarkup(<DocumentUpload transactionId={42} documentsEditable={false} expectedVersion={0} onUploaded={vi.fn()} />)).toBe('');
  });

  it.each(['ADMIN', 'PUR_STAFF'])('%s needs a valid version before upload can be shown', (role) => {
    signIn(role);
    for (const version of [undefined, -1, 1.5, Number.NaN]) {
      expect(renderToStaticMarkup(<DocumentUpload transactionId={42} documentsEditable expectedVersion={version} onUploaded={vi.fn()} />)).toBe('');
    }
    expect(renderToStaticMarkup(<DocumentUpload transactionId={42} documentsEditable expectedVersion={0} onUploaded={vi.fn()} />)).toContain('type="file"');
  });

  it.each(['IT_STAFF', 'USER', 'UNKNOWN'])('%s cannot upload even with an editable server flag', (role) => {
    signIn(role);
    expect(renderToStaticMarkup(<DocumentUpload transactionId={42} documentsEditable expectedVersion={0} onUploaded={vi.fn()} />)).toBe('');
  });

  it('renders transaction loading and an unselected document state', () => {
    signIn('PUR_STAFF');
    const list = renderToStaticMarkup(<TransactionSelector selectedId={null} onSelect={vi.fn()} />);
    expect(list).toContain('role="status"');
    expect(list).not.toContain('value="DISPOSAL"');
    expect(renderToStaticMarkup(<TransactionDocuments transactionId={null} />)).toContain('states.selectTransaction');
  });
});

describe('Document file validation', () => {
  it.each([['sample.PDF', 'application/pdf'], ['sample.png', 'image/png'], ['sample.jpg', 'image/jpeg'], ['sample.jpeg', 'image/jpeg']])('accepts matching %s files through the exact size limit', (name, type) => {
    expect(validateDocumentFile({ name, type, size: MAX_DOCUMENT_SIZE })).toBeNull();
  });

  it.each([['sample.pdf', 'image/png'], ['sample.png', 'application/pdf'], ['sample.html', 'text/html'], ['sample', 'application/pdf'], ['sample.pdf', '']])('rejects mismatched or unsupported %s with MIME %s', (name, type) => {
    expect(validateDocumentFile({ name, type, size: 100 })).toBe('validation.fileType');
  });

  it('rejects empty and oversized files', () => {
    expect(validateDocumentFile({ name: 'sample.pdf', type: 'application/pdf', size: 0 })).toBe('validation.emptyFile');
    expect(validateDocumentFile({ name: 'sample.pdf', type: 'application/pdf', size: MAX_DOCUMENT_SIZE + 1 })).toBe('validation.fileTooLarge');
  });
});
