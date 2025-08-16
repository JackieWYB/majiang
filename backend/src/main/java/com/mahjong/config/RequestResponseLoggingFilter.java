package com.mahjong.config;

import com.mahjong.service.GameAuditService;
import com.mahjong.service.SuspiciousActivityService;
import com.mahjong.service.InputValidationService;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.stream.Collectors;

/**
 * Filter for logging HTTP requests and responses with security monitoring
 */
@Component
public class RequestResponseLoggingFilter implements Filter {

    private static final Logger logger = LoggerFactory.getLogger(RequestResponseLoggingFilter.class);

    @Autowired
    private GameAuditService gameAuditService;

    @Autowired
    private SuspiciousActivityService suspiciousActivityService;

    @Autowired
    private InputValidationService inputValidationService;

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        // Skip logging for actuator endpoints and static resources
        String requestURI = httpRequest.getRequestURI();
        if (shouldSkipLogging(requestURI)) {
            chain.doFilter(request, response);
            return;
        }

        ContentCachingRequestWrapper wrappedRequest = new ContentCachingRequestWrapper(httpRequest);
        ContentCachingResponseWrapper wrappedResponse = new ContentCachingResponseWrapper(httpResponse);

        long startTime = System.currentTimeMillis();
        String clientIp = getClientIpAddress(httpRequest);
        String userAgent = httpRequest.getHeader("User-Agent");

