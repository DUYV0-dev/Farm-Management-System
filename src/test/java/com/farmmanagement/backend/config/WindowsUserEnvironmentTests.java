package com.farmmanagement.backend.config;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.MapPropertySource;
import org.springframework.mock.env.MockEnvironment;
import static org.junit.jupiter.api.Assertions.*;

class WindowsUserEnvironmentTests {
    @Test void sourceRunRecoversJwtAndTlsFromAnUpdatedWindowsUserEnvironment() {
        var defaults = WindowsUserEnvironment.defaults(false, "Windows 11", Map.of(), new Properties(),
            keys -> Map.of("JWT_SECRET", "existing-user-key", "SERVER_SSL_ENABLED", "true", "UNRELATED_SECRET", "ignored"));
        var environment = new MockEnvironment().withProperty("auth.jwt.secret", "${JWT_SECRET}")
            .withProperty("server.ssl.enabled", "${SERVER_SSL_ENABLED:false}");
        environment.getPropertySources().addLast(new MapPropertySource("userDefaults", defaults));
        assertEquals("existing-user-key", environment.getProperty("auth.jwt.secret"));
        assertTrue(environment.getProperty("server.ssl.enabled", Boolean.class));
        assertFalse(defaults.containsKey("UNRELATED_SECRET"));
    }

    @Test void explicitProcessJvmAndSpringSettingsRetainPrecedence() {
        var jvm = new Properties(); jvm.setProperty("DB_PASSWORD", "explicit-jvm-password");
        var defaults = WindowsUserEnvironment.defaults(false, "Windows 11", Map.of("DB_URL", "explicit-url"), jvm,
            keys -> {
                assertFalse(keys.contains("DB_URL")); assertFalse(keys.contains("DB_PASSWORD"));
                return Map.of("DB_URL", "user-url", "DB_PASSWORD", "user-password", "JWT_SECRET", "user-key");
            });
        assertFalse(defaults.containsKey("DB_URL")); assertFalse(defaults.containsKey("DB_PASSWORD"));
        var environment = new MockEnvironment().withProperty("JWT_SECRET", "explicit-key");
        environment.getPropertySources().addLast(new MapPropertySource("userDefaults", defaults));
        assertEquals("explicit-key", environment.getProperty("JWT_SECRET"));
    }

    @Test void packagedApplicationAndOtherPlatformsNeverReadThePersonalWindowsEnvironment() {
        for (var mode : List.of(Map.entry(true, "Windows 11"), Map.entry(false, "Linux"))) {
            assertTrue(WindowsUserEnvironment.defaults(mode.getKey(), mode.getValue(), Map.of(), new Properties(),
                keys -> { throw new AssertionError("Must not read the Windows User environment"); }).isEmpty());
        }
    }
}
