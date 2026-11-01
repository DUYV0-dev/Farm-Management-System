"use strict";
const jobs={MANAGER:'Quản lý',AGRONOMIST:'Kỹ sư nông nghiệp',WAREHOUSE:'Nhân viên kho',ACCOUNTANT:'Kế toán',WORKER:'Nhân viên canh tác'};
function jobField(form,value='WORKER') {
  const label=node('label','Vị trí công việc'), select=node('select'); select.name='job';
  for(const [code,title] of Object.entries(jobs)) { const option=node('option',title); option.value=code; option.selected=code===value; select.append(option); }
  label.append(select);form.append(label);
}
async function openStaff() {
  document.body.classList.add('supplemental');
  const version=++navigationVersion;
  const farm=selectedFarm;
  if(!farm) throw new Error('Chọn trang trại trước.');
  const staff=await request(`/api/v1/workspace/${farm.farmId}/staff`);
  if(version!==navigationVersion) return;
  activePage('staff');
  const view=await moduleView('workers',version);if(!view) return;
  view.replaceChildren();
  const header=node('div'),copy=node('div');header.className='staff-header';copy.append(node('span','QUẢN LÝ ĐỘI NGŨ'),node('h1','Nhân sự & Phân quyền'),node('p',farm.farmName+' · Quản lý tài khoản và quyền công việc trong trang trại.'));copy.firstChild.className='eyebrow';header.append(copy);
  button(header,'+ Thêm nhân viên',()=>{
    const panel=modal('Thêm nhân viên');panel.append(node('p','Tạo tài khoản và phân công công việc tại '+farm.farmName+'.'));
    saveForm(panel,'Tạo tài khoản nhân viên',form=>{form.className='form-grid';field(form,'Họ và tên','fullName','');field(form,'Tên đăng nhập','username','','text',50);field(form,'Email','email','','email',254);field(form,'Mật khẩu ban đầu','password','','password',128);jobField(form);},async data=>{await json(`/api/v1/workspace/${farm.farmId}/staff`,'POST',data);closeModal();await openStaff();message('Đã tạo tài khoản nhân viên và cấp quyền theo công việc.');});
  },'primary');view.append(header);
  const panel=node('div');panel.id='staffContent';view.append(panel);
  const stats=node('div');stats.className='staff-stats';
  for(const [title,value] of [['Tổng nhân sự',staff.length],['Đang hoạt động',staff.filter(p=>p.status==='ACTIVE').length],['Ngừng hoạt động',staff.filter(p=>p.status!=='ACTIVE').length]]) {
    const card=node('article');card.className='metric';card.append(node('small',title),node('strong',String(value)));stats.append(card);
  }
  panel.append(stats);
  const wrapper=node('div');wrapper.className='table-wrapper shadow-sm';
  const heading=node('div');heading.className='staff-heading';heading.append(node('h2','Danh sách nhân sự'));
  const search=node('input');search.type='search';search.placeholder='Tìm theo tên, tài khoản hoặc email…';search.setAttribute('aria-label','Tìm nhân viên');search.className='staff-search';heading.append(search);wrapper.append(heading);
  const responsive=node('div');responsive.className='table-responsive';
  const table=node('table');table.className='task-table';const head=node('thead'),headers=node('tr');
  for(const title of ['Nhân viên','Tên đăng nhập','Liên hệ','Vị trí','Trạng thái','Phân quyền']) headers.append(node('th',title));
  head.append(headers);table.append(head);const body=node('tbody');table.append(body);responsive.append(table);wrapper.append(responsive);panel.append(wrapper);
  function render(){body.replaceChildren();const matches=staff.filter(p=>(p.fullName+' '+p.username+' '+p.email).toLocaleLowerCase('vi').includes(search.value.toLocaleLowerCase('vi')));
  for(const person of matches) {
    const row=node('tr');
    const identity=node('td'),personWrap=node('div'),avatar=node('span'),name=node('div');personWrap.className='staff-person';avatar.className='avatar';avatar.textContent=person.fullName.trim().split(/\s+/).slice(-2).map(s=>s[0]).join('');name.append(node('strong',person.fullName),node('small',person.roles.includes('OWNER')?'Chủ sở hữu trang trại':'Nhân viên'));personWrap.append(avatar,name);identity.append(personWrap);row.append(identity);
    for(const value of [person.username,person.email,person.roles.map(r=>jobs[r]||'Chủ trang trại').join(', ')])row.append(node('td',value));const status=node('td');status.append(badge(person.status==='ACTIVE'?'Hoạt động':'Ngừng hoạt động',person.status==='ACTIVE'?'':'CANCELLED'));row.append(status);
    const actions=node('td');row.append(actions);
    if(!person.roles.includes('OWNER')) button(actions,'Phân quyền',()=>{const panel=modal('Phân quyền · '+person.fullName);saveForm(panel,'Lưu thay đổi',form=>{
      jobField(form,person.roles[0]);const label=node('label','Trạng thái'), select=node('select');select.name='status';
      for(const code of ['ACTIVE','INACTIVE']) {const option=node('option',code==='ACTIVE'?'Hoạt động':'Ngừng hoạt động');option.value=code;option.selected=person.status===code;select.append(option);}label.append(select);form.append(label);
    },async data=>{await json(`/api/v1/workspace/${farm.farmId}/staff/${person.id}`,'PUT',data);closeModal();await openStaff();message('Đã cập nhật quyền nhân viên.');});});
    else actions.append(node('span','Chủ trang trại'));
    body.append(row);
  }
  if(!matches.length){const row=node('tr'),cell=node('td','Không tìm thấy nhân sự phù hợp.');cell.colSpan=6;row.append(cell);body.append(row);}}
  search.oninput=render;render();
}
