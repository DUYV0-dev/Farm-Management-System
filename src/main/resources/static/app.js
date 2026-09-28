"use strict";
// Access token stays in memory only. Reloading the page requires login again.
let accessToken = null;
const el = id => document.getElementById(id);
function message(text) { el("message").textContent = text; }
function signedOut() { accessToken=null;el("loginPanel").hidden=false;el("accountPanel").hidden=true; }
async function request(path, options={}) {
  const headers={...options.headers};
  if(accessToken) headers.Authorization="Bearer "+accessToken;
  const res=await fetch("/api/v1/auth/"+path,{...options,headers,cache:"no-store",credentials:"omit"});
  const data=res.status===204?null:await res.json();
  if(!res.ok) {
    if(res.status===401 && path!=="login") signedOut();
    throw new Error((data?.error?.message||"Không thể xử lý yêu cầu.")+" Mã: "+(data?.requestId||"—"));
  }
  return data?.data;
}
el("showPassword").addEventListener("change",e=>{el("password").type=e.target.checked?"text":"password";});
el("loginForm").addEventListener("submit",async e=>{
  e.preventDefault();message("");el("loginButton").disabled=true;
  try {
    const result=await request("login",{method:"POST",headers:{"Content-Type":"application/json"},body:JSON.stringify({username:el("username").value.trim(),password:el("password").value})});
    accessToken=result.accessToken;el("password").value="";el("password").type="password";el("showPassword").checked=false;
    el("welcome").textContent="Xin chào, "+result.user.fullName;
    el("accountName").textContent=result.user.username;el("accountRoles").textContent=result.user.systemRoles.join(", ");
    el("loginPanel").hidden=true;el("accountPanel").hidden=false;message("Đăng nhập thành công.");
  } catch(err) { message(err instanceof TypeError?"Không kết nối được máy chủ. Vui lòng thử lại.":err.message); }
  finally { el("loginButton").disabled=false; }
});
el("checkButton").addEventListener("click",async()=>{try { await request("me");message("Phiên đăng nhập còn hiệu lực."); }catch(err){message(err.message);}});
el("logoutButton").addEventListener("click",async()=>{
  el("logoutButton").disabled=true;
  try { await request("logout",{method:"POST"});signedOut();message("Đã đăng xuất và thu hồi phiên."); }
  catch(err){message(err.message+" Nếu mất kết nối, hãy thử đăng xuất lại.");}
  finally{el("logoutButton").disabled=false;}
});
