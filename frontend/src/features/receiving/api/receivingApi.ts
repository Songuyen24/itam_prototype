import { ApiResponse } from '@/features/catalogs/types/catalog.types';
import { DocumentItem, DocumentTransaction } from '@/features/documents/types/document.types';
import { httpClient } from '@/shared/api/httpClient';
export type Option = { id: number; name: string; code?: string; category?: string; typeId?: number; default_cpu?: string; default_ram?: string; default_storage?: string; default_graphics_card?: string };
export type References = Record<'types'|'conditions'|'locations'|'departments'|'suppliers'|'models'|'software'|'assignments'|'terms', Option[]>;
export type LineInput = {
  name: string; typeId: number; assetTag?: string; serialNumber?: string;
  modelId?: number; conditionId?: number; locationId?: number; departmentId?: number; supplierId?: number;
  poNumber?: string; purchaseCost?: number; purchaseDate?: string; warrantyExpiration?: string;
  actualCpu?: string; actualRam?: string; actualStorage?: string; actualGraphicsCard?: string;
  license?: { softwareCatalogId: number; assignmentTypeId: number; termTypeId: number; seatCount: number; expiryDate?: string };
};
export type Line = { assetId: number; assetTag: string; name: string; category?: string; input?: LineInput;
  labels?: Record<string,string>; model?: Record<string,string>; effectiveHardware?: Record<string,string>;
  hardware?: Record<string,unknown>; license?: Record<string,unknown>; assetData?: Record<string,unknown> };
export type Content = { notes?: string; lastEditedBy?: string; sourceReceivingId?: number; assets: Line[]; documents: DocumentItem[] };
export type Revision = { revision: number; submittedAt: string; submittedBy?: string; snapshot: Content };
export type Event = { revision: number; action: string; time: string; actor: string; reason?: string };
const base='/v1/import-drafts';
export const receivingApi = {
  content:(id:number)=>httpClient<ApiResponse<Content>>(`${base}/${id}/content`),
  revisions:(id:number)=>httpClient<ApiResponse<Revision[]>>(`${base}/${id}/revisions`),
  events:(id:number)=>httpClient<ApiResponse<Event[]>>(`${base}/${id}/events`),
  references:()=>httpClient<ApiResponse<References>>(`${base}/reference-data`),
  create:(sourceId?:number)=>httpClient<ApiResponse<DocumentTransaction>>(base,{method:'POST',body:JSON.stringify({sourceId})}),
  notes:(id:number,expectedVersion:number,notes:string)=>httpClient(`${base}/${id}`,{method:'PUT',body:JSON.stringify({expectedVersion,notes})}),
  saveLine:(id:number,version:number,input:LineInput,assetId?:number)=>httpClient(`${base}/${id}/${assetId?`assets/${assetId}`:'hardware'}?expectedVersion=${version}`,{method:assetId?'PUT':'POST',body:JSON.stringify(input)}),
  remove:(id:number,version:number,assetId:number)=>httpClient(`${base}/${id}/assets/${assetId}?expectedVersion=${version}`,{method:'DELETE'}),
  reuse:(id:number,version:number,assetId:number)=>httpClient(`${base}/${id}/assets/${assetId}?expectedVersion=${version}`,{method:'POST'}),
  candidates:(keyword:string,page:number)=>httpClient<ApiResponse<Line[]>>(`${base}/candidates?${new URLSearchParams({keyword,page:String(page)})}`),
  action:(transaction:DocumentTransaction,action:'submit'|'withdraw'|'approve'|'reject',reason?:string)=>httpClient(`${base}/${transaction.transactionId}/${action}`,{
    method:'POST',body:JSON.stringify({expectedVersion:transaction.expectedVersion,expectedSubmissionRevision:transaction.submittedRevision,reason})}),
};
