package com.farmmanagement.backend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(named="RUN_DB_CONTEXT_TEST", matches="true")
@SpringBootTest
class FarmManagementSystemN02ApplicationTests {

    @Test
    void contextLoads() {
    }

}
