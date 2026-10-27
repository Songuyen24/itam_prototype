import { act, create, type ReactTestRenderer } from 'react-test-renderer';
import { beforeEach, describe, expect, it, vi } from 'vitest';

const mocks = vi.hoisted(() => ({
  user: { id: 1, role: 'ADMIN' },
  profiles: vi.fn(), createProfile: vi.fn(), updateProfile: vi.fn(), setProfileStatus: vi.fn(),
  createAccount: vi.fn(), updateAccount: vi.fn(), resetPassword: vi.fn(), holdings: vi.fn(),
  departments: vi.fn(), http: vi.fn(),
}));
vi.mock('react-i18next', () => { const t = (key: string) => key; return { useTranslation: () => ({ t }) }; });
vi.mock('react-router-dom', () => { const params = new URLSearchParams(); return { useSearchParams: () => [params] }; });
vi.mock('@/features/auth/contexts/AuthContext', () => ({ useAuth: () => ({ user: mocks.user }) }));
vi.mock('@/features/auth/api/managementApi', () => ({ managementApi: {
  profiles: mocks.profiles, createProfile: mocks.createProfile, updateProfile: mocks.updateProfile,
  setProfileStatus: mocks.setProfileStatus, createAccount: mocks.createAccount,
  updateAccount: mocks.updateAccount, resetPassword: mocks.resetPassword, holdings: mocks.holdings,
} }));
vi.mock('@/features/catalogs/api/catalogApi', () => ({ departmentApi: { getAll: mocks.departments } }));
vi.mock('@/shared/api/httpClient', () => ({ httpClient: mocks.http }));

import { AccountsPage } from '@/features/auth/pages/AccountsPage';
import type { EmployeeProfile } from '@/features/auth/api/managementApi';

const profile: EmployeeProfile = { id: 8, fullName: 'Lan', employeeCode: 'E-8', email: null, departmentId: null,
  departmentName: null, accountStatus: 'ACTIVE', roleCode: 'USER', hasAccount: false,
  accessGranted: false, loginLocked: false, deviceCount: 0, licenseSeats: 0 };
const page = (content = [profile]) => ({ data: { content, totalElements: content.length, totalPages: 1 } });
let tree: ReactTestRenderer;
async function render() { await act(async () => { tree = create(<AccountsPage />); }); }
function button(label: string) { return tree.root.findAllByType('button').find(node => node.children.includes(label))!; }
function forms() { return tree.root.findAllByType('form'); }

