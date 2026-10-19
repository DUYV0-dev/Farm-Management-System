package com.farmmanagement.backend.production;

import org.springframework.stereotype.Service;
import org.springframework.security.access.prepost.PreAuthorize;
import java.util.List;

@Service
public class CropProductionService {
    private final CropProductionStore store;

    public CropProductionService(CropProductionStore store) {
        this.store = store;
    }

    @PreAuthorize("hasAuthority('crops:create')")
    public CropProduction createProduction(CropProduction production) {
        return store.create(production);
    }

    @PreAuthorize("hasAuthority('crops:read')")
    public CropProduction getProduction(int id) {
        return store.byId(id).orElseThrow(() -> new IllegalArgumentException("Crop production not found: " + id));
    }

    @PreAuthorize("hasAuthority('crops:read')")
    public List<CropProduction> getProductionsByFarm(int farmId) {
        return store.byFarmId(farmId);
    }

    @PreAuthorize("hasAuthority('crops:update')")
    public CropProduction updateProduction(int id, CropProduction production) {
        store.byId(id).orElseThrow(() -> new IllegalArgumentException("Crop production not found: " + id));
        store.update(id, production);
        return store.byId(id).get();
    }

    @PreAuthorize("hasAuthority('crops:status')")
    public void deleteProduction(int id) {
        store.delete(id);
    }
}
