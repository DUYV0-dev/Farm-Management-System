package com.farmmanagement.backend.production;

import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class CropProductionService {
    private final CropProductionStore store;

    public CropProductionService(CropProductionStore store) {
        this.store = store;
    }

    public CropProduction createProduction(CropProduction production) {
        return store.create(production);
    }

    public CropProduction getProduction(int id) {
        return store.byId(id).orElseThrow(() -> new IllegalArgumentException("Crop production not found: " + id));
    }

    public List<CropProduction> getProductionsByFarm(int farmId) {
        return store.byFarmId(farmId);
    }

    public CropProduction updateProduction(int id, CropProduction production) {
        store.byId(id).orElseThrow(() -> new IllegalArgumentException("Crop production not found: " + id));
        store.update(id, production);
        return store.byId(id).get();
    }

    public void deleteProduction(int id) {
        store.delete(id);
    }
}
