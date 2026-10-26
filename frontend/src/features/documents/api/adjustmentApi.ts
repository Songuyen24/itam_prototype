import { ApiResponse } from '@/features/catalogs/types/catalog.types';
import { downloadFile, httpClient } from '@/shared/api/httpClient';
export interface Impact { transactionId: number; transactionCode: string; type: string; status: string }
export interface Adjustment { id:number; transactionId:number; version:number; kind:string; reason:string; proposedText:string; correctiveTransactionId:number|null; requesterName:string; createdAt:string; status:string; deciderName:string|null; decidedAt:string|null; decisionReason:string|null; impactSnapshot:string|null; files:{fileId:number;name:string;size:number;checksum:string}[] }
export interface AdjustmentOverview { total:number; canRequest:boolean; requests:Adjustment[]; subsequentTransactions:Impact[] }
const url=(id:number)=>`/v1/transactions/${id}/adjustments`;
export const adjustmentApi={
  list:(id:number,page=0):Promise<ApiResponse<AdjustmentOverview>>=>httpClient(`${url(id)}?page=${page}`),
  create:(id:number,body:FormData):Promise<ApiResponse<Adjustment>>=>httpClient(url(id),{method:'POST',body}),
  decide:(id:number,request:Adjustment,approve:boolean,reason:string,reviewedTransactions:Impact[]):Promise<ApiResponse<Adjustment>>=>httpClient(`${url(id)}/${request.id}/decision`,{method:'POST',body:JSON.stringify({expectedVersion:request.version,approve,reason,reviewedTransactions})}),
  download:(id:number,requestId:number,file:{fileId:number;name:string})=>downloadFile(`${url(id)}/${requestId}/files/${file.fileId}`,file.name),
};

