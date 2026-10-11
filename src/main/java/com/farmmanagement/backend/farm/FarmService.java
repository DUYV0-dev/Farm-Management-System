package com.farmmanagement.backend.farm;

import com.farmmanagement.backend.common.DomainException;


import java.util.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class FarmService {
    private final FarmStore store;
    public FarmService(FarmStore store) { this.store = store; }

    private FarmStore.Farm farm(int id) { return store.farm(id).orElseThrow(DomainException::missing); }
    private FarmStore.Plot plot(int id) { return store.plot(id).orElseThrow(DomainException::missing); }
    private FarmStore.Crop crop(int id) { return store.crop(id).orElseThrow(DomainException::missing); }
    private FarmStore.Season season(int id) { return store.season(id).orElseThrow(DomainException::missing); }

    private Double parseDouble(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            double d = Double.parseDouble(value);
            if (!Double.isFinite(d) || d <= 0 || d > 9999999
                || new java.math.BigDecimal(value).stripTrailingZeros().scale() > 2) throw new IllegalArgumentException();
            return d;
        } catch (NumberFormatException e) { throw new IllegalArgumentException(); }
    }
    
    private Integer parseInt(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            int i = Integer.parseInt(value);
            if (i <= 0 || i > 9999) throw new IllegalArgumentException();
            return i;
        } catch (NumberFormatException e) { throw new IllegalArgumentException(); }
    }

    // ── Farm ─────────────────────────────────────────────────────────────────
    @PreAuthorize("hasAuthority('farms:read')")
    @Transactional(readOnly = true)
    public Map<String, Object> listFarms(int page, int size, String search) {
        if (page < 0 || page > 100000 || size < 1 || size > 100 || search.length() > 100)
            throw new IllegalArgumentException();
        return Map.of("items", store.listFarms(page * size, size, search),
            "page", page, "size", size, "total", store.countFarms(search));
    }

    @PreAuthorize("hasAuthority('farms:read')")
    @Transactional(readOnly = true)
    public FarmStore.Farm getFarm(int id) { return farm(id); }

    @PreAuthorize("hasAuthority('farms:create')")
    public FarmStore.Farm createFarm(int actor, String name, String address,
                                     String areaHa, String description, String requestId) {
        Double a = parseDouble(areaHa);
        if (a == null) throw new IllegalArgumentException();
        int id = store.createFarm(name, address, a, description);
        store.auditAction(actor, "FARM", id, "CREATE", requestId);
        return farm(id);
    }

    @PreAuthorize("hasAuthority('farms:update')")
    public FarmStore.Farm updateFarm(int actor, int id, String name, String address,
                                     String areaHa, String description, String requestId) {
        farm(id);
        Double a = parseDouble(areaHa);
        if (a == null) throw new IllegalArgumentException();
        store.updateFarm(id, name, address, a, description);
        store.auditAction(actor, "FARM", id, "UPDATE", requestId);
        return farm(id);
    }

    @PreAuthorize("hasAuthority('farms:status')")
    public FarmStore.Farm farmStatus(int actor, int id, String status, String requestId) {
        if (!Set.of("ACTIVE", "INACTIVE").contains(status)) throw new IllegalArgumentException();
        farm(id);
        // Note: The DB trigger fpc_guard handles the check for open seasons!
        store.farmStatus(id, status);
        store.auditAction(actor, "FARM", id, "UPDATE_STATUS", requestId);
        return farm(id);
    }

    // ── Plot ─────────────────────────────────────────────────────────────────
    @PreAuthorize("hasAuthority('plots:read')")
    @Transactional(readOnly = true)
    public Map<String, Object> listPlots(int page, int size, String search, Integer farmId) {
        if (farmId != null && farmId <= 0) throw new IllegalArgumentException();
        if (page < 0 || page > 100000 || size < 1 || size > 100 || search.length() > 100)
            throw new IllegalArgumentException();
        return Map.of("items", store.listPlots(page * size, size, search, farmId),
            "page", page, "size", size, "total", store.countPlots(search, farmId));
    }

    @PreAuthorize("hasAuthority('plots:read')")
    @Transactional(readOnly = true)
    public FarmStore.Plot getPlot(int id) { return plot(id); }

    @PreAuthorize("hasAuthority('plots:create')")
    public FarmStore.Plot createPlot(int actor, int farmId, String name, String areaHa,
                                     String soilType, String location, String description, String requestId) {
        Double a = parseDouble(areaHa);
        if (a == null) throw new IllegalArgumentException();
        // The DB trigger handles the farm status and capacity checks
        int id = store.createPlot(farmId, name, a, soilType, location, description);
        store.auditAction(actor, "PLOT", id, "CREATE", requestId);
        return plot(id);
    }

    @PreAuthorize("hasAuthority('plots:update')")
    public FarmStore.Plot updatePlot(int actor, int id, String name, String areaHa,
                                     String soilType, String location, String description, String requestId) {
        plot(id);
        Double a = parseDouble(areaHa);
        if (a == null) throw new IllegalArgumentException();
        store.updatePlot(id, name, a, soilType, location, description);
        store.auditAction(actor, "PLOT", id, "UPDATE", requestId);
        return plot(id);
    }

    @PreAuthorize("hasAuthority('plots:status')")
    public FarmStore.Plot plotStatus(int actor, int id, String status, String requestId) {
        if (!Set.of("ACTIVE", "INACTIVE").contains(status)) throw new IllegalArgumentException();
        plot(id);
        // The DB trigger handles checking for open seasons
        store.plotStatus(id, status);
        store.auditAction(actor, "PLOT", id, "UPDATE_STATUS", requestId);
        return plot(id);
    }

    // ── Crop Catalog ─────────────────────────────────────────────────────────
    @PreAuthorize("hasAuthority('crops:read')")
    @Transactional(readOnly = true)
    public Map<String, Object> listCrops(int page, int size, String search) {
        if (page < 0 || page > 100000 || size < 1 || size > 100 || search.length() > 100)
            throw new IllegalArgumentException();
        return Map.of("items", store.listCrops(page * size, size, search),
            "page", page, "size", size, "total", store.countCrops(search));
    }

    @PreAuthorize("hasAuthority('crops:read')")
    @Transactional(readOnly = true)
    public FarmStore.Crop getCrop(int id) { return crop(id); }

    @PreAuthorize("hasAuthority('crops:create')")
    public FarmStore.Crop createCrop(int actor, String name, String scientificName, 
                                     String category, String growthDays, String description, String requestId) {
        int id = store.createCrop(name, scientificName, category, parseInt(growthDays), description);
        store.auditAction(actor, "CROP", id, "CREATE", requestId);
        return crop(id);
    }

    @PreAuthorize("hasAuthority('crops:update')")
    public FarmStore.Crop updateCrop(int actor, int id, String name, String scientificName, 
                                     String category, String growthDays, String description, String requestId) {
        crop(id);
        store.updateCrop(id, name, scientificName, category, parseInt(growthDays), description);
        store.auditAction(actor, "CROP", id, "UPDATE", requestId);
        return crop(id);
    }

    @PreAuthorize("hasAuthority('crops:status')")
    public FarmStore.Crop cropStatus(int actor, int id, String status, String requestId) {
        if (!Set.of("ACTIVE", "INACTIVE").contains(status)) throw new IllegalArgumentException();
        crop(id);
        store.cropStatus(id, status);
        store.auditAction(actor, "CROP", id, "UPDATE_STATUS", requestId);
        return crop(id);
    }

    // ── Crop Season ──────────────────────────────────────────────────────────
    @PreAuthorize("hasAuthority('crops:read')") // Using crops:read as it's part of crops logic
    @Transactional(readOnly = true)
    public Map<String, Object> listSeasons(int page, int size, String search, Integer plotId) {
        if (plotId != null && plotId <= 0) throw new IllegalArgumentException();
        if (page < 0 || page > 100000 || size < 1 || size > 100 || search.length() > 100)
            throw new IllegalArgumentException();
        return Map.of("items", store.listSeasons(page * size, size, search, plotId),
            "page", page, "size", size, "total", store.countSeasons(search, plotId));
    }

    @PreAuthorize("hasAuthority('crops:read')")
    @Transactional(readOnly = true)
    public FarmStore.Season getSeason(int id) { return season(id); }

    @PreAuthorize("hasAuthority('crops:create')")
    public FarmStore.Season createSeason(int actor, int plotId, int cropId, String status, String requestId) {
        if (!Set.of("PLANNED", "IN_PROGRESS", "COMPLETED", "CANCELLED").contains(status)) 
            throw new IllegalArgumentException();
        // DB trigger fpc_guard checks if plot and crop are ACTIVE
        int id = store.createSeason(plotId, cropId, status);
        store.auditAction(actor, "CROP_SEASON", id, "CREATE", requestId);
        return season(id);
    }

    @PreAuthorize("hasAuthority('crops:update')")
    public FarmStore.Season updateSeasonStatus(int actor, int id, String status, String requestId) {
        if (!Set.of("PLANNED", "IN_PROGRESS", "COMPLETED", "CANCELLED").contains(status)) 
            throw new IllegalArgumentException();
        season(id);
        store.updateSeasonStatus(id, status);
        store.auditAction(actor, "CROP_SEASON", id, "UPDATE_STATUS", requestId);
        return season(id);
    }
}
