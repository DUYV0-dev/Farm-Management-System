'use strict';
document.addEventListener('DOMContentLoaded',()=>{
  const login=document.getElementById('loginForm'),register=document.getElementById('registerForm');
  const tabs={login:document.getElementById('tabLogin'),register:document.getElementById('tabRegister')};
  function switchTab(name) {
    login.style.display=name==='login'?'block':'none';register.style.display=name==='register'?'block':'none';
    for(const [key,tab] of Object.entries(tabs)) tab.classList.toggle('active',key===name);
  }
  tabs.login.onclick=()=>switchTab('login');tabs.register.onclick=()=>switchTab('register');
  async function submit(event,action) {
    event.preventDefault();const button=event.submitter||event.target.querySelector('[type=submit]');button.disabled=true;
    try {await action();} catch(error) {showToast('Không thực hiện được',error.message,'danger');}
    finally {button.disabled=false;}
  }
  login.addEventListener('submit',event=>submit(event,()=>window.parent.ManagementApi.authenticate({username:document.getElementById('loginEmail').value.trim(),password:document.getElementById('loginPassword').value})));
  register.addEventListener('submit',event=>submit(event,async()=>{
    const value=id=>document.getElementById(id).value;
    await window.parent.ManagementApi.register({fullName:value('regName'),username:value('regUsername').trim(),email:value('regEmail'),password:value('regPassword'),farmName:value('regFarmName'),address:value('regAddress'),areaHa:value('regArea')});
    document.getElementById('loginEmail').value=value('regUsername');register.reset();switchTab('login');
    showToast('Đăng ký thành công','Đăng nhập để quản lý trang trại và cấp tài khoản nhân viên.','success');
  }));
});
