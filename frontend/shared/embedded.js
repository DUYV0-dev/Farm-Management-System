'use strict';
(()=>{
  const embedded=window.parent!==window;
  if(embedded)document.body.classList.add('workspace-embedded');
  window.addEventListener('message',event=>{
    if(event.origin!==location.origin||event.data?.type!=='agrimanage:height')return;
    const frame=[...document.querySelectorAll('iframe')].find(f=>f.contentWindow===event.source);
    const height=Number(event.data.height);
    if(frame&&Number.isFinite(height)&&height>0&&height<200000&&Math.abs(frame.getBoundingClientRect().height-height)>2){frame.style.height=Math.ceil(height)+'px';frame.style.minHeight='0';}
  });
  if(!embedded)return;
  let pending=false,last=0;
  function sync(){pending=false;const height=Math.ceil(document.body.getBoundingClientRect().height+4);if(height>0&&Math.abs(height-last)>2){last=height;window.parent.postMessage({type:'agrimanage:height',height},location.origin);}}
  const schedule=()=>{if(!pending){pending=true;requestAnimationFrame(sync);}};
  new ResizeObserver(schedule).observe(document.body);
  new MutationObserver(schedule).observe(document.body,{subtree:true,childList:true,characterData:true});
  window.addEventListener('load',schedule);window.addEventListener('resize',schedule);document.fonts?.ready.then(schedule);schedule();
  function modalPosition(){let view=window,top=0;try{while(view!==view.top){top+=view.frameElement.getBoundingClientRect().top;view=view.parent;}document.body.style.setProperty('--host-height',view.innerHeight+'px');document.body.style.setProperty('--host-offset',Math.max(16,24-top)+'px');}catch{}}
  document.addEventListener('click',modalPosition,true);document.addEventListener('focusin',modalPosition);modalPosition();
})();
