document.addEventListener('DOMContentLoaded', () => {

    // 1. Crop Season Summary (Bar Chart)
    const ctxCrop = document.getElementById('cropSeasonChart').getContext('2d');
    new Chart(ctxCrop, {
        type: 'bar',
        data: {
            labels: ['Tomatoes', 'Corn', 'Wheat', 'Soybeans', 'Potatoes'],
            datasets: [{
                label: 'Expected Yield (Tons)',
                data: [45, 120, 85, 60, 95],
                backgroundColor: 'rgba(16, 185, 129, 0.85)',
                borderColor: '#10b981',
                borderWidth: 1.5,
                borderRadius: 8
            }]
        },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            plugins: {
                legend: { display: false }
            },
            scales: {
                y: { beginAtZero: true, grid: { color: 'rgba(16, 185, 129, 0.08)' } },
                x: { grid: { display: false } }
            }
        }
    });

    // 2. Farming Task Summary (Doughnut Chart)
    const ctxTask = document.getElementById('taskSummaryChart').getContext('2d');
    new Chart(ctxTask, {
        type: 'doughnut',
        data: {
            labels: ['To Do', 'In Progress', 'Completed', 'Overdue'],
            datasets: [{
                data: [15, 8, 25, 2],
                backgroundColor: [
                    '#f97316', // Terra Orange for To Do
                    '#f59e0b', // Sun Yellow for In progress
                    '#10b981', // Emerald Mint for Completed
                    '#ef4444'  // Coral Red for Overdue
                ],
                borderWidth: 3,
                borderColor: '#ffffff',
                hoverOffset: 6
            }]
        },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            plugins: {
                legend: { position: 'right', labels: { font: { family: "'Plus Jakarta Sans', sans-serif", weight: '600' } } }
            },
            cutout: '70%'
        }
    });

    // 3. Inventory Summary (Bar Chart)
    const ctxInventory = document.getElementById('inventoryChart').getContext('2d');
    new Chart(ctxInventory, {
        type: 'bar',
        data: {
            labels: ['Fertilizer (kg)', 'Seeds (kg)', 'Pesticide (L)', 'Tools (pcs)'],
            datasets: [{
                label: 'Stock Level',
                data: [850, 420, 150, 45],
                backgroundColor: 'rgba(2, 132, 199, 0.85)',
                borderColor: '#0284c7',
                borderWidth: 1.5,
                borderRadius: 8
            }]
        },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            plugins: {
                legend: { display: false }
            },
            scales: {
                y: { beginAtZero: true, grid: { color: 'rgba(16, 185, 129, 0.08)' } },
                x: { grid: { display: false } }
            }
        }
    });

    // 4. Basic Statistics (Line Chart)
    const ctxProd = document.getElementById('productivityChart').getContext('2d');
    new Chart(ctxProd, {
        type: 'line',
        data: {
            labels: ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'],
            datasets: [{
                label: 'Tasks Completed',
                data: [5, 8, 12, 7, 15, 4, 2],
                borderColor: '#10b981',
                backgroundColor: 'rgba(16, 185, 129, 0.15)',
                borderWidth: 3,
                fill: true,
                tension: 0.4,
                pointRadius: 4,
                pointBackgroundColor: '#10b981'
            }, {
                label: 'Issues Reported',
                data: [1, 0, 2, 1, 0, 0, 1],
                borderColor: '#ef4444',
                backgroundColor: 'rgba(239, 68, 68, 0.1)',
                borderWidth: 2,
                fill: true,
                tension: 0.4,
                pointRadius: 4,
                pointBackgroundColor: '#ef4444'
            }]
        },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            plugins: {
                legend: { position: 'top', labels: { font: { family: "'Plus Jakarta Sans', sans-serif", weight: '600' } } }
            },
            scales: {
                y: { beginAtZero: true, grid: { color: 'rgba(16, 185, 129, 0.08)' } },
                x: { grid: { display: false } }
            }
        }
    });

    // Button event listeners
    const btnExportReport = document.getElementById('btnExportReport');
    if (btnExportReport) {
        btnExportReport.addEventListener('click', () => {
            if (window.showToast) {
                window.showToast('Export Started', 'Preparing farm summary PDF download...', 'success');
            } else {
                alert('Exporting farm summary as PDF...');
            }
        });
    }

    const btnGenerateReport = document.getElementById('btnGenerateReport');
    if (btnGenerateReport) {
        btnGenerateReport.addEventListener('click', () => {
            if (window.showToast) {
                window.showToast('Report Wizard', 'Opening report generator...', 'info');
            } else {
                alert('Opening report generation wizard...');
            }
        });
    }

    // Global actions for report table
    window.downloadReport = (reportName) => {
        if (window.showToast) {
            window.showToast('Download Report', `Downloading "${reportName}"...`, 'success');
        } else {
            alert(`Downloading report: ${reportName}`);
        }
    };

    window.viewReport = (reportName) => {
        if (window.showToast) {
            window.showToast('Report Preview', `Viewing "${reportName}" details.`, 'info');
        } else {
            alert(`Opening view for: ${reportName}`);
        }
    };
});
