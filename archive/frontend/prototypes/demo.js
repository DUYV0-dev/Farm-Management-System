'use strict';
// Preview-only data and charts; this file is intentionally excluded from the JAR.
document.addEventListener('DOMContentLoaded',()=>{
  const banner=document.createElement('div');banner.className='demo-banner';banner.setAttribute('role','note');
  const title=document.createElement('strong');title.textContent='DEMO — Dữ liệu minh họa.';
  banner.append(title,document.createTextNode(' Không sử dụng trang này để vận hành trang trại.'));
  const link=document.createElement('a');link.href='http://127.0.0.1:5500/';link.textContent='Mở ứng dụng thật';banner.append(link);
  document.body.prepend(banner);
});
// Small offline renderer for the fixed charts shown in the archived demos.
// It deliberately does not impersonate the production reporting module.
window.Chart=class DemoChart {
  constructor(context,configuration){this.context=context;this.data=configuration.data;this.render();}
  render(){
    const canvas=this.context.canvas,labels=this.data.labels||[],sets=this.data.datasets||[];
    const width=canvas.parentElement?.clientWidth||600;
    canvas.width=Math.max(width,280);canvas.height=240;
    const ctx=this.context;ctx.clearRect(0,0,canvas.width,canvas.height);ctx.font='12px system-ui';
    const max=Math.max(1,...sets.flatMap(set=>(set.data||[]).map(Number)).filter(Number.isFinite));
    const step=(canvas.width-60)/Math.max(labels.length,1),colors=['#15845b','#0284c7','#d97706','#7c3aed'];
    labels.forEach((label,index)=>{
      sets.forEach((set,s)=>{const value=Number(set.data?.[index]||0),height=value/max*160;
        ctx.fillStyle=Array.isArray(set.backgroundColor)?set.backgroundColor[index]:set.backgroundColor||colors[s%colors.length];
        ctx.fillRect(40+index*step+s*step/Math.max(sets.length,1),195-height,Math.max(3,step/Math.max(sets.length,1)-8),height);
      });
      ctx.fillStyle='#526e60';ctx.fillText(String(label).slice(0,15),40+index*step,218,step-4);
    });
  }
  update(){this.render();}destroy(){this.context.clearRect(0,0,this.context.canvas.width,this.context.canvas.height);}
};
