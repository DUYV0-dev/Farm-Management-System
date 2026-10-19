// Native Chrome DevTools protocol; no dependency installation required.
const assert=require('node:assert/strict');
const fs=require('node:fs');
const delay=ms=>new Promise(resolve=>setTimeout(resolve,ms));
(async()=>{
    const tabs=await(await fetch('http://127.0.0.1:9223/json')).json();
    const socket=new WebSocket(tabs.find(tab=>tab.type==='page').webSocketDebuggerUrl);
    await new Promise((resolve,reject)=>{socket.onopen=resolve;socket.onerror=reject;});
    let sequence=0;const waiting=new Map(),errors=[];
    socket.onmessage=event=>{
        const result=JSON.parse(event.data);
        if(result.id){const task=waiting.get(result.id);waiting.delete(result.id);result.error?task.reject(new Error(result.error.message)):task.resolve(result.result);}
        if(result.method==='Runtime.exceptionThrown') errors.push(result.params.exceptionDetails.exception?.description||result.params.exceptionDetails.text);
        if(result.method==='Network.responseReceived' && result.params.response.status>=400 && /\/(ui|features)\//.test(result.params.response.url)) errors.push('Resource failed: '+result.params.response.url);
    };
    function send(method,params={}) {return new Promise((resolve,reject)=>{const id=++sequence;waiting.set(id,{resolve,reject});socket.send(JSON.stringify({id,method,params}));});}
    async function evaluate(expression) {const result=await send('Runtime.evaluate',{expression,awaitPromise:true,returnByValue:true});if(result.exceptionDetails)throw new Error(result.exceptionDetails.exception?.description||result.exceptionDetails.text);return result.result.value;}
    async function until(expression) {for(let attempt=0;attempt<250;attempt++){if(await evaluate(expression))return;await delay(100);}throw new Error('Timeout: '+expression+'; '+await evaluate("document.getElementById('message')?.textContent"));}
    async function form(id,values) {await evaluate(`(()=>{const form=document.getElementById(${JSON.stringify(id)});for(const [name,value] of Object.entries(${JSON.stringify(values)}))form.elements.namedItem(name).value=value;form.requestSubmit();})()`);}
    await send('Runtime.enable');await send('Page.enable');await send('Network.enable');
    await send('Emulation.setDeviceMetricsOverride',{width:1440,height:1000,deviceScaleFactor:1,mobile:false});
    await send('Page.navigate',{url:'http://127.0.0.1:18080/'});
    await until("document.readyState==='complete' && !!window.ManagementApi");
    const loginShot=await send('Page.captureScreenshot',{format:'png'});fs.writeFileSync('target/workspace-login.png',Buffer.from(loginShot.data,'base64'));
    const name='browser'+Date.now(),password='BrowserPassword123',worker=name+'worker';
    await evaluate("document.getElementById('registerPanel').open=true");
    await until("document.getElementById('authRegisterTab').getAttribute('aria-pressed')==='true'");
    const registerShot=await send('Page.captureScreenshot',{format:'png'});fs.writeFileSync('target/workspace-register.png',Buffer.from(registerShot.data,'base64'));
    await form('registerForm',{username:name,password,fullName:'Chủ trang trại thử nghiệm',email:name+'@test.invalid',farmName:name+' farm',address:'Cần Thơ',areaHa:'10'});
    await until("document.getElementById('message').textContent.includes('Đăng ký thành công')");
    await form('loginForm',{username:name,password:'WrongPassword123'});
    await until("document.getElementById('message').textContent.includes('không đúng')");
    assert.equal(await evaluate("document.getElementById('accountPanel').hidden"),true);
    await form('loginForm',{username:name,password});
    await until("document.getElementById('managementPanel').querySelector('form') && !document.getElementById('staffButton').hidden");
    assert.equal(await evaluate("document.getElementById('farmButton').hidden"),true);
    assert.equal(await evaluate("document.getElementById('usersButton').hidden"),true);
    await evaluate(`(()=>{const form=document.querySelector('#managementPanel form');for(const [name,value] of Object.entries(${JSON.stringify({username:worker,password,fullName:'Nhân viên canh tác',email:worker+'@test.invalid',job:'WORKER'})}))form.elements.namedItem(name).value=value;form.requestSubmit();})()`);
    await until(`document.getElementById('managementPanel').textContent.includes(${JSON.stringify(worker)})`);
    await evaluate("document.getElementById('completeButton').click()");
    await until("document.querySelector('iframe')?.contentDocument?.getElementById('farmCardGrid')?.textContent.includes('browser')");
    await evaluate("window.d=document.querySelector('iframe').contentDocument;d.getElementById('tab-plots').click();d.getElementById('btnAdd').click()");
    await evaluate(`(()=>{for(const [id,value] of Object.entries(${JSON.stringify({plotName:name+' plot',plotAreaHa:'2',plotSoilType:'Đất phù sa',plotLocation:'Khu A'})}))d.getElementById(id).value=value;d.getElementById('plotForm').requestSubmit();})()`);
    await until(`d.getElementById('plotsTableBody').textContent.includes(${JSON.stringify(name+' plot')})`);
    await delay(400); // Wait for the dialog closing animation before capturing the page.
    const screenshot=await send('Page.captureScreenshot',{format:'png'});fs.writeFileSync('target/workspace-browser.png',Buffer.from(screenshot.data,'base64'));
    await evaluate("document.getElementById('materialButton').click()");
    await until("document.querySelector('iframe')?.contentDocument?.getElementById('addRecord') && !document.querySelector('iframe').contentDocument.getElementById('addRecord').disabled");
    assert.equal(await evaluate("document.getElementById('materialButton').getAttribute('aria-current')"),'page');
    await evaluate("document.querySelector('iframe').contentDocument.querySelector('a[href=\"/#material-usage\"]').click()");
    await until("location.hash==='#material-usage' && document.querySelector('iframe')?.contentDocument?.getElementById('usageNotice')");
    await evaluate("document.getElementById('checkButton').click()");
    await until("document.querySelector('.overview-grid strong')?.textContent==='1'");
    await until("document.querySelector('[data-chart=expenses] .chart-empty') && document.querySelector('[data-chart=seasons] .chart-empty')");
    await evaluate(`(async()=>{
      const plots=await ManagementApi.request('/api/v1/plots?page=0&size=1');
      const crop=await ManagementApi.json('/api/v1/crops','POST',{name:${JSON.stringify(name+' rice')},scientificName:null,category:'Grain',growthDays:null,description:null});
      await ManagementApi.json('/api/v1/seasons','POST',{plotId:plots.items[0].id,cropId:crop.id,status:'PLANNED'});
      const farmId=Number(document.getElementById('farmSelector').value);
      const parts=new Intl.DateTimeFormat('en',{timeZone:'Asia/Bangkok',year:'numeric',month:'2-digit',day:'2-digit'}).formatToParts(new Date());
      const date=['year','month','day'].map(type=>parts.find(part=>part.type===type).value).join('-');
      await ManagementApi.json('/api/v1/expenses','POST',{farmId,category:'SEEDS',amount:'1500000.25',expenseDate:date});
      await ManagementApi.json('/api/v1/expenses','POST',{farmId,category:'FERTILIZER',amount:'250000.00',expenseDate:date});
      await openHome();
    })()`);
    assert.equal(await evaluate("document.querySelector('[data-chart=seasons] .chart-donut-total strong').textContent"),'1');
    assert.equal(await evaluate("document.querySelector('[data-chart=expenses] .chart-expense-total').textContent"),'1.750.000,25 ₫');
    assert.equal(await evaluate("document.querySelectorAll('[data-chart=expenses] .chart-bars li').length"),2);
    assert.equal(await evaluate("document.querySelectorAll('[data-chart=records] .chart-bars li').length"),6);
    const overviewShot=await send('Page.captureScreenshot',{format:'png'});fs.writeFileSync('target/workspace-overview.png',Buffer.from(overviewShot.data,'base64'));
    await send('Emulation.setDeviceMetricsOverride',{width:390,height:844,deviceScaleFactor:1,mobile:true});
    assert.equal(await evaluate("document.documentElement.scrollWidth<=window.innerWidth"),true,'Mobile overview must fit viewport');
    const mobile=await send('Page.captureScreenshot',{format:'png'});fs.writeFileSync('target/workspace-mobile.png',Buffer.from(mobile.data,'base64'));
    await send('Emulation.setDeviceMetricsOverride',{width:1440,height:1000,deviceScaleFactor:1,mobile:false});
    await evaluate("document.getElementById('expensesButton').click()");
    await until("document.querySelector('iframe')?.contentDocument?.querySelector('main')?.textContent.includes('Chi phí')");
    await evaluate("document.getElementById('usageButton').click()");
    await until("document.querySelector('iframe')?.contentDocument?.getElementById('addUsage') || document.querySelector('iframe')?.contentDocument?.querySelector('main')?.textContent.includes('Phân bón')");
    await evaluate("document.getElementById('logoutButton').click()");await until("document.getElementById('accountPanel').hidden");
    await form('loginForm',{username:worker,password});
    await until("!document.getElementById('accountPanel').hidden && !document.getElementById('farmButton').hidden");
    assert.equal(await evaluate("document.getElementById('staffButton').hidden"),true);
    assert.equal(await evaluate("document.getElementById('usersButton').hidden"),true);
    assert.equal(await evaluate("document.getElementById('expensesButton').hidden"),true);
    await evaluate("document.getElementById('checkButton').click()");
    await until("document.querySelector('[data-chart=seasons] .chart-donut-total strong')?.textContent==='1'");
    assert.equal(await evaluate("document.querySelector('[data-chart=expenses]')===null"),true,'Worker must not see expense statistics');
    await evaluate("document.getElementById('farmButton').click()");
    await until("document.querySelector('iframe')?.contentDocument?.getElementById('tab-plots')");
    await evaluate("window.d=document.querySelector('iframe').contentDocument;d.getElementById('tab-plots').click()");
    await until(`d.getElementById('plotsTableBody').textContent.includes(${JSON.stringify(name+' plot')})`);
    assert.equal(await evaluate("d.getElementById('btnAdd').disabled || d.getElementById('btnAdd').hidden || d.getElementById('btnAdd').style.display==='none'"),true);
    assert.deepEqual(errors,[]);
    assert.equal((await fetch('http://127.0.0.1:18080/actuator/health')).status,200);
    assert.equal((await fetch('http://127.0.0.1:18080/ui/prototypes/dashboard.html')).status,200,'Main prototype layout must be packaged');
    console.log('PASS browser: registration, rejected password, owner onboarding, staff, persisted plot, modules and worker permissions. Screenshot: target/workspace-browser.png');
    socket.close();
})().catch(error=>{console.error(error);process.exit(1);});
