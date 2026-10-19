document.addEventListener('DOMContentLoaded', () => {
    // 1. Crop Yield Chart
    const ctxYield = document.getElementById('cropYieldChart').getContext('2d');
    new Chart(ctxYield, {
        type: 'bar',
        data: {
            labels: ['Tomatoes', 'Corn', 'Wheat', 'Soybeans', 'Potatoes', 'Apples'],
            datasets: [
                {
                    label: '2025 Harvest (Tons)',
                    data: [38, 110, 75, 50, 80, 45],
                    backgroundColor: 'rgba(2, 132, 199, 0.65)',
                    borderColor: '#0284c7',
                    borderWidth: 1.5,
                    borderRadius: 6
                },
                {
                    label: '2026 Yield (Tons)',
                    data: [45, 125, 85, 60, 95, 55],
                    backgroundColor: 'rgba(16, 185, 129, 0.85)',
                    borderColor: '#10b981',
                    borderWidth: 1.5,
                    borderRadius: 6
                }
            ]
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

    // 2. Expenditure Breakdown
    const ctxExpense = document.getElementById('expenseChart').getContext('2d');
    new Chart(ctxExpense, {
        type: 'doughnut',
        data: {
            labels: ['Labor & Payroll', 'Fertilizer & Seeds', 'Fuel & Transport', 'Equipment Repairs', 'Utilities & Water'],
            datasets: [{
                data: [18500, 11200, 5400, 4200, 3550],
                backgroundColor: [
                    '#10b981', // Emerald
                    '#0284c7', // Sky Blue
                    '#f59e0b', // Sun Amber
                    '#f97316', // Terra Orange
                    '#8b5cf6'  // Lavender
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
            cutout: '68%'
        }
    });

    // 3. Resource Usage Trend
    const ctxResource = document.getElementById('resourceChart').getContext('2d');
    new Chart(ctxResource, {
        type: 'line',
        data: {
            labels: ['May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct'],
            datasets: [
                {
                    label: 'Water (1,000 Liters)',
                    data: [420, 580, 710, 690, 520, 480],
                    borderColor: '#0284c7',
                    backgroundColor: 'rgba(2, 132, 199, 0.12)',
                    fill: true,
                    tension: 0.35,
                    borderWidth: 2.5,
                    pointRadius: 4,
                    pointBackgroundColor: '#0284c7'
                },
                {
                    label: 'Fertilizer (kg)',
                    data: [300, 450, 600, 550, 400, 350],
                    borderColor: '#f59e0b',
                    backgroundColor: 'rgba(245, 158, 11, 0.12)',
                    fill: true,
                    tension: 0.35,
                    borderWidth: 2.5,
                    pointRadius: 4,
                    pointBackgroundColor: '#f59e0b'
                }
            ]
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

    // 4. Productivity Detail
    const ctxProdDetail = document.getElementById('productivityDetailChart').getContext('2d');
    new Chart(ctxProdDetail, {
        type: 'bar',
        data: {
            labels: ['Team A (Greenhouse)', 'Team B (Orchard)', 'Team C (Grains)', 'Team D (Tech)'],
            datasets: [{
                label: 'Tasks Completed',
                data: [42, 38, 29, 35],
                backgroundColor: 'rgba(16, 185, 129, 0.85)',
                borderColor: '#10b981',
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

    // Modal Control
    const generateReportModal = document.getElementById('generateReportModal');
    const generateReportForm = document.getElementById('generateReportForm');

    document.getElementById('btnNewReport').addEventListener('click', () => {
        generateReportForm.reset();
        generateReportModal.classList.add('active');
    });

    document.getElementById('btnExportPDF').addEventListener('click', () => {
        if (window.showToast) {
            window.showToast('PDF Export', 'Exporting overall Farm Analytics PDF report...', 'success');
        } else {
            alert('Exporting overall Farm Analytics as PDF...');
        }
    });

    document.getElementById('btnCloseReportModal').addEventListener('click', () => generateReportModal.classList.remove('active'));
    document.getElementById('btnCancelReportModal').addEventListener('click', () => generateReportModal.classList.remove('active'));

    generateReportForm.addEventListener('submit', (e) => {
        e.preventDefault();
        const name = document.getElementById('newReportName').value;
        const type = document.getElementById('newReportType').value;

        generateReportModal.classList.remove('active');
        if (window.showToast) {
            window.showToast('Report Generated', `Report "${name}" (${type}) has been created.`, 'success');
        } else {
            alert(`Report "${name}" generated successfully!`);
        }
    });
});
