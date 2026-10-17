'use strict';
(() => {
    const api = window.parent !== window ? window.parent.ManagementApi : null;
    if (!api) { location.replace('/#harvest'); return; }
    const el = id => document.getElementById(id);
    let seasons = [], page = 0, total = 0;
    const size = 20;
    const base = () => `/api/v1/farms/${el('farm').value}`;
    const selected = () => seasons.find(s => String(s.id) === el('season').value);
    function option(value, text) { const o = document.createElement('option'); o.value = value; o.textContent = text; return o; }
    async function run(action, button) {
        if (button) button.disabled = true;
        el('notice').textContent = '';
        try { await action(); } catch (e) { el('notice').textContent = e instanceof TypeError ? 'Chưa xác định kết quả. Hãy tải lại lịch sử để kiểm tra trước khi lưu lại.' : e.message; }
        finally { if (button) button.disabled = false; }
    }
    function selectSeason() {
        const s = selected();
        el('create').hidden = !s || !api.has('harvests:create');
        el('configuration').hidden = !s || !api.has('harvests:create') || !api.has('crops:update');
        if (!s) return;
        el('seasonInfo').textContent = `${s.plotName} · ${s.cropName} · ${s.unit || 'Chưa có đơn vị'} · ${s.start || 'Chưa có ngày bắt đầu'} → ${s.end || 'Chưa có ngày kết thúc'} · ${s.status}`;
        const f = el('configure').elements;
        f.name.value = s.name || ''; f.startDate.value = s.start || ''; f.endDate.value = s.end || ''; f.unitId.value = s.unitId || '';
    }
    async function loadSeasons() {
        const previous = el('season').value;
        seasons = await api.request(base() + '/harvest-seasons');
        el('season').replaceChildren(option('', 'Tất cả mùa vụ'), ...seasons.map(s => option(s.id, `${s.name || 'Mùa vụ #' + s.id} · ${s.cropName} · ${s.plotName}`)));
        if (seasons.some(s => String(s.id) === previous)) el('season').value = previous;
        selectSeason();
    }
    async function history() {
        el('history').replaceChildren(); el('count').textContent = 'Đang tải…';
        const q = new URLSearchParams({ page, size });
        for (const [key, value] of new FormData(el('filters'))) if (value) q.set(key, value);
        if (el('season').value) q.set('seasonId', el('season').value);
        const data = await api.request(base() + '/harvests?' + q);
        total = data.total;
        el('count').textContent = `${total} lần thu hoạch · Trang ${page + 1}`;
        for (const h of data.items) {
            const row = document.createElement('tr');
            for (const text of [h.id, `${h.seasonName || '#' + h.seasonId} / ${h.plotName} / ${h.cropName}`, h.harvestDate, `${h.quantity} ${h.unit || ''}`, h.quality || 'Chưa đánh giá', h.recordStatus, h.createdBy]) {
                const cell = document.createElement('td'); cell.textContent = text; row.append(cell);
            }
            el('history').append(row);
        }
        if (!data.items.length) { const row = document.createElement('tr'), cell = document.createElement('td'); cell.colSpan = 7; cell.textContent = 'Chưa có thu hoạch trong phạm vi đã chọn.'; row.append(cell); el('history').append(row); }
        el('previous').disabled = page === 0; el('next').disabled = (page + 1) * size >= total;
    }
    el('farm').addEventListener('change', () => run(async () => { page = 0; el('season').value = ''; await loadSeasons(); await history(); }));
    el('season').addEventListener('change', () => { selectSeason(); page = 0; run(history); });
    el('filters').addEventListener('submit', e => { e.preventDefault(); page = 0; run(history, e.submitter); });
    el('create').addEventListener('submit', e => {
        e.preventDefault();
        run(async () => {
            const data = Object.fromEntries(new FormData(e.target)); data.seasonId = Number(el('season').value);
            await api.json(base() + '/harvests', 'POST', data);
            e.target.reset(); page = 0; el('notice').textContent = 'Đã lưu thu hoạch.';
            await history();
        }, e.submitter);
    });
    el('configure').addEventListener('submit', e => {
        e.preventDefault(); run(async () => {
            const data = Object.fromEntries(new FormData(e.target)); data.unitId = Number(data.unitId);
            await api.json(base() + '/harvest-seasons/' + el('season').value, 'PUT', data);
            await loadSeasons(); el('notice').textContent = 'Đã lưu cấu hình mùa vụ.';
        }, e.submitter);
    });
    el('previous').addEventListener('click', () => { if (page > 0) { page--; run(history); } });
    el('next').addEventListener('click', () => { if ((page + 1) * size < total) { page++; run(history); } });
    run(async () => {
        const [farms, units] = await Promise.all([api.request('/api/v1/harvest-farms'), api.request('/api/v1/harvest-units')]);
        el('farm').replaceChildren(...farms.map(f => option(f.id, f.name)));
        el('unit').replaceChildren(option('', 'Chọn đơn vị'), ...units.map(u => option(u.id, u.name)));
        if (!farms.length) { el('notice').textContent = 'Bạn chưa được gán vai trò Chủ trại hoặc Quản lý ở trang trại nào.'; return; }
        await loadSeasons(); await history();
    });
})();
