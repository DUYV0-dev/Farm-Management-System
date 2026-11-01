package com.farmmanagement.backend.config;

import org.flywaydb.core.Flyway;
import java.sql.DriverManager;

/** Separate deployment entry point: runtime never receives schema-owner credentials. */
public final class DatabaseMigrations {
    private static String required(String name) {
        String value=System.getenv(name);
        if(value==null || value.isBlank()) throw new IllegalStateException("Missing environment variable: "+name);
        return value;
    }
    public static void main(String[] args) throws Exception {
        String command=args.length==0?"migrate":args[0];
        if(!java.util.Set.of("migrate","validate","info","upgrade-legacy").contains(command))
            throw new IllegalArgumentException("Use migrate, validate, info or upgrade-legacy");
        String url=required("DB_URL"),user=required("DB_MIGRATION_USERNAME"),password=required("DB_MIGRATION_PASSWORD");
        Flyway flyway=Flyway.configure().dataSource(url,user,password).locations("classpath:db/migration")
            .cleanDisabled(true).baselineOnMigrate(false).load();
        if(command.equals("upgrade-legacy")) {
            if(!"true".equals(System.getenv("ALLOW_LEGACY_BASELINE")))
                throw new IllegalStateException("Restore and inspect a backup first; explicitly set ALLOW_LEGACY_BASELINE=true");
            try(var connection=DriverManager.getConnection(url,user,password);var statement=connection.createStatement()) {
                var result=statement.executeQuery("SELECT to_regclass('public.flyway_schema_history') IS NOT NULL");result.next();
                boolean managed=result.getBoolean(1);
                if(!managed) {
                    for(String table:java.util.List.of("app_user","role","user_role","audit_log","auth_session","auth_permission","auth_role_permission")) {
                        try(var query=connection.prepareStatement("SELECT to_regclass(?) IS NOT NULL")) {
                            query.setString(1,"public."+table);var row=query.executeQuery();row.next();
                            if(!row.getBoolean(1)) throw new IllegalStateException("Unsupported legacy schema: missing "+table);
                        }
                    }
                    // ERD-v1 has material.unit_id. Conversion is a separate reviewed procedure.
                    var check=statement.executeQuery("SELECT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='material' AND column_name='unit_id')");check.next();
                    if(check.getBoolean(1)) throw new IllegalStateException("Legacy ERD requires the documented warehouse conversion before adoption");
                    Flyway.configure().dataSource(url,user,password).locations("classpath:db/migration")
                        .cleanDisabled(true).baselineVersion("3").load().baseline();
                }
            }
            flyway.migrate();
        } else if(command.equals("migrate")) flyway.migrate();
        else if(command.equals("validate")) flyway.validate();
        else for(var migration:flyway.info().all()) System.out.println(migration.getVersion()+" "+migration.getDescription()+" "+migration.getState());
        String appPassword=System.getenv("DB_APP_PASSWORD");
        if(appPassword!=null && !appPassword.isBlank() && (command.equals("migrate") || command.equals("upgrade-legacy"))) {
            if(appPassword.length()<24) throw new IllegalStateException("DB_APP_PASSWORD must contain at least 24 characters");
            try(var connection=DriverManager.getConnection(url,user,password);var statement=connection.createStatement()) {
                statement.execute("ALTER ROLE farm_app WITH PASSWORD '"+appPassword.replace("'","''")+"'");
            } catch (Exception e) {
                System.err.println("[WARN] Could not alter role farm_app: " + e.getMessage());
            }
        }
    }
}
