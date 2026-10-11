/**
 * AgriManage - Notification & Reminder Module JS
 * Handles task reminders, crop season reminders, inventory alerts, upcoming activity notifications, status management, and audit history.
 */

document.addEventListener('DOMContentLoaded', () => {
    // 1. Data Retrieval
    function getNotifications() {
        if (window.getFarmNotifications) {
            return window.getFarmNotifications();
        }
        const stored = localStorage.getItem('agri_notifications');
        return stored ? JSON.parse(stored) : [];
    }

    function saveNotifications(notifs) {
        if (window.saveFarmNotifications) {
            window.saveFarmNotifications(notifs);
        } else {
            localStorage.setItem('agri_notifications', JSON.stringify(notifs));
        }
    }

    // Dynamic Sync: Check activities & inventory to auto-generate reminders
    function syncDynamicReminders() {
        let notifs = getNotifications();
        let changed = false;

        // Check tasks/activities for upcoming/overdue
        const actStored = localStorage.getItem('agri_activities');
        if (actStored) {
            try {
                const activities = JSON.parse(actStored);
                activities.forEach(act => {
                    // Check if overdue
                    if (act.status !== 'completed' && act.dueDate) {
                        const due = new Date(act.dueDate);
                        const now = new Date();
                        if (due < now) {
                            const exists = notifs.some(n => n.id === `ACT-OVERDUE-${act.id}`);
                            if (!exists) {
                                notifs.unshift({
                                    id: `ACT-OVERDUE-${act.id}`,
                                    title: `QUÁ HẠN: ${act.title}`,
                                    desc: `Hoạt động "${act.title}" thuộc ${act.season} (${act.sector}) đã quá hạn hoàn thành!`,
                                    category: 'Task Reminder',
                                    priority: 'critical',
                                    status: 'unread',
                                    time: 'Vừa xong',
                                    timestamp: new Date().toISOString(),
                                    icon: 'fa-clock',
                                    bg: '#fef2f2',
                                    color: '#dc2626'
                                });
                                changed = true;
                            }
                        }
                    }
                });
            } catch (e) { }
        }

        if (changed) {
            saveNotifications(notifs);
        }
    }

    syncDynamicReminders();
    let notifications = getNotifications();

    // Current State
    let activeTab = 'active'; // 'active', 'history', 'settings'
    let searchQuery = '';
    let selectedCategory = '';
    let selectedStatus = '';
    let selectedPriority = '';

    // DOM Elements
    const notifCardsContainer = document.getElementById('notifCardsContainer');
    const historyCardsContainer = document.getElementById('historyCardsContainer');
    const activeNotifView = document.getElementById('activeNotifView');
    const historyNotifView = document.getElementById('historyNotifView');
    const settingsNotifView = document.getElementById('settingsNotifView');
    const notifFilterControls = document.getElementById('notifFilterControls');

    // Stats Elements
    const statUnreadCount = document.getElementById('statUnreadCount');
    const statInventoryCount = document.getElementById('statInventoryCount');
    const statTaskRemindersCount = document.getElementById('statTaskRemindersCount');
    const statSeasonRemindersCount = document.getElementById('statSeasonRemindersCount');
    const tabActiveCount = document.getElementById('tabActiveCount');

    // Search and Filters
    const notifSearchInput = document.getElementById('notifSearchInput');
    const notifCategoryFilter = document.getElementById('notifCategoryFilter');
    const notifStatusFilter = document.getElementById('notifStatusFilter');
    const notifPriorityFilter = document.getElementById('notifPriorityFilter');
    const notifSectionTabs = document.getElementById('notifSectionTabs');

    // Modals
    const reminderModal = document.getElementById('reminderModal');
    const reminderForm = document.getElementById('reminderForm');

    // 2. Section Tabs Handler
    notifSectionTabs.addEventListener('click', (e) => {
        const btn = e.target.closest('.season-tab-btn');
        if (!btn) return;

        document.querySelectorAll('#notifSectionTabs .season-tab-btn').forEach(b => b.classList.remove('active'));
        btn.classList.add('active');
        activeTab = btn.getAttribute('data-tab');

        if (activeTab === 'active') {
            activeNotifView.style.display = 'block';
            historyNotifView.style.display = 'none';
            settingsNotifView.style.display = 'none';
            notifFilterControls.style.display = 'flex';
        } else if (activeTab === 'history') {
            activeNotifView.style.display = 'none';
            historyNotifView.style.display = 'block';
            settingsNotifView.style.display = 'none';
            notifFilterControls.style.display = 'flex';
        } else {
            activeNotifView.style.display = 'none';
            historyNotifView.style.display = 'none';
            settingsNotifView.style.display = 'block';
            notifFilterControls.style.display = 'none';
        }

        render();
    });

    // 3. Filters Handler
    notifSearchInput.addEventListener('input', (e) => {
        searchQuery = e.target.value.toLowerCase().trim();
        render();
    });

    notifCategoryFilter.addEventListener('change', (e) => {
        selectedCategory = e.target.value;
        render();
    });

    notifStatusFilter.addEventListener('change', (e) => {
        selectedStatus = e.target.value;
        render();
    });

    notifPriorityFilter.addEventListener('change', (e) => {
        selectedPriority = e.target.value;
        render();
    });

    // Helper: Category Badge Class
    function getNotifCategoryBadgeClass(cat) {
        switch (cat) {
            case 'Task Reminder': return 'notif-cat-task';
            case 'Crop Season': return 'notif-cat-season';
            case 'Inventory Alert': return 'notif-cat-inventory';
            case 'Upcoming Activity': return 'notif-cat-upcoming';
            default: return 'notif-cat-general';
        }
    }

    // Helper: Priority Pill Class
    function getPriorityPillHTML(priority) {
        if (priority === 'critical') return `<span class="priority-pill pill-critical"><i class="fa-solid fa-triangle-exclamation"></i> Khẩn cấp</span>`;
        if (priority === 'warning') return `<span class="priority-pill pill-warning"><i class="fa-solid fa-circle-exclamation"></i> Cảnh báo</span>`;
        if (priority === 'info') return `<span class="priority-pill pill-info"><i class="fa-solid fa-circle-info"></i> Thông tin</span>`;
        return `<span class="priority-pill pill-success"><i class="fa-solid fa-circle-check"></i> Thành công</span>`;
    }

    // Helper: Category Icon & Style
    function getNotifStyle(n) {
        if (n.category === 'Inventory Alert') return { icon: 'fa-boxes-stacked', bg: '#fff7ed', color: '#ea580c' };
        if (n.category === 'Task Reminder') return { icon: 'fa-clock-rotate-left', bg: '#eff6ff', color: '#1d4ed8' };
        if (n.category === 'Crop Season') return { icon: 'fa-wheat-awn', bg: '#ecfdf5', color: '#047857' };
        if (n.category === 'Upcoming Activity') return { icon: 'fa-calendar-day', bg: '#f5f3ff', color: '#6d28d9' };
        return { icon: 'fa-bell', bg: '#f1f5f9', color: '#475569' };
    }

    // 4. Update Stats Banner
    function updateStats() {
        notifications = getNotifications();

        const unread = notifications.filter(n => n.status === 'unread' || (!n.read && n.status !== 'read')).length;
        const inventory = notifications.filter(n => n.category === 'Inventory Alert').length;
        const taskReminders = notifications.filter(n => n.category === 'Task Reminder').length;
        const seasonReminders = notifications.filter(n => n.category === 'Crop Season' || n.category === 'Upcoming Activity').length;

        statUnreadCount.textContent = unread;
        statInventoryCount.textContent = inventory;
        statTaskRemindersCount.textContent = taskReminders;
        statSeasonRemindersCount.textContent = seasonReminders;

        const activeCount = notifications.filter(n => n.status !== 'resolved').length;
        tabActiveCount.textContent = activeCount;
    }

    // 5. Get Filtered Notification List
    function getFilteredNotifs(isArchive = false) {
        return notifications.filter(n => {
            // Tab filter
            const isResolved = n.status === 'resolved';
            if (isArchive ? !isResolved : isResolved) return false;

            // Category filter
            if (selectedCategory && n.category !== selectedCategory) return false;

            // Status filter
            if (selectedStatus && n.status !== selectedStatus) return false;

            // Priority filter
            if (selectedPriority && n.priority !== selectedPriority) return false;

            // Search query
            if (searchQuery) {
                const matchTitle = (n.title || '').toLowerCase().includes(searchQuery);
                const matchDesc = (n.desc || '').toLowerCase().includes(searchQuery);
                if (!matchTitle && !matchDesc) return false;
            }

            return true;
        });
    }

    // 6. Main Render Function
    function render() {
        updateStats();

        if (activeTab === 'active') {
            renderNotifList(getFilteredNotifs(false), notifCardsContainer, false);
        } else if (activeTab === 'history') {
            renderNotifList(getFilteredNotifs(true), historyCardsContainer, true);
        }
    }

    window.renderNotificationsCenter = render;

    function renderNotifList(list, container, isArchive) {
        container.innerHTML = '';

        if (list.length === 0) {
            container.innerHTML = `
                <div style="background:var(--bg-surface); padding: 40px; text-align: center; border-radius: var(--radius-md); border: 1px solid var(--border-color); color: var(--text-muted);">
                    <i class="fa-solid fa-bell-slash" style="font-size:36px; margin-bottom:12px; color: var(--text-light); display:block;"></i>
                    <strong>${isArchive ? 'Chưa có thông báo lưu trữ nào.' : 'Không có thông báo hoặc nhắc nhở nào.'}</strong>
                    <p style="font-size:13px; margin-top:4px;">Tất cả cảnh báo và công việc trang trại đã được cập nhật mượt mà.</p>
                </div>
            `;
            return;
        }

        list.forEach(n => {
            const card = document.createElement('div');
            const isUnread = n.status === 'unread' || (!n.read && n.status !== 'read');
            const style = getNotifStyle(n);

            card.className = `notif-card ${isUnread ? 'unread' : ''} ${n.priority || 'info'} ${n.status === 'resolved' ? 'resolved' : ''}`;

            card.innerHTML = `
                <div class="notif-card-icon" style="background: ${style.bg}; color: ${style.color};">
                    <i class="fa-solid ${style.icon}"></i>
                </div>

                <div class="notif-card-body">
                    <div class="notif-card-header">
                        <div class="notif-card-title">
                            ${n.title}
                            ${isUnread ? '<span class="badge" style="background:var(--danger); color:#fff; font-size:10px; font-weight:700;">MỚI</span>' : ''}
                        </div>
                        <div style="display:flex; gap:6px; align-items:center;">
                            <span class="notif-cat-badge ${getNotifCategoryBadgeClass(n.category)}">${n.category}</span>
                            ${getPriorityPillHTML(n.priority || 'info')}
                        </div>
                    </div>

                    <div class="notif-card-desc">${n.desc}</div>

                    <div class="notif-card-footer">
                        <span><i class="fa-regular fa-clock"></i> ${n.time || 'Vừa xong'} • ${n.timestamp ? new Date(n.timestamp).toLocaleString('vi-VN') : ''}</span>
                        
                        <div class="notif-actions">
                            ${isUnread ? `
                                <button class="btn-chip" onclick="toggleReadStatus('${n.id}', true)"><i class="fa-solid fa-envelope-open"></i> Đánh dấu đã đọc</button>
                            ` : `
                                <button class="btn-chip" onclick="toggleReadStatus('${n.id}', false)"><i class="fa-solid fa-envelope"></i> Đánh dấu chưa đọc</button>
                            `}

                            ${n.status !== 'resolved' ? `
                                <button class="btn-chip chip-success" onclick="resolveNotif('${n.id}')"><i class="fa-solid fa-shield-check"></i> Đã xử lý</button>
                            ` : `
                                <span class="badge" style="background:#dcfce7; color:#15803d; padding:5px 10px;"><i class="fa-solid fa-check-double"></i> Đã giải quyết</span>
                            `}

                            <button class="btn-chip text-danger" onclick="deleteNotif('${n.id}')" title="Xóa thông báo"><i class="fa-solid fa-trash"></i></button>
                        </div>
                    </div>
                </div>
            `;
            card.addEventListener('click', (e) => {
                // Ignore click if user clicked directly on action buttons
                if (e.target.closest('.notif-actions') || e.target.tagName === 'BUTTON') return;
                window.openNotificationDetail(n.id);
            });

            container.appendChild(card);
        });
    }

    // 7. Global Actions: Toggle Read / Resolve / Delete
    window.toggleReadStatus = (id, readVal) => {
        const notif = notifications.find(n => n.id === id);
        if (!notif) return;

        notif.read = readVal;
        notif.status = readVal ? 'read' : 'unread';

        saveNotifications(notifications);
        render();

        if (window.showToast) {
            window.showToast('Thông báo', readVal ? 'Đã đánh dấu thông báo là đã đọc.' : 'Đã chuyển thông báo sang chưa đọc.', 'info');
        }
    };

    window.resolveNotif = (id) => {
        const notif = notifications.find(n => n.id === id);
        if (!notif) return;

        notif.status = 'resolved';
        notif.read = true;

        saveNotifications(notifications);
        render();

        if (window.showToast) {
            window.showToast('Đã xử lý cảnh báo', `Cảnh báo "${notif.title}" đã được đánh dấu giải quyết xong.`, 'success');
        }
    };

    window.deleteNotif = (id) => {
        const notif = notifications.find(n => n.id === id);
        if (!notif) return;

        if (confirm(`Bạn có chắc chắn muốn xóa thông báo "${notif.title}"?`)) {
            notifications = notifications.filter(n => n.id !== id);
            saveNotifications(notifications);
            render();
            if (window.showToast) window.showToast('Đã xóa', 'Đã xóa thông báo khỏi danh sách.', 'warning');
        }
    };

    // Mark All Read Button in Header
    document.getElementById('btnMarkAllReadCenter').addEventListener('click', () => {
        notifications.forEach(n => { n.read = true; if (n.status === 'unread') n.status = 'read'; });
        saveNotifications(notifications);
        render();
        if (window.showToast) window.showToast('Cập nhật Thông báo', 'Đã đánh dấu tất cả thông báo là đã đọc.', 'success');
    });

    // Clear History Button
    document.getElementById('btnClearHistoryNotifs').addEventListener('click', () => {
        if (confirm('Bạn có chắc chắn muốn xóa dọn dẹp các thông báo đã giải quyết trong lịch sử lưu trữ?')) {
            notifications = notifications.filter(n => n.status !== 'resolved');
            saveNotifications(notifications);
            render();
            if (window.showToast) window.showToast('Dọn dẹp lịch sử', 'Đã xóa dọn toàn bộ lịch sử thông báo đã giải quyết.', 'success');
        }
    });

    // 8. Create Custom Reminder Modal Logic
    document.getElementById('btnCreateReminder').addEventListener('click', () => {
        reminderForm.reset();
        reminderModal.classList.add('active');
    });

    document.getElementById('btnCloseReminderModal').addEventListener('click', () => reminderModal.classList.remove('active'));
    document.getElementById('btnCancelReminder').addEventListener('click', () => reminderModal.classList.remove('active'));

    reminderForm.addEventListener('submit', (e) => {
        e.preventDefault();

        const title = document.getElementById('remTitle').value.trim();
        const category = document.getElementById('remCategory').value;
        const priority = document.getElementById('remPriority').value;
        const desc = document.getElementById('remDesc').value.trim();

        const newNotif = {
            id: 'NOTIF-' + Date.now().toString().slice(-4),
            title,
            desc,
            category,
            priority,
            status: 'unread',
            read: false,
            time: 'Vừa xong',
            timestamp: new Date().toISOString()
        };

        notifications.unshift(newNotif);
        saveNotifications(notifications);

        reminderModal.classList.remove('active');
        render();

        if (window.showToast) {
            window.showToast('Đã tạo Nhắc nhở', `Thông báo mới "${title}" đã được phát đi!`, 'success');
        }
    });

    // Save Notification Settings Button
    document.getElementById('btnSaveNotifSettings').addEventListener('click', () => {
        const settings = {
            inventoryAlert: document.getElementById('cfgInventoryAlert').checked,
            taskReminder: document.getElementById('cfgTaskReminder').checked,
            equipmentAlert: document.getElementById('cfgEquipmentAlert').checked,
            toastPopup: document.getElementById('cfgToastPopup').checked
        };
        localStorage.setItem('agri_notif_settings', JSON.stringify(settings));
        if (window.showToast) window.showToast('Đã lưu Cấu hình', 'Cài đặt nhắc nhở & cảnh báo tự động đã được lưu thành công.', 'success');
    });

    // Initial Render
    render();
});
