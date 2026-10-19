'use strict';
// The dashboard owns the real backend session in memory while module frames change.
(() => {
    if (window.parent !== window && window.parent.ManagementApi) return;
    let token = null, user = null, pendingLogin = null;
    const has = permission => Boolean(user?.permissions?.includes(permission));
    async function request(path, options = {}) {
        const session = token;
        const headers = { ...options.headers };
        if (session) headers.Authorization = 'Bearer ' + session;
        const response = await fetch(path, { ...options, headers, credentials: 'omit', cache: 'no-store' });
        if (token !== session) throw new Error('Phiên đăng nhập đã thay đổi.');
        if (response.status === 204) return null;
        const result = await response.json().catch(() => null);
        if (!response.ok) {
            if (response.status === 401 && path !== '/api/v1/auth/login') {
                token = null; user = null;
                document.getElementById('managementFrame')?.remove();
            }
            throw new Error(result?.error?.message || 'Không thể xử lý yêu cầu.');
        }
        return result.data;
    }
    const json = (path, method, body) => request(path, {
        method, headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body)
    });
    function login() {
        if (pendingLogin) return pendingLogin;
        pendingLogin = new Promise((resolve, reject) => {
            const dialog = document.createElement('dialog');
            dialog.className = 'backend-login';
            dialog.innerHTML = '<form><h2>Đăng nhập quản lý</h2><p>Sử dụng tài khoản ứng dụng được cấp quyền.</p>' +
                '<label>Tên đăng nhập<input name="username" autocomplete="username" required maxlength="50"></label>' +
                '<label>Mật khẩu<input name="password" type="password" autocomplete="current-password" required maxlength="128"></label>' +
                '<p role="alert"></p><button type="submit">Đăng nhập</button> <button type="button">Hủy</button></form>';
            const form = dialog.querySelector('form'), submit = form.querySelector('[type=submit]');
            const cancel = () => { dialog.close(); dialog.remove(); reject(new Error('Đã hủy đăng nhập.')); };
            dialog.addEventListener('cancel', event => { event.preventDefault(); cancel(); });
            form.querySelector('[type=button]').onclick = cancel;
            form.onsubmit = async event => {
                event.preventDefault(); submit.disabled = true;
                const fields = new FormData(form);
                try {
                    const result = await json('/api/v1/auth/login', 'POST', {
                        username: fields.get('username').trim(), password: fields.get('password')
                    });
                    token = result.accessToken; user = result.user;
                    document.querySelectorAll('.user-name').forEach(node => node.textContent = user.fullName);
                    document.querySelectorAll('.user-role').forEach(node => node.textContent = user.systemRoles.join(', '));
                    dialog.close(); form.reset(); dialog.remove(); resolve();
                } catch (error) {
                    form.querySelector('[role=alert]').textContent = error.message;
                } finally { submit.disabled = false; }
            };
            document.body.append(dialog); dialog.showModal(); form.elements.username.focus();
        }).finally(() => { pendingLogin = null; });
        return pendingLogin;
    }
    const ready = async () => {
        if (!token) await login();
        user = (await request('/api/v1/auth/me')).user;
    };
    window.ManagementApi = { has, request, json, ready };
    const isDashboard = location.pathname.endsWith('/dashboard.html');
    async function openModule(module) {
        if (!token) await login();
        user = (await request('/api/v1/auth/me')).user;
        const permissions = module === 'farm' ? ['farms:read', 'plots:read', 'crops:read']
            : module === 'material-usage' ? ['usage:read', 'usage:create']
            : module === 'expenses' ? ['expenses:read', 'expenses:create'] : ['materials:read', 'warehouses:read'];
        if (!permissions.some(has)) throw new Error('Tài khoản chưa có quyền xem mục này.');
        const main = document.querySelector('main');
        let frame = document.getElementById('managementFrame');
        if (!frame) {
            for (const child of main.children) child.hidden = true;
            frame = document.createElement('iframe'); frame.id = 'managementFrame';
            frame.style.cssText = 'display:block;width:100%;height:calc(100vh - 130px);border:0;min-height:600px';
            main.append(frame);
        }
        frame.title = module === 'farm' ? 'Trang trại & Cây trồng'
            : module === 'material-usage' ? 'Phân bón & Thuốc bảo vệ thực vật'
            : module === 'expenses' ? 'Chi phí nông nghiệp' : 'Vật tư & Kho';
        frame.src = '/frontend/' + module + '.html';
        document.querySelectorAll('.nav-links .nav-item').forEach(link => {
            link.classList.toggle('active', link.getAttribute('href') === module + '.html');
        });
        history.replaceState(null, '', '#' + module);
    }
    function showError(error) {
        let notice = document.getElementById('sessionError');
        if (!notice) {
            notice = document.createElement('p'); notice.id = 'sessionError'; notice.setAttribute('role', 'alert');
            document.querySelector('main').prepend(notice);
        }
        notice.hidden = false; notice.textContent = error.message;
    }
    document.addEventListener('click', event => {
        const link = event.target.closest('a');
        if (!link || event.ctrlKey || event.metaKey || event.shiftKey || event.altKey) return;
        const target = link.getAttribute('href');
        if (isDashboard && ['farm.html', 'material.html', 'material-usage.html', 'expenses.html'].includes(target)) {
            event.preventDefault(); openModule(target.replace('.html', '')).catch(showError);
        } else if (link.id === 'btnLogout') {
            event.preventDefault();
            request('/api/v1/auth/logout', { method: 'POST' }).then(() => {
                token = null; user = null; window.AgriAuth?.setUser(null); location.href = 'dashboard.html';
            }).catch(showError);
        }
    }, true);
    const style = document.createElement('style');
    style.textContent = '.backend-login{margin:auto;padding:28px;border:1px solid #cbd5e1;border-radius:16px;width:min(420px,90vw)}.backend-login::backdrop{background:#153d3077}.backend-login label{display:block;margin:16px 0}.backend-login input{display:block;width:100%;padding:10px;margin-top:6px}.backend-login button{padding:10px 16px;cursor:pointer}.backend-login [role=alert],#sessionError{color:#991b1b}';
    document.head.append(style);
    const module = location.hash.slice(1);
    if (isDashboard && ['farm', 'material', 'material-usage', 'expenses'].includes(module)) openModule(module).catch(showError);
})();
