import { act, create, type ReactTestRenderer } from 'react-test-renderer';
import { beforeEach, describe, expect, it, vi } from 'vitest';

const mocks=vi.hoisted(()=>({ transaction:vi.fn(), documents:vi.fn() }));
vi.mock('react-i18next',()=>{const t=(key:string)=>key;return{useTranslation:()=>({t,i18n:{language:'en'}})};});
vi.mock('@/features/auth/contexts/AuthContext',()=>({useAuth:()=>({user:{id:1,role:'ADMIN'}})}));
vi.mock('@/features/documents/api/documentApi',()=>({documentApi:{getTransaction:mocks.transaction,getDocuments:mocks.documents}}));
vi.mock('@/features/documents/components/ImportDraftPanel',()=>({ImportDraftPanel:({compact}:{compact:boolean})=><output>{compact?'compact':'full'}</output>}));
vi.mock('@/features/documents/components/DocumentUpload',()=>({DocumentUpload:()=> <div data-panel="upload"/>}));
vi.mock('@/features/documents/components/AdjustmentPanel',()=>({AdjustmentPanel:()=> <div data-panel="adjustment"/>}));
vi.mock('@/features/documents/components/PublicationPanel',()=>({PublicationPanel:()=> <div data-panel="publication"/>,publicationPermissions:()=>({})}));
import { TransactionDocuments } from '@/features/documents/components/TransactionDocuments';

const receipt={transactionId:7,transactionCode:'IMP-7',type:'IMPORT',status:'COMPLETED',createdAt:'2026-10-28T00:00:00Z',documentsEditable:false};
const panel=(tree:ReactTestRenderer,name:string)=>tree.root.findAllByProps({'data-panel':name});
const button=(tree:ReactTestRenderer,label:string)=>tree.root.findAllByType('button').find(item=>item.children.some(child=>typeof child==='string'&&child.includes(label)))!;
describe('receiving receipt sections',()=>{
  beforeEach(()=>{mocks.transaction.mockReset().mockResolvedValue({success:true,data:receipt});mocks.documents.mockReset().mockResolvedValue({success:true,data:{content:[],totalPages:0,totalElements:0}});});
  it('mounts document, adjustment and publication work only after opening their modal',async()=>{
    let tree!:ReactTestRenderer;
    await act(async()=>{tree=create(<TransactionDocuments transactionId={7} compact/>);});
    expect(tree.root.findByType('output').children).toEqual(['compact']);
    for(const name of ['upload','adjustment','publication'])expect(panel(tree,name)).toHaveLength(0);
    await act(async()=>button(tree,'listTitle').props.onClick());
    expect(panel(tree,'upload')).toHaveLength(1);
    await act(async()=>tree.root.findAllByProps({'aria-label':'actions.close'})[0].props.onClick());
    expect(panel(tree,'upload')).toHaveLength(0);
    await act(async()=>button(tree,'receiving.requestAdjustment').props.onClick());
    expect(panel(tree,'adjustment')).toHaveLength(1);
    await act(async()=>tree.root.findAllByProps({'aria-label':'actions.close'})[0].props.onClick());
    await act(async()=>button(tree,'publication.title').props.onClick());
    expect(panel(tree,'publication')).toHaveLength(1);
    act(()=>tree.unmount());
  });
  it('keeps the existing documents view inline',async()=>{
    let tree!:ReactTestRenderer;
    await act(async()=>{tree=create(<TransactionDocuments transactionId={7}/>);});
    expect(tree.root.findByType('output').children).toEqual(['full']);
    for(const name of ['upload','adjustment','publication'])expect(panel(tree,name)).toHaveLength(1);
    expect(tree.root.findAllByProps({className:'receiving-section-actions'})).toHaveLength(0);
    act(()=>tree.unmount());
  });
});
