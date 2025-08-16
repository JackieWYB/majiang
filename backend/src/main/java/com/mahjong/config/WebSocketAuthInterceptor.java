package com.mahjong.config;

import com.mahjong.service.JwtTokenService;
import com.mahjong.service.WebSocketSessionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * WebSocket authentication interceptor for JWT token validation
 */
@Component
public class WebSocketAuthInterceptor implements ChannelInterceptor {
    
    private static final Logger logger = LoggerFactory.getLogger(WebSocketAuthInterceptor.class);
    
    private final JwtTokenService jwtTokenService;
    private final WebSocketSessionService sessionService;
    
    @Autowired
    public WebSocketAuthInterceptor(JwtTokenService jwtTokenService,
                                   WebSocketSessionService sessionService) {
        this.jwtTokenService = jwtTokenService;
        this.sessionService = sessionService;
    }
    
    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        
        if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
            // Handle connection authentication
            handleConnect(accessor);
        }
        
        return message;
    }
    
    /**
     * Handle WebSocket connection authentication
     */
    private void handleConnect(StompHeaderAccessor accessor) {
        try {
            // Get JWT token from headers
            String token = getTokenFromHeaders(accessor);
            
            if (token == null) {
                logger.warn("WebSocket connection attempt without token, sessionId: {}", 
                        accessor.getSessionId());
                return;
            }
            
            // Validate token and extract user ID
            if (jwtTokenService.validateToken(token)) {
                String userId = jwtTokenService.extractUserIdAsString(token);
                
                if (userId != null) {
                    // Create authentication object
                    Authentication auth = new UsernamePasswordAuthenticationToken(
                            userId, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
                    
                    // Set user in accessor
                    accessor.setUser(auth);
                    
                    logger.info("WebSocket authentication successful for user: {} with sessionId: {}", 
                            userId, accessor.getSessionId());
                    
                } else {
                    logger.warn("Failed to extract user ID from token, sessionId: {}", 
                            accessor.getSessionId());
                }
            } else {
                logger.warn("Invalid JWT token for WebSocket connection, sessionId: {}", 
                        accessor.getSessionId());
            }
            
        } catch (Exception e) {
            logger.error("Error during WebSocket authentication for sessionId: {}", 
                    accessor.getSessionId(), e);
        }
    }
    
    /**
     * Extract JWT token from STOMP headers
     */
    private String getTokenFromHeaders(StompHeaderAccessor accessor) {
        // Try to get token from Authorization header
        List<String> authHeaders = accessor.getNativeHeader("Authorization");
        if (authHeaders != null && !authHeaders.isEmpty()) {
            String authHeader = authHeaders.get(0);
            if (authHeader.startsWith("Bearer ")) {
                return authHeader.substring(7);
            }
        }
        
        // Try to get token from token header (alternative)
        List<String> tokenHeaders = accessor.getNativeHeader("token");
        if (tokenHeaders != null && !tokenHeaders.isEmpty()) {
            return tokenHeaders.get(0);
        }
        
        // Try to get token from query parameters (fallback for some clients)
        List<String> tokenParams = accessor.getNativeHeader("token-param");
        if (tokenParams != null && !tokenParams.isEmpty()) {
            return tokenParams.get(0);
        }
        
        return null;
    }
}