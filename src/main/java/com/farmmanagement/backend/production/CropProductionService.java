package com.farmmanagement.backend.production;

import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
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
    @Transactional(readOnly = true)
    public CropProduction getProduction(int id) {
        return store.byId(id).orElseThrow(() -> new IllegalArgumentException("Crop production not found: " + id));
    }

    @PreAuthorize("hasAuthority('crops:read')")
    @Transactional(readOnly = true)
    public List<CropProduction> getProductionsByFarm(int farmId) {
        return store.byFarmId(farmId);
    }

    @PreAuthorize("hasAuthority('crops:update')")
    public CropProduction updateProduction(int id, CropProduction production) {
        store.byId(id).orElseThrow(() -> new IllegalArgumentException("Crop production not found: " + id));
        store.update(id, production);
        return store.byId(id).get();
    }

    @PreAuthorize("hasAuthority('crops:status') or hasAuthority('crops:update')")
    public void deleteProduction(int id) {
        store.delete(id);
    }
}
