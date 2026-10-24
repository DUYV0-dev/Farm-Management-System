(function () {
    'use strict';
    const api = window.FarmApi;
    const $ = id => document.getElementById(id);
    const escape = v => String(v ?? '').replace(/[&<>"']/g, ch => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[ch]));
    const money = v => new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' }).format(v ?? 0);
    const number = v => new Intl.NumberFormat('vi-VN', { maximumFractionDigits: 2 }).format(v ?? 0);

    let tab = 'customers';
    let customers = [], salesOrders = [], products = [], farms = [];
    let noticeTimer, editingCustomerId = null;

    const statusBadge = (st) => {
        switch (st) {
            case 'PENDING': return '<span class="inventory-badge badge-pending">Chờ xử lý</span>';
            case 'CONFIRMED': return '<span class="inventory-badge badge-confirmed">Đã xác nhận</span>';
            case 'SHIPPED': return '<span class="inventory-badge badge-shipped">Đang giao hàng</span>';
            case 'DELIVERED': return '<span class="inventory-badge badge-delivered">Đã giao thành công</span>';
            case 'CANCELLED': return '<span class="inventory-badge badge-cancelled">Đã hủy</span>';
            case 'ACTIVE': return '<span class="inventory-badge active">Hoạt động</span>';
            case 'INACTIVE': return '<span class="inventory-badge inactive">Ngừng hoạt động</span>';
            default: return `<span class="inventory-badge">${escape(st)}</span>`;
        }
    };

    const typeName = (t) => {
        switch (t) {
            case 'INDIVIDUAL': return 'Cá nhân';
            case 'WHOLESALER': return 'Đại lý / Bán buôn';
            case 'RETAILER': return 'Bán lẻ';
            case 'ENTERPRISE': return 'Doanh nghiệp / Siêu thị';
            default: return escape(t);
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
            customers = (await api.all('customers', 'customers:read')) || [];
            salesOrders = (await api.all('sales-orders', 'sales:read')) || [];
            farms = (await api.all('farms', 'farms:read')) || [];
            // Try loading harvested products
            try {
                const prodRes = await api.write('products', 'GET');
                products = (prodRes.data && Array.isArray(prodRes.data)) ? prodRes.data : (Array.isArray(prodRes) ? prodRes : []);
            } catch (ignored) {
                products = [];
            }
            updateStats();
            render();
        } catch (e) {
            console.error(e);
        }
    }

    function updateStats() {
        $('statCustomers').textContent = customers.filter(c => c.status === 'ACTIVE').length;
        $('statPendingSOs').textContent = salesOrders.filter(s => s.status === 'PENDING').length;
        $('statShippedSOs').textContent = salesOrders.filter(s => s.status === 'SHIPPED' || s.status === 'CONFIRMED').length;
        const totalRev = salesOrders
            .filter(s => s.status === 'CONFIRMED' || s.status === 'SHIPPED' || s.status === 'DELIVERED')
            .reduce((acc, s) => acc + (Number(s.totalAmount) || 0), 0);
        $('statTotalRevenue').textContent = money(totalRev);
    }

    function switchTab(nextTab) {
        tab = nextTab;
        document.querySelectorAll('.inventory-tab').forEach(b => {
            const active = b.dataset.tab === tab;
            b.classList.toggle('active', active);
            b.setAttribute('aria-selected', String(active));
        });

        if (tab === 'customers') {
            $('addButtonText').textContent = 'Thêm khách hàng';
            $('listTitle').textContent = 'Danh sách khách hàng';
            $('listDescription').textContent = 'Thông tin đối tác thu mua, thương lái, siêu thị và khách hàng tiêu thụ nông sản.';
            $('typeFilter').hidden = false;
            $('typeFilter').innerHTML = `
                <option value="">Tất cả phân loại</option>
                <option value="INDIVIDUAL">Cá nhân</option>
                <option value="WHOLESALER">Đại lý / Bán buôn</option>
                <option value="RETAILER">Bán lẻ</option>
                <option value="ENTERPRISE">Doanh nghiệp / Siêu thị</option>
            `;
            $('statusFilter').innerHTML = `
                <option value="">Tất cả trạng thái</option>
                <option value="ACTIVE">Hoạt động</option>
                <option value="INACTIVE">Ngừng hoạt động</option>
            `;
        } else {
            $('addButtonText').textContent = 'Tạo đơn bán nông sản';
            $('listTitle').textContent = 'Danh sách đơn bán nông sản (SO)';
            $('listDescription').textContent = 'Theo dõi đơn hàng từ lúc đặt, xác nhận trừ kho, xuất bán đến khi giao hàng thành công.';
            $('typeFilter').hidden = true;
            $('statusFilter').innerHTML = `
                <option value="">Tất cả trạng thái</option>
                <option value="PENDING">Chờ xử lý</option>
                <option value="CONFIRMED">Đã xác nhận (Đã trừ kho)</option>
                <option value="SHIPPED">Đang giao</option>
                <option value="DELIVERED">Đã giao thành công</option>
                <option value="CANCELLED">Đã hủy</option>
            `;
        }
        render();
    }

    function render() {
        const query = $('searchRecords').value.trim().toLowerCase();
        const statusFilter = $('statusFilter').value;
        const typeFilter = $('typeFilter').value;

        if (tab === 'customers') {
            $('tableHead').innerHTML = `
                <tr>
                    <th>Khách hàng</th>
                    <th>Phân loại</th>
                    <th>Người liên hệ</th>
                    <th>Điện thoại / Email</th>
                    <th>Địa chỉ</th>
                    <th>Trạng thái</th>
                    <th>Thao tác</th>
                </tr>
            `;

            const filtered = customers.filter(c => {
                const matchQuery = !query || c.name.toLowerCase().includes(query)
                    || (c.contactName && c.contactName.toLowerCase().includes(query))
                    || (c.phone && c.phone.includes(query));
                const matchStatus = !statusFilter || c.status === statusFilter;
                const matchType = !typeFilter || c.customerType === typeFilter;
                return matchQuery && matchStatus && matchType;
            });

            $('recordCount').textContent = `${filtered.length} khách hàng`;
            $('emptyState').hidden = filtered.length > 0;

            const rowsHtml = filtered.map(c => `
                <tr>
                    <td><strong>${escape(c.name)}</strong></td>
                    <td><span style="background:#f1f5f9;padding:2px 6px;border-radius:4px;font-size:0.8rem;">${typeName(c.customerType)}</span></td>
                    <td>${escape(c.contactName || '—')}</td>
                    <td>${escape(c.phone || '—')}<br><small style="color:#64748b;">${escape(c.email || '')}</small></td>
                    <td>${escape(c.address || '—')}</td>
                    <td>${statusBadge(c.status)}</td>
                    <td>
                        <button class="btn-secondary btn-sm-action edit-cust-btn" data-id="${c.id}">Sửa</button>
                        <button class="btn-secondary btn-sm-action toggle-cust-btn" data-id="${c.id}" data-status="${c.status}">
                            ${c.status === 'ACTIVE' ? 'Ngừng' : 'Kích hoạt'}
                        </button>
                    </td>
                </tr>
            `).join('');
            $('tableBody').innerHTML = rowsHtml;

            document.querySelectorAll('.edit-cust-btn').forEach(b => {
                b.addEventListener('click', () => openEditCustomer(Number(b.dataset.id)));
            });
            document.querySelectorAll('.toggle-cust-btn').forEach(b => {
                b.addEventListener('click', () => toggleCustomerStatus(Number(b.dataset.id), b.dataset.status));
            });

        } else {
            $('tableHead').innerHTML = `
                <tr>
                    <th>Mã đơn hàng</th>
                    <th>Khách hàng</th>
                    <th>Trang trại xuất</th>
                    <th>Ngày đặt</th>
                    <th>Tổng tiền</th>
                    <th>Trạng thái</th>
                    <th>Thao tác</th>
                </tr>
            `;

            const filtered = salesOrders.filter(s => {
                const matchQuery = !query || s.soNumber.toLowerCase().includes(query)
                    || (s.customerName && s.customerName.toLowerCase().includes(query));
                const matchStatus = !statusFilter || s.status === statusFilter;
                return matchQuery && matchStatus;
            });

            $('recordCount').textContent = `${filtered.length} đơn bán hàng`;
            $('emptyState').hidden = filtered.length > 0;

            const rowsHtml = filtered.map(s => `
                <tr>
                    <td><strong>${escape(s.soNumber)}</strong></td>
                    <td>${escape(s.customerName)}</td>
                    <td>${escape(s.farmName || 'Toàn hệ thống')}</td>
                    <td>${escape(s.orderDate)}</td>
                    <td><strong>${money(s.totalAmount)}</strong></td>
                    <td>${statusBadge(s.status)}</td>
                    <td>
                        <button class="btn-secondary btn-sm-action view-so-btn" data-id="${s.id}">Chi tiết</button>
                        ${s.status === 'PENDING' ? `<button class="btn-confirm-so btn-sm-action confirm-so-btn" data-id="${s.id}">Xác nhận</button>` : ''}
                        ${s.status === 'CONFIRMED' ? `<button class="btn-ship-so btn-sm-action ship-so-btn" data-id="${s.id}">Giao hàng</button>` : ''}
                        ${s.status === 'SHIPPED' ? `<button class="btn-deliver-so btn-sm-action deliver-so-btn" data-id="${s.id}">Đã giao</button>` : ''}
                        ${(s.status === 'PENDING' || s.status === 'CONFIRMED' || s.status === 'SHIPPED') ? `
                            <button class="btn-cancel-so btn-sm-action cancel-so-btn" data-id="${s.id}">Hủy</button>
                        ` : ''}
                    </td>
                </tr>
            `).join('');
            $('tableBody').innerHTML = rowsHtml;

            document.querySelectorAll('.view-so-btn').forEach(b => {
                b.addEventListener('click', () => openSoDetail(Number(b.dataset.id)));
            });
            document.querySelectorAll('.confirm-so-btn').forEach(b => {
                b.addEventListener('click', () => changeSoStatus(Number(b.dataset.id), 'CONFIRMED'));
            });
            document.querySelectorAll('.ship-so-btn').forEach(b => {
                b.addEventListener('click', () => changeSoStatus(Number(b.dataset.id), 'SHIPPED'));
            });
            document.querySelectorAll('.deliver-so-btn').forEach(b => {
                b.addEventListener('click', () => changeSoStatus(Number(b.dataset.id), 'DELIVERED'));
            });
            document.querySelectorAll('.cancel-so-btn').forEach(b => {
                b.addEventListener('click', () => changeSoStatus(Number(b.dataset.id), 'CANCELLED'));
            });
        }
    }

    // ─── Customer Dialog ──────────────────────────────────────────────────────
    function openAddCustomer() {
        editingCustomerId = null;
        $('customerDialogTitle').textContent = 'Thêm khách hàng mới';
        $('customerForm').reset();
        $('customerError').hidden = true;
        $('customerDialog').showModal();
    }

    function openEditCustomer(id) {
        editingCustomerId = id;
        const c = customers.find(x => x.id === id);
        if (!c) return;
        $('customerDialogTitle').textContent = 'Chỉnh sửa thông tin khách hàng';
        $('custName').value = c.name || '';
        $('custContact').value = c.contactName || '';
        $('custType').value = c.customerType || 'INDIVIDUAL';
        $('custPhone').value = c.phone || '';
        $('custEmail').value = c.email || '';
        $('custAddress').value = c.address || '';
        $('custTax').value = c.taxCode || '';
        $('custNotes').value = c.notes || '';
        $('customerError').hidden = true;
        $('customerDialog').showModal();
    }

    async function toggleCustomerStatus(id, currentStatus) {
        const next = currentStatus === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE';
        if (!confirm(`Bạn có chắc muốn ${next === 'ACTIVE' ? 'kích hoạt lại' : 'ngừng hoạt động'} khách hàng này?`)) return;
        try {
            await api.write(`customers/${id}/status`, 'PUT', { status: next });
            notify('Đã cập nhật trạng thái khách hàng.');
            await loadData();
        } catch (e) {
            alert(e.message || 'Lỗi cập nhật trạng thái');
        }
    }

    // ─── SO Creation Dialog ───────────────────────────────────────────────────
    function openAddSo() {
        $('soForm').reset();
        $('soError').hidden = true;

        const activeCusts = customers.filter(c => c.status === 'ACTIVE');
        $('soCustomer').innerHTML = activeCusts.map(c => `<option value="${c.id}">${escape(c.name)} (${typeName(c.customerType)})</option>`).join('');

        const farmOpts = farms.map(f => `<option value="${f.id}">${escape(f.name)}</option>`).join('');
        $('soFarm').innerHTML = `<option value="">-- Tất cả / Mặc định --</option>` + farmOpts;

        $('soItemsContainer').innerHTML = '';
        addSoItemRow();
        recalcSoTotal();

        $('soDialog').showModal();
    }

    function addSoItemRow() {
        const row = document.createElement('div');
        row.className = 'item-row';
        const opts = products.map(p => {
            const stock = p.quantity != null ? p.quantity : 0;
            return `<option value="${p.productId}" data-stock="${stock}">${escape(p.productName)} (Còn: ${number(stock)})</option>`;
        }).join('');

        row.innerHTML = `
            <select class="item-prod" required style="flex:2;">${opts || '<option value="1">Nông sản thu hoạch chung</option>'}</select>
            <input type="number" class="item-qty" placeholder="Số lượng" step="0.01" min="0.01" value="1" required style="flex:1;">
            <input type="number" class="item-price" placeholder="Đơn giá" step="1000" min="0" value="50000" required style="flex:1;">
            <button type="button" class="btn-cancel remove-so-item-btn" style="padding:4px 8px;">×</button>
        `;
        row.querySelector('.remove-so-item-btn').addEventListener('click', () => {
            if ($('soItemsContainer').children.length > 1) {
                row.remove();
                recalcSoTotal();
            }
        });
        row.querySelector('.item-qty').addEventListener('input', recalcSoTotal);
        row.querySelector('.item-price').addEventListener('input', recalcSoTotal);

        $('soItemsContainer').appendChild(row);
        recalcSoTotal();
    }

    function recalcSoTotal() {
        let total = 0;
        document.querySelectorAll('#soItemsContainer .item-row').forEach(row => {
            const qty = Number(row.querySelector('.item-qty').value) || 0;
            const price = Number(row.querySelector('.item-price').value) || 0;
            total += qty * price;
        });
        $('soTotalDisplay').textContent = money(total);
    }

    // ─── SO Detail Dialog & Status Transitions ────────────────────────────────
    async function openSoDetail(id) {
        try {
            const res = await api.write(`sales-orders/${id}`, 'GET');
            const data = res.data || res;
            $('soDetailTitle').textContent = `Chi tiết đơn bán nông sản: ${data.soNumber}`;

            const itemsHtml = data.items.map(it => `
                <tr>
                    <td><strong>${escape(it.productName)}</strong></td>
                    <td>${number(it.quantity)}</td>
                    <td>${money(it.unitPrice)}</td>
                    <td><strong>${money(it.totalPrice)}</strong></td>
                </tr>
            `).join('');

            $('soDetailContent').innerHTML = `
                <div style="display:grid;grid-template-columns:1fr 1fr;gap:12px;margin-bottom:12px;background:#f8fafc;padding:12px;border-radius:6px;">
                    <div><strong>Khách hàng:</strong> ${escape(data.customerName)}</div>
                    <div><strong>Trang trại:</strong> ${escape(data.farmName || 'Toàn hệ thống')}</div>
                    <div><strong>Ngày đặt:</strong> ${escape(data.orderDate)}</div>
                    <div><strong>Ngày hẹn giao:</strong> ${escape(data.deliveryDate || 'Chưa định')}</div>
                    <div><strong>Trạng thái:</strong> ${statusBadge(data.status)}</div>
                    <div><strong>Địa chỉ giao:</strong> ${escape(data.deliveryAddress || 'Tại kho')}</div>
                    <div style="grid-column:1/-1;"><strong>Tổng giá trị đơn:</strong> <span style="font-size:1.1rem;color:#16a34a;font-weight:700;">${money(data.totalAmount)}</span></div>
                </div>
                <h4 style="margin-top:12px;margin-bottom:6px;">Danh mục nông sản xuất bán:</h4>
                <table class="so-items-table">
                    <thead>
                        <tr><th>Nông sản</th><th>Số lượng bán</th><th>Đơn giá</th><th>Thành tiền</th></tr>
                    </thead>
                    <tbody>${itemsHtml}</tbody>
                </table>
            `;

            let actionsHtml = `<button type="button" class="btn-cancel" data-close>Đóng</button>`;
            if (data.status === 'PENDING') {
                actionsHtml += `<button type="button" class="btn-primary btn-confirm-so" id="modalConfirmBtn">Xác nhận đơn (Trừ kho)</button>`;
            } else if (data.status === 'CONFIRMED') {
                actionsHtml += `<button type="button" class="btn-primary btn-ship-so" id="modalShipBtn">Bắt đầu giao hàng (SHIPPED)</button>`;
            } else if (data.status === 'SHIPPED') {
                actionsHtml += `<button type="button" class="btn-primary btn-deliver-so" id="modalDeliverBtn">Hoàn tất giao hàng (DELIVERED)</button>`;
            }
            if (data.status !== 'DELIVERED' && data.status !== 'CANCELLED') {
                actionsHtml += `<button type="button" class="btn-secondary btn-cancel-so" id="modalCancelBtn">Hủy đơn (Hoàn kho)</button>`;
            }

            $('soDetailActions').innerHTML = actionsHtml;
            $('soDetailActions').querySelector('[data-close]').addEventListener('click', () => $('soDetailDialog').close());

            $('modalConfirmBtn')?.addEventListener('click', async () => { await changeSoStatus(data.id, 'CONFIRMED'); $('soDetailDialog').close(); });
            $('modalShipBtn')?.addEventListener('click', async () => { await changeSoStatus(data.id, 'SHIPPED'); $('soDetailDialog').close(); });
            $('modalDeliverBtn')?.addEventListener('click', async () => { await changeSoStatus(data.id, 'DELIVERED'); $('soDetailDialog').close(); });
            $('modalCancelBtn')?.addEventListener('click', async () => { await changeSoStatus(data.id, 'CANCELLED'); $('soDetailDialog').close(); });

            $('soDetailError').hidden = true;
            $('soDetailDialog').showModal();
        } catch (e) {
            alert(e.message || 'Lỗi tải chi tiết đơn hàng');
        }
    }

    async function changeSoStatus(id, newStatus) {
        let confirmText = `Bạn có chắc muốn chuyển trạng thái đơn hàng sang ${newStatus}?`;
        if (newStatus === 'CONFIRMED') confirmText = 'Xác nhận đơn hàng? Số lượng nông sản sẽ được tự động kiểm tra và trừ khỏi kho sản phẩm.';
        if (newStatus === 'CANCELLED') confirmText = 'Hủy đơn hàng? Nếu đơn đã được xác nhận, số lượng nông sản sẽ được tự động hoàn lại vào kho.';

        if (!confirm(confirmText)) return;
        try {
            await api.write(`sales-orders/${id}/status`, 'PUT', { status: newStatus });
            notify(`Đã cập nhật trạng thái đơn hàng thành công.`);
            await loadData();
        } catch (e) {
            alert(e.message || 'Lỗi cập nhật trạng thái đơn hàng');
        }
    }

    // ─── Event Listeners ──────────────────────────────────────────────────────
    document.addEventListener('DOMContentLoaded', () => {
        $('tab-customers').addEventListener('click', () => switchTab('customers'));
        $('tab-sales').addEventListener('click', () => switchTab('sales'));
        $('searchRecords').addEventListener('input', render);
        $('statusFilter').addEventListener('change', render);
        $('typeFilter').addEventListener('change', render);
        $('clearFilters').addEventListener('click', () => {
            $('searchRecords').value = '';
            $('statusFilter').value = '';
            $('typeFilter').value = '';
            render();
        });

        $('addRecord').addEventListener('click', () => {
            if (tab === 'customers') openAddCustomer();
            else openAddSo();
        });

        $('addSoItemRowBtn').addEventListener('click', addSoItemRow);

        document.querySelectorAll('[data-close]').forEach(b => {
            b.addEventListener('click', () => {
                b.closest('dialog')?.close();
            });
        });

        // Customer Form Submit
        $('customerForm').addEventListener('submit', async (e) => {
            e.preventDefault();
            const payload = {
                name: $('custName').value.trim(),
                contactName: $('custContact').value.trim() || null,
                customerType: $('custType').value,
                phone: $('custPhone').value.trim() || null,
                email: $('custEmail').value.trim() || null,
                address: $('custAddress').value.trim() || null,
                taxCode: $('custTax').value.trim() || null,
                notes: $('custNotes').value.trim() || null
            };
            try {
                if (editingCustomerId) {
                    await api.write(`customers/${editingCustomerId}`, 'PUT', payload);
                    notify('Cập nhật khách hàng thành công.');
                } else {
                    await api.write('customers', 'POST', payload);
                    notify('Thêm khách hàng mới thành công.');
                }
                $('customerDialog').close();
                await loadData();
            } catch (err) {
                $('customerError').textContent = err.message || 'Lỗi khi lưu khách hàng.';
                $('customerError').hidden = false;
            }
        });

        // SO Form Submit
        $('soForm').addEventListener('submit', async (e) => {
            e.preventDefault();
            const rows = document.querySelectorAll('#soItemsContainer .item-row');
            if (!rows.length) {
                alert('Vui lòng chọn ít nhất một nông sản.');
                return;
            }
            const items = [];
            for (const r of rows) {
                items.push({
                    productId: Number(r.querySelector('.item-prod').value),
                    quantity: String(r.querySelector('.item-qty').value),
                    unitPrice: String(r.querySelector('.item-price').value),
                    notes: null
                });
            }

            const payload = {
                customerId: Number($('soCustomer').value),
                farmId: $('soFarm').value ? Number($('soFarm').value) : null,
                deliveryDate: $('soDeliveryDate').value || null,
                status: $('soStatus').value,
                deliveryAddress: $('soDeliveryAddress').value.trim() || null,
                notes: $('soNotes').value.trim() || null,
                items: items
            };

            try {
                await api.write('sales-orders', 'POST', payload);
                notify('Tạo đơn bán nông sản thành công.');
                $('soDialog').close();
                await loadData();
            } catch (err) {
                $('soError').textContent = err.message || 'Lỗi khi tạo đơn bán hàng.';
                $('soError').hidden = false;
            }
        });

        switchTab('customers');
        loadData();
    });
})();
