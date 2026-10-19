package com.farmmanagement.backend.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.core.env.Environment;

@Component
@Profile("!seed | production")
public class WorkspaceDatabaseCheck implements ApplicationRunner {
    private final JdbcTemplate db;
    private final Environment environment;
    public WorkspaceDatabaseCheck(JdbcTemplate db,Environment environment) { this.db=db;this.environment=environment; }
    @Override public void run(ApplicationArguments arguments) {
        boolean installed=Boolean.TRUE.equals(db.queryForObject("SELECT to_regprocedure('public.workspace_memberships(integer)') IS NOT NULL",Boolean.class));
        if(!installed) throw new IllegalStateException("Database chưa được nâng cấp luồng trang trại. Chạy scripts/Upgrade-Workspace.ps1 bằng tài khoản quản trị PostgreSQL; xem docs/WORKSPACE.md.");
        boolean bypass=Boolean.TRUE.equals(db.queryForObject("""
            SELECT r.rolsuper OR r.rolbypassrls OR EXISTS (
              SELECT 1 FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace
              WHERE n.nspname='public' AND c.relname IN ('farm','plot','crop','material','warehouse')
              AND pg_has_role(current_user,c.relowner,'USAGE'))
            FROM pg_roles r WHERE r.rolname=current_user
            """,Boolean.class));
        if(bypass) throw new IllegalStateException("Chạy ứng dụng bằng DB_USERNAME=farm_app, không dùng tài khoản chủ database hoặc tài khoản bỏ qua row security.");
        boolean protectedTables=Boolean.TRUE.equals(db.queryForObject("""
            SELECT count(*)=8 AND bool_and(c.relrowsecurity)
            FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace
            WHERE n.nspname='public' AND c.relname IN ('farm','plot','crop','material','material_category','warehouse','crop_season','warehouse_inventory')
            """,Boolean.class));
        if(!protectedTables) throw new IllegalStateException("Thiếu row security trên bảng nghiệp vụ. Chạy lại migration 12 bằng tài khoản chủ database.");
        if(environment.getProperty("app.production",Boolean.class,false)) {
            byte[] key=java.util.Base64.getDecoder().decode(environment.getRequiredProperty("auth.jwt.secret"));
            if(java.util.stream.IntStream.range(0,key.length).allMatch(i->key[i]==key[0]))
                throw new IllegalStateException("JWT_SECRET production phải là khóa ngẫu nhiên, không sử dụng khóa mẫu kiểm thử.");
        }
    }
}
