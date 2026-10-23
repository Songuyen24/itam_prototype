import { act, create, type ReactTestRenderer } from 'react-test-renderer';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ReportsPage } from '@/features/reports/ReportsPage';
const client=vi.hoisted(()=>vi.fn()); const download=vi.hoisted(()=>vi.fn());
vi.mock('@/shared/api/httpClient',()=>({httpClient:client,downloadFile:download}));
vi.mock('react-i18next',()=>({useTranslation:()=>({t:(key:string)=>key})}));
vi.mock('@/features/auth/contexts/AuthContext',()=>({useAuth:()=>({user:{role:'IT_STAFF'}})}));
const permissions={ASSETS:{view:true,export:false},TRANSACTIONS:{view:true,export:true},ALLOCATIONS:{view:false,export:false}};
let tree:ReactTestRenderer;
const result=(name:string)=>({data:{items:[{name}],summary:[],total:1}});
beforeEach(()=>{client.mockReset();download.mockReset();client.mockImplementation((url:string)=>Promise.resolve(url.endsWith('/permissions')?{data:permissions}:url.endsWith('/options')?{data:{}}:result('current')));});
afterEach(()=>{if(tree)act(()=>tree.unmount());});
describe('M6 reports',()=>{
 it('disables export without grant and does not query denied group',async()=>{
  await act(async()=>{tree=create(<ReportsPage/>);});
  expect(tree.root.findAllByType('button').find(b=>b.children.includes('export_all_results_to_excel'))?.props.disabled).toBe(true);
  await act(async()=>tree.root.findAllByType('select')[0].props.onChange({target:{value:'ALLOCATIONS'}}));
  expect(client.mock.calls.some(([url])=>String(url).includes('/allocations?'))).toBe(false);
 });
 it('ignores the old group response after switching',async()=>{
  let resolveOld!:(value:unknown)=>void;
  client.mockImplementation((url:string)=>url.endsWith('/permissions')?Promise.resolve({data:permissions}):url.endsWith('/options')?Promise.resolve({data:{}}):url.includes('/assets?')?new Promise(resolve=>{resolveOld=resolve;}):Promise.resolve(result('new transactions')));
  await act(async()=>{tree=create(<ReportsPage/>);});
  await act(async()=>tree.root.findAllByType('select')[0].props.onChange({target:{value:'TRANSACTIONS'}}));
  await act(async()=>resolveOld(result('stale assets')));
  expect(JSON.stringify(tree.toJSON())).toContain('new transactions');expect(JSON.stringify(tree.toJSON())).not.toContain('stale assets');
 });
 it('exports the selected group and active date filter',async()=>{
  await act(async()=>{tree=create(<ReportsPage/>);});
  await act(async()=>tree.root.findAllByType('select')[0].props.onChange({target:{value:'TRANSACTIONS'}}));
  await act(async()=>tree.root.findByProps({'aria-label':'column_from'}).props.onChange({target:{value:'2026-01-02'}}));
  await act(async()=>tree.root.findAllByType('button').find(b=>b.children.includes('export_all_results_to_excel'))!.props.onClick());
  expect(download.mock.calls[0][0]).toContain('/transactions/export?from=2026-01-02');
 });
 it('renders creator and processor names supplied by report',async()=>{
  client.mockImplementation((url:string)=>Promise.resolve(url.endsWith('/permissions')?{data:permissions}:url.endsWith('/options')?{data:{}}:{data:{items:[{requester_id:998,processed_by:999,requester_name:'Creator One',processor_name:'Processor Two'}],summary:[],total:1}}));
  await act(async()=>{tree=create(<ReportsPage/>);});
  await act(async()=>tree.root.findAllByType('select')[0].props.onChange({target:{value:'TRANSACTIONS'}}));
  expect(JSON.stringify(tree.toJSON())).toContain('Creator One');
  expect(JSON.stringify(tree.toJSON())).toContain('Processor Two');
  expect(JSON.stringify(tree.toJSON())).not.toContain('column_requester_id');
  expect(JSON.stringify(tree.toJSON())).not.toContain('column_processed_by');
 });
});
