(function () {
    'use strict';
    const api = window.FarmApi, $ = id => document.getElementById(id), size = 20;
    const escape = value => String(value ?? '').replace(/[&<>"']/g, char => ({ '&':'&amp;', '<':'&lt;', '>':'&gt;', '"':'&quot;', "'":'&#39;' }[char]));
    const number = value => new Intl.NumberFormat('vi-VN').format(value);
    // Keep decimal amounts as strings to preserve cents, even above Number.MAX_SAFE_INTEGER.
    const money = value => {
        const [whole, fraction = ''] = String(value).split('.');
        return `${number(BigInt(whole))},${fraction.padEnd(2, '0')} ₫`;
    };
    const option = (id, label) => `<option value="${escape(id)}">${escape(label)}</option>`;
    const seasonLabel = season => `#${season.id} · ${season.plotName} / ${season.cropName}`;
    const today = () => {
        const parts = new Intl.DateTimeFormat('en', { timeZone:'Asia/Bangkok', year:'numeric', month:'2-digit', day:'2-digit' }).formatToParts(new Date());
        const part = name => parts.find(item => item.type === name).value;
        return `${part('year')}-${part('month')}-${part('day')}`;
    };
    let options = { farms:[], seasons:[], categories:[] }, page = 0, total = 0, rows = [], busy = false, saving = false;
    let activeFilters = '';
    const categoryName = code => options.categories.find(item => item.code === code)?.name || code;
    function error(id, message) { $(id).textContent = message; $(id).hidden = false; }
    function seasonOptions(selectId, farmId, placeholder) {
        const selected = $(selectId).value;
        $(selectId).innerHTML = option('', placeholder) + options.seasons
            .filter(season => !farmId || String(season.farmId) === farmId)
            .map(season => option(season.id, seasonLabel(season))).join('');
        if ([...$(selectId).options].some(item => item.value === selected)) $(selectId).value = selected;
    }
    function formSeasons() {
        seasonOptions('expenseSeason', $('expenseFarm').value, 'Chi phí chung (không gắn mùa vụ)');
        $('expenseSeason').disabled = !$('expenseFarm').value;
    }
    function filterSeasons() { seasonOptions('seasonFilter', $('farmFilter').value, 'Tất cả mùa vụ'); }
    async function refreshOptions() {
        options = await api.read('expenses/options');
        const selectedFarm = $('farmFilter').value, selectedCategory = $('categoryFilter').value;
        $('farmFilter').innerHTML = option('', 'Tất cả trang trại') + options.farms.map(farm => option(farm.id, farm.name)).join('');
        $('categoryFilter').innerHTML = option('', 'Tất cả loại') + options.categories.map(item => option(item.code, item.name)).join('');
        if ([...$('farmFilter').options].some(item => item.value === selectedFarm)) $('farmFilter').value = selectedFarm;
        if ([...$('categoryFilter').options].some(item => item.value === selectedCategory)) $('categoryFilter').value = selectedCategory;
        filterSeasons();
        $('expenseFarm').innerHTML = option('', 'Chọn trang trại') + options.farms.map(farm => option(farm.id, farm.name)).join('');
        $('expenseCategory').innerHTML = option('', 'Chọn loại chi phí') + options.categories.map(item => option(item.code, item.name)).join('');
        formSeasons();
    }
    function pagination() {
        $('previousPage').disabled = busy || page === 0;
        $('nextPage').disabled = busy || (page + 1) * size >= total;
    }
    async function history() {
        if (!api.has('expenses:read') || busy) return;
        busy = true; pagination(); $('expenseError').hidden = true;
        $('addExpense').disabled = true;
        $('filterForm').querySelectorAll('input, select, button').forEach(control => { control.disabled = true; });
        $('expenseRows').setAttribute('aria-busy', 'true');
        try {
            const query = activeFilters ? '&' + activeFilters : '';
            const [data, summary] = await Promise.all([
                api.read(`expenses?page=${page}&size=${size}${query}`),
                api.read('expenses/summary' + (activeFilters ? '?' + activeFilters : ''))
            ]);
            rows = data.items; total = data.total;
            $('expenseRows').innerHTML = rows.map(row => `<tr>
                <td>${escape(row.expenseDate)}</td><td>${escape(categoryName(row.category))}</td>
                <td>${escape(money(row.amount))}</td><td>${escape(row.farmName)}<span class="record-meta">${row.seasonId
                    ? `#${row.seasonId} · ${escape(row.plotName)} / ${escape(row.cropName)}` : 'Chi phí chung'}</span></td>
                <td>${escape(row.description || '—')}</td><td><button type="button" class="btn-secondary" data-detail="${row.id}">Chi tiết</button></td></tr>`).join('');
            $('recordCount').textContent = `${number(total)} khoản chi`;
            $('emptyHistory').hidden = rows.length > 0;
            $('pageSummary').textContent = total ? `Trang ${page + 1} / ${Math.ceil(total / size)} · ${number(total)} bản ghi` : '0 bản ghi';
            $('totalAmount').textContent = money(data.totalAmount);
            $('expenseCount').textContent = number(total);
            $('summaryRows').innerHTML = summary.byCategory.length ? summary.byCategory.map(row => `<tr>
                <td>${escape(categoryName(row.category))}</td><td>${number(row.count)}</td><td>${escape(money(row.totalAmount))}</td></tr>`).join('')
                : '<tr><td colspan="3">Chưa có dữ liệu chi phí.</td></tr>';
        } catch (err) {
            rows = []; total = 0;
            $('expenseRows').replaceChildren(); $('summaryRows').replaceChildren();
            $('recordCount').textContent = ''; $('pageSummary').textContent = '';
            $('totalAmount').textContent = $('expenseCount').textContent = '—';
            $('emptyHistory').hidden = true; error('expenseError', err.message);
        } finally {
            busy = false; $('expenseRows').removeAttribute('aria-busy'); pagination();
            $('addExpense').disabled = false;
            $('filterForm').querySelectorAll('input, select, button').forEach(control => { control.disabled = false; });
        }
    }
    function applyFilters() {
        if (busy) return;
        const fields = new FormData($('filterForm'));
        if (fields.get('fromDate') && fields.get('toDate') && fields.get('fromDate') > fields.get('toDate')) {
            error('expenseError', 'Ngày bắt đầu phải trước hoặc bằng ngày kết thúc.'); return;
        }
        const query = new URLSearchParams();
        for (const [key,value] of fields) if (value) query.set(key,value);
        activeFilters = query.toString(); page = 0; history();
    }
    $('farmFilter').addEventListener('change', filterSeasons);
    $('expenseFarm').addEventListener('change', () => { $('expenseSeason').value = ''; formSeasons(); });
    $('filterForm').addEventListener('submit', event => { event.preventDefault(); applyFilters(); });
    $('clearFilters').addEventListener('click', () => {
        if (busy) return;
        $('filterForm').reset(); filterSeasons(); applyFilters();
    });
    $('previousPage').addEventListener('click', () => { if (!busy && page > 0) { page--; history(); } });
    $('nextPage').addEventListener('click', () => { if (!busy && (page + 1) * size < total) { page++; history(); } });
    $('addExpense').addEventListener('click', async () => {
        if (busy || saving || !api.has('expenses:create')) return;
        $('addExpense').disabled = true;
        try {
            await refreshOptions(); $('expenseForm').reset(); formSeasons(); $('formError').hidden = true;
            $('expenseDate').value = today();
            $('saveExpense').disabled = options.farms.length === 0;
            $('formHint').textContent = options.farms.length ? 'Chọn trang trại và mùa vụ liên quan, hoặc ghi nhận chi phí chung.'
                : 'Cần có trang trại trước khi ghi nhận chi phí.';
            $('expenseDialog').showModal();
        } catch (err) { error('expenseError', err.message); }
        finally { $('addExpense').disabled = false; }
    });
    const close = () => { if (!saving) $('expenseDialog').close(); };
    $('closeDialog').addEventListener('click', close); $('cancelExpense').addEventListener('click', close);
    $('expenseDialog').addEventListener('cancel', event => { if (saving) event.preventDefault(); });
    $('expenseForm').addEventListener('submit', async event => {
        event.preventDefault();
        if (saving || !api.has('expenses:create')) return;
        const fields = new FormData($('expenseForm')), amount = fields.get('amount').trim();
        $('formError').hidden = true;
        if (!/^[0-9]{1,16}(\.[0-9]{1,2})?$/.test(amount) || !/[1-9]/.test(amount)) {
            error('formError', 'Số tiền phải lớn hơn 0 và có tối đa 2 chữ số thập phân.'); return;
        }
        saving = true;
        $('expenseForm').querySelectorAll('input, select, textarea, button').forEach(control => { control.disabled = true; });
        try {
            await api.write('expenses', 'POST', {
                farmId:Number(fields.get('farmId')), seasonId:fields.get('seasonId') ? Number(fields.get('seasonId')) : null,
                category:fields.get('category'), amount, expenseDate:fields.get('expenseDate'),
                description:fields.get('description').trim() || null
            });
            $('expenseDialog').close(); $('expenseForm').reset();
            $('expenseNotice').textContent = 'Đã ghi nhận chi phí trên máy chủ.'; $('expenseNotice').hidden = false;
            if (api.has('expenses:read')) { page = 0; await history(); }
        } catch (err) { error('formError', err.message); }
        finally {
            saving = false;
            $('expenseForm').querySelectorAll('input, select, textarea, button').forEach(control => { control.disabled = false; });
            formSeasons();
        }
    });
    $('expenseRows').addEventListener('click', event => {
        const button = event.target.closest('[data-detail]');
        const row = button && rows.find(item => item.id === Number(button.dataset.detail));
        if (!row) return;
        window.showRecord(`Khoản chi #${row.id}`, [
            ['Loại chi phí', categoryName(row.category)], ['Số tiền', money(row.amount)], ['Ngày phát sinh', row.expenseDate],
            ['Trang trại', row.farmName], ['Mùa vụ', row.seasonId ? `#${row.seasonId}` : 'Chi phí chung'],
            ['Khu đất', row.plotName], ['Cây trồng', row.cropName], ['Mô tả', row.description],
            ['Người ghi nhận', row.createdBy ? `#${row.createdBy}` : '—'],
            ['Thời điểm ghi nhận', new Date(row.createdAt).toLocaleString('vi-VN')]
        ]);
    });
    (async () => {
        try {
            await api.ready();
            if (!api.has('expenses:read') && !api.has('expenses:create')) throw new Error('Tài khoản chưa có quyền quản lý chi phí.');
            await refreshOptions();
            $('addExpense').hidden = !api.has('expenses:create');
            $('historyPanel').hidden = $('summaryPanel').hidden = !api.has('expenses:read');
            await history();
        } catch (err) { error('expenseError', err.message); }
    })();
})();
