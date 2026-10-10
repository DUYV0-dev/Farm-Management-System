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

    loginForm.addEventListener('submit', (e) => {
        e.preventDefault();
        const email = document.getElementById('loginEmail').value;

        let name = 'Farm Manager';
        let role = 'Administrator';
        let avatar = 'https://i.pravatar.cc/150?img=33';

        if (email.includes('jane')) {
            name = 'Jane Smith';
            role = 'Agronomist';
            avatar = 'https://i.pravatar.cc/150?img=47';
        } else if (email.includes('mike')) {
            name = 'Mike Johnson';
            role = 'Technician';
            avatar = 'https://i.pravatar.cc/150?img=12';
        }

        const userObj = { name, role, email, avatar };
        AgriAuth.setUser(userObj);

        showToast('Welcome back!', `Logged in as ${name} (${role})`, 'success');
        setTimeout(() => {
            window.location.href = 'dashboard.html';
        }, 800);
    });

    registerForm.addEventListener('submit', (e) => {
        e.preventDefault();
        const name = document.getElementById('regName').value;
        const role = document.getElementById('regRole').value;
        const email = document.getElementById('regEmail').value;

        const userObj = {
            name,
            role,
            email,
            avatar: `https://i.pravatar.cc/150?u=${encodeURIComponent(email)}`
        };

        AgriAuth.setUser(userObj);
        showToast('Account Created!', `Welcome to AgriManage, ${name}!`, 'success');

        // Send a welcoming notification
        if (window.addFarmNotification) {
            window.addFarmNotification(
                'Welcome to AgriManage',
                `System account activated for ${name} (${role}).`,
                'success'
            );
        }

        setTimeout(() => {
            window.location.href = 'dashboard.html';
        }, 1000);
    });
});
