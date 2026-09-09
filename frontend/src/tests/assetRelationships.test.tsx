import { beforeEach, describe, expect, it, vi } from 'vitest';
import { renderToStaticMarkup } from 'react-dom/server';
import { AssetDetailModal } from '@/features/assets/components/AssetDetailModal';
import { AssetRelationships } from '@/features/assets/components/AssetRelationships';
import { relationshipApi } from '@/features/assets/api/relationshipApi';
import { AssetDetail } from '@/features/assets/types/asset.types';
import en from '@/shared/i18n/locales/en/assets.json';
import viLocale from '@/shared/i18n/locales/vi/assets.json';
const client=vi.hoisted(()=>vi.fn());
vi.mock('@/shared/api/httpClient',()=>({httpClient:client}));
vi.mock('react-i18next',()=>({useTranslation:()=>({t:(key:string)=>key,i18n:{language:'en'}})}));
const asset:AssetDetail={assetId:42,assetTag:'OEM-42',name:'Demo Windows',categoryCode:'LICENSE',statusCode:'IN_STOCK',hardwareConfig:{},
  license:{softwareCatalogId:1,softwareName:'Windows demo',assignmentTypeId:1,assignmentTypeCode:'OEM',termTypeId:1,termTypeCode:'PERPETUAL',seatCount:10,allocatedSeats:3,availableSeats:7,licenseKey:'PRIVATE-DEMO-KEY'}};

describe('T11B asset management',()=>{
  beforeEach(()=>client.mockReset());
  it('does not render relationships, audit or keys in the personal detail view',()=>{
    const html=renderToStaticMarkup(<AssetDetailModal isOpen onClose={()=>{}} asset={asset}/>);
    expect(html).toContain('Windows demo');
    for(const value of ['relationships.title','relationships.history','PRIVATE-DEMO-KEY','detail.config']) expect(html).not.toContain(value);
  });
  it('shows package seats and reservations to inventory managers without exposing the key',()=>{
    const html=renderToStaticMarkup(<AssetRelationships asset={asset}/>);
    expect(html).toContain('license.summary');expect(html).toContain('license.allocations');
    expect(html).not.toContain('PRIVATE-DEMO-KEY');
  });
  it('does not offer linking for assets in use or per-user packages',()=>{
    for(const item of [{...asset,statusCode:'IN_USE'},{...asset,license:{...asset.license!,assignmentTypeCode:'PER_USER'}}]) {
      const html=renderToStaticMarkup(<AssetRelationships asset={item}/>);
      expect(html).not.toContain('relationships.add');
    }
  });
  it('uses parent/child identifiers and the protected relationship endpoint',async()=>{
    client.mockResolvedValue({success:true});await relationshipApi.create(10,42,'INSTALLED_ON');
    expect(client).toHaveBeenCalledWith('/v1/assets/10/relationships',{method:'POST',body:JSON.stringify({childAssetId:42,type:'INSTALLED_ON'})});
    await relationshipApi.remove(8);expect(client).toHaveBeenLastCalledWith('/v1/asset-relationships/8',{method:'DELETE'});
  });
  it('paginates allocations and history independently of asset identity',async()=>{
    client.mockResolvedValue({success:true});await relationshipApi.allocations(42,2);await relationshipApi.history(42,3);
    expect(client.mock.calls.map(x=>x[0])).toEqual(['/v1/assets/42/allocations?page=2','/v1/assets/42/history?page=3']);
  });
  it('provides matching Vietnamese and English keys for all new messages',()=>{
    function paths(x:object,prefix=''):string[]{return Object.entries(x).flatMap(([k,v])=>typeof v==='object'?paths(v,`${prefix}${k}.`):[`${prefix}${k}`]);}
    for(const section of ['license','relationships'] as const) expect(paths(en[section]).sort()).toEqual(paths(viLocale[section]).sort());
  });
});
