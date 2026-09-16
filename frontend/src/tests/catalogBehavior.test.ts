import { describe, expect, it, vi } from 'vitest';
import { supportsCatalogFilters, syncSupplierContacts } from '@/features/catalogs/pages/CatalogsPage';

describe('catalog page behavior', () => {
  it('synchronizes added, changed, and removed supplier contacts', async () => {
    const api = {
      addContact: vi.fn().mockResolvedValue({}),
      updateContact: vi.fn().mockResolvedValue({}),
      deleteContact: vi.fn().mockResolvedValue({}),
    };
    const original = [
      { contactId: 1, name: 'Old name', email: 'old@example.com' },
      { contactId: 2, name: 'Remove me', email: 'remove@example.com' },
    ];
    const next = [
      { contactId: 1, name: 'New name', email: 'old@example.com' },
      { name: 'Added', email: 'added@example.com' },
    ];

    await syncSupplierContacts(9, original, next, api);

    expect(api.updateContact).toHaveBeenCalledWith(1, next[0]);
    expect(api.deleteContact).toHaveBeenCalledWith(2);
    expect(api.addContact).toHaveBeenCalledWith(9, next[1]);
  });

  it('only exposes filters for catalogs whose API supports them', () => {
    expect(supportsCatalogFilters('departments')).toBe(true);
    expect(supportsCatalogFilters('types')).toBe(true);
    expect(supportsCatalogFilters('categories')).toBe(false);
    expect(supportsCatalogFilters('statuses')).toBe(false);
    expect(supportsCatalogFilters('license-terms')).toBe(false);
  });
});
