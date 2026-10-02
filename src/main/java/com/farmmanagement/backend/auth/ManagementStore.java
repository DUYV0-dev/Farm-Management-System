package com.farmmanagement.backend.auth;

import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ManagementStore {
    private final JdbcTemplate db;
    public ManagementStore(JdbcTemplate db) { this.db = db; }
    public record Role(int id, String name, String description, List<String> permissions) {}

    // Serialize administrative mutations, including last-administrator checks.
    public void lockAdministration() { db.execute("SELECT pg_advisory_xact_lock(62024001)"); }
    public List<Integer> userIds(int offset, int limit, String search) {
        return db.queryForList("""
            SELECT user_id FROM public.app_user
            WHERE strpos(lower(username),lower(?))>0 OR strpos(lower(full_name),lower(?))>0
               OR strpos(lower(email),lower(?))>0 ORDER BY user_id LIMIT ? OFFSET ?
            """,Integer.class,search,search,search,limit,offset);
    }
    public long userCount(String search) {
        return db.queryForObject("""
            SELECT count(*) FROM public.app_user
            WHERE strpos(lower(username),lower(?))>0 OR strpos(lower(full_name),lower(?))>0
               OR strpos(lower(email),lower(?))>0
            """,Long.class,search,search,search);
    }
    public int createUser(String username, String hash, String fullName, String email) {
        return db.queryForObject("""
            INSERT INTO public.app_user(username,password_hash,full_name,email,status)
            VALUES (?,?,?,?,'ACTIVE') RETURNING user_id
            """,Integer.class,username,hash,fullName,email);
    }
    public void updateUser(int id, String fullName, String email) {
        db.update("UPDATE public.app_user SET full_name=?,email=? WHERE user_id=?",fullName,email,id);
    }
    public void status(int id, String status) {
        db.update("UPDATE public.app_user SET status=? WHERE user_id=?",status,id);
    }
    public List<String> rolePermissions(int id) {
        return db.queryForList("SELECT permission_code FROM public.auth_role_permission WHERE role_id=? ORDER BY permission_code",String.class,id);
    }
    public Optional<Role> role(int id) {
        return db.query("SELECT role_id,role_name,description FROM public.role WHERE scope='SYSTEM' AND role_id=?",
            (r,n)->new Role(r.getInt(1),r.getString(2),r.getString(3),rolePermissions(r.getInt(1))),id).stream().findFirst();
    }
    public List<Role> roles() {
        return db.query("SELECT role_id,role_name,description FROM public.role WHERE scope='SYSTEM' ORDER BY role_id",
            (r,n)->new Role(r.getInt(1),r.getString(2),r.getString(3),rolePermissions(r.getInt(1))));
    }
    public int defaultRole() {
        return db.queryForObject("SELECT role_id FROM public.role WHERE scope='SYSTEM' AND role_name='USER'",Integer.class);
    }
    public List<Map<String,Object>> permissions() {
        return db.query("SELECT permission_code,description FROM public.auth_permission ORDER BY permission_code",
            (r,n)->Map.of("code",r.getString(1),"description",r.getString(2)));
    }
    public List<String> permissionCodes() {
        return db.queryForList("SELECT permission_code FROM public.auth_permission",String.class);
    }
    public int createRole(String name, String description) {
        return db.queryForObject("INSERT INTO public.role(role_name,scope,description) VALUES (?,'SYSTEM',?) RETURNING role_id",Integer.class,name,description);
    }
    public void updateRole(int id, String name, String description) {
        db.update("UPDATE public.role SET role_name=?,description=? WHERE role_id=? AND scope='SYSTEM'",name,description,id);
    }
    public void assignRoles(int uid, List<Integer> roleIds) {
        db.update("DELETE FROM public.user_role ur USING public.role r WHERE ur.role_id=r.role_id AND r.scope='SYSTEM' AND ur.user_id=?",uid);
        for (int id : roleIds) db.update("INSERT INTO public.user_role(user_id,role_id) VALUES (?,?)",uid,id);
    }
    public void setPermissions(int roleId, List<String> permissions) {
        db.update("DELETE FROM public.auth_role_permission WHERE role_id=?",roleId);
        for (String permission : permissions)
            db.update("INSERT INTO public.auth_role_permission(role_id,permission_code) VALUES (?,?)",roleId,permission);
    }
    public long activeAdmins() {
        return db.queryForObject("""
            SELECT count(DISTINCT u.user_id) FROM public.app_user u
            JOIN public.user_role ur USING(user_id) JOIN public.role r USING(role_id)
            WHERE u.status='ACTIVE' AND r.scope='SYSTEM' AND r.role_name='SYSTEM_ADMIN'
            """,Long.class);
    }
    public void audit(int actor, String type, int id, String action, String requestId) {
        db.update("""
            INSERT INTO public.audit_log(actor_user_id,event_at,entity_type,entity_key,action,request_id)
            VALUES (?,CURRENT_TIMESTAMP,?,?,?,?)
            """,actor,type,Integer.toString(id),action,requestId);
    }
}
