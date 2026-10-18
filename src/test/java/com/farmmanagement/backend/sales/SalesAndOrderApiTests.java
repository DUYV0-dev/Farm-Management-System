package com.farmmanagement.backend.sales;

import com.farmmanagement.backend.auth.AuthStore;
import com.farmmanagement.backend.common.Api;
import com.farmmanagement.backend.common.ApiErrors;
import com.farmmanagement.backend.common.RequestIdFilter;
import com.farmmanagement.backend.config.SecurityConfig;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = {CustomerController.class, SalesOrderController.class}, properties = {
    "auth.jwt.secret=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
    "auth.jwt.issuer=test", "auth.jwt.audience=test",
    "logging.file.name=target/sales-tests.log"
})
@Import({SecurityConfig.class, CustomerService.class, SalesOrderService.class, Api.class, ApiErrors.class, RequestIdFilter.class})
class SalesAndOrderApiTests {

    @Autowired MockMvc mvc;
    @Autowired JwtEncoder encoder;
    @MockitoBean AuthStore auth;
    @MockitoBean CustomerStore customerStore;
    @MockitoBean SalesOrderStore salesOrderStore;

    String token;

    @BeforeEach
    void setup() {
        var now = Instant.now().minusSeconds(1);
        var sid = UUID.randomUUID();
        token = encoder.encode(JwtEncoderParameters.from(
            JwsHeader.with(MacAlgorithm.HS256).build(),
            JwtClaimsSet.builder()
                .issuer("test")
                .audience(List.of("test"))
                .subject("1")
                .id(sid.toString())
                .claim("sessionId", sid.toString())
                .issuedAt(now)
                .expiresAt(now.plusSeconds(86400))
                .build()
        )).getTokenValue();

        when(auth.validSession(any(), eq(1))).thenReturn(true);
        when(auth.roles(1)).thenReturn(List.of("SYSTEM_ADMIN"));
        when(auth.permissions(1)).thenReturn(List.of(
            "customers:read", "customers:create", "customers:update", "customers:status",
            "sales:read", "sales:create", "sales:update", "sales:status"
        ));
    }

