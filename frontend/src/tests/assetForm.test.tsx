import { renderToStaticMarkup } from 'react-dom/server';
import { describe, expect, it, vi } from 'vitest';
import { AssetFormModal } from '@/features/assets/components/AssetFormModal';
import { LicenseFields } from '@/features/assets/components/LicenseFields';

vi.mock('react-i18next', () => ({ useTranslation: () => ({ t: (key: string) => key }) }));

describe('AssetFormModal', () => {
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
