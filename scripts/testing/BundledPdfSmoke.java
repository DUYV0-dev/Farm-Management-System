package com.farmmanagement.backend.dashboard;

import static com.farmmanagement.backend.dashboard.ReportModels.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;

/** Run against the actual runtime image/JAR without a database or OS fonts. */
public class BundledPdfSmoke {
    public static void main(String[] args) throws Exception {
        if(!System.getProperty("os.name").equals("Linux") || System.getProperty("user.name").equals("root"))
            throw new IllegalStateException("Expected Linux and a non-root runtime user");
        var filters=new Filters(LocalDate.of(2026,10,1),LocalDate.of(2026,10,31),null,null,null);
        var row=new ProductionRow(1,1,"Mùa vụ Đắk Lắk","IN_PROGRESS",1,"Khu đất",1,"Lúa",
            LocalDate.of(2026,10,15),new BigDecimal("12.125"),1,"kg","Tốt");
        var snapshot=new Snapshot(UUID.randomUUID(),1,"Trang trại tiếng Việt",1,Instant.now(),
            Instant.now().plusSeconds(3600),filters,VERSION,ZONE,List.of(row),List.of());
        byte[] bytes=new ReportExportService(null,"classpath:fonts/NotoSans-Regular.ttf").pdf(snapshot);
        try(var doc=Loader.loadPDF(bytes)) {
            String text=new PDFTextStripper().getText(doc);
            if(!text.contains("Mùa vụ Đắk Lắk") || !text.contains("Trang trại tiếng Việt") || !text.contains("12.125"))
                throw new IllegalStateException("Vietnamese text or quantity missing from PDF");
            System.out.println("PASS: Linux non-root runtime exported Vietnamese PDF using only the font inside backend.jar; pages="+doc.getNumberOfPages());
        }
    }
}
