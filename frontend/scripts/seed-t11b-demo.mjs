// Optional local demo fixture. Uses the regular authorized APIs and is repeatable.
// ITAM_DEMO_PASSWORD must be supplied for an existing local Admin/IT test account.
const base = process.env.ITAM_API_URL || 'http://127.0.0.1:8080/api/v1';
const password = process.env.ITAM_DEMO_PASSWORD;
if (!password) throw new Error('Set ITAM_DEMO_PASSWORD for an existing local demo account.');
let token;
async function api(path, method='GET', data) {
  const response=await fetch(base+path,{method,headers:{'Content-Type':'application/json',...(token?{Authorization:`Bearer ${token}`}:{})},body:data?JSON.stringify(data):undefined});
  const result=await response.json();
  if (!response.ok) throw new Error(`${method} ${path}: ${response.status} ${result.code || ''}`);
  return result.data;
}
const login=await api('/auth/login','POST',{email:process.env.ITAM_DEMO_EMAIL || 'admin@itam.example',password});
token=login.accessToken || login.token;
if (!token) throw new Error('Login did not return a token.');
const categories=(await api('/asset-categories?size=100')).content;
const types=(await api('/asset-types?size=100')).content;
async function assetType(category) {
  const categoryId=categories.find(x=>x.code===category).categoryId;
  const preferredCode={DEVICE:'LAPTOP',COMPONENT:'RAM',LICENSE:'SOFTWARE_LICENSE'}[category];
  const existing=types.find(x=>x.code===preferredCode && x.categoryId===categoryId);
  return existing?.typeId || (await api('/asset-types','POST',{code:`T11B_DEMO_${category}`,name:`Demo ${category}`,categoryId,isActive:true})).typeId;
}
const softwareList=(await api('/software-catalog?size=100')).content;
let software=softwareList.find(x=>x.name==='T11B Fictional Software');
if(!software)software=await api('/software-catalog','POST',{name:'T11B Fictional Software',manufacturer:'Fictional vendor',version:'Demo',isActive:true});
const assignmentTypes=(await api('/license-assignment-types?size=100')).content;
const terms=(await api('/license-term-types?size=100')).content;
const results=[];
for(const [suffix,category,name,assignment] of [
  ['LAPTOP','DEVICE','T11B Demo Laptop'],['RAM','COMPONENT','T11B Demo RAM 16GB'],
  ['OEM','LICENSE','T11B Demo OEM 10 seats','OEM'],['PERUSER','LICENSE','T11B Demo Per-User 10 seats','PER_USER']
]) {
  const assetTag=`T11B-DEMO-${suffix}`;
  let asset=(await api(`/assets?assetTag=${assetTag}&size=100`)).content.find(x=>x.assetTag===assetTag);
  if(!asset)asset=await api('/assets','POST',{assetTag,name,typeId:await assetType(category),
    ...(assignment?{license:{softwareCatalogId:software.softwareCatalogId,assignmentTypeId:assignmentTypes.find(x=>x.code===assignment).id,
      termTypeId:terms.find(x=>x.code==='PERPETUAL').id,seatCount:10,licenseKey:'FICTIONAL-DEMO-ONLY'}}:{})});
  results.push(asset);
}
const parent=results[0];const existing=(await api(`/assets/${parent.assetId}/relationships?size=100`)).content;
for(const child of results.slice(1,3)) if(!existing.some(r=>r.child.assetId===child.assetId))
  await api(`/assets/${parent.assetId}/relationships`,'POST',{childAssetId:child.assetId,type:child.categoryCode==='COMPONENT'?'COMPONENT_OF':'INSTALLED_ON'});
console.log(JSON.stringify(results.map(({assetId,assetTag})=>({assetId,assetTag})),null,2));
