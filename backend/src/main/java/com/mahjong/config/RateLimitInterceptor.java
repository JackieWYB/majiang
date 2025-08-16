package com.mahjong.config;

import com.mahjong.service.RateLimitService;
import com.mahjong.service.JwtTokenService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Interceptor to apply rate limiting to REST API endpoints
 */
@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    private static final Logger logger = LoggerFactory.getLogger(RateLimitInterceptor.class);

    @Autowired
    private RateLimitService rateLimitService;

    @Autowired
    private JwtTokenService jwtTokenService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // Skip rate limiting for health checks and actuator endpoints
        String requestURI = request.getRequestURI();
        if (requestURI.contains("/actuator/") || requestURI.contains("/health")) {
            return true;
        }

        String userId = extractUserIdFromRequest(request);
        String ipAddress = getClientIpAddress(request);

        // Apply different rate limits based on endpoint
        if (requestURI.contains("/auth/login")) {
            if (!rateLimitService.isLoginAttemptAllowed(ipAddress)) {
                logger.warn("Login rate limit exceeded for IP: {}", ipAddress);
                response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
                response.getWriter().write("{\"error\":\"Too many login attempts. Please try again later.\"}");
                response.setContentType("application/json");
                return false;
            }
        } else if (requestURI.contains("/room") && "POST".equals(request.getMethod())) {
            if (userId != null && !rateLimitService.isRoomCreationAllowed(userId)) {
                logger.warn("Room creation rate limit exceeded for user: {}", userId);
                response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
                response.getWriter().write("{\"error\":\"Too many room creation requests. Please try again later.\"}");
                response.setContentType("application/json");
                return false;
            }
        } else {
            // General API rate limiting
            if (userId != null && !rateLimitService.isApiRequestAllowed(userId)) {
                logger.warn("API rate limit exceeded for user: {}", userId);
                response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
                response.getWriter().write("{\"error\":\"Too many requests. Please slow down.\"}");
                response.setContentType("application/json");
                return false;
            }
        }

        return true;
    }

    private String extractUserIdFromRequest(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            try {
                return jwtTokenService.extractUserId(token).toString();
            } catch (Exception e) {
                logger.debug("Failed to extract user ID from token: {}", e.getMessage());
            }
        }
        return null;
    }

    private String getClientIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }
        
        return request.getRemoteAddr();
    }
}