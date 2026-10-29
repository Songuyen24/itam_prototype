import { act, create, ReactTestRenderer } from 'react-test-renderer';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { AssetImportModal } from '@/features/assets/components/AssetImportModal';
import { ImportDraftPanel } from '@/features/documents/components/ImportDraftPanel';
import { ApiError } from '@/shared/api/httpClient';

const { preview, confirm, receivingConfirm, content, revisions, events, references, action, session } = vi.hoisted(() => ({
  preview: vi.fn(), confirm: vi.fn(), receivingConfirm: vi.fn(), content: vi.fn(), revisions: vi.fn(), events: vi.fn(), references: vi.fn(), action: vi.fn(),
  session: {user: {id: 4, role: 'PUR_STAFF'}},
}));
vi.mock('@/features/assets/api/importApi', () => ({importApi:{preview,confirm,downloadTemplate:vi.fn()}}));
vi.mock('@/features/receiving/api/receivingApi', () => ({receivingApi:{previewExcel:preview,confirmExcel:receivingConfirm,downloadTemplate:vi.fn(),content,revisions,events,references,action}}));
vi.mock('@/features/auth/contexts/AuthContext', () => ({useAuth:()=>session}));
const translate=(key:string, options?:{page?:number;pages?:number})=>options?.page?`${key}:${options.page}/${options.pages}`:key;
vi.mock('react-i18next', () => ({useTranslation:()=>({t:translate,i18n:{language:'en'}})}));

const transaction={transactionId:5,transactionCode:'IMP-5',type:'IMPORT' as const,status:'DRAFT' as const,requesterId:4,createdAt:'2026-10-20',documentsEditable:true,expectedVersion:1};
const rows=Array.from({length:31},(_,i)=>({rowNumber:i+2,validationStatus:i===30?'INVALID':'VALID',errors:[],rawData:{name:`Laptop-${i}`,assetTag:i===0?null:`TAG-${i}`,type:'LAPTOP'}}));
const mixed={fileName:'sample.xlsx',totalRows:31,validRows:30,invalidRows:1,duplicateRows:0,rows};
const file=()=>({name:'sample.xlsx',size:100} as File);
const buttons=(tree:ReactTestRenderer)=>tree.root.findAllByType('button');
const button=(tree:ReactTestRenderer,label:string)=>buttons(tree).find(node=>node.findAll(child=>typeof child.type==='string' && child.type==='span').some(child=>child.children.includes(label)) || node.children.includes(label))!;
const words=(tree:ReactTestRenderer)=>JSON.stringify(tree.toJSON());
async function flush(){await act(async()=>{await Promise.resolve();await Promise.resolve();});}
async function choose(tree:ReactTestRenderer){
  const input=tree.root.findAllByType('input').find(node=>node.props.type==='file')!;
  await act(async()=>{input.props.onChange({target:{files:[file()]}});});
  await act(async()=>{button(tree,'imports:upload.btnPreview').props.onClick();});
  await flush();
}

describe('Excel modal interactions',()=>{
  beforeEach(()=>{vi.clearAllMocks();preview.mockResolvedValue({success:true,data:mixed});vi.stubGlobal('crypto',{randomUUID:vi.fn().mockReturnValueOnce('id-one').mockReturnValueOnce('id-two')});});
  it('blocks confirmation with no valid rows',async()=>{
    preview.mockResolvedValueOnce({success:true,data:{...mixed,validRows:0,invalidRows:31,rows:rows.map(row=>({...row,validationStatus:'INVALID'}))}});
    const tree=create(<AssetImportModal isOpen onClose={()=>{}} onImportSuccess={()=>{}}/>);
    await choose(tree);
    expect(button(tree,'imports:preview.btnConfirm').props.disabled).toBe(true);
    expect(confirm).not.toHaveBeenCalled();
    tree.unmount();
  });
  it('keeps request ID across failure, changes it for corrected file, preserves retry batch and pages results',async()=>{
    confirm.mockRejectedValueOnce(new Error('network')).mockResolvedValueOnce({success:true,data:{importBatchId:7,importedRows:29,totalRows:31,rows:rows.map((row,i)=>({...row,rawData:{...row.rawData,assetTag:i===0?'AUTO-0':row.rawData.assetTag},validationStatus:i<29?'IMPORTED':'INVALID'}))}})
      .mockResolvedValueOnce({success:true,data:{importBatchId:8,importedRows:1,totalRows:31,rows:[{...rows[29],validationStatus:'IMPORTED'}]}});
    const tree=create(<AssetImportModal isOpen onClose={()=>{}} onImportSuccess={()=>{}}/>);
    await choose(tree);
    expect(words(tree)).toContain('Laptop-0');
    expect(words(tree)).not.toContain('Laptop-29');
    await act(async()=>{button(tree,'imports:preview.next').props.onClick();});
    expect(words(tree)).toContain('Laptop-29');
    await act(async()=>{button(tree,'imports:preview.btnConfirm').props.onClick();});await flush();
    expect(confirm.mock.calls[0][0].requestId).toBe('id-one');
    await act(async()=>{button(tree,'imports:preview.btnConfirm').props.onClick();});await flush();
    expect(confirm.mock.calls[1][0].requestId).toBe('id-one');
    expect(words(tree)).toContain('imports:completed.batchId');
    expect(words(tree)).toContain('AUTO-0');
    await act(async()=>{button(tree,'imports:preview.next').props.onClick();});
    expect(words(tree)).toContain('TAG-29');
    await act(async()=>{button(tree,'imports:completed.retryFile').props.onClick();});
    await choose(tree);
    await act(async()=>{button(tree,'imports:preview.btnConfirm').props.onClick();});await flush();
    expect(confirm.mock.calls[2][0]).toMatchObject({requestId:'id-two',retryBatchId:7});
    tree.unmount();
  });
  it('replaces stale preview rows when confirmation finds no valid rows',async()=>{
    const fresh=rows.map(row=>({...row,validationStatus:'INVALID',errorMessage:'Already exists'}));
    confirm.mockRejectedValueOnce(new ApiError('No valid rows',400,'NO_VALID_ROWS',[],fresh));
    const tree=create(<AssetImportModal isOpen onClose={()=>{}} onImportSuccess={()=>{}}/>);
    await choose(tree);
    await act(async()=>{button(tree,'imports:preview.btnConfirm').props.onClick();});await flush();
    expect(button(tree,'imports:preview.btnConfirm').props.disabled).toBe(true);
    expect(words(tree)).toContain('Already exists');
    tree.unmount();
  });
});

