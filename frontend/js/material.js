(function () {
    'use strict';
    const api = window.FarmApi;
    const store = { normalize: value => String(value ?? '').trim().toLocaleLowerCase('vi') };
    const paths = { categories: 'material-categories', materials: 'materials', warehouses: 'warehouses', inventory: 'inventory' };
    const permission = (type, operation) => type === 'inventory' ? (operation === 'read' ? 'warehouses:read' : 'inventory:manage') : (type === 'categories' ? 'materials' : type)+':'+operation;
    const can = (type, operation) => api.has(permission(type, operation));
    async function reload() {
        const values = await Promise.all(Object.keys(paths).map(type => api.all(paths[type], permission(type, 'read'))));
        data = Object.fromEntries(Object.keys(paths).map((key,i)=>[key,values[i]]));
        farms = await api.all('farms','farms:read');
        stats(); relationOptions(); render();
    }
    const $ = id => document.getElementById(id);
    const escape = value => String(value ?? '').replace(/[&<>"']/g, ch => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[ch]));
    const number = value => new Intl.NumberFormat('vi-VN', { maximumFractionDigits: 3 }).format(value);
    const config = {
        materials: { title: 'Danh sách vật tư', add: 'Thêm vật tư', edit: 'Sửa vật tư', description: 'Thông tin, đơn vị tính và phân loại vật tư.', search: 'Tìm tên vật tư, nhà sản xuất…', columns: ['Vật tư', 'Nhóm vật tư', 'Đơn vị', 'Nhà sản xuất', 'Trạng thái', 'Thao tác'] },
        categories: { title: 'Nhóm vật tư', add: 'Thêm nhóm vật tư', edit: 'Sửa nhóm vật tư', description: 'Phân loại vật tư để quản lý và tra cứu thuận tiện.', search: 'Tìm tên nhóm, mô tả…', columns: ['Nhóm vật tư', 'Mô tả', 'Số vật tư', 'Trạng thái', 'Thao tác'] },
        warehouses: { title: 'Kho lưu trữ', add: 'Thêm kho', edit: 'Sửa thông tin kho', description: 'Thông tin kho và vị trí lưu trữ theo trang trại.', search: 'Tìm tên kho, vị trí…', columns: ['Kho', 'Trang trại', 'Vị trí', 'Sức chứa', 'Trạng thái', 'Thao tác'] },
        inventory: { title: 'Tồn kho vật tư', add: 'Cập nhật tồn kho', edit: 'Cập nhật tồn kho', description: 'Số lượng hiện có của từng vật tư tại mỗi kho.', search: 'Tìm tên vật tư, tên kho…', columns: ['Vật tư', 'Kho lưu trữ', 'Số lượng', 'Tình trạng', 'Cập nhật gần nhất', 'Thao tác'] }
    };
    const PAGE_SIZE = 7;
    let data, farms, tab = 'materials', page = 0, editingId = null, pendingStatus = null, noticeTimer;
    const filters = Object.fromEntries(Object.keys(config).map(key => [key, { search: '', relation: '', status: '' }]));
    const find = (type, id) => data[type].find(row => row.id === id);
    const farmName = id => farms.find(f => f.id === id)?.name || `Trang trại #${id} (không có trong danh sách)`;
    const badge = status => `<span class="inventory-badge ${status === 'ACTIVE' ? 'active' : 'inactive'}">${status === 'ACTIVE' ? 'Hoạt động' : 'Ngừng hoạt động'}</span>`;
    const option = (value, name) => `<option value="${escape(value)}">${escape(name)}</option>`;
    function notify(message) {
        clearTimeout(noticeTimer);
        $('inventoryNotice').textContent = message;
        $('inventoryNotice').hidden = false;
        noticeTimer = setTimeout(() => { $('inventoryNotice').hidden = true; }, 4500);
    }
    function error(id, message) { $(id).textContent = message; $(id).hidden = false; }
    function stats() {
        $('statMaterials').textContent = number(data.materials.filter(r => r.status === 'ACTIVE').length);
        $('statCategories').textContent = number(data.categories.length);
        $('statWarehouses').textContent = number(data.warehouses.filter(r => r.status === 'ACTIVE').length);
        $('statEmpty').textContent = number(data.inventory.filter(r => r.quantity === 0).length);
    }
    function relationOptions() {
        const list = tab === 'materials' ? data.categories : tab === 'warehouses' ? farms : data.warehouses;
        const label = tab === 'materials' ? 'nhóm vật tư' : tab === 'warehouses' ? 'trang trại' : 'kho';
        $('relationFilter').hidden = tab === 'categories';
        $('relationFilter').setAttribute('aria-label', `Lọc theo ${label}`);
        $('relationFilter').innerHTML = option('', `Tất cả ${label}`) + list.map(row => option(row.id, row.name)).join('');
        $('relationFilter').value = filters[tab].relation;
        if ($('relationFilter').selectedIndex < 0) filters[tab].relation = $('relationFilter').value = '';
    }
    function switchTab(nextTab) {
        if (!can(nextTab, 'read')) return;
        tab = nextTab; page = 0;
        $('addRecord').hidden = !can(tab, 'create');
        document.querySelectorAll('[data-tab]').forEach(button => {
            button.hidden = !can(button.dataset.tab, 'read');
            const selected = button.dataset.tab === tab;
            button.classList.toggle('active', selected);
            button.setAttribute('aria-selected', String(selected));
            button.tabIndex = selected ? 0 : -1;
        });
        $('inventoryPanel').setAttribute('aria-labelledby', `tab-${tab}`);
        $('addRecord').querySelector('span').textContent = config[tab].add;
        $('listTitle').textContent = $('tableCaption').textContent = config[tab].title;
        $('listDescription').textContent = config[tab].description;
        $('searchRecords').placeholder = config[tab].search;
        $('searchRecords').value = filters[tab].search;
        relationOptions();
        $('statusFilter').innerHTML = tab === 'inventory'
            ? option('', 'Tất cả tình trạng') + option('AVAILABLE', 'Còn tồn kho') + option('EMPTY', 'Tồn kho bằng 0')
            : option('', 'Tất cả trạng thái') + option('ACTIVE', 'Hoạt động') + option('INACTIVE', 'Ngừng hoạt động');
        $('statusFilter').value = filters[tab].status;
        render();
    }
    function actions(row) {
        const edit = !can(tab, 'update') ? '' : `<button type="button" data-action="edit" data-id="${row.id}" aria-label="${tab === 'inventory' ? 'Cập nhật tồn kho' : 'Sửa ' + escape(row.name)}"><i class="fa-solid fa-pen" aria-hidden="true"></i> ${tab === 'inventory' ? 'Cập nhật' : 'Sửa'}</button>`;
        if (tab === 'inventory') {
            const active = find('warehouses', row.warehouseId)?.status === 'ACTIVE' && find('materials', row.materialId)?.status === 'ACTIVE';
            return active ? edit : '<span class="record-meta">Kho hoặc vật tư đã ngừng</span>';
        }
        return `<button type="button" data-action="view" data-id="${row.id}">Chi tiết</button>${tab === 'warehouses' ? `<button type="button" data-action="stock" data-id="${row.id}" aria-label="Xem tồn kho ${escape(row.name)}">Tồn kho</button>` : ''}${edit}${!can(tab, 'status') ? '' : `<button type="button" data-action="status" data-id="${row.id}" aria-label="${row.status === 'ACTIVE' ? 'Ngừng' : 'Kích hoạt'} ${escape(row.name)}">${row.status === 'ACTIVE' ? 'Ngừng' : 'Kích hoạt'}</button>`}`;
    }
    function cells(row) {
        const name = `<span class="record-name">${escape(row.name)}</span><span class="record-meta">#${String(row.id).padStart(3, '0')}</span>`;
        if (tab === 'materials') return [name, escape(find('categories', row.categoryId)?.name || row.categoryName), escape(row.unit), escape(row.manufacturer || '—'), badge(row.status)];
        if (tab === 'categories') return [name, escape(row.description || '—'), number(data.materials.filter(m => m.categoryId === row.id).length), badge(row.status)];
        if (tab === 'warehouses') return [name, escape(row.farmName || farmName(row.farmId)), escape(row.location || '—'), escape(row.capacityText || 'Chưa ghi nhận'), badge(row.status)];
        const material = find('materials', row.materialId), warehouse = find('warehouses', row.warehouseId);
        const date = new Date(row.lastUpdated);
        return [
            `<span class="record-name">${escape(material?.name || row.materialName)}</span><span class="record-meta">${escape(find('categories', material?.categoryId)?.name)}</span>`,
            `<span class="record-name">${escape(warehouse?.name || row.warehouseName)}</span><span class="record-meta">${escape(farmName(warehouse?.farmId))}</span>`,
            `<span class="inventory-quantity">${number(row.quantity)}</span> <span>${escape(material?.unit || row.unit)}</span>`,
            `<span class="inventory-badge ${row.quantity === 0 ? 'empty' : 'active'}">${row.quantity === 0 ? 'Hết tồn kho' : 'Còn tồn kho'}</span>`,
            Number.isNaN(date.getTime()) ? '—' : escape(date.toLocaleString('vi-VN', { dateStyle: 'short', timeStyle: 'short' }))
        ];
    }
    function render() {
        const f = filters[tab];
        const rows = data[tab].filter(row => {
            const searchText = tab === 'inventory' ? `${row.materialName} ${row.warehouseName}` : `${row.name} ${row.manufacturer || ''} ${row.location || ''} ${row.description || ''}`;
            const relation = tab === 'materials' ? row.categoryId : tab === 'warehouses' ? row.farmId : row.warehouseId;
            return (!f.search || store.normalize(searchText).includes(store.normalize(f.search))) && (!f.relation || relation === Number(f.relation)) && (!f.status || (tab === 'inventory' ? (f.status === 'EMPTY' ? row.quantity === 0 : row.quantity > 0) : row.status === f.status));
        });
        const pages = Math.max(1, Math.ceil(rows.length / PAGE_SIZE));
        page = Math.min(page, pages - 1);
        $('tableHead').innerHTML = `<tr>${config[tab].columns.map(text => `<th scope="col">${text}</th>`).join('')}</tr>`;
        $('tableBody').innerHTML = rows.slice(page * PAGE_SIZE, (page + 1) * PAGE_SIZE).map(row => `<tr>${cells(row).map(cell => `<td>${cell}</td>`).join('')}<td><div class="record-actions">${actions(row)}</div></td></tr>`).join('');
        $('recordCount').textContent = `${number(rows.length)} bản ghi`;
        $('emptyState').hidden = rows.length > 0;
        const hasFilter = Boolean(f.search || f.relation || f.status);
        $('emptyTitle').textContent = hasFilter ? 'Không tìm thấy kết quả' : 'Chưa có dữ liệu';
        $('emptyDescription').textContent = hasFilter ? 'Thử từ khóa khác hoặc xóa bộ lọc để xem toàn bộ dữ liệu.' : `Chọn “${config[tab].add}” để tạo bản ghi đầu tiên.`;
        $('pageSummary').textContent = rows.length ? `Hiển thị ${page * PAGE_SIZE + 1}–${Math.min((page + 1) * PAGE_SIZE, rows.length)} / ${number(rows.length)} bản ghi` : '0 bản ghi';
        $('pageNumber').textContent = `${page + 1} / ${pages}`;
        $('previousPage').disabled = page === 0;
        $('nextPage').disabled = page >= pages - 1;
    }
    function field(key, label, value = '', max = 100, required = false) {
        return `<div class="form-group"><label for="field-${key}">${label}${required ? ' *' : ''}</label><input type="text" id="field-${key}" name="${key}" maxlength="${max}" value="${escape(value)}" ${required ? 'required' : ''}></div>`;
    }
    function select(key, label, rows, value, locked = false) {
        // Locked relationships match the update contracts (categoryId / farmId cannot change).
        const options = rows.filter(row => row.status === 'ACTIVE' || row.id === value);
        return `<div class="form-group"><label for="field-${key}">${label} *</label><select id="field-${key}" name="${key}" required ${locked ? 'disabled' : ''}>${option('', `Chọn ${label.toLocaleLowerCase('vi')}`)}${options.map(row => `<option value="${row.id}" ${row.id === value ? 'selected' : ''}>${escape(row.name)}${row.status !== 'ACTIVE' ? ' (ngừng hoạt động)' : ''}</option>`).join('')}</select></div>`;
    }
    function openForm(id = null) {
        editingId = id;
        const row = id == null ? {} : find(tab, id);
        if (!row) return;
        $('formError').hidden = true;
        $('saveRecord').disabled = false;
        $('dialogTitle').textContent = id == null ? config[tab].add : config[tab].edit;
        $('saveRecord').textContent = tab === 'inventory' ? 'Lưu số tồn mới' : 'Lưu';
        let html = '<p class="inventory-help">Các trường có dấu * là bắt buộc.</p>';
        if (tab === 'materials') html += select('categoryId', 'Nhóm vật tư', data.categories, row.categoryId, id != null);
        if (tab === 'warehouses') html += select('farmId', 'Trang trại', farms, row.farmId, id != null);
        if (tab !== 'inventory') html += field('name', tab === 'materials' ? 'Tên vật tư' : tab === 'categories' ? 'Tên nhóm vật tư' : 'Tên kho', row.name, tab === 'materials' ? 150 : 100, true);
        if (tab === 'materials') html += `<div class="form-row">${field('unit', 'Đơn vị tính', row.unit, 50, true)}${field('manufacturer', 'Nhà sản xuất', row.manufacturer, 150)}</div>`;
        if (tab === 'warehouses') html += field('location', 'Vị trí', row.location, 255) + field('capacityText', 'Sức chứa (ví dụ: 250 m² hoặc 10 tấn)', row.capacityText, 100);
        if (tab === 'categories' || tab === 'materials') html += `<div class="form-group"><label for="field-description">Mô tả</label><textarea id="field-description" name="description" rows="3" maxlength="1000">${escape(row.description)}</textarea></div>`;
        if (tab === 'inventory') {
            html += select('warehouseId', 'Kho lưu trữ', data.warehouses, row.warehouseId ?? Number(filters.inventory.relation), id != null);
            html += select('materialId', 'Vật tư', data.materials, row.materialId, id != null);
            html += '<p class="inventory-preview" id="stockPreview" aria-live="polite"></p><div class="form-group"><label for="field-quantity">Số lượng tồn mới *</label><input type="number" id="field-quantity" name="quantity" min="0" max="999999999.999" step="0.001" required inputmode="decimal" aria-describedby="quantityHelp"></div><p class="inventory-help" id="quantityHelp">Nhập số lượng thực tế sau kiểm kê. Giá trị này thay thế số tồn hiện tại, không cộng thêm. Tối đa 3 chữ số thập phân.</p>';
        }
        if (id != null && (tab === 'materials' || tab === 'warehouses')) html += `<p class="inventory-help">${tab === 'materials' ? 'Nhóm vật tư' : 'Trang trại'} được giữ cố định khi chỉnh sửa.</p>`;
        $('formFields').innerHTML = html;
        const missingChoice = tab === 'materials' && id == null && !data.categories.some(c => c.status === 'ACTIVE')
            ? 'Chưa có nhóm vật tư hoạt động. Hãy thêm hoặc kích hoạt nhóm ở tab Nhóm vật tư trước.'
            : tab === 'warehouses' && id == null && !farms.some(f => f.status === 'ACTIVE')
                ? 'Chưa có trang trại hoạt động. Hãy thêm hoặc kích hoạt trang trại ở trang Farms trước.'
                : tab === 'inventory' && (!data.warehouses.some(w => w.status === 'ACTIVE') || !data.materials.some(m => m.status === 'ACTIVE'))
                    ? 'Cần ít nhất một kho và một vật tư đang hoạt động để cập nhật tồn kho.' : '';
        if (missingChoice) { error('formError', missingChoice); $('saveRecord').disabled = true; }
        if (tab === 'inventory') {
            $('field-quantity').value = row.quantity ?? '';
            $('field-warehouseId').addEventListener('change', updateStockPreview);
            $('field-materialId').addEventListener('change', updateStockPreview);
            updateStockPreview();
        }
        $('recordDialog').showModal();
    }
    function updateStockPreview() {
        const warehouseId = Number($('field-warehouseId').value), materialId = Number($('field-materialId').value);
        const row = data.inventory.find(r => r.warehouseId === warehouseId && r.materialId === materialId);
        const material = find('materials', materialId);
        $('stockPreview').textContent = !warehouseId || !materialId ? 'Chọn kho và vật tư để xem số tồn hiện tại.' : row ? `Tồn hiện tại: ${number(row.quantity)} ${material.unit}. Lưu sẽ thay thế số lượng này.` : `Chưa có bản ghi tồn kho cho cặp kho – vật tư này. Đơn vị: ${material.unit}.`;
    }
    $('recordForm').addEventListener('submit', async event => {
        event.preventDefault();
        if ($('saveRecord').disabled) return;
        $('saveRecord').disabled = true;
        try {
            const input = Object.fromEntries(new FormData(event.currentTarget));
            const existing = editingId == null ? null : find(tab, editingId);
            let body;
            if(tab === 'inventory') body = {
                warehouseId: existing?.warehouseId ?? Number(input.warehouseId),
                materialId: existing?.materialId ?? Number(input.materialId), quantity: input.quantity
            };
            else {
                body = {...input};
                if(tab === 'materials' && !existing) body.categoryId = Number(body.categoryId);
                if(tab === 'warehouses' && !existing) body.farmId = Number(body.farmId);
            }
            await api.write(paths[tab]+(existing && tab !== 'inventory' ? '/'+editingId : ''), existing && tab !== 'inventory' ? 'PUT' : 'POST',body);
            $('recordDialog').close();
            await reload(); notify('Đã lưu dữ liệu trên máy chủ.');
        } catch(failure) { error($('recordDialog').open ? 'formError' : 'storageError',failure.message); }
        finally { $('saveRecord').disabled = false; }
    });
    $('tableBody').addEventListener('click', event => {
        const button = event.target.closest('button[data-action]');
        if (!button) return;
        const id = Number(button.dataset.id);
        if (button.dataset.action === 'view') {
            const labels = {id:'Mã',name:'Tên',description:'Mô tả',categoryName:'Nhóm vật tư',farmName:'Trang trại',unit:'Đơn vị',manufacturer:'Nhà sản xuất',location:'Vị trí',capacityText:'Sức chứa',status:'Trạng thái',createdAt:'Ngày tạo'};
            const row = find(tab,id);
            showRecord(row.name,Object.entries(row).filter(([key])=>labels[key]).map(([key,value])=>[labels[key],value]));
        }
        if (button.dataset.action === 'edit') openForm(id);
        if (button.dataset.action === 'stock') { filters.inventory.relation = String(id); filters.inventory.search = filters.inventory.status = ''; switchTab('inventory'); }
        if (button.dataset.action === 'status') {
            const row = find(tab, id);
            pendingStatus = { type: tab, id };
            $('statusTitle').textContent = row.status === 'ACTIVE' ? 'Ngừng hoạt động' : 'Kích hoạt lại';
            $('statusMessage').textContent = `${row.status === 'ACTIVE' ? 'Ngừng hoạt động' : 'Kích hoạt lại'} “${row.name}”? ${row.status === 'ACTIVE' ? 'Dữ liệu đã lưu vẫn được giữ lại. Bản ghi ngừng hoạt động không xuất hiện trong lựa chọn tạo mới.' : ''}`;
            $('statusError').hidden = true;
            $('statusDialog').showModal();
        }
    });
    $('confirmStatus').addEventListener('click', async () => {
        if (!pendingStatus || $('confirmStatus').disabled) return;
        $('confirmStatus').disabled = true;
        try {
            const {type,id} = pendingStatus;
            const row = find(type,id);
            await api.write(paths[type]+'/'+id+'/status','PUT',{status: row.status === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE'});
            pendingStatus = null; $('statusDialog').close();
            await reload(); notify('Đã cập nhật trạng thái.');
        } catch(failure) { error($('statusDialog').open ? 'statusError' : 'storageError',failure.message); }
        finally { $('confirmStatus').disabled = false; }
    });
    document.querySelectorAll('[data-close]').forEach(button => button.addEventListener('click', () => button.closest('dialog').close()));
    document.querySelectorAll('[data-tab]').forEach(button => {
        button.addEventListener('click', () => { if (data) switchTab(button.dataset.tab); });
        button.addEventListener('keydown', event => {
            const tabs = [...document.querySelectorAll('[data-tab]')];
            const i = tabs.indexOf(button);
            const target = { ArrowRight: (i + 1) % tabs.length, ArrowLeft: (i + tabs.length - 1) % tabs.length, Home: 0, End: tabs.length - 1 }[event.key];
            if (target !== undefined && data) { event.preventDefault(); tabs[target].focus(); switchTab(tabs[target].dataset.tab); }
        });
    });
    $('addRecord').addEventListener('click', () => openForm());
    for (const [id, key, eventName] of [['searchRecords', 'search', 'input'], ['relationFilter', 'relation', 'change'], ['statusFilter', 'status', 'change']]) {
        $(id).addEventListener(eventName, () => { if (data) { filters[tab][key] = $(id).value; page = 0; render(); } });
    }
    $('clearFilters').addEventListener('click', () => { if (data) { filters[tab] = { search: '', relation: '', status: '' }; switchTab(tab); } });
    $('previousPage').addEventListener('click', () => { if (data && page > 0) { page--; render(); } });
    $('nextPage').addEventListener('click', () => { if (data) { page++; render(); } });
    async function initialize() {
        $('addRecord').disabled = true;
        try {
            tab = can('materials','read') ? 'materials' : 'warehouses';
            await reload(); $('storageError').hidden = true;
            $('addRecord').disabled = false; switchTab(tab);
        } catch(failure) {
            data = null; $('tableBody').replaceChildren(); error('storageError',failure.message);
        }
    }
    initialize();
})();
