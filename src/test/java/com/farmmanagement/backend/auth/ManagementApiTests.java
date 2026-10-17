package com.farmmanagement.backend.auth;

import com.farmmanagement.backend.common.*;
import com.farmmanagement.backend.config.SecurityConfig;
import com.farmmanagement.backend.management.*;

import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers={AuthController.class,ManagementController.class,ManagementPages.class},properties={
    "auth.jwt.secret=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
    "auth.jwt.issuer=test-issuer", "auth.jwt.audience=test-audience",
    "logging.file.name=target/test-backend.log", "logging.level.org.springframework=INFO"
})
@Import({SecurityConfig.class,AuthService.class,ManagementService.class,Api.class,ApiErrors.class,RequestIdFilter.class})
class ManagementApiTests {
    @Autowired MockMvc mvc;
    @Autowired JwtEncoder encoder;
    @MockitoBean AuthStore users;
    @MockitoBean ManagementStore store;
    String token;
    @BeforeEach void setup() {
        UUID sid=UUID.randomUUID(); Instant now=Instant.now().minusSeconds(1);
        var claims=JwtClaimsSet.builder().issuer("test-issuer").audience(List.of("test-audience"))
            .subject("1").id(sid.toString()).claim("sessionId",sid.toString()).issuedAt(now).expiresAt(now.plusSeconds(86400)).build();
        token=encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(),claims)).getTokenValue();
        when(users.validSession(any(),eq(1))).thenReturn(true);
        when(users.byId(1)).thenReturn(Optional.of(new AuthStore.User(1,"actor","hash","Actor","actor@test.vn","ACTIVE",Instant.now())));
        when(users.roles(1)).thenReturn(List.of("USER"));
        when(users.permissions(1)).thenReturn(List.of());
    }
    @ParameterizedTest
    @CsvSource({"/api/v1/users,users:read", "/api/v1/roles,roles:read", "/api/v1/permissions,roles:read", "/admin/users,users:read", "/admin/roles,roles:read"})
    void protectedReadsRequireAuthenticationAndExactPermission(String path,String permission) throws Exception {
        mvc.perform(get(path)).andExpect(status().isUnauthorized());
        mvc.perform(get(path).header("Authorization","Bearer "+token)).andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
        when(users.permissions(1)).thenReturn(List.of(permission));
        mvc.perform(get(path).header("Authorization","Bearer "+token)).andExpect(status().isOk())
            .andExpect(header().string("Cache-Control","no-store"));
    }
    @Test void revokedSessionRejectedEvenWithPermission() throws Exception {
        when(users.permissions(1)).thenReturn(List.of("users:read")); when(users.validSession(any(),anyInt())).thenReturn(false);
        mvc.perform(get("/api/v1/users").header("Authorization","Bearer "+token)).andExpect(status().isUnauthorized());
        verifyNoInteractions(store);
    }
    @Test void permissionsAreReadAgainForSameToken() throws Exception {
        when(users.permissions(1)).thenReturn(List.of("users:read"));
        mvc.perform(get("/api/v1/users").header("Authorization","Bearer "+token)).andExpect(status().isOk());
        when(users.permissions(1)).thenReturn(List.of());
        mvc.perform(get("/api/v1/users").header("Authorization","Bearer "+token)).andExpect(status().isForbidden());
    }
    @Test void roleNameAloneDoesNotBypassPermissionValidation() throws Exception {
        when(users.roles(1)).thenReturn(List.of("SYSTEM_ADMIN"));
        mvc.perform(get("/api/v1/users").header("Authorization","Bearer "+token)).andExpect(status().isForbidden());
    }
    @ParameterizedTest
    @CsvSource(delimiter='|',value={
        "POST|/api/v1/users|{\"username\":\"worker\",\"password\":\"ValidPassword123\",\"fullName\":\"Worker\",\"email\":\"w@example.com\"}",
        "PUT|/api/v1/users/2|{\"fullName\":\"Worker\",\"email\":\"w@example.com\"}",
        "PUT|/api/v1/users/2/status|{\"status\":\"INACTIVE\"}",
        "PUT|/api/v1/users/2/roles|{\"roleIds\":[2]}",
        "PUT|/api/v1/users/2/password|{\"newPassword\":\"ValidPassword123\"}",
        "POST|/api/v1/roles|{\"name\":\"MANAGER\",\"description\":\"Manager\"}",
        "PUT|/api/v1/roles/2|{\"name\":\"MANAGER\",\"description\":\"Manager\"}",
        "PUT|/api/v1/roles/2/permissions|{\"permissions\":[\"users:read\"]}"
    })
    void allMutationsDenyOrdinaryUsers(String method,String path,String body) throws Exception {
        mvc.perform(request(org.springframework.http.HttpMethod.valueOf(method),path).header("Authorization","Bearer "+token)
            .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
        verifyNoInteractions(store);
    }
    @Test void createUserReturns201AndLocation() throws Exception {
        when(users.roles(1)).thenReturn(List.of("SYSTEM_ADMIN")); when(users.permissions(1)).thenReturn(List.of("users:create"));
        when(store.defaultRole()).thenReturn(2);
        when(store.role(2)).thenReturn(Optional.of(new ManagementStore.Role(2,"USER","User",List.of())));
        when(store.createUser(eq("worker"),anyString(),eq("Worker"),eq("w@example.com"))).thenReturn(3);
        when(users.byId(3)).thenReturn(Optional.of(new AuthStore.User(3,"worker","secret-hash","Worker","w@example.com","ACTIVE",Instant.now())));
        when(users.publicUser(any())).thenReturn(Map.of("id",3,"username","worker"));
        mvc.perform(post("/api/v1/users").header("Authorization","Bearer "+token).contentType(MediaType.APPLICATION_JSON)
            .content("{\"username\":\"worker\",\"password\":\"ValidPassword123\",\"fullName\":\"Worker\",\"email\":\"w@example.com\"}"))
            .andExpect(status().isCreated()).andExpect(header().string("Location","/api/v1/users/3"))
            .andExpect(jsonPath("$.data.password").doesNotExist()).andExpect(jsonPath("$.data.hash").doesNotExist());
        verify(store).createUser(eq("worker"),startsWith("$argon2id$"),eq("Worker"),eq("w@example.com"));
    }
    @Test void bodyCannotInjectRolesOrActor() throws Exception {
        when(users.permissions(1)).thenReturn(List.of("users:create"));
        mvc.perform(post("/api/v1/users").header("Authorization","Bearer "+token).contentType(MediaType.APPLICATION_JSON)
            .content("{\"username\":\"worker\",\"password\":\"ValidPassword123\",\"fullName\":\"Worker\",\"email\":\"w@example.com\",\"roleIds\":[1]}"))
            .andExpect(status().isUnprocessableContent());
        verifyNoInteractions(store);
    }
    @Test void duplicateDataIs409() throws Exception {
        when(users.permissions(1)).thenReturn(List.of("roles:write"));
        when(store.createRole("MANAGER","Manager")).thenThrow(new org.springframework.dao.DuplicateKeyException("private db detail"));
        mvc.perform(post("/api/v1/roles").header("Authorization","Bearer "+token).contentType(MediaType.APPLICATION_JSON)
            .content("{\"name\":\"MANAGER\",\"description\":\"Manager\"}"))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("CONFLICT"));
    }
    @Test void invalidPaginationAndIdAre422() throws Exception {
        when(users.permissions(1)).thenReturn(List.of("users:read"));
        mvc.perform(get("/api/v1/users?page=oops").header("Authorization","Bearer "+token)).andExpect(status().isUnprocessableContent());
        mvc.perform(get("/api/v1/users/-1").header("Authorization","Bearer "+token)).andExpect(status().isUnprocessableContent());
    }
    @Test void missingUserIs404() throws Exception {
        when(users.permissions(1)).thenReturn(List.of("users:read"));
        mvc.perform(get("/api/v1/users/999").header("Authorization","Bearer "+token)).andExpect(status().isNotFound());
    }
    @Test void databaseFailureDuringAuthenticationIs503() throws Exception {
        when(users.validSession(any(),anyInt())).thenThrow(new org.springframework.dao.DataAccessResourceFailureException("private db detail"));
        mvc.perform(get("/api/v1/users").header("Authorization","Bearer "+token)).andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.error.code").value("SERVICE_UNAVAILABLE"));
    }
    @Test void protectedFragmentsCannotBeFetchedAsStaticResources() throws Exception {
        mvc.perform(get("/protected/users.html").header("Authorization","Bearer "+token)).andExpect(status().isForbidden());
        mvc.perform(get("/actuator/health").header("Authorization","Bearer "+token)).andExpect(status().isForbidden());
    }
    @Test void logoutRevokesOnlyCurrentSession() throws Exception {
        mvc.perform(post("/api/v1/auth/logout").header("Authorization","Bearer "+token)).andExpect(status().isNoContent());
        verify(users).revoke(any(UUID.class),eq(1)); verify(users,never()).revokeAll(anyInt());
    }
    @Test void assigningRolesUsesAuthenticatedActorAndAuditId() throws Exception {
        when(users.roles(1)).thenReturn(List.of("SYSTEM_ADMIN")); when(users.permissions(1)).thenReturn(List.of("users:assign"));
        when(users.byId(2)).thenReturn(Optional.of(new AuthStore.User(2,"worker","hash","Worker","w@test.vn","ACTIVE",Instant.now())));
        when(store.role(3)).thenReturn(Optional.of(new ManagementStore.Role(3,"MANAGER","Manager",List.of("users:read"))));
        when(users.publicUser(any())).thenReturn(Map.of("id",2));
        var result=mvc.perform(put("/api/v1/users/2/roles").header("Authorization","Bearer "+token)
            .contentType(MediaType.APPLICATION_JSON).content("{\"roleIds\":[3]}"))
            .andExpect(status().isOk()).andReturn();
        verify(store).assignRoles(2,List.of(3));
        verify(store).audit(1,"USER_ROLE",2,"ROLE_CHANGE",result.getResponse().getHeader("X-Request-Id"));
    }
    @Test void passwordChangeEndpointWorksWithoutAdministrativePermission() throws Exception {
        var passwords=new SecurityConfig().passwordEncoder();
        when(users.byId(1,true)).thenReturn(Optional.of(new AuthStore.User(1,"user",passwords.encode("CurrentSecret123"),"User","u@test.vn","ACTIVE",Instant.now())));
        mvc.perform(post("/api/v1/auth/password").header("Authorization","Bearer "+token).contentType(MediaType.APPLICATION_JSON)
            .content("{\"currentPassword\":\"CurrentSecret123\",\"newPassword\":\"DifferentSecret456\"}"))
            .andExpect(status().isNoContent());
        verify(users).changePassword(eq(1),startsWith("$argon2id$"));
    }
    @Test void farmRoleIdsCannotBeAssignedAsSystemRoles() throws Exception {
        when(users.roles(1)).thenReturn(List.of("SYSTEM_ADMIN")); when(users.permissions(1)).thenReturn(List.of("users:assign"));
        when(users.byId(2)).thenReturn(Optional.of(new AuthStore.User(2,"worker","hash","Worker","w@test.vn","ACTIVE",Instant.now())));
        when(store.role(99)).thenReturn(Optional.empty());
        mvc.perform(put("/api/v1/users/2/roles").header("Authorization","Bearer "+token).contentType(MediaType.APPLICATION_JSON)
            .content("{\"roleIds\":[99]}"))
            .andExpect(status().isNotFound());
        verify(store,never()).assignRoles(anyInt(),anyList());
    }

    @Test void permissionRevokedWhileWaitingForAdministrationLockIsDenied() throws Exception {
        when(users.roles(1)).thenReturn(List.of("SYSTEM_ADMIN"));
        when(users.permissions(1)).thenReturn(List.of("roles:write"));
        when(store.createRole("REVIEWER","Reviewer")).thenReturn(9);
        when(store.role(9)).thenReturn(Optional.of(new ManagementStore.Role(9,"REVIEWER","Reviewer",List.of())));
        doAnswer(invocation -> {
            when(users.permissions(1)).thenReturn(List.of());
            return null;
        }).when(store).lockAdministration();
        mvc.perform(post("/api/v1/roles").header("Authorization","Bearer "+token)
            .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"REVIEWER\",\"description\":\"Reviewer\"}"))
            .andExpect(status().isForbidden());
        verify(store,never()).createRole(anyString(),anyString());
        verify(store,never()).audit(anyInt(),anyString(),anyInt(),anyString(),anyString());
    }
    @Test void accountDisabledWhileWaitingForAdministrationLockIsDenied() throws Exception {
        when(users.permissions(1)).thenReturn(List.of("roles:write"));
        when(store.createRole("REVIEWER","Reviewer")).thenReturn(9);
        when(store.role(9)).thenReturn(Optional.of(new ManagementStore.Role(9,"REVIEWER","Reviewer",List.of())));
        doAnswer(invocation -> {
            when(users.byId(1)).thenReturn(Optional.of(new AuthStore.User(1,"actor","hash","Actor","actor@test.vn","INACTIVE",Instant.now())));
            return null;
        }).when(store).lockAdministration();
        mvc.perform(post("/api/v1/roles").header("Authorization","Bearer "+token)
            .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"REVIEWER\",\"description\":\"Reviewer\"}"))
            .andExpect(status().isForbidden());
        verify(store,never()).createRole(anyString(),anyString());
    }
}
