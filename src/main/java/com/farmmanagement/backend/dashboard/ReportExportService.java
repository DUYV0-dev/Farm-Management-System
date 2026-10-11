package com.farmmanagement.backend.dashboard;

import static com.farmmanagement.backend.dashboard.ReportModels.*;
import com.farmmanagement.backend.common.DomainException;
import java.io.*;
import java.math.BigDecimal;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.Semaphore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.*;

@Service
public class ReportExportService {
    private final DashboardService reports;
    private final String fontPath;
    private final Semaphore slot=new Semaphore(1);
    public ReportExportService(DashboardService reports,@Value("${reports.pdf.font:C:/Windows/Fonts/arial.ttf}") String fontPath) { this.reports=reports;this.fontPath=fontPath; }
    @PreAuthorize("hasAuthority('reports:read') and hasAuthority('harvests:read') and hasAuthority('crops:read') and ((#format == 'pdf' and hasAuthority('reports:export:pdf')) or (#format == 'xlsx' and hasAuthority('reports:export:xlsx')))")
    public byte[] export(int actor,int farm,UUID id,String format) {
        var s=reports.snapshot(actor,farm,id);
        if(!slot.tryAcquire()) throw DomainException.conflict("Đang xuất một báo cáo khác; hãy thử lại sau.");
        try { return switch(format) { case "pdf" -> pdf(s);case "xlsx" -> xlsx(s);default -> throw new IllegalArgumentException(); }; }
        catch(IOException e) { throw new DomainException(503,"EXPORT_UNAVAILABLE","Không tạo được file. Kiểm tra cấu hình font PDF hoặc tài nguyên máy chủ."); }
        finally { slot.release(); }
    }
    static List<List<Object>> detailRows(Snapshot s) {
        return s.details().stream().map(r->Arrays.<Object>asList(r.harvestId(),r.seasonName()==null?"#"+r.seasonId():r.seasonName(),r.seasonStatus(),r.plotName(),r.cropName(),r.date(),r.quantity(),r.unit(),r.quality())).toList();
    }
    static List<List<Object>> summaryRows(Snapshot s) {
        return s.summary().stream().map(r->Arrays.<Object>asList(r.seasonId(),r.seasonName(),r.status(),r.cropName(),r.plotName(),r.unit(),r.periodQuantity(),r.cumulativeQuantity(),r.expectedYield(),r.achievementPercent(),r.ratioReason(),r.harvestCount())).toList();
    }
    private static final List<String> DETAIL=List.of("Mã thu hoạch","Mùa vụ","Trạng thái vụ hiện tại","Khu đất","Cây trồng","Ngày thu","Sản lượng","Đơn vị","Chất lượng");
    private static final List<String> SUMMARY=List.of("Mã mùa vụ","Mùa vụ","Trạng thái hiện tại","Cây trồng","Khu đất","Đơn vị","Trong kỳ","Lũy kế đến ngày cuối","Kế hoạch","Đạt kế hoạch (%)","Lý do thiếu tỷ lệ","Số lần thu trong kỳ");
    byte[] xlsx(Snapshot s) throws IOException {
        try(var wb=new XSSFWorkbook();var out=new ByteArrayOutputStream()) {
            var date=wb.createCellStyle();date.setDataFormat(wb.createDataFormat().getFormat("yyyy-mm-dd"));
            var number=wb.createCellStyle();number.setDataFormat(wb.createDataFormat().getFormat("0.000"));
            var meta=wb.createSheet("Thông tin");
            List<List<Object>> metadata=List.of(List.of("Báo cáo",s.reportId().toString()),List.of("Trang trại",s.farmName()),List.of("Ngày tạo",s.generatedAt().toString()),List.of("Từ ngày",s.appliedFilters().fromDate()),List.of("Đến ngày",s.appliedFilters().toDate()),List.of("Bộ lọc",s.appliedFilters().toString()),List.of("Múi giờ",s.timezone()),List.of("Công thức",s.metricVersion()),List.of("Quy tắc","Thu hoạch CONFIRMED; trạng thái vụ hiện tại. Lũy kế đến toDate. Các đơn vị tách riêng."));
            fill(meta,metadata,date,number);
            sheet(wb,"PR01 Chi tiết",DETAIL,detailRows(s),date,number);sheet(wb,"PR02 Tổng hợp",SUMMARY,summaryRows(s),date,number);
            var exact=new ArrayList<List<Object>>();exact.add(List.of("Loại bản ghi","Mã","Trường","Giá trị thập phân chính xác"));
            for(var r:s.details()) precise(exact,"HARVEST",r.harvestId(),"quantity",r.quantity());
            for(var r:s.summary()) {
                precise(exact,"SEASON",r.seasonId(),"periodQuantity",r.periodQuantity());precise(exact,"SEASON",r.seasonId(),"cumulativeQuantity",r.cumulativeQuantity());
                precise(exact,"SEASON",r.seasonId(),"expectedYield",r.expectedYield());precise(exact,"SEASON",r.seasonId(),"achievementPercent",r.achievementPercent());
            }
            fill(wb.createSheet("Giá trị chính xác"),exact,date,number);
            wb.write(out);return out.toByteArray();
        }
    }
    private void precise(List<List<Object>> rows,String type,int id,String field,BigDecimal value) {
        if(value!=null && value.precision()>15) rows.add(List.of(type,id,field,value.toPlainString()));
    }
    private void sheet(XSSFWorkbook wb,String name,List<String> headers,List<List<Object>> rows,CellStyle date,CellStyle number) {
        var sheet=wb.createSheet(name);var all=new ArrayList<List<Object>>();all.add(new ArrayList<Object>(headers));all.addAll(rows);fill(sheet,all,date,number);
        sheet.createFreezePane(0,1);sheet.setRepeatingRows(new org.apache.poi.ss.util.CellRangeAddress(0,0,-1,-1));
        sheet.setAutoFilter(new org.apache.poi.ss.util.CellRangeAddress(0,Math.max(0,rows.size()),0,headers.size()-1));
    }
    private void fill(Sheet sheet,List<List<Object>> rows,CellStyle date,CellStyle number) {
        int index=0;
        for(var values:rows) { var row=sheet.createRow(index++);for(int i=0;i<values.size();i++) {
            var cell=row.createCell(i);var v=values.get(i);
            if(v instanceof LocalDate d) { cell.setCellValue(d);cell.setCellStyle(date); }
            else if(v instanceof BigDecimal d) { cell.setCellValue(d.doubleValue());cell.setCellStyle(number); }
            else if(v instanceof Number n) cell.setCellValue(n.doubleValue());
            else if(v!=null) cell.setCellValue(v.toString()); // Explicit string cells, never formulas.
            sheet.setColumnWidth(i,24*256);
        } }
    }
    byte[] pdf(Snapshot s) throws IOException {
        if(!Files.isRegularFile(Path.of(fontPath))) throw new IOException("PDF font unavailable");
        try(var doc=new PDDocument();var out=new ByteArrayOutputStream()) {
            var font=PDType0Font.load(doc,new File(fontPath));
            pdfTable(doc,font,s,"PR01 Chi tiết thu hoạch",DETAIL,detailRows(s));
            pdfTable(doc,font,s,"PR02 Tổng hợp sản lượng",SUMMARY,summaryRows(s));
            int total=doc.getNumberOfPages();
            for(int i=0;i<total;i++) try(var stream=new PDPageContentStream(doc,doc.getPage(i),PDPageContentStream.AppendMode.APPEND,true,true)) { text(stream,font,10,720,18,"Trang "+(i+1)+" / "+total); }
            doc.save(out);return out.toByteArray();
        }
    }
    private void pdfTable(PDDocument doc,PDFont font,Snapshot s,String title,List<String> headers,List<List<Object>> rows) throws IOException {
        float width=770f/headers.size(),y=0;PDPageContentStream stream=null;
        try {
            // Empty reports still contain titles, metadata and column headings.
            var data=new ArrayList<>(rows);if(data.isEmpty()) data.add(Arrays.asList(new Object[headers.size()]));
            for(var row:data) {
                var wrapped=new ArrayList<List<String>>();int lines=1;
                for(var value:row) { var w=wrap(font,value==null?"":value.toString(),width-6,8);wrapped.add(w);lines=Math.max(lines,w.size()); }
                float height=Math.max(20,lines*11+6);
                if(stream==null || y-height<38) {
                    if(stream!=null) stream.close();
                    var page=new PDPage(new PDRectangle(842,595));doc.addPage(page);stream=new PDPageContentStream(doc,page);
                    float top=568;
                    for(String heading:wrap(font,title+" — "+s.farmName(),770,13)) { text(stream,font,13,35,top,heading);top-=17; }
                    text(stream,font,8,35,top,s.appliedFilters().fromDate()+" đến "+s.appliedFilters().toDate()+" | "+s.timezone()+" | "+s.metricVersion());top-=14;
                    text(stream,font,8,35,top,"Snapshot "+s.reportId()+" | "+s.generatedAt());top-=14;
                    text(stream,font,8,35,top,"Thu hoạch xác nhận; trạng thái vụ hiện tại; lũy kế đến ngày cuối; đơn vị tách riêng.");top-=14;
                    text(stream,font,7,35,top,"Lọc: plotId="+s.appliedFilters().plotId()+", cropId="+s.appliedFilters().cropId()+", seasonId="+s.appliedFilters().seasonId());top-=18;
                    int headerLines=1;
                    for(int i=0;i<headers.size();i++) { var h=wrap(font,headers.get(i),width-6,8);headerLines=Math.max(headerLines,h.size());for(int j=0;j<h.size();j++) text(stream,font,8,35+i*width,top-j*11,h.get(j)); }
                    y=top-10-headerLines*11;
                }
                for(int i=0;i<wrapped.size();i++) for(int j=0;j<wrapped.get(i).size();j++) text(stream,font,8,35+i*width,y-12-j*11,wrapped.get(i).get(j));
                stream.moveTo(35,y-height);stream.lineTo(805,y-height);stream.stroke();y-=height;
            }
        } finally { if(stream!=null) stream.close(); }
    }
    private static List<String> wrap(PDFont font,String text,float width,int size) throws IOException {
        var lines=new ArrayList<String>();var line=new StringBuilder();
        for(int cp:text.codePoints().toArray()) {
            if(Character.isISOControl(cp)) cp=' ';
            String ch=new String(Character.toChars(cp));
            try { font.encode(ch); } catch(IllegalArgumentException e) { throw new IOException("PDF font cannot encode report text",e); }
            if(font.getStringWidth(line+ch)/1000*size>width && !line.isEmpty()) { lines.add(line.toString());line.setLength(0); }
            line.append(ch);
        }
        lines.add(line.toString());return lines;
    }
    private static void text(PDPageContentStream s,PDFont font,int size,float x,float y,String text) throws IOException {
        s.beginText();s.setFont(font,size);s.newLineAtOffset(x,y);s.showText(text);s.endText();
    }
}
