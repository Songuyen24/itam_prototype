import { act, create, type ReactTestRenderer } from 'react-test-renderer';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
const mocks=vi.hoisted(()=>({template:vi.fn(),preview:vi.fn(),confirm:vi.fn(),done:vi.fn(),close:vi.fn()}));
vi.mock('react-i18next',()=>{const t=(key:string)=>key;return{useTranslation:()=>({t})};});
vi.mock('@/features/catalogs/api/catalogImportApi',()=>({importableCatalogs:['departments','locations','suppliers','types','models','software'],catalogImportApi:{template:mocks.template,preview:mocks.preview,confirm:mocks.confirm}}));
import { CatalogImportModal } from '@/features/catalogs/components/CatalogImportModal';
let tree:ReactTestRenderer|null=null;
const button=(label:string)=>tree!.root.findAllByType('button').find(item=>item.children.includes(label))!;
describe('catalog import modal',()=>{
  beforeEach(()=>{Object.values(mocks).forEach(mock=>mock.mockReset());mocks.preview.mockResolvedValue({data:{columns:['code / Mã','name / Tên','address / Địa chỉ'],rows:[{row:2,values:['001','Office','Street'],status:'VALID',errors:[]}],valid:1,invalid:0,created:0}});mocks.confirm.mockResolvedValue({data:{columns:['code / Mã','name / Tên','address / Địa chỉ'],rows:[{row:2,values:['001','Office','Street'],status:'CREATED',errors:[]}],valid:1,invalid:0,created:1}});});
  afterEach(()=>{if(tree)act(()=>tree!.unmount());tree=null;});
  it('uses selected kind, retains leading-zero data and uploads only after Preview',async()=>{
    await act(async()=>{tree=create(<CatalogImportModal kind="locations" onClose={mocks.close} onImported={mocks.done}/>);});
    const file=new File(['x'],'locations.xlsx');
    await act(async()=>tree!.root.findByType('input').props.onChange({target:{files:[file]}}));
    expect(mocks.preview).not.toHaveBeenCalled();expect(button('import.confirm')).toBeUndefined();
    await act(async()=>button('import.preview').props.onClick());
    expect(mocks.preview).toHaveBeenCalledWith('locations',file);
    expect(JSON.stringify(tree!.toJSON())).toContain('001');expect(JSON.stringify(tree!.toJSON())).toContain('Street');
    await act(async()=>button('import.confirm').props.onClick());
    expect(mocks.confirm).toHaveBeenCalledWith('locations',file);expect(mocks.done).toHaveBeenCalledOnce();
    await act(async()=>button('import.template').props.onClick());expect(mocks.template).toHaveBeenCalledWith('locations');
  });
  it('clears preview on file change and hides Confirm for invalid-only rows',async()=>{
    mocks.preview.mockResolvedValueOnce({data:{columns:['code / Mã'],rows:[{row:2,values:['001'],status:'INVALID',errors:['ALREADY_EXISTS']}],valid:0,invalid:1,created:0}});
    await act(async()=>{tree=create(<CatalogImportModal kind="departments" onClose={mocks.close} onImported={mocks.done}/>);});
    await act(async()=>tree!.root.findByType('input').props.onChange({target:{files:[new File(['x'],'a.xlsx')]}}));
    await act(async()=>button('import.preview').props.onClick());
    expect(button('import.confirm')).toBeUndefined();expect(mocks.confirm).not.toHaveBeenCalled();
    await act(async()=>tree!.root.findByType('input').props.onChange({target:{files:[new File(['y'],'b.xlsx')]}}));
    expect(JSON.stringify(tree!.toJSON())).not.toContain('ALREADY_EXISTS');
  });
});
