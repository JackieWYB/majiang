package com.mahjong.service;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * JWT token service for generating and validating authentication tokens
 */
@Service
public class JwtTokenService {
    
    private static final Logger logger = LoggerFactory.getLogger(JwtTokenService.class);
    
    private final SecretKey secretKey;
    private final long jwtExpiration;
    private final long refreshExpiration;
    
    public JwtTokenService(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.expiration}") long jwtExpiration,
            @Value("${jwt.refresh-expiration}") long refreshExpiration) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.jwtExpiration = jwtExpiration;
        this.refreshExpiration = refreshExpiration;
    }
    
    /**
     * Generate access token for user
     * 
     * @param userId User ID
     * @param openId WeChat openId
     * @return JWT access token
     */
    public String generateAccessToken(Long userId, String openId) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userId);
        claims.put("openId", openId);
        claims.put("type", "access");
        
        return createToken(claims, userId.toString(), jwtExpiration);
    }
    
    /**
     * Generate access token for user with role
     * 
     * @param userId User ID
     * @param openId WeChat openId
     * @param role User role
     * @return JWT access token
     */
    public String generateAccessToken(Long userId, String openId, String role) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userId);
        claims.put("openId", openId);
        claims.put("role", role);
        claims.put("type", "access");
        
        return createToken(claims, userId.toString(), jwtExpiration);
    }
    
    /**
     * Generate refresh token for user
     * 
     * @param userId User ID
     * @param openId WeChat openId
     * @return JWT refresh token
     */
    public String generateRefreshToken(Long userId, String openId) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userId);
        claims.put("openId", openId);
        claims.put("type", "refresh");
        
        return createToken(claims, userId.toString(), refreshExpiration);
    }
    
    /**
     * Generate refresh token for user with role
     * 
     * @param userId User ID
     * @param openId WeChat openId
     * @param role User role
     * @return JWT refresh token
     */
    public String generateRefreshToken(Long userId, String openId, String role) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userId);
        claims.put("openId", openId);
        claims.put("role", role);
        claims.put("type", "refresh");
        
        return createToken(claims, userId.toString(), refreshExpiration);
    }
    
    /**
     * Generate token pair (access + refresh)
     * 
     * @param userId User ID
     * @param openId WeChat openId
     * @return Token pair containing access and refresh tokens
     */
    public TokenPair generateTokenPair(Long userId, String openId) {
        String accessToken = generateAccessToken(userId, openId);
        String refreshToken = generateRefreshToken(userId, openId);
        
        return new TokenPair(accessToken, refreshToken);
    }
    
    /**
     * Generate token pair (access + refresh) with role
     * 
     * @param userId User ID
     * @param openId WeChat openId
     * @param role User role
     * @return Token pair containing access and refresh tokens
     */
    public TokenPair generateTokenPair(Long userId, String openId, String role) {
        String accessToken = generateAccessToken(userId, openId, role);
        String refreshToken = generateRefreshToken(userId, openId, role);
        
        return new TokenPair(accessToken, refreshToken);
    }
    
    /**
     * Extract user ID from token
     * 
     * @param token JWT token
     * @return User ID
     * @throws JwtTokenException if token is invalid
     */
    public Long extractUserId(String token) {
        try {
            Claims claims = extractAllClaims(token);
            Object userIdObj = claims.get("userId");
            
            if (userIdObj instanceof Integer) {
                return ((Integer) userIdObj).longValue();
            } else if (userIdObj instanceof Long) {
                return (Long) userIdObj;
            } else if (userIdObj instanceof String) {
                try {
                    return Long.parseLong((String) userIdObj);
                } catch (NumberFormatException e) {
                    // For non-numeric user IDs, return a hash-based ID
                    return (long) userIdObj.hashCode();
                }
            }
            
            throw new JwtTokenException("Invalid userId format in token");
        } catch (Exception e) {
            logger.error("Failed to extract userId from token", e);
            throw new JwtTokenException("Failed to extract userId from token", e);
        }
    }
    
    /**
     * Extract user ID as string from token
     * 
     * @param token JWT token
     * @return User ID as string
     * @throws JwtTokenException if token is invalid
     */
    public String extractUserIdAsString(String token) {
        try {
            Claims claims = extractAllClaims(token);
            Object userIdObj = claims.get("userId");
            
            if (userIdObj != null) {
                return userIdObj.toString();
            }
            
            throw new JwtTokenException("No userId found in token");
        } catch (Exception e) {
            logger.error("Failed to extract userId from token", e);
            throw new JwtTokenException("Failed to extract userId from token", e);
        }
    }
    
    /**
     * Extract openId from token
     * 
     * @param token JWT token
     * @return WeChat openId
     * @throws JwtTokenException if token is invalid
     */
    public String extractOpenId(String token) {
        try {
            Claims claims = extractAllClaims(token);
            return claims.get("openId", String.class);
        } catch (Exception e) {
            logger.error("Failed to extract openId from token", e);
            throw new JwtTokenException("Failed to extract openId from token", e);
        }
    }
    
    /**
     * Extract token type from token
     * 
     * @param token JWT token
     * @return Token type (access/refresh)
     */
    public String extractTokenType(String token) {
        try {
            Claims claims = extractAllClaims(token);
            return claims.get("type", String.class);
        } catch (Exception e) {
            logger.error("Failed to extract token type from token", e);
            throw new JwtTokenException("Failed to extract token type from token", e);
        }
    }
    
    /**
     * Extract role from token
     * 
     * @param token JWT token
     * @return User role
     */
    public String extractRole(String token) {
        try {
            Claims claims = extractAllClaims(token);
            return claims.get("role", String.class);
        } catch (Exception e) {
            logger.debug("Failed to extract role from token (may not have role): {}", e.getMessage());
            return "USER"; // Default role
        }
    }
    
    /**
     * Validate token
     * 
     * @param token JWT token
     * @return true if token is valid
     */
    public boolean validateToken(String token) {
        try {
            extractAllClaims(token);
            return true;
        } catch (JwtException e) {
            logger.debug("Token validation failed: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * Check if token is expired
     * 
     * @param token JWT token
     * @return true if token is expired
     */
    public boolean isTokenExpired(String token) {
        try {
            Claims claims = extractAllClaims(token);
            return claims.getExpiration().before(new Date());
        } catch (JwtException e) {
            return true;
        }
    }
    
    /**
     * Refresh access token using refresh token
     * 
     * @param refreshToken Valid refresh token
     * @return New token pair
     * @throws JwtTokenException if refresh token is invalid
     */
    public TokenPair refreshTokens(String refreshToken) {
        try {
            if (!validateToken(refreshToken)) {
                throw new JwtTokenException("Invalid refresh token");
            }
            
            String tokenType = extractTokenType(refreshToken);
            if (!"refresh".equals(tokenType)) {
                throw new JwtTokenException("Token is not a refresh token");
            }
            
            Long userId = extractUserId(refreshToken);
            String openId = extractOpenId(refreshToken);
            String role = extractRole(refreshToken);
            
            return generateTokenPair(userId, openId, role);
            
        } catch (Exception e) {
            logger.error("Failed to refresh tokens", e);
            throw new JwtTokenException("Failed to refresh tokens", e);
        }
    }
    
    /**
     * Create JWT token with claims
     */
    private String createToken(Map<String, Object> claims, String subject, long expiration) {
        Instant now = Instant.now();
        Instant expiryDate = now.plus(expiration, ChronoUnit.MILLIS);
        
        return Jwts.builder()
                .setClaims(claims)
                .setSubject(subject)
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(expiryDate))
                .signWith(secretKey, SignatureAlgorithm.HS512)
                .compact();
    }
    
    /**
     * Extract all claims from token
     */
    private Claims extractAllClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(secretKey)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }
    
    /**
     * Token pair containing access and refresh tokens
     */
    public static class TokenPair {
        private final String accessToken;
        private final String refreshToken;
        
        public TokenPair(String accessToken, String refreshToken) {
            this.accessToken = accessToken;
            this.refreshToken = refreshToken;
        }
        
        public String getAccessToken() {
            return accessToken;
        }
        
        public String getRefreshToken() {
            return refreshToken;
        }
    }
    
    /**
     * Generate simple token for testing (using userId as string)
     * 
     * @param userIdStr User ID as string
     * @return JWT access token
     */
    public String generateToken(String userIdStr) {
        try {
            Long userId = Long.parseLong(userIdStr);
            return generateAccessToken(userId, "test-openid-" + userIdStr);
        } catch (NumberFormatException e) {
            // For non-numeric user IDs, create a simple token
            Map<String, Object> claims = new HashMap<>();
            claims.put("userId", userIdStr);
            claims.put("openId", "test-openid-" + userIdStr);
            claims.put("type", "access");
            
            return createToken(claims, userIdStr, jwtExpiration);
        }
    }
    
    /**
     * JWT token exception
     */
    public static class JwtTokenException extends RuntimeException {
        public JwtTokenException(String message) {
            super(message);
        }
        
        public JwtTokenException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}