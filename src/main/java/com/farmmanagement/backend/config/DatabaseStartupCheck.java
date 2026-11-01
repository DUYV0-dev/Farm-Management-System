package com.farmmanagement.backend.config;

import java.net.URI;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;

/**
 * Check the resolved connection before Hibernate obscures connection errors.
 * Never changes schema.
 */
public final class DatabaseStartupCheck implements ApplicationContextInitializer<ConfigurableApplicationContext> {
    @Override
    public void initialize(ConfigurableApplicationContext context) {
        var environment = context.getEnvironment();
        String url = required(environment, "spring.datasource.url",
                "Chưa chọn database. Đặt biến môi trường DB_URL cho application.properties. "
                        + "Ứng dụng không tự chuyển sang PostgreSQL ở cổng 5432.");
        String username = required(environment, "spring.datasource.username", "Thiếu tài khoản database: DB_USERNAME.");
        String password = required(environment, "spring.datasource.password", "Thiếu mật khẩu database: DB_PASSWORD.");
        String target = target(url);
        var properties = new Properties();
        properties.setProperty("user", username);
        properties.setProperty("password", password);
        properties.setProperty("connectTimeout", "5");
        properties.setProperty("socketTimeout", "10");
        try (var connection = DriverManager.getConnection(url, properties)) {
            connection.setReadOnly(true);
            try (var statement = connection.createStatement();
                    var result = statement.executeQuery("SELECT current_database(), current_user")) {
                result.next();
                LoggerFactory.getLogger(DatabaseStartupCheck.class).info(
                        "Database đã chọn: {} (database={}, role={})", target, result.getString(1),
                        result.getString(2));
            }
            if (environment.getProperty("app.production", Boolean.class, false)) {
                try (var statement = connection.createStatement()) {
                    // The runtime role intentionally cannot read Flyway's owner-only history table.
                    // Check the required release objects through the catalog; deployment validates
                    // checksums.
                    try (var result = statement.executeQuery("""
                            SELECT to_regclass('public.flyway_schema_history') IS NOT NULL
                               AND to_regclass('public.farming_task') IS NOT NULL
                               AND to_regclass('public.inventory_movement') IS NOT NULL
                               AND to_regclass('public.product_listing') IS NOT NULL
                               AND to_regclass('public.store_order') IS NOT NULL
                               AND to_regclass('public.store_order_item') IS NOT NULL
                               AND to_regprocedure('public.store_catalog()') IS NOT NULL
                               AND to_regprocedure('public.store_checkout(jsonb)') IS NOT NULL
                               AND to_regprocedure('public.store_receipt(uuid)') IS NOT NULL
                            """)) {
                        result.next();
                        if (!result.getBoolean(1))
                            throw new IllegalStateException("Database " + target
                                    + " thiếu cấu trúc của migration 017/018. Sao lưu và kiểm tra quy trình nâng cấp; "
                                    + "xem docs/DATABASE_CONFIGURATION.md.");
                    }
                }
            }
        } catch (SQLException error) {
            // Do not attach the driver exception: JDBC URLs can contain credentials.
            throw new IllegalStateException(failureMessage(target, error.getSQLState()));
        }
    }

    static String required(Environment environment, String name, String message) {
        try {
            String value = environment.getProperty(name);
            if (value != null && !value.contains("${") && (!value.isBlank() || name.endsWith("password")))
                return value;
        } catch (IllegalArgumentException ignored) {
        }
        throw new IllegalStateException(message);
    }

    static String target(String url) {
        try {
            URI uri = URI.create(url.substring("jdbc:".length()));
            if (uri.getHost() != null && uri.getPath() != null && !uri.getPath().isBlank())
                return uri.getHost() + ":" + (uri.getPort() < 0 ? 5432 : uri.getPort()) + uri.getPath();
        } catch (RuntimeException ignored) {
        }
        return "PostgreSQL đã cấu hình";
    }

    static String failureMessage(String target, String state) {
        if (state != null && state.startsWith("28"))
            return "Không đăng nhập được database " + target
                    + ". Kiểm tra DB_USERNAME/DB_PASSWORD của đúng cụm PostgreSQL; ";
        if ("3D000".equals(state))
            return "Không tìm thấy database " + target + ". Kiểm tra tên database trong cấu hình.";
        return "Không kết nối được database " + target + ". Kiểm tra PostgreSQL đã chạy và đúng cổng. "
                + "Với bản cài hiện tại, kiểm tra dịch vụ FarmManagement-PostgreSQL. "
                + "Xem docs/DATABASE_CONFIGURATION.md. Ứng dụng không tự chuyển database.";
    }
}
