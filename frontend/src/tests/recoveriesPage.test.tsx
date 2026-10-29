import { act, create, type ReactTestRenderer } from 'react-test-renderer';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

const mocks=vi.hoisted(()=>({list:vi.fn(),detail:vi.fn(),submit:vi.fn(),withdraw:vi.fn(),cancel:vi.fn(),confirm:vi.fn(),user:{id:1,role:'IT_STAFF'}}));
vi.mock('react-i18next',()=>{const t=(key:string)=>key;return{useTranslation:()=>({t,i18n:{language:'en'}})};});
vi.mock('@/features/auth/contexts/AuthContext',()=>({useAuth:()=>({user:mocks.user})}));
vi.mock('@/features/documents/api/documentApi',()=>({documentApi:{getTransactions:mocks.list}}));
vi.mock('@/features/recovery/api/recoveryApi',()=>({recoveryApi:{detail:mocks.detail,submit:mocks.submit,withdraw:mocks.withdraw,cancel:mocks.cancel,confirm:mocks.confirm}}));
vi.mock('@/features/recovery/components/RecoveryForm',()=>({RecoveryForm:({onCompleted}:{onCompleted:(value:unknown)=>void})=><button onClick={()=>onCompleted({transactionId:8,transactionCode:'RC-8',status:'DRAFT'})}>save recovery</button>}));
vi.mock('@/features/documents/components/PublicationPanel',()=>({PublicationPanel:()=>null}));
import { RecoveriesPage } from '@/features/recovery/pages/RecoveriesPage';

const receipt={transactionId:7,transactionCode:'RC-7',status:'PENDING',createdAt:'2026-10-28T00:00:00Z',requesterName:'Mai'};
const detail={...receipt,creatorUserId:1,returnerName:'Lan',receivingLocationName:'Office',recoveryDate:'2026-10-28',reason:'Return',lines:[]};
let tree:ReactTestRenderer|null=null;
const buttons=()=>tree!.root.findAllByType('button');
const pick=(label:string)=>buttons().find(button=>button.children.includes(label))!;
const response={success:true,data:{content:[receipt],totalPages:2,totalElements:21}};
describe('recovery list and create dialog',()=>{
  beforeEach(()=>{mocks.list.mockReset().mockResolvedValue(response);mocks.detail.mockReset().mockResolvedValue({data:detail});});
  afterEach(()=>{if(tree)act(()=>tree!.unmount());tree=null;});
  it('loads all receipts first and keeps filters on detail close',async()=>{
    await act(async()=>{tree=create(<RecoveriesPage/>);});
    expect(mocks.list).toHaveBeenCalledWith({type:'RECOVERY',keyword:'',status:undefined,from:undefined,to:undefined,page:0,size:20});
    expect(JSON.stringify(tree!.toJSON())).toContain('RC-7');
    expect(tree!.root.findAllByProps({className:'page-tabs'})).toHaveLength(0);
    await act(async()=>tree!.root.findByType('input').props.onChange({target:{value:'RC-7'}}));
    await act(async()=>tree!.root.findByType('select').props.onChange({target:{value:'PENDING'}}));
    await act(async()=>pick('details').props.onClick());
    expect(mocks.detail).toHaveBeenCalledWith(7);
    expect(tree!.root.findAllByProps({className:'recovery-dialog'})).toHaveLength(1);
    await act(async()=>tree!.root.findAllByProps({className:'recovery-dialog'})[0].findAllByType('button').find(button=>button.children.includes('back'))!.props.onClick());
    expect(tree!.root.findAllByProps({className:'recovery-dialog'})).toHaveLength(0);
    expect(mocks.list).toHaveBeenLastCalledWith(expect.objectContaining({keyword:'RC-7',status:'PENDING',page:0}));
  });
  it('applies a single created day then creates via modal without clearing search',async()=>{
    await act(async()=>{tree=create(<RecoveriesPage/>);});
    await act(async()=>tree!.root.findByType('input').props.onChange({target:{value:'RC'}}));
    await act(async()=>buttons().find(button=>button.props['aria-label']==='createdRange')!.props.onClick());
    await act(async()=>buttons().find(button=>button.props['aria-pressed']!==undefined&&button.children.includes('8'))!.props.onClick());
    await act(async()=>pick('receiving.applyRange').props.onClick());
    const last=mocks.list.mock.lastCall![0];expect(last.from).toBe(last.to);expect(last.page).toBe(0);
    await act(async()=>pick('tabForm').props.onClick());
    expect(tree!.root.findAllByProps({id:'recovery-edit-title'})).toHaveLength(1);
    await act(async()=>pick('save recovery').props.onClick());
    expect(tree!.root.findAllByProps({id:'recovery-edit-title'})).toHaveLength(0);
    expect(tree!.root.findAllByType('input').find(input=>input.props.type==='search')!.props.value).toBe('RC');
  });
});
