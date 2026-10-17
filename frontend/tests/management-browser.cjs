// Run against the isolated test app (18080) and headless Chrome CDP (9223).
// Uses only Node.js built-ins. See scripts/Test-Management.ps1 and docs/MANAGEMENT.md.
const fs = require('node:fs');
const assert = require('node:assert/strict');
const path = require('node:path');
const sleep = ms => new Promise(r => setTimeout(r,ms));
(async () => {
    const pages=await (await fetch('http://127.0.0.1:9223/json')).json();
    const ws=new WebSocket(pages.find(p=>p.type==='page').webSocketDebuggerUrl);
    await new Promise((resolve,reject)=>{ws.onopen=resolve;ws.onerror=reject;});
    let seq=0;const pending=new Map(), errors=[];
    ws.onmessage=event=>{
        const msg=JSON.parse(event.data);
        if(msg.id){const task=pending.get(msg.id);pending.delete(msg.id);msg.error?task.reject(new Error(msg.error.message)):task.resolve(msg.result);}
        if(msg.method==='Runtime.exceptionThrown') errors.push(msg.params.exceptionDetails.text);
        if(msg.method==='Page.javascriptDialogOpening') send('Page.handleJavaScriptDialog',{accept:true});
    };
    function send(method,params={}){return new Promise((resolve,reject)=>{const id=++seq;pending.set(id,{resolve,reject});ws.send(JSON.stringify({id,method,params}));});}
    async function evaluate(expression){const r=await send('Runtime.evaluate',{expression,awaitPromise:true,returnByValue:true});if(r.exceptionDetails)throw new Error(r.exceptionDetails.exception?.description || r.exceptionDetails.text);return r.result.value;}
    async function waitFor(expression){for(let i=0;i<100;i++){if(await evaluate(expression))return;await sleep(100);}throw new Error('Timed out: '+expression+'; '+await evaluate("document.getElementById('message')?.textContent || ''"));}
    async function moduleReady(name){await evaluate(`document.getElementById('${name}Button').click()`);await waitFor(`document.querySelector('iframe')?.contentDocument?.readyState==='complete' && document.querySelector('iframe').contentDocument.getElementById('${name==='farm'?'btnAdd':'addRecord'}') && !document.querySelector('iframe').contentDocument.getElementById('${name==='farm'?'btnAdd':'addRecord'}').disabled`);await evaluate("window.d=document.querySelector('iframe').contentDocument;window.w=document.querySelector('iframe').contentWindow;true");}
    async function fill(values){await evaluate(`for(const [id,value] of Object.entries(${JSON.stringify(values)})){d.getElementById(id).value=value;}`);}
    async function submit(id,closed){await evaluate(`d.getElementById('${id}').requestSubmit()`);await waitFor(closed);}
    const username=fs.readFileSync(path.join('target','browser-username.txt'),'utf8').replace(/^\uFEFF/,'').trim();
    const suffix='Browser '+Date.now();
    await send('Runtime.enable');await send('Page.enable');
    await send('Emulation.setDeviceMetricsOverride',{width:1440,height:1000,deviceScaleFactor:1,mobile:false});
    await send('Page.navigate',{url:'http://127.0.0.1:18080/'});
    await waitFor("document.readyState==='complete' && !!window.ManagementApi && !!document.getElementById('loginForm')");
    await evaluate(`document.getElementById('username').value=${JSON.stringify(username)};document.getElementById('password').value='IntegrationPassword123';document.getElementById('loginForm').requestSubmit()`);
    await waitFor("!document.getElementById('accountPanel').hidden");
    await moduleReady('farm');
    await fill({searchFarms:suffix});await evaluate("d.getElementById('searchFarms').dispatchEvent(new Event('input',{bubbles:true}))");await sleep(400);
    await evaluate("d.getElementById('btnAdd').click()");
    await fill({farmName:suffix,farmAddress:'Can Tho',farmAreaHa:'0.3',farmDescription:'Browser persistence check'});
    await submit('farmForm',"!d.getElementById('farmModal').classList.contains('active')");
    await waitFor(`d.getElementById('farmCardGrid').textContent.includes(${JSON.stringify(suffix)})`);
    assert.equal(await evaluate("(()=>{const b=d.getElementById('btnAdd'),r=b.getBoundingClientRect();return d.elementFromPoint(r.x+r.width/2,r.y+r.height/2)?.closest('#btnAdd')===b;})()"),true,'Closed modal must not intercept pointer input');
    await evaluate("d.querySelector('[data-handler=goToPlotsByFarm]').click()");
    await waitFor("d.getElementById('panel-plots').classList.contains('active')");
    await evaluate("d.getElementById('btnAdd').click()");
    await fill({plotName:suffix+' plot',plotAreaHa:'0.1',plotSoilType:'Loam',plotLocation:'North'});
    await submit('plotForm',"!d.getElementById('plotModal').classList.contains('active')");
    await waitFor(`d.getElementById('plotsTableBody').textContent.includes(${JSON.stringify(suffix)})`);
    await evaluate("d.getElementById('btnAdd').click()");
    await fill({plotName:suffix+' second plot',plotAreaHa:'0.2'});
    await submit('plotForm',"!d.getElementById('plotModal').classList.contains('active')");
    assert.equal(await evaluate("d.getElementById('plotsTableBody').querySelectorAll('tr').length"),2);
    await evaluate("d.getElementById('tab-crops').click();d.getElementById('btnAdd').click()");
    await fill({cropName:suffix+' crop',cropScientificName:'Rice',cropGrowthDays:'90'});
    await submit('cropForm',"!d.getElementById('cropModal').classList.contains('active')");
    await evaluate("d.getElementById('tab-seasons').click();d.getElementById('btnAdd').click()");
    await evaluate(`for(const id of ['seasonPlotId','seasonCropId']){const select=d.getElementById(id);select.value=[...select.options].find(o=>o.textContent.includes(${JSON.stringify(suffix)})).value;}`);
    await submit('seasonForm',"!d.getElementById('seasonModal').classList.contains('active')");
    console.log('PASS browser farm, plot, crop and season creation through authenticated API');
    await moduleReady('material');
    async function add(tab,values){await evaluate(`d.querySelector('[data-tab=${tab}]').click();d.getElementById('addRecord').click()`);await fill(values);}
    await add('categories',{'field-name':suffix+' category','field-description':'Browser test'});
    await submit('recordForm',"!d.getElementById('recordDialog').open && !d.getElementById('saveRecord').disabled");
    await add('materials',{'field-name':suffix+' material','field-unit':'kg','field-manufacturer':'Test'});
    await evaluate(`d.getElementById('field-categoryId').value=[...d.getElementById('field-categoryId').options].find(o=>o.textContent.includes(${JSON.stringify(suffix)})).value`);
    await submit('recordForm',"!d.getElementById('recordDialog').open && !d.getElementById('saveRecord').disabled");
    await add('warehouses',{'field-name':suffix+' warehouse','field-location':'North','field-capacityText':'100 m2'});
    await evaluate(`d.getElementById('field-farmId').value=[...d.getElementById('field-farmId').options].find(o=>o.textContent.includes(${JSON.stringify(suffix)})).value`);
    await submit('recordForm',"!d.getElementById('recordDialog').open && !d.getElementById('saveRecord').disabled");
    await add('inventory',{'field-quantity':'12.125'});
    await evaluate(`for(const id of ['field-warehouseId','field-materialId'])d.getElementById(id).value=[...d.getElementById(id).options].find(o=>o.textContent.includes(${JSON.stringify(suffix)})).value`);
    await submit('recordForm',"!d.getElementById('recordDialog').open && !d.getElementById('saveRecord').disabled");
    await fill({searchRecords:suffix});await evaluate("d.getElementById('searchRecords').dispatchEvent(new Event('input',{bubbles:true}))");
    await waitFor("d.getElementById('tableBody').textContent.includes('12,125')");
    await evaluate("d.querySelector('[data-action=edit]').click()");await fill({'field-quantity':'7'});
    await submit('recordForm',"!d.getElementById('recordDialog').open && !d.getElementById('saveRecord').disabled");
    await waitFor("d.getElementById('tableBody').querySelector('.inventory-quantity').textContent==='7'");
    await moduleReady('farm');await moduleReady('material');
    await evaluate("d.querySelector('[data-tab=inventory]').click()");await fill({searchRecords:suffix});await evaluate("d.getElementById('searchRecords').dispatchEvent(new Event('input',{bubbles:true}))");
    await waitFor("d.getElementById('tableBody').querySelector('.inventory-quantity')?.textContent==='7'");
    console.log('PASS browser material category, material, warehouse, stock replacement and reload persistence');
    const shot=await send('Page.captureScreenshot',{format:'png'});fs.writeFileSync('target/management-desktop.png',Buffer.from(shot.data,'base64'));
    await send('Emulation.setDeviceMetricsOverride',{width:390,height:844,deviceScaleFactor:1,mobile:true});await sleep(300);
    const mobile=await send('Page.captureScreenshot',{format:'png'});fs.writeFileSync('target/management-mobile.png',Buffer.from(mobile.data,'base64'));
    assert.deepEqual(errors,[]);
    await evaluate("document.getElementById('logoutButton').click()");await waitFor("document.getElementById('accountPanel').hidden");
    console.log('PASS logout and no uncaught browser exceptions');ws.close();
})().catch(error=>{console.error(error.message);process.exit(1);});
