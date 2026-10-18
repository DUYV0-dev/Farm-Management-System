package com.farmmanagement.backend.sales;

import com.farmmanagement.backend.common.DomainException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class SalesOrderService {
    private final SalesOrderStore store;
    private final CustomerStore customerStore;

    public SalesOrderService(SalesOrderStore store, CustomerStore customerStore) {
        this.store = store;
        this.customerStore = customerStore;
    }

    public record ItemInput(int productId, BigDecimal quantity, BigDecimal unitPrice, String notes) {}

    private SalesOrderStore.SalesOrder salesOrder(int id) {
        return store.salesOrder(id).orElseThrow(DomainException::missing);
    }

    private CustomerStore.Customer customer(int id) {
        return customerStore.customer(id).orElseThrow(DomainException::missing);
    }

    @PreAuthorize("hasAuthority('sales:read')")
    @Transactional(readOnly = true)
    public Map<String, Object> listSalesOrders(int page, int size, Integer customerId, Integer farmId,
                                               String status, String paymentStatus,
                                               LocalDate fromDate, LocalDate toDate) {
        if (page < 0 || page > 100000 || size < 1 || size > 100) throw new IllegalArgumentException();
        if (fromDate != null && toDate != null && fromDate.isAfter(toDate)) throw new IllegalArgumentException();
        if (status != null && !status.isBlank()
            && !Set.of("PENDING", "CONFIRMED", "SHIPPED", "DELIVERED", "CANCELLED").contains(status)) {
            throw new IllegalArgumentException();
        }
        if (paymentStatus != null && !paymentStatus.isBlank()
            && !Set.of("UNPAID", "PARTIALLY_PAID", "PAID").contains(paymentStatus)) {
            throw new IllegalArgumentException();
        }

        return Map.of(
            "items", store.listSalesOrders(page * size, size, customerId, farmId, status, paymentStatus, fromDate, toDate),
            "page", page,
            "size", size,
            "total", store.countSalesOrders(customerId, farmId, status, paymentStatus, fromDate, toDate)
        );
    }

    @PreAuthorize("hasAuthority('sales:read')")
    @Transactional(readOnly = true)
    public SalesOrderStore.SalesOrder getSalesOrder(int id) {
        return salesOrder(id);
    }

    @PreAuthorize("hasAuthority('sales:create')")
    public SalesOrderStore.SalesOrder createSalesOrder(int actor, String customSoNumber,
                                                       int customerId, Integer farmId,
                                                       LocalDate orderDate, LocalDate deliveryDate,
                                                       String initialStatus, String paymentStatus,
                                                       String deliveryAddress, String notes,
                                                       List<ItemInput> items, String requestId) {
        var cust = customer(customerId);
        if (!"ACTIVE".equals(cust.status())) {
            throw DomainException.conflict("Khách hàng đã ngừng hoạt động.");
        }
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("Đơn bán hàng phải có ít nhất 1 nông sản.");
        }

        String status = (initialStatus != null && !initialStatus.isBlank()) ? initialStatus.trim() : "PENDING";
        if (!Set.of("PENDING", "CONFIRMED").contains(status)) {
            throw new IllegalArgumentException("Trạng thái khởi tạo đơn hàng chỉ có thể là PENDING hoặc CONFIRMED.");
        }

        String payStatus = (paymentStatus != null && !paymentStatus.isBlank()) ? paymentStatus.trim() : "UNPAID";
        if (!Set.of("UNPAID", "PARTIALLY_PAID", "PAID").contains(payStatus)) {
            throw new IllegalArgumentException();
        }

        LocalDate date = orderDate != null ? orderDate : LocalDate.now();
        String soNumber = (customSoNumber != null && !customSoNumber.isBlank())
            ? customSoNumber.trim()
            : generateSoNumber(date);

        BigDecimal totalAmount = BigDecimal.ZERO;
        for (var item : items) {
            validateItem(item);
            BigDecimal lineTotal = item.quantity().multiply(item.unitPrice()).setScale(2, RoundingMode.HALF_UP);
            totalAmount = totalAmount.add(lineTotal);
        }

        // If order starts as CONFIRMED, check stock and deduct product availability immediately
        if ("CONFIRMED".equals(status)) {
            deductStockForItems(items);
        }

        int soId = store.createSalesOrder(soNumber, customerId, farmId, date, deliveryDate,
            status, payStatus, totalAmount, deliveryAddress, notes, actor);

        for (var item : items) {
            BigDecimal lineTotal = item.quantity().multiply(item.unitPrice()).setScale(2, RoundingMode.HALF_UP);
            store.createSalesOrderItem(soId, item.productId(), item.quantity(), item.unitPrice(), lineTotal, item.notes());
        }

        store.auditAction(actor, "SALES_ORDER", soId, "CREATE", requestId);
        return salesOrder(soId);
    }

    @PreAuthorize("hasAuthority('sales:update')")
    public SalesOrderStore.SalesOrder updateSalesOrder(int actor, int id, LocalDate deliveryDate,
                                                       String paymentStatus, String deliveryAddress,
                                                       String notes, List<ItemInput> items, String requestId) {
        store.lockSalesOrder(id);
        var so = salesOrder(id);
        if (!"PENDING".equals(so.status())) {
            throw DomainException.conflict("Chỉ có thể chỉnh sửa đơn bán hàng khi ở trạng thái PENDING.");
        }

        String payStatus = (paymentStatus != null && !paymentStatus.isBlank()) ? paymentStatus.trim() : so.paymentStatus();
        if (!Set.of("UNPAID", "PARTIALLY_PAID", "PAID").contains(payStatus)) {
            throw new IllegalArgumentException();
        }

        BigDecimal totalAmount = so.totalAmount();
        if (items != null && !items.isEmpty()) {
            store.deleteSalesOrderItems(id);
            totalAmount = BigDecimal.ZERO;
            for (var item : items) {
                validateItem(item);
                BigDecimal lineTotal = item.quantity().multiply(item.unitPrice()).setScale(2, RoundingMode.HALF_UP);
                totalAmount = totalAmount.add(lineTotal);
                store.createSalesOrderItem(id, item.productId(), item.quantity(), item.unitPrice(), lineTotal, item.notes());
            }
        }

        store.updateSalesOrder(id, deliveryDate, payStatus, totalAmount, deliveryAddress, notes);
        store.auditAction(actor, "SALES_ORDER", id, "UPDATE", requestId);
        return salesOrder(id);
    }

    @PreAuthorize("hasAuthority('sales:status')")
    public SalesOrderStore.SalesOrder changeSalesOrderStatus(int actor, int id, String newStatus, String requestId) {
        if (!Set.of("CONFIRMED", "SHIPPED", "DELIVERED", "CANCELLED").contains(newStatus)) {
            throw new IllegalArgumentException();
        }
        store.lockSalesOrder(id);
        var so = salesOrder(id);
        String currentStatus = so.status();

        if (currentStatus.equals(newStatus)) {
            return so;
        }
        if ("DELIVERED".equals(currentStatus)) {
            throw DomainException.conflict("Đơn hàng đã giao thành công, không thể thay đổi trạng thái.");
        }
        if ("CANCELLED".equals(currentStatus)) {
            throw DomainException.conflict("Đơn hàng đã bị hủy, không thể thay đổi trạng thái.");
        }

        // State Machine validation
        switch (newStatus) {
            case "CONFIRMED" -> {
                if (!"PENDING".equals(currentStatus)) {
                    throw DomainException.conflict("Chỉ đơn PENDING mới có thể xác nhận (CONFIRMED).");
                }
                // Check stock and deduct product availability
                List<ItemInput> inputs = so.items().stream()
                    .map(it -> new ItemInput(it.productId(), it.quantity(), it.unitPrice(), it.notes()))
                    .toList();
                deductStockForItems(inputs);
            }
            case "SHIPPED" -> {
                if (!"CONFIRMED".equals(currentStatus)) {
                    throw DomainException.conflict("Đơn hàng cần được xác nhận (CONFIRMED) trước khi giao hàng (SHIPPED).");
                }
            }
            case "DELIVERED" -> {
                if (!"SHIPPED".equals(currentStatus) && !"CONFIRMED".equals(currentStatus)) {
                    throw DomainException.conflict("Đơn hàng phải ở trạng thái CONFIRMED hoặc SHIPPED trước khi hoàn tất giao hàng.");
                }
            }
            case "CANCELLED" -> {
                // If the order was confirmed or shipped, stock was deducted -> restore product availability!
                if ("CONFIRMED".equals(currentStatus) || "SHIPPED".equals(currentStatus)) {
                    for (var item : so.items()) {
                        store.restoreProductStock(item.productId(), item.quantity());
                        store.auditAction(actor, "HARVESTED_PRODUCT", item.productId(), "RESTORE_STOCK_CANCEL_SO_" + id, requestId);
                    }
                }
            }
        }

        store.updateSalesOrderStatus(id, newStatus);
        store.auditAction(actor, "SALES_ORDER", id, "STATUS_" + newStatus, requestId);
        return salesOrder(id);
    }

    private void deductStockForItems(List<ItemInput> items) {
        for (var item : items) {
            var productOpt = store.lockProductForUpdate(item.productId());
            if (productOpt.isEmpty()) {
                throw DomainException.conflict("Nông sản (ID " + item.productId() + ") không tồn tại.");
            }
            var product = productOpt.get();
            if (product.quantity().compareTo(item.quantity()) < 0) {
                throw DomainException.conflict("Sản phẩm '" + product.productName() + "' không đủ số lượng tồn kho để bán. Hiện có: "
                    + product.quantity() + ", yêu cầu: " + item.quantity());
            }
            store.deductProductStock(item.productId(), item.quantity());
        }
    }

    private void validateItem(ItemInput item) {
        if (item.productId() <= 0) throw new IllegalArgumentException("Mã nông sản không hợp lệ.");
        if (item.quantity() == null || item.quantity().compareTo(BigDecimal.ZERO) <= 0
            || item.quantity().compareTo(new BigDecimal("999999999.99")) > 0
            || item.quantity().stripTrailingZeros().scale() > 2) {
            throw new IllegalArgumentException("Số lượng nông sản không hợp lệ (phải > 0, tối đa 2 chữ số thập phân).");
        }
        if (item.unitPrice() == null || item.unitPrice().compareTo(BigDecimal.ZERO) < 0
            || item.unitPrice().compareTo(new BigDecimal("999999999999.99")) > 0
            || item.unitPrice().stripTrailingZeros().scale() > 2) {
            throw new IllegalArgumentException("Đơn giá nông sản không hợp lệ (phải >= 0, tối đa 2 chữ số thập phân).");
        }
    }

    private String generateSoNumber(LocalDate date) {
        String dateStr = date.format(DateTimeFormatter.BASIC_ISO_DATE);
        String rand = UUID.randomUUID().toString().replace("-", "").substring(0, 4).toUpperCase(Locale.ROOT);
        return "SO-" + dateStr + "-" + rand;
    }
}
