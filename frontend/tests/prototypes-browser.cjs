// Production navigation uses prototype layouts with the authenticated shell.
const assert=require('node:assert/strict');
const base='http://127.0.0.1:18080';
(async()=>{
 const tabs=await(await fetch('http://127.0.0.1:9223/json')).json();
 const socket=new WebSocket(tabs.find(t=>t.type==='page').webSocketDebuggerUrl);
 await new Promise((resolve,reject)=>{socket.onopen=resolve;socket.onerror=reject;});
 let sequence=0;const pending=new Map(),errors=[];
 socket.onmessage=event=>{const message=JSON.parse(event.data);if(message.id){const task=pending.get(message.id);pending.delete(message.id);message.error?task.reject(Error(message.error.message)):task.resolve(message.result);}if(message.method==='Runtime.exceptionThrown')errors.push(message.params.exceptionDetails.text);if(message.method==='Network.responseReceived'&&message.params.response.status>=400)errors.push(message.params.response.url);};
 const send=(method,params={})=>new Promise((resolve,reject)=>{const id=++sequence;pending.set(id,{resolve,reject});socket.send(JSON.stringify({id,method,params}));});
 const evaluate=async expression=>{
  const result=await send('Runtime.evaluate',{expression,returnByValue:true,awaitPromise:true});
  if(result.exceptionDetails) throw Error(result.exceptionDetails.text);
  return result.result.value;
 };
 await send('Runtime.enable');await send('Page.enable');await send('Network.enable');
 for(const name of ['activities','dashboard','index','login','notifications','reports','schedule','workers']){
  const response=await fetch(base+'/ui/prototypes/'+name+'.html');
  assert.equal(response.status,200,'Packaged prototype missing: '+name);
  const html=await response.text();
  assert.ok(!/DEMO|demo\.js|demo\.css|password123|onclick=|localStorage/i.test(html),'Mock behavior in main interface: '+name);
  for(const match of html.matchAll(/(?:src|href)="([^"]+)"/g)){
   const url=match[1];if(url.startsWith('#')||url.startsWith('/#'))continue;
   const asset=new URL(url,base+'/ui/prototypes/'+name+'.html');
   assert.equal(asset.origin,base,'Assets should use the application origin');
   assert.equal((await fetch(asset)).status,200,'Missing asset: '+asset);
  }
  if(name==='login' || name==='workers') continue;
  const page={dashboard:'home',index:'tasks'}[name]||name;
  await evaluate('ManagementApi.navigate('+JSON.stringify(page)+')');
  let loaded=false;
  for(let retry=0;retry<150;retry++){
   await new Promise(resolve=>setTimeout(resolve,50));
   loaded=await evaluate("location.hash==='#"+page+"' && !!document.querySelector('#managementPanel .page-header') && !document.getElementById('accountPanel').hidden");
   if(loaded) break;
  }
  assert.ok(loaded,'Prototype navigation failed: '+name);
  assert.equal(await evaluate("!!document.querySelector('.demo-banner')"),false);
  if(['activities','index','schedule','notifications'].includes(name)) {
   assert.equal(await evaluate("document.getElementById('managementPanel').textContent.includes('Chức năng chưa sẵn sàng')"),true);
  }
 }
 assert.equal((await fetch(base+'/ui/prototypes/demo.js')).status,401);
 assert.equal((await fetch(base+'/ui/prototypes/js/login.js')).status,401);
 await send('Page.navigate',{url:base+'/ui/prototypes/schedule.html'});
 let redirected=false;
 for(let retry=0;retry<150;retry++){
  await new Promise(resolve=>setTimeout(resolve,50));
  redirected=await evaluate("location.pathname==='/' && location.hash==='#schedule' && !!window.ManagementApi && !document.getElementById('authScreen').hidden");
  if(redirected) break;
 }
 assert.ok(redirected,'Direct prototype link must require login and retain its destination');
 assert.deepEqual(errors.filter(url=>!url.includes('/demo.js')&&!url.includes('/js/login.js')),[]);
 console.log('PASS: packaged prototypes, real navigation, no mock scripts, and direct-link login.');
 socket.close();
})().catch(error=>{console.error(error);process.exit(1);});
