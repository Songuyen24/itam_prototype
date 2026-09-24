import { httpClient } from '@/shared/api/httpClient';
import { ApiResponse, PageResponse } from '@/features/catalogs/types/catalog.types';
export interface AssetRef { assetId:number; assetTag:string; name:string; category:string; status:string; assignedToFullName?:string }
export interface Relationship { relationshipId:number; parent:AssetRef; child:AssetRef; type:'COMPONENT_OF'|'INSTALLED_ON'; allocationId?:number; removable:boolean; blockedReason?:string }
export interface Allocation { allocationId:number; seats:number; status:string; deviceId?:number; deviceTag?:string; userId?:number; userName?:string; createdAt:string; releasedAt?:string }
export interface History { id:number; action:string; actor:string; createdAt:string }
export const relationshipApi = {
  list:(id:number,page=0)=>httpClient<ApiResponse<PageResponse<Relationship>>>(`/v1/assets/${id}/relationships?page=${page}`),
  allocations:(id:number,page=0)=>httpClient<ApiResponse<PageResponse<Allocation>>>(`/v1/assets/${id}/allocations?page=${page}`),
  history:(id:number,page=0)=>httpClient<ApiResponse<PageResponse<History>>>(`/v1/assets/${id}/history?page=${page}`),
  create:(parentId:number,childAssetId:number,type:Relationship['type'])=>httpClient<ApiResponse<Relationship>>(`/v1/assets/${parentId}/relationships`,{method:'POST',body:JSON.stringify({childAssetId,type})}),
  remove:(id:number)=>httpClient<void>(`/v1/asset-relationships/${id}`,{method:'DELETE'}),
};
