"use strict";
// Bearer tokens stay in memory; no storage or authentication cookies.
let accessToken = null, currentUser = null, userPage = 0, userSearch = "";
let selectedFarm = null, memberships = [];
const el = id => document.getElementById(id);
const has = permission => currentUser?.permissions.includes(permission);
const transferPermissions=['plots:read','plots:create','crops:read','crops:create','materials:read','materials:create','warehouses:read','warehouses:create','inventory:manage','expenses:read','expenses:create','usage:read','usage:create'];
const modulePermissions={
  farm:['farms:read','plots:read','crops:read'],material:['materials:read','warehouses:read'],
  expenses:['expenses:read','expenses:create'],'material-usage':['usage:read','usage:create'],'data-transfer':transferPermissions,
  harvest:['harvests:read'],dashboard:['dashboard:read'],purchase:['suppliers:read','purchases:read'],
  sales:['customers:read','sales:read'],traceability:['farms:read']
};
const message = text => {
  el("message").textContent = text;
  if(text && !document.body.classList.contains('supplemental')) el('prototypeFrame')?.contentWindow?.showToast?.('AgriManage',text,'info');
};
let navigationVersion=0;
function activePage(name) {
  document.querySelectorAll('[data-page]').forEach(button=>{
    if(button.dataset.page===name) button.setAttribute('aria-current','page');else button.removeAttribute('aria-current');
    button.classList.toggle('active',button.dataset.page===name);
  });
  history.replaceState(null,'','#'+name);
}
function signedOut() {
  accessToken = null; currentUser = null;
  navigationVersion++;
  selectedFarm=null; memberships=[]; el("registerPanel").hidden=false; el("farmWorkspace").hidden=true;
  el("loginPanel").hidden = false; el("accountPanel").hidden = true;
  el("managementPanel").replaceChildren(); el("passwordForm").reset();
  document.body.classList.remove("signed-in");
  el('authScreen').hidden=false;el('managementPanel').hidden=true;
  authView(false);
  document.querySelector('.account-settings').open=false;
  document.body.classList.remove('supplemental');el('prototypeFrame').src='/ui/prototypes/login.html';
}
function authView(register) {
  el('registerPanel').open=register;
  el('loginPanel').hidden=register;
  el('authLoginTab').setAttribute('aria-pressed',String(!register));
  el('authRegisterTab').setAttribute('aria-pressed',String(register));
  el('authLoginTab').classList.toggle('active',!register);
  el('authRegisterTab').classList.toggle('active',register);
}
el('authLoginTab').onclick=()=>authView(false);
el('authRegisterTab').onclick=()=>authView(true);
el('registerPanel').addEventListener('toggle',()=>{
  if(!currentUser) authView(el('registerPanel').open);
});
function renderAccount(user) {
  currentUser = user;
  el("registerPanel").hidden=true;
  const admin=user.systemRoles.includes("SYSTEM_ADMIN");
  el("workspaceType").textContent=admin?"QUẢN TRỊ HỆ THỐNG":"KHÔNG GIAN TRANG TRẠI";
  el("welcome").textContent = "Xin chào, " + user.fullName;
  el("accountName").textContent = user.username;
  el("accountRoles").textContent = user.systemRoles.join(", ");
  el("loginPanel").hidden = true; el("accountPanel").hidden = false;
  document.body.classList.add("signed-in");
  el('authScreen').hidden=true;el('managementPanel').hidden=false;
  el("farmButton").hidden = !["farms:read", "plots:read", "crops:read"].some(has);
  el("materialButton").hidden = !["materials:read", "warehouses:read"].some(has);
  el('transferButton').hidden = !transferPermissions.some(has) || !selectedFarm || selectedFarm.onboardingStatus!=='READY';
  document.querySelectorAll("[data-permission]").forEach(node => { node.hidden = !has(node.dataset.permission); });
  if(selectedFarm && selectedFarm.onboardingStatus!=="READY") {
    for(const id of ["farmButton","materialButton","expensesButton","usageButton","transferButton","harvestButton","dashboardButton","purchaseButton","salesButton","traceabilityButton"]) el(id).hidden=true;
  }
}
async function request(path, options = {}) {
  const session = accessToken;
  const farmSession=selectedFarm?.farmId;
  const headers = { ...options.headers };
  if(selectedFarm && path!=='/api/v1/workspace' && !['/api/v1/auth/login','/api/v1/auth/register'].includes(path)) headers['X-Farm-Id']=String(selectedFarm.farmId);
  if (accessToken) headers.Authorization = "Bearer " + accessToken;
  const res = await fetch(path, { ...options, headers, cache: "no-store", credentials: "omit" });
  if (session !== accessToken || farmSession!==selectedFarm?.farmId) throw new Error("Phiên đăng nhập hoặc trang trại đã thay đổi. Vui lòng thử lại.");
  if (!res.ok) {
    const data = await res.json().catch(() => null);
    if (res.status === 401 && path !== "/api/v1/auth/login") signedOut();
    const failure=new Error((data?.error?.message || "Không thể xử lý yêu cầu.") + " Mã: " + (data?.requestId || "—"));
    failure.details=data?.error?.details || [];failure.status=res.status;throw failure;
  }
  if (res.status === 204) return null;
  if (options.responseType==='blob') return {blob:await res.blob(),checksum:res.headers.get('X-Data-SHA256'),rows:res.headers.get('X-Row-Count')};
  if (["application/pdf", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"].some(type => res.headers.get("Content-Type")?.includes(type))) return res.blob();
  if(res.headers.get("Content-Type")?.includes("text/html")) return res.text();
  const payload=await res.json();
  return payload && Object.hasOwn(payload,'data')?payload.data:payload;
}
async function download(path,filename) {
  const result=await request(path,{responseType:'blob'}),url=URL.createObjectURL(result.blob),link=document.createElement('a');
  link.href=url;link.download=filename;document.body.append(link);link.click();link.remove();
  setTimeout(()=>URL.revokeObjectURL(url),10000);
}
const json = (path, method, body) => request(path, { method, headers: { "Content-Type": "application/json" }, body: JSON.stringify(body) });
async function run(action, button) {
  if (button) button.disabled = true;
  message("");
  try { await action(); } catch (error) { message(error instanceof TypeError ? "Không kết nối được máy chủ. Vui lòng thử lại." : error.message); }
  finally { if (button) button.disabled = false; }
}
function formHandler(form, action) {
  form.addEventListener("submit", event => {
    event.preventDefault(); run(() => action(Object.fromEntries(new FormData(form))), event.submitter);
  });
}
function node(tag, text) {
  const result = document.createElement(tag);
  if (text !== undefined) result.textContent = text;
  return result;
}
function field(form, title, name, value, type = "text", max = 100) {
  const label = node("label", title), input = node("input");
  input.name = name; input.type = type; input.value = value; input.required = true; input.maxLength = max;
  if (type === "password") { input.minLength = 12; input.autocomplete = "new-password"; }
  label.append(input); form.append(label); return input;
}
function button(parent, title, action) {
  const result = node("button", title); result.type = "button";
  result.addEventListener("click", () => run(action, result)); parent.append(result); return result;
}
function saveForm(parent, title, setup, action) {
  const form = node("form"); setup(form);
  const submit = node("button", title); submit.type = "submit"; form.append(submit);
  formHandler(form, action); parent.append(form);
}
async function refreshMe() { renderAccount((await request("/api/v1/auth/me")).user); }
async function loadWorkspace() {
  memberships=(await request('/api/v1/workspace')).filter(m=>m.status==='ACTIVE' && m.farmStatus==='ACTIVE');
  selectedFarm=memberships.find(m=>m.farmId===selectedFarm?.farmId) || memberships[0] || null;
  el('farmSelector').replaceChildren();
  for(const farm of memberships) { const option=node('option',farm.farmName); option.value=farm.farmId; option.selected=farm===selectedFarm; el('farmSelector').append(option); }
  el('farmWorkspace').hidden=!selectedFarm;
  const owner=selectedFarm?.roles.includes('OWNER');
  el('staffButton').hidden=!owner;
  el('completeButton').hidden=!owner || selectedFarm.onboardingStatus==='READY';
  el('onboardingMessage').textContent=!selectedFarm?'': selectedFarm.onboardingStatus==='READY'
    ? 'Trang trại: '+selectedFarm.farmName+' · Vị trí: '+selectedFarm.roles.map(role=>role==='OWNER'?'Chủ trang trại':jobs[role]||role).join(', ')
    : 'Bước 2: Chủ trang trại thêm nhân viên và phân công vị trí. Sau đó bắt đầu thiết lập khu đất, cây trồng và kho.';
  await refreshMe();
}
async function prototypeView(name,version) {
  const response=await fetch('/ui/prototypes/'+name+'.html',{cache:'no-store',credentials:'omit'});
  if(!response.ok) throw new Error('Không tải được giao diện. Vui lòng thử lại.');
  const html=await response.text();
  if(version!==navigationVersion || !currentUser) return null;
  const template=new DOMParser().parseFromString(html,'text/html').querySelector('main>.content-container');
  if(!template) throw new Error('Giao diện chưa sẵn sàng.');
  const content=document.importNode(template,true);
  el('managementPanel').replaceChildren(content);
  return content;
}
async function renderRealOverview(page='home') {
  const version=++navigationVersion;
  await refreshMe();if(version!==navigationVersion) return;activePage(page);
  const view=await prototypeView(page==='reports'?'reports':'dashboard',version);
  if(!view) return;
  const header=view.querySelector('.page-header');
  header.querySelector('h1').textContent=page==='reports'?'Báo cáo & Thống kê':selectedFarm?selectedFarm.farmName:'Quản trị hệ thống';
  header.querySelector('.subtitle').textContent=selectedFarm
    ? 'Số liệu hiện tại của trang trại. Chọn chức năng trong menu để bắt đầu.'
    : 'Quản lý tài khoản và vai trò của người sử dụng hệ thống.';
  if(selectedFarm && selectedFarm.onboardingStatus!=='READY') {
    header.append(node('p','Thêm nhân viên và phân công vị trí trước khi thiết lập trang trại.'));return;
  }
  const counters=selectedFarm
    ? [['plots','plots:read','Khu đất'],['crops','crops:read','Loại cây trồng'],['seasons','crops:read','Mùa vụ'],['warehouses','warehouses:read','Kho'],['materials','materials:read','Vật tư'],['expenses','expenses:read','Khoản chi']]
    : [['users','users:read','Người dùng'],['roles','roles:read','Vai trò hệ thống']];
  const grid=view.querySelector('#overviewStats');
  const results=await Promise.allSettled(counters.filter(item=>has(item[1])).map(async ([resource,permission,title])=>{
    const data=await request('/api/v1/'+resource+(resource==='roles'?'':'?page=0&size=1'));
    return [title,Array.isArray(data)?data.length:data.total];
  }));
  if(version!==navigationVersion || !currentUser) return;
  for(const result of results) {
    const card=node('article');card.className='stat-card shadow-sm';
    if(result.status==='fulfilled') {
      const icon=node('span',{'Khu đất':'▧','Loại cây trồng':'♧','Mùa vụ':'☀','Kho':'▤','Vật tư':'◈','Khoản chi':'₫','Người dùng':'♙','Vai trò hệ thống':'✓'}[result.value[0]]||'◇');
      icon.className='overview-icon';icon.setAttribute('aria-hidden','true');
      const info=node('div');info.append(node('h3',result.value[0]),node('strong',String(result.value[1])));
      card.append(icon,info);
    }
    else card.append(node('p','Không tải được một mục thống kê. Vui lòng thử lại.'));
    grid.append(card);
  }
  if (version!==navigationVersion || !currentUser) return;
  await window.OverviewCharts.render({panel:view.querySelector('#overviewCharts'),counts:results.filter(result=>result.status==='fulfilled').map(result=>result.value),
    incomplete:results.some(result=>result.status==='rejected'),
    request,has,farm:selectedFarm,isCurrent:()=>version===navigationVersion && !!currentUser,retry:()=>run(()=>openHome(page))});
}
async function showOriginalPrototype(name,module=null) {
  const version=++navigationVersion;
  await refreshMe();if(version!==navigationVersion) return;
  document.body.classList.remove('supplemental');
  el('managementPanel').replaceChildren();
  const page={dashboard:'home',index:'tasks'}[name]||name;
  activePage(module||page);
  el('prototypeFrame').src='/ui/prototypes/'+name+'.html'+(module?'#module-'+module:'');
}
async function openHome() { return showOriginalPrototype('dashboard'); }
el("showPassword").addEventListener("change", event => { el("password").type = event.target.checked ? "text" : "password"; });
async function authenticate(data) {
  const result = await json("/api/v1/auth/login", "POST", { username: data.username.trim(), password: data.password });
  accessToken = result.accessToken; el("loginForm").reset(); el("password").type = "password";
  renderAccount(result.user); message("Đăng nhập thành công.");
  await loadWorkspace();
  const module = location.hash.slice(1);
  if (selectedFarm?.onboardingStatus==='READY' && Object.hasOwn(modulePermissions,module)) await openModule(module);
  else if(module==='staff' && selectedFarm?.roles.includes('OWNER')) await openStaff();
  else if(['users','roles'].includes(module) && has(module+':read')) await openPage(module);
  else if(['activities','tasks','schedule','reports','notifications','workers'].includes(module)) await openPrototype(module);
  else await openHome();
}
formHandler(el("loginForm"), authenticate);
formHandler(el("passwordForm"), async data => {
  await json("/api/v1/auth/password", "POST", data); signedOut(); message("Đã đổi mật khẩu. Vui lòng đăng nhập lại.");
});
el("checkButton").onclick = () => run(()=>openHome(),el('checkButton'));
el("logoutButton").onclick = () => run(async () => { await request("/api/v1/auth/logout", { method: "POST" }); signedOut(); message("Đã đăng xuất."); }, el("logoutButton"));
el("usersButton").onclick = () => run(() => openPage("users"), el("usersButton"));
el("rolesButton").onclick = () => run(() => openPage("roles"), el("rolesButton"));
async function openModule(name) {
  if (!Object.hasOwn(modulePermissions,name)) return;
  if(!selectedFarm || selectedFarm.onboardingStatus!=='READY') throw new Error('Thêm nhân viên và phân công vị trí trước khi thiết lập trang trại.');
  const version=++navigationVersion;
  await refreshMe();if(version!==navigationVersion) return;
  const required = modulePermissions[name];
  if (!required.some(has)) throw new Error("Bạn không có quyền xem mục này.");
  await showOriginalPrototype('dashboard',name);
}
async function openPrototype(name) {
  if(!['activities','tasks','schedule','reports','notifications','workers'].includes(name)) return;
  return showOriginalPrototype(name==='tasks'?'index':name);
}
function navigate(name) {
  return run(()=>{
    if(!currentUser) {signedOut();return;}
    if(name==='staff' || ['users','roles'].includes(name)) document.body.classList.add('supplemental');
    return name==='staff'?openStaff():name==='home'?openHome():['users','roles'].includes(name)?openPage(name):['activities','tasks','schedule','reports','notifications','workers'].includes(name)?openPrototype(name):openModule(name);
  });
}
window.ManagementApi = Object.freeze({ has, request, json, download, navigate,authenticate,
  profile:()=>({user:currentUser,farm:selectedFarm,farms:memberships}),
  register:data=>json('/api/v1/auth/register','POST',data),
  logout:async()=>{await request('/api/v1/auth/logout',{method:'POST'});signedOut();},
  selectFarm:async id=>{selectedFarm=memberships.find(f=>f.farmId===id)||null;navigationVersion++;await loadWorkspace();await openHome();}
});
document.querySelector('.workspace-brand').addEventListener('click',event=>{
  event.preventDefault();navigate('home');
});
el("farmButton").onclick = () => run(() => openModule("farm"), el("farmButton"));
el("materialButton").onclick = () => run(() => openModule("material"), el("materialButton"));
el('expensesButton').onclick=()=>run(()=>openModule('expenses'));
el('usageButton').onclick=()=>run(()=>openModule('material-usage'));
el('transferButton').onclick=()=>run(()=>openModule('data-transfer'));
for(const name of ['purchase','sales','traceability']) el(name+'Button').onclick=()=>run(()=>openModule(name));
formHandler(el('registerForm'),async data=>{
  await json('/api/v1/auth/register','POST',data);el('registerForm').reset();el('registerPanel').open=false;
  el('username').value=data.username;el('password').focus();message('Đăng ký thành công. Đăng nhập để thêm nhân viên và phân quyền.');
});
el('farmSelector').onchange=()=>run(async()=>{
  navigationVersion++;selectedFarm=memberships.find(m=>m.farmId===Number(el('farmSelector').value));el('managementPanel').replaceChildren();await loadWorkspace();
  if(selectedFarm?.roles.includes('OWNER') && selectedFarm.onboardingStatus!=='READY') await openStaff();else await openHome();
});
el('staffButton').onclick=()=>run(openStaff);
document.querySelectorAll('[data-page]').forEach(button=>{
  if(['activities','tasks','schedule','reports','notifications'].includes(button.dataset.page)) button.onclick=()=>navigate(button.dataset.page);
});
el('completeButton').onclick=()=>run(async()=>{
  await request(`/api/v1/workspace/${selectedFarm.farmId}/complete`,{method:'POST'});await loadWorkspace();await openModule('farm');message('Bước 3: Thiết lập khu đất, cây trồng và mùa vụ.');
});

el("loginButton").disabled = false;

el("harvestButton").onclick = () => run(() => openModule("harvest"), el("harvestButton"));

el("dashboardButton").onclick = () => run(() => openModule("dashboard"), el("dashboardButton"));
