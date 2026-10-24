(function () {
    'use strict';
    const api = window.FarmApi, $ = id => document.getElementById(id), size = 20;
    const labels = { FERTILIZER: 'Phân bón', PESTICIDE: 'Thuốc bảo vệ thực vật' };
    const escape = value => String(value ?? '').replace(/[&<>"']/g, char => ({ '&':'&amp;', '<':'&lt;', '>':'&gt;', '"':'&quot;', "'":'&#39;' }[char]));
    const number = value => new Intl.NumberFormat('vi-VN', { maximumFractionDigits: 3 }).format(value);
    const option = (id, label) => `<option value="${escape(id)}">${escape(label)}</option>`;
    const seasonLabel = season => `#${season.id} · ${season.farmName} / ${season.plotName} / ${season.cropName}`;
    const today = () => {
        const parts = new Intl.DateTimeFormat('en', { timeZone: 'Asia/Bangkok', year: 'numeric', month: '2-digit', day: '2-digit' }).formatToParts(new Date());
        const part = name => parts.find(item => item.type === name).value;
        return `${part('year')}-${part('month')}-${part('day')}`;
    };
    let options = { seasons: [], materials: [], types: [] }, page = 0, total = 0, rows = [], busy = false, saving = false;
    let activeFilters = '';
    function error(id, message) { $(id).textContent = message; $(id).hidden = false; }
    function seasonFilters() {
        const selected = $('seasonFilter').value, farm = $('farmFilter').value;
        $('seasonFilter').innerHTML = option('', 'Tất cả mùa vụ') + options.seasons
            .filter(season => !farm || String(season.farmId) === farm).map(season => option(season.id, seasonLabel(season))).join('');
        if ([...$('seasonFilter').options].some(item => item.value === selected)) $('seasonFilter').value = selected;
    }
    function populateOptions() {
        const selected = ['farmFilter', 'seasonFilter', 'materialFilter', 'typeFilter'].map(id => $(id).value);
        const farms = [...new Map(options.seasons.map(season => [season.farmId, season.farmName])).entries()];
        $('farmFilter').innerHTML = option('', 'Tất cả trang trại') + farms.map(([id,name]) => option(id,name)).join('');
        $('materialFilter').innerHTML = option('', 'Tất cả vật tư') + options.materials.map(material => option(material.id, material.name)).join('');
        $('typeFilter').innerHTML = option('', 'Tất cả loại') + options.types.map(type => option(type.code,type.name)).join('');
        ['farmFilter', 'materialFilter', 'typeFilter'].forEach((id,index) => {
            const value = selected[[0,2,3][index]];
            if ([...$(id).options].some(item => item.value === value)) $(id).value = value;
        });
        seasonFilters();
        if ([...$('seasonFilter').options].some(item => item.value === selected[1])) $('seasonFilter').value = selected[1];
        $('usageSeason').innerHTML = option('', 'Chọn mùa vụ') + options.seasons.filter(season => season.available).map(season => option(season.id, seasonLabel(season))).join('');
        $('usageMaterial').innerHTML = option('', 'Chọn vật tư') + options.materials.filter(material => material.available)
            .map(material => option(material.id, `${material.name}${material.categoryName ? ' (' + material.categoryName + ')' : ''} — ${material.unit}`)).join('');
        $('usageType').innerHTML = option('', 'Chọn loại sử dụng') + options.types.map(type => option(type.code,type.name)).join('');
    }
    async function refreshOptions() {
        options = await api.read('material-usages/options'); populateOptions();
    }
    function updateUnit() {
        const material = options.materials.find(item => item.id === Number($('usageMaterial').value));
        $('usageUnit').textContent = material ? `Đơn vị: ${material.unit}` : 'Đơn vị được lấy từ vật tư đã chọn.';
    }
    function pagination() {
        $('previousPage').disabled = busy || page === 0;
        $('nextPage').disabled = busy || (page + 1) * size >= total;
    }
    async function history() {
        if (!api.has('usage:read') || busy) return;
        busy = true; pagination(); $('usageError').hidden = true;
        $('addUsage').disabled = true;
        $('filterForm').querySelectorAll('button').forEach(button => { button.disabled = true; });
        $('usageRows').setAttribute('aria-busy', 'true');
        try {
            const query = activeFilters ? '&' + activeFilters : '';
            const [data, summary] = await Promise.all([
                api.read(`material-usages?page=${page}&size=${size}${query}`),
                api.read('material-usages/summary' + (activeFilters ? '?' + activeFilters : ''))
            ]);
            rows = data.items; total = data.total;
            $('usageRows').innerHTML = rows.map(row => `<tr>
                <td>${escape(row.applicationDate)}</td><td>${escape(labels[row.type])}</td>
                <td>${escape(row.materialName)}</td><td>${number(row.quantity)} ${escape(row.unit)}</td>
                <td>${escape(row.farmName)}<span class="record-meta">#${row.seasonId} · ${escape(row.plotName)} / ${escape(row.cropName)}</span></td>
                <td>${escape(row.notes || '—')}</td><td><button type="button" class="btn-secondary" data-detail="${row.id}">Chi tiết</button></td></tr>`).join('');
            $('recordCount').textContent = `${number(total)} lần sử dụng`;
            $('emptyHistory').hidden = rows.length > 0;
            $('pageSummary').textContent = total ? `Trang ${page + 1} / ${Math.ceil(total / size)} · ${number(total)} bản ghi` : '0 bản ghi';
            $('summaryRows').innerHTML = summary.byMaterial.length ? summary.byMaterial.map(row => `<tr>
                <td>${escape(labels[row.type])}</td><td>${escape(row.materialName)}</td><td>${number(row.count)}</td>
                <td>${number(row.quantity)} ${escape(row.unit)}</td></tr>`).join('') : '<tr><td colspan="4">Chưa có dữ liệu sử dụng.</td></tr>';
        } catch (err) {
            rows = []; total = 0;
            $('usageRows').replaceChildren(); $('summaryRows').replaceChildren();
            $('recordCount').textContent = ''; $('pageSummary').textContent = '';
            $('emptyHistory').hidden = true; error('usageError', err.message);
        } finally {
            busy = false; $('usageRows').removeAttribute('aria-busy'); pagination();
            $('addUsage').disabled = false;
            $('filterForm').querySelectorAll('button').forEach(button => { button.disabled = false; });
        }
    }
    function applyFilters() {
        if (busy) return;
        const fields = new FormData($('filterForm'));
        if (fields.get('fromDate') && fields.get('toDate') && fields.get('fromDate') > fields.get('toDate')) {
            error('usageError', 'Ngày bắt đầu phải trước hoặc bằng ngày kết thúc.'); return;
        }
        const query = new URLSearchParams();
        for (const [key,value] of fields) if (value) query.set(key,value);
        activeFilters = query.toString(); page = 0; history();
    }
    $('farmFilter').addEventListener('change', seasonFilters);
    $('filterForm').addEventListener('submit', event => { event.preventDefault(); applyFilters(); });
    $('clearFilters').addEventListener('click', () => {
        if (busy) return;
        $('filterForm').reset(); seasonFilters(); applyFilters();
    });
    $('previousPage').addEventListener('click', () => { if (!busy && page > 0) { page--; history(); } });
    $('nextPage').addEventListener('click', () => { if (!busy && (page + 1) * size < total) { page++; history(); } });
    $('usageMaterial').addEventListener('change', updateUnit);
    $('addUsage').addEventListener('click', async () => {
        if (busy || saving) return;
        $('addUsage').disabled = true;
        try {
            await refreshOptions(); $('usageForm').reset(); $('formError').hidden = true;
            $('usageDate').value = $('usageDate').max = today(); updateUnit();
            const available = options.seasons.some(item => item.available) && options.materials.some(item => item.available);
            $('saveUsage').disabled = !available;
            $('formHint').textContent = available ? 'Chọn phân bón hoặc thuốc bảo vệ thực vật và nhập lượng đã sử dụng.'
                : 'Cần có vật tư hoạt động và mùa vụ đang thực hiện hoặc đã hoàn thành để ghi nhận sử dụng.';
            $('usageDialog').showModal();
        } catch (err) { error('usageError', err.message); }
        finally { $('addUsage').disabled = false; }
    });
    const close = () => { if (!saving) $('usageDialog').close(); };
    $('closeDialog').addEventListener('click', close); $('cancelUsage').addEventListener('click', close);
    $('usageDialog').addEventListener('cancel', event => { if (saving) event.preventDefault(); });
    $('usageForm').addEventListener('submit', async event => {
        event.preventDefault();
        if (saving || !api.has('usage:create')) return;
        saving = true; $('saveUsage').disabled = true; $('formError').hidden = true;
        const fields = new FormData($('usageForm'));
        try {
            if (fields.get('applicationDate') > today()) throw new Error('Ngày sử dụng không được ở tương lai.');
            await api.write('material-usages', 'POST', {
                seasonId: Number(fields.get('seasonId')), materialId: Number(fields.get('materialId')),
                type: fields.get('type'), quantity: fields.get('quantity'),
                applicationDate: fields.get('applicationDate'), notes: fields.get('notes').trim() || null
            });
            $('usageDialog').close(); $('usageForm').reset();
            $('usageNotice').textContent = 'Đã ghi nhận sử dụng trên máy chủ.'; $('usageNotice').hidden = false;
            // Refresh failure must not make a successful save appear to have failed.
            if (api.has('usage:read')) { page = 0; await history(); }
        } catch (err) { error('formError', err.message); }
        finally { saving = false; $('saveUsage').disabled = false; }
    });
    $('usageRows').addEventListener('click', event => {
        const button = event.target.closest('[data-detail]');
        const row = button && rows.find(item => item.id === Number(button.dataset.detail));
        if (!row) return;
        window.showRecord(`Lần sử dụng #${row.id}`, [
            ['Loại', labels[row.type]], ['Vật tư', row.materialName], ['Số lượng', `${number(row.quantity)} ${row.unit}`],
            ['Ngày sử dụng', row.applicationDate], ['Trang trại', row.farmName], ['Mùa vụ', `#${row.seasonId}`],
            ['Khu đất', row.plotName], ['Cây trồng', row.cropName], ['Ghi chú', row.notes],
            ['Người ghi nhận', row.createdBy ? `#${row.createdBy}` : '—'],
            ['Thời điểm ghi nhận', new Date(row.createdAt).toLocaleString('vi-VN')]
        ]);
    });
    (async () => {
        try {
            await api.ready();
            if (!api.has('usage:read') && !api.has('usage:create')) throw new Error('Tài khoản chưa có quyền quản lý sử dụng phân bón và thuốc.');
            await refreshOptions();
            $('addUsage').hidden = !api.has('usage:create');
            $('historyPanel').hidden = $('summaryPanel').hidden = !api.has('usage:read');
            await history();
        } catch (err) { error('usageError', err.message); }
    })();
})();
