// AgriManage Shared System JS (Authentication, Profile & Real-time Notifications)

(function () {
    // 1. Initial User Session Setup
    const DEFAULT_USER = {
        name: 'Farm Manager',
        role: 'Administrator',
        email: 'manager@agrimanage.com',
        avatar: 'https://i.pravatar.cc/150?img=33'
    };

    function getUser() {
        const stored = localStorage.getItem('agri_user');
        if (stored) {
            try { return JSON.parse(stored); } catch (e) {}
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

    // 2. Initial Notifications Data
    const DEFAULT_NOTIFICATIONS = [
        {
            id: 1,
            title: 'Irrigation Warning',
            desc: 'Low water pressure detected in Sector 4 sprinklers.',
            time: '10 mins ago',
            read: false,
            type: 'warning',
            icon: 'fa-droplet',
            bg: 'var(--accent-orange-light)',
            color: 'var(--accent-orange)'
        },
        {
            id: 2,
            title: 'Task Completed',
            desc: 'Jane Smith completed "Harvest apples in Orchard B".',
            time: '1 hour ago',
            read: false,
            type: 'success',
            icon: 'fa-circle-check',
            bg: 'var(--primary-light)',
            color: 'var(--primary)'
        },
        {
            id: 3,
            title: 'Upcoming Season Event',
            desc: 'Soil fertilization scheduled for Corn Field 1 tomorrow.',
            time: '3 hours ago',
            read: false,
            type: 'info',
            icon: 'fa-calendar-day',
            bg: 'var(--accent-teal-light)',
            color: 'var(--accent-teal)'
        },
        {
            id: 4,
            title: 'Pest Alert Resolved',
            desc: 'Biological scan cleared in Berry Patch Sector 2.',
            time: 'Yesterday',
            read: true,
            type: 'info',
            icon: 'fa-shield-halved',
            bg: 'var(--accent-blue-light)',
            color: 'var(--accent-blue)'
        }
    ];

    function getNotifications() {
        const stored = localStorage.getItem('agri_notifications');
        if (stored) {
            try { return JSON.parse(stored); } catch (e) {}
        }
        return DEFAULT_NOTIFICATIONS;
    }

    function setNotifications(notifs) {
        localStorage.setItem('agri_notifications', JSON.stringify(notifs));
    }

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
            // Add Weather Widget
            if (!navActions.querySelector('.nav-weather-widget')) {
                const weatherWidget = document.createElement('div');
                weatherWidget.className = 'nav-weather-widget';
                weatherWidget.innerHTML = `<i class="fa-solid fa-sun"></i> <span>28°C Sunny</span>`;
                navActions.insertBefore(weatherWidget, navActions.firstChild);
            }

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
                        <h3>Notifications</h3>
                        <button class="btn-link" id="btnMarkAllRead">Mark all read</button>
                    </div>
                    <ul class="notification-list" id="notificationList"></ul>
                    <div class="notification-footer">
                        <span style="font-size:12px; color:var(--text-muted);">Real-time Farm Alerts & Activity</span>
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
                    notifs.forEach(n => n.read = true);
                    setNotifications(notifs);
                    renderNotifications();
                    showToast('Notifications Updated', 'All notifications marked as read.', 'success');
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
        const unreadCount = notifs.filter(n => !n.read).length;

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
                    <p style="font-size: 14px;">No notifications yet.</p>
                </li>
            `;
            return;
        }

        notifs.forEach(n => {
            const li = document.createElement('li');
            li.className = `notification-item ${n.read ? '' : 'unread'}`;
            li.innerHTML = `
                <div class="notification-icon" style="background: ${n.bg}; color: ${n.color};">
                    <i class="fa-solid ${n.icon}"></i>
                </div>
                <div class="notification-content">
                    <div class="notification-title">${n.title}</div>
                    <div class="notification-desc">${n.desc}</div>
                    <div class="notification-time">${n.time}</div>
                </div>
            `;

            li.addEventListener('click', () => {
                n.read = true;
                setNotifications(notifs);
                renderNotifications();
            });

            notifList.appendChild(li);
        });
    }

    // Expose global helper to push a new notification live
    window.addFarmNotification = function (title, desc, type = 'info') {
        const notifs = getNotifications();
        const newNotif = {
            id: Date.now(),
            title,
            desc,
            time: 'Just now',
            read: false,
            type,
            icon: type === 'warning' ? 'fa-triangle-exclamation' : (type === 'success' ? 'fa-circle-check' : 'fa-bell'),
            bg: type === 'warning' ? 'var(--accent-orange-light)' : (type === 'success' ? 'var(--primary-light)' : 'var(--accent-blue-light)'),
            color: type === 'warning' ? 'var(--accent-orange)' : (type === 'success' ? 'var(--primary)' : 'var(--accent-blue)')
        };
        notifs.unshift(newNotif);
        setNotifications(notifs);
        renderNotifications();
        showToast(title, desc, type);
    };

    window.AgriAuth = {
        getUser,
        setUser
    };
})();
