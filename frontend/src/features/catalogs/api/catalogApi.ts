import { httpClient } from '@/shared/api/httpClient';
import {
  ApiResponse,
  PageResponse,
  Department,
  LocationItem,
  Supplier,
  SupplierContact,
  AssetCategoryItem,
  AssetTypeItem,
  AssetStatusItem,
  AssetConditionItem,
  ModelItem,
  SoftwareCatalogItem,
  LicenseAssignmentType,
  LicenseTermType,
} from '../types/catalog.types';

// ==========================================
// 1. DEPARTMENTS
// ==========================================
export const departmentApi = {
  getAll: (search?: string, isActive?: boolean, page = 0, size = 20) => {
    const params = new URLSearchParams({ page: String(page), size: String(size) });
    if (search) params.append('search', search);
    if (isActive !== undefined) params.append('isActive', String(isActive));
    return httpClient<ApiResponse<PageResponse<Department>>>(`/v1/departments?${params}`);
  },
  getById: (id: number) => httpClient<ApiResponse<Department>>(`/v1/departments/${id}`),
  create: (data: { code: string; name: string; isActive?: boolean }) =>
    httpClient<ApiResponse<Department>>('/v1/departments', {
      method: 'POST',
      body: JSON.stringify(data),
    }),
  update: (id: number, data: { code: string; name: string; isActive?: boolean }) =>
    httpClient<ApiResponse<Department>>(`/v1/departments/${id}`, {
      method: 'PUT',
      body: JSON.stringify(data),
    }),
  delete: (id: number) =>
    httpClient<ApiResponse<void>>(`/v1/departments/${id}`, {
      method: 'DELETE',
    }),
  toggleActive: (id: number, active?: boolean) => {
    const query = active !== undefined ? `?active=${active}` : '';
    return httpClient<ApiResponse<Department>>(`/v1/departments/${id}/active${query}`, {
      method: 'PATCH',
    });
  },
};

// ==========================================
// 2. LOCATIONS
// ==========================================
export const locationApi = {
  getAll: (search?: string, isActive?: boolean, page = 0, size = 20) => {
    const params = new URLSearchParams({ page: String(page), size: String(size) });
    if (search) params.append('search', search);
    if (isActive !== undefined) params.append('isActive', String(isActive));
    return httpClient<ApiResponse<PageResponse<LocationItem>>>(`/v1/locations?${params}`);
  },
  getById: (id: number) => httpClient<ApiResponse<LocationItem>>(`/v1/locations/${id}`),
  create: (data: { code: string; name: string; address?: string; isActive?: boolean }) =>
    httpClient<ApiResponse<LocationItem>>('/v1/locations', {
      method: 'POST',
      body: JSON.stringify(data),
    }),
  update: (id: number, data: { code: string; name: string; address?: string; isActive?: boolean }) =>
    httpClient<ApiResponse<LocationItem>>(`/v1/locations/${id}`, {
      method: 'PUT',
      body: JSON.stringify(data),
    }),
  delete: (id: number) =>
    httpClient<ApiResponse<void>>(`/v1/locations/${id}`, {
      method: 'DELETE',
    }),
  toggleActive: (id: number, active?: boolean) => {
    const query = active !== undefined ? `?active=${active}` : '';
    return httpClient<ApiResponse<LocationItem>>(`/v1/locations/${id}/active${query}`, {
      method: 'PATCH',
    });
  },
};

