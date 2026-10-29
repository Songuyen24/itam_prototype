import { act, create } from 'react-test-renderer';
import { beforeEach, expect, it, vi } from 'vitest';
import { DashboardPage } from '@/features/dashboard/pages/DashboardPage';
import { dashboardApi } from '@/features/dashboard/api/dashboardApi';
import en from '@/shared/i18n/locales/en/dashboard.json';
import vn from '@/shared/i18n/locales/vi/dashboard.json';
const client=vi.hoisted(()=>vi.fn());
const translate=(key:string)=>key;
vi.mock('@/shared/api/httpClient',()=>({httpClient:client,downloadFile:vi.fn()}));
vi.mock('react-i18next',()=>({useTranslation:()=>({t:translate,i18n:{language:'en'}})}));
vi.mock('@/features/auth/contexts/AuthContext',()=>({useAuth:()=>({user:{role:'IT_STAFF'}})}));
beforeEach(()=>client.mockReset());
it('sends an explicit custom completion period to the API',async()=>{
  await dashboardApi.summary({period:'custom',from:'2026-10-01',to:'2026-10-26'});
  expect(client).toHaveBeenCalledWith('/v1/dashboard/summary?period=custom&from=2026-10-01&to=2026-10-26');
});
it('shows distinct units, unknown historical seats and a voucher deep link',async()=>{
  client.mockResolvedValue({data:{from:'2026-10-01',to:'2026-10-26',byStatus:{IN_STOCK:5,IN_USE:0},damagedDevices:2,
    inventory:[{category:'LICENSE',count:1,total_seats:10,active_seats:2,reserved_seats:3,available_seats:5}],
    movements:[{type:'IMPORT',transactions:1,devices:0,components:0,packages:1,seats:null,recipients:0}],daily:[{day:'2026-10-12',type:'IMPORT',transactions:1}],
    pending:{IMPORT:2},drafts:{HANDOVER:1},failedEmails:0,failedPdfs:1,
    recent:[{transaction_id:42,transaction_code:'IMP-42',type:'IMPORT',status:'COMPLETED',completed_at:'2026-10-12T08:00:00Z',actor:'IT worker',recipient:'',devices:0,components:0,packages:1,seats:null}]}});
  let page!:ReturnType<typeof create>;
  await act(async()=>{page=create(<DashboardPage/>); await Promise.resolve();});
  expect(page.root.findByType('a').props.href).toBe('/documents?transactionId=42');
  const text=JSON.stringify(page.toJSON());
  for(const label of ['packages','seats','unavailable','reserved_seats','damagedCondition','IT worker']) expect(text).toContain(label);
  expect(page.root.findAllByProps({className:'dashboard-status-track'}).map(track=>track.children[0]).map(bar=>typeof bar==='string' ? '' : bar.props.style.width)).toEqual(['100%','0%']);
  for(const hint of ['description','currentHint','statusHint','periodHint','seatsHint','chartHint','actionsHint','recentHint']) expect(page.root.findAllByType('p').some(p=>p.children.includes(hint))).toBe(false);
  expect(page.root.findAllByType('button').some(b=>b.children.includes('export'))).toBe(false);
  await act(async()=>{page.root.findByProps({className:'btn btn-secondary dashboard-period-trigger'}).props.onClick();});
  expect(client).toHaveBeenCalledTimes(1);
  await act(async()=>{page.root.findByType('select').props.onChange({target:{value:'lastMonth'}});});
  await act(async()=>{page.root.findByType('dialog').findByProps({className:'dashboard-header-actions'}).findAllByType('button')[0].props.onClick();});
  expect(client).toHaveBeenCalledTimes(1);
  await act(async()=>{page.root.findByProps({className:'btn btn-secondary dashboard-period-trigger'}).props.onClick();});
  expect(page.root.findByType('select').props.value).toBe('thisMonth');
  await act(async()=>{page.root.findByType('select').props.onChange({target:{value:'custom'}});});
  const dates=page.root.findAllByType('input').filter(input=>input.props.type==='date');
  await act(async()=>{dates[0].props.onChange({target:{value:'2026-10-01'}});});
  await act(async()=>{dates[1].props.onChange({target:{value:'2026-10-26'}});});
  await act(async()=>{page.root.findByType('form').props.onSubmit({preventDefault:()=>{}});});
  expect(client.mock.calls[client.mock.calls.length - 1]?.[0]).toBe('/v1/dashboard/summary?period=custom&from=2026-10-01&to=2026-10-26');
  expect(page.root.findByProps({className:'btn btn-secondary dashboard-period-trigger'}).children).toContain('2026-10-01 – 2026-10-26');
  act(()=>page.unmount());
});
it('keeps both languages complete for the dashboard',()=>{
  expect(Object.keys(vn).sort()).toEqual(Object.keys(en).sort());
  expect(vn.seatsHint).not.toEqual(en.seatsHint);
});


