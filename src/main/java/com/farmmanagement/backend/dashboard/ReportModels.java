package com.farmmanagement.backend.dashboard;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import com.farmmanagement.backend.common.DomainException;

public final class ReportModels {
    private ReportModels() {}
    public static final String VERSION="ADR-v1.0";
    public static final String ZONE="Asia/Ho_Chi_Minh";
    public record Filters(LocalDate fromDate,LocalDate toDate,Integer plotId,Integer cropId,Integer seasonId) {
        public Filters {
            if(fromDate==null || toDate==null || fromDate.isAfter(toDate) || toDate.toEpochDay()-fromDate.toEpochDay()>3660
                || (plotId!=null && plotId<=0) || (cropId!=null && cropId<=0) || (seasonId!=null && seasonId<=0))
                throw new DomainException(400,"INVALID_FILTER","Chọn khoảng ngày hợp lệ, tối đa 10 năm, và mã lọc lớn hơn 0.");
        }
    }
    public record ProductionRow(int harvestId,int seasonId,String seasonName,String seasonStatus,int plotId,String plotName,
        int cropId,String cropName,LocalDate date,BigDecimal quantity,int unitId,String unit,String quality) {}
    public record SeasonRow(int seasonId,String seasonName,String status,String cropName,String plotName,String unit,
        BigDecimal expectedYield,BigDecimal periodQuantity,BigDecimal cumulativeQuantity,BigDecimal achievementPercent,String ratioReason,long harvestCount) {}
    public record Snapshot(UUID reportId,int farmId,String farmName,int userId,Instant generatedAt,Instant expiresAt,
        Filters appliedFilters,String metricVersion,String timezone,List<ProductionRow> details,List<SeasonRow> summary) {}
}