// ==========================================
// 3. SUPPLIERS & CONTACTS
// ==========================================
export const supplierApi = {
  getAll: (search?: string, isActive?: boolean, page = 0, size = 20) => {
    const params = new URLSearchParams({ page: String(page), size: String(size) });
    if (search) params.append('search', search);
    if (isActive !== undefined) params.append('isActive', String(isActive));
    return httpClient<ApiResponse<PageResponse<Supplier>>>(`/v1/suppliers?${params}`);
  },
  getById: (id: number) => httpClient<ApiResponse<Supplier>>(`/v1/suppliers/${id}`),
  create: (data: Partial<Supplier>) =>
    httpClient<ApiResponse<Supplier>>('/v1/suppliers', {
      method: 'POST',
      body: JSON.stringify(data),
    }),
  update: (id: number, data: Partial<Supplier>) =>
    httpClient<ApiResponse<Supplier>>(`/v1/suppliers/${id}`, {
      method: 'PUT',
      body: JSON.stringify(data),
    }),
  delete: (id: number) =>
    httpClient<ApiResponse<void>>(`/v1/suppliers/${id}`, {
      method: 'DELETE',
    }),
  toggleActive: (id: number, active?: boolean) => {
    const query = active !== undefined ? `?active=${active}` : '';
    return httpClient<ApiResponse<Supplier>>(`/v1/suppliers/${id}/active${query}`, {
      method: 'PATCH',
    });
  },
  getContacts: (supplierId: number) =>
    httpClient<ApiResponse<SupplierContact[]>>(`/v1/suppliers/${supplierId}/contacts`),
  addContact: (supplierId: number, data: SupplierContact) =>
    httpClient<ApiResponse<SupplierContact>>(`/v1/suppliers/${supplierId}/contacts`, {
      method: 'POST',
      body: JSON.stringify(data),
    }),
  updateContact: (contactId: number, data: SupplierContact) =>
    httpClient<ApiResponse<SupplierContact>>(`/v1/supplier-contacts/${contactId}`, {
      method: 'PUT',
      body: JSON.stringify(data),
    }),
  deleteContact: (contactId: number) =>
    httpClient<ApiResponse<void>>(`/v1/supplier-contacts/${contactId}`, {
      method: 'DELETE',
    }),
};

// ==========================================
// 4. ASSET CATEGORIES
// ==========================================
export const categoryApi = {
  getAll: (page = 0, size = 20) => {
    const params = new URLSearchParams({ page: String(page), size: String(size) });
    return httpClient<ApiResponse<PageResponse<AssetCategoryItem>>>(`/v1/asset-categories?${params}`);
  },
  getById: (id: number) => httpClient<ApiResponse<AssetCategoryItem>>(`/v1/asset-categories/${id}`),
  create: (data: { code: string; name: string; isActive?: boolean }) =>
    httpClient<ApiResponse<AssetCategoryItem>>('/v1/asset-categories', {
      method: 'POST',
      body: JSON.stringify(data),
    }),
  update: (id: number, data: { code: string; name: string; isActive?: boolean }) =>
    httpClient<ApiResponse<AssetCategoryItem>>(`/v1/asset-categories/${id}`, {
      method: 'PUT',
      body: JSON.stringify(data),
    }),
  delete: (id: number) =>
    httpClient<ApiResponse<void>>(`/v1/asset-categories/${id}`, {
      method: 'DELETE',
    }),
  toggleActive: (id: number, active?: boolean) => {
    const query = active !== undefined ? `?active=${active}` : '';
    return httpClient<ApiResponse<AssetCategoryItem>>(`/v1/asset-categories/${id}/active${query}`, {
      method: 'PATCH',
    });
  },
};

// ==========================================
// 5. ASSET TYPES
// ==========================================
export const assetTypeApi = {
  getAll: (search?: string, isActive?: boolean, page = 0, size = 20) => {
    const params = new URLSearchParams({ page: String(page), size: String(size) });
    if (search) params.append('search', search);
    if (isActive !== undefined) params.append('isActive', String(isActive));
    return httpClient<ApiResponse<PageResponse<AssetTypeItem>>>(`/v1/asset-types?${params}`);
  },
  getById: (id: number) => httpClient<ApiResponse<AssetTypeItem>>(`/v1/asset-types/${id}`),
  create: (data: { code: string; categoryId: number; name: string; isActive?: boolean }) =>
    httpClient<ApiResponse<AssetTypeItem>>('/v1/asset-types', {
      method: 'POST',
      body: JSON.stringify(data),
    }),
  update: (id: number, data: { code: string; categoryId: number; name: string; isActive?: boolean }) =>
    httpClient<ApiResponse<AssetTypeItem>>(`/v1/asset-types/${id}`, {
      method: 'PUT',
      body: JSON.stringify(data),
    }),
  delete: (id: number) =>
    httpClient<ApiResponse<void>>(`/v1/asset-types/${id}`, {
      method: 'DELETE',
    }),
  toggleActive: (id: number, active?: boolean) => {
    const query = active !== undefined ? `?active=${active}` : '';
    return httpClient<ApiResponse<AssetTypeItem>>(`/v1/asset-types/${id}/active${query}`, {
      method: 'PATCH',
    });
  },
};

