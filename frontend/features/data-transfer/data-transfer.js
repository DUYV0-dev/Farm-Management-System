'use strict';
(() => {
    const api=(window.parent!==window?window.parent.ManagementApi:null)||window.ManagementApi;
    if(!api) { location.replace('/#data-transfer');return; }
    const el=id=>document.getElementById(id);
    let capabilities=null,validated=null,busy=false;
    const current=()=>capabilities?.datasets.find(item=>item.key===el('dataset').value);
    function status(message,error=false) {
        el('transferMessage').textContent=message;el('transferMessage').className=error?'transfer-error':'transfer-success';
    }
    function controls() {
        const dataset=current(),file=el('importFile').files[0];
        el('dataset').disabled=busy||!capabilities?.datasets.length;el('format').disabled=busy;
        el('importFile').disabled=busy||!dataset?.canImport;
        el('downloadTemplate').disabled=busy||!dataset?.canImport;el('exportData').disabled=busy||!dataset?.canExport;
        el('validateFile').disabled=busy||!dataset?.canImport||!file;
        el('applyFile').disabled=busy||!validated||validated.file!==file||validated.dataset!==dataset?.key||validated.format!==el('format').value;
    }
    function clearValidation() {
        validated=null;el('importSummary').textContent='';el('validationErrors').hidden=true;el('errorRows').replaceChildren();controls();
    }
    function renderSchema() {
        const dataset=current();el('schemaRows').replaceChildren();
        if(!dataset) return;
        el('modeDescription').textContent=dataset.mode==='set-quantity'
            ?'Tồn kho: nhập sẽ đặt lại số lượng của từng cặp kho/vật tư. Hãy kiểm tra số lượng trước khi lưu.'
            :'Nhập tạo bản ghi mới. Bản ghi trùng tên hoặc trùng toàn bộ nội dung sẽ bị từ chối; dữ liệu hiện có được giữ nguyên.';
        for(const field of dataset.fields) {
            const tr=document.createElement('tr');
            const rule=field.type==='TEXT'?`Văn bản, tối đa ${field.maxLength} ký tự`
                :field.type==='ENUM'?field.values.join(', ')+(field.defaultValue?`; mặc định ${field.defaultValue}`:'')
                :field.type==='DATE'?'Ngày hợp lệ YYYY-MM-DD'
                :`${field.type==='INTEGER'?'Số nguyên':'Số thập phân'} từ ${field.minimum} đến ${field.maximum}`+(field.type==='DECIMAL'?`; tối đa ${field.scale} chữ số thập phân`:'');
            for(const value of [field.name,field.required?'Có':'Không',rule]) {const td=document.createElement('td');td.textContent=value;tr.append(td);}
            el('schemaRows').append(tr);
        }
    }
    async function action(callback) {
        if(busy) return;busy=true;controls();
        try { await callback(); }
        catch(error) {
            validated=null;status(error.message||'Không thể xử lý tệp.',true);
            el('errorRows').replaceChildren();
            for(const issue of error.details||[]) {
                const tr=document.createElement('tr');
                for(const value of [issue.row,issue.field,issue.message]) {const td=document.createElement('td');td.textContent=String(value);tr.append(td);}
                el('errorRows').append(tr);
            }
            el('validationErrors').hidden=!el('errorRows').children.length;
        } finally {busy=false;controls();}
    }
    async function download(template) {
        const dataset=current(),format=el('format').value;
        const result=await api.request(`/api/v1/data-transfer/${dataset.key}/export?format=${format}&template=${template}`,{responseType:'blob'});
        const url=URL.createObjectURL(result.blob),link=document.createElement('a');link.href=url;
        link.download=dataset.key+(template?'-template':'')+'.'+format;document.body.append(link);link.click();link.remove();
        setTimeout(()=>URL.revokeObjectURL(url),10000);
        status(template?'Đã tải tệp mẫu.':`Đã xuất ${result.rows} bản ghi và đối chiếu tệp thành công.`);
    }
    async function upload(validateOnly) {
        const file=el('importFile').files[0],dataset=current(),format=el('format').value;
        if(!file||file.size===0||file.size>capabilities.maxBytes) throw new Error('Chọn tệp có dữ liệu và kích thước tối đa 1 MiB.');
        if(!validateOnly && (!validated||validated.file!==file||validated.dataset!==dataset.key||validated.format!==format))
            throw new Error('Hãy kiểm tra lại tệp trước khi nhập.');
        el('validationErrors').hidden=true;el('errorRows').replaceChildren();
        const result=await api.request(`/api/v1/data-transfer/${dataset.key}/import?format=${format}&validateOnly=${validateOnly}`,{
            method:'POST',headers:{'Content-Type':format==='csv'?'text/csv':'application/json'},body:file
        });
        if(validateOnly) {
            validated={file,dataset:dataset.key,format};
            el('importSummary').textContent=`${result.rowCount} bản ghi hợp lệ. Chưa lưu dữ liệu. Bấm bước 2 để nhập.`;
            status('Kiểm tra dữ liệu thành công.');
        } else {
            validated=null;el('importFile').value='';
            el('importSummary').textContent=`Đã nhập ${result.imported} bản ghi. Dữ liệu đọc lại khớp dữ liệu nhập. SHA-256: ${result.persistedChecksum}`;
            status('Nhập dữ liệu thành công và đã đối chiếu tính nhất quán.');
        }
    }
    el('dataset').addEventListener('change',()=>{clearValidation();renderSchema();});
    el('format').addEventListener('change',clearValidation);
    el('importFile').addEventListener('change',()=>{
        const extension=el('importFile').files[0]?.name.split('.').pop().toLowerCase();
        if(['csv','json'].includes(extension)) el('format').value=extension;clearValidation();
    });
    el('downloadTemplate').onclick=()=>action(()=>download(true));el('exportData').onclick=()=>action(()=>download(false));
    el('validateFile').onclick=()=>action(()=>upload(true));el('applyFile').onclick=()=>action(()=>upload(false));
    action(async()=>{
        capabilities=await api.request('/api/v1/data-transfer');
        for(const dataset of capabilities.datasets) {const option=document.createElement('option');option.value=dataset.key;option.textContent=dataset.label;el('dataset').append(option);}
        renderSchema();status(capabilities.datasets.length?'Chọn loại dữ liệu và định dạng để bắt đầu.':'Bạn chưa có quyền nhập hoặc xuất dữ liệu.');
    });
})();
