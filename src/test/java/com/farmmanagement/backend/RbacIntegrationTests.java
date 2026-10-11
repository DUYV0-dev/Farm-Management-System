package com.farmmanagement.backend;

import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import tools.jackson.databind.*;

// Uses only the disposable PostgreSQL cluster created by Test-Management.ps1.
@EnabledIfSystemProperty(named="management.test.db", matches="true")
@SpringBootTest(properties={
    "spring.datasource.url=jdbc:postgresql://127.0.0.1:55432/management_test",
    "spring.datasource.username=farm_app", "spring.datasource.password=",
    "auth.jwt.secret=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
    "logging.file.name=target/management-integration.log"
})
@AutoConfigureMockMvc
class RbacIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired PasswordEncoder passwords;
    final JdbcTemplate admin = new JdbcTemplate(new DriverManagerDataSource(
        "jdbc:postgresql://127.0.0.1:55432/management_test", "postgres", ""));
    String adminToken, prefix;
    int actor;

    @BeforeEach void setup() throws Exception {
        prefix="rbac"+UUID.randomUUID().toString().replace("-","").substring(0,12);
        actor=admin.queryForObject("INSERT INTO app_user(username,password_hash,full_name,email) VALUES (?,?,?,?) RETURNING user_id",
            Integer.class,prefix,passwords.encode("IntegrationPassword123"),prefix,prefix+"@test.invalid");
        admin.update("INSERT INTO user_role SELECT ?,role_id FROM role WHERE role_name='SYSTEM_ADMIN' AND scope='SYSTEM'",actor);
        adminToken=login(prefix,"IntegrationPassword123");
    }
    JsonNode call(String token,String method,String path,Object body,int expected) throws Exception {
        var req=request(HttpMethod.valueOf(method),"/api/v1/"+path);
        if(token!=null) req.header("Authorization","Bearer "+token);
        if(body!=null) req.contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body));
        var response=mvc.perform(req).andReturn().getResponse();
        // Avoid dumping request headers, passwords or bearer tokens on a failed assertion.
        assertEquals(expected,response.getStatus(),method+" "+path);
        if(response.getStatus()==204) return null;
        return mapper.readTree(response.getContentAsString()).path("data");
    }
    String login(String name,String password) throws Exception {
        return call(null,"POST","auth/login",Map.of("username",name,"password",password),200).path("accessToken").asText();
    }
    int createUser() throws Exception {
        return call(adminToken,"POST","users",Map.of("username",prefix+".user","password","IntegrationPassword123",
            "fullName","RBAC user","email",prefix+".user@test.invalid"),201).path("id").asInt();
    }
    int role(String... permissions) throws Exception {
        int id=call(adminToken,"POST","roles",Map.of("name",prefix.toUpperCase(Locale.ROOT),"description","RBAC test"),201).path("id").asInt();
        permissions(id,List.of(permissions)); return id;
    }
    void permissions(int role,List<String> permissions) throws Exception {
        call(adminToken,"PUT","roles/"+role+"/permissions",Map.of("permissions",permissions),200);
    }
    void assign(int user,int role) throws Exception {
        call(adminToken,"PUT","users/"+user+"/roles",Map.of("roleIds",List.of(role)),200);
    }
    int systemRole(String name) {
        return admin.queryForObject("SELECT role_id FROM role WHERE scope='SYSTEM' AND role_name=?",Integer.class,name);
    }

    @Test void roleLifecycleUpdatesExistingTokenAndRecordsAudit() throws Exception {
        int user=createUser(), role=role("farms:read");
        String token=login(prefix+".user","IntegrationPassword123");
        call(token,"GET","farms",null,403);
        assign(user,role);
        call(token,"GET","farms",null,200);
        call(token,"GET","roles",null,403);
        call(token,"POST","farms",Map.of("name",prefix,"address","Can Tho","areaHa","1","description","Test"),403);
        call(adminToken,"PUT","roles/"+role,Map.of("name",prefix.toUpperCase(Locale.ROOT)+"_RENAMED","description","Updated"),200);
        assertEquals(prefix.toUpperCase(Locale.ROOT)+"_RENAMED",
            call(adminToken,"GET","users/"+user,null,200).path("systemRoles").get(0).asText());
        permissions(role,List.of());
        call(token,"GET","farms",null,403);
        permissions(role,List.of("farms:read"));
        call(token,"GET","farms",null,200);
        assign(user,systemRole("USER"));
        call(token,"GET","farms",null,403);
        assertTrue(admin.queryForObject("SELECT count(*) FROM audit_log WHERE actor_user_id=? AND action='ROLE_CHANGE'",Integer.class,actor)>=5);
    }

    @Test void delegatedManagerCannotElevateOrModifyAdministrator() throws Exception {
        int user=createUser(), role=role("users:assign","users:update","users:password","roles:permissions","roles:read");
        assign(user,role);
        String token=login(prefix+".user","IntegrationPassword123");
        call(token,"PUT","users/"+user+"/roles",Map.of("roleIds",List.of(systemRole("SYSTEM_ADMIN"))),403);
        call(token,"PUT","roles/"+role+"/permissions",Map.of("permissions",List.of("roles:permissions","farms:create")),403);
        call(token,"PUT","users/"+actor,Map.of("fullName","Changed","email",prefix+".changed@test.invalid"),403);
        call(token,"PUT","users/"+actor+"/password",Map.of("newPassword","ChangedPassword123"),403);
        call(token,"PUT","roles/"+systemRole("SYSTEM_ADMIN")+"/permissions",Map.of("permissions",List.of()),403);
        assertEquals(List.of(role),admin.queryForList("SELECT role_id FROM user_role WHERE user_id=?",Integer.class,user));
        assertEquals(0,admin.queryForObject("SELECT count(*) FROM audit_log WHERE actor_user_id=?",Integer.class,user));
        assertEquals(prefix,admin.queryForObject("SELECT full_name FROM app_user WHERE user_id=?",String.class,actor));
    }

    @Test void farmScopeCannotSupplySystemPermissions() throws Exception {
        int user=createUser();
        String token=login(prefix+".user","IntegrationPassword123");
        int farmRole=admin.queryForObject("INSERT INTO role(role_name,scope,description) VALUES (?,'FARM','Test') RETURNING role_id",
            Integer.class,prefix.toUpperCase(Locale.ROOT));
        admin.update("INSERT INTO auth_role_permission VALUES (?,'users:read')",farmRole);
        admin.update("INSERT INTO user_role VALUES (?,?)",user,farmRole);
        call(token,"GET","users",null,403);
        call(adminToken,"PUT","users/"+user+"/roles",Map.of("roleIds",List.of(farmRole)),404);
        admin.update("DELETE FROM user_role WHERE user_id=? AND role_id=?",user,systemRole("USER"));
        call(token,"GET","users",null,401);
        call(null,"POST","auth/login",Map.of("username",prefix+".user","password","IntegrationPassword123"),401);
    }

    @Test void deactivationAndPasswordResetPermanentlyRevokeOldSessions() throws Exception {
        int user=createUser(), role=role("farms:read"); assign(user,role);
        String first=login(prefix+".user","IntegrationPassword123");
        String second=login(prefix+".user","IntegrationPassword123");
        call(adminToken,"PUT","users/"+user+"/status",Map.of("status","INACTIVE"),200);
        call(first,"GET","farms",null,401);
        call(adminToken,"PUT","users/"+user+"/status",Map.of("status","ACTIVE"),200);
        call(first,"GET","farms",null,401); call(second,"GET","farms",null,401);
        String active=login(prefix+".user","IntegrationPassword123");
        call(active,"GET","farms",null,200);
        call(adminToken,"PUT","users/"+user+"/password",Map.of("newPassword","ChangedPassword456"),204);
        call(active,"GET","farms",null,401);
        call(null,"POST","auth/login",Map.of("username",prefix+".user","password","IntegrationPassword123"),401);
        call(login(prefix+".user","ChangedPassword456"),"GET","farms",null,200);
    }

    @ParameterizedTest
    @ValueSource(strings={"permission","account"})
    void queuedWriteRechecksAuthorizationAfterConcurrentRevocation(String revocation) throws Exception {
        int user=createUser(), role=role("roles:write"); assign(user,role);
        String token=login(prefix+".user","IntegrationPassword123");
        String name=prefix.toUpperCase(Locale.ROOT)+"_QUEUED";
        var pool=Executors.newSingleThreadExecutor();
        try(var connection=DriverManager.getConnection("jdbc:postgresql://127.0.0.1:55432/management_test","postgres", "")) {
            connection.setAutoCommit(false);
            try(var statement=connection.createStatement()) { statement.execute("SELECT pg_advisory_xact_lock(62024001)"); }
            var response=pool.submit(()->call(token,"POST","roles",Map.of("name",name,"description","Queued request"),403));
            try {
                long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);
                boolean waiting=false;
                while(System.nanoTime()<deadline) {
                    waiting=Boolean.TRUE.equals(admin.queryForObject("SELECT EXISTS(SELECT 1 FROM pg_locks WHERE locktype='advisory' AND objid=62024001 AND NOT granted)",Boolean.class));
                    if(waiting) break;
                    if(response.isDone()) { response.get(); fail("Request did not wait for the administrative lock"); }
                    Thread.sleep(20);
                }
                assertTrue(waiting,"Request must pass authentication and wait on the database lock");
                String sql=revocation.equals("permission")
                    ? "DELETE FROM auth_role_permission WHERE role_id=?"
                    : "UPDATE app_user SET status='INACTIVE' WHERE user_id=?";
                try(var statement=connection.prepareStatement(sql)) {
                    statement.setInt(1,revocation.equals("permission")?role:user); statement.executeUpdate();
                }
                connection.commit();
                response.get(10,TimeUnit.SECONDS);
                assertEquals(0,admin.queryForObject("SELECT count(*) FROM role WHERE role_name=?",Integer.class,name));
                assertEquals(0,admin.queryForObject("SELECT count(*) FROM audit_log WHERE actor_user_id=?",Integer.class,user));
            } finally { connection.rollback(); }
        } finally { pool.shutdownNow(); assertTrue(pool.awaitTermination(10,TimeUnit.SECONDS)); }
    }
}
