import { beforeEach, describe, expect, it, vi } from 'vitest';
import { renderToStaticMarkup } from 'react-dom/server';
import renderer, { act } from 'react-test-renderer';
import { AssetDetailModal } from '@/features/assets/components/AssetDetailModal';
import { AssetRelationships } from '@/features/assets/components/AssetRelationships';
import { relationshipApi } from '@/features/assets/api/relationshipApi';
import { assetApi } from '@/features/assets/api/assetApi';
import { AssetDetail } from '@/features/assets/types/asset.types';
import en from '@/shared/i18n/locales/en/assets.json';
import viLocale from '@/shared/i18n/locales/vi/assets.json';
const client=vi.hoisted(()=>vi.fn());
vi.mock('@/shared/api/httpClient',()=>({httpClient:client}));
vi.mock('react-i18next',()=>{const t=(key:string)=>key;return {useTranslation:()=>({t,i18n:{language:'en'}})};});
const asset:AssetDetail={assetId:42,assetTag:'OEM-42',name:'Demo Windows',categoryCode:'LICENSE',statusCode:'IN_STOCK',version:0,hardwareConfig:{},
  license:{softwareCatalogId:1,softwareName:'Windows demo',assignmentTypeId:1,assignmentTypeCode:'OEM',termTypeId:1,termTypeCode:'PERPETUAL',seatCount:10,allocatedSeats:3,availableSeats:7,licenseKey:'PRIVATE-DEMO-KEY'}};

