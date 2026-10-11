package com.farmmanagement.backend;

import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import static org.junit.jupiter.api.Assertions.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.*;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import tools.jackson.databind.*;

@EnabledIfSystemProperty(named="management.test.db", matches="true")
@SpringBootTest(properties={
    "spring.datasource.url=jdbc:postgresql://127.0.0.1:55432/management_test",
    "spring.datasource.username=farm_app", "spring.datasource.password=",
    "auth.jwt.secret=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
    "logging.file.name=target/management-integration.log"
})
@AutoConfigureMockMvc
class ManagementIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired PasswordEncoder passwords;
    final JdbcTemplate admin = new JdbcTemplate(new DriverManagerDataSource(
        "jdbc:postgresql://127.0.0.1:55432/management_test", "postgres", ""));
    String token, prefix;
    int actor;

    @BeforeEach void login() throws Exception {
        prefix = "t"+UUID.randomUUID().toString().replace("-","").substring(0,12);
        actor = admin.queryForObject("INSERT INTO app_user(username,password_hash,full_name,email) VALUES (?,?,?,?) RETURNING user_id",
            Integer.class,prefix,passwords.encode("IntegrationPassword123"),prefix,prefix+"@test.invalid");
        admin.update("INSERT INTO user_role SELECT ?,role_id FROM role WHERE role_name='SYSTEM_ADMIN' AND scope='SYSTEM'",actor);
        var result=mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsString(obj("username",prefix,"password","IntegrationPassword123"))))
            .andExpect(status().isOk()).andReturn();
        token=mapper.readTree(result.getResponse().getContentAsString()).path("data").path("accessToken").asText();
    }
    Map<String,Object> obj(Object... pairs) {
        var body=new LinkedHashMap<String,Object>();
        for(int i=0;i<pairs.length;i+=2) body.put((String)pairs[i],pairs[i+1]);
        return body;
    }
    JsonNode call(String method,String path,Object body,int expected) throws Exception {
        var req=request(HttpMethod.valueOf(method),"/api/v1/"+path).header("Authorization","Bearer "+token);
        if(body!=null) req.contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body));
        var response=mvc.perform(req).andExpect(status().is(expected)).andReturn().getResponse();
        return mapper.readTree(response.getContentAsString()).path("data");
    }
    Map<String,Object> farmBody(String area) { return obj("name",prefix+" farm","address","Can Tho","areaHa",area,"description","Description"); }
    int farm(String area) throws Exception { return call("POST","farms",farmBody(area),201).path("id").asInt(); }
    Map<String,Object> plotBody(int farm,String name,String area) { return obj("farmId",farm,"name",name,"areaHa",area,"soilType","Loam","location","North","description",null); }
    int plot(int farm,String area) throws Exception { return call("POST","plots",plotBody(farm,prefix+" plot",area),201).path("id").asInt(); }
    Map<String,Object> cropBody() { return obj("name",prefix+" crop","scientificName","Rice","category","Grain","growthDays",null,"description","Catalog"); }
    int crop() throws Exception { return call("POST","crops",cropBody(),201).path("id").asInt(); }
    int[] stockSetup() throws Exception {
        int f=farm("10");
        int c=call("POST","material-categories",obj("name",prefix+" category","description","Fertilizer"),201).path("id").asInt();
        int m=call("POST","materials",obj("categoryId",c,"name",prefix+" material","unit","kg","manufacturer",null,"description","NPK"),201).path("id").asInt();
        int w=call("POST","warehouses",obj("farmId",f,"name",prefix+" warehouse","location","Gate","capacityText","100 m2"),201).path("id").asInt();
        return new int[]{f,c,m,w};
    }

    @Test void farmPlotCropLifecyclePersistsAndEnforcesRelationships() throws Exception {
        int f=farm("10"), p=plot(f,"6"), c=crop();
        assertTrue(call("GET","crops/"+c,null,200).path("growthDays").isNull());
        int season=call("POST","seasons",obj("plotId",p,"cropId",c,"status","IN_PROGRESS"),201).path("id").asInt();
        call("POST","plots",plotBody(f,prefix+" overflow","5"),409);
        call("PUT","farms/"+f,farmBody("5"),409);
        for(String resource:List.of("farms/"+f,"plots/"+p,"crops/"+c)) call("PUT",resource+"/status",obj("status","INACTIVE"),409);
        call("PUT","seasons/"+season+"/status",obj("status","COMPLETED"),200);
        call("PUT","plots/"+p+"/status",obj("status","INACTIVE"),200);
        call("PUT","crops/"+c+"/status",obj("status","INACTIVE"),200);
        call("PUT","farms/"+f+"/status",obj("status","INACTIVE"),200);
        call("PUT","plots/"+p+"/status",obj("status","ACTIVE"),409);
        call("POST","seasons",obj("plotId",p,"cropId",c,"status","PLANNED"),409);
        assertEquals("INACTIVE",call("GET","farms/"+f,null,200).path("status").asText());
        assertEquals(1,call("GET","farms?search="+prefix+"&page=0&size=10",null,200).path("total").asInt());
        assertEquals(0,call("GET","farms?search="+prefix+"&page=1&size=10",null,200).path("items").size());
        assertTrue(admin.queryForObject("SELECT count(*) FROM audit_log WHERE actor_user_id=?",Integer.class,actor)>=8);
    }

    @Test void inventoryLifecycleIsAtomicAndBlocksInvalidDeactivation() throws Exception {
        int[] ids=stockSetup(); int f=ids[0],c=ids[1],m=ids[2],w=ids[3];
        var first=call("POST","inventory",obj("warehouseId",w,"materialId",m,"quantity","12.125"),200);
        int inv=first.path("id").asInt();
        var next=call("POST","inventory",obj("warehouseId",w,"materialId",m,"quantity","7"),200);
        assertEquals(inv,next.path("id").asInt()); assertEquals(7,next.path("quantity").asInt());
        assertEquals(1,call("GET","inventory?warehouseId="+w,null,200).path("total").asInt());
        assertEquals(m,call("GET","inventory/"+inv,null,200).path("materialId").asInt());
        for(String resource:List.of("materials/"+m,"warehouses/"+w,"material-categories/"+c,"farms/"+f))
            call("PUT",resource+"/status",obj("status","INACTIVE"),409);
        call("PUT","materials/"+m,obj("name",prefix+" material","unit","liter","manufacturer",null,"description",null),409);
        call("POST","inventory",obj("warehouseId",w,"materialId",m,"quantity","0"),200);
        call("PUT","materials/"+m+"/status",obj("status","INACTIVE"),200);
        call("PUT","material-categories/"+c+"/status",obj("status","INACTIVE"),200);
        call("PUT","warehouses/"+w+"/status",obj("status","INACTIVE"),200);
        call("PUT","farms/"+f+"/status",obj("status","INACTIVE"),200);
        call("POST","inventory",obj("warehouseId",w,"materialId",m,"quantity","1"),409);
        assertEquals(0,call("GET","inventory/"+inv,null,200).path("quantity").asInt());
        call("PUT","materials/"+m+"/status",obj("status","ACTIVE"),409);
        call("PUT","warehouses/"+w+"/status",obj("status","ACTIVE"),409);
    }

    @Test void updatesDuplicatesAndInputValidation() throws Exception {
        int[] ids=stockSetup(); int f=ids[0],c=ids[1],m=ids[2],w=ids[3];
        call("POST","farms",farmBody("10"),409);
        call("POST","material-categories",obj("name","  "+prefix.toUpperCase()+" CATEGORY  ","description",null),409);
        call("PUT","material-categories/"+c,obj("name",prefix+" renamed","description","Updated"),200);
        assertEquals("Updated",call("GET","material-categories/"+c,null,200).path("description").asText());
        call("PUT","materials/"+m,obj("name",prefix+" renamed","unit","bag","manufacturer","Factory","description","Updated"),200);
        assertEquals("bag",call("GET","materials/"+m,null,200).path("unit").asText());
        call("PUT","warehouses/"+w,obj("name",prefix+" renamed","location","West","capacityText","50 m2"),200);
        assertEquals("West",call("GET","warehouses/"+w,null,200).path("location").asText());
        int p=plot(f,"2"), crop=crop();
        var pb=plotBody(f,prefix+" updated","3"); pb.remove("farmId"); call("PUT","plots/"+p,pb,200);
        var cb=cropBody(); cb.put("growthDays","90"); call("PUT","crops/"+crop,cb,200);
        assertEquals(90,call("GET","crops/"+crop,null,200).path("growthDays").asInt());
        for(String value:List.of("NaN","Infinity","-1","0","1.001","10000000")) call("PUT","farms/"+f,farmBody(value),422);
        for(String value:List.of("-1","0.0001","1000000000","NaN")) call("POST","inventory",obj("warehouseId",w,"materialId",m,"quantity",value),422);
        call("POST","plots",plotBody(2147483647,"Missing","1"),409);
        call("GET","farms/2147483647",null,404);
        call("GET","inventory?page=-1",null,422);
        call("GET","materials?unknown=value",null,422);
        call("POST","farms",obj("name","only name"),422);
    }

    @Test void everyModuleRequiresExactPermissionsAndRevocationIsImmediate() throws Exception {
        admin.update("DELETE FROM user_role WHERE user_id=?",actor);
        admin.update("INSERT INTO user_role SELECT ?,role_id FROM role WHERE role_name='USER' AND scope='SYSTEM'",actor);
        for(String path:List.of("farms","plots","crops","seasons","material-categories","materials","warehouses","inventory")) {
            mvc.perform(get("/api/v1/"+path)).andExpect(status().isUnauthorized());
            call("GET",path,null,403);
        }
        call("POST","farms",farmBody("10"),403);
        call("POST","inventory",obj("warehouseId",1,"materialId",1,"quantity","0"),403);
        admin.update("INSERT INTO user_role SELECT ?,role_id FROM role WHERE role_name='SYSTEM_ADMIN' AND scope='SYSTEM'",actor);
        for(String path:List.of("farms","plots","crops","seasons","material-categories","materials","warehouses","inventory")) call("GET",path,null,200);
        admin.update("UPDATE auth_session SET revoked_at=CURRENT_TIMESTAMP WHERE user_id=?",actor);
        call("GET","inventory",null,401);
    }

    @Test void packagedUiAndProtectedFarmRouteAreAvailable() throws Exception {
        mvc.perform(get("/")).andExpect(status().isOk());
        for(String path:List.of("/ui/farm.html","/ui/material.html","/ui/js/api-client.js","/ui/js/farm.js","/ui/js/material.js","/ui/css/connected.css"))
            mvc.perform(get(path)).andExpect(status().isOk());
        mvc.perform(get("/admin/farms").header("Authorization","Bearer "+token)).andExpect(status().isOk());
    }

    @Test void concurrentPlotWritesCannotOverbookFarm() throws Exception {
        int f=farm("10");
        try(var pool=Executors.newFixedThreadPool(2)) {
            var gate=new CountDownLatch(1);
            var tasks=new ArrayList<Future<Boolean>>();
            for(int i=0;i<2;i++) {
                final int n=i;
                tasks.add(pool.submit(()-> {
                    gate.await();
                    try(var db=DriverManager.getConnection("jdbc:postgresql://127.0.0.1:55432/management_test","farm_app","");
                        var stmt=db.prepareStatement("INSERT INTO plot(farm_id,plot_name,area_ha) VALUES (?,?,7)")) {
                        stmt.setInt(1,f);stmt.setString(2,prefix+" concurrent "+n);stmt.executeUpdate();return true;
                    } catch(SQLException ex) { assertEquals("23514",ex.getSQLState());return false; }
                }));
            }
            gate.countDown(); int accepted=0;
            for(var task:tasks) if(task.get(10,TimeUnit.SECONDS)) accepted++;
            assertEquals(1,accepted);
            assertEquals(7,admin.queryForObject("SELECT sum(area_ha) FROM plot WHERE farm_id=?",Integer.class,f));
        }
    }

    @Test void concurrentInventoryUpsertsKeepOneRow() throws Exception {
        int[] ids=stockSetup(); int m=ids[2],w=ids[3];
        try(var pool=Executors.newFixedThreadPool(2)) {
            var gate=new CountDownLatch(1); var tasks=new ArrayList<Future<?>>();
            for(int i=1;i<=2;i++) { final int qty=i;
                tasks.add(pool.submit(()-> {
                    gate.await();
                    call("POST","inventory",obj("warehouseId",w,"materialId",m,"quantity",Integer.toString(qty)),200);return null;
                }));
            }
            gate.countDown();for(var task:tasks) task.get(10,TimeUnit.SECONDS);
            assertEquals(1,admin.queryForObject("SELECT count(*) FROM warehouse_inventory WHERE warehouse_id=? AND material_id=?",Integer.class,w,m));
        }
    }
}
