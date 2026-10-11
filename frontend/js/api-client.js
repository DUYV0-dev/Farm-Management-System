'use strict';
// The authenticated parent owns the bearer token in memory. No token or business data in storage.
window.FarmApi = (() => {
    const parentApi = window.parent !== window ? window.parent.ManagementApi : null;
    if (!parentApi) {
        location.replace('/#' + (location.pathname.includes('material') ? 'material' : 'farm'));
        return { has: () => false, all: async () => [], write: async () => { throw new Error('Vui lòng đăng nhập.'); } };
    }
    return {
        has: parentApi.has,
        async all(resource, permission) {
            if (!parentApi.has(permission)) return [];
            const items = [];
            for (let page = 0; ; page++) {
                const data = await parentApi.request(`/api/v1/${resource}?page=${page}&size=100`);
                items.push(...data.items);
                if (items.length >= data.total || !data.items.length) return items;
            }
        },
        write: (resource, method, body) => parentApi.json('/api/v1/' + resource, method, body)
    };
})();
window.showToast = (title, message, type = 'success') => {
    let notice = document.getElementById('apiNotice');
    if (!notice) {
        notice = document.createElement('p'); notice.id = 'apiNotice'; notice.setAttribute('role', 'status');
    }
    (document.querySelector('.modal-overlay.active .modal-body') || document.querySelector('main')).prepend(notice);
    notice.textContent = title + ': ' + message;
    notice.className = type === 'danger' ? 'api-error' : 'api-success';
};
window.showRecord = (title, entries) => {
    const dialog = document.createElement('dialog'); dialog.className = 'record-detail';
    const heading = document.createElement('h2'); heading.textContent = title;
    const list = document.createElement('dl');
    for (const [label, value] of entries) {
        const term = document.createElement('dt'), detail = document.createElement('dd');
        term.textContent = label; detail.textContent = value ?? '—'; list.append(term, detail);
    }
    const close = document.createElement('button'); close.textContent = 'Đóng'; close.className = 'btn-primary';
    close.addEventListener('click', () => dialog.close());
    dialog.addEventListener('close', () => dialog.remove());
    dialog.append(heading, list, close); document.body.append(dialog); dialog.showModal();
};
document.addEventListener('DOMContentLoaded', () => {
    document.querySelector('.top-nav')?.remove();
    const refresh = document.createElement('button'); refresh.type = 'button'; refresh.textContent = 'Tải lại dữ liệu';
    refresh.className = 'btn-cancel'; refresh.addEventListener('click', () => location.reload());
    document.querySelector('.page-header')?.append(refresh);
});
