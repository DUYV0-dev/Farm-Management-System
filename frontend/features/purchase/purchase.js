(function () {
    'use strict';
    const api = window.FarmApi;
    const $ = id => document.getElementById(id);
    const escape = v => String(v ?? '').replace(/[&<>"']/g, ch => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[ch]));
    const money = v => new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' }).format(v ?? 0);
    const number = v => new Intl.NumberFormat('vi-VN', { maximumFractionDigits: 3 }).format(v ?? 0);

    let tab = 'suppliers';
    let suppliers = [], purchaseOrders = [], materials = [], warehouses = [], farms = [];
    let page = 0, noticeTimer, editingSupplierId = null;

    const statusBadge = (st) => {
        switch (st) {
            case 'DRAFT': return '<span class="inventory-badge badge-draft">Bản nháp</span>';
            case 'ORDERED': return '<span class="inventory-badge badge-ordered">Đã đặt hàng</span>';
            case 'RECEIVED': return '<span class="inventory-badge badge-received">Đã nhập kho</span>';
            case 'CANCELLED': return '<span class="inventory-badge badge-cancelled">Đã hủy</span>';
            case 'ACTIVE': return '<span class="inventory-badge active">Hoạt động</span>';
            case 'INACTIVE': return '<span class="inventory-badge inactive">Ngừng hoạt động</span>';
            default: return `<span class="inventory-badge">${escape(st)}</span>`;
        }
    };

    function notify(msg) {
        clearTimeout(noticeTimer);
        const el = $('inventoryNotice');
        el.textContent = msg;
        el.hidden = false;
        noticeTimer = setTimeout(() => { el.hidden = true; }, 4000);
    }

    async function loadData() {
        try {
            suppliers = (await api.all('suppliers', 'suppliers:read')) || [];
            purchaseOrders = (await api.all('purchase-orders', 'purchases:read')) || [];
            materials = (await api.all('materials', 'materials:read')) || [];
            warehouses = (await api.all('warehouses', 'warehouses:read')) || [];
            farms = (await api.all('farms', 'farms:read')) || [];
            updateStats();
            render();
        } catch (e) {
            console.error(e);
        }
    }

    function updateStats() {
        $('statSuppliers').textContent = suppliers.filter(s => s.status === 'ACTIVE').length;
        $('statOpenPOs').textContent = purchaseOrders.filter(p => p.status === 'DRAFT' || p.status === 'ORDERED').length;
        $('statReceivedPOs').textContent = purchaseOrders.filter(p => p.status === 'RECEIVED').length;
        const totalSpend = purchaseOrders
            .filter(p => p.status === 'RECEIVED' || p.status === 'ORDERED')
            .reduce((acc, p) => acc + (Number(p.totalAmount) || 0), 0);
        $('statTotalSpend').textContent = money(totalSpend);
    }

    function switchTab(nextTab) {
        tab = nextTab;
        page = 0;
        document.querySelectorAll('.inventory-tab').forEach(b => {
            const active = b.dataset.tab === tab;
            b.classList.toggle('active', active);
            b.setAttribute('aria-selected', String(active));
        });

        if (tab === 'suppliers') {
            $('addButtonText').textContent = 'Thêm nhà cung cấp';
            $('listTitle').textContent = 'Danh sách nhà cung cấp';
            $('listDescription').textContent = 'Thông tin đối tác cung cấp hạt giống, phân bón, thuốc và thiết bị nông nghiệp.';
            $('statusFilter').innerHTML = `
                <option value="">Tất cả trạng thái</option>
                <option value="ACTIVE">Hoạt động</option>
                <option value="INACTIVE">Ngừng hoạt động</option>
            `;
        } else {
            $('addButtonText').textContent = 'Tạo đơn mua vật tư';
            $('listTitle').textContent = 'Danh sách đơn mua vật tư (PO)';
            $('listDescription').textContent = 'Theo dõi đơn hàng từ lúc đặt, vận chuyển đến khi nhận hàng và cập nhật vào kho.';
            $('statusFilter').innerHTML = `
                <option value="">Tất cả trạng thái</option>
                <option value="DRAFT">Bản nháp</option>
                <option value="ORDERED">Đã đặt hàng</option>
                <option value="RECEIVED">Đã nhập kho</option>
                <option value="CANCELLED">Đã hủy</option>
            `;
        }
        render();
    }

    function render() {
        const query = $('searchRecords').value.trim().toLowerCase();
        const statusFilter = $('statusFilter').value;

        if (tab === 'suppliers') {
            $('tableHead').innerHTML = `
                <tr>
                    <th>Nhà cung cấp</th>
                    <th>Người liên hệ</th>
                    <th>Điện thoại / Email</th>
                    <th>Địa chỉ</th>
                    <th>Trạng thái</th>
                    <th>Thao tác</th>
                </tr>
            `;

            const filtered = suppliers.filter(s => {
                const matchQuery = !query || s.name.toLowerCase().includes(query)
                    || (s.contactName && s.contactName.toLowerCase().includes(query))
                    || (s.phone && s.phone.includes(query));
                const matchStatus = !statusFilter || s.status === statusFilter;
                return matchQuery && matchStatus;
            });

            $('recordCount').textContent = `${filtered.length} nhà cung cấp`;
            $('emptyState').hidden = filtered.length > 0;

            const rowsHtml = filtered.map(s => `
                <tr>
                    <td><strong>${escape(s.name)}</strong></td>
                    <td>${escape(s.contactName || '—')}</td>
                    <td>${escape(s.phone || '—')}<br><small style="color:#64748b;">${escape(s.email || '')}</small></td>
                    <td>${escape(s.address || '—')}</td>
                    <td>${statusBadge(s.status)}</td>
                    <td>
                        <button class="btn-secondary btn-sm-action edit-sup-btn" data-id="${s.id}">Sửa</button>
                        <button class="btn-secondary btn-sm-action toggle-sup-btn" data-id="${s.id}" data-status="${s.status}">
                            ${s.status === 'ACTIVE' ? 'Ngừng' : 'Kích hoạt'}
                        </button>
                    </td>
                </tr>
            `).join('');
            $('tableBody').innerHTML = rowsHtml;

            document.querySelectorAll('.edit-sup-btn').forEach(b => {
                b.addEventListener('click', () => openEditSupplier(Number(b.dataset.id)));
            });
            document.querySelectorAll('.toggle-sup-btn').forEach(b => {
                b.addEventListener('click', () => toggleSupplierStatus(Number(b.dataset.id), b.dataset.status));
            });

        } else {
            $('tableHead').innerHTML = `
                <tr>
                    <th>Mã đơn hàng</th>
                    <th>Nhà cung cấp</th>
                    <th>Kho tiếp nhận</th>
                    <th>Ngày đặt</th>
                    <th>Tổng tiền</th>
                    <th>Trạng thái</th>
                    <th>Thao tác</th>
                </tr>
            `;

            const filtered = purchaseOrders.filter(p => {
                const matchQuery = !query || p.poNumber.toLowerCase().includes(query)
                    || (p.supplierName && p.supplierName.toLowerCase().includes(query));
                const matchStatus = !statusFilter || p.status === statusFilter;
                return matchQuery && matchStatus;
            });

            $('recordCount').textContent = `${filtered.length} đơn mua hàng`;
            $('emptyState').hidden = filtered.length > 0;

            const rowsHtml = filtered.map(p => `
                <tr>
                    <td><strong>${escape(p.poNumber)}</strong></td>
                    <td>${escape(p.supplierName)}</td>
                    <td>${escape(p.warehouseName)}<br><small style="color:#64748b;">${escape(p.farmName)}</small></td>
                    <td>${escape(p.orderDate)}</td>
                    <td><strong>${money(p.totalAmount)}</strong></td>
                    <td>${statusBadge(p.status)}</td>
                    <td>
                        <button class="btn-secondary btn-sm-action view-po-btn" data-id="${p.id}">Chi tiết</button>
                        ${p.status === 'DRAFT' ? `<button class="btn-order btn-sm-action order-po-btn" data-id="${p.id}">Đặt hàng</button>` : ''}
                        ${(p.status === 'ORDERED' || p.status === 'DRAFT') ? `
                            <button class="btn-receive btn-sm-action receive-po-btn" data-id="${p.id}">Nhập kho</button>
                            <button class="btn-cancel-po btn-sm-action cancel-po-btn" data-id="${p.id}">Hủy</button>
                        ` : ''}
                    </td>
                </tr>
            `).join('');
            $('tableBody').innerHTML = rowsHtml;

            document.querySelectorAll('.view-po-btn').forEach(b => {
                b.addEventListener('click', () => openPoDetail(Number(b.dataset.id)));
            });
            document.querySelectorAll('.order-po-btn').forEach(b => {
                b.addEventListener('click', () => changePoStatus(Number(b.dataset.id), 'ORDERED'));
            });
            document.querySelectorAll('.receive-po-btn').forEach(b => {
                b.addEventListener('click', () => receivePo(Number(b.dataset.id)));
            });
            document.querySelectorAll('.cancel-po-btn').forEach(b => {
                b.addEventListener('click', () => changePoStatus(Number(b.dataset.id), 'CANCELLED'));
            });
        }
    }

    // ─── Supplier Dialog ──────────────────────────────────────────────────────
    function openAddSupplier() {
        editingSupplierId = null;
        $('supplierDialogTitle').textContent = 'Thêm nhà cung cấp mới';
        $('supplierForm').reset();
        $('supplierError').hidden = true;
        $('supplierDialog').showModal();
    }

    function openEditSupplier(id) {
        editingSupplierId = id;
        const s = suppliers.find(x => x.id === id);
        if (!s) return;
        $('supplierDialogTitle').textContent = 'Chỉnh sửa nhà cung cấp';
        $('supName').value = s.name || '';
        $('supContact').value = s.contactName || '';
        $('supPhone').value = s.phone || '';
        $('supEmail').value = s.email || '';
        $('supAddress').value = s.address || '';
        $('supTax').value = s.taxCode || '';
        $('supNotes').value = s.notes || '';
        $('supplierError').hidden = true;
        $('supplierDialog').showModal();
    }

    async function toggleSupplierStatus(id, currentStatus) {
        const next = currentStatus === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE';
        if (!confirm(`Bạn có chắc muốn ${next === 'ACTIVE' ? 'kích hoạt lại' : 'ngừng hoạt động'} nhà cung cấp này?`)) return;
        try {
            await api.write(`suppliers/${id}/status`, 'PUT', { status: next });
            notify('Đã cập nhật trạng thái nhà cung cấp.');
            await loadData();
        } catch (e) {
            alert(e.message || 'Lỗi cập nhật trạng thái');
        }
    }

    // ─── PO Creation Dialog ───────────────────────────────────────────────────
    function openAddPo() {
        $('poForm').reset();
        $('poError').hidden = true;

        const activeSups = suppliers.filter(s => s.status === 'ACTIVE');
        $('poSupplier').innerHTML = activeSups.map(s => `<option value="${s.id}">${escape(s.name)}</option>`).join('');

        const activeFarms = farms.filter(f => f.status === 'ACTIVE');
        $('poFarm').innerHTML = activeFarms.map(f => `<option value="${f.id}">${escape(f.name)}</option>`).join('');

        const activeWhs = warehouses.filter(w => w.status === 'ACTIVE');
        $('poWarehouse').innerHTML = activeWhs.map(w => `<option value="${w.id}">${escape(w.name)} (${escape(w.farmName)})</option>`).join('');

        $('itemsContainer').innerHTML = '';
        addItemRow(); // add initial item row
        recalcPoTotal();

        $('poDialog').showModal();
    }

    function addItemRow() {
        const row = document.createElement('div');
        row.className = 'item-row';
        const activeMats = materials.filter(m => m.status === 'ACTIVE');
        const opts = activeMats.map(m => `<option value="${m.id}">${escape(m.name)} (${escape(m.unit)})</option>`).join('');

        row.innerHTML = `
            <select class="item-mat" required style="flex:2;">${opts}</select>
            <input type="number" class="item-qty" placeholder="Số lượng" step="0.001" min="0.001" value="1" required style="flex:1;">
            <input type="number" class="item-price" placeholder="Đơn giá" step="100" min="0" value="0" required style="flex:1;">
            <button type="button" class="btn-cancel remove-item-btn" style="padding:4px 8px;">×</button>
        `;
        row.querySelector('.remove-item-btn').addEventListener('click', () => {
            if ($('itemsContainer').children.length > 1) {
                row.remove();
                recalcPoTotal();
            }
        });
        row.querySelector('.item-qty').addEventListener('input', recalcPoTotal);
        row.querySelector('.item-price').addEventListener('input', recalcPoTotal);

        $('itemsContainer').appendChild(row);
        recalcPoTotal();
    }

    function recalcPoTotal() {
        let total = 0;
        document.querySelectorAll('#itemsContainer .item-row').forEach(row => {
            const qty = Number(row.querySelector('.item-qty').value) || 0;
            const price = Number(row.querySelector('.item-price').value) || 0;
            total += qty * price;
        });
        $('poTotalDisplay').textContent = money(total);
    }

    // ─── PO Detail Dialog & Goods Receipt ─────────────────────────────────────
    async function openPoDetail(id) {
        try {
            const po = await api.write(`purchase-orders/${id}`, 'GET');
            const data = po.data || po;
            $('poDetailTitle').textContent = `Chi tiết đơn mua hàng: ${data.poNumber}`;

            const itemsHtml = data.items.map(it => `
                <tr>
                    <td><strong>${escape(it.materialName)}</strong></td>
                    <td>${escape(it.unit)}</td>
                    <td>${number(it.quantity)}</td>
                    <td>${number(it.receivedQuantity)}</td>
                    <td>${money(it.unitPrice)}</td>
                    <td><strong>${money(it.totalPrice)}</strong></td>
                </tr>
            `).join('');

            $('poDetailContent').innerHTML = `
                <div style="display:grid;grid-template-columns:1fr 1fr;gap:12px;margin-bottom:12px;background:#f8fafc;padding:12px;border-radius:6px;">
                    <div><strong>Nhà cung cấp:</strong> ${escape(data.supplierName)}</div>
                    <div><strong>Kho nhập:</strong> ${escape(data.warehouseName)} (${escape(data.farmName)})</div>
                    <div><strong>Ngày đặt:</strong> ${escape(data.orderDate)}</div>
                    <div><strong>Dự kiến giao:</strong> ${escape(data.expectedDeliveryDate || 'Chưa định')}</div>
                    <div><strong>Trạng thái:</strong> ${statusBadge(data.status)}</div>
                    <div><strong>Ngày nhập kho thực tế:</strong> ${escape(data.actualDeliveryDate || 'Chưa nhập')}</div>
                    <div style="grid-column:1/-1;"><strong>Tổng giá trị:</strong> <span style="font-size:1.1rem;color:#16a34a;font-weight:700;">${money(data.totalAmount)}</span></div>
                </div>
                <h4 style="margin-top:12px;margin-bottom:6px;">Danh mục vật tư trong đơn:</h4>
                <table class="po-items-table">
                    <thead>
                        <tr><th>Vật tư</th><th>Đơn vị</th><th>Số lượng đặt</th><th>Đã nhận</th><th>Đơn giá</th><th>Thành tiền</th></tr>
                    </thead>
                    <tbody>${itemsHtml}</tbody>
                </table>
            `;

            let actionsHtml = `<button type="button" class="btn-cancel" data-close>Đóng</button>`;
            if (data.status === 'ORDERED' || data.status === 'DRAFT') {
                actionsHtml += `<button type="button" class="btn-primary btn-receive" id="modalReceiveBtn">Nhập kho vật tư & Cập nhật tồn kho</button>`;
            }
            $('poDetailActions').innerHTML = actionsHtml;
            $('poDetailActions').querySelector('[data-close]').addEventListener('click', () => $('poDetailDialog').close());

            const receiveBtn = $('modalReceiveBtn');
            if (receiveBtn) {
                receiveBtn.addEventListener('click', async () => {
                    await receivePo(data.id);
                    $('poDetailDialog').close();
                });
            }

            $('poDetailError').hidden = true;
            $('poDetailDialog').showModal();
        } catch (e) {
            alert(e.message || 'Lỗi tải chi tiết đơn hàng');
        }
    }

    async function changePoStatus(id, newStatus) {
        const actionLabel = newStatus === 'ORDERED' ? 'chuyển sang trạng thái ĐÃ ĐẶT HÀNG' : 'HỦY đơn hàng';
        if (!confirm(`Bạn có chắc muốn ${actionLabel}?`)) return;
        try {
            await api.write(`purchase-orders/${id}/status`, 'PUT', { status: newStatus });
            notify(`Đã cập nhật trạng thái đơn mua hàng thành công.`);
            await loadData();
        } catch (e) {
            alert(e.message || 'Lỗi cập nhật trạng thái đơn hàng');
        }
    }

    async function receivePo(id) {
        if (!confirm('Xác nhận nhận hàng và cập nhật số lượng vật tư vào tồn kho kho lưu trữ?')) return;
        try {
            await api.write(`purchase-orders/${id}/receive`, 'POST', {});
            notify('Nhập kho thành công! Số lượng vật tư đã được cộng tự động vào tồn kho.');
            await loadData();
        } catch (e) {
            alert(e.message || 'Lỗi khi nhập kho');
        }
    }

    // ─── Event Listeners ──────────────────────────────────────────────────────
    document.addEventListener('DOMContentLoaded', () => {
        $('tab-suppliers').addEventListener('click', () => switchTab('suppliers'));
        $('tab-purchases').addEventListener('click', () => switchTab('purchases'));
        $('searchRecords').addEventListener('input', render);
        $('statusFilter').addEventListener('change', render);
        $('clearFilters').addEventListener('click', () => {
            $('searchRecords').value = '';
            $('statusFilter').value = '';
            render();
        });

        $('addRecord').addEventListener('click', () => {
            if (tab === 'suppliers') openAddSupplier();
            else openAddPo();
        });

        $('addItemRowBtn').addEventListener('click', addItemRow);

        // Close buttons for modals
        document.querySelectorAll('[data-close]').forEach(b => {
            b.addEventListener('click', () => {
                b.closest('dialog')?.close();
            });
        });

        // Supplier Form Submit
        $('supplierForm').addEventListener('submit', async (e) => {
            e.preventDefault();
            const payload = {
                name: $('supName').value.trim(),
                contactName: $('supContact').value.trim() || null,
                phone: $('supPhone').value.trim() || null,
                email: $('supEmail').value.trim() || null,
                address: $('supAddress').value.trim() || null,
                taxCode: $('supTax').value.trim() || null,
                notes: $('supNotes').value.trim() || null
            };
            try {
                if (editingSupplierId) {
                    await api.write(`suppliers/${editingSupplierId}`, 'PUT', payload);
                    notify('Cập nhật nhà cung cấp thành công.');
                } else {
                    await api.write('suppliers', 'POST', payload);
                    notify('Thêm nhà cung cấp mới thành công.');
                }
                $('supplierDialog').close();
                await loadData();
            } catch (err) {
                $('supplierError').textContent = err.message || 'Lỗi khi lưu nhà cung cấp.';
                $('supplierError').hidden = false;
            }
        });

        // PO Form Submit
        $('poForm').addEventListener('submit', async (e) => {
            e.preventDefault();
            const rows = document.querySelectorAll('#itemsContainer .item-row');
            if (!rows.length) {
                alert('Vui lòng thêm ít nhất một vật tư.');
                return;
            }
            const items = [];
            for (const r of rows) {
                items.push({
                    materialId: Number(r.querySelector('.item-mat').value),
                    quantity: String(r.querySelector('.item-qty').value),
                    unitPrice: String(r.querySelector('.item-price').value),
                    notes: null
                });
            }

            const payload = {
                supplierId: Number($('poSupplier').value),
                farmId: Number($('poFarm').value),
                warehouseId: Number($('poWarehouse').value),
                expectedDeliveryDate: $('poExpectedDate').value || null,
                notes: $('poNotes').value.trim() || null,
                items: items
            };

            try {
                await api.write('purchase-orders', 'POST', payload);
                notify('Tạo đơn mua vật tư thành công.');
                $('poDialog').close();
                await loadData();
            } catch (err) {
                $('poError').textContent = err.message || 'Lỗi khi tạo đơn mua hàng.';
                $('poError').hidden = false;
            }
        });

        switchTab('suppliers');
        loadData();
    });
})();
