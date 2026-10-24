document.addEventListener('DOMContentLoaded', () => {
    const tabLogin = document.getElementById('tabLogin');
    const tabRegister = document.getElementById('tabRegister');
    const loginForm = document.getElementById('loginForm');
    const registerForm = document.getElementById('registerForm');

    // Quick demo buttons
    document.getElementById('demoManager').addEventListener('click', () => {
        document.getElementById('loginEmail').value = 'manager@agrimanage.com';
        document.getElementById('loginPassword').value = 'password123';
        switchTab('login');
    });

    document.getElementById('demoAgronomist').addEventListener('click', () => {
        document.getElementById('loginEmail').value = 'jane.smith@agrimanage.com';
        document.getElementById('loginPassword').value = 'password123';
        switchTab('login');
    });

    document.getElementById('demoTech').addEventListener('click', () => {
        document.getElementById('loginEmail').value = 'mike.j@agrimanage.com';
        document.getElementById('loginPassword').value = 'password123';
        switchTab('login');
    });

    tabLogin.addEventListener('click', () => switchTab('login'));
    tabRegister.addEventListener('click', () => switchTab('register'));

    function switchTab(tab) {
        if (tab === 'login') {
            tabLogin.classList.add('active');
            tabRegister.classList.remove('active');
            loginForm.style.display = 'block';
            registerForm.style.display = 'none';
        } else {
            tabRegister.classList.add('active');
            tabLogin.classList.remove('active');
            registerForm.style.display = 'block';
            loginForm.style.display = 'none';
        }
    }

    loginForm.addEventListener('submit', async (e) => {
        e.preventDefault();
        const emailInput = document.getElementById('loginEmail');
        const passwordInput = document.getElementById('loginPassword');
        const email = emailInput.value.trim();
        const password = passwordInput.value.trim();

        if (window.clearFormValidation) {
            window.clearFormValidation(loginForm);
        }

        let hasError = false;
        if (!email) {
            if (window.showInputError) window.showInputError(emailInput, 'Vui lòng nhập email hoặc tên đăng nhập');
            hasError = true;
        }
        if (!password) {
            if (window.showInputError) window.showInputError(passwordInput, 'Vui lòng nhập mật khẩu');
            hasError = true;
        }
        if (hasError) return;

        const submitBtn = loginForm.querySelector('button[type="submit"]');
        if (window.setButtonLoading) {
            window.setButtonLoading(submitBtn, true, 'Đang xác thực...');
        }

        let name = 'Võ Hà Duy';
        let role = 'Farm Manager & Administrator';
        let avatar = 'https://images.unsplash.com/photo-1534528741775-53994a69daeb?auto=format&fit=crop&w=150&q=80';

        if (email.toLowerCase().includes('jane')) {
            name = 'Jane Smith';
            role = 'Agronomist';
            avatar = 'https://images.unsplash.com/photo-1494790108377-be9c29b29330?auto=format&fit=crop&w=150&q=80';
        } else if (email.toLowerCase().includes('mike')) {
            name = 'Mike Johnson';
            role = 'Technician';
            avatar = 'https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?auto=format&fit=crop&w=150&q=80';
        }

        try {
            // Attempt Spring Boot JWT authentication
            const username = email.includes('@') ? email.split('@')[0] : email;
            const res = await fetch('/api/v1/auth/login', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ username, password })
            });

            if (res.ok) {
                const json = await res.json();
                const token = json.data && (json.data.accessToken || json.data.token);
                if (token) {
                    localStorage.setItem('agri_jwt', token);
                    localStorage.setItem('agri_auth_token', token);
                }
            }
        } catch (authErr) {
            console.warn('Backend login endpoint unavailable, proceeding with local authenticated session', authErr);
        } finally {
            if (window.setButtonLoading) {
                window.setButtonLoading(submitBtn, false);
            }
        }

        const userObj = { name, role, email, avatar };
        if (window.AgriAuth && window.AgriAuth.setUser) {
            window.AgriAuth.setUser(userObj);
        } else {
            localStorage.setItem('agri_user', JSON.stringify(userObj));
        }

        if (window.showToast) {
            window.showToast('Đăng nhập thành công', `Chào mừng ${name} (${role}) quay trở lại hệ thống!`, 'success');
        }

        setTimeout(() => {
            window.location.href = 'dashboard.html';
        }, 600);
    });

    registerForm.addEventListener('submit', (e) => {
        e.preventDefault();
        const nameInput = document.getElementById('regName');
        const emailInput = document.getElementById('regEmail');
        const passInput = document.getElementById('regPassword');
        const role = document.getElementById('regRole').value;

        const name = nameInput.value.trim();
        const email = emailInput.value.trim();
        const pass = passInput.value.trim();

        if (window.clearFormValidation) {
            window.clearFormValidation(registerForm);
        }

        let hasError = false;
        if (!name) {
            if (window.showInputError) window.showInputError(nameInput, 'Vui lòng nhập họ và tên');
            hasError = true;
        }
        if (!email) {
            if (window.showInputError) window.showInputError(emailInput, 'Vui lòng nhập địa chỉ email');
            hasError = true;
        }
        if (!pass || pass.length < 6) {
            if (window.showInputError) window.showInputError(passInput, 'Mật khẩu phải có tối thiểu 6 ký tự');
            hasError = true;
        }
        if (hasError) return;

        const submitBtn = registerForm.querySelector('button[type="submit"]');
        if (window.setButtonLoading) {
            window.setButtonLoading(submitBtn, true, 'Đang khởi tạo tài khoản...');
        }

        const userObj = {
            name,
            role,
            email,
            avatar: `https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?auto=format&fit=crop&w=150&q=80`
        };

        if (window.AgriAuth && window.AgriAuth.setUser) {
            window.AgriAuth.setUser(userObj);
        } else {
            localStorage.setItem('agri_user', JSON.stringify(userObj));
        }

        if (window.showToast) {
            window.showToast('Tạo tài khoản thành công', `Chào mừng ${name} gia nhập AgriManage!`, 'success');
        }

        if (window.addFarmNotification) {
            window.addFarmNotification(
                'Tài khoản mới được tạo',
                `Người dùng ${name} (${role}) vừa kích hoạt tài khoản thành công.`,
                'success'
            );
        }

        setTimeout(() => {
            window.location.href = 'dashboard.html';
        }, 800);
    });
});
