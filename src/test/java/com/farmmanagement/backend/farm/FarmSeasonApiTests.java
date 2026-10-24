package com.farmmanagement.backend.farm;

import com.farmmanagement.backend.auth.AuthStore;
import com.farmmanagement.backend.common.*;
import com.farmmanagement.backend.config.SecurityConfig;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = FarmController.class, properties = {
    "auth.jwt.secret=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
    "auth.jwt.issuer=test-issuer", "auth.jwt.audience=test-audience",
    "logging.file.name=target/season-test.log"
})
@Import({SecurityConfig.class, FarmService.class, Api.class, ApiErrors.class, RequestIdFilter.class})
class FarmSeasonApiTests {
    @Autowired MockMvc mvc;
    @Autowired JwtEncoder encoder;
    @Autowired ObjectMapper mapper;
    @MockitoBean AuthStore users;
    @MockitoBean FarmStore store;
    @MockitoBean com.farmmanagement.backend.tenancy.FarmWorkspaceStore workspaces;
    @MockitoBean org.springframework.transaction.support.TransactionTemplate transaction;

    String token;

    @BeforeEach
    void setup() {
        doAnswer(call->{ ((java.util.function.Consumer<org.springframework.transaction.TransactionStatus>)call.getArgument(0))
            .accept(mock(org.springframework.transaction.TransactionStatus.class)); return null; }).when(transaction).executeWithoutResult(any());
        when(workspaces.memberships(1)).thenReturn(List.of(new com.farmmanagement.backend.tenancy.FarmWorkspaceStore.Membership(
            1, "Farm", "ACTIVE", "ACTIVE", "READY", List.of("MANAGER"), List.of("crops:read", "crops:create", "crops:update", "crops:delete"))));

        UUID sid = UUID.randomUUID();
        Instant now = Instant.now().minusSeconds(1);
        var claims = JwtClaimsSet.builder().issuer("test-issuer").audience(List.of("test-audience"))
            .subject("1").id(sid.toString()).claim("sessionId", sid.toString())
            .issuedAt(now).expiresAt(now.plusSeconds(86400)).build();
        token = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
        when(users.validSession(any(), eq(1))).thenReturn(true);
        when(users.roles(1)).thenReturn(List.of("USER"));
        when(users.permissions(1)).thenReturn(List.of("crops:read", "crops:create", "crops:update", "crops:delete"));
    }

    @Test
    void listSeasonsReturnsData() throws Exception {
        var season = new FarmStore.Season(1, "Vụ Đông Xuân 2026", 10, "Khu A", "Farm 1", 5, "Lúa ST25", "2026-01-01", "2026-04-30", "Ghi chú", "IN_PROGRESS");
        when(store.listSeasons(anyInt(), anyInt(), anyString(), isNull())).thenReturn(List.of(season));
        when(store.countSeasons(anyString(), isNull())).thenReturn(1L);

        mvc.perform(get("/api/v1/seasons").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items[0].id").value(1))
            .andExpect(jsonPath("$.data.items[0].seasonName").value("Vụ Đông Xuân 2026"))
            .andExpect(jsonPath("$.data.items[0].startDate").value("2026-01-01"))
            .andExpect(jsonPath("$.data.items[0].endDate").value("2026-04-30"));
    }

    @Test
    void createSeasonFullPayloadSucceeds() throws Exception {
        var created = new FarmStore.Season(1, "Vụ Hè Thu 2026", 10, "Khu A", "Farm 1", 5, "Lúa ST25", "2026-05-01", "2026-08-31", "Ghi chú", "PLANNED");
        when(store.createSeason(eq("Vụ Hè Thu 2026"), eq(10), eq(5), any(), any(), eq("Ghi chú"), eq("PLANNED"))).thenReturn(1);
        when(store.season(1)).thenReturn(Optional.of(created));

        var payload = Map.of(
            "seasonName", "Vụ Hè Thu 2026",
            "plotId", 10,
            "cropId", 5,
            "startDate", "2026-05-01",
            "endDate", "2026-08-31",
            "description", "Ghi chú",
            "status", "PLANNED"
        );

        mvc.perform(post("/api/v1/seasons")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsString(payload)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.seasonName").value("Vụ Hè Thu 2026"));
    }

    @Test
    void createSeasonLegacyPayloadSucceeds() throws Exception {
        var created = new FarmStore.Season(2, null, 10, "Khu A", "Farm 1", 5, "Lúa ST25", null, null, null, "PLANNED");
        when(store.createSeason(isNull(), eq(10), eq(5), isNull(), isNull(), isNull(), eq("PLANNED"))).thenReturn(2);
        when(store.season(2)).thenReturn(Optional.of(created));

        var payload = Map.of("plotId", 10, "cropId", 5, "status", "PLANNED");

        mvc.perform(post("/api/v1/seasons")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsString(payload)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.id").value(2));
    }

    @Test
    void createSeasonInvalidDatesThrowsBadRequest() throws Exception {
        var payload = Map.of(
            "seasonName", "Sai ngày",
            "plotId", 10,
            "cropId", 5,
            "startDate", "2026-09-01",
            "endDate", "2026-05-01",
            "description", "Lỗi ngày",
            "status", "PLANNED"
        );

        mvc.perform(post("/api/v1/seasons")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsString(payload)))
            .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void updateSeasonFullPayloadSucceeds() throws Exception {
        var existing = new FarmStore.Season(1, "Vụ Cũ", 10, "Khu A", "Farm 1", 5, "Lúa", null, null, null, "PLANNED");
        var updated = new FarmStore.Season(1, "Vụ Mới", 10, "Khu A", "Farm 1", 5, "Lúa", "2026-06-01", "2026-09-30", "Mô tả mới", "IN_PROGRESS");
        when(store.season(1)).thenReturn(Optional.of(existing), Optional.of(updated));

        var payload = Map.of(
            "seasonName", "Vụ Mới",
            "startDate", "2026-06-01",
            "endDate", "2026-09-30",
            "description", "Mô tả mới",
            "status", "IN_PROGRESS"
        );

        mvc.perform(put("/api/v1/seasons/1")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsString(payload)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.seasonName").value("Vụ Mới"))
            .andExpect(jsonPath("$.data.status").value("IN_PROGRESS"));

        verify(store).updateSeason(eq(1), eq("Vụ Mới"), any(), any(), eq("Mô tả mới"), eq("IN_PROGRESS"));
    }

    @Test
    void deleteSeasonSucceeds() throws Exception {
        var existing = new FarmStore.Season(1, "Vụ Xóa", 10, "Khu A", "Farm 1", 5, "Lúa", null, null, null, "CANCELLED");
        when(store.season(1)).thenReturn(Optional.of(existing));

        mvc.perform(delete("/api/v1/seasons/1")
            .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.deleted").value(true));

        verify(store).deleteSeason(1);
    }
}
