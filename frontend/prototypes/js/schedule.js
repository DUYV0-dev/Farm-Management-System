/**
 * AgriManage - Farm Activity Calendar & Season Planner Module JS
 * Handles calendar grid rendering, agenda list view, crop season upcoming activities,
 * overdue tasks detection & highlighting, task detail modal, and status/season filtering.
 */

document.addEventListener('DOMContentLoaded', () => {
    // Current Baseline System Date: 16/10/2026
    const TODAY_STR = '2026-10-16';
    let currentDate = new Date(2026, 9, 16); // October 2026
    let currentViewMode = 'grid'; // 'grid' or 'agenda'

    // Initial Activities across Seasons with Overdue, In Progress, Completed and Upcoming states
    const DEFAULT_ACTIVITIES = [
        {
            id: 'ACT-101',
            title: 'Bón phân đợt 1 cho lúa Đông Xuân Sector 1',
            season: 'Mùa Đông Xuân 2026',
            category: 'Bón phân',
            assignee: 'Nguyễn Văn Hùng',
            sector: 'Cánh đồng Lúa Sector 1',
            priority: 'High',
            startDate: '2026-10-01',
            dueDate: '2026-10-04',
            status: 'completed',
            progress: 100,
            description: 'Bón thúc đợt 1 phân NPK 20-20-15 tạo đà đẻ nhánh khỏe cho lúa vụ Đông Xuân.'
        },
        {
            id: 'ACT-102',
            title: 'Phun thuốc sinh học phòng trừ sâu cuốn lá Greenhouse A2',
            season: 'Mùa Đông Xuân 2026',
            category: 'Phun thuốc',
            assignee: 'Lê Thị Mai',
            sector: 'Nhà màng Greenhouse A2',
            priority: 'Critical',
            startDate: '2026-10-04',
            dueDate: '2026-10-07', // Due date was Oct 7, today is Oct 16 -> OVERDUE 9 days!
            status: 'in_progress',
            progress: 65,
            description: 'Sử dụng chế phẩm sinh học BT phun phòng ngừa sâu cuốn lá giai đoạn phát triển mầm.'
        },
        {
            id: 'ACT-105',
            title: 'Bảo trì hệ thống tưới nhỏ giọt & Bộ lọc đĩa Control Unit A',
            season: 'Mùa Đông Xuân 2026',
            category: 'Kiểm tra & Bảo trì',
            assignee: 'Võ Hà Duy',
            sector: 'Trạm Control Unit A',
            priority: 'High',
            startDate: '2026-10-05',
            dueDate: '2026-10-06', // Due date Oct 6 -> OVERDUE 10 days!
            status: 'in_progress',
            progress: 40,
            description: 'Súc rửa lọc đĩa 120 mesh, kiểm tra áp suất đường ống chính dưới 1.2 bar.'
        },
        {
            id: 'ACT-104',
            title: 'Làm đất & Cày xới lên luống chuẩn bị gieo ngô',
            season: 'Mùa Thu Đông 2026',
            category: 'Làm đất',
            assignee: 'Trần Văn Lượng',
            sector: 'Lô đất D3 - Phía Nam',
            priority: 'Medium',
            startDate: '2026-10-08',
            dueDate: '2026-10-12', // Due date Oct 12 -> OVERDUE 4 days!
            status: 'todo',
            progress: 0,
            description: 'Cày sâu 25cm, lên luống cao 30cm và rải vôi bột xử lý nấm đất.'
        },
        {
            id: 'ACT-103',
            title: 'Thu hoạch Cà chua sô cô la vụ Hè Thu - Greenhouse B',
            season: 'Mùa Hè Thu 2026',
            category: 'Thu hoạch',
            assignee: 'Phạm Thanh Sơn',
            sector: 'Greenhouse B',
            priority: 'High',
            startDate: '2026-10-05',
            dueDate: '2026-10-08',
            status: 'completed',
            progress: 100,
            description: 'Hái cà chua sô cô la đạt chín 85%, đóng thùng xuất xưởng 1,850 kg.'
        },
        {
            id: 'ACT-106',
            title: 'Kiểm tra độ ẩm & Đo dinh dưỡng đất Lô D3',
            season: 'Mùa Thu Đông 2026',
            category: 'Kiểm tra & Bảo trì',
            assignee: 'Lê Thị Mai',
            sector: 'Lô đất D3 - Phía Nam',
            priority: 'Medium',
            startDate: '2026-10-15',
            dueDate: '2026-10-16', // Today!
            status: 'in_progress',
            progress: 50,
            description: 'Lấy mẫu đất kiểm tra độ pH (mục tiêu 6.5) và hàm lượng chất hữu cơ trước khi gieo.'
        },
        {
            id: 'ACT-107',
            title: 'Xuống giống ngô ngọt F1 Thái Lan vụ Thu Đông',
            season: 'Mùa Thu Đông 2026',
            category: 'Gieo trồng',
            assignee: 'Nguyễn Văn Hùng',
            sector: 'Cánh đồng Lô D3 - Phía Nam',
            priority: 'High',
            startDate: '2026-10-18',
            dueDate: '2026-10-21',
            status: 'todo',
            progress: 0,
            description: 'Gieo hạt giống bắp ngọt F1 khoảng cách 30cm, độ sâu 3cm, đảm bảo độ ẩm 75%.'
        },
        {
            id: 'ACT-108',
            title: 'Bón thúc đợt 2 phân vi sinh hữu cơ cho lúa Sector 1',
            season: 'Mùa Đông Xuân 2026',
            category: 'Bón phân',
            assignee: 'Phạm Thanh Sơn',
            sector: 'Cánh đồng Lúa Sector 1',
            priority: 'Medium',
            startDate: '2026-10-20',
            dueDate: '2026-10-24',
            status: 'todo',
            progress: 0,
            description: 'Bón thúc đợt 2 kích thích đẻ nhánh hữu hiệu và phát triển bộ rễ.'
        },
        {
            id: 'ACT-109',
            title: 'Kiểm tra dư lượng & Đo độ ngọt Brix Dưa lưới Greenhouse C1',
            season: 'Mùa Hè Thu 2026',
            category: 'Kiểm tra & Bảo trì',
            assignee: 'Lê Thị Mai',
            sector: 'Greenhouse C1',
            priority: 'High',
            startDate: '2026-10-22',
            dueDate: '2026-10-23',
            status: 'todo',
            progress: 0,
            description: 'Lấy mẫu kiểm định đạt độ brix > 14.5% và cách ly an toàn trước thu hoạch.'
        },
        {
            id: 'ACT-110',
            title: 'Đại thu hoạch Dưa lưới Huỳnh Long ruột cam xuất khẩu',
            season: 'Mùa Hè Thu 2026',
            category: 'Thu hoạch',
            assignee: 'Phạm Thanh Sơn',
            sector: 'Greenhouse C1',
            priority: 'Critical',
            startDate: '2026-10-25',
            dueDate: '2026-10-28',
            status: 'todo',
            progress: 0,
            description: 'Thu hoạch cắt cuống chữ T dự kiến 4.2 tấn dưa lưới Huỳnh Long dán tem QR VietGAP.'
        },
        {
            id: 'ACT-111',
            title: 'Vệ sinh khử khuẩn buồng ươm & Bồn chứa thủy canh',
            season: 'Mùa Đông Xuân 2026',
            category: 'Làm đất',
            assignee: 'Võ Hà Duy',
            sector: 'Khu Thủy canh Greenhouse D',
            priority: 'Low',
            startDate: '2026-10-28',
            dueDate: '2026-10-30',
            status: 'todo',
            progress: 0,
            description: 'Súc rửa bồn chứa dung dịch dinh dưỡng bằng nước ozone và thay giá thể xơ dừa.'
        }
    ];

    // Load activities from localStorage (synced with activities.html) or initialize
    function getActivities() {
        const stored = localStorage.getItem('agri_activities');
        if (stored) {
            try {
                const parsed = JSON.parse(stored);
                if (Array.isArray(parsed) && parsed.length >= DEFAULT_ACTIVITIES.length) {
                    return parsed;
                }
            } catch (e) { }
        }
        localStorage.setItem('agri_activities', JSON.stringify(DEFAULT_ACTIVITIES));
        return DEFAULT_ACTIVITIES;
    }

    function saveActivities(actList) {
        localStorage.setItem('agri_activities', JSON.stringify(actList));
    }

    let activities = getActivities();

    // Helper: Detect if task is Overdue
    function isTaskOverdue(task) {
        if (!task || !task.dueDate) return false;
        if (task.status === 'completed') return false;
        return task.dueDate < TODAY_STR;
    }

    // Helper: Days overdue count
    function getDaysOverdue(dueDateStr) {
        const d1 = new Date(TODAY_STR);
        const d2 = new Date(dueDateStr);
        const diffTime = d1 - d2;
        return Math.max(1, Math.ceil(diffTime / (1000 * 60 * 60 * 24)));
    }

    // DOM Elements
    const calendarGrid = document.getElementById('calendarGrid');
    const calendarGridViewContainer = document.getElementById('calendarGridViewContainer');
    const calendarAgendaViewContainer = document.getElementById('calendarAgendaViewContainer');
    const agendaList = document.getElementById('agendaList');
    const monthYearText = document.getElementById('currentMonthYear');

    // Controls
    const btnPrevMonth = document.getElementById('btnPrevMonth');
    const btnNextMonth = document.getElementById('btnNextMonth');
    const btnToday = document.getElementById('btnToday');
    const btnViewMonthGrid = document.getElementById('btnViewMonthGrid');
    const btnViewAgenda = document.getElementById('btnViewAgenda');

    // Filters
    const filterSeason = document.getElementById('filterSeason');
    const filterStatus = document.getElementById('filterStatus');
    const filterCategory = document.getElementById('filterCategory');
    const filterSearch = document.getElementById('filterSearch');
    const btnResetFilters = document.getElementById('btnResetFilters');

    // Overdue Alert Banner
    const overdueAlertBanner = document.getElementById('overdueAlertBanner');
    const overdueAlertTitle = document.getElementById('overdueAlertTitle');
    const overdueAlertDesc = document.getElementById('overdueAlertDesc');
    const btnFilterOverdueOnly = document.getElementById('btnFilterOverdueOnly');

    // KPIs
    const kpiMonthTotal = document.getElementById('kpiMonthTotal');
    const kpiInProgressCount = document.getElementById('kpiInProgressCount');
    const kpiUpcomingCount = document.getElementById('kpiUpcomingCount');
    const kpiOverdueCount = document.getElementById('kpiOverdueCount');
    const kpiMonthName = document.getElementById('kpiMonthName');

    // Sidebars
    const upcomingActivitiesContainer = document.getElementById('upcomingActivitiesContainer');
    const upcomingBadgeCount = document.getElementById('upcomingBadgeCount');
    const overdueTasksContainer = document.getElementById('overdueTasksContainer');
    const overdueBadgeCount = document.getElementById('overdueBadgeCount');

    // Modals
    const taskDetailModal = document.getElementById('taskDetailModal');
    const btnCloseDetailModal = document.getElementById('btnCloseDetailModal');
    const btnModalClose = document.getElementById('btnModalClose');
    const btnModalCompleteTask = document.getElementById('btnModalCompleteTask');
    const btnModalGoActivities = document.getElementById('btnModalGoActivities');

    const eventModal = document.getElementById('eventModal');
    const btnAddSchedule = document.getElementById('btnAddSchedule');
    const btnCloseEventModal = document.getElementById('btnCloseEventModal');
    const btnCancelEvent = document.getElementById('btnCancelEvent');
    const eventForm = document.getElementById('eventForm');
    const btnExportSchedule = document.getElementById('btnExportSchedule');

    let activeDetailTask = null;

    // Category CSS mapping
    function getCategoryClass(cat) {
        if (!cat) return 'event-cat-soil';
        if (cat === 'Làm đất') return 'event-cat-soil';
        if (cat === 'Gieo trồng') return 'event-cat-plant';
        if (cat === 'Bón phân') return 'event-cat-fertilizer';
        if (cat === 'Tưới nước') return 'event-cat-irrigation';
        if (cat === 'Phun thuốc') return 'event-cat-spray';
        if (cat === 'Thu hoạch') return 'event-cat-harvest';
        return 'event-cat-maintenance';
    }

    // Category Icon mapping
    function getCategoryIcon(cat) {
        if (!cat) return 'fa-leaf';
        if (cat === 'Làm đất') return 'fa-tractor';
        if (cat === 'Gieo trồng') return 'fa-seedling';
        if (cat === 'Bón phân') return 'fa-leaf';
        if (cat === 'Tưới nước') return 'fa-droplet';
        if (cat === 'Phun thuốc') return 'fa-spray-can-sparkles';
        if (cat === 'Thu hoạch') return 'fa-wheat-awn';
        return 'fa-wrench';
    }

    // Navigation Listeners
    btnPrevMonth.addEventListener('click', () => {
        currentDate.setMonth(currentDate.getMonth() - 1);
        renderAll();
    });

    btnNextMonth.addEventListener('click', () => {
        currentDate.setMonth(currentDate.getMonth() + 1);
        renderAll();
    });

    btnToday.addEventListener('click', () => {
        currentDate = new Date(2026, 9, 16);
        renderAll();
    });

    // View Mode Switcher Listeners
    btnViewMonthGrid.addEventListener('click', () => {
        currentViewMode = 'grid';
        btnViewMonthGrid.classList.add('active');
        btnViewAgenda.classList.remove('active');
        calendarGridViewContainer.style.display = 'block';
        calendarAgendaViewContainer.style.display = 'none';
        renderCalendarGrid();
    });

    btnViewAgenda.addEventListener('click', () => {
        currentViewMode = 'agenda';
        btnViewAgenda.classList.add('active');
        btnViewMonthGrid.classList.remove('active');
        calendarGridViewContainer.style.display = 'none';
        calendarAgendaViewContainer.style.display = 'block';
        renderAgendaList();
    });

    // Filter Change Listeners
    filterSeason.addEventListener('change', renderAll);
    filterStatus.addEventListener('change', renderAll);
    filterCategory.addEventListener('change', renderAll);
    filterSearch.addEventListener('input', renderAll);

    btnResetFilters.addEventListener('click', () => {
        filterSeason.value = '';
        filterStatus.value = '';
        filterCategory.value = '';
        filterSearch.value = '';
        renderAll();
    });

    btnFilterOverdueOnly.addEventListener('click', () => {
        filterStatus.value = 'overdue';
        renderAll();
    });

    // Main Filter Function
    function getFilteredActivities() {
        const season = filterSeason.value;
        const status = filterStatus.value;
        const category = filterCategory.value;
        const query = filterSearch.value.trim().toLowerCase();

        return activities.filter(act => {
            // Season filter
            if (season && act.season !== season) return false;

            // Category filter
            if (category && act.category !== category) return false;

            // Status filter (including 'overdue')
            if (status) {
                if (status === 'overdue') {
                    if (!isTaskOverdue(act)) return false;
                } else if (act.status !== status) {
                    return false;
                }
            }

            // Keyword search filter
            if (query) {
                const matchTitle = act.title && act.title.toLowerCase().includes(query);
                const matchAssignee = act.assignee && act.assignee.toLowerCase().includes(query);
                const matchSector = act.sector && act.sector.toLowerCase().includes(query);
                const matchId = act.id && act.id.toLowerCase().includes(query);
                if (!matchTitle && !matchAssignee && !matchSector && !matchId) return false;
            }

            return true;
        });
    }

    // Render Calendar Grid
    function renderCalendarGrid() {
        const year = currentDate.getFullYear();
        const month = currentDate.getMonth();

        const monthNames = [
            "Tháng 1", "Tháng 2", "Tháng 3", "Tháng 4", "Tháng 5", "Tháng 6",
            "Tháng 7", "Tháng 8", "Tháng 9", "Tháng 10", "Tháng 11", "Tháng 12"
        ];
        monthYearText.textContent = `${monthNames[month]}, ${year}`;
        if (kpiMonthName) kpiMonthName.textContent = `${monthNames[month]}/${year}`;

        // Preserve Day Headers
        const dayHeaders = Array.from(calendarGrid.querySelectorAll('.calendar-day-header'));
        calendarGrid.innerHTML = '';
        dayHeaders.forEach(dh => calendarGrid.appendChild(dh));

        const firstDayOfMonth = new Date(year, month, 1).getDay(); // 0 is Sunday
        const startingIndex = firstDayOfMonth === 0 ? 6 : firstDayOfMonth - 1; // Mon=0, Sun=6
        const totalDaysInMonth = new Date(year, month + 1, 0).getDate();
        const prevMonthLastDate = new Date(year, month, 0).getDate();

        const filteredList = getFilteredActivities();

        // 1. Previous Month Padding Cells
        for (let i = startingIndex - 1; i >= 0; i--) {
            const cell = document.createElement('div');
            cell.className = 'calendar-day-cell other-month';
            cell.innerHTML = `
                <div class="calendar-day-top">
                    <span class="day-number">${prevMonthLastDate - i}</span>
                </div>
            `;
            calendarGrid.appendChild(cell);
        }

        // 2. Current Month Day Cells
        for (let day = 1; day <= totalDaysInMonth; day++) {
            const cell = document.createElement('div');
            const dayFormatted = day < 10 ? `0${day}` : `${day}`;
            const monthFormatted = (month + 1) < 10 ? `0${month + 1}` : `${month + 1}`;
            const dateStr = `${year}-${monthFormatted}-${dayFormatted}`;
            const isToday = (dateStr === TODAY_STR);

            cell.className = `calendar-day-cell ${isToday ? 'today' : ''}`;

            // Cell Header
            cell.innerHTML = `
                <div class="calendar-day-top">
                    <span class="day-number">${day}</span>
                    ${isToday ? '<span class="today-label-badge"><i class="fa-solid fa-star"></i> Hôm nay</span>' : ''}
                </div>
                <div class="events-list-wrapper"></div>
            `;

            const eventsWrapper = cell.querySelector('.events-list-wrapper');

            // Find matching tasks for this day:
            // A task belongs to this day if dateStr matches dueDate or startDate, or falls in between
            const dayTasks = filteredList.filter(t => {
                const sDate = t.startDate || t.dueDate;
                const dDate = t.dueDate || t.startDate;
                return (dateStr >= sDate && dateStr <= dDate) || (dateStr === dDate);
            });

            // Render up to 3 event pills, with +X more badge if needed
            const visibleTasks = dayTasks.slice(0, 3);
            visibleTasks.forEach(task => {
                const overdue = isTaskOverdue(task);
                const pill = document.createElement('div');
                pill.className = `calendar-event-item ${getCategoryClass(task.category)} ${overdue ? 'is-overdue' : ''} ${task.status === 'completed' ? 'is-completed' : ''}`;

                const catIcon = getCategoryIcon(task.category);

                pill.innerHTML = `
                    <span class="event-title-span" title="${task.title} - ${task.sector}">
                        <i class="fa-solid ${overdue ? 'fa-triangle-exclamation text-danger' : catIcon}"></i>
                        <span>${task.title}</span>
                    </span>
                    ${overdue ? '<span class="overdue-tag">Trễ</span>' : (task.status === 'completed' ? '<i class="fa-solid fa-check text-green"></i>' : '')}
                `;

                pill.addEventListener('click', (e) => {
                    e.stopPropagation();
                    openTaskDetailModal(task);
                });

                eventsWrapper.appendChild(pill);
            });

            if (dayTasks.length > 3) {
                const moreBadge = document.createElement('div');
                moreBadge.className = 'more-events-badge';
                moreBadge.textContent = `+${dayTasks.length - 3} việc khác`;
                moreBadge.addEventListener('click', (e) => {
                    e.stopPropagation();
                    // Switch to agenda view or open first task
                    openTaskDetailModal(dayTasks[3]);
                });
                eventsWrapper.appendChild(moreBadge);
            }

            calendarGrid.appendChild(cell);
        }

        // 3. Next Month Padding Cells
        const totalCells = startingIndex + totalDaysInMonth;
        const nextPadding = totalCells % 7 === 0 ? 0 : 7 - (totalCells % 7);
        for (let j = 1; j <= nextPadding; j++) {
            const cell = document.createElement('div');
            cell.className = 'calendar-day-cell other-month';
            cell.innerHTML = `
                <div class="calendar-day-top">
                    <span class="day-number">${j}</span>
                </div>
            `;
            calendarGrid.appendChild(cell);
        }
    }

    // Render Agenda List View
    function renderAgendaList() {
        agendaList.innerHTML = '';
        const filteredList = getFilteredActivities();

        if (filteredList.length === 0) {
            agendaList.innerHTML = `
                <div style="text-align:center; padding:40px 20px; background:#fff; border-radius:var(--radius-md); border:1px solid var(--border-color);">
                    <i class="fa-solid fa-calendar-xmark" style="font-size:32px; color:var(--text-muted); margin-bottom:10px;"></i>
                    <p style="color:var(--text-muted); font-size:14px;">Không tìm thấy công việc canh tác nào phù hợp với bộ lọc hiện tại.</p>
                </div>
            `;
            return;
        }

        // Sort by dueDate
        const sorted = [...filteredList].sort((a, b) => (a.dueDate || '').localeCompare(b.dueDate || ''));

        // Group by Date
        const grouped = {};
        sorted.forEach(t => {
            const d = t.dueDate || t.startDate || 'Chưa định ngày';
            if (!grouped[d]) grouped[d] = [];
            grouped[d].push(t);
        });

        Object.keys(grouped).forEach(dateKey => {
            const groupCard = document.createElement('div');
            groupCard.className = 'agenda-day-group';

            const isToday = (dateKey === TODAY_STR);
            let dateLabel = dateKey;
            if (isToday) dateLabel = `${dateKey} (Hôm nay)`;

            groupCard.innerHTML = `
                <div class="agenda-day-header">
                    <div class="agenda-day-title">
                        <i class="fa-regular fa-calendar-check text-primary"></i> ${dateLabel}
                    </div>
                    <span class="badge" style="background:#e2e8f0; color:#334155; font-size:11.5px; font-weight:700;">
                        ${grouped[dateKey].length} công việc
                    </span>
                </div>
                <div class="agenda-tasks-list"></div>
            `;

            const tasksListContainer = groupCard.querySelector('.agenda-tasks-list');
            grouped[dateKey].forEach(task => {
                const overdue = isTaskOverdue(task);
                const row = document.createElement('div');
                row.className = `agenda-task-row ${overdue ? 'overdue-row' : ''}`;

                row.innerHTML = `
                    <div style="display:flex; align-items:center; gap:12px; flex:1;">
                        <div style="width:36px; height:36px; border-radius:10px; background:${overdue ? '#fef2f2' : 'var(--primary-light)'}; color:${overdue ? '#dc2626' : 'var(--primary-hover)'}; display:flex; align-items:center; justify-content:center; font-size:16px;">
                            <i class="fa-solid ${overdue ? 'fa-triangle-exclamation' : getCategoryIcon(task.category)}"></i>
                        </div>
                        <div>
                            <div style="display:flex; align-items:center; gap:8px; flex-wrap:wrap;">
                                <strong style="font-size:14px; color:var(--text-main);">${task.title}</strong>
                                ${overdue ? `<span class="badge" style="background:#fef2f2; color:#dc2626; border:1px solid #f87171; font-size:11px; font-weight:800;"><i class="fa-solid fa-clock"></i> QUÁ HẠN (${getDaysOverdue(task.dueDate)} ngày)</span>` : ''}
                                <span class="badge" style="background:#ecfdf5; color:#047857; font-size:11px;">${task.season}</span>
                            </div>
                            <div style="font-size:12.5px; color:var(--text-muted); margin-top:2px;">
                                <span><i class="fa-solid fa-user-gear"></i> ${task.assignee}</span> • 
                                <span><i class="fa-solid fa-location-dot"></i> ${task.sector}</span>
                            </div>
                        </div>
                    </div>

                    <div style="display:flex; align-items:center; gap:16px;">
                        <div style="text-align:right;">
                            <div style="font-size:12px; font-weight:700; color:var(--text-main);">${task.progress}%</div>
                            <div class="progress-bar" style="width:70px; height:5px; background:#e2e8f0; border-radius:3px; margin-top:4px;">
                                <div style="height:100%; width:${task.progress}%; background:var(--primary);"></div>
                            </div>
                        </div>
                        <span class="badge" style="font-size:11.5px; font-weight:700; background:${task.status === 'completed' ? '#dcfce7' : (task.status === 'in_progress' ? '#fff7ed' : '#f1f5f9')}; color:${task.status === 'completed' ? '#15803d' : (task.status === 'in_progress' ? '#c2410c' : '#475569')};">
                            ${task.status === 'completed' ? 'Hoàn thành' : (task.status === 'in_progress' ? 'Đang làm' : 'Chưa bắt đầu')}
                        </span>
                        <i class="fa-solid fa-chevron-right" style="color:var(--text-muted); font-size:12px;"></i>
                    </div>
                `;

                row.addEventListener('click', () => openTaskDetailModal(task));
                tasksListContainer.appendChild(row);
            });

            agendaList.appendChild(groupCard);
        });
    }

    // Render Sidebars & Overdue Banner & KPIs
    function renderSidebarsAndKpi() {
        const allActs = activities;

        // 1. Overdue Tasks
        const overdueTasks = allActs.filter(t => isTaskOverdue(t));
        if (kpiOverdueCount) kpiOverdueCount.textContent = overdueTasks.length;
        if (overdueBadgeCount) overdueBadgeCount.textContent = `${overdueTasks.length} việc`;

        if (overdueTasks.length > 0) {
            overdueAlertBanner.style.display = 'flex';
            overdueAlertTitle.textContent = `Phát hiện ${overdueTasks.length} Công việc Canh tác Quá hạn Cần xử lý gấp!`;
            overdueAlertDesc.textContent = `Các hoạt động đã quá ngày hết hạn mà chưa hoàn thành 100%. Gồm: "${overdueTasks[0].title}" (Trễ ${getDaysOverdue(overdueTasks[0].dueDate)} ngày)...`;
        } else {
            overdueAlertBanner.style.display = 'none';
        }

        // Render Overdue Mini List in Sidebar
        overdueTasksContainer.innerHTML = '';
        if (overdueTasks.length > 0) {
            overdueTasks.forEach(t => {
                const days = getDaysOverdue(t.dueDate);
                const item = document.createElement('div');
                item.className = 'overdue-mini-item';
                item.innerHTML = `
                    <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom:4px;">
                        <span style="font-size:11px; font-weight:800; color:#dc2626; text-transform:uppercase;">
                            <i class="fa-solid fa-triangle-exclamation"></i> Trễ ${days} ngày
                        </span>
                        <span style="font-size:11px; color:#991b1b; font-weight:700;">Hạn: ${t.dueDate}</span>
                    </div>
                    <strong style="font-size:13px; color:#991b1b; display:block; margin-bottom:4px; line-height:1.4;">${t.title}</strong>
                    <div style="font-size:11.5px; color:#7f1d1d; display:flex; justify-content:space-between;">
                        <span><i class="fa-solid fa-user"></i> ${t.assignee}</span>
                        <span>Tiến độ: <strong>${t.progress}%</strong></span>
                    </div>
                `;
                item.addEventListener('click', () => openTaskDetailModal(t));
                overdueTasksContainer.appendChild(item);
            });
        } else {
            overdueTasksContainer.innerHTML = `
                <div style="text-align:center; padding:18px 10px; color:#15803d; font-size:13px; background:#f0fdf4; border-radius:var(--radius-md); border:1px solid #bbf7d0;">
                    <i class="fa-solid fa-circle-check" style="font-size:20px; display:block; margin-bottom:6px;"></i>
                    Tuyệt vời! Không có công việc nào bị quá hạn.
                </div>
            `;
        }

        // 2. Upcoming Crop Season Activities (Dates >= TODAY_STR)
        const upcoming = allActs
            .filter(t => (t.dueDate >= TODAY_STR || t.startDate >= TODAY_STR) && t.status !== 'completed' && !isTaskOverdue(t))
            .sort((a, b) => (a.startDate || a.dueDate).localeCompare(b.startDate || b.dueDate));

        if (kpiUpcomingCount) kpiUpcomingCount.textContent = upcoming.length;
        if (upcomingBadgeCount) upcomingBadgeCount.textContent = `${upcoming.length} việc`;

        upcomingActivitiesContainer.innerHTML = '';
        if (upcoming.length > 0) {
            upcoming.slice(0, 5).forEach(t => {
                const targetDate = t.startDate || t.dueDate;
                let countdownLabel = 'Sắp tới';
                let countdownClass = 'countdown-week';

                if (targetDate === TODAY_STR) {
                    countdownLabel = 'Hôm nay';
                    countdownClass = 'countdown-tomorrow';
                } else {
                    const diffDays = Math.ceil((new Date(targetDate) - new Date(TODAY_STR)) / (1000 * 60 * 60 * 24));
                    if (diffDays === 1) {
                        countdownLabel = 'Ngày mai';
                        countdownClass = 'countdown-tomorrow';
                    } else if (diffDays <= 3) {
                        countdownLabel = `Còn ${diffDays} ngày`;
                        countdownClass = 'countdown-soon';
                    } else {
                        countdownLabel = `Còn ${diffDays} ngày`;
                        countdownClass = 'countdown-week';
                    }
                }

                const item = document.createElement('div');
                item.className = 'upcoming-activity-item';
                item.innerHTML = `
                    <div class="upcoming-item-header">
                        <span class="badge" style="background:#ecfdf5; color:#047857; font-size:11px; font-weight:700;">
                            <i class="fa-solid fa-wheat-awn"></i> ${t.season}
                        </span>
                        <span class="countdown-pill ${countdownClass}">${countdownLabel}</span>
                    </div>
                    <strong style="font-size:13.5px; color:var(--text-main); display:block; margin-bottom:4px; line-height:1.4;">${t.title}</strong>
                    <div style="font-size:12px; color:var(--text-muted); display:flex; justify-content:space-between;">
                        <span><i class="fa-solid fa-location-dot"></i> ${t.sector}</span>
                        <span><i class="fa-regular fa-calendar"></i> ${targetDate}</span>
                    </div>
                `;
                item.addEventListener('click', () => openTaskDetailModal(t));
                upcomingActivitiesContainer.appendChild(item);
            });
        } else {
            upcomingActivitiesContainer.innerHTML = `<div style="color:var(--text-muted); font-size:13px; text-align:center; padding:12px;">Không có hoạt động sắp tới.</div>`;
        }

        // 3. Other KPIs
        const inProgress = allActs.filter(t => t.status === 'in_progress');
        if (kpiInProgressCount) kpiInProgressCount.textContent = inProgress.length;

        // Month total
        const year = currentDate.getFullYear();
        const month = currentDate.getMonth() + 1;
        const monthStr = `${year}-${month < 10 ? '0' + month : month}`;
        const monthTotal = allActs.filter(t => (t.dueDate && t.dueDate.startsWith(monthStr)) || (t.startDate && t.startDate.startsWith(monthStr)));
        if (kpiMonthTotal) kpiMonthTotal.textContent = monthTotal.length;
    }

    // Master Render
    function renderAll() {
        if (currentViewMode === 'grid') {
            renderCalendarGrid();
        } else {
            renderAgendaList();
        }
        renderSidebarsAndKpi();
    }

    // Open Task Detail Modal
    function openTaskDetailModal(task) {
        if (!task) return;
        activeDetailTask = task;

        const overdue = isTaskOverdue(task);

        document.getElementById('modalTaskId').textContent = task.id || 'ACT-EVENT';
        document.getElementById('modalTaskTitle').textContent = task.title;

        // Overdue Alert Notice inside modal
        const alertBox = document.getElementById('modalOverdueAlert');
        if (overdue) {
            alertBox.style.display = 'block';
            document.getElementById('modalOverdueDate').textContent = `${task.dueDate} (Đã trễ ${getDaysOverdue(task.dueDate)} ngày)`;
        } else {
            alertBox.style.display = 'none';
        }

        // Status badge & Progress
        const statusBadge = document.getElementById('modalTaskStatusBadge');
        if (task.status === 'completed') {
            statusBadge.style.background = '#dcfce7';
            statusBadge.style.color = '#15803d';
            statusBadge.innerHTML = '<i class="fa-solid fa-circle-check"></i> Đã hoàn thành (100%)';
        } else if (task.status === 'in_progress') {
            statusBadge.style.background = '#fff7ed';
            statusBadge.style.color = '#c2410c';
            statusBadge.innerHTML = '<i class="fa-solid fa-spinner"></i> Đang thực hiện';
        } else {
            statusBadge.style.background = '#f1f5f9';
            statusBadge.style.color = '#475569';
            statusBadge.innerHTML = '<i class="fa-solid fa-list-check"></i> Chưa bắt đầu (To Do)';
        }

        const pct = task.progress !== undefined ? task.progress : (task.status === 'completed' ? 100 : 0);
        document.getElementById('modalTaskProgressPercent').textContent = `${pct}%`;
        document.getElementById('modalTaskProgressBar').style.width = `${pct}%`;

        // Details
        document.getElementById('modalTaskSeason').textContent = task.season || 'Mùa vụ chung';
        document.getElementById('modalTaskCategory').textContent = task.category || 'Hoạt động canh tác';
        document.getElementById('modalTaskAssignee').textContent = task.assignee || 'Chưa phân công';
        document.getElementById('modalTaskSector').textContent = task.sector || 'Toàn trang trại';
        document.getElementById('modalTaskStartDate').textContent = task.startDate || 'Chưa định ngày';

        const dueEl = document.getElementById('modalTaskDueDate');
        dueEl.textContent = task.dueDate || 'Chưa định hạn';
        dueEl.style.color = overdue ? '#dc2626' : 'var(--text-main)';

        const priorityEl = document.getElementById('modalTaskPriority');
        priorityEl.textContent = task.priority || 'Medium';
        if (task.priority === 'Critical') {
            priorityEl.style.background = '#fef2f2'; priorityEl.style.color = '#dc2626';
        } else if (task.priority === 'High') {
            priorityEl.style.background = '#fff7ed'; priorityEl.style.color = '#ea580c';
        } else {
            priorityEl.style.background = '#f0fdf4'; priorityEl.style.color = '#15803d';
        }

        document.getElementById('modalTaskDesc').textContent = task.description || 'Không có mô tả kỹ thuật chi tiết.';

        // Complete button toggle
        if (task.status === 'completed') {
            btnModalCompleteTask.style.display = 'none';
        } else {
            btnModalCompleteTask.style.display = 'inline-flex';
        }

        taskDetailModal.classList.add('active');
    }

    // Modal Close Listeners
    btnCloseDetailModal.addEventListener('click', () => taskDetailModal.classList.remove('active'));
    btnModalClose.addEventListener('click', () => taskDetailModal.classList.remove('active'));

    // Mark task completed from modal
    btnModalCompleteTask.addEventListener('click', () => {
        if (!activeDetailTask) return;
        activeDetailTask.status = 'completed';
        activeDetailTask.progress = 100;
        saveActivities(activities);
        taskDetailModal.classList.remove('active');
        renderAll();

        if (window.showToast) {
            window.showToast('Hoàn thành xuất sắc!', `Đã đánh dấu hoàn thành 100% công việc: ${activeDetailTask.title}`, 'success');
        } else {
            alert(`Đã hoàn thành công việc: ${activeDetailTask.title}`);
        }
    });

    // Add Schedule Event Modal
    btnAddSchedule.addEventListener('click', () => {
        eventForm.reset();
        document.getElementById('eventStartDate').value = TODAY_STR;
        document.getElementById('eventDueDate').value = '2026-10-25';
        eventModal.classList.add('active');
    });

    btnCloseEventModal.addEventListener('click', () => eventModal.classList.remove('active'));
    btnCancelEvent.addEventListener('click', () => eventModal.classList.remove('active'));

    eventForm.addEventListener('submit', (e) => {
        e.preventDefault();

        const title = document.getElementById('eventTitle').value.trim();
        const season = document.getElementById('eventSeason').value;
        const category = document.getElementById('eventCategory').value;
        const assignee = document.getElementById('eventAssignee').value;
        const sector = document.getElementById('eventSector').value.trim();
        const startDate = document.getElementById('eventStartDate').value;
        const dueDate = document.getElementById('eventDueDate').value;
        const priority = document.getElementById('eventPriority').value;
        const description = document.getElementById('eventDescription').value.trim();

        const newAct = {
            id: 'ACT-' + Date.now().toString().slice(-4),
            title,
            season,
            category,
            assignee,
            sector,
            startDate,
            dueDate,
            priority,
            description,
            status: 'todo',
            progress: 0,
            notes: [],
            history: [{ time: new Date().toLocaleString('vi-VN'), type: 'create', desc: 'Thêm hoạt động qua Lịch Canh Tác' }]
        };

        activities.push(newAct);
        saveActivities(activities);
        eventModal.classList.remove('active');
        renderAll();

        if (window.addFarmNotification) {
            window.addFarmNotification(
                `LỊCH MỚI: ${title}`,
                `Hoạt động "${category}" đã được lên lịch cho ${assignee} tại ${sector}. Hạn: ${dueDate}`,
                'info'
            );
        } else if (window.showToast) {
            window.showToast('Lên lịch thành công', `Đã thêm hoạt động vào lịch canh tác: ${title}`, 'success');
        }
    });

    // Export Schedule Action (CSV with UTF-8 BOM)
    btnExportSchedule.addEventListener('click', () => {
        let csvContent = "\uFEFFMã việc,Tiêu đề công việc,Mùa vụ,Loại hoạt động,Người phụ trách,Lô thửa đất,Ngày bắt đầu,Hạn chót,Tiến độ (%),Trạng thái,Quá hạn\n";

        activities.forEach(t => {
            const overdue = isTaskOverdue(t) ? 'Có (Quá hạn)' : 'Không';
            csvContent += `"${t.id}","${t.title}","${t.season}","${t.category}","${t.assignee}","${t.sector}","${t.startDate}","${t.dueDate}","${t.progress}%","${t.status}","${overdue}"\n`;
        });

        const blob = new Blob([csvContent], { type: 'text/csv;charset=utf-8;' });
        const link = document.createElement("a");
        const url = URL.createObjectURL(blob);
        link.setAttribute("href", url);
        link.setAttribute("download", `Lich_Canh_Tac_AgriManage_${TODAY_STR}.csv`);
        document.body.appendChild(link);
        link.click();
        document.body.removeChild(link);

        if (window.showToast) {
            window.showToast('Xuất tệp thành công', 'Đã tải xuống bảng dữ liệu Lịch Canh Tác (CSV).', 'success');
        }
    });

    // Close Modals on click outside or Escape
    [taskDetailModal, eventModal].forEach(modal => {
        if (!modal) return;
        modal.addEventListener('click', (e) => {
            if (e.target === modal) modal.classList.remove('active');
        });
    });

    document.addEventListener('keydown', (e) => {
        if (e.key === 'Escape') {
            if (taskDetailModal) taskDetailModal.classList.remove('active');
            if (eventModal) eventModal.classList.remove('active');
        }
    });

    // Initial render
    renderAll();
});
