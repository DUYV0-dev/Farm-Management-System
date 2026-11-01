package com.farmmanagement.backend.config;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.*;
import org.springframework.core.io.support.ResourcePropertySource;
import org.springframework.mock.env.MockEnvironment;
import static org.junit.jupiter.api.Assertions.*;

class DeploymentConfigurationTests {
    MockEnvironment environment(Map<String,Object> values) throws Exception {
        var env=new MockEnvironment();
        env.getPropertySources().addFirst(new MapPropertySource("deployment",values));
        env.getPropertySources().addLast(new ResourcePropertySource("classpath:application.properties"));
        return env;
    }
    @Test void sameArtifactCanSelectRemoteDatabaseAndDifferentRuntimeCredentials() throws Exception {
        var env=environment(Map.of("DB_URL","jdbc:postgresql://remote.example:5432/production",
            "DB_USERNAME","remote_runtime","DB_PASSWORD","deployment-only-password"));
        assertEquals("jdbc:postgresql://remote.example:5432/production",env.getProperty("spring.datasource.url"));
        assertEquals("remote_runtime",env.getProperty("spring.datasource.username"));
        assertEquals("deployment-only-password",env.getProperty("spring.datasource.password"));
    }
    @Test void missingDatabaseOrPasswordDoesNotFallBackToDeveloperCredentials() throws Exception {
        var env=environment(Map.of());
        assertThrows(IllegalArgumentException.class,()->env.getProperty("spring.datasource.url"));
        assertThrows(IllegalArgumentException.class,()->env.getProperty("spring.datasource.password"));
        assertEquals("farm_app",env.getProperty("spring.datasource.username"));
    }
    @Test void bundledConfigurationContainsOnlyEnvironmentReferencesForSecrets() throws Exception {
        var config=new Properties();
        try(var input=getClass().getResourceAsStream("/application.properties")) { config.load(input); }
        for(String key:config.stringPropertyNames()) {
            if(key.endsWith("password") || key.endsWith("secret")) {
                assertTrue(config.getProperty(key).matches("\\$\\{[A-Z_]+:?}"),key+" must not contain a literal secret");
            }
        }
    }
}
