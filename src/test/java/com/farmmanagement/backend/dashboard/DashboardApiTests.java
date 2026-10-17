package com.farmmanagement.backend.dashboard;

import static com.farmmanagement.backend.dashboard.ReportModels.*;
import com.farmmanagement.backend.auth.AuthStore;
import com.farmmanagement.backend.harvest.HarvestStore;
import com.farmmanagement.backend.common.*;
import com.farmmanagement.backend.config.SecurityConfig;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers=DashboardController.class,properties={"auth.jwt.secret=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=","auth.jwt.issuer=test","auth.jwt.audience=test","logging.file.name=target/dashboard-tests.log"})
@Import({SecurityConfig.class,DashboardService.class,ReportExportService.class,Api.class,ApiErrors.class,RequestIdFilter.class})
class DashboardApiTests {
    @Autowired MockMvc mvc;
    @Autowired JwtEncoder encoder;
    @Autowired ObjectMapper mapper;
    @MockitoBean AuthStore auth;
    @MockitoBean HarvestStore farms;
    @MockitoBean DashboardStore store;
    String token;
    String base="/api/v1/farms/1";
    String query="?fromDate=2020-06-01&toDate=2020-06-30";
    Filters filter=new Filters(LocalDate.of(2020,6,1),LocalDate.of(2020,6,30),null,null,null);
    Snapshot snapshot;
    @BeforeEach void setup() {
        var now=Instant.now().minusSeconds(1);var sid=UUID.randomUUID();
        token=encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(),JwtClaimsSet.builder().issuer("test").audience(List.of("test")).subject("1").id(sid.toString()).claim("sessionId",sid.toString()).issuedAt(now).expiresAt(now.plusSeconds(86400)).build())).getTokenValue();
        when(auth.validSession(any(),eq(1))).thenReturn(true);when(auth.roles(1)).thenReturn(List.of("USER"));
        when(auth.permissions(1)).thenReturn(List.of("dashboard:read","reports:read","harvests:read","crops:read","warehouses:read","expenses:read","reports:export:xlsx","reports:export:pdf"));
        when(farms.allowed(1,1,false)).thenReturn(true);when(store.farmName(1)).thenReturn("Trang trại Vinh");
        when(store.production(eq(1),any(),any())).thenReturn(List.of());when(store.seasons(eq(1),any(),anyInt())).thenReturn(List.of());
        when(store.details(eq(1),any(),anyInt())).thenReturn(List.of());when(store.reportSeasons(eq(1),any(),anyInt())).thenReturn(List.of());
        var rows=new ArrayList<ProductionRow>();for(int i=0;i<25;i++) rows.add(new ProductionRow(i,2,"Mùa vụ","IN_PROGRESS",3,"Khu đất",4,"Lúa",filter.fromDate(),new BigDecimal("12.125"),1,"kg","Loại A"));
        snapshot=new Snapshot(UUID.randomUUID(),1,"Trang trại Vinh",1,now,now.plusSeconds(86400),filter,VERSION,ZONE,rows,List.of());
        when(store.snapshot(1,1,snapshot.reportId())).thenReturn(Optional.of(mapper.writeValueAsString(snapshot)));
    }
    @Test void emptyProductionIsAvailableAndHasNoInventedTotals() throws Exception {
        mvc.perform(get(base+"/dashboard/production"+query).header("Authorization","Bearer "+token)).andExpect(status().isOk()).andExpect(jsonPath("$.data.sourceStatus").value("AVAILABLE")).andExpect(jsonPath("$.data.unitGroups").isEmpty());
    }
    @Test void missingSourcesDoNotBecomeZeroAndExpensesHaveSeparatePermission() throws Exception {
        mvc.perform(get(base+"/statistics/tasks"+query).header("Authorization","Bearer "+token)).andExpect(status().isOk()).andExpect(jsonPath("$.data.sourceStatus").value("UNAVAILABLE"));
        mvc.perform(get(base+"/statistics/expenses"+query).header("Authorization","Bearer "+token)).andExpect(status().isOk()).andExpect(jsonPath("$.data.sourceStatus").value("UNAVAILABLE")).andExpect(jsonPath("$.data.purchaseValue").isEmpty());
        when(auth.permissions(1)).thenReturn(List.of("dashboard:read"));
        mvc.perform(get(base+"/statistics/expenses"+query).header("Authorization","Bearer "+token)).andExpect(status().isForbidden());
        mvc.perform(get(base+"/dashboard/production"+query).header("Authorization","Bearer "+token)).andExpect(status().isForbidden());
    }
    @Test void currentInventoryDoesNotPretendToBeHistorical() throws Exception {
        when(store.inventory(1,null,null)).thenReturn(List.of(Map.of("quantity",new BigDecimal("125.125"),"unit","kg")));
        mvc.perform(get(base+"/statistics/inventory"+query).header("Authorization","Bearer "+token)).andExpect(status().isOk()).andExpect(jsonPath("$.data.sourceStatus").value("PARTIAL")).andExpect(jsonPath("$.data.historicalStatus").value("UNAVAILABLE")).andExpect(jsonPath("$.data.currentItems[0].quantity").value(125.125));
    }
    @Test void invalidDatesRepeatedAndUnsupportedFiltersRejected() throws Exception {
        for(String q:List.of("", "?fromDate=2020-01-02&toDate=2020-01-01",query+"&fromDate=2020-01-01",query+"&warehouseId=2", "?fromDate=2020-02-30&toDate=2020-03-01"))
            mvc.perform(get(base+"/dashboard/production"+q).header("Authorization","Bearer "+token)).andExpect(status().isBadRequest());
        mvc.perform(get(base+"/statistics/inventory"+query+"&cropId=2").header("Authorization","Bearer "+token)).andExpect(status().isBadRequest());
    }
    @Test void missingTokenRevokedSessionAndForeignFarmDenied() throws Exception {
        mvc.perform(get(base+"/dashboard/production"+query)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/farms/9/dashboard/production"+query).header("Authorization","Bearer "+token)).andExpect(status().isNotFound());
        verify(store,never()).production(eq(9),any(),any());
        when(auth.validSession(any(),eq(1))).thenReturn(false);
        mvc.perform(get(base+"/dashboard/production"+query).header("Authorization","Bearer "+token)).andExpect(status().isUnauthorized());
    }
    @Test void filterIdsCheckedAgainstFarm() throws Exception {
        mvc.perform(get(base+"/dashboard/production"+query+"&seasonId=9").header("Authorization","Bearer "+token)).andExpect(status().isNotFound());
        verify(store,never()).production(anyInt(),any(),any());
    }
    @Test void snapshotCreationPersistencePaginationAndExportAllRows() throws Exception {
        mvc.perform(post(base+"/reports/production").header("Authorization","Bearer "+token).contentType(MediaType.APPLICATION_JSON).content("{\"fromDate\":\"2020-06-01\",\"toDate\":\"2020-06-30\"}")).andExpect(status().isCreated());
        verify(store).save(any(),anyString());
        mvc.perform(get(base+"/reports/"+snapshot.reportId()+"?page=1&size=20").header("Authorization","Bearer "+token)).andExpect(status().isOk()).andExpect(jsonPath("$.data.items.length()").value(5)).andExpect(jsonPath("$.data.total").value(25));
        var file=mvc.perform(get(base+"/reports/"+snapshot.reportId()+"/export?format=xlsx").header("Authorization","Bearer "+token)).andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store")).andReturn().getResponse().getContentAsByteArray();
        try(var wb=new org.apache.poi.xssf.usermodel.XSSFWorkbook(new java.io.ByteArrayInputStream(file))) { assertEquals(26,wb.getSheet("PR01 Chi tiết").getPhysicalNumberOfRows()); }
    }
    @Test void expiredAndDifferentOwnerReportsCannotBeReadOrExported() throws Exception {
        var expired=new Snapshot(snapshot.reportId(),1,"Farm",1,Instant.now().minusSeconds(90000),Instant.now().minusSeconds(1),filter,VERSION,ZONE,List.of(),List.of());
        when(store.snapshot(1,1,snapshot.reportId())).thenReturn(Optional.of(mapper.writeValueAsString(expired)));
        mvc.perform(get(base+"/reports/"+snapshot.reportId()).header("Authorization","Bearer "+token)).andExpect(status().isGone());
        mvc.perform(get(base+"/reports/"+snapshot.reportId()+"/export?format=xlsx").header("Authorization","Bearer "+token)).andExpect(status().isGone());
        when(store.snapshot(1,1,snapshot.reportId())).thenReturn(Optional.empty());
        mvc.perform(get(base+"/reports/"+snapshot.reportId()).header("Authorization","Bearer "+token)).andExpect(status().isNotFound());
    }
    @Test void withdrawnMembershipAndExportPermissionBlockSavedFiles() throws Exception {
        when(farms.allowed(1,1,false)).thenReturn(false);
        mvc.perform(get(base+"/reports/"+snapshot.reportId()+"/export?format=xlsx").header("Authorization","Bearer "+token)).andExpect(status().isNotFound());
        when(farms.allowed(1,1,false)).thenReturn(true);
        when(auth.permissions(1)).thenReturn(List.of("reports:read","harvests:read","crops:read"));
        mvc.perform(get(base+"/reports/"+snapshot.reportId()+"/export?format=xlsx").header("Authorization","Bearer "+token)).andExpect(status().isForbidden());
    }
    @Test void oversizedSnapshotIsNotSilentlyTruncated() throws Exception {
        when(store.details(eq(1),any(),anyInt())).thenReturn(Collections.nCopies(10000,snapshot.details().getFirst()));
        mvc.perform(post(base+"/reports/production").header("Authorization","Bearer "+token).contentType(MediaType.APPLICATION_JSON).content("{\"fromDate\":\"2020-06-01\",\"toDate\":\"2020-06-30\"}")).andExpect(status().isContentTooLarge());
        verify(store,never()).save(any(),anyString());
    }
    @Test void sourceErrorsAreNotZeroSuccess() throws Exception {
        when(store.production(eq(1),any(),any())).thenThrow(new org.springframework.dao.DataAccessResourceFailureException("test"));
        mvc.perform(get(base+"/dashboard/production"+query).header("Authorization","Bearer "+token)).andExpect(status().isServiceUnavailable());
    }
}
