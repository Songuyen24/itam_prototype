import { act, create, type ReactTestRenderer } from 'react-test-renderer';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { AdjustmentPanel } from '@/features/documents/components/AdjustmentPanel';
const api=vi.hoisted(()=>({list:vi.fn(),decide:vi.fn(),create:vi.fn(),download:vi.fn()}));
vi.mock('@/features/documents/api/adjustmentApi',()=>({adjustmentApi:api}));
const t=(key:string)=>key;
vi.mock('react-i18next',()=>({useTranslation:()=>({t})}));
vi.mock('react-router-dom',()=>({Link:({children}:{children:React.ReactNode})=><span>{children}</span>}));
let tree:ReactTestRenderer;
const request={id:1,version:3,kind:'DESCRIPTION',status:'PENDING',requesterName:'Creator',createdAt:'2026-01-01',reason:'Correction',proposedText:'New text',files:[]};
const overview={canRequest:false,total:11,requests:[request],subsequentTransactions:[{transactionId:4,transactionCode:'NEXT',type:'RECOVERY',status:'COMPLETED'}]};
beforeEach(()=>{vi.clearAllMocks();api.list.mockResolvedValue({data:overview});api.decide.mockResolvedValue({data:request});});
afterEach(()=>{if(tree)act(()=>tree.unmount());});
describe('Completed adjustments',()=>{
  it('requires a decision reason and sends the exact version and reviewed impacts',async()=>{
    await act(async()=>{tree=create(<AdjustmentPanel transactionId={10} isAdmin/>);});
    let approve=tree.root.findAllByType('button').find(b=>b.children.includes('adjustment.approve'))!;
    expect(approve.props.disabled).toBe(true);
    await act(async()=>tree.root.findByType('textarea').props.onChange({target:{value:'Reviewed'}}));
    approve=tree.root.findAllByType('button').find(b=>b.children.includes('adjustment.approve'))!;
    await act(async()=>approve.props.onClick());
    expect(api.decide).toHaveBeenCalledWith(10,request,true,'Reviewed',overview.subsequentTransactions);
  });
  it('hides unauthorized actions and pages on server',async()=>{
    await act(async()=>{tree=create(<AdjustmentPanel transactionId={10} isAdmin={false}/>);});
    expect(tree.root.findAllByType('form')).toHaveLength(0);
    expect(tree.root.findAllByType('textarea')).toHaveLength(0);
    await act(async()=>tree.root.findAllByType('button').find(b=>b.children.includes('→'))!.props.onClick());
    expect(api.list).toHaveBeenLastCalledWith(10,1);
  });
  it('recovers initial load failure with Retry',async()=>{
    api.list.mockRejectedValueOnce(new Error('Denied'));
    await act(async()=>{tree=create(<AdjustmentPanel transactionId={10} isAdmin={false}/>);});
    expect(tree.root.findByProps({role:'alert'}).children).toContain('Denied');
    await act(async()=>tree.root.findByType('button').props.onClick());
    expect(tree.root.findAllByProps({role:'alert'})).toHaveLength(0);
  });
});
