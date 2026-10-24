package com.farmmanagement.backend.transfer;

import com.farmmanagement.backend.common.DomainException;
import com.farmmanagement.backend.farm.FarmService;
import com.farmmanagement.backend.inventory.InventoryService;
import com.farmmanagement.backend.expense.*;
import com.farmmanagement.backend.usage.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class TransferService {
    private final TransferStore store;
    private final TransferCodec codec;
    private final TransferValidator validator;
    private final FarmService farms;
    private final InventoryService inventory;
    private final FarmExpenseService expenses;
    private final MaterialUsageService usages;
    public TransferService(TransferStore store,TransferCodec codec,TransferValidator validator,FarmService farms,
                           InventoryService inventory,FarmExpenseService expenses,MaterialUsageService usages) {
        this.store=store;this.codec=codec;this.validator=validator;this.farms=farms;
        this.inventory=inventory;this.expenses=expenses;this.usages=usages;
    }
    private boolean has(String permission) {
        var authentication=SecurityContextHolder.getContext().getAuthentication();
        return authentication!=null && authentication.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals(permission));
    }
    private void require(String permission) { if(!has(permission)) throw new AccessDeniedException("Permission required"); }
    public Map<String,Object> capabilities() {
        var datasets=new ArrayList<Map<String,Object>>();
        for(var schema:TransferSchema.ALL) {
            boolean read=has(schema.readPermission()),write=has(schema.writePermission());
            if(read || write) datasets.add(Map.of("key",schema.key(),"label",schema.label(),"canExport",read,"canImport",write,
                "mode",schema.mode(),"fields",schema.fields(),"columns",schema.columns()));
        }
        return Map.of("version",TransferSchema.VERSION,"formats",List.of("csv","json"),"maxBytes",TransferSchema.MAX_BYTES,
            "maxRows",TransferSchema.MAX_ROWS,"datasets",datasets,"farmId",store.currentFarm());
    }
    public record ExportFile(byte[] bytes,String checksum,int rowCount) {}
    public ExportFile export(String dataset,String format,boolean template) {
        var schema=TransferSchema.get(dataset);
        if(template) require(schema.writePermission());else require(schema.readPermission());
        var rows=template?List.<Map<String,String>>of():store.rows(schema,store.currentFarm(),null);
        if(rows.size()>TransferSchema.MAX_ROWS) throw TransferException.file("ROW_LIMIT","Dữ liệu vượt 1.000 dòng. Hãy xuất theo từng tập nhỏ bằng API quản lý hoặc công cụ sao lưu.");
        var validation=validator.validate(schema,rows);
        if(!validation.issues().isEmpty()) throw TransferException.file("EXPORT_VALIDATION","Dữ liệu hiện có không phù hợp với hợp đồng nhập/xuất. Kiểm tra trường dữ liệu trước khi xuất.");
        byte[] bytes=codec.encode(schema,format,validation.rows());
        if(bytes.length>TransferSchema.MAX_BYTES) throw TransferException.file("FILE_SIZE","Tệp xuất vượt 1 MiB; chưa tạo tệp bị cắt bớt.");
        // Round-trip the actual generated file, including CSV escaping/formula protection.
        if(!rows.isEmpty()) {
            var decoded=validator.validate(schema,codec.decode(schema,format,bytes));
            if(!decoded.issues().isEmpty() || !codec.checksum(validation.rows()).equals(codec.checksum(decoded.rows())))
                throw new DomainException(409,"CONSISTENCY_ERROR","Đối chiếu tệp xuất thất bại.");
        }
        return new ExportFile(bytes,codec.checksum(validation.rows()),rows.size());
    }
    public Map<String,Object> importFile(String dataset,String format,byte[] bytes,boolean validateOnly,int actor,String requestId) {
        var schema=TransferSchema.get(dataset);require(schema.writePermission());
        int farm=store.currentFarm();
        var validation=validator.validate(schema,codec.decode(schema,format,bytes));
        if(!validation.issues().isEmpty()) throw new TransferException(validation.issues());
        var rows=validation.rows();var issues=new ArrayList<TransferException.Issue>();
        // Serialize transfer jobs in this farm. Other business writers remain
        // protected by existing unique/FK constraints and database guard triggers.
        if(!validateOnly) store.lockImport(farm);
        var keys=new HashSet<String>();var sourceIds=new HashSet<String>();
        BigDecimal plotArea=BigDecimal.ZERO;
        for(int i=0;i<rows.size();i++) {
            var row=rows.get(i);int number=i+1;
            if("INACTIVE".equals(row.get("status")) && schema.statusPermission()!=null && !has(schema.statusPermission()))
                TransferValidator.issue(issues,number,"status","FORBIDDEN_FIELD","Bạn chưa có quyền nhập trạng thái INACTIVE.");
            String source=row.get("sourceId");
            if(source!=null && !source.isBlank() && !sourceIds.add(source))
                TransferValidator.issue(issues,number,"sourceId","DUPLICATE","sourceId bị trùng trong tệp.");
            String key=row.get("name")!=null?row.get("name").toLowerCase(Locale.ROOT)
                : schema.key().equals("inventory")?row.get("warehouseId")+":"+row.get("materialId"):codec.checksum(List.of(row));
            if(!keys.add(key)) TransferValidator.issue(issues,number,"row","DUPLICATE","Bản ghi bị trùng trong tệp.");
            if(row.get("name")!=null && store.duplicateName(schema,farm,row.get("name")))
                TransferValidator.issue(issues,number,"name","DUPLICATE","Tên đã tồn tại trong trang trại.");
            if(row.get("name")==null && !schema.mode().equals("set-quantity") && store.duplicateValues(schema,farm,row))
                TransferValidator.issue(issues,number,"row","DUPLICATE","Bản ghi có cùng nội dung đã tồn tại.");
            checkReference(issues,row,number,"plotId","plots",farm,true);
            checkReference(issues,row,number,"cropId","crops",farm,true);
            checkReference(issues,row,number,"categoryId","material-categories",farm,true);
            checkReference(issues,row,number,"warehouseId","warehouses",farm,true);
            checkReference(issues,row,number,"materialId","materials",farm,true);
            checkReference(issues,row,number,"seasonId","seasons",farm,schema.key().equals("material-usages"));
            if(schema.key().equals("plots")) plotArea=plotArea.add(new BigDecimal(row.get("areaHa")));
        }
        if(schema.key().equals("plots") && plotArea.compareTo(store.freePlotArea(farm))>0)
            TransferValidator.issue(issues,0,"areaHa","CAPACITY","Tổng diện tích nhập vượt diện tích trang trại còn lại.");
        if(!issues.isEmpty()) throw new TransferException(issues);
        String expected=codec.checksum(rows);
        if(validateOnly) return Map.of("valid",true,"validatedOnly",true,"rowCount",rows.size(),"checksum",expected,
            "mode",schema.mode(),"message","Dữ liệu hợp lệ tại thời điểm kiểm tra; chưa lưu bản ghi nào.");
        var ids=new ArrayList<Integer>();var persisted=new ArrayList<Map<String,String>>();
        for(int i=0;i<rows.size();i++) {
            var row=rows.get(i);
            try {
                int id=create(schema,row,farm,actor,requestId);ids.add(id);
                var saved=store.rows(schema,farm,id);
                if(saved.size()!=1) throw new DomainException(409,"CONSISTENCY_ERROR","Không đọc lại được bản ghi đã nhập.");
                var checked=validator.validate(schema,saved);
                if(!checked.issues().isEmpty()) throw new DomainException(409,"CONSISTENCY_ERROR","Dữ liệu lưu không đúng hợp đồng nhập/xuất.");
                persisted.add(checked.rows().getFirst());
            } catch(DataIntegrityViolationException ex) {
                throw new TransferException(List.of(new TransferException.Issue(i+1,"row","CONFLICT","Dữ liệu trùng hoặc vi phạm ràng buộc nghiệp vụ. Toàn bộ lần nhập được hủy.")));
            } catch(DomainException ex) {
                throw new TransferException(List.of(new TransferException.Issue(i+1,"row",ex.code(),ex.getMessage())));
            }
        }
        String actual=codec.checksum(persisted);
        if(!expected.equals(actual)) throw new DomainException(409,"CONSISTENCY_ERROR","Dữ liệu lưu khác dữ liệu nhập; toàn bộ lần nhập được hủy.");
        return Map.of("imported",rows.size(),"ids",ids,"mode",schema.mode(),"inputChecksum",expected,
            "persistedChecksum",actual,"consistent",true);
    }
    private void checkReference(List<TransferException.Issue> issues,Map<String,String> row,int number,String field,String target,int farm,boolean active) {
        if(row.get(field)!=null && !store.reference(target,Integer.parseInt(row.get(field)),farm,active))
            TransferValidator.issue(issues,number,field,"REFERENCE","Bản ghi liên quan không tồn tại, không khả dụng hoặc không thuộc trang trại đang chọn.");
    }
    private int create(TransferSchema.Dataset schema,Map<String,String> row,int farm,int actor,String requestId) {
        String name=row.get("name"),description=row.get("description");
        int id=switch(schema.key()) {
            case "plots" -> farms.createPlot(actor,farm,name,row.get("areaHa"),row.get("soilType"),row.get("location"),description,requestId).id();
            case "crops" -> farms.createCrop(actor,name,row.get("scientificName"),row.get("category"),row.get("growthDays"),description,requestId).id();
            case "seasons" -> farms.createSeason(actor,row.get("seasonName"),integer(row,"plotId"),integer(row,"cropId"),date(row,"startDate"),date(row,"endDate"),description,row.get("status"),requestId).id();
            case "material-categories" -> inventory.createCategory(actor,name,description,requestId).id();
            case "materials" -> inventory.createMaterial(actor,integer(row,"categoryId"),name,row.get("unit"),row.get("manufacturer"),description,requestId).id();
            case "warehouses" -> inventory.createWarehouse(actor,farm,name,row.get("location"),row.get("capacityText"),requestId).id();
            case "inventory" -> inventory.adjustInventory(actor,integer(row,"warehouseId"),integer(row,"materialId"),row.get("quantity"),requestId).id();
            case "expenses" -> expenses.recordExpense(actor,farm,integer(row,"seasonId"),ExpenseCategory.valueOf(row.get("category")),new BigDecimal(row.get("amount")),date(row,"expenseDate"),description,requestId).id();
            case "material-usages" -> usages.record(actor,integer(row,"seasonId"),integer(row,"materialId"),UsageType.valueOf(row.get("type")),new BigDecimal(row.get("quantity")),date(row,"applicationDate"),row.get("notes"),requestId).id();
            default -> throw new IllegalArgumentException();
        };
        if(schema.statusPermission()!=null && "INACTIVE".equals(row.get("status"))) {
            switch(schema.key()) {
                case "plots" -> farms.plotStatus(actor,id,"INACTIVE",requestId);
                case "crops" -> farms.cropStatus(actor,id,"INACTIVE",requestId);
                case "material-categories" -> inventory.categoryStatus(actor,id,"INACTIVE",requestId);
                case "materials" -> inventory.materialStatus(actor,id,"INACTIVE",requestId);
                case "warehouses" -> inventory.warehouseStatus(actor,id,"INACTIVE",requestId);
                default -> throw new IllegalArgumentException();
            }
        }
        return id;
    }
    private Integer integer(Map<String,String> row,String key) { return row.get(key)==null?null:Integer.valueOf(row.get(key)); }
    private LocalDate date(Map<String,String> row,String key) { return row.get(key)==null?null:LocalDate.parse(row.get(key)); }
}
