package com.mahjong.service;

import com.mahjong.service.JwtTokenService.JwtTokenException;
import com.mahjong.service.JwtTokenService.TokenPair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JwtTokenServiceTest {
    
    private JwtTokenService jwtTokenService;
    
    @BeforeEach
    void setUp() {
        jwtTokenService = new JwtTokenService(
                "test-secret-key-that-is-long-enough-for-hmac-sha512-algorithm-minimum-512-bits-required",
                3600000L, // 1 hour
                86400000L // 24 hours
        );
    }
    
    @Test
    void shouldGenerateAccessToken() {
        // Given
        Long userId = 123L;
        String openId = "test-openid";
        
        // When
        String token = jwtTokenService.generateAccessToken(userId, openId);
        
        // Then
        assertNotNull(token);
        assertFalse(token.isEmpty());
        assertTrue(jwtTokenService.validateToken(token));
        assertEquals(userId, jwtTokenService.extractUserId(token));
        assertEquals(openId, jwtTokenService.extractOpenId(token));
        assertEquals("access", jwtTokenService.extractTokenType(token));
    }
    
    @Test
    void shouldGenerateRefreshToken() {
        // Given
        Long userId = 123L;
        String openId = "test-openid";
        
        // When
        String token = jwtTokenService.generateRefreshToken(userId, openId);
        
        // Then
        assertNotNull(token);
        assertFalse(token.isEmpty());
        assertTrue(jwtTokenService.validateToken(token));
        assertEquals(userId, jwtTokenService.extractUserId(token));
        assertEquals(openId, jwtTokenService.extractOpenId(token));
        assertEquals("refresh", jwtTokenService.extractTokenType(token));
    }
    
    @Test
    void shouldGenerateTokenPair() {
        // Given
        Long userId = 123L;
        String openId = "test-openid";
        
        // When
        TokenPair tokenPair = jwtTokenService.generateTokenPair(userId, openId);
        
        // Then
        assertNotNull(tokenPair);
        assertNotNull(tokenPair.getAccessToken());
        assertNotNull(tokenPair.getRefreshToken());
        assertNotEquals(tokenPair.getAccessToken(), tokenPair.getRefreshToken());
        
        // Verify access token
        assertTrue(jwtTokenService.validateToken(tokenPair.getAccessToken()));
        assertEquals("access", jwtTokenService.extractTokenType(tokenPair.getAccessToken()));
        
        // Verify refresh token
        assertTrue(jwtTokenService.validateToken(tokenPair.getRefreshToken()));
        assertEquals("refresh", jwtTokenService.extractTokenType(tokenPair.getRefreshToken()));
    }
    
    @Test
    void shouldExtractUserIdFromToken() {
        // Given
        Long userId = 456L;
        String openId = "test-openid";
        String token = jwtTokenService.generateAccessToken(userId, openId);
        
        // When
        Long extractedUserId = jwtTokenService.extractUserId(token);
        
        // Then
        assertEquals(userId, extractedUserId);
    }
    
    @Test
    void shouldExtractOpenIdFromToken() {
        // Given
        Long userId = 123L;
        String openId = "test-openid-123";
        String token = jwtTokenService.generateAccessToken(userId, openId);
        
        // When
        String extractedOpenId = jwtTokenService.extractOpenId(token);
        
        // Then
        assertEquals(openId, extractedOpenId);
    }
    
    @Test
    void shouldValidateValidToken() {
        // Given
        String token = jwtTokenService.generateAccessToken(123L, "test-openid");
        
        // When
        boolean isValid = jwtTokenService.validateToken(token);
        
        // Then
        assertTrue(isValid);
    }
    
    @Test
    void shouldRejectInvalidToken() {
        // Given
        String invalidToken = "invalid.jwt.token";
        
        // When
        boolean isValid = jwtTokenService.validateToken(invalidToken);
        
        // Then
        assertFalse(isValid);
    }
    
    @Test
    void shouldRefreshTokens() {
        // Given
        Long userId = 123L;
        String openId = "test-openid";
        String refreshToken = jwtTokenService.generateRefreshToken(userId, openId);
        
        // When
        TokenPair newTokenPair = jwtTokenService.refreshTokens(refreshToken);
        
        // Then
        assertNotNull(newTokenPair);
        assertNotNull(newTokenPair.getAccessToken());
        assertNotNull(newTokenPair.getRefreshToken());
        
        // Verify new tokens are valid
        assertTrue(jwtTokenService.validateToken(newTokenPair.getAccessToken()));
        assertTrue(jwtTokenService.validateToken(newTokenPair.getRefreshToken()));
        
        // Verify user data is preserved
        assertEquals(userId, jwtTokenService.extractUserId(newTokenPair.getAccessToken()));
        assertEquals(openId, jwtTokenService.extractOpenId(newTokenPair.getAccessToken()));
    }
    
    @Test
    void shouldRejectAccessTokenForRefresh() {
        // Given
        String accessToken = jwtTokenService.generateAccessToken(123L, "test-openid");
        
        // When & Then
        JwtTokenException exception = assertThrows(
                JwtTokenException.class,
                () -> jwtTokenService.refreshTokens(accessToken)
        );
        
        assertTrue(exception.getMessage().contains("not a refresh token") || 
                   exception.getMessage().contains("Token is not a refresh token"));
    }
    
    @Test
    void shouldRejectInvalidTokenForRefresh() {
        // Given
        String invalidToken = "invalid.jwt.token";
        
        // When & Then
        assertThrows(
                JwtTokenException.class,
                () -> jwtTokenService.refreshTokens(invalidToken)
        );
    }
    
    @Test
    void shouldHandleTokenExpiration() {
        // Given - create service with very short expiration
        JwtTokenService shortExpiryService = new JwtTokenService(
                "test-secret-key-that-is-long-enough-for-hmac-sha512-algorithm-minimum-512-bits-required",
                1L, // 1 millisecond
                1L  // 1 millisecond
        );
        
        String token = shortExpiryService.generateAccessToken(123L, "test-openid");
        
        // Wait for token to expire
        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // When
        boolean isExpired = shortExpiryService.isTokenExpired(token);
        boolean isValid = shortExpiryService.validateToken(token);
        
        // Then
        assertTrue(isExpired);
        assertFalse(isValid);
    }
    
    @Test
    void shouldThrowExceptionForInvalidTokenExtraction() {
        // Given
        String invalidToken = "invalid.jwt.token";
        
        // When & Then
        assertThrows(JwtTokenException.class, () -> jwtTokenService.extractUserId(invalidToken));
        assertThrows(JwtTokenException.class, () -> jwtTokenService.extractOpenId(invalidToken));
        assertThrows(JwtTokenException.class, () -> jwtTokenService.extractTokenType(invalidToken));
    }
}