// ==========================================
// 6. ASSET STATUSES
// ==========================================
export const assetStatusApi = {
  getAll: (page = 0, size = 20) => {
    const params = new URLSearchParams({ page: String(page), size: String(size) });
    return httpClient<ApiResponse<PageResponse<AssetStatusItem>>>(`/v1/asset-statuses?${params}`);
  },
  create: (data: { code: string; name: string; isActive?: boolean }) =>
    httpClient<ApiResponse<AssetStatusItem>>('/v1/asset-statuses', {
      method: 'POST',
      body: JSON.stringify(data),
    }),
  update: (id: number, data: { code: string; name: string; isActive?: boolean }) =>
    httpClient<ApiResponse<AssetStatusItem>>(`/v1/asset-statuses/${id}`, {
      method: 'PUT',
      body: JSON.stringify(data),
    }),
  delete: (id: number) =>
    httpClient<ApiResponse<void>>(`/v1/asset-statuses/${id}`, {
      method: 'DELETE',
    }),
  toggleActive: (id: number, active?: boolean) => {
    const query = active !== undefined ? `?active=${active}` : '';
    return httpClient<ApiResponse<AssetStatusItem>>(`/v1/asset-statuses/${id}/active${query}`, {
      method: 'PATCH',
    });
  },
};

// ==========================================
// 7. ASSET CONDITIONS
// ==========================================
export const assetConditionApi = {
  getAll: (page = 0, size = 20) => {
    const params = new URLSearchParams({ page: String(page), size: String(size) });
    return httpClient<ApiResponse<PageResponse<AssetConditionItem>>>(`/v1/asset-conditions?${params}`);
  },
  create: (data: { code: string; name: string; isActive?: boolean }) =>
    httpClient<ApiResponse<AssetConditionItem>>('/v1/asset-conditions', {
      method: 'POST',
      body: JSON.stringify(data),
    }),
  update: (id: number, data: { code: string; name: string; isActive?: boolean }) =>
    httpClient<ApiResponse<AssetConditionItem>>(`/v1/asset-conditions/${id}`, {
      method: 'PUT',
      body: JSON.stringify(data),
    }),
  delete: (id: number) =>
    httpClient<ApiResponse<void>>(`/v1/asset-conditions/${id}`, {
      method: 'DELETE',
    }),
  toggleActive: (id: number, active?: boolean) => {
    const query = active !== undefined ? `?active=${active}` : '';
    return httpClient<ApiResponse<AssetConditionItem>>(`/v1/asset-conditions/${id}/active${query}`, {
      method: 'PATCH',
    });
  },
};

// ==========================================
// 8. MODELS
// ==========================================
export const modelApi = {
  getAll: (search?: string, isActive?: boolean, page = 0, size = 20) => {
    const params = new URLSearchParams({ page: String(page), size: String(size) });
    if (search) params.append('search', search);
    if (isActive !== undefined) params.append('isActive', String(isActive));
    return httpClient<ApiResponse<PageResponse<ModelItem>>>(`/v1/models?${params}`);
  },
  getById: (id: number) => httpClient<ApiResponse<ModelItem>>(`/v1/models/${id}`),
  create: (data: {
    name: string;
    brand: string;
    typeId: number;
    defaultCpu?: string;
    defaultRam?: string;
    defaultStorage?: string;
    defaultGraphicsCard?: string;
    isActive?: boolean;
  }) =>
    httpClient<ApiResponse<ModelItem>>('/v1/models', {
      method: 'POST',
      body: JSON.stringify(data),
    }),
  update: (
    id: number,
    data: {
      name: string;
      brand: string;
      typeId: number;
      defaultCpu?: string;
      defaultRam?: string;
      defaultStorage?: string;
      defaultGraphicsCard?: string;
      isActive?: boolean;
    }
  ) =>
    httpClient<ApiResponse<ModelItem>>(`/v1/models/${id}`, {
      method: 'PUT',
      body: JSON.stringify(data),
    }),
  delete: (id: number) =>
    httpClient<ApiResponse<void>>(`/v1/models/${id}`, {
      method: 'DELETE',
    }),
  toggleActive: (id: number, active?: boolean) => {
    const query = active !== undefined ? `?active=${active}` : '';
    return httpClient<ApiResponse<ModelItem>>(`/v1/models/${id}/active${query}`, {
      method: 'PATCH',
    });
  },
};

