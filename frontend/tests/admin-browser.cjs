const assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path'),{execFileSync}=require('node:child_process');
const delay=ms=>new Promise(r=>setTimeout(r,ms));
(async()=>{
 const bin=process.argv[2];assert.ok(bin,'Pass the isolated PostgreSQL bin directory from Verify-Production.ps1');
 const psql=path.join(bin,'psql.exe'),base='http://127.0.0.1:18080',name='adminbrowser'+Date.now(),password='AdminBrowserPassword123';
 const sql=statement=>execFileSync(psql,['-X','-w','-h','127.0.0.1','-p','55432','-U','postgres','-d','management_test','-v','ON_ERROR_STOP=1','-c',statement],{encoding:'utf8',windowsHide:true});
 async function json(url,body){const r=await fetch(base+url,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(body)});return{status:r.status,body:await r.json()};}
 assert.equal((await json('/api/v1/auth/register',{username:name,password,fullName:'Admin Browser',email:name+'@test.invalid'})).status,201);
 assert.equal((await fetch(base+'/admin/users')).status,401);
 const tabs=await(await fetch('http://127.0.0.1:9223/json')).json(),socket=new WebSocket(tabs.find(t=>t.type==='page').webSocketDebuggerUrl);
 await new Promise((resolve,reject)=>{socket.onopen=resolve;socket.onerror=reject;});
 let sequence=0;const pending=new Map(),errors=[];
 socket.onmessage=e=>{const m=JSON.parse(e.data);if(m.id){const p=pending.get(m.id);pending.delete(m.id);m.error?p.reject(Error(m.error.message)):p.resolve(m.result);}if(m.method==='Runtime.exceptionThrown')errors.push(m.params.exceptionDetails.exception?.description||m.params.exceptionDetails.text);};
 const send=(method,params={})=>new Promise((resolve,reject)=>{const id=++sequence;pending.set(id,{resolve,reject});socket.send(JSON.stringify({id,method,params}));});
 async function evaluate(expression){const r=await send('Runtime.evaluate',{expression,awaitPromise:true,returnByValue:true});if(r.exceptionDetails)throw Error(r.exceptionDetails.exception?.description||r.exceptionDetails.text);return r.result.value;}
 async function until(expression){for(let i=0;i<120;i++){if(await evaluate(expression))return;await delay(100);}throw Error('Admin browser timeout: '+expression+'; '+await evaluate("document.body.textContent.slice(-1400)"));}
 const search=async username=>{await evaluate(`(()=>{const f=document.getElementById('searchUsers');f.elements.namedItem('search').value=${JSON.stringify(username)};f.requestSubmit();})()`);await until(`document.querySelector('#userList table')?.textContent.includes(${JSON.stringify(username)}) && !document.getElementById('userList').hasAttribute('aria-busy')`);};
 const action=async title=>evaluate(`[...document.querySelectorAll('#userList td button')].find(b=>b.textContent===${JSON.stringify(title)}).click()`);
 try{
  await send('Runtime.enable');await send('Page.enable');await send('Emulation.setDeviceMetricsOverride',{width:1440,height:1000,deviceScaleFactor:1,mobile:false});
  await send('Page.navigate',{url:base+'/admin'});await until('!!window.ManagementApi');
  assert.equal(await evaluate("document.getElementById('authTitle').textContent"),'Đăng nhập quản trị.');
  assert.equal(await evaluate("document.getElementById('authRegisterTab').hidden"),true);
  await evaluate(`ManagementApi.authenticate(${JSON.stringify({username:name,password})})`);await until("!!document.getElementById('adminDenied')");
  assert.equal(await evaluate("!!document.querySelector('[data-page=admin]')"),false);
  assert.equal(await evaluate("ManagementApi.request('/api/v1/users').then(()=>200).catch(e=>e.status)"),403);
  // Model the user's chosen workflow: promote the existing account directly in PostgreSQL.
  sql(`INSERT INTO user_role SELECT u.user_id,r.role_id FROM app_user u CROSS JOIN role r WHERE u.username='${name}' AND r.scope='SYSTEM' AND r.role_name='SYSTEM_ADMIN' ON CONFLICT DO NOTHING`);
  await evaluate("ManagementApi.navigate('admin')");await until("!!document.querySelector('#userList table')");
  assert.equal(await evaluate('ManagementApi.profile().farm'),null);
  assert.equal(await evaluate("!!document.querySelector('[data-page=admin]')"),true);
  const managed='managed'+Date.now();
  await evaluate("document.getElementById('createUserButton').click()");await until("document.getElementById('portalDialog').open");
  await evaluate(`(()=>{const f=document.querySelector('#dialogContent form');for(const[k,v]of Object.entries(${JSON.stringify({username:managed,password,fullName:'Người dùng thử',email:managed+'@test.invalid'})}))f.elements.namedItem(k).value=v;f.requestSubmit();})()`);await until("!document.getElementById('portalDialog').open");
  await search(managed);assert.equal(await evaluate("document.querySelectorAll('#userList tbody tr').length"),1);
  const managedSession=(await json('/api/v1/auth/login',{username:managed,password})).body.data.accessToken;
  await action('Chỉnh sửa');await until("document.getElementById('portalDialog').open");
  await evaluate("(()=>{const f=document.querySelector('#dialogContent form');f.elements.namedItem('fullName').value='Người dùng đã sửa';f.requestSubmit();})()");await until("!document.getElementById('portalDialog').open && document.getElementById('userList').textContent.includes('Người dùng đã sửa')");
  await action('Khóa');await until("document.getElementById('portalDialog').open");await evaluate("document.querySelector('#dialogContent .primary').click()");await until("!document.getElementById('portalDialog').open && document.getElementById('userList').textContent.includes('Đã khóa')");
  assert.equal((await json('/api/v1/auth/login',{username:managed,password})).status,401);
  assert.equal((await fetch(base+'/api/v1/auth/me',{headers:{Authorization:'Bearer '+managedSession}})).status,401);
  await action('Mở khóa');await until("document.getElementById('portalDialog').open");await evaluate("document.querySelector('#dialogContent .primary').click()");await until("!document.getElementById('portalDialog').open && document.getElementById('userList').textContent.includes('Đang hoạt động')");
  await action('Đặt lại mật khẩu');await until("document.getElementById('portalDialog').open");
  const changed='ChangedBrowserPassword456';await evaluate(`(()=>{const f=document.querySelector('#dialogContent form');f.elements.namedItem('newPassword').value=${JSON.stringify(changed)};f.requestSubmit();})()`);await until("!document.getElementById('portalDialog').open");
  assert.equal((await json('/api/v1/auth/login',{username:managed,password})).status,401);assert.equal((await json('/api/v1/auth/login',{username:managed,password:changed})).status,200);
  await action('Phân quyền');await until("document.getElementById('portalDialog').open && !!document.querySelector('.admin-permissions')");
  await evaluate("(()=>{const f=document.querySelector('#dialogContent form');[...f.querySelectorAll('label')].find(l=>l.textContent.includes('Quản trị viên hệ thống')).querySelector('input').checked=true;f.requestSubmit();})()");await until("!document.getElementById('portalDialog').open && document.querySelector('#userList table').textContent.includes('Quản trị viên hệ thống')");
  fs.writeFileSync('target/admin-users.png',Buffer.from((await send('Page.captureScreenshot',{format:'png'})).data,'base64'));
  await evaluate("document.querySelector('[data-admin-page=roles]').click()");await until("!!document.querySelector('.admin-role-card')");
  fs.writeFileSync('target/admin-roles.png',Buffer.from((await send('Page.captureScreenshot',{format:'png'})).data,'base64'));
  await evaluate("document.querySelector('[data-admin-page=users]').click()");await until("!!document.querySelector('#userList table')");
  await send('Emulation.setDeviceMetricsOverride',{width:390,height:844,deviceScaleFactor:1,mobile:true});await delay(100);
  assert.equal(await evaluate('document.documentElement.scrollWidth<=innerWidth+1'),true,'Admin layout must fit the mobile viewport');
  fs.writeFileSync('target/admin-mobile.png',Buffer.from((await send('Page.captureScreenshot',{format:'png'})).data,'base64'));
  sql(`DELETE FROM user_role ur USING app_user u,role r WHERE ur.user_id=u.user_id AND ur.role_id=r.role_id AND u.username='${name}' AND r.scope='SYSTEM' AND r.role_name='SYSTEM_ADMIN'`);
  assert.equal(await evaluate("ManagementApi.request('/api/v1/users').then(()=>200).catch(e=>e.status)"),403);
  await evaluate("ManagementApi.navigate('admin')");await until("!!document.getElementById('adminDenied')");
  assert.equal(await evaluate("!!document.querySelector('[data-page=admin]')"),false);
  assert.deepEqual(errors,[]);console.log('PASS: PostgreSQL promotion/revocation, admin login, user search/create/edit, lock/unlock, password reset, roles, session revocation and mobile layout');
 }finally{socket.close();}
})().catch(error=>{console.error(error);process.exitCode=1;});
