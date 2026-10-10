/**
 * AgriManage - Farming Activity & Progress Tracking Module JS
 * Handles season progress tracking, activity CRUD, status updates, completion recording, field notes, and audit history.
 */

document.addEventListener('DOMContentLoaded', () => {
    // 1. Initial Seed Data across Crop Seasons
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
            description: 'Bón thúc đợt 1 phân NPK 20-20-15 tạo đà đẻ nhánh khỏe cho lúa vụ Đông Xuân.',
            completionRecord: {
                actualDate: '2026-10-03 16:30',
                outputQty: 250,
                outputUnit: 'kg phân NPK',
                inspector: 'Trần Văn Lượng (Quản lý vườn)',
                quality: 'Xuất sắc (5★)',
                notes: 'Đã bón phân đồng đều trên diện tích 2.5 ha, lúa sinh trưởng rất tốt.'
            },
            notes: [
                {
                    id: 'N-101',
                    date: '2026-10-01 07:30',
                    category: 'Theo dõi thời tiết & Độ ẩm',
                    author: 'Nguyễn Văn Hùng',
                    content: 'Nhiệt độ 26°C, độ ẩm đất 78%, thời tiết râm mát rất phù hợp rải phân.',
                    materials: []
                },
                {
                    id: 'N-102',
                    date: '2026-10-02 09:15',
                    category: 'Sử dụng Vật tư & Thiết bị',
                    author: 'Nguyễn Văn Hùng',
                    content: 'Đã xuất kho 250kg phân NPK 20-20-15 Hải Phong và súng rải phân vôi cơ giới.',
                    materials: [{ name: 'Phân NPK 20-20-15', qty: 250, unit: 'kg' }]
                }
            ],
            history: [
                { time: '2026-10-01 07:00', type: 'create', desc: 'Khởi tạo hoạt động canh tác cho Mùa Đông Xuân 2026' },
                { time: '2026-10-01 07:30', type: 'note', desc: 'Đã ghi nhật ký theo dõi thời tiết & độ ẩm' },
                { time: '2026-10-02 14:00', type: 'progress', desc: 'Cập nhật tiến độ từ 0% ➔ 60%' },
                { time: '2026-10-03 16:30', type: 'completion', desc: 'Ghi nhận HOÀN THÀNH 100% bởi Trần Văn Lượng. Số lượng thực hiện: 250 kg phân NPK' }
            ]
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
            dueDate: '2026-10-07',
            status: 'in_progress',
            progress: 65,
            description: 'Sử dụng chế phẩm sinh học BT phun phòng ngừa sâu cuốn lá giai đoạn phát triển mầm.',
            completionRecord: null,
            notes: [
                {
                    id: 'N-103',
                    date: '2026-10-05 08:45',
                    category: 'Sức khỏe cây & Sâu bệnh',
                    author: 'Lê Thị Mai',
                    content: 'Phát hiện ổ bướm đêm mật độ thấp ở góc nhà màng A2, tiến hành phun chế phẩm sinh học BT.',
                    materials: [{ name: 'Chế phẩm sinh học BT', qty: 15, unit: 'lít' }]
                }
            ],
            history: [
                { time: '2026-10-04 08:00', type: 'create', desc: 'Khởi tạo hoạt động phun thuốc phòng trừ' },
                { time: '2026-10-05 08:45', type: 'progress', desc: 'Chuyển trạng thái sang Đang thực hiện. Tiến độ: 65%' }
            ]
        },
        {
            id: 'ACT-103',
            title: 'Thu hoạch Cà chua sô cô la vụ Hè Thu - Greenhouse B',
            season: 'Mùa Hè Thu 2026',
            category: 'Thu hoạch',
            assignee: 'Phạm Thanh Sơn',
            sector: 'Greenhouse B',
            priority: 'High',
            startDate: '2026-09-20',
            dueDate: '2026-09-25',
            status: 'completed',
            progress: 100,
            description: 'Hái cà chua sô cô la đạt chín 85%, đóng thùng và dán tem xuất kho.',
            completionRecord: {
                actualDate: '2026-09-24 17:00',
                outputQty: 1850,
                outputUnit: 'kg cà chua',
                inspector: 'Hồng Thái Vinh (BA/PO)',
                quality: 'Xuất sắc (5★)',
                notes: 'Thu hoạch vượt chỉ tiêu 150kg, màu sắc đẹp, không bị sâu bệnh hay dập nát.'
            },
            notes: [
                {
                    id: 'N-104',
                    date: '2026-09-22 14:00',
                    category: 'Nhật ký đồng ruộng',
                    author: 'Phạm Thanh Sơn',
                    content: 'Đợt hái thứ nhất đạt 900kg, đóng được 45 thùng chuẩn 20kg.',
                    materials: []
                }
            ],
            history: [
                { time: '2026-09-20 07:00', type: 'create', desc: 'Khởi tạo hoạt động thu hoạch cà chua' },
                { time: '2026-09-24 17:00', type: 'completion', desc: 'Hoàn thành thu hoạch 1,850 kg cà chua sạch' }
            ]
        },
        {
            id: 'ACT-104',
            title: 'Làm đất & Cày xới chuẩn bị gieo ngô Mùa Thu Đông',
            season: 'Mùa Thu Đông 2026',
            category: 'Làm đất',
            assignee: 'Trần Văn Lượng',
            sector: 'Lô đất D3 - Phía Nam',
            priority: 'Medium',
            startDate: '2026-10-08',
            dueDate: '2026-10-12',
            status: 'todo',
            progress: 0,
            description: 'Cày sâu 25cm, lên luống cao 30cm và rải vôi bột xử lý nấm bệnh trong đất.',
            completionRecord: null,
            notes: [],
            history: [
                { time: '2026-10-05 10:00', type: 'create', desc: 'Khởi tạo kế hoạch làm đất cho mùa vụ Thu Đông 2026' }
            ]
        },
        {
            id: 'ACT-105',
            title: 'Bảo trì hệ thống tưới nhỏ giọt & Bộ lọc trung tâm',
            season: 'Mùa Đông Xuân 2026',
            category: 'Kiểm tra & Bảo trì',
            assignee: 'Võ Hà Duy',
            sector: 'Tram Control Unit A',
            priority: 'Medium',
            startDate: '2026-10-05',
            dueDate: '2026-10-06',
            status: 'in_progress',
            progress: 40,
            description: 'Súc rửa lọc đĩa 2 inch, thay 4 van xả cặn đường ống chính Sector 2.',
            completionRecord: null,
            notes: [
                {
                    id: 'N-105',
                    date: '2026-10-05 15:30',
                    category: 'Sử dụng Vật tư & Thiết bị',
                    author: 'Võ Hà Duy',
                    content: 'Thay mới 4 van xả cặn tự động và vệ sinh lõi lọc đĩa 120 mesh.',
                    materials: [{ name: 'Van xả cặn 34mm', qty: 4, unit: 'cái' }]
                }
            ],
            history: [
                { time: '2026-10-05 09:00', type: 'create', desc: 'Khởi tạo lịch bảo trì hệ thống tưới' },
                { time: '2026-10-05 15:30', type: 'progress', desc: 'Cập nhật tiến độ 40%' }
            ]
        }
    ];

    // Load or initialize localStorage
    function getActivities() {
        const stored = localStorage.getItem('agri_activities');
        if (stored) {
            try { return JSON.parse(stored); } catch (e) { }
        }
        return DEFAULT_ACTIVITIES;
    }

    function saveActivities(actList) {
        localStorage.setItem('agri_activities', JSON.stringify(actList));
    }

    let activities = getActivities();

    // Current State Filters
    let selectedSeason = 'Mùa Đông Xuân 2026';
    let searchQuery = '';
    let selectedCategory = '';
    let selectedStatus = '';
    let activeView = 'table'; // 'table' or 'kanban'

    // DOM Elements
    const activityTableBody = document.getElementById('activity-table-body');
    const tableViewContainer = document.getElementById('tableViewContainer');
    const kanbanViewContainer = document.getElementById('kanbanViewContainer');
    const btnViewTable = document.getElementById('btnViewTable');
    const btnViewKanban = document.getElementById('btnViewKanban');

    // Season Stats Banner Elements
    const seasonProgressPercent = document.getElementById('seasonProgressPercent');
    const seasonProgressBar = document.getElementById('seasonProgressBar');
    const seasonTotalCount = document.getElementById('seasonTotalCount');
    const seasonInProgressCount = document.getElementById('seasonInProgressCount');
    const seasonCompletedCount = document.getElementById('seasonCompletedCount');
    const currentSeasonTitle = document.getElementById('currentSeasonTitle');
    const currentSeasonSubtitle = document.getElementById('currentSeasonSubtitle');

    // Search and Filter Elements
    const searchInput = document.getElementById('searchInput');
    const categoryFilter = document.getElementById('categoryFilter');
    const statusFilter = document.getElementById('statusFilter');
    const seasonTabSelector = document.getElementById('seasonTabSelector');

    // Modals
    const activityModal = document.getElementById('activityModal');
    const progressModal = document.getElementById('progressModal');
    const completionModal = document.getElementById('completionModal');
    const notesModal = document.getElementById('notesModal');
    const historyModal = document.getElementById('historyModal');

    // 2. View Mode Switcher
    btnViewTable.addEventListener('click', () => {
        activeView = 'table';
        btnViewTable.classList.add('active');
        btnViewKanban.classList.remove('active');
        tableViewContainer.style.display = 'block';
        kanbanViewContainer.style.display = 'none';
        render();
    });

    btnViewKanban.addEventListener('click', () => {
        activeView = 'kanban';
        btnViewKanban.classList.add('active');
        btnViewTable.classList.remove('active');
        tableViewContainer.style.display = 'none';
        kanbanViewContainer.style.display = 'grid';
        render();
    });

    // 3. Season Tabs Handler
    seasonTabSelector.addEventListener('click', (e) => {
        const btn = e.target.closest('.season-tab-btn');
        if (!btn) return;

        document.querySelectorAll('.season-tab-btn').forEach(b => b.classList.remove('active'));
        btn.classList.add('active');
        selectedSeason = btn.getAttribute('data-season');
        render();
    });

    // 4. Filters Handler
    searchInput.addEventListener('input', (e) => {
        searchQuery = e.target.value.toLowerCase().trim();
        render();
    });

    categoryFilter.addEventListener('change', (e) => {
        selectedCategory = e.target.value;
        render();
    });

    statusFilter.addEventListener('change', (e) => {
        selectedStatus = e.target.value;
        render();
    });

    // Helper: Category Badge Class
    function getCategoryBadgeClass(cat) {
        switch (cat) {
            case 'Làm đất': return 'cat-lamdat';
            case 'Gieo trồng': return 'cat-gieotrong';
            case 'Bón phân': return 'cat-bonphan';
            case 'Tưới nước': return 'cat-tuoinuoc';
            case 'Phun thuốc': return 'cat-phunthuoc';
            case 'Thu hoạch': return 'cat-thuhoach';
            default: return 'cat-baotri';
        }
    }

    // Helper: Season Badge Class
    function getSeasonBadgeClass(season) {
        if (season.includes('Đông Xuân')) return 'season-dongxuan';
        if (season.includes('Hè Thu')) return 'season-hethu';
        return 'season-thudong';
    }

    // Helper: Progress Fill Color
    function getProgressColor(progress) {
        if (progress < 30) return '#f59e0b';
        if (progress < 75) return '#0284c7';
        return '#10b981';
    }

    // Helper: Format Date
    function formatDateStr(dateStr) {
        if (!dateStr) return 'N/A';
        const d = new Date(dateStr);
        if (isNaN(d.getTime())) return dateStr;
        return d.toLocaleDateString('vi-VN', { day: '2-digit', month: '2-digit', year: 'numeric' });
    }

    // 5. Calculate & Render Season Statistics
    function updateSeasonBannerStats() {
        let seasonActivities = activities;
        if (selectedSeason !== 'ALL') {
            seasonActivities = activities.filter(a => a.season === selectedSeason);
            currentSeasonTitle.textContent = selectedSeason;
            if (selectedSeason === 'Mùa Đông Xuân 2026') {
                currentSeasonSubtitle.textContent = 'Thời gian: 01/11/2025 - 30/04/2026 | Cây trồng chủ lực: Lúa chất lượng cao & Cà chua màng';
            } else if (selectedSeason === 'Mùa Hè Thu 2026') {
                currentSeasonSubtitle.textContent = 'Thời gian: 01/05/2026 - 31/08/2026 | Cây trồng chủ lực: Dưa lưới, Ngô sinh khối & Ớt ngọt';
            } else {
                currentSeasonSubtitle.textContent = 'Thời gian: 01/09/2026 - 31/12/2026 | Cây trồng chủ lực: Rau màu ngắn ngày & Khoai tây';
            }
        } else {
            currentSeasonTitle.textContent = 'Tất cả Mùa vụ Canh tác';
            currentSeasonSubtitle.textContent = 'Tổng hợp thống kê tiến độ trên toàn bộ các mùa vụ trang trại';
        }

        const total = seasonActivities.length;
        const completed = seasonActivities.filter(a => a.status === 'completed').length;
        const inProgress = seasonActivities.filter(a => a.status === 'in_progress').length;

        // Weighted Progress percentage
        let avgProgress = 0;
        if (total > 0) {
            const sumProgress = seasonActivities.reduce((acc, curr) => acc + (curr.progress || 0), 0);
            avgProgress = Math.round(sumProgress / total);
        }

        seasonTotalCount.textContent = total;
        seasonInProgressCount.textContent = inProgress;
        seasonCompletedCount.textContent = completed;
        seasonProgressPercent.textContent = `${avgProgress}%`;
        seasonProgressBar.style.width = `${avgProgress}%`;
    }

    // 6. Main Filtered Activities Retrieval
    function getFilteredActivities() {
        return activities.filter(act => {
            // Season filter
            if (selectedSeason !== 'ALL' && act.season !== selectedSeason) return false;

            // Category filter
            if (selectedCategory && act.category !== selectedCategory) return false;

            // Status filter
            if (selectedStatus && act.status !== selectedStatus) return false;

            // Search query
            if (searchQuery) {
                const matchTitle = act.title.toLowerCase().includes(searchQuery);
                const matchAssignee = act.assignee.toLowerCase().includes(searchQuery);
                const matchSector = act.sector.toLowerCase().includes(searchQuery);
                const matchDesc = (act.description || '').toLowerCase().includes(searchQuery);
                if (!matchTitle && !matchAssignee && !matchSector && !matchDesc) return false;
            }

            return true;
        });
    }

    // 7. Render Table View & Kanban View
    function render() {
        updateSeasonBannerStats();
        const filteredList = getFilteredActivities();

        if (activeView === 'table') {
            renderTable(filteredList);
        } else {
            renderKanban(filteredList);
        }
    }

    function renderTable(list) {
        activityTableBody.innerHTML = '';

        if (list.length === 0) {
            activityTableBody.innerHTML = `
                <tr>
                    <td colspan="7" style="text-align:center; padding: 40px; color: var(--text-muted);">
                        <i class="fa-solid fa-folder-open" style="font-size:36px; margin-bottom:12px; color: var(--text-light); display:block;"></i>
                        <strong>Không tìm thấy hoạt động canh tác phù hợp.</strong>
                        <p style="font-size:13px; margin-top:4px;">Thử thay đổi bộ lọc hoặc bấm "Thêm Hoạt động Canh tác" để tạo mới.</p>
                    </td>
                </tr>
            `;
            return;
        }

        list.forEach(act => {
            const tr = document.createElement('tr');

            // Material pills preview
            let materialsHTML = '';
            if (act.notes && act.notes.length > 0) {
                act.notes.forEach(n => {
                    if (n.materials && n.materials.length > 0) {
                        n.materials.forEach(m => {
                            materialsHTML += `<span class="material-pill"><i class="fa-solid fa-box"></i> ${m.name}: ${m.qty} ${m.unit}</span>`;
                        });
                    }
                });
            }

            // Completion tag preview
            let completionHTML = '';
            if (act.status === 'completed' && act.completionRecord) {
                completionHTML = `
                    <div class="completion-tag">
                        <i class="fa-solid fa-circle-check"></i> Đã thu/ghi nhận: ${act.completionRecord.outputQty} ${act.completionRecord.outputUnit} (${act.completionRecord.quality || 'Đạt'})
                    </div>
                `;
            }

            tr.innerHTML = `
                <td>
                    <div class="task-title-cell">
                        <span class="crop-season-badge ${getSeasonBadgeClass(act.season)}">${act.season}</span>
                        <strong style="font-size:14.5px; margin-top:4px; display:block;">${act.title}</strong>
                        <span style="font-size:12.5px; color:var(--text-muted);">${act.description || 'Không có mô tả'}</span>
                        ${completionHTML}
                        ${materialsHTML ? `<div class="material-pills-wrap">${materialsHTML}</div>` : ''}
                    </div>
                </td>
                <td>
                    <span class="activity-cat-badge ${getCategoryBadgeClass(act.category)}">
                        <i class="fa-solid fa-seedling"></i> ${act.category}
                    </span>
                </td>
                <td>
                    <div class="assignee-cell">
                        <div class="avatar" style="background:var(--primary-gradient); color:#fff; font-weight:700;">${act.assignee.charAt(0)}</div>
                        <div>
                            <strong style="display:block; font-size:13.5px;">${act.assignee}</strong>
                            <span style="font-size:12px; color:var(--text-muted);"><i class="fa-solid fa-location-dot"></i> ${act.sector}</span>
                        </div>
                    </div>
                </td>
                <td>
                    <div class="schedule-cell" style="font-size:12.5px;">
                        <span><i class="fa-regular fa-calendar-check" style="color:var(--primary);"></i> Bắt đầu: ${formatDateStr(act.startDate)}</span>
                        <span><i class="fa-solid fa-flag-checkered" style="color:var(--accent-brown);"></i> Hạn: ${formatDateStr(act.dueDate)}</span>
                    </div>
                </td>
                <td>
                    <div class="progress-container" style="min-width:130px;">
                        <div class="progress-info">
                            <strong style="font-size:13px; color:var(--text-main);">${act.progress || 0}%</strong>
                        </div>
                        <div class="progress-bar">
                            <div class="progress-fill" style="width: ${act.progress || 0}%; background: ${getProgressColor(act.progress || 0)}"></div>
                        </div>
                    </div>
                </td>
                <td>
                    <select class="status-select status-${act.status}" onchange="updateActivityStatusInline('${act.id}', this.value)">
                        <option value="todo" ${act.status === 'todo' ? 'selected' : ''}>Chưa bắt đầu</option>
                        <option value="in_progress" ${act.status === 'in_progress' ? 'selected' : ''}>Đang thực hiện</option>
                        <option value="on_hold" ${act.status === 'on_hold' ? 'selected' : ''}>Tạm dừng</option>
                        <option value="completed" ${act.status === 'completed' ? 'selected' : ''}>Hoàn thành</option>
                    </select>
                </td>
                <td style="text-align:center;">
                    <div class="action-buttons" style="justify-content:center;">
                        <button class="btn-icon-sm" onclick="openProgressModal('${act.id}')" title="Theo dõi & Cập nhật Tiến độ %" style="background:#eff6ff; color:#1d4ed8;"><i class="fa-solid fa-sliders"></i></button>
                        <button class="btn-icon-sm" onclick="openCompletionModal('${act.id}')" title="Ghi nhận Hoàn thành Hoạt động" style="background:#dcfce7; color:#15803d;"><i class="fa-solid fa-circle-check"></i></button>
                        <button class="btn-icon-sm" onclick="openNotesModal('${act.id}')" title="Ghi chú & Nhật ký Đồng ruộng" style="background:#fefce8; color:#b45309;"><i class="fa-solid fa-book-open"></i></button>
                        <button class="btn-icon-sm" onclick="openHistoryModal('${act.id}')" title="Xem Lịch sử Thay đổi" style="background:#f5f3ff; color:#7c3aed;"><i class="fa-solid fa-clock-rotate-left"></i></button>
                        <button class="btn-icon-sm" onclick="editActivity('${act.id}')" title="Chỉnh sửa"><i class="fa-solid fa-pen"></i></button>
                        <button class="btn-icon-sm text-danger" onclick="deleteActivity('${act.id}')" title="Xóa"><i class="fa-solid fa-trash"></i></button>
                    </div>
                </td>
            `;
            activityTableBody.appendChild(tr);
        });
    }

    function renderKanban(list) {
        kanbanViewContainer.innerHTML = '';

        const cols = [
            { id: 'todo', title: 'Chưa bắt đầu', icon: 'fa-hourglass-start', color: '#64748b' },
            { id: 'in_progress', title: 'Đang thực hiện', icon: 'fa-spinner fa-spin', color: '#0284c7' },
            { id: 'on_hold', title: 'Tạm dừng', icon: 'fa-circle-pause', color: '#f59e0b' },
            { id: 'completed', title: 'Hoàn thành', icon: 'fa-circle-check', color: '#10b981' }
        ];

        cols.forEach(col => {
            const colItems = list.filter(a => a.status === col.id);

            const colDiv = document.createElement('div');
            colDiv.className = 'kanban-col';
            colDiv.innerHTML = `
                <div class="kanban-col-header">
                    <div class="kanban-col-title" style="color: ${col.color}">
                        <i class="fa-solid ${col.icon}"></i> ${col.title}
                    </div>
                    <span class="badge" style="background:${col.color}; color:#fff; font-weight:700;">${colItems.length}</span>
                </div>
                <div class="kanban-cards-wrap" style="display:flex; flex-direction:column; gap:12px;">
                    ${colItems.length === 0 ? '<div style="font-size:13px; color:var(--text-muted); text-align:center; padding:20px 0;">Không có hoạt động</div>' : ''}
                </div>
            `;

            const cardsWrap = colDiv.querySelector('.kanban-cards-wrap');
            colItems.forEach(act => {
                const card = document.createElement('div');
                card.className = 'kanban-card';
                card.innerHTML = `
                    <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom:6px;">
                        <span class="crop-season-badge ${getSeasonBadgeClass(act.season)}">${act.season}</span>
                        <span class="activity-cat-badge ${getCategoryBadgeClass(act.category)}">${act.category}</span>
                    </div>
                    <div class="kanban-card-title">${act.title}</div>
                    <div style="font-size:12.5px; color:var(--text-muted); margin-bottom:8px;"><i class="fa-solid fa-location-dot"></i> ${act.sector}</div>
                    
                    <div class="progress-container">
                        <div class="progress-info">
                            <span style="font-size:12px; font-weight:700;">Tiến độ: ${act.progress || 0}%</span>
                        </div>
                        <div class="progress-bar" style="height:6px;">
                            <div class="progress-fill" style="width: ${act.progress || 0}%; background: ${getProgressColor(act.progress || 0)}"></div>
                        </div>
                    </div>

                    <div class="kanban-card-meta">
                        <span><i class="fa-solid fa-user"></i> ${act.assignee}</span>
                        <span><i class="fa-solid fa-flag"></i> ${formatDateStr(act.dueDate)}</span>
                    </div>

                    <div class="quick-action-btn-group">
                        <button class="btn-chip" onclick="openProgressModal('${act.id}')"><i class="fa-solid fa-sliders"></i> Tiến độ</button>
                        <button class="btn-chip chip-success" onclick="openCompletionModal('${act.id}')"><i class="fa-solid fa-check"></i> Nghiệm thu</button>
                        <button class="btn-chip" onclick="openNotesModal('${act.id}')"><i class="fa-solid fa-book-open"></i> Nhật ký</button>
                        <button class="btn-chip" onclick="openHistoryModal('${act.id}')"><i class="fa-solid fa-clock-rotate-left"></i> Lịch sử</button>
                    </div>
                `;
                cardsWrap.appendChild(card);
            });

            kanbanViewContainer.appendChild(colDiv);
        });
    }

    // 8. Global Handler: Inline Status Update
    window.updateActivityStatusInline = (id, newStatus) => {
        const act = activities.find(a => a.id === id);
        if (!act) return;

        const oldStatus = act.status;
        act.status = newStatus;

        // Auto adjust progress
        if (newStatus === 'completed') act.progress = 100;
        else if (newStatus === 'in_progress' && act.progress === 0) act.progress = 25;

        // Log history entry
        const statusMap = { todo: 'Chưa bắt đầu', in_progress: 'Đang thực hiện', on_hold: 'Tạm dừng', completed: 'Hoàn thành' };
        act.history.unshift({
            time: new Date().toLocaleString('vi-VN'),
            type: newStatus === 'completed' ? 'completion' : 'progress',
            desc: `Cập nhật trạng thái từ "${statusMap[oldStatus] || oldStatus}" ➔ "${statusMap[newStatus] || newStatus}"`
        });

        saveActivities(activities);
        render();

        if (window.showToast) {
            window.showToast('Cập nhật trạng thái', `Đã chuyển "${act.title}" sang trạng thái ${statusMap[newStatus]}`, 'success');
        }
    };

    // 9. Create / Edit Activity Modal Logic
    document.getElementById('btnCreateActivity').addEventListener('click', () => {
        document.getElementById('activityForm').reset();
        document.getElementById('activityId').value = '';
        document.getElementById('modalTitle').textContent = 'Thêm Hoạt động Canh tác Mới';
        document.getElementById('actSeason').value = selectedSeason !== 'ALL' ? selectedSeason : 'Mùa Đông Xuân 2026';
        activityModal.classList.add('active');
    });

    document.getElementById('btnCloseActivityModal').addEventListener('click', () => activityModal.classList.remove('active'));
    document.getElementById('btnCancelActivity').addEventListener('click', () => activityModal.classList.remove('active'));

    document.getElementById('activityForm').addEventListener('submit', (e) => {
        e.preventDefault();

        const id = document.getElementById('activityId').value;
        const title = document.getElementById('actTitle').value.trim();
        const season = document.getElementById('actSeason').value;
        const category = document.getElementById('actCategory').value;
        const assignee = document.getElementById('actAssignee').value;
        const sector = document.getElementById('actSector').value.trim();
        const startDate = document.getElementById('actStartDate').value;
        const dueDate = document.getElementById('actDueDate').value;
        const priority = document.getElementById('actPriority').value;
        const description = document.getElementById('actDescription').value.trim();

        if (id) {
            // Update
            const act = activities.find(a => a.id === id);
            act.title = title;
            act.season = season;
            act.category = category;
            act.assignee = assignee;
            act.sector = sector;
            act.startDate = startDate;
            act.dueDate = dueDate;
            act.priority = priority;
            act.description = description;

            act.history.unshift({
                time: new Date().toLocaleString('vi-VN'),
                type: 'create',
                desc: 'Cập nhật thông tin và yêu cầu kỹ thuật hoạt động'
            });

            if (window.showToast) window.showToast('Cập nhật thành công', `Đã lưu thay đổi cho hoạt động "${title}"`, 'success');
        } else {
            // Create New
            const newAct = {
                id: 'ACT-' + Date.now().toString().slice(-4),
                title, season, category, assignee, sector, startDate, dueDate, priority, description,
                status: 'todo',
                progress: 0,
                completionRecord: null,
                notes: [],
                history: [
                    { time: new Date().toLocaleString('vi-VN'), type: 'create', desc: `Tạo mới hoạt động cho ${season}` }
                ]
            };
            activities.unshift(newAct);

            if (window.showToast) window.showToast('Tạo thành công', `Đã thêm hoạt động mới: "${title}"`, 'success');
        }

        saveActivities(activities);
        activityModal.classList.remove('active');
        render();
    });

    window.editActivity = (id) => {
        const act = activities.find(a => a.id === id);
        if (!act) return;

        document.getElementById('activityId').value = act.id;
        document.getElementById('actTitle').value = act.title;
        document.getElementById('actSeason').value = act.season;
        document.getElementById('actCategory').value = act.category;
        document.getElementById('actAssignee').value = act.assignee;
        document.getElementById('actSector').value = act.sector;
        document.getElementById('actStartDate').value = act.startDate;
        document.getElementById('actDueDate').value = act.dueDate;
        document.getElementById('actPriority').value = act.priority || 'Medium';
        document.getElementById('actDescription').value = act.description || '';

        document.getElementById('modalTitle').textContent = 'Chỉnh sửa Hoạt động Canh tác';
        activityModal.classList.add('active');
    };

    window.deleteActivity = (id) => {
        const act = activities.find(a => a.id === id);
        if (!act) return;

        if (confirm(`Bạn có chắc chắn muốn xóa hoạt động "${act.title}"?`)) {
            activities = activities.filter(a => a.id !== id);
            saveActivities(activities);
            render();
            if (window.showToast) window.showToast('Đã xóa', `Đã xóa hoạt động "${act.title}"`, 'warning');
        }
    };

    // 10. Track & Update Progress Modal Logic
    const progressSlider = document.getElementById('progressSlider');
    const progressValueDisplay = document.getElementById('progressValueDisplay');

    progressSlider.addEventListener('input', (e) => {
        progressValueDisplay.textContent = `${e.target.value}%`;
    });

    window.setPresetProgress = (val) => {
        progressSlider.value = val;
        progressValueDisplay.textContent = `${val}%`;
    };

    window.openProgressModal = (id) => {
        const act = activities.find(a => a.id === id);
        if (!act) return;

        document.getElementById('progressActId').value = act.id;
        document.getElementById('progressActTitle').textContent = act.title;
        document.getElementById('progressActSeason').textContent = `${act.season} • ${act.sector}`;
        progressSlider.value = act.progress || 0;
        progressValueDisplay.textContent = `${act.progress || 0}%`;
        document.getElementById('progressNoteInput').value = '';

        progressModal.classList.add('active');
    };

    document.getElementById('btnCloseProgressModal').addEventListener('click', () => progressModal.classList.remove('active'));
    document.getElementById('btnCancelProgress').addEventListener('click', () => progressModal.classList.remove('active'));

    document.getElementById('btnSaveProgress').addEventListener('click', () => {
        const id = document.getElementById('progressActId').value;
        const act = activities.find(a => a.id === id);
        if (!act) return;

        const oldProgress = act.progress || 0;
        const newProgress = parseInt(progressSlider.value, 10);
        const note = document.getElementById('progressNoteInput').value.trim();

        act.progress = newProgress;

        // Auto transition status
        if (newProgress === 100) {
            act.status = 'completed';
        } else if (newProgress > 0 && act.status === 'todo') {
            act.status = 'in_progress';
        }

        // Add history log
        act.history.unshift({
            time: new Date().toLocaleString('vi-VN'),
            type: 'progress',
            desc: `Cập nhật tiến độ: ${oldProgress}% ➔ ${newProgress}%${note ? `. Ghi chú: ${note}` : ''}`
        });

        saveActivities(activities);
        progressModal.classList.remove('active');
        render();

        if (window.showToast) {
            window.showToast('Cập nhật Tiến độ', `Tiến độ "${act.title}" đã được ghi nhận: ${newProgress}%`, 'success');
        }
    });

    // 11. Record Completion Modal Logic
    window.openCompletionModal = (id) => {
        const act = activities.find(a => a.id === id);
        if (!act) return;

        document.getElementById('completionActId').value = act.id;
        document.getElementById('completionActTitle').textContent = act.title;
        document.getElementById('completionActSub').textContent = `${act.season} • ${act.sector} • Phụ trách: ${act.assignee}`;

        // Set default completion datetime-local format
        const now = new Date();
        now.setMinutes(now.getMinutes() - now.getTimezoneOffset());
        document.getElementById('completionDate').value = now.toISOString().slice(0, 16);

        document.getElementById('completionInspector').value = 'Trần Văn Lượng (Quản lý)';
        document.getElementById('completionOutputQty').value = act.completionRecord ? act.completionRecord.outputQty : '';
        document.getElementById('completionOutputUnit').value = act.completionRecord ? act.completionRecord.outputUnit : 'kg';
        document.getElementById('completionNotes').value = act.completionRecord ? act.completionRecord.notes : '';

        completionModal.classList.add('active');
    };

    document.getElementById('btnCloseCompletionModal').addEventListener('click', () => completionModal.classList.remove('active'));
    document.getElementById('btnCancelCompletion').addEventListener('click', () => completionModal.classList.remove('active'));

    document.getElementById('completionForm').addEventListener('submit', (e) => {
        e.preventDefault();

        const id = document.getElementById('completionActId').value;
        const act = activities.find(a => a.id === id);
        if (!act) return;

        const dateVal = document.getElementById('completionDate').value;
        const inspector = document.getElementById('completionInspector').value.trim();
        const outputQty = parseFloat(document.getElementById('completionOutputQty').value);
        const outputUnit = document.getElementById('completionOutputUnit').value.trim();
        const quality = document.getElementById('completionQuality').value;
        const notes = document.getElementById('completionNotes').value.trim();

        // Mark as completed & 100% progress
        act.status = 'completed';
        act.progress = 100;
        act.completionRecord = {
            actualDate: dateVal ? dateVal.replace('T', ' ') : new Date().toLocaleString('vi-VN'),
            outputQty,
            outputUnit,
            inspector,
            quality,
            notes
        };

        // Add history certificate entry
        act.history.unshift({
            time: new Date().toLocaleString('vi-VN'),
            type: 'completion',
            desc: `Ghi nhận HOÀN THÀNH (100%) bởi ${inspector}. Kết quả/Sản lượng: ${outputQty} ${outputUnit} - Đánh giá: ${quality}`
        });

        saveActivities(activities);
        completionModal.classList.remove('active');
        render();

        if (window.showToast) {
            window.showToast('Ghi nhận Hoàn thành', `Đã xác nhận hoàn thành hoạt động "${act.title}" (${outputQty} ${outputUnit})`, 'success');
        }
    });

    // 12. Record Activity Notes & Materials Modal Logic
    window.openNotesModal = (id) => {
        const act = activities.find(a => a.id === id);
        if (!act) return;

        document.getElementById('noteActId').value = act.id;
        document.getElementById('noteActTitle').textContent = `Nhật ký cho: ${act.title}`;
        document.getElementById('noteForm').reset();
        document.getElementById('noteAuthor').value = act.assignee;

        renderNotesList(act);
        notesModal.classList.add('active');
    };

    function renderNotesList(act) {
        const container = document.getElementById('notesListContainer');
        container.innerHTML = '';

        if (!act.notes || act.notes.length === 0) {
            container.innerHTML = `<div style="font-size:13px; color:var(--text-muted); text-align:center; padding:15px;">Chưa có ghi chú nhật ký nào.</div>`;
            return;
        }

        act.notes.forEach(note => {
            const card = document.createElement('div');
            card.className = 'note-item-card';

            let matsHTML = '';
            if (note.materials && note.materials.length > 0) {
                note.materials.forEach(m => {
                    matsHTML += `<span class="material-pill"><i class="fa-solid fa-flask"></i> ${m.name}: ${m.qty} ${m.unit}</span>`;
                });
            }

            card.innerHTML = `
                <div class="note-item-header">
                    <div class="note-author-group">
                        <i class="fa-solid fa-user-pen text-primary"></i> ${note.author}
                        <span class="badge" style="background:var(--primary-light); color:var(--primary-hover); font-size:11px;">${note.category}</span>
                    </div>
                    <span class="note-time">${note.date}</span>
                </div>
                <div class="note-text-content">${note.content}</div>
                ${matsHTML ? `<div class="material-pills-wrap">${matsHTML}</div>` : ''}
            `;
            container.appendChild(card);
        });
    }

    document.getElementById('btnCloseNotesModal').addEventListener('click', () => notesModal.classList.remove('active'));
    document.getElementById('btnCancelNote').addEventListener('click', () => notesModal.classList.remove('active'));

    document.getElementById('noteForm').addEventListener('submit', (e) => {
        e.preventDefault();

        const id = document.getElementById('noteActId').value;
        const act = activities.find(a => a.id === id);
        if (!act) return;

        const category = document.getElementById('noteCategory').value;
        const author = document.getElementById('noteAuthor').value.trim();
        const content = document.getElementById('noteContent').value.trim();

        const matName = document.getElementById('matName').value.trim();
        const matQty = parseFloat(document.getElementById('matQty').value);
        const matUnit = document.getElementById('matUnit').value.trim();

        const materials = [];
        if (matName && !isNaN(matQty)) {
            materials.push({ name: matName, qty: matQty, unit: matUnit || 'đơn vị' });
        }

        const newNote = {
            id: 'N-' + Date.now().toString().slice(-4),
            date: new Date().toLocaleString('vi-VN'),
            category,
            author,
            content,
            materials
        };

        if (!act.notes) act.notes = [];
        act.notes.unshift(newNote);

        // History log
        act.history.unshift({
            time: new Date().toLocaleString('vi-VN'),
            type: 'note',
            desc: `Thêm ghi chú nhật ký (${category}) bởi ${author}`
        });

        saveActivities(activities);
        renderNotesList(act);
        document.getElementById('noteForm').reset();
        document.getElementById('noteAuthor').value = author;
        render();

        if (window.showToast) window.showToast('Nhật ký đồng ruộng', 'Đã thêm ghi chú mới vào hoạt động', 'success');
    });

    // 13. History Audit Trail Timeline Modal
    window.openHistoryModal = (id) => {
        const act = activities.find(a => a.id === id);
        if (!act) return;

        document.getElementById('historyActTitle').textContent = act.title;
        document.getElementById('historyActSub').textContent = `${act.season} • Phụ trách: ${act.assignee} • Vị trí: ${act.sector}`;

        const timelineContainer = document.getElementById('historyTimelineContainer');
        timelineContainer.innerHTML = '';

        if (!act.history || act.history.length === 0) {
            timelineContainer.innerHTML = `<div style="font-size:13px; color:var(--text-muted); text-align:center; padding:20px;">Chưa có lịch sử thay đổi.</div>`;
        } else {
            act.history.forEach(evt => {
                const eventDiv = document.createElement('div');
                eventDiv.className = 'timeline-event';

                let markerClass = '';
                let iconClass = 'fa-pen';
                if (evt.type === 'completion') {
                    markerClass = 'marker-completion';
                    iconClass = 'fa-award';
                } else if (evt.type === 'progress') {
                    markerClass = 'marker-progress';
                    iconClass = 'fa-sliders';
                } else if (evt.type === 'note') {
                    markerClass = 'marker-note';
                    iconClass = 'fa-book-open';
                }

                eventDiv.innerHTML = `
                    <div class="timeline-event-marker ${markerClass}">
                        <i class="fa-solid ${iconClass}"></i>
                    </div>
                    <div class="timeline-event-box shadow-sm">
                        <div class="timeline-event-header">
                            <strong class="timeline-event-title">${evt.desc}</strong>
                            <span class="timeline-event-time"><i class="fa-regular fa-clock"></i> ${evt.time}</span>
                        </div>
                    </div>
                `;
                timelineContainer.appendChild(eventDiv);
            });
        }

        historyModal.classList.add('active');
    };

    document.getElementById('btnCloseHistoryModal').addEventListener('click', () => historyModal.classList.remove('active'));
    document.getElementById('btnCloseHistory').addEventListener('click', () => historyModal.classList.remove('active'));

    // Initial render
    render();
});
