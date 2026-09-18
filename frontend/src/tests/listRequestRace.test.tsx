import { act, create, ReactTestRenderer } from 'react-test-renderer';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

const mocks = vi.hoisted(() => {
  const api = () => ({ getAll: vi.fn() });
  return {
    departments: api(), locations: api(), suppliers: api(), categories: api(), types: api(), statuses: api(),
    conditions: api(), models: api(), software: api(), assignments: api(), terms: api(),
    getAssets: vi.fn(), getMyAssets: vi.fn(),
  };
});
const t = (key: string) => key;

vi.mock('react-i18next', () => ({ useTranslation: () => ({ t, i18n: { language: 'en' } }) }));
vi.mock('@/features/auth/contexts/AuthContext', () => ({ useAuth: () => ({ user: { id: 1, role: 'IT_STAFF' } }) }));
vi.mock('@/features/catalogs/api/catalogApi', () => ({
  departmentApi: mocks.departments, locationApi: mocks.locations, supplierApi: mocks.suppliers,
  categoryApi: mocks.categories, assetTypeApi: mocks.types, assetStatusApi: mocks.statuses,
  assetConditionApi: mocks.conditions, modelApi: mocks.models, softwareCatalogApi: mocks.software,
  licenseAssignmentTypeApi: mocks.assignments, licenseTermTypeApi: mocks.terms,
}));
vi.mock('@/features/assets/api/assetApi', () => ({ assetApi: { getAssets: mocks.getAssets, getMyAssets: mocks.getMyAssets } }));
vi.mock('@/features/catalogs/components/CatalogTable', () => ({
  CatalogTable: ({ data, isLoading, onEdit }: any) => <>
    <output data-testid="catalog-rows" data-loading={String(isLoading)}>{data.map((row: any) => row.name).join(',')}</output>
    {data[0] && <button data-testid="select-catalog-row" onClick={() => onEdit?.(data[0])}>select</button>}
  </>,
}));
vi.mock('@/features/catalogs/components/CatalogModal', () => ({ CatalogModal: ({ initialData }: any) => <output data-testid="catalog-selection">{initialData?.name ?? ''}</output> }));
vi.mock('@/features/catalogs/components/ModelModal', () => ({ ModelModal: () => null }));
vi.mock('@/features/catalogs/components/SupplierModal', () => ({ SupplierModal: () => null }));
vi.mock('@/features/catalogs/components/ConfirmDeleteModal', () => ({ ConfirmDeleteModal: () => null }));
vi.mock('@/features/assets/components/AssetFilterBar', () => ({
  AssetFilterBar: ({ keyword, onKeywordChange }: any) => <input data-testid="asset-query" value={keyword} onChange={event => onKeywordChange((event.target as HTMLInputElement).value)} />,
}));
vi.mock('@/features/assets/components/AssetFormModal', () => ({ AssetFormModal: () => null }));
vi.mock('@/features/assets/components/AssetDetailModal', () => ({ AssetDetailModal: () => null }));
vi.mock('@/features/assets/components/AssetImportModal', () => ({ AssetImportModal: () => null }));

import { AssetsPage } from '@/features/assets/pages/AssetsPage';
import { CatalogsPage } from '@/features/catalogs/pages/CatalogsPage';

type Deferred<T> = { promise: Promise<T>; resolve: (value: T) => void; reject: (reason: unknown) => void };
const deferred = <T,>(): Deferred<T> => {
  let resolve!: (value: T) => void;
  let reject!: (reason: unknown) => void;
  const promise = new Promise<T>((ok, fail) => { resolve = ok; reject = fail; });
  return { promise, resolve, reject };
};
const page = (content: any[], totalElements = content.length, totalPages = 1) => ({
  success: true,
  data: { content, pageNumber: 0, pageSize: 20, totalElements, totalPages, first: true, last: totalPages === 1, empty: content.length === 0 },
});
const renderers: ReactTestRenderer[] = [];

async function render(node: React.ReactElement) {
  let tree!: ReactTestRenderer;
  await act(async () => { tree = create(node); });
  renderers.push(tree);
  return tree;
}

function resetApis() {
  Object.values(mocks).forEach((value: any) => {
    if ('getAll' in value) value.getAll.mockReset().mockResolvedValue(page([]));
    else value.mockReset();
  });
}

