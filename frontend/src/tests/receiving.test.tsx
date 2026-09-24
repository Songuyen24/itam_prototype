import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderToStaticMarkup } from 'react-dom/server';
import { receivingApi, References } from '@/features/receiving/api/receivingApi';
import { ReceivingAssetForm } from '@/features/receiving/components/ReceivingAssetForm';
import { ReceivingAssetDetails } from '@/features/receiving/components/ReceivingAssetDetails';
import en from '@/shared/i18n/locales/en/documents.json';
import vn from '@/shared/i18n/locales/vi/documents.json';
const client=vi.hoisted(()=>vi.fn());
vi.mock('@/shared/api/httpClient',()=>({httpClient:client}));
vi.mock('react-i18next',()=>({useTranslation:()=>({t:(key:string)=>key,i18n:{language:'en'}})}));
const refs:References={types:[{id:1,name:'Laptop',category:'DEVICE'},{id:2,name:'Package',category:'LICENSE'}],conditions:[{id:1,name:'New'}],models:[],locations:[],departments:[],suppliers:[],software:[{id:1,name:'Office'}],assignments:[{id:1,name:'Per user',code:'PER_USER'}],terms:[{id:1,name:'Subscription',code:'SUBSCRIPTION'}]};
describe('T14 receiving',()=>{
  beforeEach(()=>client.mockReset());
  it('sends exactly the content and submission versions currently reviewed',async()=>{
    client.mockResolvedValue({success:true});
    await receivingApi.action({transactionId:5,transactionCode:'IMP-5',type:'IMPORT',status:'PENDING',createdAt:'2026-09-09',documentsEditable:false,expectedVersion:9,submittedRevision:2},'reject','Wrong configuration');
    expect(client).toHaveBeenCalledWith('/v1/import-drafts/5/reject',{method:'POST',body:JSON.stringify({expectedVersion:9,expectedSubmissionRevision:2,reason:'Wrong configuration'})});
    expect(client).toHaveBeenCalledTimes(1);
  });
  it('edits and removes only a versioned line of the selected draft',async()=>{
    await receivingApi.saveLine(5,9,{name:'RAM',typeId:3},8);
    await receivingApi.remove(5,10,8);
    expect(client.mock.calls.map(c=>[c[0],c[1].method])).toEqual([['/v1/import-drafts/5/assets/8?expectedVersion=9','PUT'],['/v1/import-drafts/5/assets/8?expectedVersion=10','DELETE']]);
  });
  it('uses the restricted purchasing candidate endpoint rather than inventory',async()=>{
    await receivingApi.candidates('TAG & serial',2);
    expect(client.mock.calls[0][0]).toBe('/v1/import-drafts/candidates?keyword=TAG+%26+serial&page=2');
  });
  it('requires hardware condition while keeping serial optional',()=>{
    const html=renderToStaticMarkup(<ReceivingAssetForm refs={refs} initial={{name:'Laptop',typeId:1}} busy={false} onSave={async()=>{}} onCancel={()=>{}}/>);
    expect(html).toMatch(/receiving.conditionId<select[^>]*required/);
    expect(html).toMatch(/receiving.serialNumber<input[^>]*type="text"/);
    expect(html).not.toMatch(/receiving.serialNumber<input[^>]*required/);
    expect(html).toContain('receiving.actualRam');
  });
  it('requires subscription expiry and seats and omits hardware fields for licenses',()=>{
    const html=renderToStaticMarkup(<ReceivingAssetForm refs={refs} initial={{name:'Package',typeId:2,license:{softwareCatalogId:1,assignmentTypeId:1,termTypeId:1,seatCount:10}}} busy={false} onSave={async()=>{}} onCancel={()=>{}}/>);
    expect(html).toMatch(/receiving.expiryDate<input[^>]*required/);
    expect(html).toContain('min="1"');
    expect(html).not.toContain('receiving.conditionId');
    expect(html).not.toContain('licenseKey');
  });
  it('renders frozen model defaults, actual values and effective configuration',()=>{
    const html=renderToStaticMarkup(<ReceivingAssetDetails line={{assetId:1,assetTag:'OLD-TAG',name:'Old name',category:'DEVICE',input:{name:'Old name',typeId:1,actualRam:'32GB'},model:{default_ram:'16GB'},effectiveHardware:{ram:'32GB'}}}/>);
    expect(html).toContain('OLD-TAG');expect(html).toContain('16GB');expect(html).toContain('32GB');
    expect(html).toContain('receiving.defaultConfig');expect(html).toContain('receiving.effectiveConfig');
  });
  it('has matching English and Vietnamese receiving labels and events',()=>{
    expect(Object.keys(en.receiving).sort()).toEqual(Object.keys(vn.receiving).sort());
    expect(Object.keys(en.receiving.events)).toEqual(Object.keys(vn.receiving.events));
    expect(en.receiving.approveConfirm).toContain('all');expect(vn.receiving.approveConfirm).toContain('toàn bộ');
  });
});
