"use strict";
const adminRoleName=name=>({SYSTEM_ADMIN:'Quản trị viên hệ thống',USER:'Người dùng'}[name]||name);
function adminDenied(){
  const main=el('managementPanel');main.replaceChildren();
  const panel=node('section');panel.className='admin-denied';panel.id='adminDenied';
  panel.append(node('h1','Bạn chưa có quyền quản trị'),node('p','Tài khoản này chưa được cấp quyền truy cập trang quản trị hệ thống. Hãy liên hệ người quản trị để được cấp quyền.'));
  button(panel,'Về trang tài khoản',()=>goAccount('overview'),'primary');main.append(panel);
}
async function openPage(name){
  const version=++navigationVersion;selectedFarm=null;accountPage='admin';setMenu(false);
  const main=el('managementPanel');main.hidden=false;main.style.flex='';main.style.paddingBottom='';
  el('featureFrame').hidden=true;el('featureFrame').src='about:blank';
  main.replaceChildren(node('p','Đang tải trang quản trị…'));
  await refreshMe();if(version!==navigationVersion)return;
  name=['users','roles'].includes(name)?name:has('users:read')?'users':'roles';
  if(location.hash!=='#/admin/'+name)history.pushState(null,'','#/admin/'+name);
  renderNavigation();activePage('admin');
  if(!has(name+':read')){adminDenied();return;}
  const markup=await request('/admin/'+name);if(version!==navigationVersion)return;
  main.replaceChildren();heading(name==='users'?'Quản lý người dùng':'Vai trò & Quyền truy cập','Quản lý tài khoản và quyền hệ thống trên toàn ứng dụng.','QUẢN TRỊ HỆ THỐNG');
  const tabs=node('nav');tabs.className='admin-tabs';tabs.setAttribute('aria-label','Chức năng quản trị');
  for(const[id,title]of [['users','Người dùng'],['roles','Vai trò & Quyền']])if(has(id+':read')){
    const b=button(tabs,title,()=>openPage(id));b.dataset.adminPage=id;b.classList.toggle('active',name===id);if(name===id)b.setAttribute('aria-current','page');
  }
  main.append(tabs);
  // Only server-owned fragments are parsed as HTML; account data uses textContent.
  const fragment=new DOMParser().parseFromString(markup,'text/html');main.append(...fragment.body.childNodes);
  if(name==='users'){
    userPage=0;userSearch='';
    formHandler(el('searchUsers'),async d=>{userSearch=d.search.trim();userPage=0;await loadUsers();});
    el('previousUsers').onclick=()=>run(async()=>{userPage=Math.max(0,userPage-1);await loadUsers();});
    el('nextUsers').onclick=()=>run(async()=>{userPage++;await loadUsers();});
    el('refreshUsers').onclick=()=>run(()=>openPage('users'));
    const create=el('createUserButton');create.hidden=!has('users:create');create.onclick=()=>createAdminUser();
    await loadUsers();
  }else{
    await loadRoles();
  }
}
async function loadUsers(){
  const version=navigationVersion,list=el('userList');if(!list)return;
  list.setAttribute('aria-busy','true');
  try{
    const data=await request(`/api/v1/users?page=${userPage}&size=10&search=${encodeURIComponent(userSearch)}`);
    if(version!==navigationVersion||!list.isConnected)return;
    if(!data.items.length&&userPage>0){userPage--;return loadUsers();}
    list.replaceChildren();el('adminUserSummary').textContent=`${data.total} tài khoản${userSearch?' phù hợp tìm kiếm':''}. Quyền quản trị được kiểm tra lại từ hệ thống khi thực hiện thao tác.`;
    el('userPage').textContent=`Trang ${userPage+1} / ${Math.max(1,Math.ceil(data.total/10))}`;
    el('previousUsers').disabled=userPage===0;el('nextUsers').disabled=(userPage+1)*10>=data.total;
    table(list,[[u=>{const n=node('div');n.className='admin-person';n.append(node('strong',u.fullName),node('small','@'+u.username+(u.id===currentUser.id?' · Bạn':'')));return n;},'Người dùng'],['email','Email'],[u=>{const b=badge(u.status==='ACTIVE'?'Đang hoạt động':'Đã khóa');b.classList.add('admin-status');if(u.status!=='ACTIVE')b.classList.add('inactive');return b;},'Trạng thái'],[u=>u.systemRoles.map(adminRoleName).join(', '),'Vai trò hệ thống']],data.items,(u,td)=>{
      td.dataset.userId=u.id;const actions=node('div');actions.className='admin-actions';td.append(actions);
      if(has('users:update'))button(actions,'Chỉnh sửa',()=>editAdminUser(u));
      if(has('users:assign')&&has('roles:read'))button(actions,'Phân quyền',()=>assignAdminRoles(u));
      if(u.id!==currentUser.id){
        if(has('users:status'))button(actions,u.status==='ACTIVE'?'Khóa':'Mở khóa',()=>confirmAdminStatus(u));
        if(has('users:password'))button(actions,'Đặt lại mật khẩu',()=>resetAdminPassword(u));
      }
    });
  }finally{if(list.isConnected)list.removeAttribute('aria-busy');}
}
function createAdminUser(){
  const panel=modal('Thêm người dùng');
  saveForm(panel,'Tạo tài khoản',f=>{
    field(f,'Họ và tên','fullName','','text',100);field(f,'Tên đăng nhập','username','','text',50).pattern='[a-z0-9._-]{3,50}';
    field(f,'Email','email','','email',254);field(f,'Mật khẩu ban đầu (ít nhất 12 ký tự, gồm chữ và số)','password','','password',128);
  },async values=>{await json('/api/v1/users','POST',values);closeModal();userPage=0;await loadUsers();message('Đã tạo tài khoản với vai trò Người dùng.');});
}
function editAdminUser(u){
  const panel=modal('Chỉnh sửa · @'+u.username);
  saveForm(panel,'Lưu thông tin',f=>{field(f,'Họ và tên','fullName',u.fullName);field(f,'Email','email',u.email,'email',254);},async values=>{
    await json('/api/v1/users/'+u.id,'PUT',values);closeModal();await refreshMe();await loadUsers();message('Đã cập nhật thông tin tài khoản.');
  });
}
function confirmAdminStatus(u){
  const inactive=u.status==='ACTIVE',panel=modal((inactive?'Khóa':'Mở khóa')+' · @'+u.username);
  const note=node('p',inactive?'Tài khoản sẽ không thể đăng nhập và mọi phiên hiện tại sẽ bị thu hồi.':'Tài khoản có thể đăng nhập lại bằng mật khẩu hiện có.');note.className='admin-confirm';panel.append(note);
  button(panel,inactive?'Xác nhận khóa tài khoản':'Xác nhận mở khóa',async()=>{
    await json('/api/v1/users/'+u.id+'/status','PUT',{status:inactive?'INACTIVE':'ACTIVE'});closeModal();await loadUsers();message(inactive?'Đã khóa tài khoản và thu hồi các phiên đăng nhập.':'Đã mở khóa tài khoản.');
  },'primary');
}
function resetAdminPassword(u){
  const panel=modal('Đặt lại mật khẩu · @'+u.username);panel.append(node('p','Mọi phiên đăng nhập của tài khoản sẽ bị thu hồi sau khi đặt lại mật khẩu.'));
  saveForm(panel,'Đặt lại mật khẩu',f=>field(f,'Mật khẩu mới (ít nhất 12 ký tự, gồm chữ và số)','newPassword','','password',128),async values=>{
    await json('/api/v1/users/'+u.id+'/password','PUT',values);closeModal();message('Đã đặt lại mật khẩu và thu hồi các phiên đăng nhập.');
  });
}
async function assignAdminRoles(u){
  const roles=await request('/api/v1/roles'),panel=modal('Phân quyền · @'+u.username);
  panel.append(node('p','Quản trị viên hệ thống có toàn quyền quản lý tài khoản. Thay đổi có hiệu lực với cả phiên đăng nhập đang hoạt động.'));
  const form=node('form'),group=node('fieldset');group.className='admin-permissions';group.append(node('legend','Vai trò hệ thống'));
  for(const role of roles){
    const l=node('label'),input=node('input'),copy=node('span');input.type='checkbox';input.value=role.id;input.checked=u.systemRoles.includes(role.name);
    copy.append(node('strong',adminRoleName(role.name)),node('small',role.description||role.name));l.append(input,copy);group.append(l);
  }
  form.append(group,node('button','Lưu phân quyền'));panel.append(form);
  formHandler(form,async()=>{
    const roleIds=[...group.querySelectorAll('input:checked')].map(i=>Number(i.value));if(!roleIds.length)throw Error('Chọn ít nhất một vai trò cho tài khoản.');
    await json('/api/v1/users/'+u.id+'/roles','PUT',{roleIds});closeModal();await refreshMe();
    if(has('users:read'))await loadUsers();else adminDenied();message('Đã cập nhật vai trò hệ thống.');
  });
}
function editAdminRole(role){
  const panel=modal(role?'Chỉnh sửa vai trò':'Thêm vai trò');
  saveForm(panel,'Lưu vai trò',f=>{field(f,'Tên vai trò','name',role?.name||'','text',50).pattern='[A-Z][A-Z0-9_]{1,49}';field(f,'Mô tả','description',role?.description||'','text',255);},async values=>{
    await json('/api/v1/roles'+(role?'/'+role.id:''),role?'PUT':'POST',values);closeModal();await refreshMe();await loadRoles();message('Đã lưu vai trò.');
  });
}
async function loadRoles(){
  const version=navigationVersion,list=el('roleList');if(!list)return;
  const [roles,permissions]=await Promise.all([request('/api/v1/roles'),request('/api/v1/permissions')]);if(version!==navigationVersion||!list.isConnected)return;
  list.replaceChildren();
  for(const role of roles){
    const card=node('article');card.className='admin-role-card';card.append(node('h2',adminRoleName(role.name)),node('p',role.description),node('small',role.permissions.length+' quyền hệ thống'));
    if(has('roles:write')&&!['SYSTEM_ADMIN','USER'].includes(role.name))button(card,'Chỉnh sửa',()=>editAdminRole(role));
    if(has('roles:permissions')&&role.name!=='SYSTEM_ADMIN')button(card,'Chỉnh quyền truy cập',()=>{
      const panel=modal('Quyền truy cập · '+adminRoleName(role.name)),form=node('form'),group=node('fieldset');group.className='admin-permissions';group.append(node('legend','Quyền được cấp'));
      for(const p of permissions){const l=node('label'),i=node('input'),copy=node('span');i.type='checkbox';i.value=p.code;i.checked=role.permissions.includes(p.code);copy.append(node('strong',p.code),node('small',p.description));l.append(i,copy);group.append(l);}
      form.append(group,node('button','Lưu quyền truy cập'));panel.append(form);
      formHandler(form,async()=>{await json('/api/v1/roles/'+role.id+'/permissions','PUT',{permissions:[...group.querySelectorAll('input:checked')].map(i=>i.value)});closeModal();await refreshMe();if(has('roles:read'))await loadRoles();else adminDenied();message('Đã cập nhật quyền truy cập.');});
    });else card.append(node('p',role.permissions.join(', ')||'Chưa có quyền quản trị.'));
    list.append(card);
  }
}
