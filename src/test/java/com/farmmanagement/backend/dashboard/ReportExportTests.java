package com.farmmanagement.backend.dashboard;

import static com.farmmanagement.backend.dashboard.ReportModels.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.io.*;
import java.nio.file.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;

class ReportExportTests {
    Snapshot snapshot;
    @BeforeEach void setup() {
        var rows=new ArrayList<ProductionRow>();
        for(int i=0;i<60;i++) rows.add(new ProductionRow(i,2,"Mùa vụ Vinh","IN_PROGRESS",3,"Khu đất số một",4,"Lúa",LocalDate.of(2020,6,1),new BigDecimal("12.125"),1,"kg","=HYPERLINK(\"x\")"));
        snapshot=new Snapshot(UUID.randomUUID(),1,"Trang trại Vinh",1,Instant.now(),Instant.now().plusSeconds(86400),new Filters(LocalDate.of(2020,6,1),LocalDate.of(2020,6,30),null,null,null),VERSION,ZONE,rows,List.of(new SeasonRow(2,"Mùa vụ Vinh","IN_PROGRESS","Lúa","Khu đất","kg",new BigDecimal("250"),new BigDecimal("200"),new BigDecimal("300"),new BigDecimal("120.00"),null,2)));
    }
    @Test void spreadsheetKeepsNumbersDatesVietnameseAndUntrustedTextAsText() throws Exception {
        byte[] bytes=new ReportExportService(null,"").xlsx(snapshot);
        try(var wb=new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            var sheet=wb.getSheet("PR01 Chi tiết");assertEquals(61,sheet.getPhysicalNumberOfRows());
            assertEquals("Mùa vụ Vinh",sheet.getRow(1).getCell(1).getStringCellValue());
            assertEquals(CellType.NUMERIC,sheet.getRow(1).getCell(5).getCellType());
            assertEquals(12.125,sheet.getRow(1).getCell(6).getNumericCellValue());
            assertEquals(CellType.STRING,sheet.getRow(1).getCell(8).getCellType());
            assertEquals("=HYPERLINK(\"x\")",sheet.getRow(1).getCell(8).getStringCellValue());
            assertEquals(120,wb.getSheet("PR02 Tổng hợp").getRow(1).getCell(9).getNumericCellValue());
        }
    }
    @Test void missingFontFailsExplicitly() { assertThrows(IOException.class,()->new ReportExportService(null,"missing-font.ttf").pdf(snapshot)); }
    @Test void valuesBeyondExcelPrecisionAlsoHaveExactText() throws Exception {
        var r=new ProductionRow(99,2,"Vụ","IN_PROGRESS",3,"Khu",4,"Lúa",LocalDate.of(2020,6,1),new BigDecimal("9999999999999.999"),1,"kg",null);
        var s=new Snapshot(snapshot.reportId(),1,"Farm",1,snapshot.generatedAt(),snapshot.expiresAt(),snapshot.appliedFilters(),VERSION,ZONE,List.of(r),List.of());
        try(var wb=new XSSFWorkbook(new ByteArrayInputStream(new ReportExportService(null,"").xlsx(s)))) {
            assertEquals("9999999999999.999",wb.getSheet("Giá trị chính xác").getRow(1).getCell(3).getStringCellValue());
            assertEquals(CellType.NUMERIC,wb.getSheet("PR01 Chi tiết").getRow(1).getCell(6).getCellType());
        }
    }
    @Test void pdfHasVietnameseAllRowsRepeatedHeadersAndPageNumbers() throws Exception {
        String font=System.getProperty("reports.test.font","C:/Windows/Fonts/arial.ttf");
        Assumptions.assumeTrue(Files.isRegularFile(Path.of(font)),"Pass -Dreports.test.font with a Vietnamese TTF on non-Windows machines");
        var bytes=new ReportExportService(null,font).pdf(snapshot);
        try(var pdf=Loader.loadPDF(bytes)) {
            assertTrue(pdf.getNumberOfPages()>2);
            var text=new PDFTextStripper().getText(pdf);
            assertTrue(text.contains("Mùa vụ Vinh"));assertTrue(text.contains("Trang 1 /"));
            assertEquals(60,text.split("12.125",-1).length-1);
            assertTrue(text.contains("120.00"));assertTrue(text.contains("PR02"));
        }
        Files.createDirectories(Path.of("target/dashboard-evidence"));Files.write(Path.of("target/dashboard-evidence/production-sample.pdf"),bytes);Files.write(Path.of("target/dashboard-evidence/production-sample.xlsx"),new ReportExportService(null,font).xlsx(snapshot));
    }
}
