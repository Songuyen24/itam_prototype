import { act, create, type ReactTestRenderer } from 'react-test-renderer';
import { beforeEach, describe, expect, it, vi } from 'vitest';

const mocks=vi.hoisted(()=>({ preview:vi.fn(), confirm:vi.fn(), template:vi.fn(), done:vi.fn(), close:vi.fn() }));
vi.mock('react-i18next',()=>{const t=(key:string)=>key;return{useTranslation:()=>({t})};});
vi.mock('@/features/auth/api/managementApi',()=>({managementApi:{accountImportPreview:mocks.preview,accountImportConfirm:mocks.confirm,accountImportTemplate:mocks.template}}));
import { AccountsImportModal } from '@/features/auth/components/AccountsImportModal';

const valid={row:2,employeeCode:'0012',fullName:'Lan',email:null,departmentCode:'IT',status:'VALID',errors:[],id:null};
let tree:ReactTestRenderer;
const button=(label:string)=>tree.root.findAllByType('button').find(item=>item.children.includes(label))!;
describe('account import modal',()=>{
  beforeEach(()=>{Object.values(mocks).forEach(mock=>mock.mockReset());mocks.preview.mockResolvedValue({data:{rows:[valid]}});mocks.confirm.mockResolvedValue({data:{rows:[{...valid,status:'CREATED',id:9}]}});});
  it('uploads only on preview, confirms the same file and clears results on file change',async()=>{
    await act(async()=>{tree=create(<AccountsImportModal open onClose={mocks.close} onDone={mocks.done}/>);});
    const file=new File(['one'],'users.xlsx');
    await act(async()=>tree.root.findByType('input').props.onChange({target:{files:[file]}}));
    expect(mocks.preview).not.toHaveBeenCalled();expect(button('management.confirmImport')).toBeUndefined();
    await act(async()=>button('management.previewImport').props.onClick());
    expect(mocks.preview).toHaveBeenCalledWith(file);
    expect(JSON.stringify(tree.toJSON())).toContain('0012');
    expect(JSON.stringify(tree.toJSON())).toContain('IT');
    await act(async()=>button('management.confirmImport').props.onClick());
    expect(mocks.confirm).toHaveBeenCalledWith(file);expect(mocks.done).toHaveBeenCalledOnce();
    const changed=new File(['two'],'changed.xlsx');
    await act(async()=>tree.root.findByType('input').props.onChange({target:{files:[changed]}}));
    expect(JSON.stringify(tree.toJSON())).not.toContain('0012');
    expect(button('management.confirmImport')).toBeUndefined();
    act(()=>tree.unmount());
  });
  it('does not confirm invalid-only previews or render passwords',async()=>{
    mocks.preview.mockResolvedValue({data:{rows:[{...valid,status:'INVALID',errors:['IDENTITY_DUPLICATE']}]}});
    await act(async()=>{tree=create(<AccountsImportModal open onClose={mocks.close} onDone={mocks.done}/>);});
    await act(async()=>tree.root.findByType('input').props.onChange({target:{files:[new File(['x'],'users.xlsx')]}}));
    await act(async()=>button('management.previewImport').props.onClick());
    expect(button('management.confirmImport')).toBeUndefined();expect(mocks.confirm).not.toHaveBeenCalled();
    expect(JSON.stringify(tree.toJSON())).toContain('management.importErrors.IDENTITY_DUPLICATE');
    expect(JSON.stringify(tree.toJSON())).toContain('management.importNoValid');
    act(()=>tree.unmount());
  });
});
