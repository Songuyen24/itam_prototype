import { renderToStaticMarkup } from 'react-dom/server';
import { act, create } from 'react-test-renderer';
import { describe, expect, it, vi } from 'vitest';
import { CatalogModal } from '@/features/catalogs/components/CatalogModal';

vi.mock('react-i18next', () => ({ useTranslation: () => ({ t: (key: string) => key }) }));

describe('CatalogModal', () => {
  const categories: [] = [];
  it('renders a labelled native dialog', () => {
    const html = renderToStaticMarkup(<CatalogModal isOpen title="Department" categories={categories} isSaving={false} onSave={() => {}} onClose={() => {}} />);
    expect(html).toContain('<dialog');
    expect(html).toContain('aria-labelledby="catalog-modal-title"');
    expect(html).not.toContain('<dialog open');
  });

  it('opens and closes the native dialog while restoring the opener', () => {
    const focus = vi.fn();
    const opener = { focus } as unknown as HTMLElement;
    vi.stubGlobal('document', { activeElement: opener });
    const dialog = { open: false, showModal() { this.open = true; }, close() { this.open = false; }, querySelector: () => ({ focus() {} }) };
    let tree: ReturnType<typeof create>;
    act(() => { tree = create(<CatalogModal isOpen title="Department" categories={categories} isSaving={false} onSave={() => {}} onClose={() => {}} />, { createNodeMock: element => element.type === 'dialog' ? dialog : null }); });
    act(() => tree!.unmount());
    expect(dialog.open).toBe(false);
    expect(focus).toHaveBeenCalledOnce();
    vi.unstubAllGlobals();
  });
});
