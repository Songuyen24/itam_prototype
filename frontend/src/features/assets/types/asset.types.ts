export interface HardwareConfig {
  defaultCpu?: string;
  defaultRam?: string;
  defaultStorage?: string;
  defaultGraphicsCard?: string;

  actualCpu?: string;
  actualRam?: string;
  actualStorage?: string;
  actualGraphicsCard?: string;

  effectiveCpu?: string;
  effectiveRam?: string;
  effectiveStorage?: string;
  effectiveGraphicsCard?: string;
}

export interface Asset {
  assetId: number;
  assetTag: string;
  name: string;

  typeId?: number;
  typeCode?: string;
  typeName?: string;

  categoryId?: number;
  categoryCode?: string;
  categoryName?: string;

  statusId?: number;
  statusCode?: string;
  statusName?: string;

  conditionId?: number;
  conditionCode?: string;
  conditionName?: string;

  modelId?: number;
  modelName?: string;
  modelBrand?: string;
  serialNumber?: string;

  effectiveCpu?: string;
  effectiveRam?: string;
  effectiveStorage?: string;
  effectiveGraphicsCard?: string;

  assignedToUserId?: number;
  assignedToFullName?: string;
  assignedToEmail?: string;

  departmentId?: number;
  departmentCode?: string;
  departmentName?: string;

  locationId?: number;
  locationCode?: string;
  locationName?: string;

  supplierId?: number;
  supplierCode?: string;
  supplierName?: string;

  poNumber?: string;
  purchaseDate?: string;
  purchaseCost?: number;

  createdAt?: string;
  updatedAt?: string;
}

export interface AssetDetail extends Asset {
  warrantyExpiration?: string;
  hardwareConfig: HardwareConfig;
  createdByUserId?: number;
  createdByFullName?: string;
  updatedByUserId?: number;
  updatedByFullName?: string;
}

export interface CreateHardwareAssetPayload {
  assetTag: string;
  name: string;
  typeId: number;
  statusId?: number;
  departmentId?: number;
  locationId?: number;
  supplierId?: number;
  poNumber?: string;
  purchaseDate?: string;
  purchaseCost?: number;
  assignedToUserId?: number;

  // Hardware details
  serialNumber?: string;
  modelId?: number;
  conditionId?: number;
  warrantyExpiration?: string;
  actualCpu?: string;
  actualRam?: string;
  actualStorage?: string;
  actualGraphicsCard?: string;
}

export interface UpdateHardwareAssetPayload {
  assetTag: string;
  name: string;
  typeId: number;
  statusId?: number;
  departmentId?: number;
  locationId?: number;
  supplierId?: number;
  poNumber?: string;
  purchaseDate?: string;
  purchaseCost?: number;
  assignedToUserId?: number;

  // Hardware details
  serialNumber?: string;
  modelId?: number;
  conditionId?: number;
  warrantyExpiration?: string;
  actualCpu?: string;
  actualRam?: string;
  actualStorage?: string;
  actualGraphicsCard?: string;
}

export interface AssetFilterParams {
  keyword?: string;
  assetTag?: string;
  serialNumber?: string;
  typeId?: number;
  categoryId?: number;
  statusId?: number;
  conditionId?: number;
  modelId?: number;
  departmentId?: number;
  locationId?: number;
  supplierId?: number;
  assignedTo?: number;
  page?: number;
  size?: number;
}

export interface UniquenessValidationResult {
  assetTagAvailable: boolean;
  assetTagMessage: string;
  serialNumberAvailable: boolean;
  serialNumberMessage: string;
}
