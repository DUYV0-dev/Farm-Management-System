package com.farmmanagement.backend.transfer;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class TransferValidator {
    public record Validation(List<Map<String,String>> rows, List<TransferException.Issue> issues) {}
    public Validation validate(TransferSchema.Dataset schema,List<Map<String,String>> input) {
        var issues=new ArrayList<TransferException.Issue>(); var rows=new ArrayList<Map<String,String>>();
        for(int i=0;i<input.size();i++) {
            var raw=input.get(i);var row=new LinkedHashMap<String,String>();int number=i+1;
            for(String field:raw.keySet()) if(!schema.columns().contains(field))
                issue(issues,number,field,"UNKNOWN_FIELD","Trường không được hỗ trợ.");
            String source=raw.get("sourceId");
            if(source!=null && !source.isBlank()) {
                try { if(!source.matches("[0-9]+") || Integer.parseInt(source)<=0) throw new IllegalArgumentException(); }
                catch(IllegalArgumentException ex) { issue(issues,number,"sourceId","INTEGER","sourceId phải là số nguyên dương."); }
            }
            row.put("sourceId",source);
            for(var field:schema.fields()) {
                String value=raw.get(field.name());
                if(value!=null) value=value.trim();
                if(value!=null && value.isEmpty()) value=null;
                if(value==null) value=field.defaultValue();
                if(value==null && field.required()) issue(issues,number,field.name(),"REQUIRED","Thiếu trường bắt buộc.");
                if(value!=null) {
                    try { value=normalize(field,value); }
                    catch(IllegalArgumentException ex) {
                        issue(issues,number,field.name(),"INVALID_VALUE",message(field));
                    }
                }
                row.put(field.name(),value);
            }
            if(validDate(row.get("startDate")) && validDate(row.get("endDate"))
                && LocalDate.parse(row.get("startDate")).isAfter(LocalDate.parse(row.get("endDate"))))
                issue(issues,number,"endDate","DATE_RANGE","Ngày kết thúc không được trước ngày bắt đầu.");
            if(validDate(row.get("applicationDate")) && LocalDate.parse(row.get("applicationDate")).isAfter(LocalDate.now(ZoneId.of("Asia/Bangkok"))))
                issue(issues,number,"applicationDate","FUTURE_DATE","Ngày sử dụng không được ở tương lai.");
            rows.add(row);
        }
        return new Validation(rows,issues);
    }
    private boolean validDate(String value) {
        if(value==null) return false;
        try { LocalDate.parse(value);return true; } catch(DateTimeException ex) { return false; }
    }
    private String normalize(TransferSchema.Field field,String value) {
        if(value.length()>field.maxLength() || value.chars().anyMatch(c -> c==0 || (Character.isISOControl(c) && c!='\t' && c!='\r' && c!='\n')))
            throw new IllegalArgumentException();
        return switch(field.type()) {
            case TEXT -> value;
            case ENUM -> { if(!field.values().contains(value)) throw new IllegalArgumentException();yield value; }
            case DATE -> {
                if(!value.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}") || value.startsWith("0000-")) throw new IllegalArgumentException();
                try { yield LocalDate.parse(value).toString(); } catch(DateTimeException ex) { throw new IllegalArgumentException(); }
            }
            case INTEGER,DECIMAL -> {
                if(!value.matches(field.type()==TransferSchema.Kind.INTEGER?"[0-9]+":"[0-9]+(?:\\.[0-9]+)?")) throw new IllegalArgumentException();
                BigDecimal number=new BigDecimal(value).stripTrailingZeros();
                if(number.scale()>field.scale() || number.compareTo(new BigDecimal(field.minimum()))<0
                    || number.compareTo(new BigDecimal(field.maximum()))>0) throw new IllegalArgumentException();
                yield number.toPlainString();
            }
        };
    }
    private String message(TransferSchema.Field field) {
        return switch(field.type()) {
            case TEXT -> "Văn bản tối đa "+field.maxLength()+" ký tự, không chứa ký tự điều khiển không hợp lệ.";
            case INTEGER -> "Cần số nguyên từ "+field.minimum()+" đến "+field.maximum()+".";
            case DECIMAL -> "Cần số từ "+field.minimum()+" đến "+field.maximum()+", tối đa "+field.scale()+" chữ số thập phân.";
            case DATE -> "Ngày phải hợp lệ và có định dạng YYYY-MM-DD, năm từ 0001 đến 9999.";
            case ENUM -> "Chỉ chấp nhận: "+String.join(", ",field.values())+".";
        };
    }
    public static void issue(List<TransferException.Issue> issues,int row,String field,String code,String message) {
        if(issues.size()<250) issues.add(new TransferException.Issue(row,field,code,message));
    }
}