describe('T11B asset management',()=>{
  beforeEach(()=>client.mockReset());
  it('does not render relationships, audit or keys in the personal detail view',()=>{
    const html=renderToStaticMarkup(<AssetDetailModal isOpen onClose={()=>{}} asset={asset}/>);
    expect(html).toContain('Windows demo');
    for(const value of ['relationships.title','relationships.history','PRIVATE-DEMO-KEY','detail.config']) expect(html).not.toContain(value);
  });
  it('shows package seats and reservations to inventory managers without exposing the key',()=>{
    const html=renderToStaticMarkup(<AssetDetailModal isOpen onClose={()=>{}} asset={asset} onEdit={()=>{}}/>);
    expect(html).toContain('license.allocatedSeats');expect(html).toContain('license.allocations');
    expect(html).not.toContain('PRIVATE-DEMO-KEY');
  });
  it('puts license facts first and omits hardware-only fields in manager details',()=>{
    const html=renderToStaticMarkup(<AssetDetailModal isOpen onClose={()=>{}} asset={asset} onEdit={()=>{}}/>);
    expect(html.indexOf('license.software')).toBeLessThan(html.indexOf('detail.type'));
    for(const field of ['detail.model','detail.serial','detail.condition','detail.warranty','detail.assigned','detail.config']) expect(html).not.toContain(field);
    for(const field of ['license.allocatedSeats','license.availableSeats','license.expiry']) expect(html).toContain(field);
    expect(html).not.toContain('license.reservedHint');
  });
  it('renders allocation data in a table with dates and empty-state columns',async()=>{
    const empty={data:{content:[],totalElements:0,totalPages:0}};
    vi.spyOn(relationshipApi,'list').mockResolvedValue(empty as never);
    vi.spyOn(relationshipApi,'history').mockResolvedValue(empty as never);
    vi.spyOn(relationshipApi,'allocations').mockResolvedValue({data:{content:[{allocationId:9,seats:2,status:'ACTIVE',deviceTag:'D-7',userName:'User A',createdAt:'2026-10-30T10:00:00Z'}],totalElements:1,totalPages:1}} as never);
    vi.spyOn(assetApi,'getAssetById').mockResolvedValue({data:asset} as never);
    let tree:renderer.ReactTestRenderer;
    await act(async()=>{tree=renderer.create(<AssetRelationships asset={asset}/>);});
    const table=tree!.root.findByProps({className:'data-table asset-allocation-table'});
    expect(table.findAllByType('th').map(node=>node.children.join(''))).toEqual(['license.allocationId','license.device','license.user','license.seats','license.status','license.createdAt','license.releasedAt']);
    expect(table.findAllByType('tbody')[0].findAllByType('td').map(node=>node.children.join(''))).toEqual(['#9','D-7','User A','2','license.ACTIVE',expect.any(String),'—']);
    expect(tree!.root.findAllByType('h3').map(node=>node.children.join(''))).toEqual(['license.allocations']);
    expect(tree!.root.findAllByType('h4').map(node=>node.children.join(''))).toEqual(['relationships.history']);
    expect(tree!.root.findAllByType('button').some(node=>node.children.includes('relationships.openAdd'))).toBe(false);
    tree!.unmount();vi.restoreAllMocks();
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
  it('renders history columns and requests five records on each page',async()=>{
    const empty={data:{content:[],totalElements:0,totalPages:0}};
    vi.spyOn(relationshipApi,'list').mockResolvedValue(empty as never);
    const history=vi.spyOn(relationshipApi,'history').mockImplementation(async(_id,page=0)=>({data:{content:[{id:page+1,action:'CREATED',actor:`User ${page+1}`,createdAt:'2026-10-30T10:00:00Z'}],totalElements:6,totalPages:2}} as never));
    vi.spyOn(relationshipApi,'allocations').mockResolvedValue(empty as never);
    vi.spyOn(assetApi,'getAssetById').mockResolvedValue({data:asset} as never);
    let tree:renderer.ReactTestRenderer;
    await act(async()=>{tree=renderer.create(<AssetRelationships asset={asset}/>);});
    const table=tree!.root.findByProps({className:'data-table asset-history-table'});
    expect(table.findAllByType('th').map(node=>node.children.join(''))).toEqual(['relationships.historyAction','relationships.historyActor','relationships.historyTime']);
    expect(table.findAllByType('tbody')[0].findAllByType('tr')[0].findAllByType('td').map(node=>node.children.join(''))).toEqual(['relationships.actions.CREATED','User 1',expect.any(String)]);
    expect(history).toHaveBeenCalledWith(42,0,5);
    const nextButtons=tree!.root.findAllByType('button').filter(node=>node.props['aria-label']==='pagination.next');
    const next=nextButtons[nextButtons.length-1];
    await act(async()=>next!.props.onClick());
    expect(history).toHaveBeenLastCalledWith(42,1,5);
    expect(table.findAllByType('tbody')[0].findAllByType('tr')[0].findAllByType('td')[1].children).toEqual(['User 2']);
    tree!.unmount();
    vi.restoreAllMocks();
  });
  it('provides matching Vietnamese and English keys for all new messages',()=>{
    function paths(x:object,prefix=''):string[]{return Object.entries(x).flatMap(([k,v])=>typeof v==='object'?paths(v,`${prefix}${k}.`):[`${prefix}${k}`]);}
    for(const section of ['license','relationships'] as const) expect(paths(en[section]).sort()).toEqual(paths(viLocale[section]).sort());
  });
  it('opens the add dialog, keeps an attach error there, then closes after success',async()=>{
    const device={...asset,categoryCode:'DEVICE' as const,license:undefined};
    const empty={data:{content:[],totalElements:0,totalPages:0}};
    vi.spyOn(relationshipApi,'list').mockResolvedValue(empty as never);
    vi.spyOn(relationshipApi,'history').mockResolvedValue(empty as never);
    vi.spyOn(relationshipApi,'allocations').mockResolvedValue(empty as never);
    vi.spyOn(assetApi,'getAssetById').mockResolvedValue({data:device} as never);
    const candidates=vi.spyOn(assetApi,'relationshipCandidates').mockResolvedValue({data:{content:[{assetId:7,assetTag:'D-7',name:'Device'}],totalElements:1,totalPages:1}} as never);
    const create=vi.spyOn(relationshipApi,'create').mockRejectedValueOnce(new Error('Attach failed')).mockResolvedValueOnce({} as never);
    let dialogOpen=false;
    let tree:renderer.ReactTestRenderer;
    await act(async()=>{tree=renderer.create(<AssetRelationships asset={device}/>,{createNodeMock:element=>element.type==='dialog'?{showModal:()=>{dialogOpen=true;},close:()=>{dialogOpen=false;}}:element.type==='button'?{focus:()=>{}}:null});});
    expect(tree!.root.findAllByProps({className:'document-pagination'})).toHaveLength(1);
    expect(tree!.root.findAllByType('h3').some(node=>node.children.includes('relationships.title'))).toBe(true);
    const button=(label:string)=>tree!.root.findAllByType('button').find(node=>node.children.includes(label))!;
    await act(async()=>button('relationships.openAdd').props.onClick());
    expect(dialogOpen).toBe(true);
    expect(candidates).toHaveBeenCalledWith(42,'',0);
    expect(tree!.root.findByType('select').findAllByType('option')).toHaveLength(2);
    await act(async()=>tree!.root.findByType('input').props.onChange({target:{value:'old search'}}));
    await act(async()=>tree!.root.findByType('dialog').props.onCancel({preventDefault:()=>{},stopPropagation:()=>{}}));
    expect(dialogOpen).toBe(false);
    await act(async()=>button('relationships.openAdd').props.onClick());
    expect(candidates).toHaveBeenLastCalledWith(42,'',0);
    expect(tree!.root.findByType('input').props.value).toBe('');
    expect(tree!.root.findByType('select').findAllByType('option')).toHaveLength(2);
    await act(async()=>tree!.root.findByType('select').props.onChange({target:{value:'7'}}));
    await act(async()=>button('relationships.add').props.onClick());
    expect(tree!.root.findByProps({role:'alert'}).children).toContain('Attach failed');
    expect(dialogOpen).toBe(true);
    await act(async()=>button('relationships.add').props.onClick());
    expect(create).toHaveBeenCalledTimes(2);
    expect(dialogOpen).toBe(false);
    expect(tree!.root.findAllByType('dialog')).toHaveLength(0);
    tree!.unmount();
    vi.restoreAllMocks();
  });
});
