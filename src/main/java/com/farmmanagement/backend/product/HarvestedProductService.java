package com.farmmanagement.backend.product;

import org.springframework.stereotype.Service;
import org.springframework.security.access.prepost.PreAuthorize;
import java.util.List;

@Service
public class HarvestedProductService {
    private final HarvestedProductStore store;

    public HarvestedProductService(HarvestedProductStore store) {
        this.store = store;
    }

    @PreAuthorize("hasAuthority('crops:create')")
    public HarvestedProduct createProduct(HarvestedProduct product) {
        return store.create(product);
    }

    @PreAuthorize("hasAuthority('crops:read')")
    public HarvestedProduct getProduct(int id) {
        return store.byId(id).orElseThrow(() -> new IllegalArgumentException("Product not found: " + id));
    }

    @PreAuthorize("hasAuthority('crops:read')")
    public List<HarvestedProduct> getProductsByProduction(int productionId) {
        return store.byProductionId(productionId);
    }

    @PreAuthorize("hasAuthority('crops:update')")
    public HarvestedProduct updateProduct(int id, HarvestedProduct product) {
        store.byId(id).orElseThrow(() -> new IllegalArgumentException("Product not found: " + id));
        store.update(id, product);
        return store.byId(id).get();
    }

    @PreAuthorize("hasAuthority('crops:status')")
    public void deleteProduct(int id) {
        store.delete(id);
    }
}
