package com.farmmanagement.backend.config;

import java.sql.*;
import java.util.Properties;
import java.util.logging.Logger;
import org.junit.jupiter.api.*;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.mock.env.MockEnvironment;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DatabaseStartupCheckTests {
    final Connection connection = mock(Connection.class);
    final Statement statement = mock(Statement.class);
    final Driver driver = new Driver() {
        public boolean acceptsURL(String url) { return url.startsWith("jdbc:startupcheck:"); }
        public Connection connect(String url, Properties properties) throws SQLException {
            if (!acceptsURL(url)) return null;
            if (url.endsWith("auth-error")) throw new SQLException("password=do-not-log", "28P01");
            return connection;
        }
        public DriverPropertyInfo[] getPropertyInfo(String url, Properties info) { return new DriverPropertyInfo[0]; }
        public int getMajorVersion() { return 1; }
        public int getMinorVersion() { return 0; }
        public boolean jdbcCompliant() { return false; }
        public Logger getParentLogger() { return Logger.getGlobal(); }
    };

    @BeforeEach void prepare() throws Exception {
        DriverManager.registerDriver(driver);
        when(connection.createStatement()).thenReturn(statement);
    }
    @AfterEach void cleanup() throws Exception { DriverManager.deregisterDriver(driver); }

    GenericApplicationContext context(String url, boolean production) {
        var context = new GenericApplicationContext();
        context.setEnvironment(new MockEnvironment().withProperty("spring.datasource.url", url)
            .withProperty("spring.datasource.username", "farm_app").withProperty("spring.datasource.password", "secret")
            .withProperty("app.production", Boolean.toString(production)));
        return context;
    }
    ResultSet result(boolean value) throws Exception {
        var result = mock(ResultSet.class);
        when(result.next()).thenReturn(true);
        when(result.getBoolean(1)).thenReturn(value);
        return result;
    }

    @Test void missingDatabaseDoesNotChooseTheLegacyPort() {
        var environment = new MockEnvironment().withProperty("spring.datasource.url", "${DB_URL}");
        assertThrows(IllegalStateException.class, () -> DatabaseStartupCheck.required(environment, "spring.datasource.url", "missing database"));
    }
    @Test void selectedDatabaseWinsAndEmptyTestPasswordIsAllowed() {
        var environment = new MockEnvironment().withProperty("spring.datasource.url", "jdbc:postgresql://localhost:55433/farm_management")
            .withProperty("spring.datasource.password", "");
        assertTrue(DatabaseStartupCheck.required(environment,"spring.datasource.url","missing").contains(":55433/"));
        assertEquals("", DatabaseStartupCheck.required(environment,"spring.datasource.password","missing"));
    }
    @Test void connectionCheckIsReadOnlyAndRequiresTheCurrentProductionMigration() throws Exception {
        var identity = result(true);
        when(identity.getString(1)).thenReturn("farm_management");
        when(identity.getString(2)).thenReturn("farm_app");
        var current = result(true);
        when(statement.executeQuery(anyString())).thenReturn(identity, current);
        try (var context = context("jdbc:startupcheck:ok", true)) {
            new DatabaseStartupCheck().initialize(context);
        }
        verify(connection).setReadOnly(true);
        verify(statement, times(2)).executeQuery(anyString());
        verify(statement, never()).executeQuery(contains("FROM public.flyway_schema_history"));
        verify(statement, never()).executeUpdate(anyString());
        verify(connection).close();
    }
    @Test void productionWithoutMigration018StopsBeforeApplicationStarts() throws Exception {
        var identity = result(true);
        var pending = result(false);
        when(statement.executeQuery(anyString())).thenReturn(identity, pending);
        try (var context = context("jdbc:startupcheck:ok", true)) {
            var error = assertThrows(IllegalStateException.class, () -> new DatabaseStartupCheck().initialize(context));
            assertTrue(error.getMessage().contains("018"));
        }
    }
    @Test void developmentDatabaseCanStillUseTheSupportedLegacySchema() throws Exception {
        var identity = result(true);
        when(statement.executeQuery(anyString())).thenReturn(identity);
        try (var context = context("jdbc:startupcheck:ok", false)) { new DatabaseStartupCheck().initialize(context); }
        verify(statement, times(1)).executeQuery(anyString());
    }
    @Test void authenticationErrorsNeverExposeDriverSecrets() {
        try (var context = context("jdbc:startupcheck:auth-error", false)) {
            var error = assertThrows(IllegalStateException.class, () -> new DatabaseStartupCheck().initialize(context));
            assertTrue(error.getMessage().contains("DB_USERNAME/DB_PASSWORD"));
            assertFalse(error.getMessage().contains("do-not-log"));
            assertNull(error.getCause());
        }
    }
    @Test void connectionTargetHidesUrlCredentialsAndParameters() {
        assertEquals("127.0.0.1:55433/farm_management", DatabaseStartupCheck.target("jdbc:postgresql://user:secret@127.0.0.1:55433/farm_management?password=secret"));
        assertEquals("PostgreSQL đã cấu hình", DatabaseStartupCheck.target("invalid url password=secret"));
    }
    @Test void connectionRefusedAndMissingDatabaseHaveActionableMessages() {
        assertTrue(DatabaseStartupCheck.failureMessage("127.0.0.1:55433/farm_management", "08001").contains("FarmManagement-PostgreSQL"));
        assertTrue(DatabaseStartupCheck.failureMessage("127.0.0.1:55433/absent", "3D000").contains("tên database"));
    }
}