// ==========================================
// 9. SOFTWARE CATALOG
// ==========================================
export const softwareCatalogApi = {
  getAll: (search?: string, isActive?: boolean, page = 0, size = 20) => {
    const params = new URLSearchParams({ page: String(page), size: String(size) });
    if (search) params.append('search', search);
    if (isActive !== undefined) params.append('isActive', String(isActive));
    return httpClient<ApiResponse<PageResponse<SoftwareCatalogItem>>>(`/v1/software-catalog?${params}`);
  },
  getById: (id: number) => httpClient<ApiResponse<SoftwareCatalogItem>>(`/v1/software-catalog/${id}`),
  create: (data: { name: string; manufacturer: string; version?: string; isActive?: boolean }) =>
    httpClient<ApiResponse<SoftwareCatalogItem>>('/v1/software-catalog', {
      method: 'POST',
      body: JSON.stringify(data),
    }),
  update: (
    id: number,
    data: { name: string; manufacturer: string; version?: string; isActive?: boolean }
  ) =>
    httpClient<ApiResponse<SoftwareCatalogItem>>(`/v1/software-catalog/${id}`, {
      method: 'PUT',
      body: JSON.stringify(data),
    }),
  delete: (id: number) =>
    httpClient<ApiResponse<void>>(`/v1/software-catalog/${id}`, {
      method: 'DELETE',
    }),
  toggleActive: (id: number, active?: boolean) => {
    const query = active !== undefined ? `?active=${active}` : '';
    return httpClient<ApiResponse<SoftwareCatalogItem>>(`/v1/software-catalog/${id}/active${query}`, {
      method: 'PATCH',
    });
  },
};

// ==========================================
// 10. LICENSE ASSIGNMENT TYPES
// ==========================================
export const licenseAssignmentTypeApi = {
  getAll: (page = 0, size = 20) => {
    const params = new URLSearchParams({ page: String(page), size: String(size) });
    return httpClient<ApiResponse<PageResponse<LicenseAssignmentType>>>(
      `/v1/license-assignment-types?${params}`
    );
  },
  create: (data: { code: string; name: string; active?: boolean }) =>
    httpClient<ApiResponse<LicenseAssignmentType>>('/v1/license-assignment-types', {
      method: 'POST',
      body: JSON.stringify(data),
    }),
  update: (id: number, data: { code: string; name: string; active?: boolean }) =>
    httpClient<ApiResponse<LicenseAssignmentType>>(`/v1/license-assignment-types/${id}`, {
      method: 'PUT',
      body: JSON.stringify(data),
    }),
  delete: (id: number) =>
    httpClient<ApiResponse<void>>(`/v1/license-assignment-types/${id}`, {
      method: 'DELETE',
    }),
  toggleActive: (id: number, active?: boolean) => {
    const query = active !== undefined ? `?active=${active}` : '';
    return httpClient<ApiResponse<LicenseAssignmentType>>(
      `/v1/license-assignment-types/${id}/active${query}`,
      {
        method: 'PATCH',
      }
    );
  },
};

// ==========================================
// 11. LICENSE TERM TYPES
// ==========================================
export const licenseTermTypeApi = {
  getAll: (page = 0, size = 20) => {
    const params = new URLSearchParams({ page: String(page), size: String(size) });
    return httpClient<ApiResponse<PageResponse<LicenseTermType>>>(
      `/v1/license-term-types?${params}`
    );
  },
  create: (data: { code: string; name: string; active?: boolean }) =>
    httpClient<ApiResponse<LicenseTermType>>('/v1/license-term-types', {
      method: 'POST',
      body: JSON.stringify(data),
    }),
  update: (id: number, data: { code: string; name: string; active?: boolean }) =>
    httpClient<ApiResponse<LicenseTermType>>(`/v1/license-term-types/${id}`, {
      method: 'PUT',
      body: JSON.stringify(data),
    }),
  delete: (id: number) =>
    httpClient<ApiResponse<void>>(`/v1/license-term-types/${id}`, {
      method: 'DELETE',
    }),
  toggleActive: (id: number, active?: boolean) => {
    const query = active !== undefined ? `?active=${active}` : '';
    return httpClient<ApiResponse<LicenseTermType>>(`/v1/license-term-types/${id}/active${query}`, {
      method: 'PATCH',
    });
  },
};
