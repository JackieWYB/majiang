package com.mahjong;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Main application test class
 */
@SpringBootTest
@ActiveProfiles("test")
class MahjongGameApplicationTests {

    @Test
    void contextLoads() {
        // Test that Spring context loads successfully
    }
}