describe('Receiving draft interactions',()=>{
  beforeEach(()=>{
    vi.clearAllMocks();content.mockResolvedValue({success:true,data:{notes:'',assets:[{assetId:8,assetTag:'TAG-8',name:'Laptop'}],documents:[]}});
    revisions.mockResolvedValue({success:true,data:[]});events.mockResolvedValue({success:true,data:[]});references.mockResolvedValue({success:true,data:{types:[],conditions:[],models:[],locations:[],departments:[],suppliers:[],software:[],assignments:[],terms:[]}});
    action.mockResolvedValue({success:true});preview.mockResolvedValue({success:true,data:mixed});
    receivingConfirm.mockResolvedValue({success:true,data:{transaction:{...transaction,expectedVersion:2},batch:{importBatchId:9,importedRows:30,totalRows:31,rows}}});
    vi.stubGlobal('crypto',{randomUUID:vi.fn().mockReturnValue('receiving-id')});
  });
  it('allows submission without documents and retains Excel results until close',async()=>{
    const changed=vi.fn();
    const tree=create(<ImportDraftPanel transaction={transaction} onChanged={changed}/>);
    await flush();
    expect(button(tree,'draft.submit').props.disabled).toBe(false);
    await act(async()=>{button(tree,'receiving.importExcel').props.onClick();});
    await choose(tree);
    await act(async()=>{button(tree,'imports:preview.btnConfirm').props.onClick();});await flush();
    expect(receivingConfirm.mock.calls[0][0]).toBe(5);
    expect(receivingConfirm.mock.calls[0][1]).toBe(1);
    expect(changed).not.toHaveBeenCalled();
    expect(words(tree)).toContain('imports:completed.resultTitle');
    await act(async()=>{button(tree,'imports:completed.btnFinish').props.onClick();});
    expect(changed).toHaveBeenCalledTimes(1);
    tree.unmount();
  });
  it('keeps compact receipt asset editing and history in separate dialogs',async()=>{
    const tree=create(<ImportDraftPanel compact transaction={transaction} onChanged={vi.fn()}/>);
    await flush();
    expect(tree.root.findAllByProps({className:'receiving-form'})).toHaveLength(0);
    expect(words(tree)).not.toContain('receiving.sections.identity');
    await act(async()=>button(tree,'draft.addAsset').props.onClick());
    expect(tree.root.findAllByProps({role:'dialog'})).toHaveLength(1);
    expect(words(tree)).toContain('receiving.sections.identity');
    await act(async()=>tree.root.findAllByProps({role:'dialog'})[0].findAllByType('button').find(node=>node.props['aria-label']==='actions.close')!.props.onClick());
    expect(tree.root.findAllByProps({role:'dialog'})).toHaveLength(0);
    await act(async()=>button(tree,'draft.history').props.onClick());
    expect(tree.root.findAllByProps({role:'dialog'})).toHaveLength(1);
    expect(words(tree)).toContain('draft.noHistory');
    tree.unmount();
  });
});
