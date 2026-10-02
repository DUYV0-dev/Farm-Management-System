document.addEventListener('DOMContentLoaded', () => {
    let workers = [
        {
            id: 'W1',
            name: 'John Doe',
            role: 'Harvester',
            sector: 'Greenhouse A',
            phone: '+1 555-0143',
            email: 'john.doe@agrimanage.com',
            status: 'Active'
        },
        {
            id: 'W2',
            name: 'Jane Smith',
            role: 'Agronomist',
            sector: 'Orchard B & Sector 3',
            phone: '+1 555-0188',
            email: 'jane.smith@agrimanage.com',
            status: 'Active'
        },
        {
            id: 'W3',
            name: 'Mike Johnson',
            role: 'Technician',
            sector: 'Irrigation & Machinery',
            phone: '+1 555-0129',
            email: 'mike.j@agrimanage.com',
            status: 'Active'
        },
        {
            id: 'W4',
            name: 'Sarah Connor',
            role: 'Supervisor',
            sector: 'All Sectors',
            phone: '+1 555-0199',
            email: 'sarah.c@agrimanage.com',
            status: 'On Leave'
        },
        {
            id: 'W5',
            name: 'David Miller',
            role: 'Driver',
            sector: 'Logistics & Transport',
            phone: '+1 555-0177',
            email: 'david.m@agrimanage.com',
            status: 'Active'
        },
        {
            id: 'W6',
            name: 'Emily Davis',
            role: 'Harvester',
            sector: 'Berry Field 2',
            phone: '+1 555-0112',
            email: 'emily.d@agrimanage.com',
            status: 'Active'
        }
    ];

    const workerTableBody = document.getElementById('worker-table-body');
    const workerModal = document.getElementById('workerModal');
    const workerForm = document.getElementById('workerForm');
    const workerSearch = document.getElementById('workerSearch');
    const roleFilter = document.getElementById('roleFilter');

    const statTotal = document.getElementById('stat-total-workers');
    const statActive = document.getElementById('stat-active-workers');
    const statLeave = document.getElementById('stat-leave-workers');
    const statTech = document.getElementById('stat-tech-workers');

    let currentSearch = '';
    let currentRole = '';

    document.getElementById('btnAddWorker').addEventListener('click', () => {
        workerForm.reset();
        document.getElementById('workerId').value = '';
        document.getElementById('workerModalTitle').textContent = 'Add New Worker';
        workerModal.classList.add('active');
    });

    document.getElementById('btnCloseWorkerModal').addEventListener('click', () => workerModal.classList.remove('active'));
    document.getElementById('btnCancelWorker').addEventListener('click', () => workerModal.classList.remove('active'));

    workerSearch.addEventListener('input', (e) => {
        currentSearch = e.target.value.toLowerCase();
        renderWorkers();
    });

    roleFilter.addEventListener('change', (e) => {
        currentRole = e.target.value;
        renderWorkers();
    });

    workerForm.addEventListener('submit', (e) => {
        e.preventDefault();
        const id = document.getElementById('workerId').value;
        const name = document.getElementById('workerName').value;
        const role = document.getElementById('workerRole').value;
        const sector = document.getElementById('workerSector').value;
        const phone = document.getElementById('workerPhone').value;
        const status = document.getElementById('workerStatus').value;
        const email = document.getElementById('workerEmail').value || `${name.toLowerCase().replace(' ', '.')}@agrimanage.com`;

        if (id) {
            const w = workers.find(item => item.id === id);
            w.name = name;
            w.role = role;
            w.sector = sector;
            w.phone = phone;
            w.status = status;
            w.email = email;
            if (window.addFarmNotification) {
                window.addFarmNotification('Worker Updated', `Updated details for ${name}.`, 'info');
            }
        } else {
            workers.push({
                id: 'W' + Date.now().toString().slice(-4),
                name, role, sector, phone, email, status
            });
            if (window.addFarmNotification) {
                window.addFarmNotification('New Worker Added', `Added ${name} (${role}) to ${sector}.`, 'success');
            }
        }

        workerModal.classList.remove('active');
        renderWorkers();
    });

    function getStatusClass(status) {
        if (status === 'Active') return 'active';
        if (status === 'On Leave') return 'on-leave';
        return 'inactive';
    }

    function renderWorkers() {
        workerTableBody.innerHTML = '';

        let activeCount = 0;
        let leaveCount = 0;
        let techCount = 0;

        workers.forEach(w => {
            if (w.status === 'Active') activeCount++;
            if (w.status === 'On Leave') leaveCount++;
            if (w.role === 'Technician' || w.role === 'Agronomist') techCount++;
        });

        const filtered = workers.filter(w => {
            const matchesSearch = w.name.toLowerCase().includes(currentSearch) ||
                                  w.role.toLowerCase().includes(currentSearch) ||
                                  w.sector.toLowerCase().includes(currentSearch);
            const matchesRole = currentRole === '' || w.role === currentRole;
            return matchesSearch && matchesRole;
        });

        filtered.forEach(w => {
            const tr = document.createElement('tr');
            tr.innerHTML = `
                <td>
                    <div class="assignee-cell">
                        <div class="worker-avatar-lg">${w.name.charAt(0)}</div>
                        <div>
                            <div style="font-weight: 600; color: var(--text-main);">${w.name}</div>
                            <div style="font-size: 13px; color: var(--text-muted);">${w.email}</div>
                        </div>
                    </div>
                </td>
                <td><span class="badge priority-low">${w.role}</span></td>
                <td><span style="font-weight: 500; color: var(--text-main);"><i class="fa-solid fa-location-dot" style="color: var(--primary); margin-right: 6px;"></i>${w.sector}</span></td>
                <td><span style="font-size: 14px; color: var(--text-muted);"><i class="fa-solid fa-phone" style="margin-right: 6px;"></i>${w.phone}</span></td>
                <td>
                    <span class="status-badge ${getStatusClass(w.status)}">
                        <i class="fa-solid fa-circle" style="font-size: 8px;"></i> ${w.status}
                    </span>
                </td>
                <td>
                    <div class="action-buttons">
                        <button class="btn-icon-sm" onclick="editWorker('${w.id}')" title="Edit Worker"><i class="fa-solid fa-pen"></i></button>
                        <button class="btn-icon-sm text-danger" onclick="deleteWorker('${w.id}')" title="Delete Worker"><i class="fa-solid fa-trash"></i></button>
                    </div>
                </td>
            `;
            workerTableBody.appendChild(tr);
        });

        statTotal.textContent = workers.length;
        statActive.textContent = activeCount;
        statLeave.textContent = leaveCount;
        statTech.textContent = techCount;
    }

    window.editWorker = (id) => {
        const w = workers.find(item => item.id === id);
        document.getElementById('workerId').value = w.id;
        document.getElementById('workerName').value = w.name;
        document.getElementById('workerRole').value = w.role;
        document.getElementById('workerSector').value = w.sector;
        document.getElementById('workerPhone').value = w.phone;
        document.getElementById('workerStatus').value = w.status;
        document.getElementById('workerEmail').value = w.email;

        document.getElementById('workerModalTitle').textContent = 'Edit Worker Details';
        workerModal.classList.add('active');
    };

    window.deleteWorker = (id) => {
        if (confirm('Are you sure you want to remove this worker from the roster?')) {
            workers = workers.filter(w => w.id !== id);
            renderWorkers();
        }
    };

    renderWorkers();
});