describe('unified account management', () => {
  beforeEach(() => {
    for (const value of Object.values(mocks)) if (typeof value === 'function' && 'mockReset' in value) value.mockReset();
    mocks.user = { id: 1, role: 'ADMIN' };
    mocks.profiles.mockResolvedValue(page());
    mocks.departments.mockResolvedValue({ data: { content: [], totalPages: 1 } });
    mocks.http.mockResolvedValue({ data: { ASSETS: { view: false, export: false }, TRANSACTIONS: { view: false, export: false }, ALLOCATIONS: { view: false, export: false } } });
    vi.stubGlobal('window', { confirm: () => true });
  });

  it('creates a disabled USER with the default password in one request', async () => {
    mocks.createProfile.mockResolvedValue({ data: { ...profile, hasAccount: true } });
    await render();
    expect(JSON.stringify(tree.toJSON())).toContain('E-8');
    expect(JSON.stringify(tree.toJSON())).toContain('access.noAccount');
    await act(async () => button('management.addProfile').props.onClick());
    expect(forms()).toHaveLength(1);
    const inputs = tree.root.findAllByType('input');
    await act(async () => { inputs[1].props.onChange({ target: { value: 'Lan' } }); });
    await act(async () => { inputs[2].props.onChange({ target: { value: 'E-8' } }); });
    await act(async () => forms()[0].props.onSubmit({ preventDefault() {} }));
    expect(mocks.createProfile).toHaveBeenCalledWith(expect.objectContaining({ fullName: 'Lan', employeeCode: 'E-8', password: undefined, accessGranted: false }));
    expect(mocks.createAccount).not.toHaveBeenCalled();
    expect(forms()).toHaveLength(3);
    expect(tree.root.findAllByType('select').some(select => select.props.value === 'USER')).toBe(true);
    expect(tree.root.findAllByType('input').some(input => input.props.type === 'checkbox' && input.props.checked === false)).toBe(true);
    act(() => tree.unmount());
  });

  it('sends a custom password and Admin login grant, then clears the secret', async () => {
    mocks.createProfile.mockResolvedValue({ data: { ...profile, hasAccount: true, accessGranted: true } });
    await render();
    await act(async () => button('management.addProfile').props.onClick());
    const secret=tree.root.findAllByType('input').find(input => input.props.type === 'password')!;
    await act(async () => secret.props.onChange({ target: { value: 'Custom@1234' } }));
    const grant=tree.root.findAllByType('input').find(input => input.props.type === 'checkbox')!;
    await act(async () => grant.props.onChange({ target: { checked: true } }));
    await act(async () => forms()[0].props.onSubmit({ preventDefault() {} }));
    expect(mocks.createProfile).toHaveBeenCalledWith(expect.objectContaining({ password: 'Custom@1234', accessGranted: true }));
    expect(tree.root.findAllByType('input').filter(input => input.props.type === 'password').every(input => input.props.value === '')).toBe(true);
    act(() => tree.unmount());
  });

  it('limits IT to profile edits and PUR to read-only holdings', async () => {
    mocks.user = { id: 2, role: 'IT_STAFF' };
    await render();
    await act(async () => button('management.edit').props.onClick());
    expect(forms()).toHaveLength(1);
    expect(JSON.stringify(tree.toJSON())).not.toContain('management.createLogin');
    act(() => tree.unmount());

    mocks.user = { id: 3, role: 'PUR_STAFF' };
    await render();
    expect(JSON.stringify(tree.toJSON())).not.toContain('management.addProfile');
    expect(JSON.stringify(tree.toJSON())).not.toContain('management.importAccounts');
    expect(JSON.stringify(tree.toJSON())).not.toContain('management.edit');
    act(() => tree.unmount());
  });

  it('shows report checkboxes only for existing IT/PUR accounts', async () => {
    mocks.profiles.mockResolvedValue(page([{ ...profile, roleCode: 'IT_STAFF', hasAccount: true }]));
    await render();
    await act(async () => button('management.edit').props.onClick());
    expect(mocks.http).toHaveBeenCalledWith('/v1/reports/permissions/8');
    expect(JSON.stringify(tree.toJSON())).toContain('management.reportPermissions');
    expect(tree.root.findAllByType('input').filter(input => input.props.type === 'checkbox')).toHaveLength(7);
    const checks = tree.root.findAllByType('input').filter(input => input.props.type === 'checkbox');
    await act(async () => checks[1].props.onChange({ target: { checked: true } }));
    expect(mocks.http).toHaveBeenCalledWith('/v1/reports/permissions/8/ASSETS', { method: 'PUT', body: JSON.stringify({ view: true, export: false }) });
    expect(JSON.stringify(tree.toJSON())).toContain('management.reportSaved');
    act(() => tree.unmount());
  });

  it('creates disabled USER credentials and retains profile fields after account response', async () => {
    mocks.createAccount.mockResolvedValue({ data: { id: 8, fullName: 'Lan', employeeCode: 'E-8', email: null,
      roleCode: 'USER', accessGranted: false, loginLocked: false } });
    await render();
    await act(async () => button('management.edit').props.onClick());
    const secrets = tree.root.findAllByType('input').filter(input => input.props.type === 'password');
    await act(async () => secrets[0].props.onChange({ target: { value: 'password123' } }));
    await act(async () => secrets[1].props.onChange({ target: { value: 'password123' } }));
    await act(async () => forms()[1].props.onSubmit({ preventDefault() {} }));
    expect(mocks.createAccount).toHaveBeenCalledWith({ userId: 8, password: 'password123', roleCode: 'USER', accessGranted: false });
    expect(JSON.stringify(tree.toJSON())).toContain('management.resetPassword');
    expect(tree.root.findAllByType('input').filter(input => input.props.type === 'password').every(input => input.props.value === '')).toBe(true);
    expect(forms()[0].findAllByType('input').find(input => input.props.maxLength === 100)?.props.value).toBe('E-8');
    act(() => tree.unmount());
  });

  it('updates account role and lock, and resets password from the edit modal', async () => {
    const account = { ...profile, hasAccount: true, roleCode: 'PUR_STAFF' };
    mocks.profiles.mockResolvedValue(page([account]));
    mocks.updateAccount.mockResolvedValue({ data: { id: 8, fullName: 'Lan', employeeCode: 'E-8', email: null,
      roleCode: 'PUR_STAFF', accessGranted: false, loginLocked: true } });
    mocks.resetPassword.mockResolvedValue({ data: null });
    await render();
    await act(async () => button('management.lockLogin').props.onClick());
    expect(mocks.updateAccount).toHaveBeenCalledWith(8, { roleCode: 'PUR_STAFF', accessGranted: false, loginLocked: true });
    await act(async () => button('management.edit').props.onClick());
    const checks = tree.root.findAllByType('input').filter(input => input.props.type === 'checkbox');
    await act(async () => checks[0].props.onChange({ target: { checked: true } }));
    await act(async () => forms()[1].props.onSubmit({ preventDefault() {} }));
    expect(mocks.updateAccount).toHaveBeenLastCalledWith(8, { roleCode: 'PUR_STAFF', accessGranted: false, loginLocked: true });
    const secrets = tree.root.findAllByType('input').filter(input => input.props.type === 'password');
    await act(async () => secrets[0].props.onChange({ target: { value: 'newpass123' } }));
    await act(async () => secrets[1].props.onChange({ target: { value: 'newpass123' } }));
    await act(async () => forms()[2].props.onSubmit({ preventDefault() {} }));
    expect(mocks.resetPassword).toHaveBeenCalledWith(8, 'newpass123', 'newpass123');
    expect(tree.root.findAllByType('input').filter(input => input.props.type === 'password').every(input => input.props.value === '')).toBe(true);
    act(() => tree.unmount());
  });
});
