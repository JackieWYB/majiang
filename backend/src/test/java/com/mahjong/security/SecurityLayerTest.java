package com.mahjong.security;

import com.mahjong.service.RateLimitService;
import com.mahjong.service.InputValidationService;
import com.mahjong.service.SuspiciousActivityService;
import com.mahjong.service.GameAuditService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Basic security layer tests
 */
@SpringBootTest
@ActiveProfiles("test")
public class SecurityLayerTest {

    @Autowired
    private RateLimitService rateLimitService;

    @Autowired
    private InputValidationService inputValidationService;

    @Autowired
    private SuspiciousActivityService suspiciousActivityService;

    @Autowired
    private GameAuditService gameAuditService;

    @Test
    void testRateLimitServiceBasic() {
        String userId = "test-user";
        
        // Should allow initial requests
        assertTrue(rateLimitService.isApiRequestAllowed(userId));
        assertTrue(rateLimitService.isWebSocketMessageAllowed(userId));
        assertTrue(rateLimitService.isRoomCreationAllowed(userId));
        
        String ipAddress = "192.168.1.1";
        assertTrue(rateLimitService.isLoginAttemptAllowed(ipAddress));
    }

    @Test
    void testInputValidationBasic() {
        // Valid inputs
        assertDoesNotThrow(() -> inputValidationService.validateRoomId("123456"));
        assertDoesNotThrow(() -> inputValidationService.validateNickname("TestUser"));
        assertDoesNotThrow(() -> inputValidationService.validateTile("5W"));
        
        // Invalid inputs
        assertThrows(IllegalArgumentException.class, 
                () -> inputValidationService.validateRoomId("12345"));
        assertThrows(IllegalArgumentException.class, 
                () -> inputValidationService.validateNickname("<script>"));
        assertThrows(IllegalArgumentException.class, 
                () -> inputValidationService.validateTile("0W"));
    }

    @Test
    void testSuspiciousContentDetection() {
        assertTrue(inputValidationService.containsSuspiciousContent("'; DROP TABLE users; --"));
        assertTrue(inputValidationService.containsSuspiciousContent("<script>alert('xss')</script>"));
        assertFalse(inputValidationService.containsSuspiciousContent("normal text"));
    }

    @Test
    void testGameAuditService() {
        assertDoesNotThrow(() -> {
            gameAuditService.preserveGameSeed("game123", "room123", 12345L, "TEST");
            gameAuditService.logSecurityEvent("user123", "TEST_EVENT", "test", "192.168.1.1", "LOW");
        });
    }

    @Test
    void testSuspiciousActivityService() {
        assertDoesNotThrow(() -> {
            suspiciousActivityService.logSuspiciousActivity("user123", "192.168.1.1", "TEST", "test");
            suspiciousActivityService.checkUnusualGamePattern("user123", "PERFECT_PLAY", "test");
        });
    }
}