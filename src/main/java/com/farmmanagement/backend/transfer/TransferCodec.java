package com.farmmanagement.backend.transfer;

import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.security.*;
import java.util.*;
import org.springframework.stereotype.Component;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.databind.*;
import tools.jackson.databind.json.JsonMapper;

/** Strict UTF-8 CSV/JSON with bounded input and duplicate JSON key detection. */
@Component
public class TransferCodec {
    private final ObjectMapper mapper = JsonMapper.builder(JsonFactory.builder()
        .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build()).build();
    public List<Map<String,String>> decode(TransferSchema.Dataset schema,String format,byte[] bytes) {
        if(bytes.length==0 || bytes.length>TransferSchema.MAX_BYTES)
            throw TransferException.file("FILE_SIZE","Tệp phải có dữ liệu và tối đa 1 MiB.");
        String content;
        try { content=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString(); }
        catch(CharacterCodingException ex) { throw TransferException.file("ENCODING","Tệp phải dùng UTF-8."); }
        if(content.startsWith("\uFEFF")) content=content.substring(1);
        List<Map<String,String>> rows=switch(format) {
            case "csv" -> csv(schema,content);
            case "json" -> json(schema,content);
            default -> throw TransferException.file("UNSUPPORTED_FORMAT","Chỉ hỗ trợ CSV và JSON.");
        };
        if(rows.isEmpty() || rows.size()>TransferSchema.MAX_ROWS)
            throw TransferException.file("ROW_LIMIT","Mỗi lần nhập cần từ 1 đến 1.000 bản ghi.");
        return rows;
    }
    private List<Map<String,String>> json(TransferSchema.Dataset schema,String content) {
        try {
            JsonNode root=mapper.readTree(content);
            if(root==null || !root.isObject() || !root.path("version").isIntegralNumber() || root.path("version").asInt()!=TransferSchema.VERSION
                || !root.path("dataset").isTextual() || !root.path("dataset").asText().equals(schema.key()) || !root.path("rows").isArray())
                throw TransferException.file("JSON_SCHEMA","JSON cần version=1, dataset đúng loại dữ liệu và mảng rows.");
            if(!Set.of("version","dataset","rows").containsAll(root.propertyNames()))
                throw TransferException.file("UNKNOWN_FIELD","JSON chứa trường không được hỗ trợ.");
            var rows=new ArrayList<Map<String,String>>();
            for(JsonNode node:root.get("rows")) {
                if(!node.isObject()) throw TransferException.file("JSON_SCHEMA","Mỗi bản ghi phải là một object.");
                var row=new LinkedHashMap<String,String>();
                for(var entry:node.properties()) {
                    JsonNode value=entry.getValue();
                    boolean text=schema.fields().stream().anyMatch(f -> f.name().equals(entry.getKey()) && f.type()==TransferSchema.Kind.TEXT);
                    if(!value.isNull() && ((!value.isTextual() && !value.isNumber()) || (text && !value.isTextual())))
                        throw new TransferException(List.of(new TransferException.Issue(rows.size()+1,entry.getKey(),"TYPE","Giá trị phải là chuỗi, số hoặc null.")));
                    row.put(entry.getKey(),value.isNull()?null:value.asText());
                }
                rows.add(row);
                if(rows.size()>TransferSchema.MAX_ROWS) throw TransferException.file("ROW_LIMIT","Tối đa 1.000 bản ghi.");
            }
            return rows;
        } catch(TransferException ex) { throw ex; }
        catch(RuntimeException ex) { throw TransferException.file("MALFORMED_JSON","JSON không hợp lệ hoặc có khóa trùng."); }
    }
    private List<Map<String,String>> csv(TransferSchema.Dataset schema,String content) {
        var records=parseCsv(content);
        if(records.isEmpty()) throw TransferException.file("CSV_HEADER","Thiếu dòng tiêu đề CSV.");
        var headers=records.getFirst();
        if(headers.stream().anyMatch(String::isBlank) || new HashSet<>(headers).size()!=headers.size())
            throw TransferException.file("CSV_HEADER","Tên cột không được trống hoặc trùng nhau.");
        var allowed=new ArrayList<>(schema.columns());allowed.add("_textEncoding");
        if(!allowed.containsAll(headers)) throw TransferException.file("UNKNOWN_FIELD","CSV chứa cột không được hỗ trợ.");
        var rows=new ArrayList<Map<String,String>>();
        for(int i=1;i<records.size();i++) {
            var record=records.get(i);
            if(record.size()!=headers.size()) throw new TransferException(List.of(new TransferException.Issue(i,"file","CSV_COLUMNS","Số ô không khớp số cột tiêu đề.")));
            var row=new LinkedHashMap<String,String>();
            for(int j=0;j<headers.size();j++) row.put(headers.get(j),record.get(j).isEmpty()?null:record.get(j));
            String encoding=row.remove("_textEncoding");
            if(encoding!=null) {
                if(!encoding.equals("apostrophe-v1")) throw TransferException.file("CSV_ENCODING","_textEncoding không được hỗ trợ.");
                for(var field:schema.fields()) if(field.type()==TransferSchema.Kind.TEXT) {
                    String value=row.get(field.name());
                    if(value!=null && !value.startsWith("'")) throw TransferException.file("CSV_ENCODING","Ô văn bản xuất từ hệ thống đã mất tiền tố bảo vệ.");
                    if(value!=null) row.put(field.name(),value.substring(1));
                }
            }
            rows.add(row);
        }
        return rows;
    }
    static List<List<String>> parseCsv(String content) {
        var records=new ArrayList<List<String>>(); var row=new ArrayList<String>(); var cell=new StringBuilder();
        boolean quoted=false,closed=false,started=false;
        for(int i=0;i<content.length();i++) {
            char ch=content.charAt(i); started=true;
            if(quoted) {
                if(ch=='"') {
                    if(i+1<content.length() && content.charAt(i+1)=='"') { cell.append('"');i++; }
                    else { quoted=false;closed=true; }
                } else cell.append(ch);
            } else if(ch==',' || ch=='\n' || ch=='\r') {
                row.add(cell.toString());cell.setLength(0);closed=false;
                if(ch!=',') {
                    records.add(List.copyOf(row));row.clear();started=false;
                    if(ch=='\r' && i+1<content.length() && content.charAt(i+1)=='\n') i++;
                    if(records.size()>TransferSchema.MAX_ROWS+1) throw TransferException.file("ROW_LIMIT","Tối đa 1.000 bản ghi.");
                }
            } else if(ch=='"' && cell.isEmpty() && !closed) quoted=true;
            else {
                if(closed || ch=='"') throw TransferException.file("MALFORMED_CSV","Dấu ngoặc kép CSV không hợp lệ.");
                cell.append(ch);
            }
        }
        if(quoted) throw TransferException.file("MALFORMED_CSV","Chuỗi CSV chưa đóng dấu ngoặc kép.");
        if(started || !row.isEmpty()) { row.add(cell.toString());records.add(List.copyOf(row)); }
        return records;
    }
    public byte[] encode(TransferSchema.Dataset schema,String format,List<Map<String,String>> rows) {
        if(format.equals("json")) return mapper.writeValueAsBytes(Map.of("version",TransferSchema.VERSION,"dataset",schema.key(),"rows",rows));
        if(!format.equals("csv")) throw TransferException.file("UNSUPPORTED_FORMAT","Chỉ hỗ trợ CSV và JSON.");
        var result=new StringBuilder("\uFEFF");
        result.append(String.join(",",schema.columns())).append(",_textEncoding\r\n");
        for(var row:rows) {
            var cells=new ArrayList<String>();
            for(String column:schema.columns()) {
                String value=row.get(column);
                // Reversible spreadsheet formula protection: prefix every nonempty
                // text cell with apostrophe; importer removes exactly one prefix.
                boolean text=schema.fields().stream().anyMatch(f -> f.name().equals(column) && f.type()==TransferSchema.Kind.TEXT);
                if(text && value!=null && !value.isEmpty()) value="'"+value;
                cells.add("\""+(value==null?"":value.replace("\"","\"\""))+"\"");
            }
            cells.add("\"apostrophe-v1\"");
            result.append(String.join(",",cells)).append("\r\n");
        }
        return result.toString().getBytes(StandardCharsets.UTF_8);
    }
    public String checksum(List<Map<String,String>> rows) {
        try {
            var canonical=new ArrayList<Map<String,String>>();
            for(var row:rows) { var sorted=new TreeMap<>(row);sorted.remove("sourceId");canonical.add(sorted); }
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(mapper.writeValueAsBytes(canonical)));
        } catch(NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }
}
