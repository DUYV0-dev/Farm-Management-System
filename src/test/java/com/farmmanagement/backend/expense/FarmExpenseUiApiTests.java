package com.farmmanagement.backend.expense;

import com.farmmanagement.backend.auth.AuthStore;
import com.farmmanagement.backend.common.*;
import com.farmmanagement.backend.config.SecurityConfig;
import com.farmmanagement.backend.farm.FarmStore;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = FarmExpenseController.class, properties = {
    "auth.jwt.secret=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
    "auth.jwt.issuer=test-issuer", "auth.jwt.audience=test-audience",
    "logging.file.name=target/expense-test.log"
})
@Import({SecurityConfig.class, FarmExpenseService.class, Api.class, ApiErrors.class, RequestIdFilter.class})
class FarmExpenseUiApiTests {
    @Autowired MockMvc mvc;
    @Autowired JwtEncoder encoder;
    @MockitoBean AuthStore users;
    @MockitoBean FarmExpenseStore store;
    @MockitoBean FarmStore farms;
    @MockitoBean com.farmmanagement.backend.tenancy.FarmWorkspaceStore workspaces;
    @MockitoBean org.springframework.transaction.support.TransactionTemplate transaction;
    String token;

    @BeforeEach void setup() {
        doAnswer(call->{ ((java.util.function.Consumer<org.springframework.transaction.TransactionStatus>)call.getArgument(0))
            .accept(mock(org.springframework.transaction.TransactionStatus.class)); return null; }).when(transaction).executeWithoutResult(any());
        when(workspaces.memberships(1)).thenReturn(List.of(new com.farmmanagement.backend.tenancy.FarmWorkspaceStore.Membership(
            1,"Farm","ACTIVE","ACTIVE","READY",List.of("ACCOUNTANT"),List.of())));
        UUID sid = UUID.randomUUID(); Instant now = Instant.now().minusSeconds(1);
        var claims = JwtClaimsSet.builder().issuer("test-issuer").audience(List.of("test-audience"))
            .subject("1").id(sid.toString()).claim("sessionId", sid.toString())
            .issuedAt(now).expiresAt(now.plusSeconds(86400)).build();
        token = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
        when(users.validSession(any(), eq(1))).thenReturn(true);
        when(users.roles(1)).thenReturn(List.of("USER"));
        when(users.permissions(1)).thenReturn(List.of());
    }

    @Test void optionsRequireExpensePermissionAndSupportCreateOnlyAccounts() throws Exception {
        mvc.perform(get("/api/v1/expenses/options")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/expenses/options").header("Authorization", "Bearer " + token))
            .andExpect(status().isForbidden());
        verifyNoInteractions(store);
        when(store.farms()).thenReturn(List.of(new FarmExpenseStore.FarmOption(1, "Trang trại A")));
        when(store.seasons()).thenReturn(List.of(new FarmExpenseStore.SeasonOption(2, 1, "Khu A", "Lúa", "COMPLETED")));
        for (String permission : List.of("expenses:create", "expenses:read")) {
            when(users.permissions(1)).thenReturn(List.of(permission));
            mvc.perform(get("/api/v1/expenses/options").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.farms[0].id").value(1))
                .andExpect(jsonPath("$.data.seasons[0].farmId").value(1))
                .andExpect(jsonPath("$.data.categories.length()").value(8));
        }
        verifyNoInteractions(farms);
    }

    @Test void historyAndSummaryPreserveLargeDecimalAmountsAndPassSeasonFilter() throws Exception {
        when(users.permissions(1)).thenReturn(List.of("expenses:read"));
        var amount = new BigDecimal("9999999999999999.99");
        var filter = new ExpenseFilter(1, 2, ExpenseCategory.FERTILIZER, null, null);
        var expense = new FarmExpense(3, 1, "Trang trại A", 2, "Lúa", "Khu A",
            ExpenseCategory.FERTILIZER, amount, LocalDate.of(2026, 10, 12), null, 1, Instant.now());
        when(store.totals(filter)).thenReturn(new FarmExpenseStore.Totals(25, amount));
        when(store.history(filter, 20, 20)).thenReturn(List.of(expense));
        when(store.totalsByCategory(filter)).thenReturn(List.of(new FarmExpenseStore.CategoryTotal(ExpenseCategory.FERTILIZER, 25, amount)));
        String query = "?farmId=1&seasonId=2&category=FERTILIZER";
        mvc.perform(get("/api/v1/expenses" + query + "&page=1&size=20").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.total").value(25))
            .andExpect(jsonPath("$.data.totalAmount").value("9999999999999999.99"))
            .andExpect(jsonPath("$.data.items[0].amount").value("9999999999999999.99"));
        mvc.perform(get("/api/v1/expenses/summary" + query).header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.byCategory[0].totalAmount").value("9999999999999999.99"));
        verify(store).history(filter, 20, 20);
    }

    @Test void createPermissionDoesNotExposeExpenseHistory() throws Exception {
        when(users.permissions(1)).thenReturn(List.of("expenses:create"));
        mvc.perform(get("/api/v1/expenses").header("Authorization", "Bearer " + token))
            .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/expenses/summary").header("Authorization", "Bearer " + token))
            .andExpect(status().isForbidden());
        verifyNoInteractions(store);
    }
}
