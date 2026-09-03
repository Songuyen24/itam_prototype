export interface ApiResponse<T> {
  success: boolean;
  message?: string;
  data: T;
  timestamp?: string;
  code?: string;
  errors?: string[];
}

export interface PageResponse<T> {
  content: T[];
  pageNumber: number;
  pageSize: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
  empty: boolean;
}

export interface Department {
  departmentId: number;
  code: string;
  name: string;
  isActive: boolean;
  createdAt?: string;
  updatedAt?: string;
}

export interface LocationItem {
  locationId: number;
  code: string;
  name: string;
  address?: string;
  isActive: boolean;
  createdAt?: string;
  updatedAt?: string;
}

export interface SupplierContact {
  contactId?: number;
  supplierId?: number;
  name: string;
  position?: string;
  phone?: string;
  email?: string;
}

export interface Supplier {
  supplierId: number;
  code: string;
  name: string;
  taxCode?: string;
  address?: string;
  phone?: string;
  email?: string;
  isActive: boolean;
  createdAt?: string;
  updatedAt?: string;
  contacts?: SupplierContact[];
}

export interface AssetCategoryItem {
  categoryId: number;
  code: string;
  name: string;
  isActive: boolean;
}

export interface AssetTypeItem {
  typeId: number;
  code: string;
  categoryId: number;
  categoryName?: string;
  name: string;
  isActive: boolean;
}

export interface AssetStatusItem {
  statusId: number;
  code: string;
  name: string;
  isActive: boolean;
}

export interface AssetConditionItem {
  conditionId: number;
  code: string;
  name: string;
  isActive: boolean;
}

export interface ModelItem {
  modelId: number;
  name: string;
  brand: string;
  typeId: number;
  typeName?: string;
  defaultCpu?: string;
  defaultRam?: string;
  defaultStorage?: string;
  defaultGraphicsCard?: string;
  isActive: boolean;
  createdAt?: string;
  updatedAt?: string;
}

export interface SoftwareCatalogItem {
  softwareCatalogId: number;
  name: string;
  manufacturer: string;
  version?: string;
  isActive: boolean;
  createdAt?: string;
  updatedAt?: string;
}

export interface LicenseAssignmentType {
  id: number;
  code: string;
  name: string;
  active: boolean;
}

export interface LicenseTermType {
  id: number;
  code: string;
  name: string;
  active: boolean;
}

export type CatalogTabKey =
  | 'departments'
  | 'locations'
  | 'suppliers'
  | 'categories'
  | 'types'
  | 'statuses'
  | 'conditions'
  | 'models'
  | 'software'
  | 'license-assignments'
  | 'license-terms';
