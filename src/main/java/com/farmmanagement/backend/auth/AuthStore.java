package com.farmmanagement.backend.auth;

import java.sql.*;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AuthStore {
    private final JdbcTemplate db;
    public AuthStore(JdbcTemplate db) { this.db = db; }
    // Internal record only: never serialize it or log it.
    public record User(int id, String username, String hash, String fullName, String email,
                       String status, Instant createdAt) {}
    private User row(ResultSet r, int i) throws SQLException {
        return new User(r.getInt("user_id"),r.getString("username"),r.getString("password_hash"),
            r.getString("full_name"),r.getString("email"),r.getString("status"),
            r.getTimestamp("created_at").toInstant());
    }
    public Optional<User> byName(String name, boolean lock) {
        return db.query("SELECT * FROM public.app_user WHERE username=?" + (lock ? " FOR UPDATE" : ""),this::row,name).stream().findFirst();
    }
    public Optional<User> byId(int id) {
        return db.query("SELECT * FROM public.app_user WHERE user_id=?",this::row,id).stream().findFirst();
    }
    public List<String> roles(int id) {
        return db.queryForList("SELECT r.role_name FROM public.role r JOIN public.user_role ur USING(role_id) WHERE ur.user_id=? AND r.scope='SYSTEM' ORDER BY r.role_name",String.class,id);
    }
    public Map<String,Object> publicUser(User u) {
        return Map.of("id",u.id(),"username",u.username(),"fullName",u.fullName(),"email",u.email(),
            "status",u.status(),"createdAt",u.createdAt().toString(),"systemRoles",roles(u.id()));
    }
    public List<Map<String,Object>> memberships(int id) {
        return db.query("""
            SELECT fm.farm_id,fm.status,fm.joined_at,f.farm_name,f.status AS farm_status,
              COALESCE(string_agg(r.role_name,',' ORDER BY r.role_name) FILTER (WHERE r.scope='FARM'),'') AS roles
            FROM public.farm_member fm JOIN public.farm f USING(farm_id)
            LEFT JOIN public.farm_member_role fmr ON fmr.farm_id=fm.farm_id AND fmr.user_id=fm.user_id
            LEFT JOIN public.role r ON r.role_id=fmr.role_id
            WHERE fm.user_id=? GROUP BY fm.farm_id,fm.status,fm.joined_at,f.farm_name,f.status
            ORDER BY fm.farm_id
            """,(r,n)->Map.<String,Object>of("farmId",r.getInt("farm_id"),"status",r.getString("status"),
                "farmName",r.getString("farm_name"),"farmStatus",r.getString("farm_status"),
                "joinedAt",r.getDate("joined_at").toString(),"roles",r.getString("roles").isEmpty()?List.of():List.of(r.getString("roles").split(","))),id);
    }
    public void createSession(UUID sid, int uid, Instant issued, Instant expires) {
        db.update("INSERT INTO public.auth_session(session_id,user_id,issued_at,expires_at) VALUES (?,?,?,?)",
            sid,uid,Timestamp.from(issued),Timestamp.from(expires));
    }
    public boolean validSession(UUID sid, int uid) {
        return Boolean.TRUE.equals(db.queryForObject("""
            SELECT EXISTS(SELECT 1 FROM public.auth_session s JOIN public.app_user u USING(user_id)
            WHERE s.session_id=? AND s.user_id=? AND s.revoked_at IS NULL
              AND s.expires_at>CURRENT_TIMESTAMP AND u.status='ACTIVE')
            """,Boolean.class,sid,uid));
    }
    public void revoke(UUID sid,int uid) {
        db.update("UPDATE public.auth_session SET revoked_at=COALESCE(revoked_at,CURRENT_TIMESTAMP) WHERE session_id=? AND user_id=?",sid,uid);
    }
}
