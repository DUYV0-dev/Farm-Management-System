package com.farmmanagement.backend.product;

import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class HarvestedProductService {
    private final HarvestedProductStore store;

    public HarvestedProductService(HarvestedProductStore store) {
        this.store = store;
    }

    public HarvestedProduct createProduct(HarvestedProduct product) {
        return store.create(product);
    }

    public HarvestedProduct getProduct(int id) {
        return store.byId(id).orElseThrow(() -> new IllegalArgumentException("Product not found: " + id));
    }

    public List<HarvestedProduct> getProductsByProduction(int productionId) {
        return store.byProductionId(productionId);
    }

    public HarvestedProduct updateProduct(int id, HarvestedProduct product) {
        store.byId(id).orElseThrow(() -> new IllegalArgumentException("Product not found: " + id));
        store.update(id, product);
        return store.byId(id).get();
    }

    public void deleteProduct(int id) {
        store.delete(id);
    }
}
