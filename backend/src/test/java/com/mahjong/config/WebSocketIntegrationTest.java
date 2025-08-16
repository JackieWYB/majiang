package com.mahjong.config;

import com.mahjong.service.JwtTokenService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for WebSocket configuration
 */
@SpringBootTest
@ActiveProfiles("test")
public class WebSocketIntegrationTest {
    
    @Autowired
    private WebSocketConfig webSocketConfig;
    
    @Autowired
    private WebSocketAuthInterceptor authInterceptor;
    
    @Autowired
    private JwtTokenService jwtTokenService;
    
    @Test
    void shouldLoadWebSocketConfiguration() {
        assertNotNull(webSocketConfig, "WebSocket configuration should be loaded");
        assertNotNull(authInterceptor, "WebSocket auth interceptor should be loaded");
        assertNotNull(jwtTokenService, "JWT token service should be loaded");
    }
    
    @Test
    void shouldGenerateAndValidateToken() {
        String userId = "test-user-123";
        
        // Generate token
        String token = jwtTokenService.generateToken(userId);
        assertNotNull(token, "Token should be generated");
        assertFalse(token.isEmpty(), "Token should not be empty");
        
        // Validate token
        assertTrue(jwtTokenService.validateToken(token), "Token should be valid");
        
        // Extract user ID
        String extractedUserId = jwtTokenService.extractUserIdAsString(token);
        assertEquals(userId, extractedUserId, "Extracted user ID should match original");
    }
    
    @Test
    void shouldRejectInvalidToken() {
        String invalidToken = "invalid.jwt.token";
        
        assertFalse(jwtTokenService.validateToken(invalidToken), "Invalid token should be rejected");
    }
}