const { test } = require('node:test');
const assert = require('node:assert/strict');
const store = require('../js/material-store.js');
function memory(initial = {}) {
    const values = new Map(Object.entries(initial));
    return { getItem: key => values.has(key) ? values.get(key) : null, setItem: (key, value) => values.set(key, value) };
}
const fixture = () => store.load(memory());

test('demo uses existing Farms IDs and respects an intentionally empty farm list', () => {
    const result = store.load(memory({ agri_farms: JSON.stringify([{ id: 42, name: 'Farm 42', status: 'ACTIVE' }]) }));
    assert.equal(result.data.warehouses[0].farmId, 42);
    assert.equal(result.data.warehouses.length, 1);
    assert.equal(store.load(memory({ agri_farms: '[]' })).data.warehouses.length, 0);
});
test('inventory updates replace stock and do not duplicate a warehouse/material pair', () => {
    const { data, farms } = fixture();
    const next = store.save(data, farms, 'inventory', null, { warehouseId: 1, materialId: 1, quantity: '12.375' });
    assert.equal(next.inventory.length, data.inventory.length);
    assert.equal(next.inventory[0].quantity, 12.375);
    assert.equal(data.inventory[0].quantity, 450);
});
test('zero stock is valid and quantities reject negatives, excess precision and overflow', () => {
    assert.equal(store.quantity('0'), 0);
    assert.equal(store.quantity('999999999.999'), 999999999.999);
    for (const value of ['-1', '', ' ', '1.2345', '1000000000', 'NaN', 'Infinity', '1e3']) assert.throws(() => store.quantity(value));
});
test('edit cannot silently move inventory, material category or warehouse farm', () => {
    const { data, farms } = fixture();
    const stock = store.save(data, farms, 'inventory', 1, { warehouseId: 2, materialId: 4, quantity: '0' });
    assert.equal(stock.inventory[0].warehouseId, 1);
    assert.equal(stock.inventory[0].materialId, 1);
    const materials = store.save(data, farms, 'materials', 1, { categoryId: 4, name: 'NPK', unit: 'kg' });
    assert.equal(materials.materials[0].categoryId, 1);
    const warehouses = store.save(data, farms, 'warehouses', 1, { farmId: 2, name: 'Kho A' });
    assert.equal(warehouses.warehouses[0].farmId, 1);
});
test('names are trimmed and duplicate detection ignores case', () => {
    const { data, farms } = fixture();
    assert.throws(() => store.save(data, farms, 'categories', null, { name: ' PHÂN BÓN ' }), /Tên đã tồn tại/);
    assert.throws(() => store.save(data, farms, 'materials', null, { categoryId: 1, name: '   ', unit: 'kg' }));
    const next = store.save(data, farms, 'categories', null, { name: '  Thiết bị mới  ' });
    assert.equal(next.categories.at(-1).name, 'Thiết bị mới');
});
test('warehouse name uniqueness is scoped to a farm', () => {
    const { data, farms } = fixture();
    assert.throws(() => store.save(data, farms, 'warehouses', null, { farmId: 1, name: data.warehouses[0].name }));
    assert.doesNotThrow(() => store.save(data, farms, 'warehouses', null, { farmId: 2, name: data.warehouses[0].name }));
});
test('inactive choices block new records and inventory adjustments while preserving history', () => {
    const { data, farms } = fixture();
    const next = store.setStatus(data, 'warehouses', 1);
    assert.equal(next.inventory.length, data.inventory.length);
    assert.equal(next.warehouses[0].status, 'INACTIVE');
    assert.throws(() => store.save(next, farms, 'inventory', 1, { quantity: '3' }));
    const categories = store.setStatus(data, 'categories', 1);
    assert.throws(() => store.save(categories, farms, 'materials', null, { categoryId: 1, name: 'Test', unit: 'kg' }));
});
test('stored empty data stays empty after reload', () => {
    const storage = memory();
    const data = { categories: [], materials: [], warehouses: [], inventory: [] };
    store.persist(storage, data);
    assert.deepEqual(store.load(storage).data, data);
});
test('failed persistence and corrupt storage are reported without overwriting existing data', () => {
    const storage = memory({ [store.KEY]: '{broken' });
    assert.throws(() => store.load(storage));
    assert.equal(storage.getItem(store.KEY), '{broken');
    assert.throws(() => store.persist({ setItem() { throw new Error('Quota exceeded'); } }, fixture().data), /Không thể lưu/);
});
test('save/reload preserves edited stock and rejects broken inventory references', () => {
    const { data, farms } = fixture();
    const storage = memory();
    const next = store.save(data, farms, 'inventory', null, { warehouseId: 2, materialId: 2, quantity: '7.125' });
    store.persist(storage, next);
    assert.equal(store.load(storage).data.inventory.at(-1).quantity, 7.125);
    next.inventory[0].materialId = 999;
    storage.setItem(store.KEY, JSON.stringify(next));
    assert.throws(() => store.load(storage));
});
