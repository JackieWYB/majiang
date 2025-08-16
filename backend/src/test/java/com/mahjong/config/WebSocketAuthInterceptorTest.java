package com.mahjong.config;

import com.mahjong.service.JwtTokenService;
import com.mahjong.service.WebSocketSessionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.core.Authentication;

import java.security.Principal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for WebSocketAuthInterceptor
 */
@ExtendWith(MockitoExtension.class)
class WebSocketAuthInterceptorTest {
    
    @Mock
    private JwtTokenService jwtTokenService;
    
    @Mock
    private WebSocketSessionService sessionService;
    
    @Mock
    private MessageChannel messageChannel;
    
    private WebSocketAuthInterceptor interceptor;
    
    @BeforeEach
    void setUp() {
        interceptor = new WebSocketAuthInterceptor(jwtTokenService, sessionService);
    }
    
    @Test
    void shouldAuthenticateValidToken() {
        String token = "valid-jwt-token";
        String userId = "user123";
        
        when(jwtTokenService.validateToken(token)).thenReturn(true);
        when(jwtTokenService.extractUserIdAsString(token)).thenReturn(userId);
        
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setNativeHeader("Authorization", "Bearer " + token);
        accessor.setSessionId("session123");
        
        Message<?> message = MessageBuilder.createMessage("", accessor.getMessageHeaders());
        
        Message<?> result = interceptor.preSend(message, messageChannel);
        
        assertNotNull(result);
        Principal user = accessor.getUser();
        assertNotNull(user);
        assertTrue(user instanceof Authentication);
        assertEquals(userId, user.getName());
        
        verify(jwtTokenService).validateToken(token);
        verify(jwtTokenService).extractUserIdAsString(token);
    }
    
    @Test
    void shouldRejectInvalidToken() {
        String token = "invalid-jwt-token";
        
        when(jwtTokenService.validateToken(token)).thenReturn(false);
        
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setNativeHeader("Authorization", "Bearer " + token);
        accessor.setSessionId("session123");
        
        Message<?> message = MessageBuilder.createMessage("", accessor.getMessageHeaders());
        
        Message<?> result = interceptor.preSend(message, messageChannel);
        
        assertNotNull(result);
        assertNull(accessor.getUser());
        
        verify(jwtTokenService).validateToken(token);
        verify(jwtTokenService, never()).extractUserIdAsString(token);
    }
    
    @Test
    void shouldHandleConnectionWithoutToken() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setSessionId("session123");
        // No Authorization header
        
        Message<?> message = MessageBuilder.createMessage("", accessor.getMessageHeaders());
        
        Message<?> result = interceptor.preSend(message, messageChannel);
        
        assertNotNull(result);
        assertNull(accessor.getUser());
        
        verify(jwtTokenService, never()).validateToken(any());
        verify(jwtTokenService, never()).extractUserIdAsString(any());
    }
    
    @Test
    void shouldExtractTokenFromAlternativeHeader() {
        String token = "valid-jwt-token";
        String userId = "user123";
        
        when(jwtTokenService.validateToken(token)).thenReturn(true);
        when(jwtTokenService.extractUserIdAsString(token)).thenReturn(userId);
        
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setNativeHeader("token", token); // Alternative header
        accessor.setSessionId("session123");
        
        Message<?> message = MessageBuilder.createMessage("", accessor.getMessageHeaders());
        
        Message<?> result = interceptor.preSend(message, messageChannel);
        
        assertNotNull(result);
        Principal user = accessor.getUser();
        assertNotNull(user);
        assertTrue(user instanceof Authentication);
        assertEquals(userId, user.getName());
        
        verify(jwtTokenService).validateToken(token);
        verify(jwtTokenService).extractUserIdAsString(token);
    }
    
    @Test
    void shouldIgnoreNonConnectMessages() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
        accessor.setSessionId("session123");
        
        Message<?> message = MessageBuilder.createMessage("", accessor.getMessageHeaders());
        
        Message<?> result = interceptor.preSend(message, messageChannel);
        
        assertNotNull(result);
        assertNull(accessor.getUser());
        
        verify(jwtTokenService, never()).validateToken(any());
        verify(jwtTokenService, never()).extractUserIdAsString(any());
    }
    
    @Test
    void shouldHandleTokenValidationException() {
        String token = "problematic-token";
        
        when(jwtTokenService.validateToken(token)).thenThrow(new RuntimeException("Token validation error"));
        
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setNativeHeader("Authorization", "Bearer " + token);
        accessor.setSessionId("session123");
        
        Message<?> message = MessageBuilder.createMessage("", accessor.getMessageHeaders());
        
        // Should not throw exception, should handle gracefully
        Message<?> result = interceptor.preSend(message, messageChannel);
        
        assertNotNull(result);
        assertNull(accessor.getUser());
        
        verify(jwtTokenService).validateToken(token);
    }
}