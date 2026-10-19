package com.farmmanagement.backend;

import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.*;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@EnabledIfSystemProperty(named="workspace.test.db",matches="true")
@SpringBootTest(properties={"spring.datasource.url=jdbc:postgresql://127.0.0.1:55432/management_test",
    "spring.datasource.username=farm_app","spring.datasource.password=",
    "auth.jwt.secret=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=","logging.file.name=target/workspace-integration.log"})
@AutoConfigureMockMvc
class FarmWorkspaceIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired PasswordEncoder passwords;
    final JdbcTemplate admin=new JdbcTemplate(new DriverManagerDataSource("jdbc:postgresql://127.0.0.1:55432/management_test","postgres",""));
    String prefix;
    static final String PASSWORD="WorkspacePassword123";
    record Account(String username,int uid,int farm,String token) {}
    @BeforeEach void setup() { prefix="w"+UUID.randomUUID().toString().replace("-","").substring(0,10); }
    Map<String,Object> obj(Object... pairs) {
        var result=new LinkedHashMap<String,Object>();for(int i=0;i<pairs.length;i+=2) result.put((String)pairs[i],pairs[i+1]); return result;
    }
    JsonNode call(String method,String path,String token,Integer farm,Object body,int status) throws Exception {
        var request=request(HttpMethod.valueOf(method),path);
        if(token!=null) request.header("Authorization","Bearer "+token);
        if(farm!=null) request.header("X-Farm-Id",farm);
        if(body!=null) request.contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body));
        var response=mvc.perform(request).andExpect(status().is(status)).andReturn().getResponse();
        return response.getContentAsString().isEmpty()?mapper.createObjectNode():mapper.readTree(response.getContentAsString()).path("data");
    }
    Account register(String suffix) throws Exception {
        String name=prefix+suffix;
        var result=call("POST","/api/v1/auth/register",null,null,obj("username",name,"password",PASSWORD,"fullName",name,"email",name+"@test.invalid",
            "farmName",name+" farm","address","Can Tho","areaHa","20"),201);
        return new Account(name,result.path("userId").asInt(),result.path("farmId").asInt(),login(name));
    }
    String login(String name) throws Exception { return call("POST","/api/v1/auth/login",null,null,obj("username",name,"password",PASSWORD),200).path("accessToken").asText(); }
    int staff(Account owner,String suffix,String job) throws Exception {
        String name=prefix+suffix;
        return call("POST","/api/v1/workspace/"+owner.farm+"/staff",owner.token,owner.farm,
            obj("username",name,"password",PASSWORD,"fullName",name,"email",name+"@test.invalid","job",job),201).path("id").asInt();
    }
    void complete(Account owner) throws Exception { call("POST","/api/v1/workspace/"+owner.farm+"/complete",owner.token,owner.farm,null,204); }
    Object plotBody(int farm) { return obj("farmId",farm,"name",prefix+" plot","areaHa","2","soilType","Loam","location","North","description",null); }
    Object cropBody(String name) { return obj("name",name,"scientificName",null,"category","Grain","growthDays",null,"description",null); }
    @Test void registrationStaffSetupAndPersistence() throws Exception {
        Account owner=register("owner");
        assertEquals("USER",call("GET","/api/v1/auth/me",owner.token,null,null,200).path("user").path("systemRoles").get(0).asText());
        call("GET","/api/v1/farms",owner.token,owner.farm,null,403);
        call("POST","/api/v1/workspace/"+owner.farm+"/complete",owner.token,owner.farm,null,409);
        staff(owner,"staff","AGRONOMIST"); complete(owner);
        int plot=call("POST","/api/v1/plots",owner.token,owner.farm,plotBody(owner.farm),201).path("id").asInt();
        String newToken=login(owner.username);
        assertEquals(owner.farm,call("GET","/api/v1/plots/"+plot,newToken,owner.farm,null,200).path("farmId").asInt());
        call("GET","/api/v1/users",owner.token,owner.farm,null,403);
        assertEquals(owner.uid,admin.queryForObject("SELECT owner_user_id FROM farm WHERE farm_id=?",Integer.class,owner.farm));
        assertTrue(admin.queryForObject("SELECT password_hash FROM app_user WHERE user_id=?",String.class,owner.uid).startsWith("$argon2id$"));
    }
    @Test void farmIsolationAndJobRevocationTakeEffectOnExistingTokens() throws Exception {
        Account a=register("a"),b=register("b");
        int employee=staff(a,"employee","AGRONOMIST");staff(b,"other","WORKER");complete(a);complete(b);
        int plot=call("POST","/api/v1/plots",a.token,a.farm,plotBody(a.farm),201).path("id").asInt();
        call("GET","/api/v1/plots/"+plot,b.token,b.farm,null,404);
        call("GET","/api/v1/plots",b.token,a.farm,null,403);
        call("POST","/api/v1/plots",b.token,b.farm,plotBody(a.farm),409);
        assertEquals(0,call("GET","/api/v1/plots",b.token,b.farm,null,200).path("total").asInt());
        String employeeToken=login(prefix+"employee");
        call("POST","/api/v1/crops",employeeToken,a.farm,cropBody(prefix+" rice"),201);
        call("POST","/api/v1/crops",b.token,b.farm,cropBody(prefix+" rice"),201);
        call("GET","/api/v1/workspace/"+a.farm+"/staff",employeeToken,a.farm,null,403);
        call("PUT","/api/v1/workspace/"+a.farm+"/staff/"+employee,a.token,a.farm,obj("job","WORKER","status","ACTIVE"),204);
        call("POST","/api/v1/crops",employeeToken,a.farm,cropBody(prefix+" forbidden"),403);
        call("PUT","/api/v1/workspace/"+a.farm+"/staff/"+employee,a.token,a.farm,obj("job","WORKER","status","INACTIVE"),204);
        call("GET","/api/v1/plots",employeeToken,a.farm,null,403);
    }
    @Test void registrationIsAtomicAndCannotGrantAdministrativeRoles() throws Exception {
        Account owner=register("owner");
        long users=admin.queryForObject("SELECT count(*) FROM app_user",Long.class);
        call("POST","/api/v1/auth/register",null,null,obj("username",prefix+"duplicate","password",PASSWORD,"fullName","Duplicate","email",prefix+"duplicate@test.invalid",
            "farmName",owner.username+" farm","address","Address","areaHa","10"),409);
        assertEquals(users,admin.queryForObject("SELECT count(*) FROM app_user",Long.class));
        call("POST","/api/v1/workspace/"+owner.farm+"/staff",owner.token,owner.farm,obj("username",prefix+"admin","password",PASSWORD,
            "fullName","Admin","email",prefix+"admin@test.invalid","job","SYSTEM_ADMIN"),422);
        assertEquals(users,admin.queryForObject("SELECT count(*) FROM app_user",Long.class));
    }
    @Test void systemAdministratorHasSeparateAccountManagement() throws Exception {
        Account owner=register("owner");staff(owner,"worker","WORKER");complete(owner);
        String name=prefix+"admin";
        int id=admin.queryForObject("INSERT INTO app_user(username,password_hash,full_name,email) VALUES (?,?,?,?) RETURNING user_id",Integer.class,name,passwords.encode(PASSWORD),name,name+"@test.invalid");
        admin.update("INSERT INTO user_role SELECT ?,role_id FROM role WHERE scope='SYSTEM' AND role_name='SYSTEM_ADMIN'",id);
        String token=login(name);
        call("GET","/api/v1/users",token,null,null,200);
        call("GET","/api/v1/farms",token,null,null,403);
        call("GET","/api/v1/workspace/"+owner.farm+"/staff",token,null,null,403);
    }
    @Test void frontendModulesAndLegacyEntryAreAvailable() throws Exception {
        mvc.perform(get("/")).andExpect(status().isOk()).andExpect(forwardedUrl("/index-shell.html"));
        mvc.perform(get("/ui/prototypes/login.html")).andExpect(status().isOk());
        mvc.perform(get("/ui/prototypes/dashboard.html")).andExpect(status().isOk());
        mvc.perform(get("/ui/prototypes/application.css")).andExpect(status().isOk());
        mvc.perform(get("/ui/prototypes/demo.js")).andExpect(status().isUnauthorized());
        mvc.perform(get("/ui/prototypes/js/auth.js")).andExpect(status().isOk());
        mvc.perform(get("/overview-charts.js")).andExpect(status().isOk());
        mvc.perform(get("/features/workspace/staff.js")).andExpect(status().isOk());
        mvc.perform(get("/ui/features/farm/farm.html")).andExpect(status().isOk());
        mvc.perform(get("/ui/shared/api-client.js")).andExpect(status().isOk());
        mvc.perform(get("/frontend/dashboard.html")).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/#home"));
        mvc.perform(get("/frontend/prototypes/login.html")).andExpect(status().isUnauthorized());
    }
}
