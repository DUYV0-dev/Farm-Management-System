package com.farmmanagement.backend.sales;

import com.farmmanagement.backend.common.DomainException;
import java.util.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class CustomerService {
    private final CustomerStore store;

    public CustomerService(CustomerStore store) {
        this.store = store;
    }

    private CustomerStore.Customer customer(int id) {
        return store.customer(id).orElseThrow(DomainException::missing);
    }

    @PreAuthorize("hasAuthority('customers:read')")
    @Transactional(readOnly = true)
    public Map<String, Object> listCustomers(int page, int size, String search, String status, String customerType) {
        if (page < 0 || page > 100000 || size < 1 || size > 100 || search.length() > 100) {
            throw new IllegalArgumentException();
        }
        if (status != null && !status.isBlank() && !Set.of("ACTIVE", "INACTIVE").contains(status)) {
            throw new IllegalArgumentException();
        }
        if (customerType != null && !customerType.isBlank()
            && !Set.of("INDIVIDUAL", "WHOLESALER", "RETAILER", "ENTERPRISE").contains(customerType)) {
            throw new IllegalArgumentException();
        }
        return Map.of(
            "items", store.listCustomers(page * size, size, search.trim(), status, customerType),
            "page", page,
            "size", size,
            "total", store.countCustomers(search.trim(), status, customerType)
        );
    }

    @PreAuthorize("hasAuthority('customers:read')")
    @Transactional(readOnly = true)
    public CustomerStore.Customer getCustomer(int id) {
        return customer(id);
    }

    @PreAuthorize("hasAuthority('customers:create')")
    public CustomerStore.Customer createCustomer(int actor, String name, String contactName,
                                                 String email, String phone, String address,
                                                 String customerType, String taxCode, String notes,
                                                 String requestId) {
        validateCustomerFields(name, email, phone, customerType);
        String type = (customerType == null || customerType.isBlank()) ? "INDIVIDUAL" : customerType.trim();
        int id = store.createCustomer(name.trim(), contactName, email, phone, address, type, taxCode, notes);
        store.auditAction(actor, "CUSTOMER", id, "CREATE", requestId);
        return customer(id);
    }

    @PreAuthorize("hasAuthority('customers:update')")
    public CustomerStore.Customer updateCustomer(int actor, int id, String name, String contactName,
                                                 String email, String phone, String address,
                                                 String customerType, String taxCode, String notes,
                                                 String requestId) {
        customer(id);
        validateCustomerFields(name, email, phone, customerType);
        String type = (customerType == null || customerType.isBlank()) ? "INDIVIDUAL" : customerType.trim();
        store.updateCustomer(id, name.trim(), contactName, email, phone, address, type, taxCode, notes);
        store.auditAction(actor, "CUSTOMER", id, "UPDATE", requestId);
        return customer(id);
    }

    @PreAuthorize("hasAuthority('customers:status')")
    public CustomerStore.Customer changeCustomerStatus(int actor, int id, String status, String requestId) {
        if (!Set.of("ACTIVE", "INACTIVE").contains(status)) throw new IllegalArgumentException();
        var c = customer(id);
        if ("INACTIVE".equals(status) && store.hasOpenOrders(id)) {
            throw DomainException.conflict("Khách hàng đang có đơn bán hàng chưa hoàn tất. Không thể ngừng hoạt động.");
        }
        store.customerStatus(id, status);
        store.auditAction(actor, "CUSTOMER", id, "STATUS_" + status, requestId);
        return customer(id);
    }

    private void validateCustomerFields(String name, String email, String phone, String customerType) {
        if (name == null || name.isBlank() || name.length() > 150) throw new IllegalArgumentException();
        if (customerType != null && !customerType.isBlank()
            && !Set.of("INDIVIDUAL", "WHOLESALER", "RETAILER", "ENTERPRISE").contains(customerType)) {
            throw new IllegalArgumentException();
        }
        if (email != null && !email.isBlank()) {
            String e = email.trim().toLowerCase(Locale.ROOT);
            if (!e.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+") || e.length() > 254) throw new IllegalArgumentException();
        }
        if (phone != null && !phone.isBlank()) {
            String p = phone.trim();
            if (!p.matches("^[0-9+\\-() ]{6,20}$")) throw new IllegalArgumentException();
        }
    }
}
