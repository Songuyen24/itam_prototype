import { act, create, type ReactTestRenderer } from 'react-test-renderer';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { MemoryRouter } from 'react-router-dom';

const mocks=vi.hoisted(()=>({library:vi.fn(),source:vi.fn(),preview:vi.fn(),download:vi.fn(),user:{role:'ADMIN'}}));
vi.mock('react-i18next',()=>{const t=(key:string)=>key;return{useTranslation:()=>({t,i18n:{language:'en'}})};});
vi.mock('@/features/auth/contexts/AuthContext',()=>({useAuth:()=>({user:mocks.user})}));
vi.mock('@/features/documents/api/documentApi',()=>({documentApi:{library:mocks.library,getTransaction:mocks.source,preview:mocks.preview,download:mocks.download}}));
vi.mock('@/features/documents/components/AdjustmentPanel',()=>({AdjustmentPanel:()=> <div data-adjustment="true"/>}));
import { DocumentsPage } from '@/features/documents/pages/DocumentsPage';

const file={documentId:8,originalFileName:'report.pdf',documentType:'HANDOVER_REPORT',transactionId:42,transactionCode:'HO-42',transactionType:'HANDOVER',transactionStatus:'COMPLETED',mimeType:'application/pdf',fileSize:100,uploadedByName:'Mai',createdAt:'2026-10-28T00:00:00Z',locked:true};
const page=(content=[file])=>({data:{content,totalPages:2,totalElements:22}});
let tree:ReactTestRenderer|null=null;
describe('documents file library',()=>{
  beforeEach(()=>{mocks.library.mockReset().mockResolvedValue(page());mocks.source.mockReset().mockResolvedValue({data:{transactionId:42,transactionCode:'HO-42',type:'HANDOVER',status:'COMPLETED',createdAt:'2026-10-28T00:00:00Z'}});mocks.download.mockReset().mockResolvedValue(undefined);});
  afterEach(()=>{if(tree)act(()=>tree!.unmount());tree=null;vi.unstubAllGlobals();});
  it('starts with files, searches and filters before paging without receipt creation',async()=>{
    await act(async()=>{tree=create(<MemoryRouter><DocumentsPage/></MemoryRouter>);});
    expect(mocks.library).toHaveBeenCalledWith({keyword:'',documentType:'',transactionType:'',transactionId:null,page:0,size:20});
    expect(JSON.stringify(tree!.toJSON())).toContain('report.pdf');
    expect(JSON.stringify(tree!.toJSON())).not.toContain('draft.create');
    await act(async()=>tree!.root.findByType('input').props.onChange({target:{value:'HO-42'}}));
    await act(async()=>tree!.root.findAllByType('select')[0].props.onChange({target:{value:'HANDOVER_REPORT'}}));
    expect(mocks.library).toHaveBeenLastCalledWith(expect.objectContaining({keyword:'HO-42',documentType:'HANDOVER_REPORT',page:0}));
    await act(async()=>tree!.root.findAllByType('button').find(item=>item.props['aria-label']==='pagination.next')!.props.onClick());
    expect(mocks.library).toHaveBeenLastCalledWith(expect.objectContaining({page:1}));
  });
  it('uses deep link only for source context and keeps PUR scope in UI',async()=>{
    mocks.user.role='PUR_STAFF';
    await act(async()=>{tree=create(<MemoryRouter initialEntries={['/documents?transactionId=42']}><DocumentsPage/></MemoryRouter>);});
    expect(mocks.library).toHaveBeenCalledWith(expect.objectContaining({transactionId:42}));
    expect(mocks.source).toHaveBeenCalledWith(42);
    expect(tree!.root.findAllByType('select')).toHaveLength(1);
    act(()=>tree!.unmount());tree=null;mocks.user.role='ADMIN';
  });
  it('shows a preview failure without a false unsupported message or download',async()=>{
    mocks.preview.mockReset().mockRejectedValue(new Error('File unavailable'));
    vi.stubGlobal('document', { activeElement: null });
    await act(async()=>{tree=create(<MemoryRouter><DocumentsPage/></MemoryRouter>, { createNodeMock: element => element.type === 'dialog' ? { showModal() {}, close() {}, querySelector: () => null } : null });});
    await act(async()=>tree!.root.findAllByType('button').find(item=>item.children.includes('actions.preview'))!.props.onClick());
    expect(mocks.preview).toHaveBeenCalledWith(8);
    expect(mocks.download).not.toHaveBeenCalled();
    const output=JSON.stringify(tree!.toJSON());
    expect(output).toContain('File unavailable');
    expect(output).not.toContain('states.previewUnavailable');
  });
});
