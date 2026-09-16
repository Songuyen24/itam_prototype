import { renderToStaticMarkup } from 'react-dom/server';
import { describe, expect, it, vi } from 'vitest';
import { AssetFormModal } from '@/features/assets/components/AssetFormModal';

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
});
