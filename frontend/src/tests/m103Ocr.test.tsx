import { act, create, ReactTestRenderer } from 'react-test-renderer';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { AssetImportModal } from '@/features/assets/components/AssetImportModal';
import { importApi } from '@/features/assets/api/importApi';
import { receivingApi } from '@/features/receiving/api/receivingApi';
import en from '@/shared/i18n/locales/en/imports.json';
import vn from '@/shared/i18n/locales/vi/imports.json';

const client=vi.hoisted(()=>vi.fn());
vi.mock('@/shared/api/httpClient',async original=>({...await original<typeof import('@/shared/api/httpClient')>(),httpClient:client}));
vi.mock('react-i18next',()=>({useTranslation:()=>({t:(key:string)=>key})}));
const preview={fileName:'list.pdf',totalRows:1,validRows:1,invalidRows:0,duplicateRows:0,rows:[{rowNumber:2,validationStatus:'VALID',errors:[],rawData:{rowNumber:2,name:'Laptop',type:'LAPTOP',condition:'NEW',sourcePage:1,sourceText:'Laptop | LAPTOP | NEW'}}]};
function button(tree:ReactTestRenderer,key:string) {return tree.root.findAllByType('button').find(node=>JSON.stringify(node.children.map(child=>typeof child==='string'?child:child.props.children)).includes(key))!;}
describe('M10.3 OCR preview',()=>{
  beforeEach(()=>client.mockReset());
  it('routes PDF separately and keeps confirmation on existing receiving protocol',async()=>{
    const file=new File(['pdf'],'LIST.PDF');
    await importApi.preview(file);expect(client.mock.calls[0][0]).toBe('/v1/asset-imports/pdf/preview');
    await receivingApi.previewExcel(file);expect(client.mock.calls[1][0]).toBe('/v1/import-drafts/pdf/preview');
    await receivingApi.confirmExcel(3,7,{fileName:file.name,requestId:'stable',validRows:[]});
    expect(client.mock.calls[2][0]).toBe('/v1/import-drafts/3/excel/confirm?expectedVersion=7');
  });
  it('never confirms automatically and blocks edited rows until server revalidation',async()=>{
    client.mockResolvedValue({success:true,data:preview});
    let tree!:ReactTestRenderer;
    act(()=>{tree=create(<AssetImportModal isOpen onClose={()=>{}} onImportSuccess={()=>{}}/>);});
    act(()=>tree.root.findByProps({type:'file'}).props.onChange({target:{files:[new File(['pdf'],'list.pdf')]}}));
    await act(async()=>button(tree,'imports:upload.btnPreview').props.onClick());
    expect(client).toHaveBeenCalledTimes(1);
    expect(button(tree,'imports:preview.btnConfirm').props.disabled).toBe(false);
    act(()=>button(tree,'imports:ocr.edit').props.onClick());
    const name=tree.root.findAllByType('label').find(node=>node.children.includes('imports:fields.name'))!.findByType('input');
    act(()=>name.props.onChange({target:{value:''}}));
    expect(button(tree,'imports:preview.btnConfirm').props.disabled).toBe(true);
    client.mockResolvedValueOnce({success:true,data:{...preview,validRows:0,invalidRows:1,rows:[{...preview.rows[0],validationStatus:'INVALID',rawData:{...preview.rows[0].rawData,name:''},errorMessage:'Required',errors:[{column:'name',message:'Required'}]}]}});
    await act(async()=>button(tree,'imports:ocr.validate').props.onClick());
    expect(client.mock.calls[1][0]).toBe('/v1/asset-imports/validate');
    expect(JSON.parse(client.mock.calls[1][1].body).validRows[0].name).toBe('');
    expect(button(tree,'imports:preview.btnConfirm').props.disabled).toBe(true);
    expect(JSON.stringify(tree.toJSON())).toContain('Required');
    act(()=>tree.unmount());
  });
  it('provides the same editable fields and local processing guidance in both languages',()=>{
    expect(Object.keys(en.fields).sort()).toEqual(Object.keys(vn.fields).sort());
    expect(Object.keys(en.fields)).toHaveLength(23);
    expect(en.ocr.hint).toContain('locally');expect(vn.ocr.hint).toContain('nội bộ');
  });
});