    // ─── Customer Tests ───────────────────────────────────────────────────────
    @Test
    void listCustomersReturnsPagedItems() throws Exception {
        var c = new CustomerStore.Customer(1, "Sieu Thi Co.opmart", "Le Van C", "coopmart@saigonco-op.com.vn",
            "02838360143", "TP.HCM", "ENTERPRISE", "0300456789", "Doi tac chien luoc", "ACTIVE",
            Instant.now().toString(), Instant.now().toString());

        when(customerStore.listCustomers(0, 20, "", null, null)).thenReturn(List.of(c));
        when(customerStore.countCustomers("", null, null)).thenReturn(1L);

        mvc.perform(get("/api/v1/customers").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items[0].name").value("Sieu Thi Co.opmart"))
            .andExpect(jsonPath("$.data.total").value(1));
    }

    @Test
    void createCustomerSuccessAndAudit() throws Exception {
        var c = new CustomerStore.Customer(10, "Cong Ty WinCommerce", "Tran Thi D", "wincommerce@winmart.vn",
            "02471066866", "Hanoi", "RETAILER", "0104918404", "Chuoi ban le WinMart", "ACTIVE",
            Instant.now().toString(), Instant.now().toString());

        when(customerStore.createCustomer(eq("Cong Ty WinCommerce"), any(), any(), any(), any(), eq("RETAILER"), any(), any()))
            .thenReturn(10);
        when(customerStore.customer(10)).thenReturn(Optional.of(c));

        String body = """
            {
                "name": "Cong Ty WinCommerce",
                "contactName": "Tran Thi D",
                "email": "wincommerce@winmart.vn",
                "phone": "02471066866",
                "address": "Hanoi",
                "customerType": "RETAILER",
                "taxCode": "0104918404",
                "notes": "Chuoi ban le WinMart"
            }
        """;

        mvc.perform(post("/api/v1/customers").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated())
            .andExpect(header().string("Location", "/api/v1/customers/10"))
            .andExpect(jsonPath("$.data.name").value("Cong Ty WinCommerce"));

        verify(customerStore).auditAction(eq(1), eq("CUSTOMER"), eq(10), eq("CREATE"), anyString());
    }

    // ─── Sales Order Tests ────────────────────────────────────────────────────
    @Test
    void createSalesOrderPendingDoesNotDeductStock() throws Exception {
        var cust = new CustomerStore.Customer(1, "Dai Ly Nhat Nam", "A", "a@a.com", "0901234567", "Da Nang", "WHOLESALER", null, null, "ACTIVE", null, null);
        when(customerStore.customer(1)).thenReturn(Optional.of(cust));
        when(salesOrderStore.createSalesOrder(anyString(), eq(1), eq(2), any(), any(), eq("PENDING"), eq("UNPAID"), eq(new BigDecimal("1200000.00")), any(), any(), eq(1)))
            .thenReturn(50);

        var item = new SalesOrderStore.SalesOrderItem(1, 50, 5, "Xoai Cat Hoa Loc", new BigDecimal("40.00"), new BigDecimal("30000.00"), new BigDecimal("1200000.00"), null);
        var so = new SalesOrderStore.SalesOrder(50, "SO-20261018-0001", 1, "Dai Ly Nhat Nam", 2, "Trang Trai Tien Giang",
            "2026-10-18", "2026-10-22", "PENDING", "UNPAID", new BigDecimal("1200000.00"), "Quan 1 TP.HCM", null, 1,
            Instant.now().toString(), Instant.now().toString(), List.of(item));

        when(salesOrderStore.salesOrder(50)).thenReturn(Optional.of(so));

        String body = """
            {
                "customerId": 1,
                "farmId": 2,
                "status": "PENDING",
                "deliveryAddress": "Quan 1 TP.HCM",
                "items": [
                    { "productId": 5, "quantity": "40.00", "unitPrice": "30000.00" }
                ]
            }
        """;

        mvc.perform(post("/api/v1/sales-orders").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated())
            .andExpect(header().string("Location", "/api/v1/sales-orders/50"))
            .andExpect(jsonPath("$.data.status").value("PENDING"))
            .andExpect(jsonPath("$.data.totalAmount").value(1200000.00));

        // Stock must NOT be deducted when order is created as PENDING
        verify(salesOrderStore, never()).deductProductStock(anyInt(), any());
    }

    @Test
    void createSalesOrderConfirmedDeductsProductAvailability() throws Exception {
        var cust = new CustomerStore.Customer(1, "Dai Ly Nhat Nam", "A", "a@a.com", "0901234567", "Da Nang", "WHOLESALER", null, null, "ACTIVE", null, null);
        when(customerStore.customer(1)).thenReturn(Optional.of(cust));

        // Product has 100 kg available in stock
        var stock = new SalesOrderStore.ProductStock(5, "Xoai Cat Hoa Loc", new BigDecimal("100.00"), "AVAILABLE");
        when(salesOrderStore.lockProductForUpdate(5)).thenReturn(Optional.of(stock));

        when(salesOrderStore.createSalesOrder(anyString(), eq(1), eq(2), any(), any(), eq("CONFIRMED"), eq("UNPAID"), eq(new BigDecimal("1200000.00")), any(), any(), eq(1)))
            .thenReturn(51);

        var item = new SalesOrderStore.SalesOrderItem(1, 51, 5, "Xoai Cat Hoa Loc", new BigDecimal("40.00"), new BigDecimal("30000.00"), new BigDecimal("1200000.00"), null);
        var so = new SalesOrderStore.SalesOrder(51, "SO-20261018-0002", 1, "Dai Ly Nhat Nam", 2, "Trang Trai Tien Giang",
            "2026-10-18", "2026-10-22", "CONFIRMED", "UNPAID", new BigDecimal("1200000.00"), "Quan 1 TP.HCM", null, 1,
            Instant.now().toString(), Instant.now().toString(), List.of(item));

        when(salesOrderStore.salesOrder(51)).thenReturn(Optional.of(so));

        String body = """
            {
                "customerId": 1,
                "farmId": 2,
                "status": "CONFIRMED",
                "deliveryAddress": "Quan 1 TP.HCM",
                "items": [
                    { "productId": 5, "quantity": "40.00", "unitPrice": "30000.00" }
                ]
            }
        """;

        mvc.perform(post("/api/v1/sales-orders").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.status").value("CONFIRMED"));

        // CRITICAL CHECK: Product stock MUST be deducted by 40.00!
        verify(salesOrderStore).deductProductStock(eq(5), eq(new BigDecimal("40.00")));
    }

    @Test
    void createSalesOrderConfirmedWithInsufficientStockThrowsConflict() throws Exception {
        var cust = new CustomerStore.Customer(1, "Dai Ly Nhat Nam", "A", "a@a.com", "0901234567", "Da Nang", "WHOLESALER", null, null, "ACTIVE", null, null);
        when(customerStore.customer(1)).thenReturn(Optional.of(cust));

        // Product has only 15 kg in stock, but customer requests 40 kg
        var stock = new SalesOrderStore.ProductStock(5, "Xoai Cat Hoa Loc", new BigDecimal("15.00"), "AVAILABLE");
        when(salesOrderStore.lockProductForUpdate(5)).thenReturn(Optional.of(stock));

        String body = """
            {
                "customerId": 1,
                "farmId": 2,
                "status": "CONFIRMED",
                "items": [
                    { "productId": 5, "quantity": "40.00", "unitPrice": "30000.00" }
                ]
            }
        """;

        mvc.perform(post("/api/v1/sales-orders").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error.message").value(org.hamcrest.Matchers.containsString("không đủ số lượng tồn kho để bán. Hiện có: 15.00, yêu cầu: 40.00")));

        verify(salesOrderStore, never()).deductProductStock(anyInt(), any());
    }

    @Test
    void cancelConfirmedSalesOrderRestoresProductStock() throws Exception {
        var item = new SalesOrderStore.SalesOrderItem(1, 60, 5, "Xoai Cat Hoa Loc", new BigDecimal("30.00"), new BigDecimal("30000.00"), new BigDecimal("900000.00"), null);
        var soConfirmed = new SalesOrderStore.SalesOrder(60, "SO-60", 1, "Customer 1", 2, "Farm 2",
            "2026-10-18", null, "CONFIRMED", "UNPAID", new BigDecimal("900000.00"), null, null, 1,
            Instant.now().toString(), Instant.now().toString(), List.of(item));

        var soCancelled = new SalesOrderStore.SalesOrder(60, "SO-60", 1, "Customer 1", 2, "Farm 2",
            "2026-10-18", null, "CANCELLED", "UNPAID", new BigDecimal("900000.00"), null, null, 1,
            Instant.now().toString(), Instant.now().toString(), List.of(item));

        when(salesOrderStore.salesOrder(60)).thenReturn(Optional.of(soConfirmed)).thenReturn(Optional.of(soCancelled));

        mvc.perform(put("/api/v1/sales-orders/60/status").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\": \"CANCELLED\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("CANCELLED"));

        // CRITICAL CHECK: Product stock MUST be restored by 30.00!
        verify(salesOrderStore).restoreProductStock(eq(5), eq(new BigDecimal("30.00")));
        verify(salesOrderStore).updateSalesOrderStatus(eq(60), eq("CANCELLED"));
        verify(salesOrderStore).auditAction(eq(1), eq("SALES_ORDER"), eq(60), eq("STATUS_CANCELLED"), anyString());
    }

    @Test
    void cannotChangeDeliveredOrderThrowsConflict() throws Exception {
        var so = new SalesOrderStore.SalesOrder(70, "SO-70", 1, "Cust", 1, "Farm",
            "2026-10-18", null, "DELIVERED", "PAID", new BigDecimal("500000.00"), null, null, 1, null, null, List.of());
        when(salesOrderStore.salesOrder(70)).thenReturn(Optional.of(so));

        mvc.perform(put("/api/v1/sales-orders/70/status").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\": \"CANCELLED\"}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error.message").value("Đơn hàng đã giao thành công, không thể thay đổi trạng thái."));
    }
}
