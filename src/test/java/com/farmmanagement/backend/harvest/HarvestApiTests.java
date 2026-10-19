package com.farmmanagement.backend.harvest;

import com.farmmanagement.backend.auth.*;
import com.farmmanagement.backend.common.*;
import com.farmmanagement.backend.config.SecurityConfig;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers=HarvestController.class,properties={
    "auth.jwt.secret=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
    "auth.jwt.issuer=test","auth.jwt.audience=test","logging.file.name=target/harvest-tests.log"})
@Import({SecurityConfig.class,HarvestService.class,Api.class,ApiErrors.class,RequestIdFilter.class})
class HarvestApiTests {
    @Autowired MockMvc mvc;
    @Autowired JwtEncoder encoder;
    @Autowired HarvestService service;
    @MockitoBean AuthStore auth;
    @MockitoBean HarvestStore store;
    String token;
    final String base="/api/v1/farms/1/harvests";
    HarvestStore.Season season;
    @BeforeEach void setup() {
        var now=Instant.now().minusSeconds(1);var sid=UUID.randomUUID();
        token=encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(),
            JwtClaimsSet.builder().issuer("test").audience(List.of("test")).subject("1").id(sid.toString())
            .claim("sessionId",sid.toString()).issuedAt(now).expiresAt(now.plusSeconds(86400)).build())).getTokenValue();
        when(auth.validSession(any(),eq(1))).thenReturn(true);
        when(auth.roles(1)).thenReturn(List.of("USER"));
        when(auth.permissions(1)).thenReturn(List.of("harvests:read","harvests:create","crops:update"));
        when(store.allowed(1,1,false)).thenReturn(true);when(store.allowed(1,1,true)).thenReturn(true);
        season=new HarvestStore.Season(2,1,"Plot","Crop","Season","IN_PROGRESS","CONFIRMED",LocalDate.of(2020,1,1),LocalDate.of(2020,12,31),1,"kg");
        when(store.season(1,2)).thenReturn(Optional.of(season));
        when(store.create(eq(1),eq(2),any(),any(),any())).thenReturn(3);
        var h=new HarvestStore.Harvest(3,2,1,"Season","Plot","Crop",LocalDate.of(2020,6,1),new BigDecimal("120.125"),"kg","Loại A","CONFIRMED",1,Instant.now().toString());
        when(store.get(1,3)).thenReturn(Optional.of(h));
    }
    private String body(String quantity,String date) { return "{\"seasonId\":2,\"harvestDate\":\""+date+"\",\"quantity\":\""+quantity+"\",\"quality\":\"Loại A\"}"; }
    @Test void createPersistsExactQuantityQualityActorAndAudit() throws Exception {
        mvc.perform(post(base).header("Authorization","Bearer "+token).contentType(MediaType.APPLICATION_JSON).content(body("120.125","2020-06-01")))
            .andExpect(status().isCreated()).andExpect(header().string("Location",base+"/3"))
            .andExpect(jsonPath("$.data.quality").value("Loại A")).andExpect(jsonPath("$.data.seasonId").value(2));
        verify(store).create(1,2,LocalDate.of(2020,6,1),new BigDecimal("120.125"),"Loại A");
        verify(store).audit(eq(1),eq("HARVEST"),eq(3),eq("CREATE"),anyString());
    }
    @ParameterizedTest @ValueSource(strings={"0","-1","0.0001","10000000000000","NaN"})
    void invalidQuantitiesDoNotWrite(String quantity) throws Exception {
        mvc.perform(post(base).header("Authorization","Bearer "+token).contentType(MediaType.APPLICATION_JSON).content(body(quantity,"2020-06-01"))).andExpect(status().isUnprocessableContent());
        verify(store,never()).create(anyInt(),anyInt(),any(),any(),any());
    }
    @ParameterizedTest @ValueSource(strings={"2019-12-31","2021-01-01"})
    void outsideSeasonRejected(String date) throws Exception {
        mvc.perform(post(base).header("Authorization","Bearer "+token).contentType(MediaType.APPLICATION_JSON).content(body("1",date))).andExpect(status().isConflict());
        verify(store,never()).create(anyInt(),anyInt(),any(),any(),any());
    }
    @Test void boundariesAndMultipleHarvestsOnSameDayAreAllowed() {
        HarvestService.validate(season,season.start(),new BigDecimal("1"),null);
        HarvestService.validate(season,season.end(),new BigDecimal("1"),null);
        new HarvestService(store).create(1,1,2,LocalDate.of(2020,6,1),BigDecimal.ONE,null,"first");
        new HarvestService(store).create(1,1,2,LocalDate.of(2020,6,1),BigDecimal.ONE,null,"second");
        verify(store,times(2)).create(1,2,LocalDate.of(2020,6,1),BigDecimal.ONE,null);
    }
    @Test void missingTokenAndPermissionDenied() throws Exception {
        mvc.perform(get(base)).andExpect(status().isUnauthorized());
        when(auth.permissions(1)).thenReturn(List.of());
        mvc.perform(get(base).header("Authorization","Bearer "+token)).andExpect(status().isForbidden());
    }
    @Test void crossFarmDoesNotDiscloseOrWrite() throws Exception {
        mvc.perform(get("/api/v1/farms/9/harvests/3").header("Authorization","Bearer "+token)).andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/farms/9/harvests").header("Authorization","Bearer "+token).contentType(MediaType.APPLICATION_JSON).content(body("1","2020-06-01"))).andExpect(status().isNotFound());
        verify(store,never()).get(9,3);verify(store,never()).create(anyInt(),anyInt(),any(),any(),any());
    }
    @Test void seasonFromOtherFarmRejected() throws Exception {
        when(store.season(1,2)).thenReturn(Optional.empty());
        mvc.perform(post(base).header("Authorization","Bearer "+token).contentType(MediaType.APPLICATION_JSON).content(body("1","2020-06-01"))).andExpect(status().isNotFound());
    }
    @Test void ownerCannotCreateEvenWithGlobalPermission() throws Exception {
        when(store.allowed(1,1,true)).thenReturn(false);
        mvc.perform(post(base).header("Authorization","Bearer "+token).contentType(MediaType.APPLICATION_JSON).content(body("1","2020-06-01"))).andExpect(status().isNotFound());
    }
    @Test void unknownFieldsAndLongQualityRejected() throws Exception {
        mvc.perform(post(base).header("Authorization","Bearer "+token).contentType(MediaType.APPLICATION_JSON).content(body("1","2020-06-01").replace("}",",\"createdBy\":9}"))).andExpect(status().isUnprocessableContent());
        assertThrows(IllegalArgumentException.class,()->HarvestService.validate(season,season.start(),BigDecimal.ONE,"A".repeat(51)));
    }
    @Test void closedUnconfiguredAndFutureSeasonRejected() {
        var closed=new HarvestStore.Season(2,1,"P","C","S","COMPLETED","CONFIRMED",season.start(),season.end(),1,"kg");
        assertThrows(DomainException.class,()->HarvestService.validate(closed,season.start(),BigDecimal.ONE,null));
        var empty=new HarvestStore.Season(2,1,"P","C","S","IN_PROGRESS","CONFIRMED",null,null,null,null);
        assertThrows(DomainException.class,()->HarvestService.validate(empty,season.start(),BigDecimal.ONE,null));
        var tomorrow=LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")).plusDays(1);
        var future=new HarvestStore.Season(2,1,"P","C","S","IN_PROGRESS","CONFIRMED",tomorrow.minusDays(1),tomorrow.plusDays(1),1,"kg");
        assertThrows(DomainException.class,()->HarvestService.validate(future,tomorrow,BigDecimal.ONE,null));
    }
    @Test void filteredPaginatedHistoryAndEmptyResults() throws Exception {
        when(store.history(1,2,LocalDate.of(2020,1,1),LocalDate.of(2020,12,31),0,20)).thenReturn(Map.of("items",List.of(),"page",0,"size",20,"total",0));
        mvc.perform(get(base+"?seasonId=2&fromDate=2020-01-01&toDate=2020-12-31").header("Authorization","Bearer "+token))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(0)).andExpect(jsonPath("$.data.items").isEmpty());
        mvc.perform(get(base+"?size=51").header("Authorization","Bearer "+token)).andExpect(status().isUnprocessableContent());
    }
    @Test void metadataLockedOnceHistoryExists() {
        when(store.hasHarvest(2)).thenReturn(true);
        assertThrows(DomainException.class,()->new HarvestService(store).configure(1,1,2,"Changed",season.start(),season.end(),1,"test"));
        verify(store,never()).configure(anyInt(),anyString(),any(),any(),anyInt());
    }
}
