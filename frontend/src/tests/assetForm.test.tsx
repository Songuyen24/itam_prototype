import { renderToStaticMarkup } from 'react-dom/server';
import { act, create, type ReactTestRenderer } from 'react-test-renderer';
import { describe, expect, it, vi } from 'vitest';
import { AssetFormModal } from '@/features/assets/components/AssetFormModal';
import { LicenseFields } from '@/features/assets/components/LicenseFields';

vi.mock('react-i18next', () => ({ useTranslation: () => ({ t: (key: string) => key }) }));
vi.mock('@/features/catalogs/api/catalogApi', () => ({ categoryApi: {
  getAll: async () => ({ data: { content: [{ categoryId: 1, code: 'DEVICE' }] } }),
} }));

describe('AssetFormModal', () => {
  it('shows a save rejection in a dialog without clearing inputs or closing the form', async () => {
    const onClose = vi.fn();
    let tree!: ReactTestRenderer;
    await act(async () => {
      tree = create(<AssetFormModal isOpen initialData={null} onClose={onClose}
        onSubmit={async () => { throw new Error('Business rejection'); }} isSaving={false}
        types={[{ typeId: 1, code: 'LAPTOP', categoryId: 1, name: 'Laptop', isActive: true }]}
        statuses={[{ statusId: 1, code: 'IN_STOCK', name: 'In Stock', isActive: true }]}
        conditions={[]} models={[]} departments={[]} locations={[]} suppliers={[]} />);
    });
    const name = tree.root.findAllByType('input').find(input => input.props.placeholder === 'VD: Laptop Lenovo ThinkPad T14 Gen 4')!;
    await act(async () => name.props.onChange({ target: { value: 'Laptop' } }));
    await act(async () => tree.root.findByType('form').props.onSubmit({ preventDefault() {} }));
    const dialogs = tree.root.findAllByType('dialog');
    expect(dialogs).toHaveLength(2);
    expect(dialogs[1].props['aria-describedby']).toBe('asset-submit-error-message');
    expect(dialogs[1].findByProps({ id: 'asset-submit-error-message' }).children).toEqual(['Business rejection']);
    expect(name.props.value).toBe('Laptop');
    await act(async () => dialogs[1].props.onCancel({ preventDefault() {}, stopPropagation() {} }));
    expect(tree.root.findAllByType('dialog')).toHaveLength(1);
    expect(onClose).not.toHaveBeenCalled();
    expect(name.props.value).toBe('Laptop');
    act(() => tree.unmount());
  });
  it('submits normalized numeric capacities even without blur', async () => {
    const onSubmit = vi.fn(async () => {});
    let tree!: ReactTestRenderer;
    await act(async () => {
      tree = create(<AssetFormModal isOpen initialData={null} onClose={() => {}} onSubmit={onSubmit} isSaving={false}
        types={[{ typeId: 1, code: 'LAPTOP', categoryId: 1, name: 'Laptop', isActive: true }]}
        statuses={[{ statusId: 1, code: 'IN_STOCK', name: 'In Stock', isActive: true }]}
        conditions={[]} models={[]} departments={[]} locations={[]} suppliers={[]} />);
    });
    const inputs = tree.root.findAllByType('input');
    const field = (placeholder: string) => inputs.find(input => input.props.placeholder === placeholder)!;
    await act(async () => {
      field('VD: Laptop Lenovo ThinkPad T14 Gen 4').props.onChange({ target: { value: 'Laptop' } });
      field('form.ram').props.onChange({ target: { value: '16' } });
      field('form.storage').props.onChange({ target: { value: '1' } });
    });
    const unit = tree.root.findAllByType('select').find(select => select.props.value === 'GB')!;
    await act(async () => unit.props.onChange({ target: { value: 'TB' } }));
    const drive = tree.root.findAllByType('select').find(select =>
      select.findAllByType('option').some(option => option.props.value === 'SSD'))!;
    await act(async () => drive.props.onChange({ target: { value: 'HDD' } }));
    await act(async () => tree.root.findByType('form').props.onSubmit({ preventDefault() {} }));
    expect(onSubmit).toHaveBeenCalledWith(expect.objectContaining({ actualRam: '16 GB', actualStorage: '1 TB HDD' }));
    act(() => tree.unmount());
  });
  it('only offers models belonging to the selected asset type', () => {
    const html = renderToStaticMarkup(
      <AssetFormModal
        isOpen
        initialData={null}
        onClose={() => {}}
        onSubmit={async () => {}}
        isSaving={false}
        types={[{ typeId: 1, code: 'LAPTOP', categoryId: 1, name: 'Laptop', isActive: true }]}
        statuses={[{ statusId: 1, code: 'IN_STOCK', name: 'In Stock', isActive: true }]}
        conditions={[]}
        models={[
          { modelId: 10, name: 'Laptop Model', brand: 'A', typeId: 1, isActive: true },
          { modelId: 20, name: 'Printer Model', brand: 'B', typeId: 2, isActive: true },
        ]}
        departments={[]}
        locations={[]}
        suppliers={[]}
      />
    );

    expect(html).toContain('Laptop Model');
    expect(html).not.toContain('Printer Model');
  });

  it('marks only the required base asset fields', () => {
    const html = renderToStaticMarkup(
      <AssetFormModal isOpen initialData={null} onClose={() => {}} onSubmit={async () => {}} isSaving={false}
        types={[{ typeId: 1, code: 'LAPTOP', categoryId: 1, name: 'Laptop', isActive: true }]}
        statuses={[{ statusId: 1, code: 'IN_STOCK', name: 'In Stock', isActive: true }]}
        conditions={[]} models={[]} departments={[]} locations={[]} suppliers={[]} />
    );
    expect(html).toMatch(/fields.name[^<]*<span[^>]*aria-hidden="true"[^>]*>\*<\/span>/);
    expect(html).toMatch(/fields.type[^<]*<span[^>]*aria-hidden="true"[^>]*>\*<\/span>/);
    expect(html).not.toMatch(/form.autoTag[^<]*<span[^>]*>\*<\/span>/);
    expect(html).not.toMatch(/fields.status[^<]*<span[^>]*>\*<\/span>/);
    expect(html).not.toMatch(/fields.condition[^<]*<span[^>]*>\*<\/span>/);
  });

  it('marks required license fields', () => {
    const subscription = renderToStaticMarkup(<LicenseFields value={{softwareCatalogId:0,assignmentTypeId:0,termTypeId:1,seatCount:1}} onChange={() => {}} />);
    expect(subscription).toMatch(/license.software<span[^>]*aria-hidden="true"[^>]*>\*<\/span>/);
    expect(subscription).toMatch(/license.assignment<span[^>]*aria-hidden="true"[^>]*>\*<\/span>/);
    expect(subscription).toMatch(/license.term<span[^>]*aria-hidden="true"[^>]*>\*<\/span>/);
    expect(subscription).toMatch(/license.seats<span[^>]*aria-hidden="true"[^>]*>\*<\/span>/);
  });
});