describe('list request race guards', () => {
  beforeEach(resetApis);

  afterEach(() => {
    while (renderers.length) act(() => renderers.pop()!.unmount());
    resetApis();
  });

  it('keeps newer Catalog tab rows, total, pagination, and selection when an old success resolves late', async () => {
    const oldDepartments = deferred<ReturnType<typeof page>>();
    const newLocations = deferred<ReturnType<typeof page>>();
    mocks.departments.getAll.mockReturnValue(oldDepartments.promise);
    mocks.locations.getAll.mockReturnValue(newLocations.promise);
    const tree = await render(<CatalogsPage />);

    await act(async () => tree.root.findAllByType('button').find(button => button.children.includes('catalogs:tabs.locations'))!.props.onClick());
    await act(async () => newLocations.resolve(page([{ locationId: 2, name: 'NEW-LOCATION' }], 2, 2)));
    expect(tree.root.findByProps({ 'data-testid': 'catalog-rows' }).children).toContain('NEW-LOCATION');
    expect(tree.root.findAllByType('strong').some(node => node.children.includes('2'))).toBe(true);
    expect(tree.root.findAllByType('button').find(button => button.children.includes('common:pagination.next'))!.props.disabled).toBe(false);
    act(() => tree.root.findByProps({ 'data-testid': 'select-catalog-row' }).props.onClick());
    expect(tree.root.findByProps({ 'data-testid': 'catalog-selection' }).children).toContain('NEW-LOCATION');
    await act(async () => oldDepartments.resolve(page([{ departmentId: 1, name: 'OLD-DEPARTMENT' }], 99, 9)));
    expect(tree.root.findByProps({ 'data-testid': 'catalog-rows' }).children).toContain('NEW-LOCATION');
    expect(tree.root.findByProps({ 'data-testid': 'catalog-rows' }).children).not.toContain('OLD-DEPARTMENT');
    expect(tree.root.findByProps({ 'data-testid': 'catalog-selection' }).children).toContain('NEW-LOCATION');
  });

  it('does not clear Catalog loading or show an error when the replaced request rejects', async () => {
    const oldDepartments = deferred<ReturnType<typeof page>>();
    const newLocations = deferred<ReturnType<typeof page>>();
    mocks.departments.getAll.mockReturnValue(oldDepartments.promise);
    mocks.locations.getAll.mockReturnValue(newLocations.promise);
    const tree = await render(<CatalogsPage />);

    await act(async () => tree.root.findAllByType('button').find(button => button.children.includes('catalogs:tabs.locations'))!.props.onClick());
    await act(async () => oldDepartments.reject(new Error('old request failed')));
    expect(tree.root.findByProps({ 'data-testid': 'catalog-rows' }).props['data-loading']).toBe('true');
    expect(tree.root.findAllByProps({ className: 'alert-banner alert-danger' })).toHaveLength(0);
    await act(async () => newLocations.resolve(page([{ locationId: 3, name: 'NEW-LOCATION' }])));
    expect(tree.root.findByProps({ 'data-testid': 'catalog-rows' }).props['data-loading']).toBe('false');
  });

  it('keeps Assets loading and pagination tied to the newer query after the old query rejects', async () => {
    const oldQuery = deferred<ReturnType<typeof page>>();
    const newQuery = deferred<ReturnType<typeof page>>();
    mocks.getAssets.mockReturnValueOnce(oldQuery.promise).mockReturnValueOnce(newQuery.promise);
    const tree = await render(<AssetsPage />);

    await act(async () => tree.root.findByProps({ 'data-testid': 'asset-query' }).props.onChange({ target: { value: 'new' } }));
    await act(async () => oldQuery.reject(new Error('old query failed')));
    expect(JSON.stringify(tree.toJSON())).toContain('common:labels.loading');
    expect(JSON.stringify(tree.toJSON())).not.toContain('old query failed');

    await act(async () => newQuery.resolve(page([{ assetId: 7, assetTag: 'NEW-ASSET', name: 'New asset', statusCode: 'IN_STOCK' }], 42, 3)));
    const json = JSON.stringify(tree.toJSON());
    expect(json).toContain('NEW-ASSET');
    expect(json).toContain('42');
    expect(json).toContain('common:pagination.next');
  });

  it('keeps newer Assets rows and pagination when an old query succeeds late', async () => {
    const oldQuery = deferred<ReturnType<typeof page>>();
    const newQuery = deferred<ReturnType<typeof page>>();
    mocks.getAssets.mockReturnValueOnce(oldQuery.promise).mockReturnValueOnce(newQuery.promise);
    const tree = await render(<AssetsPage />);

    await act(async () => tree.root.findByProps({ 'data-testid': 'asset-query' }).props.onChange({ target: { value: 'new' } }));
    await act(async () => newQuery.resolve(page([{ assetId: 8, assetTag: 'NEW-ASSET', name: 'New asset', statusCode: 'IN_STOCK' }], 2, 2)));
    await act(async () => oldQuery.resolve(page([{ assetId: 9, assetTag: 'OLD-ASSET', name: 'Old asset', statusCode: 'IN_STOCK' }], 99, 9)));

    const assetTags = tree.root.findAllByType('span').flatMap(node => node.children);
    expect(assetTags).toContain('NEW-ASSET');
    expect(assetTags).not.toContain('OLD-ASSET');
    expect(tree.root.findAllByType('button').filter(button => button.children.includes('2'))).toHaveLength(1);
  });
});
