"use strict";
// Native SVG charts share the authenticated shell's API and never persist data.
window.OverviewCharts = (() => {
  const colors = ['#059669', '#0284c7', '#d97706', '#8b5cf6', '#dc2626', '#0891b2', '#64748b', '#ea580c'];
  const number = value => new Intl.NumberFormat('vi-VN').format(value);
  const element = (tag, text, className) => {
    const result = document.createElement(tag);
    if (text !== undefined) result.textContent = text;
    if (className) result.className = className;
    return result;
  };
  const svg = (tag, attributes = {}) => {
    const result = document.createElementNS('http://www.w3.org/2000/svg', tag);
    for (const [key, value] of Object.entries(attributes)) result.setAttribute(key, String(value));
    return result;
  };
  const cents = value => {
    const [whole, fraction = ''] = String(value).split('.');
    return BigInt(whole) * 100n + BigInt(fraction.padEnd(2, '0').slice(0, 2));
  };
  const money = value => {
    const amount = cents(value);
    const fraction = amount % 100n;
    return number(amount / 100n) + (fraction ? ',' + String(fraction).padStart(2, '0') : '') + ' ₫';
  };
  function card(title, description, kind) {
    const result = element('article', undefined, 'chart-card');
    result.dataset.chart = kind;
    result.append(element('h3', title), element('p', description, 'chart-description'));
    return result;
  }
  function bars(container, series, format = number, units = 'bản ghi', convert = value => BigInt(value)) {
    const values = series.map(item => convert(item.value));
    const maximum = values.reduce((max, value) => value > max ? value : max, 0n);
    if (!maximum) {
      container.append(element('p', 'Chưa có dữ liệu để hiển thị biểu đồ.', 'chart-empty'));
      return;
    }
    const list = element('ul', undefined, 'chart-bars');
    series.forEach((item, index) => {
      const row = element('li');
      const heading = element('div', undefined, 'chart-bar-heading');
      heading.append(element('span', item.label), element('strong', format(item.value)));
      const plot = svg('svg', { viewBox: '0 0 1000 12', preserveAspectRatio: 'none', 'aria-hidden': 'true' });
      const title = svg('title'); title.textContent = item.label + ': ' + format(item.value);
      plot.append(title, svg('rect', { width: 1000, height: 12, rx: 6, fill: '#edf6f1' }),
        svg('rect', { width: Number(values[index] * 1000n / maximum), height: 12, rx: 6, fill: colors[index % colors.length] }));
      row.append(heading, plot); list.append(row);
    });
    container.append(list, element('p', 'Đơn vị: ' + units + '.', 'chart-note'));
  }
  function donut(container, series) {
    const total = series.reduce((sum, item) => sum + item.value, 0);
    const layout = element('div', undefined, 'chart-donut-layout');
    const ring = element('div', undefined, 'chart-donut');
    const plot = svg('svg', { viewBox: '0 0 120 120', role: 'img', 'aria-label': 'Trạng thái của ' + number(total) + ' mùa vụ' });
    plot.append(svg('circle', { cx: 60, cy: 60, r: 46, fill: 'none', stroke: '#edf6f1', 'stroke-width': 12 }));
    let offset = 0;
    const circumference = 2 * Math.PI * 46;
    for (const [index, item] of series.entries()) {
      if (!item.value || !total) continue;
      const length = item.value / total * circumference;
      const segment = svg('circle', { cx: 60, cy: 60, r: 46, fill: 'none', stroke: colors[index % colors.length],
        'stroke-width': 12, 'stroke-dasharray': length + ' ' + (circumference - length),
        'stroke-dashoffset': -offset, transform: 'rotate(-90 60 60)' });
      const title = svg('title'); title.textContent = item.label + ': ' + number(item.value);
      segment.append(title); plot.append(segment); offset += length;
    }
    const center = element('div', undefined, 'chart-donut-total');
    center.append(element('strong', number(total)), element('span', 'mùa vụ'));
    ring.append(plot, center);
    const legend = element('ul', undefined, 'chart-legend');
    series.forEach((item, index) => {
      const row = element('li');
      const swatch = svg('svg', { viewBox: '0 0 12 12', 'aria-hidden': 'true' });
      swatch.append(svg('circle', { cx: 6, cy: 6, r: 5, fill: colors[index % colors.length] }));
      row.append(swatch, element('span', item.label), element('strong', number(item.value))); legend.append(row);
    });
    layout.append(ring, legend); container.append(layout);
    if (!total) container.append(element('p', 'Chưa có mùa vụ. Tạo mùa vụ trong Trang trại & Cây trồng.', 'chart-empty'));
  }
  async function seasonStats(request, isCurrent) {
    const labels = { PLANNED: 'Dự kiến', IN_PROGRESS: 'Đang thực hiện', COMPLETED: 'Hoàn thành', CANCELLED: 'Đã hủy' };
    const totals = new Map(Object.keys(labels).map(key => [key, 0]));
    let loaded = 0;
    for (let page = 0; ; page++) {
      if (!isCurrent()) return null;
      const data = await request('/api/v1/seasons?page=' + page + '&size=100');
      for (const season of data.items) totals.set(season.status, (totals.get(season.status) || 0) + 1);
      loaded += data.items.length;
      if (!data.items.length || loaded >= data.total) break;
    }
    return [...totals].map(([status, value]) => ({ label: labels[status] || status, value }));
  }
  async function render({ panel, counts, incomplete, request, has, farm, isCurrent, retry }) {
    const charts = element('div', undefined, 'overview-charts');
    const countChart = card('Dữ liệu đang quản lý', 'Số lượng bản ghi theo từng nhóm dữ liệu được cấp quyền.', 'records');
    if (!counts.length && incomplete) countChart.append(element('p', 'Không tải được số liệu thống kê.', 'chart-error'));
    else bars(countChart, counts.map(([label, value]) => ({ label, value })));
    if (incomplete && counts.length) countChart.append(element('p', 'Một số nhóm dữ liệu chưa tải được.', 'chart-error'));
    charts.append(countChart); panel.append(charts);
    const tasks = [];
    if (farm && has('crops:read')) {
      const seasons = card('Trạng thái mùa vụ', 'Phân bố mùa vụ của trang trại đang chọn.', 'seasons');
      charts.append(seasons);
      tasks.push([seasons, async () => {
        const data = await seasonStats(request, isCurrent);
        if (isCurrent() && data) donut(seasons, data);
      }]);
    }
    if (farm && has('expenses:read')) {
      const expenses = card('Chi phí theo nhóm', 'Tổng chi phí đã ghi nhận, tính bằng đồng Việt Nam.', 'expenses');
      charts.append(expenses);
      tasks.push([expenses, async () => {
        const data = await request('/api/v1/expenses/summary');
        if (!isCurrent()) return;
        const labels = { SEEDS: 'Hạt giống', FERTILIZER: 'Phân bón', PESTICIDES: 'Thuốc BVTV', LABOR: 'Nhân công',
          EQUIPMENT: 'Thiết bị', IRRIGATION: 'Tưới tiêu', TRANSPORT: 'Vận chuyển', OTHER: 'Khác' };
        expenses.append(element('p', money(data.totalAmount), 'chart-expense-total'));
        bars(expenses, data.byCategory.map(item => ({ label: labels[item.category] || item.category, value: item.totalAmount })), money, 'VND', cents);
      }]);
    }
    await Promise.allSettled(tasks.map(async ([container, load]) => {
      const loading = element('p', 'Đang tải thống kê…', 'chart-loading'); container.append(loading);
      try {
        await load();
      } catch (error) {
        if (!isCurrent()) return;
        container.append(element('p', 'Không tải được thống kê. Vui lòng thử lại.', 'chart-error'));
        const action = element('button', 'Thử lại', 'secondary'); action.type = 'button';
        action.addEventListener('click', retry); container.append(action);
      } finally { loading.remove(); }
    }));
  }
  return Object.freeze({ render });
})();
