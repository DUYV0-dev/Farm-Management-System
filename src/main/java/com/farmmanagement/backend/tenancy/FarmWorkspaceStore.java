package com.farmmanagement.backend.tenancy;

import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class FarmWorkspaceStore {
    private final JdbcTemplate db;
    public FarmWorkspaceStore(JdbcTemplate db) { this.db=db; }
    public record Membership(int farmId,String farmName,String farmStatus,String status,String onboardingStatus,List<String> roles,List<String> permissions) {}
    public List<Membership> memberships(int uid) {
        return db.query("SELECT * FROM public.workspace_memberships(?)",(r,n)->new Membership(
            r.getInt("farm_id"),r.getString("farm_name"),r.getString("farm_status"),r.getString("member_status"),
            r.getString("onboarding_status"), strings(r.getString("roles")),strings(r.getString("permissions"))),uid);
    }
    private List<String> strings(String s) { return s==null || s.isBlank()?List.of():List.of(s.split(",")); }
    public void context(int uid,Integer farmId) {
        db.queryForObject("SELECT set_config('app.user_id',?,true)",String.class,Integer.toString(uid));
        db.queryForObject("SELECT set_config('app.farm_id',?,true)",String.class,farmId==null?"":farmId.toString());
    }
    public int registerFarm(int uid,String name,String address,java.math.BigDecimal area) {
        context(uid,null);
        int id=db.queryForObject("INSERT INTO public.farm(farm_name,address,area_ha,created_by,owner_user_id,onboarding_status) VALUES (?,?,?,?,?,'STAFF') RETURNING farm_id",
            Integer.class,name,address,area,uid,uid);
        member(id,uid,"OWNER"); return id;
    }
    public void member(int farm,int uid,String role) {
        db.update("INSERT INTO public.farm_member(farm_id,user_id) VALUES (?,?)",farm,uid);
        role(farm,uid,role);
    }
    public void role(int farm,int uid,String role) {
        db.update("DELETE FROM public.farm_member_role WHERE farm_id=? AND user_id=?",farm,uid);
        db.update("INSERT INTO public.farm_member_role(farm_id,user_id,role_id) SELECT ?,?,role_id FROM public.role WHERE scope='FARM' AND role_name=?",farm,uid,role);
    }
    public List<Map<String,Object>> staff(int farm) {
        return db.query("""
            SELECT u.user_id,u.username,u.full_name,u.email,m.status,
              string_agg(r.role_name,',' ORDER BY r.role_name) AS roles
            FROM public.farm_member m JOIN public.app_user u USING(user_id)
            JOIN public.farm_member_role mr ON mr.farm_id=m.farm_id AND mr.user_id=m.user_id
            JOIN public.role r USING(role_id) WHERE m.farm_id=?
            GROUP BY u.user_id,u.username,u.full_name,u.email,m.status ORDER BY u.user_id
            """,(r,n)->Map.<String,Object>of("id",r.getInt(1),"username",r.getString(2),"fullName",r.getString(3),
                "email",r.getString(4),"status",r.getString(5),"roles",strings(r.getString(6))),farm);
    }
    public void status(int farm,int uid,String status) {
        db.update("UPDATE public.farm_member SET status=? WHERE farm_id=? AND user_id=?",status,farm,uid);
    }
    public void complete(int farm) { db.update("UPDATE public.farm SET onboarding_status='READY' WHERE farm_id=?",farm); }
}