        try {
            // Pre-processing security checks
            performSecurityChecks(wrappedRequest, clientIp, userAgent);

            chain.doFilter(wrappedRequest, wrappedResponse);

        } catch (Exception e) {
            logger.error("Error processing request: {}", e.getMessage(), e);
            gameAuditService.logSecurityEvent(null, "REQUEST_PROCESSING_ERROR", 
                    e.getMessage(), clientIp, "MEDIUM");
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            
            // Log request/response details
            logRequestResponse(wrappedRequest, wrappedResponse, duration, clientIp, userAgent);
            
            // Copy response body back to original response
            wrappedResponse.copyBodyToResponse();
        }
    }

    private void performSecurityChecks(ContentCachingRequestWrapper request, String clientIp, String userAgent) {
        String requestURI = request.getRequestURI();
        String method = request.getMethod();
        
        // Check for suspicious user agents
        if (userAgent != null && isSuspiciousUserAgent(userAgent)) {
            suspiciousActivityService.logSuspiciousActivity(null, clientIp, "SUSPICIOUS_USER_AGENT", 
                    "User-Agent: " + userAgent);
        }

        // Check for path traversal attempts
        if (requestURI.contains("..") || requestURI.contains("%2e%2e")) {
            suspiciousActivityService.logSuspiciousActivity(null, clientIp, "PATH_TRAVERSAL_ATTEMPT", 
                    "URI: " + requestURI);
        }

        // Check for SQL injection patterns in parameters
        String queryString = request.getQueryString();
        if (queryString != null && inputValidationService.containsSuspiciousContent(queryString)) {
            suspiciousActivityService.logSuspiciousActivity(null, clientIp, "SUSPICIOUS_QUERY_PARAMETERS", 
                    "Query: " + queryString);
        }

        // Check for unusual request patterns
        if (isUnusualRequestPattern(method, requestURI)) {
            suspiciousActivityService.logSuspiciousActivity(null, clientIp, "UNUSUAL_REQUEST_PATTERN", 
                    String.format("Method: %s, URI: %s", method, requestURI));
        }
    }

    private void logRequestResponse(ContentCachingRequestWrapper request, ContentCachingResponseWrapper response, 
                                  long duration, String clientIp, String userAgent) {
        try {
            String method = request.getMethod();
            String uri = request.getRequestURI();
            String queryString = request.getQueryString();
            int status = response.getStatus();
            
            // Get request body (for POST/PUT requests)
            String requestBody = "";
            if ("POST".equals(method) || "PUT".equals(method)) {
                byte[] content = request.getContentAsByteArray();
                if (content.length > 0) {
                    requestBody = new String(content, StandardCharsets.UTF_8);
                    // Sanitize sensitive data
                    requestBody = sanitizeSensitiveData(requestBody);
                }
            }

            // Get response body (only for errors or specific endpoints)
            String responseBody = "";
            if (status >= 400 || shouldLogResponseBody(uri)) {
                byte[] content = response.getContentAsByteArray();
                if (content.length > 0) {
                    responseBody = new String(content, StandardCharsets.UTF_8);
                    // Limit response body size in logs
                    if (responseBody.length() > 1000) {
                        responseBody = responseBody.substring(0, 1000) + "... [truncated]";
                    }
                }
            }

            // Get headers (excluding sensitive ones)
            String headers = Collections.list(request.getHeaderNames()).stream()
                    .filter(name -> !isSensitiveHeader(name))
                    .map(name -> name + "=" + request.getHeader(name))
                    .collect(Collectors.joining(", "));

            // Log with appropriate level based on status
            if (status >= 500) {
                logger.error("HTTP {} {} {} - Status: {}, Duration: {}ms, IP: {}, UA: {}, Headers: {}, Request: {}, Response: {}", 
                        method, uri, queryString != null ? "?" + queryString : "", status, duration, 
                        clientIp, userAgent, headers, requestBody, responseBody);
            } else if (status >= 400) {
                logger.warn("HTTP {} {} {} - Status: {}, Duration: {}ms, IP: {}, UA: {}, Headers: {}, Request: {}, Response: {}", 
                        method, uri, queryString != null ? "?" + queryString : "", status, duration, 
                        clientIp, userAgent, headers, requestBody, responseBody);
            } else {
                logger.info("HTTP {} {} {} - Status: {}, Duration: {}ms, IP: {}", 
                        method, uri, queryString != null ? "?" + queryString : "", status, duration, clientIp);
            }

            // Log security events for suspicious responses
            if (status == 401 || status == 403) {
                gameAuditService.logSecurityEvent(null, "UNAUTHORIZED_ACCESS_ATTEMPT", 
                        String.format("Method: %s, URI: %s, Status: %d", method, uri, status), 
                        clientIp, "MEDIUM");
            }

            // Log slow requests
            if (duration > 5000) { // 5 seconds
                gameAuditService.logSecurityEvent(null, "SLOW_REQUEST", 
                        String.format("Method: %s, URI: %s, Duration: %dms", method, uri, duration), 
                        clientIp, "LOW");
            }

        } catch (Exception e) {
            logger.error("Error logging request/response: {}", e.getMessage(), e);
        }
    }

    private boolean shouldSkipLogging(String requestURI) {
        return requestURI.contains("/actuator/") ||
               requestURI.contains("/health") ||
               requestURI.contains("/metrics") ||
               requestURI.contains("/favicon.ico") ||
               requestURI.contains("/static/") ||
               requestURI.contains("/css/") ||
               requestURI.contains("/js/") ||
               requestURI.contains("/images/");
    }

    private boolean shouldLogResponseBody(String uri) {
        return uri.contains("/auth/") ||
               uri.contains("/admin/") ||
               uri.contains("/game/");
    }

    private boolean isSensitiveHeader(String headerName) {
        String lowerName = headerName.toLowerCase();
        return lowerName.equals("authorization") ||
               lowerName.equals("cookie") ||
               lowerName.equals("x-auth-token") ||
               lowerName.contains("password") ||
               lowerName.contains("secret");
    }

    private String sanitizeSensitiveData(String data) {
        if (data == null) return "";
        
        // Replace sensitive fields with masked values
        return data.replaceAll("(?i)(\"password\"\\s*:\\s*\")[^\"]*\"", "$1***\"")
                  .replaceAll("(?i)(\"token\"\\s*:\\s*\")[^\"]*\"", "$1***\"")
                  .replaceAll("(?i)(\"secret\"\\s*:\\s*\")[^\"]*\"", "$1***\"")
                  .replaceAll("(?i)(\"openId\"\\s*:\\s*\")[^\"]*\"", "$1***\"");
    }

    private boolean isSuspiciousUserAgent(String userAgent) {
        String lowerUA = userAgent.toLowerCase();
        return lowerUA.contains("bot") ||
               lowerUA.contains("crawler") ||
               lowerUA.contains("spider") ||
               lowerUA.contains("scraper") ||
               lowerUA.contains("curl") ||
               lowerUA.contains("wget") ||
               lowerUA.length() < 10 ||
               lowerUA.length() > 500;
    }

    private boolean isUnusualRequestPattern(String method, String uri) {
        // Check for unusual HTTP methods
        if (!method.matches("GET|POST|PUT|DELETE|PATCH|OPTIONS|HEAD")) {
            return true;
        }
        
        // Check for unusual URI patterns
        if (uri.contains("..") || uri.contains("%00") || uri.contains("<script")) {
            return true;
        }
        
        return false;
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