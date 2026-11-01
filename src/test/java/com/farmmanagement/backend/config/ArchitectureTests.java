package com.farmmanagement.backend.config;

import java.nio.file.*;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Architectural rules shared by all modules; no database or business fixtures. */
class ArchitectureTests {
    @Test void controllersAndServicesDoNotExecuteSql() throws Exception {
        try(var paths=Files.walk(Path.of("src/main/java/com/farmmanagement/backend"))) {
            for(var path:paths.filter(p->p.toString().endsWith("Controller.java") || p.toString().endsWith("Service.java")).toList()) {
                String code=Files.readString(path);
                assertFalse(code.contains("JdbcTemplate") || code.contains("DriverManager"),path+" must use its Store");
                assertFalse(Pattern.compile("\"(?:SELECT|INSERT INTO|UPDATE public\\.|DELETE FROM) ").matcher(code).find(),path+" contains SQL outside Store");
            }
        }
    }
    @Test void shippedFrontendDoesNotUseBrowserStorageForBusinessData() throws Exception {
        try(var paths=Files.walk(Path.of("frontend"))) {
            for(var path:paths.filter(p->p.toString().endsWith(".js") && !p.startsWith(Path.of("frontend/tests"))).toList()) {
                String code=Files.readString(path);
                assertFalse(Pattern.compile("\\b(localStorage|sessionStorage|indexedDB)\\b").matcher(code).find(),path+" must obtain persisted data through the API");
            }
        }
    }
    @Test void demoModulesAreOutsideRuntimeSourceTree() {
        for(String module:new String[]{"activity","notification","traceability"}) {
            assertFalse(Files.exists(Path.of("src/main/java/com/farmmanagement/backend",module)),"In-memory demo remains in runtime: "+module);
        }
        assertFalse(Files.exists(Path.of("frontend/prototypes")));
        assertFalse(Files.exists(Path.of("frontend/features/traceability")));
        assertTrue(Files.exists(Path.of("frontend/app/index-shell.html")));
    }
}
