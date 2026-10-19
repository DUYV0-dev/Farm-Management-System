// Isolated browser smoke test with API fixtures; never connects to the farm database.
// Run: node frontend/tests/expenses-browser.cjs (Chrome installed, Node 22+).
const fs = require('node:fs');
const path = require('node:path');
const http = require('node:http');
const { spawn } = require('node:child_process');
const assert = require('node:assert/strict');
const sleep = ms => new Promise(resolve => setTimeout(resolve, ms));
const root = path.resolve(__dirname, '..');
const output = path.resolve('target/expense-browser');
fs.mkdirSync(output, { recursive:true });
const profile = fs.mkdtempSync(path.join(output, 'chrome-'));
let chrome, socket, permissions = ['expenses:read', 'expenses:create'], failSave = false, failHistory = false;
const writes = [], reads = [], exceptions = [];
const options = {
    farms:[{ id:1, name:'Trang trại A' }, { id:2, name:'Trang trại B' }],
    seasons:[{ id:11, farmId:1, plotName:'Khu A', cropName:'Lúa', status:'COMPLETED' }, { id:22, farmId:2, plotName:'Khu B', cropName:'Ngô', status:'IN_PROGRESS' }],
    categories:[{ code:'FERTILIZER', name:'Phân bón' }, { code:'LABOR', name:'Nhân công' }]
};
const record = {
    id:1, farmId:1, farmName:'Trang trại A', seasonId:11, cropName:'Lúa', plotName:'Khu A', category:'FERTILIZER',
    amount:'9999999999999999.99', expenseDate:'2026-10-12', description:'<script>unsafe</script>', createdBy:1, createdAt:'2026-10-12T00:00:00Z'
};
const records = Array.from({ length:21 }, (_,index) => ({ ...record, id:index+1 }));
const server = http.createServer(async (req,res) => {
    const url = new URL(req.url, 'http://localhost');
    const reply = (data, status=200) => {
        res.writeHead(status, { 'Content-Type':'application/json; charset=utf-8' });
        res.end(JSON.stringify(status === 200 || status === 201 ? { data, requestId:'fixture' } : { error:{ message:data } }));
    };
    if (url.pathname === '/api/v1/auth/login') return reply({ accessToken:'fixture', user:{ fullName:'Test', systemRoles:['USER'], permissions } });
    if (url.pathname === '/api/v1/auth/me') return reply({ user:{ fullName:'Test', systemRoles:['USER'], permissions } });
    if (url.pathname === '/api/v1/expenses/options') return reply(options);
    if (url.pathname === '/api/v1/expenses' && req.method === 'POST') {
        let body = ''; for await (const chunk of req) body += chunk;
        if (failSave) { failSave = false; return reply('Không thể lưu khoản chi.', 503); }
        const value = JSON.parse(body); writes.push(value);
        records.unshift({ ...record, ...value, id:100+writes.length, farmName:'Trang trại B' });
        return reply(records[0], 201);
    }
    if (url.pathname.startsWith('/api/v1/expenses')) {
        reads.push(url.pathname + url.search);
        if (failHistory) return reply('Không thể tải lịch sử.', 503);
        const filtered = records.filter(item => (!url.searchParams.get('farmId') || String(item.farmId) === url.searchParams.get('farmId'))
            && (!url.searchParams.get('seasonId') || String(item.seasonId) === url.searchParams.get('seasonId'))
            && (!url.searchParams.get('category') || item.category === url.searchParams.get('category')));
        const totalAmount = '9999999999999999.99';
        if (url.pathname.endsWith('/summary')) return reply({ count:filtered.length, totalAmount, currency:'VND',
            byCategory:filtered.length ? [{ category:'FERTILIZER', count:filtered.length, totalAmount }] : [] });
        const page = Number(url.searchParams.get('page') || 0);
        return reply({ items:filtered.slice(page*20,page*20+20), page, size:20, total:filtered.length, totalAmount, currency:'VND' });
    }
    const resource = url.pathname.replace(/^\/(ui|frontend)\//, '');
    const file = path.resolve(root, resource);
    if (!file.startsWith(root + path.sep) || !fs.existsSync(file) || !fs.statSync(file).isFile()) {
        res.writeHead(404); return res.end();
    }
    res.writeHead(200, { 'Content-Type':({ '.html':'text/html', '.js':'text/javascript', '.css':'text/css' })[path.extname(file)] + '; charset=utf-8' });
    fs.createReadStream(file).pipe(res);
});

(async () => {
    await new Promise(resolve => server.listen(0,'127.0.0.1',resolve));
    const origin = `http://127.0.0.1:${server.address().port}`;
    const executable = process.env.CHROME_PATH || ['C:/Program Files/Google/Chrome/Application/chrome.exe', 'C:/Program Files (x86)/Google/Chrome/Application/chrome.exe']
        .find(file => fs.existsSync(file));
    assert.ok(executable, 'Set CHROME_PATH to the Chrome executable');
    chrome = spawn(executable, ['--headless=new', '--remote-debugging-port=0', `--user-data-dir=${profile}`, '--no-first-run', '--no-default-browser-check', 'about:blank'],
        { windowsHide:true, stdio:'ignore' });
    chrome.on('error', error => { throw error; });
    const portFile = path.join(profile, 'DevToolsActivePort');
    for (let attempt=0; attempt<100 && !fs.existsSync(portFile); attempt++) await sleep(100);
    const port = fs.readFileSync(portFile,'utf8').split('\n')[0];
    const pages = await (await fetch(`http://127.0.0.1:${port}/json`)).json();
    socket = new WebSocket(pages.find(page => page.type === 'page').webSocketDebuggerUrl);
    await new Promise((resolve,reject) => { socket.onopen=resolve; socket.onerror=reject; });
    let sequence = 0; const pending = new Map();
    socket.onmessage = event => {
        const message = JSON.parse(event.data);
        if (message.id) {
            const task = pending.get(message.id); pending.delete(message.id);
            message.error ? task.reject(new Error(message.error.message)) : task.resolve(message.result);
        }
        if (message.method === 'Runtime.exceptionThrown') exceptions.push(message.params.exceptionDetails.text);
    };
    const send = (method,params={}) => new Promise((resolve,reject) => {
        const id = ++sequence; pending.set(id,{ resolve,reject }); socket.send(JSON.stringify({ id,method,params }));
    });
    async function evaluate(expression) {
        const result = await send('Runtime.evaluate',{ expression, awaitPromise:true, returnByValue:true });
        if (result.exceptionDetails) throw new Error(result.exceptionDetails.exception?.description || result.exceptionDetails.text);
        return result.result.value;
    }
    async function waitFor(expression) {
        for (let attempt=0; attempt<100; attempt++) { if (await evaluate(expression)) return; await sleep(100); }
        throw new Error('Timed out: ' + expression);
    }
    async function login() {
        await waitFor("!!document.querySelector('.backend-login[open]')");
        await evaluate("const f=document.querySelector('.backend-login form');f.elements.username.value='fixture';f.elements.password.value='fixture';f.requestSubmit()");
        await waitFor("!document.querySelector('.backend-login') && document.getElementById('addExpense') && !document.getElementById('addExpense').hidden");
    }
    async function navigate() { await send('Page.navigate',{ url:origin+'/frontend/expenses.html' }); await login(); }
    await send('Runtime.enable'); await send('Page.enable');
    await send('Emulation.setDeviceMetricsOverride',{ width:1440,height:1000,deviceScaleFactor:1,mobile:false });
    await navigate();
    await waitFor("document.getElementById('expenseRows').children.length===20");
    assert.equal(await evaluate("document.getElementById('totalAmount').textContent"), '9.999.999.999.999.999,99 ₫');
    assert.equal(await evaluate("document.getElementById('expenseRows').querySelectorAll('script').length"), 0);
    await evaluate("document.getElementById('nextPage').click()");
    await waitFor("document.getElementById('expenseRows').children.length===1 && !document.getElementById('previousPage').disabled");
    assert.match(await evaluate("document.getElementById('pageSummary').textContent"), /Trang 2 \/ 2/);
    await evaluate("document.getElementById('farmFilter').value='2';document.getElementById('farmFilter').dispatchEvent(new Event('change'));document.getElementById('seasonFilter').value='22';document.getElementById('filterForm').requestSubmit()");
    await waitFor("!document.getElementById('emptyHistory').hidden");
    assert.ok(reads.some(url => url.includes('farmId=2&seasonId=22')));
    await evaluate("document.getElementById('clearFilters').click()");
    await waitFor("document.getElementById('expenseRows').children.length===20");
    const shot = await send('Page.captureScreenshot',{ format:'png' });
    fs.writeFileSync(path.join(output,'desktop.png'),Buffer.from(shot.data,'base64'));
    await evaluate("document.getElementById('addExpense').click()");
    await waitFor("document.getElementById('expenseDialog').open");
    await evaluate("document.getElementById('expenseFarm').value='2';document.getElementById('expenseFarm').dispatchEvent(new Event('change'))");
    assert.deepEqual(await evaluate("[...document.getElementById('expenseSeason').options].map(o=>o.value)"), ['', '22']);
    await evaluate("document.getElementById('expenseCategory').value='LABOR';document.querySelector('[name=amount]').value='0';document.querySelector('[name=expenseDate]').value='2026-10-12';document.getElementById('expenseForm').requestSubmit()");
    await waitFor("!document.getElementById('formError').hidden"); assert.equal(writes.length,0);
    await evaluate("document.querySelector('[name=amount]').value='9999999999999999.99';document.getElementById('expenseForm').requestSubmit()");
    await waitFor("!document.getElementById('expenseDialog').open && !document.getElementById('saveExpense').disabled");
    assert.equal(writes[0].amount,'9999999999999999.99'); assert.equal(writes[0].seasonId,null); assert.equal(writes[0].farmId,2);
    await evaluate("document.getElementById('addExpense').click()"); await waitFor("document.getElementById('expenseDialog').open");
    await evaluate("document.getElementById('expenseFarm').value='1';document.getElementById('expenseFarm').dispatchEvent(new Event('change'));document.getElementById('expenseSeason').value='11';document.getElementById('expenseCategory').value='FERTILIZER';document.querySelector('[name=amount]').value='1500000.50'");
    failSave=true;
    await evaluate("document.getElementById('expenseForm').requestSubmit()");
    await waitFor("!document.getElementById('formError').hidden && !document.getElementById('saveExpense').disabled");
    assert.equal(await evaluate("document.getElementById('expenseDialog').open"),true);
    failHistory=true;
    await evaluate("document.getElementById('expenseForm').requestSubmit()");
    await waitFor("!document.getElementById('expenseDialog').open && !document.getElementById('expenseError').hidden");
    assert.equal(writes[1].seasonId,11);
    assert.match(await evaluate("document.getElementById('expenseNotice').textContent"),/Đã ghi nhận/);
    failHistory=false;
    await evaluate("document.getElementById('clearFilters').click()");
    await waitFor("document.getElementById('expenseRows').children.length===20");
    await evaluate("document.querySelector('[data-detail]').click()");
    assert.equal(await evaluate("!!document.querySelector('.record-detail[open]')"),true);
    await evaluate("document.querySelector('.record-detail button').click()");
    await send('Emulation.setDeviceMetricsOverride',{ width:390,height:844,deviceScaleFactor:1,mobile:true });
    await sleep(200);
    assert.equal(await evaluate("document.documentElement.scrollWidth<=innerWidth"),true,'No page overflow on mobile');
    const mobile=await send('Page.captureScreenshot',{ format:'png' });
    fs.writeFileSync(path.join(output,'mobile.png'),Buffer.from(mobile.data,'base64'));
    await evaluate("document.getElementById('addExpense').click()"); await waitFor("document.getElementById('expenseDialog').open");
    assert.equal(await evaluate("document.getElementById('expenseDialog').getBoundingClientRect().width<=innerWidth"),true);
    const form=await send('Page.captureScreenshot',{ format:'png' });
    fs.writeFileSync(path.join(output,'mobile-form.png'),Buffer.from(form.data,'base64'));
    permissions=['expenses:create']; await navigate();
    assert.equal(await evaluate("document.getElementById('historyPanel').hidden && document.getElementById('summaryPanel').hidden"),true);
    permissions=['expenses:read'];
    await send('Page.navigate',{ url:origin+'/frontend/expenses.html' });
    await waitFor("!!document.querySelector('.backend-login[open]')");
    await evaluate("const f=document.querySelector('.backend-login form');f.elements.username.value='fixture';f.elements.password.value='fixture';f.requestSubmit()");
    await waitFor("document.getElementById('expenseRows').children.length===20");
    assert.equal(await evaluate("document.getElementById('addExpense').hidden"),true);
    permissions=['expenses:read','expenses:create'];
    await send('Page.navigate',{ url:origin+'/frontend/dashboard.html#expenses' });
    await waitFor("!!document.querySelector('.backend-login[open]')");
    await evaluate("const f=document.querySelector('.backend-login form');f.elements.username.value='fixture';f.elements.password.value='fixture';f.requestSubmit()");
    await waitFor("document.getElementById('managementFrame')?.contentDocument?.getElementById('expenseRows')?.children.length===20");
    assert.equal(await evaluate("document.querySelector('a[href=\"expenses.html\"]').classList.contains('active')"),true);
    assert.equal(await evaluate("!!document.getElementById('managementFrame').contentDocument.querySelector('.backend-login')"),false,'Expense frame reuses the dashboard session');
    assert.deepEqual(exceptions,[]);
    console.log('PASS expense UI: precise money, history, pagination, filters, form, farm/season relationship, save errors, detail, mobile, permissions and Dashboard session');
})().catch(error => { console.error(error); process.exitCode=1; }).finally(async () => {
    socket?.close(); chrome?.kill(); server.close();
});
