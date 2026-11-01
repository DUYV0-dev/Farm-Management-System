// Demo repository. Field names follow InventoryStore; no server writes are made.
(function (root) {
    'use strict';
    const KEY = 'agri_material_warehouse_v1';
    const normalize = value => String(value ?? '').trim().toLocaleLowerCase('vi');
    const defaults = () => ({
        categories: [
            { id: 1, name: 'Phân bón', description: 'Dinh dưỡng cho đất và cây trồng.', status: 'ACTIVE' },
            { id: 2, name: 'Hạt giống', description: 'Hạt giống và cây giống phục vụ canh tác.', status: 'ACTIVE' },
            { id: 3, name: 'Bảo vệ thực vật', description: 'Chế phẩm chăm sóc, phòng trừ sâu bệnh.', status: 'ACTIVE' },
            { id: 4, name: 'Dụng cụ', description: 'Dụng cụ lao động và thiết bị tưới.', status: 'ACTIVE' }
        ],
        materials: [
            { id: 1, categoryId: 1, name: 'Phân NPK 16-16-8', unit: 'kg', manufacturer: 'Bình Điền', description: 'Bảo quản nơi khô ráo.', status: 'ACTIVE' },
            { id: 2, categoryId: 1, name: 'Phân hữu cơ vi sinh', unit: 'kg', manufacturer: '', description: '', status: 'ACTIVE' },
            { id: 3, categoryId: 2, name: 'Hạt giống cà chua Beef', unit: 'gói', manufacturer: '', description: '', status: 'ACTIVE' },
            { id: 4, categoryId: 2, name: 'Giống lúa ST25', unit: 'kg', manufacturer: '', description: '', status: 'ACTIVE' },
            { id: 5, categoryId: 3, name: 'Chế phẩm Trichoderma', unit: 'kg', manufacturer: '', description: '', status: 'ACTIVE' },
            { id: 6, categoryId: 4, name: 'Kéo cắt cành', unit: 'cái', manufacturer: '', description: '', status: 'ACTIVE' },
            { id: 7, categoryId: 4, name: 'Ống tưới nhỏ giọt', unit: 'mét', manufacturer: '', description: '', status: 'ACTIVE' },
            { id: 8, categoryId: 3, name: 'Dung dịch neem', unit: 'lít', manufacturer: '', description: '', status: 'INACTIVE' }
        ],
        warehouses: [], inventory: []
    });
    const fallbackFarms = [
        { id: 1, name: 'Trang Trại Xanh Nguyên', status: 'ACTIVE' },
        { id: 2, name: 'Nông Trại Cần Thơ', status: 'ACTIVE' }
    ];
    function readFarms(storage) {
        const raw = storage.getItem('agri_farms');
        const farms = raw === null ? fallbackFarms : JSON.parse(raw);
        if (!Array.isArray(farms) || farms.some(f => !Number.isInteger(f.id) || typeof f.name !== 'string')) {
            throw new Error('Dữ liệu trang trại không hợp lệ. Vui lòng kiểm tra trang Farms.');
        }
        return farms;
    }
    function seed(farms) {
        const data = defaults();
        farms.filter(f => f.status === 'ACTIVE').slice(0, 2).forEach((farm, i) => {
            data.warehouses.push({ id: i + 1, farmId: farm.id, name: i ? 'Kho nông nghiệp' : 'Kho vật tư trung tâm', location: i ? 'Khu Bắc' : 'Khu A · Cổng chính', capacityText: i ? '120 m²' : '250 m²', status: 'ACTIVE' });
        });
        if (data.warehouses.length) {
            [450, 1200, 36, 0, 18.5, 24, 300].forEach((quantity, i) => data.inventory.push({ id: i + 1, warehouseId: 1, materialId: i + 1, quantity, lastUpdated: '2026-10-01T08:00:00Z' }));
        }
        if (data.warehouses.length > 1) data.inventory.push({ id: 8, warehouseId: 2, materialId: 4, quantity: 650, lastUpdated: '2026-10-02T08:00:00Z' });
        return data;
    }
    function validate(data) {
        if (!data || !['categories', 'materials', 'warehouses', 'inventory'].every(key => Array.isArray(data[key]))) throw new Error('Dữ liệu vật tư và kho không hợp lệ.');
        for (const key of ['categories', 'materials', 'warehouses', 'inventory']) {
            const ids = new Set();
            for (const row of data[key]) {
                if (!row || !Number.isInteger(row.id) || row.id <= 0 || ids.has(row.id)) throw new Error('Mã dữ liệu không hợp lệ.');
                ids.add(row.id);
                if (key !== 'inventory' && (typeof row.name !== 'string' || !['ACTIVE', 'INACTIVE'].includes(row.status))) throw new Error('Trạng thái dữ liệu không hợp lệ.');
            }
        }
        for (const m of data.materials) {
            if (!data.categories.some(c => c.id === m.categoryId) || typeof m.unit !== 'string') throw new Error('Nhóm hoặc đơn vị vật tư không hợp lệ.');
        }
        const pairs = new Set();
        for (const row of data.inventory) {
            const pair = `${row.warehouseId}:${row.materialId}`;
            if (!data.warehouses.some(w => w.id === row.warehouseId) || !data.materials.some(m => m.id === row.materialId) || !Number.isFinite(row.quantity) || row.quantity < 0 || row.quantity > 999999999.999 || pairs.has(pair)) throw new Error('Dữ liệu tồn kho không hợp lệ.');
            pairs.add(pair);
        }
        return data;
    }
    function load(storage) {
        const farms = readFarms(storage);
        const raw = storage.getItem(KEY);
        return { farms, data: raw === null ? seed(farms) : validate(JSON.parse(raw)) };
    }
    function quantity(value) {
        const text = String(value).trim();
        if (!/^\d+(\.\d{1,3})?$/.test(text) || Number(text) > 999999999.999) throw new Error('Số lượng phải từ 0 đến 999.999.999,999 và có tối đa 3 chữ số thập phân.');
        return Number(text);
    }
    function textField(input, key, max, required = false) {
        const value = String(input[key] ?? '').trim();
        if (required && !value) throw new Error('Vui lòng điền đầy đủ các trường bắt buộc, không chỉ nhập khoảng trắng.');
        if (value.length > max) throw new Error(`Nội dung vượt quá ${max} ký tự.`);
        return value;
    }
    function nextId(rows) { return rows.reduce((max, row) => Math.max(max, row.id), 0) + 1; }
    function save(data, farms, type, id, input) {
        const next = JSON.parse(JSON.stringify(data));
        const rows = next[type];
        if (!rows) throw new Error('Loại dữ liệu không hợp lệ.');
        const existing = id == null ? null : rows.find(row => row.id === id);
        if (id != null && !existing) throw new Error('Không tìm thấy bản ghi. Vui lòng tải lại trang.');
        if (type === 'inventory') {
            const warehouseId = existing ? existing.warehouseId : Number(input.warehouseId);
            const materialId = existing ? existing.materialId : Number(input.materialId);
            if (!next.warehouses.some(w => w.id === warehouseId && w.status === 'ACTIVE') || !next.materials.some(m => m.id === materialId && m.status === 'ACTIVE')) throw new Error('Vui lòng chọn kho và vật tư đang hoạt động.');
            const row = rows.find(item => item.warehouseId === warehouseId && item.materialId === materialId);
            const values = { quantity: quantity(input.quantity), lastUpdated: new Date().toISOString() };
            if (row) Object.assign(row, values);
            else rows.push({ id: nextId(rows), warehouseId, materialId, ...values });
        } else {
            const values = { name: textField(input, 'name', type === 'materials' ? 150 : 100, true) };
            if (type === 'categories' || type === 'materials') values.description = textField(input, 'description', 1000);
            if (type === 'materials') {
                values.categoryId = existing ? existing.categoryId : Number(input.categoryId);
                if (!existing && !next.categories.some(c => c.id === values.categoryId && c.status === 'ACTIVE')) throw new Error('Vui lòng chọn nhóm vật tư đang hoạt động.');
                values.unit = textField(input, 'unit', 50, true);
                values.manufacturer = textField(input, 'manufacturer', 150);
            }
            if (type === 'warehouses') {
                values.farmId = existing ? existing.farmId : Number(input.farmId);
                if (!existing && !farms.some(f => f.id === values.farmId && f.status === 'ACTIVE')) throw new Error('Vui lòng chọn trang trại đang hoạt động.');
                values.location = textField(input, 'location', 255);
                values.capacityText = textField(input, 'capacityText', 100);
            }
            if (rows.some(row => row.id !== id && normalize(row.name) === normalize(values.name) && (type !== 'warehouses' || row.farmId === values.farmId))) throw new Error('Tên đã tồn tại. Vui lòng sử dụng tên khác.');
            if (existing) Object.assign(existing, values);
            else rows.push({ id: nextId(rows), ...values, status: 'ACTIVE', createdAt: new Date().toISOString() });
        }
        return validate(next);
    }
    function setStatus(data, type, id) {
        if (!['categories', 'materials', 'warehouses'].includes(type)) throw new Error('Không thể đổi trạng thái.');
        const next = JSON.parse(JSON.stringify(data));
        const row = next[type].find(item => item.id === id);
        if (!row) throw new Error('Không tìm thấy bản ghi.');
        row.status = row.status === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE';
        return next;
    }
    function persist(storage, data) {
        try { storage.setItem(KEY, JSON.stringify(data)); }
        catch { throw new Error('Không thể lưu dữ liệu. Bộ nhớ trình duyệt đã đầy hoặc đang bị chặn.'); }
        return data;
    }
    const api = { KEY, load, save, setStatus, persist, quantity, normalize };
    if (typeof module !== 'undefined' && module.exports) module.exports = api;
    else root.MaterialStore = api;
})(globalThis);
