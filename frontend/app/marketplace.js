'use strict';
let marketplaceTab='mine',marketplaceFarm='',marketplaceQuery='';
async function marketplacePage(){
  const version=navigationVersion,main=el('managementPanel');
  const header=heading('Cửa hàng nông sản','Quản lý sản phẩm của bạn và khám phá nông sản từ các trang trại khác.');
  const publicLink=node('a','Xem cửa hàng công khai ↗');publicLink.href='/shop/';publicLink.target='_blank';publicLink.rel='noopener';header.append(publicLink);
  const tabs=node('div');tabs.className='subtabs marketplace-tabs';
  for(const[value,label]of [['mine','Sản phẩm của tôi'],['discover','Khám phá nông sản']]){const b=button(tabs,label,async()=>{marketplaceTab=value;main.replaceChildren();await marketplacePage();});b.classList.toggle('active',marketplaceTab===value);b.dataset.marketTab=value;}main.append(tabs);
  const toolbar=node('div');toolbar.className='toolbar marketplace-toolbar';toolbar.dataset.mode=marketplaceTab;
  const search=node('input');search.type='search';search.className='search';search.placeholder='Tìm sản phẩm hoặc trang trại…';search.setAttribute('aria-label','Tìm nông sản');search.value=marketplaceQuery;
  const farm=node('select');farm.setAttribute('aria-label','Lọc trang trại');farm.append(new Option('Tất cả trang trại',''));
  const status=node('select');status.setAttribute('aria-label','Trạng thái đăng bán');for(const[v,t]of [['','Tất cả trạng thái'],['published','Đang công khai'],['draft','Chưa công khai']])status.append(new Option(t,v));
  const total=node('small');toolbar.append(search,farm);if(marketplaceTab==='mine')toolbar.append(status);toolbar.append(total);main.append(toolbar);
  const products=node('div');products.className='marketplace-products';products.id='marketplaceProducts';main.append(products);
  const owned=new Set(accountData.farms.map(f=>f.id)),discover=marketplaceTab==='discover';
  let data=accountData.listings;
  if(discover){products.textContent='Đang tải nông sản…';try{const catalog=await request('/api/v1/store/products');if(version!==navigationVersion)return;data=catalog.filter(p=>!owned.has(p.farm_id));}catch(e){if(version===navigationVersion)products.textContent=e.message;return;}}
  const normalized=data.map(p=>discover?{...p,farmId:p.farm_id,farmName:p.farm_name,published:true}:p);
  for(const[id,name]of new Map(normalized.map(p=>[p.farmId,p.farmName])))farm.append(new Option(name,id));
  if([...farm.options].some(o=>o.value===marketplaceFarm))farm.value=marketplaceFarm;
  function render(){products.replaceChildren();const query=search.value.trim().toLocaleLowerCase('vi'),matches=normalized.filter(p=>(!farm.value||String(p.farmId)===farm.value)&&(!status.value||(status.value==='published')===p.published)&&(p.name+' '+p.farmName).toLocaleLowerCase('vi').includes(query));total.textContent=matches.length+' sản phẩm';
    for(const p of matches){const card=node('article'),cover=node('div'),body=node('div');card.className='marketplace-product';cover.className='marketplace-cover';cover.append(landscape());if(!discover)cover.append(badge(p.published?'Đang công khai':'Chưa công khai',p.published?'':'PENDING'));body.className='marketplace-body';body.append(node('small',p.farmName.toLocaleUpperCase('vi')),node('h3',p.name));const description=node('p',p.description||'Nông sản được thu hoạch trực tiếp tại trang trại.');description.className='product-description';body.append(description);const price=node('div');price.className='marketplace-price';price.append(node('strong',Number(p.price)>0?money(p.price):'Chưa đặt giá'),node('small','/ '+(p.unit||'đơn vị')));body.append(price,node('small','Hiện có '+number(p.quantity)+' '+(p.unit||'')));card.append(cover,body);
      if(discover){const link=node('a','Xem & đặt mua ↗');link.href='/shop/?farm='+encodeURIComponent(p.farmId);link.target='_blank';link.rel='noopener';link.className='primary';card.append(link);}else button(card,'Thiết lập đăng bán →',()=>editListing(p));products.append(card);}
    if(!matches.length){const placeholder=node('div');placeholder.className='empty marketplace-empty';placeholder.append(node('h3',normalized.length?'Chưa có sản phẩm phù hợp':discover?'Chưa có nông sản từ trang trại khác':'Bạn chưa có nông sản để đăng bán'),node('p',normalized.length?'Thử thay đổi bộ lọc hoặc từ khóa.':discover?'Sản phẩm sẽ xuất hiện khi các trang trại công khai đăng bán.':'Chọn trang trại, ghi nhận thu hoạch rồi đặt giá và công khai sản phẩm tại đây.'));if(!discover&&!normalized.length)button(placeholder,'Đi đến trang trại →',()=>goAccount('farms'));products.append(placeholder);}
  }
  search.oninput=()=>{marketplaceQuery=search.value;render();};farm.onchange=()=>{marketplaceFarm=farm.value;render();};status.onchange=render;render();
}
el('marketplaceButton').onclick=()=>navigate('shop');
