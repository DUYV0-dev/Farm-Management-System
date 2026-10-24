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
  const view=await prototypeView('workers',version);if(!view) return;
  view.querySelector('h1').textContent='Nhân viên của '+farm.farmName;
  view.replaceChildren(node('h1','Nhân sự & Phân quyền của '+farm.farmName));
  const panel=node('div');panel.id='staffContent';view.append(panel);
  const stats=node('div');stats.className='stats-container overview-grid';
  for(const [title,value] of [['Tổng nhân viên',staff.length],['Đang hoạt động',staff.filter(p=>p.status==='ACTIVE').length],['Ngừng hoạt động',staff.filter(p=>p.status!=='ACTIVE').length]]) {
    const card=node('article');card.className='stat-card shadow-sm';const info=node('div');info.append(node('h3',title),node('strong',String(value)));card.append(info);stats.append(card);
  }
  panel.append(stats);
  saveForm(panel,'Thêm nhân viên',form=>{
    field(form,'Họ tên','fullName','');field(form,'Tên đăng nhập','username','','text',50);
    field(form,'Email','email','','email',254);field(form,'Mật khẩu ban đầu (12–128 ký tự, có chữ và số)','password','','password',128);jobField(form);
  },async data=>{await json(`/api/v1/workspace/${farm.farmId}/staff`,'POST',data);await openStaff();message('Đã tạo tài khoản nhân viên và cấp quyền theo công việc.');});
  const wrapper=node('div');wrapper.className='table-wrapper shadow-sm';
  const heading=node('div');heading.className='table-header';heading.append(node('h2','Danh sách nhân viên'));wrapper.append(heading);
  const responsive=node('div');responsive.className='table-responsive';
  const table=node('table');table.className='task-table';const head=node('thead'),headers=node('tr');
  for(const title of ['Nhân viên','Tên đăng nhập','Liên hệ','Vị trí','Trạng thái','Phân quyền']) headers.append(node('th',title));
  head.append(headers);table.append(head);const body=node('tbody');table.append(body);responsive.append(table);wrapper.append(responsive);panel.append(wrapper);
  for(const person of staff) {
    const row=node('tr');
    for(const value of [person.fullName,person.username,person.email,person.roles.map(r=>jobs[r]||'Chủ trang trại').join(', '),person.status==='ACTIVE'?'Hoạt động':'Ngừng hoạt động']) row.append(node('td',value));
    const actions=node('td');row.append(actions);
    if(!person.roles.includes('OWNER')) saveForm(actions,'Lưu vị trí và trạng thái',form=>{
      jobField(form,person.roles[0]);const label=node('label','Trạng thái'), select=node('select');select.name='status';
      for(const code of ['ACTIVE','INACTIVE']) {const option=node('option',code==='ACTIVE'?'Hoạt động':'Ngừng hoạt động');option.value=code;option.selected=person.status===code;select.append(option);}label.append(select);form.append(label);
    },async data=>{await json(`/api/v1/workspace/${farm.farmId}/staff/${person.id}`,'PUT',data);await openStaff();message('Đã cập nhật quyền nhân viên.');});
    else actions.append(node('span','Chủ trang trại'));
    body.append(row);
  }
}
