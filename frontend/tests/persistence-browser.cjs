const assert=require('node:assert/strict'),fs=require('node:fs');
const delay=ms=>new Promise(resolve=>setTimeout(resolve,ms));
(async()=>{
 const state=JSON.parse(fs.readFileSync(process.env.PERSISTENCE_STATE_FILE||'target/production-browser-state.json','utf8'));
 const tabs=await(await fetch('http://127.0.0.1:9223/json')).json();
 const socket=new WebSocket(tabs.find(t=>t.type==='page').webSocketDebuggerUrl);
 await new Promise((resolve,reject)=>{socket.onopen=resolve;socket.onerror=reject;});
 let sequence=0;const pending=new Map();
 socket.onmessage=e=>{const m=JSON.parse(e.data);if(m.id){const p=pending.get(m.id);pending.delete(m.id);m.error?p.reject(Error(m.error.message)):p.resolve(m.result);}};
 const send=(method,params={})=>new Promise((resolve,reject)=>{const id=++sequence;pending.set(id,{resolve,reject});socket.send(JSON.stringify({id,method,params}));});
 async function evaluate(expression){const r=await send('Runtime.evaluate',{expression,awaitPromise:true,returnByValue:true});if(r.exceptionDetails)throw Error(r.exceptionDetails.exception?.description||r.exceptionDetails.text);return r.result.value;}
 async function until(expression){for(let i=0;i<120;i++){if(await evaluate(expression))return;await delay(100);}throw Error('Persistence verification timed out: '+expression);}
 try{
  await send('Page.enable');await send('Page.navigate',{url:'http://127.0.0.1:18080/'});await until('!!window.ManagementApi');
  assert.equal(await evaluate('ManagementApi.profile().user'),null,'A new page must not authenticate without the backend');
  await evaluate(`ManagementApi.authenticate(${JSON.stringify({username:state.username,password:'ProductionBrowser123'})})`);
  await evaluate(`ManagementApi.selectFarm(${state.farmId},'tasks')`);
  await until(`document.getElementById('featureFrame').contentDocument?.querySelector('table')?.textContent.includes(${JSON.stringify(state.taskTitle)})`);
  assert.equal(await evaluate('localStorage.length'),0,'Business data must not be restored from browser storage');
  console.log('PASS: after backend and PostgreSQL restart, a fresh login loads the UI-created task from PostgreSQL');
 }finally{socket.close();}
})().catch(error=>{console.error(error);process.exitCode=1;});
