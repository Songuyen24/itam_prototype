import { describe, expect, it } from 'vitest';
import { importPayload } from '@/features/assets/components/importPayload';

describe('Excel confirmation payload', () => {
  it('keeps all source rows for server revalidation and retry results', () => {
    const payload=importPayload({fileName:'assets.xlsx',totalRows:2,validRows:1,invalidRows:1,duplicateRows:0,rows:[
      {rowNumber:2,validationStatus:'VALID',errors:[],rawData:{name:'Laptop',assetTag:null}},
      {rowNumber:3,validationStatus:'INVALID',errors:[{rowNumber:3,column:'name',code:'REQUIRED',message:'Required'}],rawData:{name:''}},
    ]},8);
    expect(payload).toEqual({fileName:'assets.xlsx',retryBatchId:8,validRows:[
      {rowNumber:2,name:'Laptop',assetTag:null},{rowNumber:3,name:''},
    ]});
  });
});
