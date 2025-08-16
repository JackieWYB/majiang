package com.mahjong.config;

import com.mahjong.service.RateLimitService;
import com.mahjong.service.SuspiciousActivityService;
import com.mahjong.service.InputValidationService;
import com.mahjong.service.GameAuditService;
import com.mahjong.service.JwtTokenService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

import java.security.Principal;

/**
 * WebSocket security interceptor for rate limiting and validation
 */
@Component
public class WebSocketSecurityInterceptor implements ChannelInterceptor {

    private static final Logger logger = LoggerFactory.getLogger(WebSocketSecurityInterceptor.class);

    @Autowired
    private RateLimitService rateLimitService;

    @Autowired
    private SuspiciousActivityService suspiciousActivityService;

    @Autowired
    private InputValidationService inputValidationService;

    @Autowired
    private GameAuditService gameAuditService;

    @Autowired
    private JwtTokenService jwtTokenService;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        
        if (accessor != null) {
            StompCommand command = accessor.getCommand();
            String sessionId = accessor.getSessionId();
            Principal user = accessor.getUser();
            String destination = accessor.getDestination();
            
            try {
                // Apply security checks based on command type
                switch (command) {
                    case CONNECT:
                        return handleConnect(message, accessor);
                    case SEND:
                        return handleSend(message, accessor, user, destination);
                    case SUBSCRIBE:
                        return handleSubscribe(message, accessor, user, destination);
                    case DISCONNECT:
                        return handleDisconnect(message, accessor, user);
                    default:
                        break;
                }
                
            } catch (Exception e) {
                logger.error("WebSocket security check failed for session: {}, command: {}, error: {}", 
                        sessionId, command, e.getMessage());
                
                if (user != null) {
                    gameAuditService.logSecurityEvent(user.getName(), "WEBSOCKET_SECURITY_ERROR", 
                            e.getMessage(), getClientIp(accessor), "MEDIUM");
                }
                
                // Block the message
                return null;
            }
        }
        
        return message;
    }

    private Message<?> handleConnect(Message<?> message, StompHeaderAccessor accessor) {
        String sessionId = accessor.getSessionId();
        String clientIp = getClientIp(accessor);
        
        logger.info("WebSocket connection attempt - Session: {}, IP: {}", sessionId, clientIp);
        
        // Check for connection rate limiting by IP
        if (!rateLimitService.isLoginAttemptAllowed(clientIp)) {
            logger.warn("WebSocket connection rate limit exceeded for IP: {}", clientIp);
            suspiciousActivityService.logSuspiciousActivity(null, clientIp, "WEBSOCKET_CONNECTION_RATE_LIMIT", 
                    "Too many connection attempts");
            return null;
        }
        
        return message;
    }

    private Message<?> handleSend(Message<?> message, StompHeaderAccessor accessor, Principal user, String destination) {
        if (user == null) {
            logger.warn("Unauthenticated WebSocket send attempt - Session: {}, Destination: {}", 
                    accessor.getSessionId(), destination);
            return null;
        }
        
        String userId = user.getName();
        String clientIp = getClientIp(accessor);
        
        // Apply rate limiting for WebSocket messages
        if (!rateLimitService.isWebSocketMessageAllowed(userId)) {
            logger.warn("WebSocket message rate limit exceeded for user: {}", userId);
            suspiciousActivityService.logSuspiciousActivity(userId, clientIp, "WEBSOCKET_MESSAGE_RATE_LIMIT", 
                    "Too many messages sent");
            return null;
        }
        
        // Validate destination format
        if (destination != null && !isValidDestination(destination)) {
            logger.warn("Invalid WebSocket destination: {} from user: {}", destination, userId);
            suspiciousActivityService.logSuspiciousActivity(userId, clientIp, "INVALID_WEBSOCKET_DESTINATION", 
                    "Destination: " + destination);
            return null;
        }
        
        // Check for rapid successive messages (potential bot behavior)
        if (suspiciousActivityService.isRapidActionDetected(userId, "WEBSOCKET_MESSAGE")) {
            logger.warn("Rapid WebSocket messages detected for user: {}", userId);
            return null;
        }
        
        // Validate message payload
        Object payload = message.getPayload();
        if (payload != null && !isValidMessagePayload(payload.toString())) {
            logger.warn("Invalid WebSocket message payload from user: {}", userId);
            suspiciousActivityService.logSuspiciousActivity(userId, clientIp, "INVALID_WEBSOCKET_PAYLOAD", 
                    "Suspicious content in message");
            return null;
        }
        
        // Log game actions for audit
        if (destination != null && destination.startsWith("/app/room/")) {
            gameAuditService.logGameAction(extractGameId(destination), userId, "WEBSOCKET_MESSAGE", 
                    payload, clientIp);
        }
        
        return message;
    }

    private Message<?> handleSubscribe(Message<?> message, StompHeaderAccessor accessor, Principal user, String destination) {
        if (user == null) {
            logger.warn("Unauthenticated WebSocket subscribe attempt - Session: {}, Destination: {}", 
                    accessor.getSessionId(), destination);
            return null;
        }
        
        String userId = user.getName();
        String clientIp = getClientIp(accessor);
        
        // Validate subscription destination
        if (destination != null && !isValidSubscriptionDestination(destination, userId)) {
            logger.warn("Invalid subscription destination: {} for user: {}", destination, userId);
            suspiciousActivityService.logSuspiciousActivity(userId, clientIp, "INVALID_SUBSCRIPTION", 
                    "Destination: " + destination);
            return null;
        }
        
        logger.debug("WebSocket subscription - User: {}, Destination: {}", userId, destination);
        return message;
    }

    private Message<?> handleDisconnect(Message<?> message, StompHeaderAccessor accessor, Principal user) {
        String sessionId = accessor.getSessionId();
        String userId = user != null ? user.getName() : "anonymous";
        
        logger.info("WebSocket disconnection - Session: {}, User: {}", sessionId, userId);
        return message;
    }

    private boolean isValidDestination(String destination) {
        if (destination == null) return false;
        
        // Only allow specific destination patterns
        return destination.startsWith("/app/room/") ||
               destination.startsWith("/app/game/") ||
               destination.startsWith("/app/user/");
    }

    private boolean isValidSubscriptionDestination(String destination, String userId) {
        if (destination == null) return false;
        
        // Users can only subscribe to their own topics or public game topics
        if (destination.startsWith("/user/queue/")) {
            return destination.contains("/" + userId + "/");
        }
        
        if (destination.startsWith("/topic/room/")) {
            // TODO: Verify user is actually in the room
            return true;
        }
        
        return destination.startsWith("/topic/game/") ||
               destination.startsWith("/topic/lobby");
    }

    private boolean isValidMessagePayload(String payload) {
        if (payload == null) return true;
        
        // Check for suspicious content
        if (inputValidationService.containsSuspiciousContent(payload)) {
            return false;
        }
        
        // Check payload size (prevent large message attacks)
        if (payload.length() > 10000) { // 10KB limit
            return false;
        }
        
        return true;
    }

    private String extractGameId(String destination) {
        // Extract game/room ID from destination like "/app/room/123456/play"
        String[] parts = destination.split("/");
        if (parts.length >= 4) {
            return parts[3];
        }
        return null;
    }

    private String getClientIp(StompHeaderAccessor accessor) {
        // Try to get client IP from headers
        String xForwardedFor = accessor.getFirstNativeHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        
        String xRealIp = accessor.getFirstNativeHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }
        
        // Fallback to session attributes if available
        return "unknown";
    }
}