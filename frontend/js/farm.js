// ═══════════════════════════════════════════════════════════════════════════
// AgriManage – Farm / Plot / Crop Catalog / Crop Season Module
// Connected API UI. Bearer authentication is owned by the parent application.
// ═══════════════════════════════════════════════════════════════════════════

(function () {
    'use strict';

    const PAGE_SIZE = 9;

    let farms = [], plots = [], crops = [], seasons = [];
    let farmPage = 0, plotPage = 0, cropPage = 0, seasonPage = 0;
    let farmSearch = '', plotSearch = '', cropSearch = '', seasonSearch = '';
    let plotFarmFilter = null, plotFarmFilterName = '';
    let seasonPlotFilter = null, seasonPlotFilterName = '';
    let currentTab = 'farms';
    let searchTimers = {};

    const api = window.FarmApi;
    const permission = type => type === 'seasons' ? 'crops' : type;
    async function load() {
        [farms, plots, crops, seasons] = await Promise.all([
            api.all('farms', 'farms:read'), api.all('plots', 'plots:read'),
            api.all('crops', 'crops:read'), api.all('seasons', 'crops:read')
        ]);
        updateStats(); window.switchTab(currentTab); applyPermissions();
    }
    function applyPermissions() {
        document.querySelectorAll('[data-permission]').forEach(el => el.hidden = !api.has(el.dataset.permission));
        for (const type of ['farms','plots','crops','seasons']) document.getElementById('tab-'+type).hidden = !api.has(permission(type)+':read');
        document.getElementById('btnAdd').hidden = !api.has(permission(currentTab)+':create');
    }
    async function saveRecord(resource, id, data) {
        await api.write(resource + (id ? '/'+id : ''), id ? 'PUT' : 'POST', data);
        await load(); showToast('Thành công', 'Đã lưu dữ liệu trên máy chủ.');
    }
    async function changeStatus(resource, id, rows) {
        const row = rows.find(x => x.id === id);
        if (!row) return;
        const status = row.status === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE';
        if (!confirm(`${status === 'INACTIVE' ? 'Ngừng hoạt động' : 'Kích hoạt'} “${row.name}”?`)) return;
        await api.write(resource+'/'+id+'/status','PUT',{status}); await load();
        showToast('Thành công', 'Đã cập nhật trạng thái.');
    }

    function filterArr(arr, q) {
        if (!q) return arr;
        const s = q.toLowerCase();
        return arr.filter(x => Object.values(x).some(v => typeof v === 'string' && v.toLowerCase().includes(s)));
    }
    function paginate(arr, page, size) {
        return { items: arr.slice(page * size, (page + 1) * size), total: arr.length };
    }

    function updateStats() {
        document.getElementById('stat-farms').textContent = farms.filter(f => f.status === 'ACTIVE').length;
        document.getElementById('stat-plots').textContent = plots.filter(p => p.status === 'ACTIVE').length;
        document.getElementById('stat-crops').textContent = crops.filter(c => c.status === 'ACTIVE').length;
        document.getElementById('stat-seasons').textContent = seasons.filter(s => s.status === 'IN_PROGRESS').length;
    }

    window.switchTab = function (tab) {
        currentTab = tab;
        document.querySelectorAll('.farm-tab').forEach(t => t.classList.remove('active'));
        document.querySelectorAll('.tab-panel').forEach(p => p.classList.remove('active'));
        document.getElementById('tab-' + tab).classList.add('active');
        document.getElementById('panel-' + tab).classList.add('active');
        
        const labels = { farms: 'Thêm Trang Trại', plots: 'Thêm Khu Vực', crops: 'Thêm Loại Cây', seasons: 'Thêm Mùa Vụ' };
        document.getElementById('btnAddLabel').textContent = labels[tab];
        
        if (tab === 'farms') renderFarms();
        if (tab === 'plots') renderPlots();
        if (tab === 'crops') renderCrops();
        if (tab === 'seasons') renderSeasons();
        applyPermissions();
    };

    document.getElementById('btnAdd').addEventListener('click', () => {
        if (currentTab === 'farms') openFarmModal();
        if (currentTab === 'plots') openPlotModal();
        if (currentTab === 'crops') openCropModal();
        if (currentTab === 'seasons') openSeasonModal();
    });

    window.debounceSearch = function (type) {
        clearTimeout(searchTimers[type]);
        searchTimers[type] = setTimeout(() => {
            if (type === 'farms') { farmSearch = document.getElementById('searchFarms').value; farmPage = 0; renderFarms(); }
            if (type === 'plots') { plotSearch = document.getElementById('searchPlots').value; plotPage = 0; renderPlots(); }
            if (type === 'crops') { cropSearch = document.getElementById('searchCrops').value; cropPage = 0; renderCrops(); }
            if (type === 'seasons') { seasonSearch = document.getElementById('searchSeasons').value; seasonPage = 0; renderSeasons(); }
            applyPermissions();
        }, 300);
    };

    // ─── FARMS ───────────────────────────────────────────────────────────────
    function renderFarms() {
        let filtered = filterArr(farms, farmSearch);
        const { items, total } = paginate(filtered, farmPage, PAGE_SIZE);
        const grid = document.getElementById('farmCardGrid');
        if (items.length === 0) {
            grid.innerHTML = '<div class="empty-state" style="grid-column:1/-1"><i class="fa-solid fa-tractor"></i><h3>Trống</h3></div>';
        } else {
            grid.innerHTML = items.map(f => farmCard(f)).join('');
        }
        renderPagination('paginationFarms', total, farmPage, PAGE_SIZE, p => { farmPage = p; renderFarms(); });
    }

    function farmCard(f) {
        const plotCount = plots.filter(p => p.farmId === f.id).length;
        const statusClass = f.status === 'ACTIVE' ? 'status-active' : 'status-inactive';
        return `<div class="farm-card shadow-sm">
            <div class="farm-card-header">
                <div><div class="farm-card-name">${esc(f.name)}</div>
                <div class="farm-card-meta"><i class="fa-solid fa-location-dot"></i> ${esc(f.address)}</div></div>
                <span class="status-badge ${statusClass}">${f.status}</span>
            </div>
            <div class="farm-card-stats">
                <div class="farm-card-stat"><div class="farm-card-stat-value">${f.areaHa} ha</div><div class="farm-card-stat-label">Diện Tích</div></div>
                <div class="farm-card-stat"><div class="farm-card-stat-value">${plotCount}</div><div class="farm-card-stat-label">Khu Vực</div></div>
            </div>
            <div class="farm-card-actions">
                <button class="btn-secondary" data-event="click" data-handler="viewFarm" data-arg="${f.id}">Chi tiết</button>
                <button class="btn-secondary" style="flex:1" data-event="click" data-permission="plots:read" data-handler="goToPlotsByFarm" data-arg="${f.id}"><i class="fa-solid fa-map-marked-alt"></i> Xem Khu Vực</button>
                <button class="btn-icon-sm" title="Sửa" data-event="click" data-permission="farms:update" data-handler="openFarmModal" data-arg="${f.id}"><i class="fa-solid fa-pen"></i></button>
                <button class="btn-icon-sm text-danger" title="Khóa" data-event="click" data-permission="farms:status" data-handler="toggleFarmStatus" data-arg="${f.id}"><i class="fa-solid ${f.status === 'ACTIVE' ? 'fa-ban' : 'fa-check'}"></i></button>
            </div>
        </div>`;
    }

    window.goToPlotsByFarm = function (id) { plotFarmFilter = id; plotFarmFilterName = farms.find(f => f.id === id)?.name || ''; plotPage = 0; switchTab('plots'); };
    window.toggleFarmStatus = id => changeStatus('farms', id, farms);

    // ─── PLOTS ───────────────────────────────────────────────────────────────
    function renderPlots() {
        const filterEl = document.getElementById('plotFarmFilter');
        filterEl.style.display = plotFarmFilter ? 'flex' : 'none';
        document.getElementById('plotFarmFilterName').textContent = plotFarmFilterName;

        let filtered = plots;
        if (plotFarmFilter) filtered = filtered.filter(p => p.farmId === plotFarmFilter);
        filtered = filterArr(filtered, plotSearch);
        const { items, total } = paginate(filtered, plotPage, PAGE_SIZE);
        const tbody = document.getElementById('plotsTableBody');
        tbody.innerHTML = items.length === 0 ? `<tr><td colspan="6" class="empty-state">Trống</td></tr>` : 
            items.map(p => `<tr>
                <td><strong>${esc(p.name)}</strong></td>
                <td><a href="#" data-event="click" data-handler="goToPlotsByFarm" data-arg="${p.farmId}">${esc(p.farmName)}</a></td>
                <td>${p.areaHa} ha</td>
                <td>${esc(p.soilType || '—')}</td>
                <td><span class="status-badge ${p.status === 'ACTIVE' ? 'status-active' : 'status-inactive'}">${p.status}</span></td>
                <td>
                    <button class="btn-icon-sm" data-event="click" data-handler="viewPlot" data-arg="${p.id}">Chi tiết</button>
                    <button class="btn-icon-sm" title="Xem mùa vụ" data-event="click" data-permission="crops:read" data-handler="goToSeasonsByPlot" data-arg="${p.id}"><i class="fa-solid fa-cloud-sun"></i></button>
                    <button class="btn-icon-sm" data-event="click" data-permission="plots:update" data-handler="openPlotModal" data-arg="${p.id}"><i class="fa-solid fa-pen"></i></button>
                    <button class="btn-icon-sm text-danger" data-event="click" data-permission="plots:status" data-handler="togglePlotStatus" data-arg="${p.id}"><i class="fa-solid fa-ban"></i></button>
                </td>
            </tr>`).join('');
        renderPagination('paginationPlots', total, plotPage, PAGE_SIZE, pg => { plotPage = pg; renderPlots(); });
    }

    window.clearPlotFarmFilter = function () { plotFarmFilter = null; renderPlots(); };
    window.goToSeasonsByPlot = function (id) { seasonPlotFilter = id; seasonPlotFilterName = plots.find(p => p.id === id)?.name || ''; seasonPage = 0; switchTab('seasons'); };
    window.togglePlotStatus = id => changeStatus('plots', id, plots);

    // ─── CROPS ───────────────────────────────────────────────────────────────
    function renderCrops() {
        let filtered = filterArr(crops, cropSearch);
        const { items, total } = paginate(filtered, cropPage, PAGE_SIZE);
        const tbody = document.getElementById('cropsTableBody');
        tbody.innerHTML = items.length === 0 ? `<tr><td colspan="6" class="empty-state">Trống</td></tr>` : 
            items.map(c => `<tr>
                <td><strong>${esc(c.name)}</strong></td>
                <td><i>${esc(c.scientificName || '—')}</i></td>
                <td>${esc(c.category || '—')}</td>
                <td>${c.growthDays ? c.growthDays + ' ngày' : '—'}</td>
                <td><span class="status-badge ${c.status === 'ACTIVE' ? 'status-active' : 'status-inactive'}">${c.status}</span></td>
                <td>
                    <button class="btn-icon-sm" data-event="click" data-handler="viewCrop" data-arg="${c.id}">Chi tiết</button>
                    <button class="btn-icon-sm" data-event="click" data-permission="crops:update" data-handler="openCropModal" data-arg="${c.id}"><i class="fa-solid fa-pen"></i></button>
                    <button class="btn-icon-sm text-danger" data-event="click" data-permission="crops:status" data-handler="toggleCropStatus" data-arg="${c.id}"><i class="fa-solid fa-ban"></i></button>
                </td>
            </tr>`).join('');
        renderPagination('paginationCrops', total, cropPage, PAGE_SIZE, pg => { cropPage = pg; renderCrops(); });
    }
    window.toggleCropStatus = id => changeStatus('crops', id, crops);

    // ─── SEASONS ─────────────────────────────────────────────────────────────
    function renderSeasons() {
        const filterEl = document.getElementById('seasonPlotFilter');
        filterEl.style.display = seasonPlotFilter ? 'flex' : 'none';
        document.getElementById('seasonPlotFilterName').textContent = seasonPlotFilterName;

        let filtered = seasons;
        if (seasonPlotFilter) filtered = filtered.filter(s => s.plotId === seasonPlotFilter);
        filtered = filterArr(filtered, seasonSearch);
        const { items, total } = paginate(filtered, seasonPage, PAGE_SIZE);
        const tbody = document.getElementById('seasonsTableBody');
        
        const statusMap = {
            'PLANNED': 'status-planned', 'IN_PROGRESS': 'status-inprogress',
            'COMPLETED': 'status-completed', 'CANCELLED': 'status-cancelled'
        };

        tbody.innerHTML = items.length === 0 ? `<tr><td colspan="5" class="empty-state">Trống</td></tr>` : 
            items.map(s => `<tr>
                <td>#${s.id}</td>
                <td><strong>${esc(s.cropName)}</strong></td>
                <td>${esc(s.plotName)}<br><small class="text-muted">${esc(s.farmName)}</small></td>
                <td><span class="status-badge ${statusMap[s.status] || ''}">${s.status}</span></td>
                <td><button class="btn-icon-sm" data-event="click" data-permission="crops:update" data-handler="openSeasonModal" data-arg="${s.id}"><i class="fa-solid fa-pen"></i> Cập nhật</button></td>
            </tr>`).join('');
        renderPagination('paginationSeasons', total, seasonPage, PAGE_SIZE, pg => { seasonPage = pg; renderSeasons(); });
    }
    window.clearSeasonPlotFilter = function () { seasonPlotFilter = null; renderSeasons(); };

    // ─── MODAL LOGIC ─────────────────────────────────────────────────────────
    window.openFarmModal = function (id) {
        const f = farms.find(x => x.id === id);
        document.getElementById('farmId').value = id || '';
        document.getElementById('farmName').value = f ? f.name : '';
        document.getElementById('farmAddress').value = f ? f.address : '';
        document.getElementById('farmAreaHa').value = f ? f.areaHa : '';
        document.getElementById('farmDescription').value = f ? (f.description||'') : '';
        openModal('farmModal');
    };
    window.submitFarm = async function (e) {
        e.preventDefault();
        const id = parseInt(document.getElementById('farmId').value);
        const data = {
            name: document.getElementById('farmName').value.trim(),
            address: document.getElementById('farmAddress').value.trim(),
            areaHa: document.getElementById('farmAreaHa').value,
            description: document.getElementById('farmDescription').value.trim()
        };
        await saveRecord('farms', id, data); closeModal('farmModal');
    };

    window.openPlotModal = function (id) {
        const p = plots.find(x => x.id === id);
        document.getElementById('plotId').value = id || '';
        const sel = document.getElementById('plotFarmId');
        sel.innerHTML = farms.filter(f => f.status === 'ACTIVE' || f.id === p?.farmId).map(f => `<option value="${f.id}" ${p && p.farmId === f.id ? 'selected' : ''}>${esc(f.name)}</option>`).join('');
        sel.disabled = Boolean(p);
        if (!p && plotFarmFilter) sel.value = plotFarmFilter;
        document.getElementById('plotName').value = p ? p.name : '';
        document.getElementById('plotAreaHa').value = p ? p.areaHa : '';
        document.getElementById('plotSoilType').value = p ? (p.soilType||'') : '';
        document.getElementById('plotLocation').value = p ? (p.location||'') : '';
        document.getElementById('plotDescription').value = p ? (p.description||'') : '';
        openModal('plotModal');
    };
    window.submitPlot = async function (e) {
        e.preventDefault();
        const id = parseInt(document.getElementById('plotId').value);
        const farmId = parseInt(document.getElementById('plotFarmId').value);
        const farm = farms.find(f => f.id === farmId);
        if (!farm) throw new Error('Vui lòng chọn trang trại hợp lệ.');
        const data = {
            farmId, farmName: farm.name,
            name: document.getElementById('plotName').value.trim(),
            areaHa: document.getElementById('plotAreaHa').value,
            soilType: document.getElementById('plotSoilType').value.trim(),
            location: document.getElementById('plotLocation').value.trim(),
            description: document.getElementById('plotDescription').value.trim()
        };
        
        const totalUsed = plots.filter(x => x.farmId === farmId && x.id !== id).reduce((s, x) => s + Math.round(x.areaHa * 100), 0);
        if (totalUsed + Math.round(Number(data.areaHa) * 100) > Math.round(farm.areaHa * 100)) { showToast('Lỗi', 'Tổng diện tích vượt quá diện tích trang trại', 'danger'); return; }

        delete data.farmName; if (id) delete data.farmId;
        await saveRecord('plots', id, data); closeModal('plotModal');
    };

    window.openCropModal = function (id) {
        const c = crops.find(x => x.id === id);
        document.getElementById('cropId').value = id || '';
        document.getElementById('cropName').value = c ? c.name : '';
        document.getElementById('cropScientificName').value = c ? (c.scientificName||'') : '';
        document.getElementById('cropCategory').value = c ? (c.category||'') : '';
        document.getElementById('cropGrowthDays').value = c && c.growthDays ? c.growthDays : '';
        document.getElementById('cropDescription').value = c ? (c.description||'') : '';
        openModal('cropModal');
    };
    window.submitCrop = async function (e) {
        e.preventDefault();
        const id = parseInt(document.getElementById('cropId').value);
        const gDays = document.getElementById('cropGrowthDays').value;
        const data = {
            name: document.getElementById('cropName').value.trim(),
            scientificName: document.getElementById('cropScientificName').value.trim(),
            category: document.getElementById('cropCategory').value.trim(),
            growthDays: gDays || null,
            description: document.getElementById('cropDescription').value.trim()
        };
        await saveRecord('crops', id, data); closeModal('cropModal');
    };

    window.openSeasonModal = function (id) {
        const s = seasons.find(x => x.id === id);
        document.getElementById('seasonId').value = id || '';
        document.getElementById('seasonPlotId').innerHTML = plots.filter(p => p.id === s?.plotId || (p.status === 'ACTIVE' && farms.some(f => f.id === p.farmId && f.status === 'ACTIVE'))).map(p => `<option value="${p.id}" ${s && s.plotId === p.id ? 'selected' : ''}>${esc(p.name)} (${esc(p.farmName)})</option>`).join('');
        document.getElementById('seasonCropId').innerHTML = crops.filter(c => c.status === 'ACTIVE' || c.id === s?.cropId).map(c => `<option value="${c.id}" ${s && s.cropId === c.id ? 'selected' : ''}>${esc(c.name)}</option>`).join('');
        document.getElementById('seasonPlotId').disabled = Boolean(s);
        document.getElementById('seasonCropId').disabled = Boolean(s);
        if (!s && seasonPlotFilter) document.getElementById('seasonPlotId').value = seasonPlotFilter;
        document.getElementById('seasonStatus').value = s ? s.status : 'PLANNED';
        openModal('seasonModal');
    };
    window.submitSeason = async function (e) {
        e.preventDefault();
        const id = Number(document.getElementById('seasonId').value);
        const status = document.getElementById('seasonStatus').value;
        await api.write('seasons'+(id ? '/'+id+'/status' : ''),id ? 'PUT' : 'POST', id ? {status} : {
            plotId: Number(document.getElementById('seasonPlotId').value),
            cropId: Number(document.getElementById('seasonCropId').value), status
        });
        await load(); closeModal('seasonModal'); showToast('Thành công','Đã lưu mùa vụ.');
    };

    // ─── UTILS ───────────────────────────────────────────────────────────────
    function renderPagination(c, total, page, size, callback) {
        const root = document.getElementById(c); root.replaceChildren();
        const pages = Math.ceil(total / size);
        if (pages <= 1) return;
        for (let i = 0; i < pages; i++) {
            const button = document.createElement('button'); button.textContent = i+1;
            button.className = 'page-btn'+(i===page ? ' active' : '');
            button.addEventListener('click',()=> { callback(i); applyPermissions(); }); root.append(button);
        }
    }
    function openModal(id) {
        const el = document.getElementById(id); el.inert = false; el.classList.add('active');
        el.querySelector('input:not([type="hidden"]),select,button')?.focus();
    }
    window.closeModal = function(id) { const el = document.getElementById(id); el.inert = true; el.classList.remove('active'); };
    function esc(value) { return String(value ?? '').replace(/[&<>"']/g, ch => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[ch])); }

    const labels = { id:'Mã', name:'Tên', address:'Địa chỉ', areaHa:'Diện tích (ha)', description:'Mô tả', status:'Trạng thái', createdAt:'Ngày tạo', farmName:'Trang trại', soilType:'Loại đất', location:'Vị trí', scientificName:'Tên khoa học', category:'Phân loại', growthDays:'Số ngày sinh trưởng' };
    const view = (rows, id) => { const row = rows.find(r=>r.id===id); if(row) showRecord(row.name,Object.entries(row).filter(([key])=>labels[key]).map(([key,value])=>[labels[key],value])); };
    window.viewFarm = id => view(farms,id); window.viewPlot = id => view(plots,id); window.viewCrop = id => view(crops,id);
    const handlers = new Set(['viewFarm','viewPlot','viewCrop','switchTab','debounceSearch','clearPlotFarmFilter','clearSeasonPlotFilter','closeModal',
        'submitFarm','submitPlot','submitCrop','submitSeason','openFarmModal','openPlotModal','openCropModal','openSeasonModal',
        'toggleFarmStatus','togglePlotStatus','toggleCropStatus','goToPlotsByFarm','goToSeasonsByPlot']);
    for (const kind of ['click','input','submit']) document.addEventListener(kind, async event => {
        const target = event.target.closest('[data-event="'+kind+'"]');
        if (!target || !handlers.has(target.dataset.handler)) return;
        if (kind !== 'input') event.preventDefault();
        if (target.dataset.busy) return;
        target.dataset.busy = 'true';
        const submit = kind === 'submit' ? target.querySelector('[type="submit"]') : null;
        if (submit) submit.disabled = true;
        try {
            const raw = target.dataset.arg;
            await window[target.dataset.handler](raw === 'event' ? event : /^\d+$/.test(raw) ? Number(raw) : raw || undefined);
            applyPermissions();
        } catch (error) { showToast('Lỗi', error.message, 'danger'); }
        finally { delete target.dataset.busy; if(submit) submit.disabled = false; }
    });
    document.addEventListener('keydown', event => { if(event.key === 'Escape') document.querySelectorAll('.modal-overlay.active').forEach(m => closeModal(m.id)); });
    document.addEventListener('DOMContentLoaded', async () => {
        document.querySelectorAll('.modal-overlay').forEach(modal => { modal.inert = true; });
        currentTab = ['farms','plots','crops'].find(type => api.has(type+':read')) || 'farms';
        document.getElementById('btnAdd').disabled = true;
        try { await load(); document.getElementById('btnAdd').disabled = false; }
        catch(error) { showToast('Lỗi tải dữ liệu',error.message,'danger'); }
    });
})();
