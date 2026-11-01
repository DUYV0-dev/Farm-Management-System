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
    @Test void databasePromotionAndRevocationControlAdminAccessOnExistingSession() throws Exception {
        Account account=register("admincandidate");
        call("GET","/api/v1/users",account.token,null,null,403);
        mvc.perform(get("/admin/users").header("Authorization","Bearer "+account.token)).andExpect(status().isForbidden());
        admin.update("INSERT INTO user_role SELECT ?,role_id FROM role WHERE scope='SYSTEM' AND role_name='SYSTEM_ADMIN' ON CONFLICT DO NOTHING",account.uid);
        call("GET","/api/v1/users",account.token,null,null,200);
        mvc.perform(get("/admin/users").header("Authorization","Bearer "+account.token)).andExpect(status().isOk());
        mvc.perform(get("/admin/roles").header("Authorization","Bearer "+account.token)).andExpect(status().isOk());
        admin.update("DELETE FROM user_role ur USING role r WHERE ur.role_id=r.role_id AND r.scope='SYSTEM' AND r.role_name='SYSTEM_ADMIN' AND ur.user_id=?",account.uid);
        call("GET","/api/v1/users",account.token,null,null,403);
        mvc.perform(get("/admin/users").header("Authorization","Bearer "+account.token)).andExpect(status().isForbidden());
        // Revoking administration does not remove the ordinary account or its login.
        call("GET","/api/v1/auth/me",account.token,null,null,200);
        login(account.username);
    }
    @Test void currentFrontendIsAvailableAndHistoricalAssetsAreBlocked() throws Exception {
        mvc.perform(get("/")).andExpect(status().isOk()).andExpect(forwardedUrl("/index-shell.html"));
        mvc.perform(get("/admin")).andExpect(status().isOk()).andExpect(forwardedUrl("/index-shell.html"));
        mvc.perform(get("/admin/")).andExpect(status().isOk()).andExpect(forwardedUrl("/index-shell.html"));
        mvc.perform(get("/ui/prototypes/login.html")).andExpect(status().isUnauthorized());
        mvc.perform(get("/ui/prototypes/dashboard.html")).andExpect(status().isUnauthorized());
        mvc.perform(get("/ui/prototypes/application.css")).andExpect(status().isUnauthorized());
        mvc.perform(get("/ui/prototypes/demo.js")).andExpect(status().isUnauthorized());
        mvc.perform(get("/ui/prototypes/js/auth.js")).andExpect(status().isUnauthorized());
        mvc.perform(get("/overview-charts.js")).andExpect(status().isUnauthorized());
        mvc.perform(get("/features/workspace/staff.js")).andExpect(status().isOk());
        mvc.perform(get("/ui/features/farm/farm.html")).andExpect(status().isOk());
        mvc.perform(get("/ui/shared/api-client.js")).andExpect(status().isOk());
        mvc.perform(get("/frontend/dashboard.html")).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/#home"));
        mvc.perform(get("/frontend/prototypes/login.html")).andExpect(status().isUnauthorized());
    }

    int operationalSeason(Account owner) throws Exception {
        int plot=call("POST","/api/v1/plots",owner.token,owner.farm,plotBody(owner.farm),201).path("id").asInt();
        int crop=call("POST","/api/v1/crops",owner.token,owner.farm,cropBody(prefix+" crop"),201).path("id").asInt();
        return call("POST","/api/v1/seasons",owner.token,owner.farm,obj("plotId",plot,"cropId",crop,"status","IN_PROGRESS"),201).path("id").asInt();
    }
    @Test void operationalTasksPersistValidateRelationshipsAndKeepHistory() throws Exception {
        Account owner=register("owner"),other=register("other");
        int worker=staff(owner,"worker","WORKER");staff(other,"otherworker","WORKER");complete(owner);complete(other);
        int season=operationalSeason(owner);
        String today=java.time.LocalDate.now(java.time.ZoneId.of("Asia/Ho_Chi_Minh")).toString();
        Object body=obj("title","Real task","seasonId",season,"assignedTo",worker,"category","OTHER","startDate",today,"dueDate",today,"description","Persisted");
        var task=call("POST","/api/v1/operations/tasks",owner.token,owner.farm,body,201);
        int id=task.path("task_id").asInt();assertTrue(id>0);
        String calendar="/api/v1/operations/calendar?fromDate="+today+"&toDate="+today;
        assertEquals(id,call("GET",calendar,owner.token,owner.farm,null,200).path("items").get(0).path("task_id").asInt());
        assertEquals(0,call("GET",calendar,other.token,other.farm,null,200).path("total").asInt());
        assertEquals(0,call("GET",calendar+"&assignedTo="+owner.uid,owner.token,owner.farm,null,200).path("total").asInt());
        assertEquals("Real task",call("GET","/api/v1/operations/tasks",login(owner.username),owner.farm,null,200).path("items").get(0).path("title").asText());
        assertEquals(0,call("GET","/api/v1/operations/tasks",other.token,other.farm,null,200).path("total").asInt());
        call("GET","/api/v1/operations/tasks/"+id+"/history",other.token,other.farm,null,404);
        call("POST","/api/v1/operations/tasks",other.token,other.farm,body,409);
        String token=login(prefix+"worker");
        call("POST","/api/v1/operations/tasks",token,owner.farm,body,403);
        call("PUT","/api/v1/operations/tasks/"+id,token,owner.farm,obj("status","IN_PROGRESS","progress",50,"version",0,"note","Started"),200);
        call("PUT","/api/v1/operations/tasks/"+id,owner.token,owner.farm,obj("status","COMPLETED","progress",100,"version",0,"note","Stale"),409);
        call("PUT","/api/v1/operations/tasks/"+id,token,owner.farm,obj("status","COMPLETED","progress",100,"version",1,"note","Done"),200);
        assertEquals(3,call("GET","/api/v1/operations/tasks/"+id+"/history",owner.token,owner.farm,null,200).size());
        call("PUT","/api/v1/operations/tasks/"+id,token,owner.farm,obj("status","TODO","progress",0,"version",2,"note","Reopen"),409);
    }
    @Test void inventoryMovementsAreAtomicIdempotentAndTenantScoped() throws Exception {
        Account owner=register("owner"),other=register("other");staff(owner,"worker","WORKER");staff(other,"otherworker","WORKER");complete(owner);complete(other);
        int category=call("POST","/api/v1/material-categories",owner.token,owner.farm,obj("name",prefix+" cat","description",null),201).path("id").asInt();
        int material=call("POST","/api/v1/materials",owner.token,owner.farm,obj("categoryId",category,"name",prefix+" mat","unit","kg","manufacturer",null,"description",null),201).path("id").asInt();
        int warehouse=call("POST","/api/v1/warehouses",owner.token,owner.farm,obj("farmId",owner.farm,"name",prefix+" warehouse","location","North","capacityText",null),201).path("id").asInt();
        String key=UUID.randomUUID().toString();
        Object body=obj("warehouseId",warehouse,"materialId",material,"quantity","10.125","direction","IN","reason","Opening stock","requestKey",key);
        call("POST","/api/v1/operations/movements",owner.token,owner.farm,body,200);
        assertTrue(call("POST","/api/v1/operations/movements",owner.token,owner.farm,body,200).path("replayed").asBoolean());
        assertEquals(0,new java.math.BigDecimal("10.125").compareTo(admin.queryForObject("SELECT quantity FROM warehouse_inventory WHERE warehouse_id=? AND material_id=?",java.math.BigDecimal.class,warehouse,material)));
        call("POST","/api/v1/operations/movements",owner.token,owner.farm,obj("warehouseId",warehouse,"materialId",material,"quantity","20","direction","OUT","reason","Too much","requestKey",UUID.randomUUID().toString()),409);
        call("POST","/api/v1/operations/movements",other.token,other.farm,body,404);
        assertEquals(0,call("GET","/api/v1/operations/movements",other.token,other.farm,null,200).size());
        assertEquals(1,call("GET","/api/v1/operations/movements",owner.token,owner.farm,null,200).size());
        call("POST","/api/v1/operations/movements",owner.token,owner.farm,obj("warehouseId",warehouse,"materialId",material,"quantity","1","direction","OUT","reason","Different payload","requestKey",key),409);
        int season=operationalSeason(owner);
        String usageKey=UUID.randomUUID().toString(),today=java.time.LocalDate.now(java.time.ZoneId.of("Asia/Ho_Chi_Minh")).toString();
        Object usage=obj("warehouseId",warehouse,"materialId",material,"seasonId",season,"type","FERTILIZER","quantity","2.125","applicationDate",today,"notes","Used on crop","requestKey",usageKey);
        call("POST","/api/v1/operations/usage",owner.token,owner.farm,usage,200);
        call("POST","/api/v1/operations/usage",owner.token,owner.farm,usage,200);
        assertEquals(1,admin.queryForObject("SELECT count(*) FROM material_usage WHERE request_key=?::uuid",Integer.class,usageKey));
        assertEquals(0,new java.math.BigDecimal("8").compareTo(admin.queryForObject("SELECT quantity FROM warehouse_inventory WHERE warehouse_id=? AND material_id=?",java.math.BigDecimal.class,warehouse,material)));
        call("POST","/api/v1/operations/usage",owner.token,owner.farm,obj("warehouseId",warehouse,"materialId",material,"seasonId",season,"type","FERTILIZER","quantity","100","applicationDate",today,"notes","Insufficient","requestKey",UUID.randomUUID().toString()),409);
        var futures=new java.util.ArrayList<java.util.concurrent.CompletableFuture<Integer>>();
        for(int i=0;i<12;i++) futures.add(java.util.concurrent.CompletableFuture.supplyAsync(()->{
            try { return mvc.perform(post("/api/v1/operations/movements").header("Authorization","Bearer "+owner.token).header("X-Farm-Id",owner.farm).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(obj("warehouseId",warehouse,"materialId",material,"quantity","1","direction","OUT","reason","Concurrent issue","requestKey",UUID.randomUUID().toString())))).andReturn().getResponse().getStatus(); }
            catch(Exception e){throw new RuntimeException(e);}
        }));
        var outcomes=futures.stream().map(java.util.concurrent.CompletableFuture::join).toList();
        assertEquals(8,outcomes.stream().filter(code->code==200).count());assertEquals(4,outcomes.stream().filter(code->code==409).count());
        assertEquals(0,admin.queryForObject("SELECT quantity FROM warehouse_inventory WHERE warehouse_id=? AND material_id=?",java.math.BigDecimal.class,warehouse,material).signum());
        int supplier=call("POST","/api/v1/suppliers",owner.token,owner.farm,obj("name",prefix+" supplier"),201).path("id").asInt();
        int purchase=call("POST","/api/v1/purchase-orders",owner.token,owner.farm,obj("supplierId",supplier,"farmId",owner.farm,"warehouseId",warehouse,"items",java.util.List.of(obj("materialId",material,"quantity","5.125","unitPrice","100"))),201).path("id").asInt();
        call("PUT","/api/v1/purchase-orders/"+purchase+"/status",owner.token,owner.farm,obj("status","ORDERED"),200);
        call("POST","/api/v1/purchase-orders/"+purchase+"/receive",owner.token,owner.farm,null,200);
        call("POST","/api/v1/purchase-orders/"+purchase+"/receive",owner.token,owner.farm,null,409);
        call("GET","/api/v1/suppliers/"+supplier,other.token,other.farm,null,404);
        call("GET","/api/v1/purchase-orders/"+purchase,other.token,other.farm,null,404);
        assertEquals(0,new java.math.BigDecimal("5.125").compareTo(admin.queryForObject("SELECT quantity FROM warehouse_inventory WHERE warehouse_id=? AND material_id=?",java.math.BigDecimal.class,warehouse,material)));


    }
    @Test void harvestCreatesSaleableLotAndTraceabilityUsesActualRecords() throws Exception {
        Account owner=register("owner"),other=register("other");staff(owner,"worker","WORKER");staff(other,"otherworker","WORKER");complete(owner);complete(other);
        int season=operationalSeason(owner),unit=admin.queryForObject("SELECT unit_id FROM unit WHERE unit_code='kg'",Integer.class);
        java.time.LocalDate today=java.time.LocalDate.now(java.time.ZoneId.of("Asia/Ho_Chi_Minh"));
        call("PUT","/api/v1/farms/"+owner.farm+"/harvest-seasons/"+season,owner.token,owner.farm,obj("name",prefix+" season","startDate",today.minusDays(5).toString(),"endDate",today.plusDays(5).toString(),"unitId",unit),200);
        int harvest=call("POST","/api/v1/farms/"+owner.farm+"/harvests",owner.token,owner.farm,obj("seasonId",season,"harvestDate",today.toString(),"quantity","12.125","quality","A"),201).path("id").asInt();
        assertTrue(harvest>0);
        var trace=call("GET","/api/v1/operations/traceability/"+harvest,owner.token,owner.farm,null,200);
        assertEquals(owner.username+" farm",trace.path("harvest").path("farm_name").asText());
        assertEquals(1,trace.path("products").size());
        call("GET","/api/v1/operations/traceability/"+harvest,other.token,other.farm,null,404);
        assertEquals(0,call("GET","/api/v1/operations/products",other.token,other.farm,null,200).path("items").size());
        assertEquals(1,call("GET","/api/v1/operations/products",owner.token,owner.farm,null,200).path("items").size());
        int product=trace.path("products").get(0).path("product_id").asInt();
        int customer=call("POST","/api/v1/customers",owner.token,owner.farm,obj("name",prefix+" customer"),201).path("id").asInt();
        Object orderBody=obj("customerId",customer,"farmId",owner.farm,"status","CONFIRMED","items",java.util.List.of(obj("productId",product,"quantity","2.125","unitPrice","1000")));
        int order=call("POST","/api/v1/sales-orders",owner.token,owner.farm,orderBody,201).path("id").asInt();
        call("PUT","/api/v1/sales-orders/"+order+"/status",owner.token,owner.farm,obj("status","CONFIRMED"),200);
        assertEquals(0,new java.math.BigDecimal("10.000").compareTo(admin.queryForObject("SELECT quantity FROM harvested_product WHERE product_id=?",java.math.BigDecimal.class,product)));
        call("PUT","/api/v1/sales-orders/"+order+"/status",owner.token,owner.farm,obj("status","CANCELLED"),200);
        call("PUT","/api/v1/sales-orders/"+order+"/status",owner.token,owner.farm,obj("status","CANCELLED"),200);
        assertEquals(0,new java.math.BigDecimal("12.125").compareTo(admin.queryForObject("SELECT quantity FROM harvested_product WHERE product_id=?",java.math.BigDecimal.class,product)));
        call("GET","/api/v1/customers/"+customer,other.token,other.farm,null,404);
        call("GET","/api/v1/sales-orders/"+order,other.token,other.farm,null,404);
        call("PUT","/api/v1/operations/products/"+product,owner.token,owner.farm,obj("storageLocation","Cold store","status","QUARANTINED"),200);
        call("POST","/api/v1/sales-orders",owner.token,owner.farm,orderBody,409);

    }
    @Test void operationalApisRejectAnonymousRequestsAndDemoWrites() throws Exception {
        call("GET","/api/v1/operations/tasks",null,null,null,401);
        call("POST","/api/v1/traceability",null,null,obj("id","fake"),401);
        call("POST","/api/v1/activities",null,null,obj("title","fake"),401);
    }

    @Test void dataTransferValidatesBeforeWritingAndExportsOnlyTheSelectedFarm() throws Exception {
        Account owner=register("owner"),other=register("other");staff(owner,"worker","WORKER");staff(other,"otherworker","WORKER");complete(owner);complete(other);
        Object body=obj("version",1,"dataset","crops","rows",java.util.List.of(obj("name",prefix+" imported crop","category","Grain")));
        call("POST","/api/v1/data-transfer/crops/import?format=json&validateOnly=true",owner.token,owner.farm,body,200);
        assertEquals(0,call("GET","/api/v1/crops",owner.token,owner.farm,null,200).path("total").asInt());
        assertEquals(1,call("POST","/api/v1/data-transfer/crops/import?format=json&validateOnly=false",owner.token,owner.farm,body,200).path("imported").asInt());
        var exported=mvc.perform(get("/api/v1/data-transfer/crops/export?format=json").header("Authorization","Bearer "+owner.token).header("X-Farm-Id",owner.farm)).andExpect(status().isOk()).andReturn().getResponse();
        assertEquals(1,mapper.readTree(exported.getContentAsString()).path("rows").size());
        var empty=mvc.perform(get("/api/v1/data-transfer/crops/export?format=json").header("Authorization","Bearer "+other.token).header("X-Farm-Id",other.farm)).andExpect(status().isOk()).andReturn().getResponse();
        assertEquals(0,mapper.readTree(empty.getContentAsString()).path("rows").size());
        Object invalid=obj("version",1,"dataset","crops","rows",java.util.List.of(obj("name",prefix+" valid"),obj("name","")));
        call("POST","/api/v1/data-transfer/crops/import?format=json&validateOnly=false",owner.token,owner.farm,invalid,422);
        assertEquals(1,call("GET","/api/v1/crops",owner.token,owner.farm,null,200).path("total").asInt());
    }
    Account portalAccount(String suffix) throws Exception {
        String name=prefix+suffix;
        int uid=call("POST","/api/v1/auth/register",null,null,obj("username",name,"password",PASSWORD,"fullName",name,"email",name+"@test.invalid"),201).path("userId").asInt();
        return new Account(name,uid,0,login(name));
    }
    Account portalFarm(Account a,String suffix) throws Exception {
        int farm=call("POST","/api/v1/account/farms",a.token,null,obj("name",prefix+suffix,"address","Can Tho","area","20"),201).path("id").asInt();
        return new Account(a.username,a.uid,farm,a.token);
    }
    int saleable(Account owner) throws Exception {
        int season=operationalSeason(owner),unit=admin.queryForObject("SELECT unit_id FROM unit WHERE unit_code='kg'",Integer.class);
        var today=java.time.LocalDate.now();
        call("PUT","/api/v1/farms/"+owner.farm+"/harvest-seasons/"+season,owner.token,owner.farm,obj("name",prefix+" season","startDate",today.minusDays(5).toString(),"endDate",today.plusDays(5).toString(),"unitId",unit),200);
        int harvest=call("POST","/api/v1/farms/"+owner.farm+"/harvests",owner.token,owner.farm,obj("seasonId",season,"harvestDate",today.toString(),"quantity","12.125","quality","A"),201).path("id").asInt();
        return admin.queryForObject("SELECT product_id FROM harvested_product WHERE harvest_id=?",Integer.class,harvest);
    }
    JsonNode publish(Account a,int product,int version,boolean published) throws Exception {
        return call("PUT","/api/v1/account/farms/"+a.farm+"/listings/"+product,a.token,null,obj("name",prefix+" produce","description","Fresh from farm","price","1000","published",published,"version",version),200);
    }
    Map<String,Object> checkout(int listing,int version,String quantity) {
        return obj("requestKey",UUID.randomUUID().toString(),"buyerName","Guest","phone","0912345678","email","guest@test.invalid","address","Private guest address","note","Private note","items",List.of(obj("listingId",listing,"quantity",new java.math.BigDecimal(quantity),"version",version)));
    }
    @Test void accountRegistrationHasNoFarmAndOwnersCanManageMultipleFarmsWithoutStaff() throws Exception {
        Account a=portalAccount("a"),b=portalAccount("b");
        assertEquals(0,call("GET","/api/v1/workspace",a.token,null,null,200).size());
        assertEquals(0,call("GET","/api/v1/account",a.token,null,null,200).path("farms").size());
        Account f1=portalFarm(a," one"),f2=portalFarm(a," two"),f3=portalFarm(b," other");
        call("POST","/api/v1/plots",a.token,f1.farm,plotBody(f1.farm),201);
        var data=call("GET","/api/v1/account",a.token,f3.farm,null,200); // Ignore any stale farm header.
        assertEquals(2,data.path("farms").size());assertEquals(0,data.path("people").size());
        assertEquals(1,call("GET","/api/v1/account",b.token,null,null,200).path("farms").size());
        call("PUT","/api/v1/account/profile",a.token,null,obj("fullName","Updated owner","email",a.username+"@test.invalid","phone","0912345678","bio","Growing together"),200);
        assertEquals("Updated owner",call("GET","/api/v1/account",a.token,null,null,200).path("profile").path("fullName").asText());
        int employee=staff(f1,"employee","WORKER");String token=login(prefix+"employee");
        assertEquals(0,call("GET","/api/v1/account",token,f1.farm,null,200).path("farms").size());
        assertEquals(1,call("GET","/api/v1/account",a.token,null,null,200).path("people").size());
        call("GET","/api/v1/account?from=2026-12-01&to=2026-01-01",a.token,null,null,422);
    }
    @Test void publicStoreOrdersArePrivateTenantScopedAndStockDeductsOnlyOnceOnAcceptance() throws Exception {
        Account a=portalFarm(portalAccount("a")," farm a"),b=portalFarm(portalAccount("b")," farm b");
        int product=saleable(a),otherProduct=saleable(b);int employee=staff(a,"employee","WORKER");
        var draft=publish(a,product,0,false);int listing=draft.path("id").asInt();
        var catalog=call("GET","/api/v1/store/products",null,null,null,200);
        assertFalse(catalog.toString().contains(prefix+" produce"));
        publish(a,product,1,true);var other=publish(b,otherProduct,0,true);
        call("PUT","/api/v1/account/farms/"+a.farm+"/listings/"+product,b.token,null,obj("name","Attack","description","","price","1","published",true,"version",2),403);
        call("PUT","/api/v1/account/farms/"+a.farm+"/listings/"+product,login(prefix+"employee"),null,obj("name","Attack","description","","price","1","published",true,"version",2),403);
        call("POST","/api/v1/store/orders",null,null,checkout(listing,1,"1"),409);
        call("POST","/api/v1/store/orders",null,null,checkout(listing,2,"100"),409);
        var mixed=checkout(listing,2,"1");mixed.put("items",List.of(obj("listingId",listing,"quantity",1,"version",2),obj("listingId",other.path("id").asInt(),"quantity",1,"version",1)));
        call("POST","/api/v1/store/orders",null,null,mixed,409);
        var body=checkout(listing,2,"2.125");var receipt=call("POST","/api/v1/store/orders",null,null,body,201);String id=receipt.path("id").asText();
        assertEquals(id,call("POST","/api/v1/store/orders",null,null,body,201).path("id").asText());
        assertEquals("PENDING",receipt.path("status").asText());assertFalse(receipt.toString().contains("guest@test.invalid"));assertFalse(receipt.toString().contains("Private"));assertFalse(receipt.toString().contains("0912345678"));
        assertEquals(0,new java.math.BigDecimal("12.125").compareTo(admin.queryForObject("SELECT quantity FROM harvested_product WHERE product_id=?",java.math.BigDecimal.class,product)));
        call("GET","/api/v1/account/farms/"+a.farm+"/orders/"+id,b.token,null,null,403);
        call("POST","/api/v1/account/farms/"+b.farm+"/orders/"+id+"/decision",b.token,null,obj("accept",true),404);
        var accepted=call("POST","/api/v1/account/farms/"+a.farm+"/orders/"+id+"/decision",a.token,null,obj("accept",true),200);
        assertEquals(accepted.path("salesOrderId").asInt(),call("POST","/api/v1/account/farms/"+a.farm+"/orders/"+id+"/decision",a.token,null,obj("accept",true),200).path("salesOrderId").asInt());
        assertEquals(0,new java.math.BigDecimal("10.000").compareTo(admin.queryForObject("SELECT quantity FROM harvested_product WHERE product_id=?",java.math.BigDecimal.class,product)));
        assertEquals("CONFIRMED",call("GET","/api/v1/store/orders/"+id,null,null,null,200).path("status").asText());
        assertEquals(0,call("GET","/api/v1/account",a.token,null,null,200).path("revenue").decimalValue().signum());
        int so=accepted.path("salesOrderId").asInt();call("PUT","/api/v1/sales-orders/"+so+"/status",a.token,a.farm,obj("status","DELIVERED"),200);
        assertEquals(0,new java.math.BigDecimal("2125").compareTo(call("GET","/api/v1/account",a.token,null,null,200).path("revenue").decimalValue()));
        var changed=new LinkedHashMap<>(body);changed.put("note","Changed request");call("POST","/api/v1/store/orders",null,null,changed,409);
        var rejected=call("POST","/api/v1/store/orders",null,null,checkout(listing,2,"1"),201);
        call("POST","/api/v1/account/farms/"+a.farm+"/orders/"+rejected.path("id").asText()+"/decision",a.token,null,obj("accept",false),200);
        assertEquals(0,new java.math.BigDecimal("10.000").compareTo(admin.queryForObject("SELECT quantity FROM harvested_product WHERE product_id=?",java.math.BigDecimal.class,product)));
    }

    @Test void simultaneousStoreRetriesAndCompetingAcceptancesCannotOversell() throws Exception {
        Account a=portalFarm(portalAccount("owner")," concurrent farm");int product=saleable(a),listing=publish(a,product,0,true).path("id").asInt();
        var body=checkout(listing,1,"10");var retries=new ArrayList<java.util.concurrent.CompletableFuture<String>>();
        for(int i=0;i<6;i++) retries.add(java.util.concurrent.CompletableFuture.supplyAsync(()->{
            try { return call("POST","/api/v1/store/orders",null,null,body,201).path("id").asText(); } catch(Exception e){throw new RuntimeException(e);}
        }));
        var ids=retries.stream().map(java.util.concurrent.CompletableFuture::join).distinct().toList();assertEquals(1,ids.size());
        String second=call("POST","/api/v1/store/orders",null,null,checkout(listing,1,"10"),201).path("id").asText();
        var accepts=new ArrayList<java.util.concurrent.CompletableFuture<Integer>>();
        for(String id:List.of(ids.getFirst(),second))accepts.add(java.util.concurrent.CompletableFuture.supplyAsync(()->{
            try {return mvc.perform(post("/api/v1/account/farms/"+a.farm+"/orders/"+id+"/decision").header("Authorization","Bearer "+a.token).contentType(MediaType.APPLICATION_JSON).content("{\"accept\":true}")).andReturn().getResponse().getStatus();}catch(Exception e){throw new RuntimeException(e);}
        }));
        var statuses=accepts.stream().map(java.util.concurrent.CompletableFuture::join).sorted().toList();assertEquals(List.of(200,409),statuses);
        assertEquals(0,new java.math.BigDecimal("2.125").compareTo(admin.queryForObject("SELECT quantity FROM harvested_product WHERE product_id=?",java.math.BigDecimal.class,product)));
        assertEquals(1,admin.queryForObject("SELECT count(*) FROM sales_order WHERE farm_id=?",Integer.class,a.farm));
        assertEquals(1,admin.queryForObject("SELECT count(*) FROM customer WHERE tenant_farm_id=?",Integer.class,a.farm));
        call("POST","/api/v1/store/orders",null,null,obj("requestKey",UUID.randomUUID().toString(),"buyerName","Guest","phone","0912345678","email","","address","Can Tho","note","","items",List.of(obj("listingId",listing,"quantity",0,"version",1))),422);
    }

}
