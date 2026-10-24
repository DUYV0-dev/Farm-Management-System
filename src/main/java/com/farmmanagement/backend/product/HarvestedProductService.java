package com.farmmanagement.backend.product;

import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class HarvestedProductService {
    private final HarvestedProductStore store;

    public HarvestedProductService(HarvestedProductStore store) {
        this.store = store;
    }

    @PreAuthorize("hasAuthority('harvests:create')")
    public HarvestedProduct createProduct(HarvestedProduct product) {
        return store.create(product);
    }

    @PreAuthorize("hasAuthority('harvests:read')")
    @Transactional(readOnly = true)
    public HarvestedProduct getProduct(int id) {
        return store.byId(id).orElseThrow(() -> new IllegalArgumentException("Product not found: " + id));
    }

    @PreAuthorize("hasAuthority('harvests:read')")
    @Transactional(readOnly = true)
    public List<HarvestedProduct> getProductsByProduction(int productionId) {
        return store.byProductionId(productionId);
    }

    @PreAuthorize("hasAuthority('harvests:create')")
    public HarvestedProduct updateProduct(int id, HarvestedProduct product) {
        store.byId(id).orElseThrow(() -> new IllegalArgumentException("Product not found: " + id));
        store.update(id, product);
        return store.byId(id).get();
    }

    @PreAuthorize("hasAuthority('harvests:create')")
    public void deleteProduct(int id) {
        store.delete(id);
    }
}
