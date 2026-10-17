// Local isolated test app + Edge/Chrome CDP; no third-party Node packages.
const fs = require('node:fs');
const path = require('node:path');
const assert = require('node:assert/strict');
const sleep = ms => new Promise(resolve => setTimeout(resolve, ms));
(async () => {
  const pages = await (await fetch('http://127.0.0.1:9223/json')).json();
  const ws = new WebSocket(pages.find(p => p.type === 'page').webSocketDebuggerUrl);
  await new Promise((resolve, reject) => { ws.onopen = resolve; ws.onerror = reject; });
  let seq = 0; const pending = new Map(), errors = [];
  ws.onmessage = event => {
    const m = JSON.parse(event.data);
    if (m.id) { const task = pending.get(m.id); pending.delete(m.id); m.error ? task.reject(new Error(m.error.message)) : task.resolve(m.result); }
    if (m.method === 'Runtime.exceptionThrown') errors.push(m.params.exceptionDetails.text);
  };
  function send(method, params = {}) { return new Promise((resolve, reject) => { const id = ++seq; pending.set(id, {resolve, reject}); ws.send(JSON.stringify({id, method, params})); }); }
  async function evaluate(expression) {
    const r = await send('Runtime.evaluate', {expression, awaitPromise: true, returnByValue: true});
    if (r.exceptionDetails) throw new Error(r.exceptionDetails.exception?.description || r.exceptionDetails.text);
    return r.result.value;
  }
  async function wait(expression) { for (let i = 0; i < 100; i++) { if (await evaluate(expression)) return; await sleep(100); } throw new Error('Timed out: ' + expression + '; ' + await evaluate("document.getElementById('message')?.textContent || ''")); }
  const evidence = path.resolve('target/dashboard-evidence'); fs.mkdirSync(evidence, {recursive: true});
  const downloads = path.join(evidence, 'browser-files'); fs.mkdirSync(downloads, {recursive: true});
  await send('Browser.setDownloadBehavior', {behavior: 'allow', downloadPath: downloads});
  await send('Runtime.enable'); await send('Page.enable');
  await send('Emulation.setDeviceMetricsOverride', {width: 1440, height: 1000, deviceScaleFactor: 1, mobile: false});
  await send('Page.navigate', {url: 'http://127.0.0.1:18080/'});
  await wait("document.readyState === 'complete' && !!window.ManagementApi");
  await evaluate("document.getElementById('username').value='dashboard-browser';document.getElementById('password').value='IntegrationPassword123';document.getElementById('loginForm').requestSubmit()");
  await wait("!document.getElementById('accountPanel').hidden");
  await evaluate("document.getElementById('dashboardButton').click()");
  await wait("document.querySelector('iframe')?.contentDocument?.getElementById('farm')?.options.length > 0");
  await evaluate("window.d=document.querySelector('iframe').contentDocument;window.w=document.querySelector('iframe').contentWindow;d.getElementById('filters').elements.fromDate.value='2020-06-01';d.getElementById('filters').elements.toDate.value='2020-06-30';d.getElementById('filters').requestSubmit()");
  await wait("d.getElementById('production').textContent.includes('200') && d.getElementById('seasons').textContent.includes('300')");
  assert.ok(await evaluate("d.getElementById('seasons').textContent.includes('120')"));
  await wait("d.getElementById('inventory').textContent.includes('125.125')");
  assert.ok(await evaluate("d.getElementById('tasks').textContent.includes('Chưa khả dụng')"));
  assert.ok(await evaluate("d.getElementById('expenses').textContent.includes('Chưa khả dụng')"));
  await evaluate("d.getElementById('createReport').click()");
  await wait("d.getElementById('reportInfo').textContent.includes('2 bản ghi')");
  assert.equal(await evaluate("d.getElementById('details').querySelectorAll('tbody tr').length"), 2);
  for (const format of ['xlsx', 'pdf']) {
    await evaluate(`d.getElementById('${format}').click()`);
    let found = false;
    for (let i = 0; i < 100; i++) { if (fs.readdirSync(downloads).some(f => f.endsWith('.' + format))) { found = true; break; } await sleep(100); }
    assert.ok(found, 'Browser download ' + format);
  }
  fs.writeFileSync(path.join(evidence, 'dashboard-desktop.png'), Buffer.from((await send('Page.captureScreenshot', {format: 'png'})).data, 'base64'));
  await send('Emulation.setDeviceMetricsOverride', {width: 390, height: 844, deviceScaleFactor: 1, mobile: true}); await sleep(300);
  assert.ok(await evaluate("document.documentElement.scrollWidth <= window.innerWidth + 1"), 'No outer horizontal overflow');
  fs.writeFileSync(path.join(evidence, 'dashboard-mobile.png'), Buffer.from((await send('Page.captureScreenshot', {format: 'png'})).data, 'base64'));
  assert.deepEqual(errors, []);
  await evaluate("document.getElementById('logoutButton').click()"); await wait("document.getElementById('accountPanel').hidden");
  console.log('PASS dashboard filtering, quantities, cumulative ratio, inventory, unavailable sources, report creation, PDF/Excel downloads, mobile layout, logout and no uncaught exceptions');
  ws.close();
})().catch(error => { console.error(error.message); process.exit(1); });
