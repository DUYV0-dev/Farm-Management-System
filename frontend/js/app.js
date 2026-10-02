document.addEventListener('DOMContentLoaded', () => {
    // Initial Data
    let tasks = [
        {
            id: 'T1',
            title: 'Prepare soil for Greenhouse A',
            description: 'Till the soil and apply organic compost before planting new tomato seeds.',
            assignee: 'John Doe',
            priority: 'High',
            startDate: '2026-09-29',
            dueDate: '2026-10-01',
            status: 'todo',
            progress: 0,
            history: [
                { time: new Date().toISOString(), desc: 'Task created by Farm Manager' }
            ]
        },
        {
            id: 'T2',
            title: 'Irrigation system maintenance',
            description: 'Check for leaks in sector 4 and replace broken sprinklers.',
            assignee: 'Mike Johnson',
            priority: 'Medium',
            startDate: '2026-09-28',
            dueDate: '2026-09-29',
            status: 'in_progress',
            progress: 50,
            history: [
                { time: new Date(Date.now() - 86400000).toISOString(), desc: 'Task created by Farm Manager' },
                { time: new Date().toISOString(), desc: 'Status changed to In Progress by Mike' }
            ]
        },
        {
            id: 'T3',
            title: 'Harvest apples in Orchard B',
            description: 'Pick all ripe apples. Grade them and pack into boxes carefully.',
            assignee: 'Jane Smith',
            priority: 'Critical',
            startDate: '2026-09-25',
            dueDate: '2026-09-27',
            status: 'completed',
            progress: 100,
            history: [
                { time: new Date(Date.now() - 2*86400000).toISOString(), desc: 'Task created' },
                { time: new Date(Date.now() - 86400000).toISOString(), desc: 'Task completed by Jane' }
            ]
        }
    ];

    // DOM Elements
    const tableBody = document.getElementById('task-table-body');
    const taskModal = document.getElementById('taskModal');
    const historyModal = document.getElementById('historyModal');
    const taskForm = document.getElementById('taskForm');
    
    // Stats Elements
    const statTotal = document.getElementById('stat-total');
    const statPending = document.getElementById('stat-pending');
    const statProgress = document.getElementById('stat-progress');
    const statCompleted = document.getElementById('stat-completed');
    


    // Search elements
    const searchInput = document.getElementById('searchInput');
    const btnFilter = document.getElementById('btnFilter');
    let currentFilter = '';

    // Modals control
    document.getElementById('btnCreateTask').addEventListener('click', () => {
        taskForm.reset();
        document.getElementById('taskId').value = '';
        document.getElementById('modalTitle').textContent = 'Create Farming Task';
        taskModal.classList.add('active');
    });

    document.getElementById('btnCloseModal').addEventListener('click', () => taskModal.classList.remove('active'));
    document.getElementById('btnCancelTask').addEventListener('click', () => taskModal.classList.remove('active'));
    document.getElementById('btnCloseHistory').addEventListener('click', () => historyModal.classList.remove('active'));

    // Search and Filter
    searchInput.addEventListener('input', (e) => {
        currentFilter = e.target.value.toLowerCase();
        renderTable();
    });

    btnFilter.addEventListener('click', () => {
        alert('Advanced filtering options will be available soon.');
    });

    // Form Submission
    taskForm.addEventListener('submit', (e) => {
        e.preventDefault();
        
        const id = document.getElementById('taskId').value;
        const title = document.getElementById('taskName').value;
        const assignee = document.getElementById('taskAssignee').value;
        const priority = document.getElementById('taskPriority').value;
        const startDate = document.getElementById('taskStartDate').value;
        const dueDate = document.getElementById('taskDueDate').value;
        const description = document.getElementById('taskDescription').value;

        if (id) {
            // Update
            const task = tasks.find(t => t.id === id);
            task.title = title;
            task.assignee = assignee;
            task.priority = priority;
            task.startDate = startDate;
            task.dueDate = dueDate;
            task.description = description;
            task.history.push({ time: new Date().toISOString(), desc: 'Task details updated' });
            if (window.addFarmNotification) {
                window.addFarmNotification('Task Updated', `Task "${title}" was updated.`, 'info');
            }
        } else {
            // Create
            const newTask = {
                id: 'T' + Date.now().toString().slice(-4),
                title, assignee, priority, startDate, dueDate, description,
                status: 'todo',
                progress: 0,
                history: [
                    { time: new Date().toISOString(), desc: 'Task created' }
                ]
            };
            tasks.push(newTask);
            if (window.addFarmNotification) {
                window.addFarmNotification('New Farming Task', `Task "${title}" assigned to ${assignee}.`, 'success');
            }
        }

        taskModal.classList.remove('active');
        renderTable();
    });

    function formatDate(dateStr) {
        const d = new Date(dateStr);
        return d.toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' });
    }

    function getProgressColor(progress) {
        if (progress < 30) return '#f59e0b'; // Orange
        if (progress < 70) return '#3b82f6'; // Blue
        return '#43a047'; // Green
    }

    function renderTable() {
        tableBody.innerHTML = '';
        let counts = { todo: 0, in_progress: 0, completed: 0 };

        tasks.forEach(task => counts[task.status]++);

        const filteredTasks = tasks.filter(task => {
            if (!currentFilter) return true;
            return task.title.toLowerCase().includes(currentFilter) || 
                   task.assignee.toLowerCase().includes(currentFilter);
        });

        filteredTasks.forEach(task => {
            
            const tr = document.createElement('tr');
            tr.innerHTML = `
                <td>
                    <div class="task-title-cell">
                        <strong>${task.title}</strong>
                        <span>${task.description}</span>
                    </div>
                </td>
                <td>
                    <div class="assignee-cell">
                        <div class="avatar">${task.assignee.charAt(0)}</div>
                        <span>${task.assignee}</span>
                    </div>
                </td>
                <td><span class="badge priority-${task.priority.toLowerCase()}">${task.priority}</span></td>
                <td>
                    <div class="schedule-cell">
                        <span><i class="fa-regular fa-calendar" style="margin-right:6px"></i> Start: ${formatDate(task.startDate)}</span>
                        <span><i class="fa-solid fa-flag-checkered" style="margin-right:6px"></i> Due: ${formatDate(task.dueDate)}</span>
                    </div>
                </td>
                <td>
                    <div class="progress-container">
                        <div class="progress-info">
                            <span>${task.progress}%</span>
                        </div>
                        <div class="progress-bar">
                            <div class="progress-fill" style="width: ${task.progress}%; background: ${getProgressColor(task.progress)}"></div>
                        </div>
                    </div>
                </td>
                <td>
                    <select class="status-select status-${task.status}" onchange="updateTaskStatus('${task.id}', this.value)">
                        <option value="todo" ${task.status === 'todo' ? 'selected' : ''}>To Do</option>
                        <option value="in_progress" ${task.status === 'in_progress' ? 'selected' : ''}>In Progress</option>
                        <option value="completed" ${task.status === 'completed' ? 'selected' : ''}>Completed</option>
                    </select>
                </td>
                <td>
                    <div class="action-buttons">
                        <button class="btn-icon-sm" onclick="editTask('${task.id}')" title="Edit"><i class="fa-solid fa-pen"></i></button>
                        <button class="btn-icon-sm" onclick="viewHistory('${task.id}')" title="History"><i class="fa-solid fa-clock-rotate-left"></i></button>
                        <button class="btn-icon-sm text-danger" onclick="deleteTask('${task.id}')" title="Delete"><i class="fa-solid fa-trash"></i></button>
                    </div>
                </td>
            `;
            tableBody.appendChild(tr);
        });

        // Update stats
        statTotal.textContent = tasks.length;
        statPending.textContent = counts.todo;
        statProgress.textContent = counts.in_progress;
        statCompleted.textContent = counts.completed;
    }

    // Global Functions for inline events
    window.updateTaskStatus = (id, newStatus) => {
        const task = tasks.find(t => t.id === id);
        if (task.status !== newStatus) {
            let oldStatus = task.status;
            task.status = newStatus;
            
            // Auto update progress
            if (newStatus === 'completed') task.progress = 100;
            else if (newStatus === 'in_progress' && task.progress === 0) task.progress = 25;
            
            task.history.push({
                time: new Date().toISOString(),
                desc: `Status updated from ${oldStatus.replace('_', ' ')} to ${newStatus.replace('_', ' ')}`
            });
            
            renderTable();
        }
    };

    window.editTask = (id) => {
        const task = tasks.find(t => t.id === id);
        document.getElementById('taskId').value = task.id;
        document.getElementById('taskName').value = task.title;
        
        // Handle select matching
        const assigneeSelect = document.getElementById('taskAssignee');
        for(let option of assigneeSelect.options) {
            if(option.value.includes(task.assignee)) {
                assigneeSelect.value = option.value;
                break;
            }
        }
        
        document.getElementById('taskPriority').value = task.priority;
        document.getElementById('taskStartDate').value = task.startDate;
        document.getElementById('taskDueDate').value = task.dueDate;
        document.getElementById('taskDescription').value = task.description;
        
        document.getElementById('modalTitle').textContent = 'Edit Task';
        taskModal.classList.add('active');
    };

    window.deleteTask = (id) => {
        if(confirm('Are you sure you want to delete this farming task?')) {
            tasks = tasks.filter(t => t.id !== id);
            renderTable();
        }
    };

    window.viewHistory = (id) => {
        const task = tasks.find(t => t.id === id);
        const timeline = document.getElementById('historyTimeline');
        timeline.innerHTML = '';
        
        [...task.history].reverse().forEach(log => {
            const date = new Date(log.time);
            timeline.innerHTML += `
                <li class="history-item">
                    <div class="history-time">${date.toLocaleString()}</div>
                    <div class="history-desc">${log.desc}</div>
                </li>
            `;
        });
        
        historyModal.classList.add('active');
    };

    // Initial render
    renderTable();
});
