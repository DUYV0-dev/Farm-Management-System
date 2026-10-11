// AgriManage Shared System JS (Authentication, Profile & Real-time Notifications)

(function () {
    // 1. Initial User Session Setup
    const DEFAULT_USER = {
        name: '',
        role: '',
        email: '',
        avatar: ''
    };

    function getUser() {
        const stored = localStorage.getItem('agri_user');
        if (stored) {
            try { return JSON.parse(stored); } catch (e) { }
        }
        return DEFAULT_USER;
    }

    function setUser(userObj) {
        if (!userObj) {
            localStorage.removeItem('agri_user');
        } else {
            localStorage.setItem('agri_user', JSON.stringify(userObj));
        }
    }

    // 2. Initial Notifications & Reminders Data
    const DEFAULT_NOTIFICATIONS = [
        {
            id: 'NOTIF-100',
            title: '🔴 SỤT ÁP NƯỚC HỆ THỐNG TƯỚI TỰ ĐỘNG - SECTOR 4',
            desc: 'Phát hiện sụt áp lực nước nghiêm trọng (dưới 1.2 bar) tại đường ống tưới chính Sector 4. Nguyên nhân dự kiến do tắc nghẽn bộ lọc đĩa 120 mesh và hư hỏng 2 van xả cặn tự động. Yêu cầu kỹ sư Võ Hà Duy tiến hành súc rửa màng lọc, thay thế 2 van xả 34mm và kiểm tra lại áp suất toàn hệ thống trước 14:00 chiều nay để tránh ảnh hưởng đến đợt tưới tiêu vụ lúa Đông Xuân 2026.',
            category: 'Inventory Alert',
            priority: 'critical',
            status: 'unread',
            time: '5 phút trước',
            timestamp: new Date().toISOString(),
            icon: 'fa-droplet',
            bg: '#fef2f2',
            color: '#dc2626'
        },
        {
            id: 'NOTIF-101',
            title: 'CẢNH BÁO VẬT TƯ: NPK 16-16-8 DƯỚI NGƯỠNG TỐI THIỂU',
            desc: 'Số lượng phân NPK 16-16-8 trong kho chỉ còn 45kg (ngưỡng tối thiểu: 100kg). Cần đặt mua bổ sung ngay.',
            category: 'Inventory Alert',
            priority: 'critical',
            status: 'unread', // 'unread', 'read', 'resolved'
            time: '15 phút trước',
            timestamp: new Date(Date.now() - 900000).toISOString(),
            icon: 'fa-triangle-exclamation',
            bg: '#fef2f2',
            color: '#dc2626'
        },
        {
            id: 'NOTIF-102',
            title: 'NHẮC NHỞ CÔNG VIỆC: Quá hạn kiểm tra hệ thống tưới',
            desc: 'Công việc "Bảo trì hệ thống tưới nhỏ giọt Control Unit A" chưa hoàn thành theo kế hoạch.',
            category: 'Task Reminder',
            priority: 'warning',
            status: 'unread',
            time: '1 giờ trước',
            timestamp: new Date(Date.now() - 3600000).toISOString(),
            icon: 'fa-clock',
            bg: '#fffbeb',
            color: '#b45309'
        },
        {
            id: 'NOTIF-103',
            title: 'NHẮC NHỞ MÙA VỤ: Đợt bón thúc 2 vụ Đông Xuân 2026',
            desc: 'Theo lịch mùa vụ Đông Xuân 2026, Cánh đồng Lúa Sector 1 đến giai đoạn đẻ nhánh cần bón thúc đợt 2.',
            category: 'Crop Season',
            priority: 'info',
            status: 'unread',
            time: '3 giờ trước',
            timestamp: new Date(Date.now() - 3*3600000).toISOString(),
            icon: 'fa-wheat-awn',
            bg: '#ecfdf5',
            color: '#047857'
        },
        {
            id: 'NOTIF-104',
            title: 'THÔNG BÁO HOẠT ĐỘNG SẮP DIỄN RA: Phun thuốc phòng trừ sâu',
            desc: 'Lịch phun chế phẩm sinh học BT cho Greenhouse A2 sẽ bắt đầu vào 08:00 sáng mai.',
            category: 'Upcoming Activity',
            priority: 'info',
            status: 'read',
            time: 'Hôm qua',
            timestamp: new Date(Date.now() - 86400000).toISOString(),
            icon: 'fa-calendar-day',
            bg: '#f5f3ff',
            color: '#6d28d9'
        },
        {
            id: 'NOTIF-105',
            title: 'CẢNH BÁO THIẾT BỊ: Kiểm tra bảo trì máy cày Kubota',
            desc: 'Máy cày Kubota L5018 đã hoạt động 150 giờ, cần thay dầu và vệ sinh lọc gió định kỳ.',
            category: 'Inventory Alert',
            priority: 'warning',
            status: 'resolved',
            time: '2 ngày trước',
            timestamp: new Date(Date.now() - 2*86400000).toISOString(),
            icon: 'fa-wrench',
            bg: '#fff7ed',
            color: '#c2410c'
        }
    ];

    function getNotifications() {
        const stored = localStorage.getItem('agri_notifications');
        if (stored) {
            try { return JSON.parse(stored); } catch (e) { }
        }
        return DEFAULT_NOTIFICATIONS;
    }

    function setNotifications(notifs) {
        localStorage.setItem('agri_notifications', JSON.stringify(notifs));
    }

    window.getFarmNotifications = getNotifications;
    window.saveFarmNotifications = setNotifications;

    // Toast Container Initialization
    function ensureToastContainer() {
        let container = document.getElementById('toastContainer');
        if (!container) {
            container = document.createElement('div');
            container.id = 'toastContainer';
            container.className = 'toast-container';
            document.body.appendChild(container);
        }
        return container;
    }

    window.showToast = function (title, message, type = 'success') {
        const container = ensureToastContainer();
        const toast = document.createElement('div');
        toast.className = `toast toast-${type}`;

        let iconClass = 'fa-check-circle';
        if (type === 'warning') iconClass = 'fa-triangle-exclamation';
        if (type === 'danger') iconClass = 'fa-circle-exclamation';

        toast.innerHTML = `
            <i class="fa-solid ${iconClass}" style="font-size:20px;"></i>
            <div>
                <strong style="display:block; font-size:14px; margin-bottom:2px;">${title}</strong>
                <span style="font-size:13px; color:var(--text-muted);">${message}</span>
            </div>
        `;
        container.appendChild(toast);

        setTimeout(() => {
            toast.style.opacity = '0';
            toast.style.transform = 'translateX(50px)';
            toast.style.transition = 'all 0.3s ease';
            setTimeout(() => toast.remove(), 300);
        }, 3500);
    };

    // DOM Interaction Setup
    document.addEventListener('DOMContentLoaded', () => {
        const currentUser = getUser();
        const navActions = document.querySelector('.nav-actions');

        if (navActions) {
            // Build User Profile UI & Dropdown inside nav-actions
            const userProfileEl = navActions.querySelector('.user-profile');
            const bellBtn = navActions.querySelector('.btn-icon');

            if (userProfileEl) {
                userProfileEl.querySelector('.user-name').textContent = currentUser.name;
                userProfileEl.querySelector('.user-role').textContent = currentUser.role;
                if (currentUser.avatar) {
                    const img = userProfileEl.querySelector('img');
                    if (img) img.src = currentUser.avatar;
                }

                // Append User Menu Dropdown
                const userMenu = document.createElement('div');
                userMenu.className = 'user-dropdown-menu';
                userMenu.id = 'userDropdownMenu';
                userMenu.innerHTML = `
                    <div class="user-dropdown-header">
                        <strong>${currentUser.name}</strong>
                        <span>${currentUser.email}</span>
                    </div>
                    <a href="javascript:void(0)" class="user-dropdown-item" id="btnAccountSettings">
                        <i class="fa-solid fa-user-gear"></i> Account Settings
                    </a>
                    <a href="login.html" class="user-dropdown-item text-danger" id="btnLogout">
                        <i class="fa-solid fa-right-from-bracket"></i> Log Out
                    </a>
                `;
                navActions.appendChild(userMenu);

                userProfileEl.addEventListener('click', (e) => {
                    e.stopPropagation();
                    const notifPanel = document.getElementById('notificationPanel');
                    if (notifPanel) notifPanel.classList.remove('active');
                    userMenu.classList.toggle('active');
                });

                document.getElementById('btnLogout').addEventListener('click', () => {
                    setUser(null);
                });

                document.getElementById('btnAccountSettings').addEventListener('click', () => {
                    alert(`User Profile Settings:\nName: ${currentUser.name}\nEmail: ${currentUser.email}\nRole: ${currentUser.role}`);
                });
            }

            // Notifications Bell & Panel Setup
            if (bellBtn) {
                bellBtn.setAttribute('id', 'btnBellNotification');

                const notifPanel = document.createElement('div');
                notifPanel.className = 'notification-panel';
                notifPanel.id = 'notificationPanel';
                notifPanel.innerHTML = `
                    <div class="notification-header">
                        <h3>Thông báo & Nhắc nhở</h3>
                        <button class="btn-link" id="btnMarkAllRead">Đánh dấu đã đọc</button>
                    </div>
                    <ul class="notification-list" id="notificationList"></ul>
                    <div class="notification-footer" style="padding:10px 16px; text-align:center; background:var(--bg-body);">
                        <a href="notifications.html" style="color:var(--primary); font-weight:700; text-decoration:none; font-size:12.5px;">
                            <i class="fa-solid fa-list-check"></i> Xem Tất Cả & Lịch Sử Thông Báo
                        </a>
                    </div>
                `;
                navActions.appendChild(notifPanel);

                bellBtn.addEventListener('click', (e) => {
                    e.stopPropagation();
                    const userMenu = document.getElementById('userDropdownMenu');
                    if (userMenu) userMenu.classList.remove('active');
                    notifPanel.classList.toggle('active');
                });

                document.getElementById('btnMarkAllRead').addEventListener('click', () => {
                    const notifs = getNotifications();
                    notifs.forEach(n => { n.read = true; n.status = 'read'; });
                    setNotifications(notifs);
                    renderNotifications();
                    if (window.renderNotificationsCenter) window.renderNotificationsCenter();
                    showToast('Cập nhật Thông báo', 'Đã đánh dấu tất cả là đã đọc.', 'success');
                });

                // Close menus when clicking outside
                document.addEventListener('click', () => {
                    if (notifPanel) notifPanel.classList.remove('active');
                    const userMenu = document.getElementById('userDropdownMenu');
                    if (userMenu) userMenu.classList.remove('active');
                });

                renderNotifications();
            }
        }
    });

    function renderNotifications() {
        const notifList = document.getElementById('notificationList');
        const bellBtn = document.getElementById('btnBellNotification');
        if (!notifList || !bellBtn) return;

        const notifs = getNotifications();
        const unreadCount = notifs.filter(n => (!n.read && n.status !== 'read') || n.status === 'unread').length;

        // Update badge dot or count
        let badge = bellBtn.querySelector('.badge-dot');
        if (unreadCount > 0) {
            if (!badge) {
                badge = document.createElement('span');
                badge.className = 'badge-dot';
                bellBtn.appendChild(badge);
            }
            badge.style.display = 'block';
        } else if (badge) {
            badge.style.display = 'none';
        }

        notifList.innerHTML = '';
        if (notifs.length === 0) {
            notifList.innerHTML = `
                <li style="padding: 30px; text-align: center; color: var(--text-muted);">
                    <i class="fa-solid fa-bell-slash" style="font-size: 24px; margin-bottom: 8px;"></i>
                    <p style="font-size: 14px;">Chưa có thông báo nào.</p>
                </li>
            `;
            return;
        }

        notifs.slice(0, 5).forEach(n => {
            const li = document.createElement('li');
            const isUnread = n.status === 'unread' || (!n.read && n.status !== 'read');
            li.className = `notification-item ${isUnread ? 'unread' : ''}`;
            li.innerHTML = `
                <div class="notification-icon" style="background: ${n.bg || '#eff6ff'}; color: ${n.color || '#1d4ed8'};">
                    <i class="fa-solid ${n.icon || 'fa-bell'}"></i>
                </div>
                <div class="notification-content">
                    <div class="notification-title">${n.title}</div>
                    <div class="notification-desc">${n.desc}</div>
                    <div class="notification-time"><i class="fa-regular fa-clock"></i> ${n.time || 'Vừa xong'}</div>
                </div>
            `;
            li.addEventListener('click', () => {
                const notifPanel = document.getElementById('notificationPanel');
                if (notifPanel) notifPanel.classList.remove('active');
                window.openNotificationDetail(n.id);
            });
            notifList.appendChild(li);
        });
    }

    // Universal Notification Details Modal Creator & Handler
    window.openNotificationDetail = function(id) {
        const notifs = getNotifications();
        const n = notifs.find(item => String(item.id) === String(id));
        if (!n) return;

        // Auto mark as read when opened
        if (n.status === 'unread' || !n.read) {
            n.read = true;
            if (n.status === 'unread') n.status = 'read';
            setNotifications(notifs);
            renderNotifications();
            if (window.renderNotificationsCenter) window.renderNotificationsCenter();
        }

        // Ensure Modal Container Exists in DOM
        let modal = document.getElementById('universalNotifDetailModal');
        if (!modal) {
            modal = document.createElement('div');
            modal.id = 'universalNotifDetailModal';
            modal.className = 'modal-overlay';
            modal.innerHTML = `
                <div class="modal-content shadow-md" style="max-width: 560px;">
                    <div class="modal-header">
                        <div style="display:flex; align-items:center; gap:10px;">
                            <div id="unmIcon" class="notification-icon" style="width:38px; height:38px; font-size:16px;"></div>
                            <h2 id="unmHeaderTitle" style="font-size:17px;">Chi tiết Thông báo</h2>
                        </div>
                        <button class="btn-close" id="btnCloseUnmModal"><i class="fa-solid fa-xmark"></i></button>
                    </div>
                    <div class="modal-body">
                        <div style="margin-bottom:14px; display:flex; gap:6px; flex-wrap:wrap; align-items:center;">
                            <span id="unmCategory" class="notif-cat-badge"></span>
                            <span id="unmPriority" class="priority-pill"></span>
                            <span id="unmStatus" class="badge" style="font-size:11px; padding:4px 8px;"></span>
                        </div>

                        <h3 id="unmTitle" style="font-size:16.5px; font-weight:700; color:var(--text-main); margin-bottom:10px; line-height:1.4;"></h3>
                        
                        <div style="background:var(--bg-body); border:1px solid var(--border-color); border-radius:var(--radius-md); padding:16px; margin-bottom:16px;">
                            <p id="unmDesc" style="font-size:14px; color:var(--text-main); line-height:1.6; margin-bottom:12px;"></p>
                            <div style="font-size:12px; color:var(--text-muted); display:flex; justify-content:space-between;">
                                <span><i class="fa-regular fa-clock"></i> Thời gian: <strong id="unmTime"></strong></span>
                                <span id="unmTimestamp"></span>
                            </div>
                        </div>

                        <div id="unmActionBox" style="margin-bottom:20px; display:none;">
                            <!-- Dynamic Suggested Action -->
                        </div>

                        <div class="form-actions" style="justify-content:space-between;">
                            <div style="display:flex; gap:6px;">
                                <button type="button" class="btn-chip chip-success" id="unmBtnResolve"><i class="fa-solid fa-shield-check"></i> Đã xử lý</button>
                                <button type="button" class="btn-chip text-danger" id="unmBtnDelete"><i class="fa-solid fa-trash"></i> Xóa</button>
                            </div>
                            <button type="button" class="btn-primary" id="unmBtnClose">Đóng</button>
                        </div>
                    </div>
                </div>
            `;
            document.body.appendChild(modal);

            document.getElementById('btnCloseUnmModal').addEventListener('click', () => modal.classList.remove('active'));
            document.getElementById('unmBtnClose').addEventListener('click', () => modal.classList.remove('active'));
        }

        // Fill modal content
        const unmIcon = document.getElementById('unmIcon');
        unmIcon.className = `notification-icon`;
        unmIcon.style.background = n.bg || '#eff6ff';
        unmIcon.style.color = n.color || '#1d4ed8';
        unmIcon.innerHTML = `<i class="fa-solid ${n.icon || 'fa-bell'}"></i>`;

        document.getElementById('unmTitle').textContent = n.title;
        document.getElementById('unmDesc').textContent = n.desc;
        document.getElementById('unmTime').textContent = n.time || 'Vừa xong';
        document.getElementById('unmTimestamp').textContent = n.timestamp ? new Date(n.timestamp).toLocaleString('vi-VN') : '';

        // Category Tag
        const unmCat = document.getElementById('unmCategory');
        unmCat.className = `notif-cat-badge ${n.category === 'Task Reminder' ? 'notif-cat-task' : (n.category === 'Crop Season' ? 'notif-cat-season' : (n.category === 'Inventory Alert' ? 'notif-cat-inventory' : 'notif-cat-upcoming'))}`;
        unmCat.textContent = n.category || 'Thông báo';

        // Priority Pill
        const unmPriority = document.getElementById('unmPriority');
        unmPriority.className = `priority-pill ${n.priority === 'critical' ? 'pill-critical' : (n.priority === 'warning' ? 'pill-warning' : 'pill-info')}`;
        unmPriority.innerHTML = n.priority === 'critical' ? '<i class="fa-solid fa-triangle-exclamation"></i> Khẩn cấp' : (n.priority === 'warning' ? '<i class="fa-solid fa-circle-exclamation"></i> Cảnh báo' : '<i class="fa-solid fa-circle-info"></i> Thông tin');

        // Status Badge
        const unmStatus = document.getElementById('unmStatus');
        if (n.status === 'resolved') {
            unmStatus.style.background = '#dcfce7';
            unmStatus.style.color = '#15803d';
            unmStatus.textContent = 'ĐÃ XỬ LÝ (RESOLVED)';
        } else {
            unmStatus.style.background = 'var(--primary-light)';
            unmStatus.style.color = 'var(--primary-hover)';
            unmStatus.textContent = 'ĐÃ ĐỌC (READ)';
        }

        // Action Box Link
        const actionBox = document.getElementById('unmActionBox');
        if (n.category === 'Task Reminder' || n.category === 'Crop Season' || n.category === 'Upcoming Activity') {
            actionBox.style.display = 'block';
            actionBox.innerHTML = `
                <a href="activities.html" class="btn-primary" style="display:inline-flex; align-items:center; gap:8px; text-decoration:none; padding:10px 18px; border-radius:var(--radius-md);">
                    <i class="fa-solid fa-arrow-right-to-bracket"></i> Đi đến Phân hệ Theo dõi Hoạt động Canh tác
                </a>
            `;
        } else if (n.category === 'Inventory Alert') {
            actionBox.style.display = 'block';
            actionBox.innerHTML = `
                <div style="background:#fff7ed; border:1px solid rgba(249,115,22,0.3); padding:12px; border-radius:var(--radius-md); font-size:13px; color:#c2410c;">
                    <strong><i class="fa-solid fa-boxes-stacked"></i> Hướng dẫn xử lý kho:</strong> Đăng nhập hệ thống quản lý vật tư hoặc liên hệ cán bộ quản lý kho để lập phiếu xuất/nhập hàng.
                </div>
            `;
        } else {
            actionBox.style.display = 'none';
        }

        // Resolve Button Action
        const btnResolve = document.getElementById('unmBtnResolve');
        btnResolve.onclick = function() {
            n.status = 'resolved';
            n.read = true;
            setNotifications(notifs);
            renderNotifications();
            if (window.renderNotificationsCenter) window.renderNotificationsCenter();
            modal.classList.remove('active');
            showToast('Đã xử lý', `Cảnh báo "${n.title}" đã được ghi nhận xử lý xong.`, 'success');
        };

        // Delete Button Action
        const btnDelete = document.getElementById('unmBtnDelete');
        btnDelete.onclick = function() {
            if (confirm(`Bạn có chắc muốn xóa thông báo "${n.title}"?`)) {
                const updated = notifs.filter(item => String(item.id) !== String(id));
                setNotifications(updated);
                renderNotifications();
                if (window.renderNotificationsCenter) window.renderNotificationsCenter();
                modal.classList.remove('active');
                showToast('Đã xóa', 'Đã xóa thông báo khỏi hệ thống.', 'warning');
            }
        };

        modal.classList.add('active');
    };

    // Expose global helper to push a new notification live
    window.addFarmNotification = function (title, desc, type = 'info') {
        const notifs = getNotifications();
        const newNotif = {
            id: 'NOTIF-' + Date.now().toString().slice(-4),
            title,
            desc,
            time: 'Vừa xong',
            timestamp: new Date().toISOString(),
            read: false,
            status: 'unread',
            category: type === 'warning' ? 'Inventory Alert' : (type === 'success' ? 'Task Reminder' : 'General'),
            priority: type === 'danger' ? 'critical' : (type === 'warning' ? 'warning' : 'info'),
            icon: type === 'warning' ? 'fa-triangle-exclamation' : (type === 'success' ? 'fa-circle-check' : 'fa-bell'),
            bg: type === 'warning' ? '#fffbeb' : (type === 'success' ? '#ecfdf5' : '#eff6ff'),
            color: type === 'warning' ? '#b45309' : (type === 'success' ? '#047857' : '#1d4ed8')
        };
        notifs.unshift(newNotif);
        setNotifications(notifs);
        renderNotifications();
        if (window.renderNotificationsCenter) window.renderNotificationsCenter();
        showToast(title, desc, type);
    };

    window.AgriAuth = {
        getUser,
        setUser
    };
})();

