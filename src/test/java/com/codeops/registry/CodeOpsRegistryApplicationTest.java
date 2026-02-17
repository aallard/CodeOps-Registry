package com.codeops.registry;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class CodeOpsRegistryApplicationTest {

    @Test
    void contextLoads() {
        // Verifies Spring context starts without errors
    }
}
