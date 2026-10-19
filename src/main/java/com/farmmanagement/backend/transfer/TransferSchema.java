package com.farmmanagement.backend.transfer;

import java.util.*;

/** Whitelisted transfer contract; SQL identifiers never come from a request. */
public final class TransferSchema {
    private TransferSchema() {}
    public static final int VERSION = 1, MAX_ROWS = 1000, MAX_BYTES = 1024 * 1024;
    public enum Kind { TEXT, INTEGER, DECIMAL, DATE, ENUM }
    public record Field(String name, String column, Kind type, boolean required, int maxLength,
                        int scale, String minimum, String maximum, List<String> values, String defaultValue) {}
    public record Dataset(String key, String label, String table, String idColumn, String scope,
                          String readPermission, String writePermission, String statusPermission,
                          String mode, List<Field> fields) {
        public List<String> columns() {
            var result = new ArrayList<String>(); result.add("sourceId");
            fields.forEach(f -> result.add(f.name())); return result;
        }
    }
    private static Field text(String name, String column, boolean required, int max) {
        return new Field(name,column,Kind.TEXT,required,max,0,null,null,List.of(),null);
    }
    private static Field integer(String name, String column, boolean required, int max) {
        return new Field(name,column,Kind.INTEGER,required,10,0,"1",Integer.toString(max),List.of(),null);
    }
    private static Field decimal(String name, String column, String min, String max, int scale) {
        return new Field(name,column,Kind.DECIMAL,true,40,scale,min,max,List.of(),null);
    }
    private static Field date(String name, String column, boolean required) {
        return new Field(name,column,Kind.DATE,required,10,0,null,null,List.of(),null);
    }
    private static Field choice(String name, String column, String fallback, String... values) {
        return new Field(name,column,Kind.ENUM,fallback==null,30,0,null,null,List.of(values),fallback);
    }
    private static Field status() { return choice("status","status","ACTIVE","ACTIVE","INACTIVE"); }
    private static Field description() { return text("description","description",false,1000); }
    private static Field reference(String name, String column) { return integer(name,column,true,Integer.MAX_VALUE); }
    private static Dataset dataset(String key,String label,String table,String id,String scope,
                                   String read,String write,String status,String mode,Field... fields) {
        return new Dataset(key,label,table,id,scope,read,write,status,mode,List.of(fields));
    }
    public static final List<Dataset> ALL = List.of(
        dataset("plots","Khu đất","plot","plot_id","t.farm_id=?","plots:read","plots:create","plots:status","append",
            text("name","plot_name",true,100),decimal("areaHa","area_ha","0.01","9999999",2),
            text("soilType","soil_type",false,100),text("location","location",false,255),description(),status()),
        dataset("crops","Cây trồng","crop","crop_id","t.tenant_farm_id=?","crops:read","crops:create","crops:status","append",
            text("name","crop_name",true,100),text("scientificName","scientific_name",false,150),text("category","category",false,100),
            integer("growthDays","growth_days",false,9999),description(),status()),
        dataset("seasons","Mùa vụ","crop_season","season_id","EXISTS(SELECT 1 FROM public.plot p WHERE p.plot_id=t.plot_id AND p.farm_id=?)",
            "crops:read","crops:create",null,"append",text("seasonName","season_name",false,100),reference("plotId","plot_id"),
            reference("cropId","crop_id"),date("startDate","start_date",false),date("endDate","end_date",false),description(),
            choice("status","status","PLANNED","PLANNED","IN_PROGRESS","COMPLETED","CANCELLED")),
        dataset("material-categories","Nhóm vật tư","material_category","category_id","t.tenant_farm_id=?",
            "materials:read","materials:create","materials:status","append",text("name","category_name",true,100),description(),status()),
        dataset("materials","Vật tư","material","material_id","t.tenant_farm_id=?","materials:read","materials:create","materials:status","append",
            reference("categoryId","category_id"),text("name","material_name",true,100),text("unit","unit",true,50),
            text("manufacturer","manufacturer",false,150),description(),status()),
        dataset("warehouses","Kho","warehouse","warehouse_id","t.farm_id=?","warehouses:read","warehouses:create","warehouses:status","append",
            text("name","warehouse_name",true,100),text("location","location",false,255),text("capacityText","capacity_text",false,100),status()),
        dataset("inventory","Tồn kho","warehouse_inventory","inventory_id","EXISTS(SELECT 1 FROM public.warehouse w WHERE w.warehouse_id=t.warehouse_id AND w.farm_id=?)",
            "warehouses:read","inventory:manage",null,"set-quantity",reference("warehouseId","warehouse_id"),reference("materialId","material_id"),
            decimal("quantity","quantity","0","999999999.999",3)),
        dataset("expenses","Chi phí","farm_expense","expense_id","t.farm_id=?","expenses:read","expenses:create",null,"append",
            integer("seasonId","season_id",false,Integer.MAX_VALUE),
            choice("category","category",null,"SEEDS","FERTILIZER","PESTICIDES","LABOR","EQUIPMENT","IRRIGATION","TRANSPORT","OTHER"),
            decimal("amount","amount","0.01","9999999999999999.99",2),date("expenseDate","expense_date",true),description()),
        dataset("material-usages","Sử dụng vật tư","material_usage","usage_id","EXISTS(SELECT 1 FROM public.crop_season s JOIN public.plot p USING(plot_id) WHERE s.season_id=t.season_id AND p.farm_id=?)",
            "usage:read","usage:create",null,"append",reference("seasonId","season_id"),reference("materialId","material_id"),
            choice("type","usage_type",null,"FERTILIZER","PESTICIDE"),decimal("quantity","quantity","0.001","999999999999.999",3),
            date("applicationDate","application_date",true),text("notes","notes",false,1000))
    );
    public static Dataset get(String key) {
        return ALL.stream().filter(d -> d.key().equals(key)).findFirst()
            .orElseThrow(() -> TransferException.file("UNSUPPORTED_DATASET","Loại dữ liệu không được hỗ trợ."));
    }
}
