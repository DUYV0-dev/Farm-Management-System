'use strict';
(() => {
  const api = window.parent !== window ? window.parent.ManagementApi : null;
  if (!api) { location.replace('/#dashboard'); return; }
  const el = id => document.getElementById(id);
  let reportId = null, reportFarm = null, page = 0, generation = 0;
  const node = (tag, text) => { const n = document.createElement(tag); if (text !== undefined) n.textContent = text; return n; };
  const canReport = () => ['reports:read', 'harvests:read', 'crops:read'].every(api.has);
  const filters = keys => Object.fromEntries([...new FormData(el('filters'))].filter(([key, value]) => keys.includes(key) && value));
  const productionKeys = ['fromDate', 'toDate', 'plotId', 'cropId', 'seasonId'];
  const stockKeys = ['fromDate', 'toDate', 'warehouseId', 'materialId'];
  const base = () => `/api/v1/farms/${el('farm').value}`;
  async function run(action, button) {
    if (button) button.disabled = true;
    el('notice').textContent = '';
    try { await action(); } catch (e) { el('notice').textContent = e instanceof TypeError ? 'Không kết nối được máy chủ. Hãy kiểm tra rồi tải lại.' : e.message; }
    finally { if (button) button.disabled = false; }
  }
  function table(target, headers, rows) {
    target.replaceChildren();
    if (!rows.length) { target.append(node('p', 'Không có dữ liệu trong phạm vi đã chọn.')); return; }
    const container = node('div'), t = node('table'), head = node('thead'), tr = node('tr'), body = node('tbody');
    container.style.overflowX = 'auto';
    headers.forEach(h => tr.append(node('th', h))); head.append(tr); t.append(head);
    for (const values of rows) { const row = node('tr'); values.forEach(value => row.append(node('td', value ?? 'Chưa có'))); body.append(row); }
    t.append(body); container.append(t); target.append(container);
  }
  function chart(target,rows,label,type='bar') {
    for(const unitId of new Set(rows.map(r=>r.unitId))){const group=rows.filter(r=>r.unitId===unitId),host=node('div');target.append(host);AgriCharts[type](host,{title:'Sản lượng',unit:group[0].unit,labels:group.map(label),series:[{name:'Thu hoạch',values:group.map(r=>r.quantity)}]});}
    if(!rows.length)AgriCharts[type](target,{title:'Sản lượng',labels:[],series:[]});
  }
  function exact(target,headers,rows){const details=node('details'),summary=node('summary','Xem số liệu chi tiết'),data=node('div');details.className='data-disclosure';table(data,headers,rows);details.append(summary,data);target.append(details);}
  async function part(id, path, permissions, keys, render, turn) {
    const target = el(id);
    if (!permissions.every(api.has)) { target.textContent = 'Bạn chưa có quyền xem mục này.'; return; }
    target.textContent = 'Đang tải…';
    try {
      const data = await api.request(base() + path + '?' + new URLSearchParams(filters(keys)));
      if (turn !== generation) return;
      render(data);
    } catch (e) { if (turn === generation) target.textContent = `Không tải được dữ liệu: ${e.message}`; }
  }
  async function dashboard() {
    const turn = ++generation;
    el('monthly').replaceChildren(); el('crops').replaceChildren();
    el('expenseSection').hidden = !api.has('expenses:read');
    el('reportSection').hidden = !canReport();
    await Promise.all([
      part('production', '/dashboard/production', ['dashboard:read', 'harvests:read'], productionKeys, d => {
        const summary=el('production');summary.replaceChildren();const units=node('div');units.className='report-unit-summary';for(const r of d.unitGroups){const card=node('article');card.append(node('small','Sản lượng ('+r.unit+')'),node('strong',AgriCharts.number(r.quantity)+' '+r.unit),node('small',r.count+' lần thu hoạch'));units.append(card);}summary.append(units);if(!d.unitGroups.length)summary.append(node('p','Chưa có thu hoạch trong khoảng thời gian đã chọn.'));
        chart(el('monthly'),d.byMonth,r=>r.period,'line');exact(el('monthly'),['Tháng','Đơn vị','Sản lượng','Số lần'],d.byMonth.map(r=>[r.period,r.unit,r.quantity,r.count]));
        chart(el('crops'),d.byCrop,r=>r.cropName);exact(el('crops'),['Cây trồng','Đơn vị','Sản lượng','Số lần'],d.byCrop.map(r=>[r.cropName,r.unit,r.quantity,r.count]));
        el('production').append(node('p', 'Chỉ tính thu hoạch đã xác nhận; tách riêng các đơn vị sản lượng.'));
      }, turn),
      part('seasons', '/statistics/seasons', ['dashboard:read', 'crops:read', 'harvests:read'], productionKeys, d => {
        const target=el('seasons');AgriCharts.donut(target,{title:'Trạng thái mùa vụ',unit:'mùa vụ',items:[{label:'Dự kiến',value:d.byStatus.PLANNED},{label:'Đang canh tác',value:d.byStatus.IN_PROGRESS},{label:'Kết thúc',value:d.byStatus.COMPLETED}]});const detail=node('details'),summary=node('summary','Xem từng mùa vụ'),data=node('div');detail.className='data-disclosure';seasonTable(data,d.items);detail.append(summary,data);target.append(detail);
        el('seasons').append(node('p', `${d.total} mùa vụ trong khoảng thời gian đã chọn. ${d.unconfiguredSeasons} mùa vụ chưa cấu hình đủ ngày bắt đầu/kết thúc.`));
      }, turn),
      part('tasks', '/statistics/tasks', ['dashboard:read'], productionKeys, d => { const names={TODO:'Chưa bắt đầu',IN_PROGRESS:'Đang thực hiện',COMPLETED:'Hoàn thành',CANCELLED:'Đã hủy'};AgriCharts.donut(el('tasks'),{title:'Tiến độ công việc',items:(d.byStatus||[]).map(r=>({label:names[r.status]||r.status,value:r.count}))}); }, turn),
      part('inventory', '/statistics/inventory', ['dashboard:read', 'warehouses:read'], stockKeys, d => {
        const target=el('inventory');AgriCharts.donut(target,{title:'Số dòng vật tư theo số dư',unit:'dòng vật tư',items:[{label:'Còn tồn',value:d.currentItems.filter(r=>Number(r.quantity)>0).length},{label:'Hết tồn',value:d.currentItems.filter(r=>Number(r.quantity)===0).length}]});exact(target,['Kho','Vật tư','Đơn vị','Tồn hiện tại','Cập nhật cuối'],d.currentItems.map(r=>[r.warehouse,r.material,r.unit,r.quantity,r.lastUpdated]));
        el('inventory').append(node('p', d.reason));
      }, turn),
      api.has('expenses:read') ? part('expenses', '/statistics/expenses', ['expenses:read'], ['fromDate','toDate'], d => { AgriCharts.bar(el('expenses'),{title:'Chi phí & giá trị mua hàng',unit:'VND',labels:['Sản xuất','Mua hàng đã nhận'],series:[{name:'Giá trị',values:[d.productionExpense,d.purchaseValue]}]});exact(el('expenses'),['Khoản mục','Giá trị (VND)'],[['Chi phí sản xuất',d.productionExpense],['Giá trị mua hàng đã nhận',d.purchaseValue]]);el('expenses').append(node('p',d.rule)); }, turn) : Promise.resolve()
    ]);
  }
  function seasonTable(target, rows) {
    table(target, ['Mùa vụ', 'Trạng thái hiện tại', 'Cây trồng', 'Khu đất', 'Đơn vị', 'Số lần thu', 'Trong kỳ', 'Lũy kế', 'Kế hoạch', 'Đạt (%)'], rows.map(r => [r.seasonName || '#' + r.seasonId, r.status, r.cropName, r.plotName, r.unit, r.harvestCount, r.periodQuantity, r.cumulativeQuantity, r.expectedYield, r.achievementPercent ?? r.ratioReason]));
  }
  async function reportPage() {
    const d = await api.request(`/api/v1/farms/${reportFarm}/reports/${reportId}?page=${page}&size=20`);
    el('reportInfo').textContent = `${d.farmName} — ${d.total} bản ghi. Tạo lúc ${d.generatedAt}, hết hạn ${d.expiresAt}. Kỳ ${d.appliedFilters.fromDate} → ${d.appliedFilters.toDate}. Bộ lọc: khu đất ${d.appliedFilters.plotId ?? 'tất cả'}, cây ${d.appliedFilters.cropId ?? 'tất cả'}, vụ ${d.appliedFilters.seasonId ?? 'tất cả'}. Trang ${page + 1}.`;
    seasonTable(el('summary'), d.summary);
    table(el('details'), ['Mã', 'Mùa vụ', 'Trạng thái vụ', 'Khu đất', 'Cây', 'Ngày', 'Sản lượng', 'Đơn vị', 'Chất lượng'], d.items.map(r => [r.harvestId, r.seasonName || '#' + r.seasonId, r.seasonStatus, r.plotName, r.cropName, r.date, r.quantity, r.unit, r.quality]));
    el('previous').disabled = page === 0; el('next').disabled = (page + 1) * 20 >= d.total;
    el('pdf').hidden = !api.has('reports:export:pdf'); el('xlsx').hidden = !api.has('reports:export:xlsx');
  }
  el('filters').addEventListener('submit', e => { e.preventDefault(); run(dashboard, e.submitter); });
  el('createReport').onclick = () => run(async () => {
    if (!el('filters').reportValidity()) return;
    const body = filters(productionKeys); for (const k of ['plotId', 'cropId', 'seasonId']) if (body[k]) body[k] = Number(body[k]);
    const farm = el('farm').value, s = await api.json(`/api/v1/farms/${farm}/reports/production`, 'POST', body);
    reportId = s.reportId; reportFarm = farm; page = 0; await reportPage();
  }, el('createReport'));
  el('previous').onclick = () => { if (page > 0) { page--; run(reportPage); } };
  el('next').onclick = () => { page++; run(reportPage); };
  for (const format of ['pdf', 'xlsx']) el(format).onclick = () => run(() => api.download(`/api/v1/farms/${reportFarm}/reports/${reportId}/export?format=${format}`, `production-${reportId}.${format}`), el(format));
  run(async () => {
    const today = new Intl.DateTimeFormat('sv-SE', {timeZone: 'Asia/Ho_Chi_Minh'}).format(new Date());
    el('filters').elements.toDate.value = today; el('filters').elements.fromDate.value = today.slice(0, 7) + '-01';
    const farms = await api.request('/api/v1/dashboard-farms');
    el('farm').replaceChildren(...farms.map(f => { const o = node('option', f.name); o.value = f.id; return o; }));
    if (!farms.length) { el('notice').textContent = 'Bạn chưa có vai trò Chủ trại hoặc Quản lý trong trang trại nào.'; el('createReport').disabled = true; return; }
    const active=api.profile().farm?.farmId;if(active){el('farm').value=String(active);el('farm').closest('label').hidden=true;}
    await dashboard();
  });
})();
