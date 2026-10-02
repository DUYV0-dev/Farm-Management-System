document.addEventListener('DOMContentLoaded', () => {
    let events = [
        { id: 1, title: 'Tomato Harvest', category: 'harvest', date: '2026-10-02', sector: 'Greenhouse A' },
        { id: 2, title: 'Irrigation Check', category: 'maintenance', date: '2026-10-04', sector: 'Sector 4' },
        { id: 3, title: 'Soil Fertilizer App', category: 'planting', date: '2026-10-07', sector: 'Corn Field 1' },
        { id: 4, title: 'Weekly Agronomy Audit', category: 'inspection', date: '2026-10-09', sector: 'All Fields' },
        { id: 5, title: 'Wheat Seed Sowing', category: 'planting', date: '2026-10-14', sector: 'Field B' },
        { id: 6, title: 'Apple Picking Peak', category: 'harvest', date: '2026-10-18', sector: 'Orchard B' },
        { id: 7, title: 'Tractor Service', category: 'maintenance', date: '2026-10-22', sector: 'Equipment Shed' },
        { id: 8, title: 'Pest Control Scan', category: 'inspection', date: '2026-10-27', sector: 'Berry Patch' }
    ];

    let currentDate = new Date(2026, 9, 1); // October 2026
    const calendarGrid = document.getElementById('calendarGrid');
    const monthYearText = document.getElementById('currentMonthYear');
    const eventModal = document.getElementById('eventModal');
    const eventForm = document.getElementById('eventForm');
    const eventCategoryFilter = document.getElementById('eventCategoryFilter');

    document.getElementById('btnPrevMonth').addEventListener('click', () => {
        currentDate.setMonth(currentDate.getMonth() - 1);
        renderCalendar();
    });

    document.getElementById('btnNextMonth').addEventListener('click', () => {
        currentDate.setMonth(currentDate.getMonth() + 1);
        renderCalendar();
    });

    document.getElementById('btnToday').addEventListener('click', () => {
        currentDate = new Date(2026, 9, 1); // Set to Oct 2026 baseline
        renderCalendar();
    });

    document.getElementById('btnAddSchedule').addEventListener('click', () => {
        eventForm.reset();
        document.getElementById('eventDate').value = '2026-10-15';
        eventModal.classList.add('active');
    });

    document.getElementById('btnCloseEventModal').addEventListener('click', () => eventModal.classList.remove('active'));
    document.getElementById('btnCancelEvent').addEventListener('click', () => eventModal.classList.remove('active'));

    eventCategoryFilter.addEventListener('change', () => {
        renderCalendar();
    });

    eventForm.addEventListener('submit', (e) => {
        e.preventDefault();
        const title = document.getElementById('eventTitle').value;
        const category = document.getElementById('eventCategory').value;
        const date = document.getElementById('eventDate').value;
        const sector = document.getElementById('eventSector').value;

        events.push({
            id: Date.now(),
            title, category, date, sector
        });

        eventModal.classList.remove('active');
        renderCalendar();
    });

    function getEventClass(cat) {
        if (cat === 'harvest') return 'event-harvest';
        if (cat === 'maintenance') return 'event-maintenance';
        if (cat === 'planting') return 'event-planting';
        return 'event-inspection';
    }

    function renderCalendar() {
        const year = currentDate.getFullYear();
        const month = currentDate.getMonth();

        const monthNames = ["January", "February", "March", "April", "May", "June", 
                            "July", "August", "September", "October", "November", "December"];
        monthYearText.textContent = `${monthNames[month]} ${year}`;

        // Keep day headers
        const dayHeaders = Array.from(calendarGrid.querySelectorAll('.calendar-day-header'));
        calendarGrid.innerHTML = '';
        dayHeaders.forEach(dh => calendarGrid.appendChild(dh));

        const firstDayOfMonth = new Date(year, month, 1).getDay(); // 0 is Sunday
        const startingIndex = firstDayOfMonth === 0 ? 6 : firstDayOfMonth - 1; // Mon=0, Sun=6
        const totalDaysInMonth = new Date(year, month + 1, 0).getDate();
        const prevMonthLastDate = new Date(year, month, 0).getDate();

        const selectedCategory = eventCategoryFilter.value;

        // Render Previous Month Padding Days
        for (let i = startingIndex - 1; i >= 0; i--) {
            const cell = document.createElement('div');
            cell.className = 'calendar-day-cell other-month';
            cell.innerHTML = `<span class="day-number">${prevMonthLastDate - i}</span>`;
            calendarGrid.appendChild(cell);
        }

        // Render Current Month Days
        const todayStr = '2026-10-02';
        for (let day = 1; day <= totalDaysInMonth; day++) {
            const cell = document.createElement('div');
            const dayFormatted = day < 10 ? `0${day}` : `${day}`;
            const monthFormatted = (month + 1) < 10 ? `0${month + 1}` : `${month + 1}`;
            const dateStr = `${year}-${monthFormatted}-${dayFormatted}`;

            cell.className = `calendar-day-cell ${dateStr === todayStr ? 'today' : ''}`;
            cell.innerHTML = `<span class="day-number">${day}</span>`;

            // Filter matching events
            const dayEvents = events.filter(e => {
                const categoryMatch = !selectedCategory || e.category === selectedCategory;
                return e.date === dateStr && categoryMatch;
            });

            dayEvents.forEach(e => {
                const eventPill = document.createElement('div');
                eventPill.className = `calendar-event ${getEventClass(e.category)}`;
                eventPill.title = `${e.title} (${e.sector})`;
                eventPill.innerHTML = `<i class="fa-solid fa-circle" style="font-size:6px; margin-right:4px;"></i>${e.title}`;
                eventPill.addEventListener('click', (ev) => {
                    ev.stopPropagation();
                    alert(`Event: ${e.title}\nCategory: ${e.category.toUpperCase()}\nLocation: ${e.sector}\nDate: ${e.date}`);
                });
                cell.appendChild(eventPill);
            });

            calendarGrid.appendChild(cell);
        }

        // Render Next Month Padding Days
        const totalCells = startingIndex + totalDaysInMonth;
        const nextPadding = totalCells % 7 === 0 ? 0 : 7 - (totalCells % 7);
        for (let j = 1; j <= nextPadding; j++) {
            const cell = document.createElement('div');
            cell.className = 'calendar-day-cell other-month';
            cell.innerHTML = `<span class="day-number">${j}</span>`;
            calendarGrid.appendChild(cell);
        }
    }

    renderCalendar();
});
