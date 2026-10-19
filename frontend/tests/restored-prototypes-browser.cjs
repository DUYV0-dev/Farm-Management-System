const assert=require('node:assert/strict'),fs=require('node:fs');
const delay=ms=>new Promise(resolve=>setTimeout(resolve,ms));
(async()=>{
 const tabs=await(await fetch('http://127.0.0.1:9223/json')).json();
 const socket=new WebSocket(tabs.find(tab=>tab.type==='page').webSocketDebuggerUrl);
 await new Promise((resolve,reject)=>{socket.onopen=resolve;socket.onerror=reject;});
 let sequence=0;const pending=new Map(),errors=[];
 socket.onmessage=event=>{
  const message=JSON.parse(event.data);
  if(message.id){const task=pending.get(message.id);pending.delete(message.id);message.error?task.reject(Error(message.error.message)):task.resolve(message.result);}
  if(message.method==='Runtime.exceptionThrown')errors.push(message.params.exceptionDetails.exception?.description||message.params.exceptionDetails.text);
  if(message.method==='Network.responseReceived'&&message.params.response.status>=400&&/\/(ui|features)\//.test(message.params.response.url))errors.push(message.params.response.url);
 };
 const send=(method,params={})=>new Promise((resolve,reject)=>{const id=++sequence;pending.set(id,{resolve,reject});socket.send(JSON.stringify({id,method,params}));});
 const evaluate=async expression=>{const result=await send('Runtime.evaluate',{expression,awaitPromise:true,returnByValue:true});if(result.exceptionDetails)throw Error(result.exceptionDetails.exception?.description||result.exceptionDetails.text);return result.result.value;};
 async function until(expression){for(let i=0;i<150;i++){if(await evaluate(expression))return;await delay(100);}throw Error('Timeout: '+expression+'; '+JSON.stringify(errors)+'; '+await evaluate("document.getElementById('prototypeFrame').contentDocument.body.textContent.slice(-1800)"));}
 const original="document.getElementById('prototypeFrame').contentDocument";
 const feature=original+".querySelector('main iframe')?.contentDocument";
 async function form(expression,values){await evaluate(`(()=>{const form=${expression};for(const [id,value] of Object.entries(${JSON.stringify(values)}))form.ownerDocument.getElementById(id).value=value;form.requestSubmit();})()`);}
 await send('Runtime.enable');await send('Page.enable');await send('Network.enable');
 await send('Emulation.setDeviceMetricsOverride',{width:1440,height:1000,deviceScaleFactor:1,mobile:false});
 await send('Page.navigate',{url:'http://127.0.0.1:18080/'});
 await until(`!!window.ManagementApi && !!${original}?.getElementById('loginEmail') && ${original}.readyState==='complete'`);
 assert.equal(await evaluate(`${original}.querySelector('.auth-card')!==null`),true);
 assert.equal(await evaluate(`${original}.body.textContent.includes('Quick Demo Login')`),false);
 const loginShot=await send('Page.captureScreenshot',{format:'png'});fs.writeFileSync('target/original-prototypes-login.png',Buffer.from(loginShot.data,'base64'));
 const name='original'+Date.now(),password='OriginalPassword123';
 await evaluate(`${original}.getElementById('tabRegister').click()`);
 await form(original+".getElementById('registerForm')",{regName:'Chủ giao diện cũ',regUsername:name,regEmail:name+'@test.invalid',regPassword:password,regFarmName:name+' farm',regAddress:'Cần Thơ',regArea:'10'});
 await until(`${original}.body.textContent.includes('Đăng ký thành công')`);
 await form(original+".getElementById('loginForm')",{loginEmail:name,loginPassword:'WrongPassword123'});
 await until(`${original}.body.textContent.includes('không đúng')`);
 assert.equal(await evaluate('ManagementApi.profile().user'),null);
 await form(original+".getElementById('loginForm')",{loginEmail:name,loginPassword:password});
 await until(`${original}?.getElementById('cropSeasonChart') && ${original}.readyState==='complete'`);
 assert.equal(await evaluate(`${original}.querySelector('.stat-info h2').textContent`),'125.5','Original dashboard cards retained');
 assert.equal(await evaluate(`${original}.querySelectorAll('.dashboard-grid canvas').length`),4,'Original charts retained');
 assert.equal(await evaluate(`${original}.querySelector('.user-name').textContent`),'Chủ giao diện cũ');
 const dashboardShot=await send('Page.captureScreenshot',{format:'png'});fs.writeFileSync('target/original-prototypes-dashboard.png',Buffer.from(dashboardShot.data,'base64'));
 for(const [page,id] of [['activities','activitiesTableBody'],['tasks','task-table-body'],['workers','workersTableBody'],['schedule','calendarGrid'],['reports','cropYieldChart'],['notifications','notificationTabs']]) {
  await evaluate(`ManagementApi.navigate(${JSON.stringify(page)})`);
  const file=page==='tasks'?'index':page;
  await until(`${original}?.URL.includes('/${file}.html') && ${original}.readyState==='complete'`);
  assert.equal(await evaluate(`${original}.querySelector('.page-header')!==null`),true);
  assert.equal(await evaluate(`${original}.body.textContent.includes('Chức năng chưa sẵn sàng')`),false);
  if(page==='tasks') {
   await evaluate(`${original}.getElementById('btnCreateTask').click()`);
   assert.equal(await evaluate(`${original}.getElementById('taskModal').classList.contains('active')`),true);
   await evaluate(`${original}.getElementById('btnCloseModal').click()`);
   await evaluate(`${original}.querySelector('[data-click^="viewHistory"]').click()`);
   assert.equal(await evaluate(`${original}.getElementById('historyModal').classList.contains('active')`),true,'Original table actions work under CSP');
  }
 }
 await evaluate("ManagementApi.navigate('staff')");
 await until("document.body.classList.contains('supplemental') && !!document.querySelector('#managementPanel form')");
 await evaluate(`(()=>{const form=document.querySelector('#managementPanel form');for(const [key,value] of Object.entries(${JSON.stringify({fullName:'Nhân viên thật',username:name+'worker',password,email:name+'worker@test.invalid',job:'WORKER'})}))form.elements.namedItem(key).value=value;form.requestSubmit();})()`);
 await until(`document.getElementById('managementPanel').textContent.includes(${JSON.stringify(name+'worker')})`);
 await evaluate("document.getElementById('completeButton').click()");
 await until(`${feature}?.getElementById('farmCardGrid')?.textContent.includes(${JSON.stringify(name)})`);
 assert.equal(await evaluate(`${original}.querySelector('.top-nav')!==null`),true,'Original navigation wraps feature page');
 await evaluate(`${feature}.getElementById('tab-plots').click();${feature}.getElementById('btnAdd').click()`);
 await form(feature+".getElementById('plotForm')",{plotName:name+' plot',plotAreaHa:'2',plotSoilType:'Đất phù sa',plotLocation:'Khu A'});
 await until(`${feature}.getElementById('plotsTableBody').textContent.includes(${JSON.stringify(name+' plot')})`);
 assert.equal(await evaluate("(async()=> (await ManagementApi.request('/api/v1/plots?page=0&size=1')).total)()"),1);
 for(const [page,id] of [['material','addRecord'],['expenses','expenseNotice'],['material-usage','usageNotice']]) {
  await evaluate(`ManagementApi.navigate(${JSON.stringify(page)})`);
  await until(`${original}?.URL.endsWith('#module-${page}') && ${feature}?.querySelector('main')`);
 }
 await evaluate("ManagementApi.navigate('home')");
 await until(`${original}?.getElementById('cropSeasonChart') && ${original}.readyState==='complete'`);
 await send('Emulation.setDeviceMetricsOverride',{width:390,height:844,deviceScaleFactor:1,mobile:true});
 await delay(250);
 assert.equal(await evaluate(`${original}.documentElement.scrollWidth<=document.getElementById('prototypeFrame').clientWidth`),true,'Original UI fits mobile');
 const mobile=await send('Page.captureScreenshot',{format:'png'});fs.writeFileSync('target/original-prototypes-mobile.png',Buffer.from(mobile.data,'base64'));
 await evaluate('ManagementApi.logout()');
 await until(`${original}?.getElementById('loginEmail')`);
 assert.equal(await evaluate('ManagementApi.profile().user'),null);
 await send('Page.navigate',{url:'http://127.0.0.1:18080/ui/prototypes/schedule.html'});
 await until(`location.pathname==='/' && location.hash==='#schedule' && !!${original}?.getElementById('loginEmail')`);
 assert.deepEqual(errors,[]);
 console.log('PASS: original prototype pages and dialogs, real login/registration, supplemental staff, nested feature CRUD, mobile and direct links.');
 socket.close();
})().catch(error=>{console.error(error);process.exit(1);});
