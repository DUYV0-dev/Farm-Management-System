package com.farmmanagement.backend.supplier;

import com.farmmanagement.backend.auth.AuthStore;
import com.farmmanagement.backend.common.Api;
import com.farmmanagement.backend.common.ApiErrors;
import com.farmmanagement.backend.common.RequestIdFilter;
import com.farmmanagement.backend.config.SecurityConfig;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
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

@WebMvcTest(controllers = {SupplierController.class, PurchaseOrderController.class}, properties = {
    "auth.jwt.secret=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
    "auth.jwt.issuer=test", "auth.jwt.audience=test",
    "logging.file.name=target/supplier-tests.log"
})
@Import({SecurityConfig.class, SupplierService.class, Api.class, ApiErrors.class, RequestIdFilter.class})
class SupplierAndPurchaseApiTests {

    @Autowired MockMvc mvc;
    @Autowired JwtEncoder encoder;
    @MockitoBean AuthStore auth;
    @MockitoBean SupplierStore store;

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
            "suppliers:read", "suppliers:create", "suppliers:update", "suppliers:status",
            "purchases:read", "purchases:create", "purchases:update", "purchases:receive", "purchases:cancel"
        ));
    }

    // ─── Supplier Tests ───────────────────────────────────────────────────────
    @Test
    void listSuppliersReturnsPagedItems() throws Exception {
        var s = new SupplierStore.Supplier(1, "Vinaseed Co", "Nguyen Van A", "contact@vinaseed.vn",
            "0901234567", "Hanoi", "0101234567", "Uyen tin", "ACTIVE", Instant.now().toString(), Instant.now().toString());
        when(store.listSuppliers(0, 20, "", null)).thenReturn(List.of(s));
        when(store.countSuppliers("", null)).thenReturn(1L);

        mvc.perform(get("/api/v1/suppliers").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items[0].name").value("Vinaseed Co"))
            .andExpect(jsonPath("$.data.total").value(1));
    }

    @Test
    void createSupplierSuccessAndAudit() throws Exception {
        var s = new SupplierStore.Supplier(5, "Cong Ty Phan Bon Binh Dien", "Tran B", "binhdien@mail.com",
            "0912345678", "Long An", "0300123456", "Phan bon chat luong", "ACTIVE",
            Instant.now().toString(), Instant.now().toString());

        when(store.createSupplier(eq("Cong Ty Phan Bon Binh Dien"), any(), any(), any(), any(), any(), any()))
            .thenReturn(5);
        when(store.supplier(5)).thenReturn(Optional.of(s));

        String body = """
            {
                "name": "Cong Ty Phan Bon Binh Dien",
                "contactName": "Tran B",
                "email": "binhdien@mail.com",
                "phone": "0912345678",
                "address": "Long An",
                "taxCode": "0300123456",
                "notes": "Phan bon chat luong"
            }
        """;

        mvc.perform(post("/api/v1/suppliers").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated())
            .andExpect(header().string("Location", "/api/v1/suppliers/5"))
            .andExpect(jsonPath("$.data.name").value("Cong Ty Phan Bon Binh Dien"));

        verify(store).auditAction(eq(1), eq("SUPPLIER"), eq(5), eq("CREATE"), anyString());
    }

    @Test
    void createSupplierInvalidEmailThrows422() throws Exception {
        String body = """
            {
                "name": "Nha Cung Cap X",
                "email": "invalid-email-format"
            }
        """;
        mvc.perform(post("/api/v1/suppliers").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void deactivatingSupplierWithOpenOrdersThrowsConflict() throws Exception {
        var s = new SupplierStore.Supplier(1, "Vinaseed", "A", "a@a.com", "0901234567", "HN", null, null, "ACTIVE", null, null);
        when(store.supplier(1)).thenReturn(Optional.of(s));
        when(store.hasOpenOrders(1)).thenReturn(true);

        mvc.perform(put("/api/v1/suppliers/1/status").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\": \"INACTIVE\"}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error.message").value("Nhà cung cấp đang có đơn mua hàng chưa hoàn tất. Không thể ngừng hoạt động."));
    }

    // ─── Purchase Order Tests ─────────────────────────────────────────────────
    @Test
    void createPurchaseOrderCalculatesTotalAmountAndSavesItems() throws Exception {
        var sup = new SupplierStore.Supplier(2, "Vat Tu Phu My", "B", "b@b.com", "0901234567", "BRVT", null, null, "ACTIVE", null, null);
        when(store.supplier(2)).thenReturn(Optional.of(sup));
        when(store.isWarehouseActive(1)).thenReturn(true);
        when(store.isMaterialActive(10)).thenReturn(true);
        when(store.isMaterialActive(20)).thenReturn(true);
        when(store.createPurchaseOrder(anyString(), eq(2), eq(1), eq(1), any(), any(), eq(new BigDecimal("250000.00")), any(), eq(1)))
            .thenReturn(100);

        var item1 = new SupplierStore.PurchaseOrderItem(1, 100, 10, "Phan NPK", "kg", new BigDecimal("10.000"), new BigDecimal("15000.00"), new BigDecimal("150000.00"), BigDecimal.ZERO, "Ghi chu 1");
        var item2 = new SupplierStore.PurchaseOrderItem(2, 100, 20, "Phan Urea", "kg", new BigDecimal("5.000"), new BigDecimal("20000.00"), new BigDecimal("100000.00"), BigDecimal.ZERO, "Ghi chu 2");
        var po = new SupplierStore.PurchaseOrder(100, "PO-20261018-0001", 2, "Vat Tu Phu My", 1, "Trang Trai 1", 1, "Kho Tong",
            "2026-10-18", "2026-10-25", null, "DRAFT", new BigDecimal("250000.00"), "Dat hang thang 10", 1,
            Instant.now().toString(), Instant.now().toString(), List.of(item1, item2));

        when(store.purchaseOrder(100)).thenReturn(Optional.of(po));

        String body = """
            {
                "supplierId": 2,
                "farmId": 1,
                "warehouseId": 1,
                "poNumber": "PO-20261018-0001",
                "orderDate": "2026-10-18",
                "expectedDeliveryDate": "2026-10-25",
                "notes": "Dat hang thang 10",
                "items": [
                    { "materialId": 10, "quantity": "10.000", "unitPrice": "15000.00", "notes": "Ghi chu 1" },
                    { "materialId": 20, "quantity": "5.000", "unitPrice": "20000.00", "notes": "Ghi chu 2" }
                ]
            }
        """;

        mvc.perform(post("/api/v1/purchase-orders").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated())
            .andExpect(header().string("Location", "/api/v1/purchase-orders/100"))
            .andExpect(jsonPath("$.data.poNumber").value("PO-20261018-0001"))
            .andExpect(jsonPath("$.data.totalAmount").value(250000.00))
            .andExpect(jsonPath("$.data.items.length()").value(2));

        verify(store).createPurchaseOrderItem(eq(100), eq(10), eq(new BigDecimal("10.000")), eq(new BigDecimal("15000.00")), eq(new BigDecimal("150000.00")), eq("Ghi chu 1"));
        verify(store).createPurchaseOrderItem(eq(100), eq(20), eq(new BigDecimal("5.000")), eq(new BigDecimal("20000.00")), eq(new BigDecimal("100000.00")), eq("Ghi chu 2"));
        verify(store).auditAction(eq(1), eq("PURCHASE_ORDER"), eq(100), eq("CREATE"), anyString());
    }

    @Test
    void receiveMaterialsUpdatesWarehouseInventoryAndMarksReceived() throws Exception {
        var item1 = new SupplierStore.PurchaseOrderItem(1, 100, 10, "Phan NPK", "kg", new BigDecimal("50.000"), new BigDecimal("15000.00"), new BigDecimal("750000.00"), BigDecimal.ZERO, null);
        var poOrdered = new SupplierStore.PurchaseOrder(100, "PO-20261018-0001", 2, "Vat Tu Phu My", 1, "Trang Trai 1", 3, "Kho Vat Tu So 3",
            "2026-10-18", "2026-10-20", null, "ORDERED", new BigDecimal("750000.00"), null, 1,
            Instant.now().toString(), Instant.now().toString(), List.of(item1));

        var itemReceived = new SupplierStore.PurchaseOrderItem(1, 100, 10, "Phan NPK", "kg", new BigDecimal("50.000"), new BigDecimal("15000.00"), new BigDecimal("750000.00"), new BigDecimal("50.000"), null);
        var poReceived = new SupplierStore.PurchaseOrder(100, "PO-20261018-0001", 2, "Vat Tu Phu My", 1, "Trang Trai 1", 3, "Kho Vat Tu So 3",
            "2026-10-18", "2026-10-20", LocalDate.now().toString(), "RECEIVED", new BigDecimal("750000.00"), null, 1,
            Instant.now().toString(), Instant.now().toString(), List.of(itemReceived));

        when(store.purchaseOrder(100)).thenReturn(Optional.of(poOrdered)).thenReturn(Optional.of(poReceived));
        when(store.isWarehouseActive(3)).thenReturn(true);
        when(store.isMaterialActive(10)).thenReturn(true);

        mvc.perform(post("/api/v1/purchase-orders/100/receive").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("RECEIVED"))
            .andExpect(jsonPath("$.data.items[0].receivedQuantity").value(50.000));

        // CRITICAL CHECK: Warehouse inventory must be incremented with received quantity!
        verify(store).addInventoryStock(eq(3), eq(10), eq(new BigDecimal("50.000")));
        verify(store).updatePurchaseOrderItemReceived(eq(1), eq(new BigDecimal("50.000")));
        verify(store).updatePurchaseOrderStatus(eq(100), eq("RECEIVED"), eq(LocalDate.now()));
        verify(store).auditAction(eq(1), eq("PURCHASE_ORDER"), eq(100), eq("RECEIVE"), anyString());
        verify(store).auditAction(eq(1), eq("INVENTORY"), eq(3), eq("RECEIVE_MATERIALS_FROM_PO_100"), anyString());
    }

    @Test
    void receiveAlreadyReceivedOrderThrowsConflict() throws Exception {
        var po = new SupplierStore.PurchaseOrder(100, "PO-001", 2, "Sup", 1, "Farm", 1, "Wh",
            "2026-10-18", null, "2026-10-18", "RECEIVED", new BigDecimal("1000.00"), null, 1, null, null, List.of());
        when(store.purchaseOrder(100)).thenReturn(Optional.of(po));

        mvc.perform(post("/api/v1/purchase-orders/100/receive").header("Authorization", "Bearer " + token))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error.message").value("Đơn mua hàng này đã được nhập kho trước đó."));

        verify(store, never()).addInventoryStock(anyInt(), anyInt(), any());
    }

    @Test
    void unauthenticatedRequestReturns401() throws Exception {
        mvc.perform(get("/api/v1/suppliers"))
            .andExpect(status().isUnauthorized());
    }
}
