'use strict';
(() => {
  const name=location.pathname.split('/').pop().replace('.html','');
  const page={dashboard:'home',index:'tasks'}[name]||name;
  if(window.parent===window) { location.replace('/#'+(page==='login'?'home':page));return; }
  const api=()=>window.parent.ManagementApi;
  const responsive=document.createElement('style');
  responsive.textContent='.top-nav .nav-container{display:grid!important;grid-template-columns:auto minmax(0,1fr) auto!important;grid-template-rows:auto auto!important;align-items:center!important;gap:8px 18px!important;padding:10px 24px!important;max-width:1600px!important;margin:0 auto!important}.top-nav .nav-brand{grid-column:1!important;grid-row:1!important;display:flex!important;align-items:center!important;gap:12px!important}.top-nav .nav-links{grid-column:2!important;grid-row:1!important;display:flex!important;flex-wrap:nowrap!important;align-items:center!important;gap:4px!important;overflow-x:auto!important;margin:0 10px!important;scrollbar-width:none!important;-ms-overflow-style:none!important}.top-nav .nav-links::-webkit-scrollbar{display:none!important}.top-nav .nav-item{flex-shrink:0!important;white-space:nowrap!important;padding:8px 13px!important;font-size:13.5px!important}.top-nav .nav-actions{grid-column:3!important;grid-row:1!important;display:flex!important;align-items:center!important;gap:12px!important;justify-self:end!important}.top-nav .btn-icon{width:38px!important;height:38px!important;font-size:15px!important}.top-nav .user-profile{padding:3px 12px 3px 4px!important;gap:10px!important}.top-nav .user-profile img{width:32px!important;height:32px!important}.nav-farm-bar,.top-nav .nav-container>div[style*="order:4"],.top-nav .nav-container>div[style*="order: 4"]{grid-column:1 / -1!important;grid-row:2!important;display:flex!important;align-items:center!important;justify-content:space-between!important;gap:14px!important;flex-wrap:wrap!important;padding:7px 16px!important;margin-top:2px!important;background:linear-gradient(90deg,rgba(16,185,129,0.05) 0%,rgba(16,185,129,0.02) 100%)!important;border:1px solid rgba(16,185,129,0.12)!important;border-radius:12px!important;font-size:13px!important;color:var(--text-main)!important}.nav-farm-bar label{display:inline-flex!important;align-items:center!important;gap:8px!important;font-weight:700!important;margin:0!important}.nav-farm-bar select{background:#fff!important;border:1px solid rgba(16,185,129,0.25)!important;border-radius:8px!important;padding:4px 10px!important;font-size:13px!important;font-weight:600!important;cursor:pointer!important}.nav-farm-bar .farm-bar-note,.nav-farm-bar span{display:inline-flex!important;align-items:center!important;gap:6px!important;font-size:12px!important;color:var(--text-muted)!important;background:rgba(255,255,255,0.7)!important;padding:3px 10px!important;border-radius:20px!important;border:1px solid rgba(16,185,129,0.08)!important}.chart-card{min-width:0}.chart-container canvas{max-width:100%;width:100%}@media(max-width:700px){.dashboard-grid,.reports-charts-grid{grid-template-columns:minmax(0,1fr)!important}.main-content{padding:24px 16px}.nav-container{padding-inline:16px}.page-header{flex-wrap:wrap}.report-table-wrapper{padding:20px;overflow-x:auto}.stat-card{padding:16px;gap:8px}.stat-left{gap:8px}.stat-icon{width:36px;height:36px;font-size:18px}.stat-trend{display:none}}';
  document.head.append(responsive);
  window.ManagementApi={has:p=>api()?.has(p),request:(...args)=>api().request(...args),json:(...args)=>api().json(...args),
    download:(...args)=>api().download(...args),profile:()=>api().profile(),navigate:p=>api().navigate(p)};
  document.addEventListener('click',event=>{
    const link=event.target.closest('a');
    if(!link || event.ctrlKey || event.metaKey || event.shiftKey || event.altKey) return;
    if(link.id==='btnLogout') { event.preventDefault();event.stopImmediatePropagation();api().logout();return; }
    const target=link.getAttribute('href');
    if(!target || !/^[\w-]+\.html$/.test(target)) return;
    event.preventDefault();event.stopImmediatePropagation();
    const destination=target.replace('.html','');
    api().navigate({dashboard:'home','advanced-dashboard':'dashboard',index:'tasks',login:'home'}[destination]||destination);
  },true);
  const actions=new Set('downloadReport viewReport editWorker deleteWorker updateTaskStatus editTask viewHistory deleteTask toggleReadStatus resolveNotif deleteNotif downloadArchiveReport viewArchiveReport updateActivityStatusInline openProgressModal openCompletionModal openNotesModal openHistoryModal editActivity deleteActivity'.split(' '));
  for(const [eventName,attribute] of [['click','data-click'],['change','data-change']]) document.addEventListener(eventName,event=>{
    const target=event.target.closest('['+attribute+']');if(!target) return;
    const call=target.getAttribute(attribute).match(/^(\w+)\((.*)\);?$/);
    if(!call || !actions.has(call[1]) || typeof window[call[1]]!=='function') return;
    const tokens=call[2].match(/'(?:\\.|[^'])*'|"(?:\\.|[^"])*"|this\.value|true|false|\d+/g)||[];
    const args=tokens.map(value=>value==='this.value'?target.value:value==='true'?true:value==='false'?false:/^\d+$/.test(value)?Number(value):value.slice(1,-1).replace(/\\(['"\\])/g,'$1'));
    window[call[1]](...args);
  });
  document.addEventListener('DOMContentLoaded',()=>{
    const module=location.hash.slice(1);
    if(module.startsWith('module-')) setTimeout(()=>{
      const feature=module.slice(7),directory=feature==='material'?'inventory':feature==='dashboard'?'advanced-dashboard':feature;
      const main=document.querySelector('main');
      const frame=document.createElement('iframe');frame.title={farm:'Trang trại & Cây trồng',material:'Vật tư & Kho',expenses:'Chi phí','material-usage':'Phân bón & Thuốc BVTV','data-transfer':'Nhập / Xuất dữ liệu',harvest:'Thu hoạch',dashboard:'Dashboard & Báo cáo',purchase:'Nhà cung cấp & Mua hàng',sales:'Khách hàng & Bán hàng',traceability:'Truy xuất nguồn gốc'}[feature]||feature;
      frame.src='/ui/features/'+directory+'/'+(feature==='dashboard'?'advanced-dashboard':feature)+'.html';frame.style.cssText='display:block;width:100%;height:calc(100vh - 210px);min-height:850px;border:0';
      main.replaceChildren(frame);
      document.querySelectorAll('.nav-item').forEach(link=>link.classList.toggle('active',link.getAttribute('href')===feature+'.html'));
    },0);
    const nav=document.querySelector('.nav-container');if(!nav) return;
    const profile=api()?.profile();
    const additions=[['harvest','Thu hoạch',['harvests:read']],['advanced-dashboard','Báo cáo nâng cao',['dashboard:read']],
      ['purchase','Mua hàng',['suppliers:read','purchases:read']],['sales','Bán hàng',['customers:read','sales:read']],
      ['traceability','Truy xuất',['farms:read']]];
    const links=nav.querySelector('.nav-links');
    if(profile?.farm?.onboardingStatus==='READY' && links) for(const [feature,label,permissions] of additions) {
      let link=links.querySelector('a[href="'+feature+'.html"]');
      if(!link) {link=document.createElement('a');link.href=feature+'.html';link.className='nav-item';link.textContent=label;links.append(link);}
      link.hidden=!permissions.some(p=>api().has(p));
      link.classList.toggle('active',module==='module-'+(feature==='advanced-dashboard'?'dashboard':feature));
    }
    if(profile?.farm?.onboardingStatus==='READY' && ['plots:read','plots:create','crops:read','crops:create','materials:read','materials:create','warehouses:read','warehouses:create','inventory:manage','expenses:read','expenses:create','usage:read','usage:create'].some(p=>api().has(p))) {
      const transfer=document.createElement('a');transfer.href='data-transfer.html';transfer.className='nav-item';transfer.textContent='Nhập / Xuất';
      transfer.classList.toggle('active',module==='module-data-transfer');nav.querySelector('.nav-links')?.append(transfer);
    }
    if(profile?.farm) {
      const context=document.createElement('div');
      context.className='nav-farm-bar';
      const label=document.createElement('label');
      label.innerHTML='<i class="fa-solid fa-seedling" style="color:var(--primary);font-size:14px"></i> Trang trại: ';
      const select=document.createElement('select');
      for(const farm of profile.farms) {const option=document.createElement('option');option.value=farm.farmId;option.textContent=farm.farmName;option.selected=farm.farmId===profile.farm.farmId;select.append(option);}
      select.onchange=()=>api().selectFarm(Number(select.value));label.append(select);context.append(label);
      if(!module) {const note=document.createElement('span');note.className='farm-bar-note';note.innerHTML='<i class="fa-solid fa-circle-info" style="color:#059669"></i> Các số liệu và thao tác trên trang này là dữ liệu minh họa của giao diện cũ.';context.append(note);}
      nav.append(context);
    }
    /* Update the user-profile widget with real session data */
    if(profile?.user) {
      const nameEl=nav.querySelector('.user-name');if(nameEl) nameEl.textContent=profile.user.fullName||profile.user.username||'User';
      const roleEl=nav.querySelector('.user-role');if(roleEl) roleEl.textContent=(profile.farm?.roles||[]).join(', ')||profile.user.systemRoles?.join(', ')||'';
    }
    for(const link of document.querySelectorAll('.nav-item')) {
      const destination=link.getAttribute('href');
      const requirements={'farm.html':['farms:read','plots:read','crops:read'],'material.html':['materials:read','warehouses:read'],'expenses.html':['expenses:read','expenses:create'],'material-usage.html':['usage:read','usage:create']};
      if(requirements[destination]) link.hidden=!requirements[destination].some(p=>api().has(p))||profile?.farm?.onboardingStatus!=='READY';
      if(destination==='staff.html') link.hidden=!profile?.farm?.roles.includes('OWNER');
    }
  });
})();
