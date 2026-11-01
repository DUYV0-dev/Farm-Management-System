'use strict';
window.AgriFarmOverview=(()=>{
  const node=(tag,text)=>{const n=document.createElement(tag);if(text!==undefined)n.textContent=text;return n;};
  async function render(target,{api,profile}){
    const data=await api.request('/api/v1/operations/overview'),charts=window.AgriCharts;
    const toolbar=node('div');toolbar.className='module-toolbar';toolbar.append(node('p','Dữ liệu hiện tại · '+data.today));if(api.has('dashboard:read')){const b=node('button','Xem báo cáo chi tiết ↗');b.type='button';b.onclick=()=>api.navigate('dashboard');toolbar.append(b);}target.append(toolbar);
    const stats=node('div');stats.className='stats';for(const[key,label]of [['plots','Khu đất canh tác'],['seasons','Mùa vụ'],['tasks','Công việc'],['expenses','Chi phí đã ghi nhận'],['materials','Vật tư'],['warehouses','Kho']].filter(([key])=>data[key]!==undefined).slice(0,4)){const card=node('article');card.className='stat';card.append(node('small',label),node('strong',new Intl.NumberFormat('vi-VN').format(data[key])+(key==='expenses'?' ₫':'')));stats.append(card);}target.append(stats);
    const grid=node('div');grid.className='chart-grid';target.append(grid);
    function card(title,description){const panel=node('section'),chart=node('div');panel.className='chart-panel';panel.append(node('h2',title),node('p',description),chart);grid.append(panel);return chart;}
    const today=data.today,query='?fromDate='+today.slice(0,4)+'-01-01&toDate='+today,base='/api/v1/farms/'+profile.farm.farmId;
    async function load(path,target,renderer){try{renderer(await api.request(base+path+query));}catch(e){target.textContent='Chưa tải được dữ liệu: '+e.message;}}
    const jobs=[];
    if(api.has('dashboard:read')){const task=card('Tiến độ công việc','Phân bố trạng thái công việc trong kỳ.');jobs.push(load('/statistics/tasks',task,d=>{const names={TODO:'Chưa bắt đầu',IN_PROGRESS:'Đang thực hiện',COMPLETED:'Hoàn thành',CANCELLED:'Đã hủy'};charts.donut(task,{title:'Trạng thái công việc',items:Object.entries(names).map(([key,label])=>({label,value:d.byStatus.find(r=>r.status===key)?.count||0}))});}));}
    if(['dashboard:read','harvests:read'].every(api.has)){const production=card('Sản lượng thu hoạch','Theo tháng, tách biểu đồ cho từng đơn vị tính.');jobs.push(load('/dashboard/production',production,d=>{production.replaceChildren();const units=[...new Set(d.byMonth.map(r=>r.unitId))];if(!units.length){charts.line(production,{title:'Sản lượng theo tháng',labels:[],series:[]});return;}for(const unit of units){const rows=d.byMonth.filter(r=>r.unitId===unit),chart=node('div');production.append(chart);charts.line(chart,{title:'Sản lượng theo tháng',unit:rows[0].unit,labels:rows.map(r=>r.period),series:[{name:'Thu hoạch',values:rows.map(r=>r.quantity)}]});}}));}
    if(api.has('expenses:read')){const expenses=card('Chi phí & mua vật tư','Hai khoản mục được trình bày riêng để tránh cộng trùng chi phí.');jobs.push(load('/statistics/expenses',expenses,d=>charts.bar(expenses,{title:'Giá trị trong năm',unit:'VND',labels:['Chi phí sản xuất','Mua hàng đã nhận'],series:[{name:'Giá trị',values:[d.productionExpense,d.purchaseValue]}]})));}
    if(['dashboard:read','warehouses:read'].every(api.has)){const stock=card('Tình trạng tồn kho','Số dòng tồn kho hiện tại; không cộng lượng của các đơn vị khác nhau.');jobs.push(load('/statistics/inventory',stock,d=>charts.donut(stock,{title:'Vật tư theo số dư',unit:'dòng vật tư',items:[{label:'Còn tồn',value:d.currentItems.filter(r=>Number(r.quantity)>0).length},{label:'Bằng 0',value:d.currentItems.filter(r=>Number(r.quantity)===0).length}]})));}
    if(!jobs.length){const panel=card('Không gian làm việc của bạn','Biểu đồ chỉ hiển thị khi bạn được cấp quyền xem báo cáo hoặc chi phí.');panel.append(node('p','Dùng menu để xem các công việc và nghiệp vụ được phân công.'));}
    await Promise.all(jobs);
  }
  return Object.freeze({render});
})();
