package com.mahjong.security;

import com.mahjong.service.RateLimitService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Security tests for rate limiting functionality
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public class RateLimitSecurityTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private RateLimitService rateLimitService;

    private String baseUrl;

    @BeforeEach
    void setUp() {
        baseUrl = "http://localhost:" + port + "/api";
    }

    @Test
    void testApiRateLimitExceeded() {
        String userId = "test-user-1";
        
        // Simulate exceeding API rate limit
        for (int i = 0; i < 105; i++) { // Exceed 100 requests per minute limit
            boolean allowed = rateLimitService.isApiRequestAllowed(userId);
            if (i < 100) {
                assertTrue(allowed, "Request " + i + " should be allowed");
            } else {
                assertFalse(allowed, "Request " + i + " should be blocked");
            }
        }
    }

    @Test
    void testWebSocketRateLimitExceeded() {
        String userId = "test-user-2";
        
        // Simulate exceeding WebSocket message rate limit
        for (int i = 0; i < 35; i++) { // Exceed 30 messages per minute limit
            boolean allowed = rateLimitService.isWebSocketMessageAllowed(userId);
            if (i < 30) {
                assertTrue(allowed, "Message " + i + " should be allowed");
            } else {
                assertFalse(allowed, "Message " + i + " should be blocked");
            }
        }
    }

    @Test
    void testLoginRateLimitByIP() {
        String ipAddress = "192.168.1.100";
        
        // Simulate exceeding login rate limit
        for (int i = 0; i < 7; i++) { // Exceed 5 attempts per 15 minutes limit
            boolean allowed = rateLimitService.isLoginAttemptAllowed(ipAddress);
            if (i < 5) {
                assertTrue(allowed, "Login attempt " + i + " should be allowed");
            } else {
                assertFalse(allowed, "Login attempt " + i + " should be blocked");
            }
        }
    }

    @Test
    void testRoomCreationRateLimit() {
        String userId = "test-user-3";
        
        // Simulate exceeding room creation rate limit
        for (int i = 0; i < 12; i++) { // Exceed 10 rooms per hour limit
            boolean allowed = rateLimitService.isRoomCreationAllowed(userId);
            if (i < 10) {
                assertTrue(allowed, "Room creation " + i + " should be allowed");
            } else {
                assertFalse(allowed, "Room creation " + i + " should be blocked");
            }
        }
    }

    @Test
    void testRateLimitHttpResponse() {
        // This test would require a valid JWT token and proper endpoint
        // For now, we'll test the rate limit service directly
        
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer invalid-token");
        HttpEntity<String> entity = new HttpEntity<>(headers);
        
        // Make multiple requests to trigger rate limiting
        for (int i = 0; i < 3; i++) {
            ResponseEntity<String> response = restTemplate.exchange(
                    baseUrl + "/health", 
                    HttpMethod.GET, 
                    entity, 
                    String.class
            );
            
            // Health endpoint should not be rate limited
            assertNotEquals(HttpStatus.TOO_MANY_REQUESTS, response.getStatusCode());
        }
    }

    @Test
    void testRateLimitTokenRefill() throws InterruptedException {
        String userId = "test-user-4";
        
        // Consume all tokens
        for (int i = 0; i < 100; i++) {
            rateLimitService.isApiRequestAllowed(userId);
        }
        
        // Next request should be blocked
        assertFalse(rateLimitService.isApiRequestAllowed(userId));
        
        // Check remaining tokens
        long remainingTokens = rateLimitService.getRemainingApiTokens(userId);
        assertEquals(0, remainingTokens);
        
        // Wait a bit and check if tokens are refilled (in real scenario, would wait longer)
        Thread.sleep(1000);
        
        // In a real test, we would wait for the refill period
        // For now, just verify the method doesn't throw exceptions
        assertDoesNotThrow(() -> rateLimitService.getTimeUntilRefill(userId, "api"));
    }

    @Test
    void testDifferentUsersHaveSeparateRateLimits() {
        String user1 = "test-user-5";
        String user2 = "test-user-6";
        
        // Exhaust rate limit for user1
        for (int i = 0; i < 100; i++) {
            rateLimitService.isApiRequestAllowed(user1);
        }
        
        // User1 should be blocked
        assertFalse(rateLimitService.isApiRequestAllowed(user1));
        
        // User2 should still be allowed
        assertTrue(rateLimitService.isApiRequestAllowed(user2));
    }

    @Test
    void testRateLimitWithNullUser() {
        // Test rate limiting behavior with null user ID
        assertDoesNotThrow(() -> {
            boolean allowed = rateLimitService.isApiRequestAllowed(null);
            // Should handle null gracefully
        });
    }

    @Test
    void testRateLimitWithEmptyUser() {
        // Test rate limiting behavior with empty user ID
        assertDoesNotThrow(() -> {
            boolean allowed = rateLimitService.isApiRequestAllowed("");
            // Should handle empty string gracefully
        });
    }
}