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
  function chart(target, rows, label) {
    // Separate scales per unit; the table below keeps exact values available.
    for (const unitId of new Set(rows.map(r => r.unitId))) {
      const group = rows.filter(r => r.unitId === unitId), max = Math.max(...group.map(r => Number(r.quantity)), 1);
      const figure = node('figure'); figure.append(node('figcaption', `Sản lượng (${group[0].unit})`));
      for (const r of group) {
        const p = node('p', `${label(r)}: ${r.quantity} ${r.unit} `), meter = node('meter');
        meter.min = 0; meter.max = max; meter.value = Number(r.quantity); meter.setAttribute('aria-label', `${label(r)}: ${r.quantity} ${r.unit}`); p.append(meter); figure.append(p);
      }
      target.append(figure);
    }
  }
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
        table(el('production'), ['Đơn vị', 'Sản lượng trong kỳ', 'Số lần thu'], d.unitGroups.map(r => [r.unit, r.quantity, r.count]));
        table(el('monthly'), ['Tháng', 'Đơn vị', 'Sản lượng', 'Số lần'], d.byMonth.map(r => [r.period, r.unit, r.quantity, r.count])); chart(el('monthly'), d.byMonth, r => r.period);
        table(el('crops'), ['Cây trồng', 'Đơn vị', 'Sản lượng', 'Số lần'], d.byCrop.map(r => [r.cropName, r.unit, r.quantity, r.count])); chart(el('crops'), d.byCrop, r => r.cropName);
        el('production').append(node('p', `Dữ liệu lúc ${d.generatedAt}. ${d.rule}`));
      }, turn),
      part('seasons', '/statistics/seasons', ['dashboard:read', 'crops:read', 'harvests:read'], productionKeys, d => {
        seasonTable(el('seasons'), d.items);
        el('seasons').append(node('p', `${d.total} vụ giao kỳ — PLANNED: ${d.byStatus.PLANNED}, IN_PROGRESS: ${d.byStatus.IN_PROGRESS}, COMPLETED: ${d.byStatus.COMPLETED}. ${d.unconfiguredSeasons} vụ trong trang trại chưa đủ ngày. ${d.rule}`));
      }, turn),
      part('tasks', '/statistics/tasks', ['dashboard:read'], productionKeys, d => { el('tasks').textContent = `Chưa khả dụng: ${d.reason}`; }, turn),
      part('inventory', '/statistics/inventory', ['dashboard:read', 'warehouses:read'], stockKeys, d => {
        table(el('inventory'), ['Kho', 'Vật tư', 'Đơn vị', 'Tồn hiện tại', 'Cập nhật cuối'], d.currentItems.map(r => [r.warehouse, r.material, r.unit, r.quantity, r.lastUpdated]));
        el('inventory').append(node('p', d.reason));
      }, turn),
      api.has('expenses:read') ? part('expenses', '/statistics/expenses', ['expenses:read'], stockKeys, d => { el('expenses').textContent = `Chưa khả dụng: ${d.reason}`; }, turn) : Promise.resolve()
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
    await dashboard();
  });
})();
