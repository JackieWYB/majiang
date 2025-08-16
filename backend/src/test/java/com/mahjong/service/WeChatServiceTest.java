package com.mahjong.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mahjong.service.WeChatService.WeChatAuthException;
import com.mahjong.service.WeChatService.WeChatSession;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class WeChatServiceTest {
    
    private MockWebServer mockWebServer;
    private WeChatService weChatService;
    
    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();
        
        String baseUrl = mockWebServer.url("/").toString();
        
        weChatService = new WeChatService(
                WebClient.builder(),
                "test-app-id",
                "test-app-secret",
                baseUrl.replaceAll("/$", "") + "/sns/jscode2session",
                new ObjectMapper()
        );
    }
    
    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }
    
    @Test
    void shouldSuccessfullyExchangeCodeForSession() {
        // Given
        String code = "test-code";
        String responseJson = """
            {
                "openid": "test-openid",
                "unionid": "test-unionid",
                "session_key": "test-session-key"
            }
            """;
        
        mockWebServer.enqueue(new MockResponse()
                .setBody(responseJson)
                .addHeader("Content-Type", "application/json"));
        
        // When
        WeChatSession session = weChatService.code2Session(code);
        
        // Then
        assertNotNull(session);
        assertEquals("test-openid", session.getOpenId());
        assertEquals("test-unionid", session.getUnionId());
        assertEquals("test-session-key", session.getSessionKey());
    }
    
    @Test
    void shouldHandleWeChatApiError() {
        // Given
        String code = "invalid-code";
        String responseJson = """
            {
                "errcode": 40013,
                "errmsg": "invalid appid"
            }
            """;
        
        mockWebServer.enqueue(new MockResponse()
                .setBody(responseJson)
                .addHeader("Content-Type", "application/json"));
        
        // When & Then
        WeChatAuthException exception = assertThrows(
                WeChatAuthException.class,
                () -> weChatService.code2Session(code)
        );
        
        assertTrue(exception.getMessage().contains("invalid appid"));
    }
    
    @Test
    void shouldHandleEmptyResponse() {
        // Given
        String code = "test-code";
        
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("")
                .addHeader("Content-Type", "application/json"));
        
        // When & Then
        assertThrows(
                WeChatAuthException.class,
                () -> weChatService.code2Session(code)
        );
    }
    
    @Test
    void shouldHandleMissingOpenId() {
        // Given
        String code = "test-code";
        String responseJson = """
            {
                "session_key": "test-session-key"
            }
            """;
        
        mockWebServer.enqueue(new MockResponse()
                .setBody(responseJson)
                .addHeader("Content-Type", "application/json"));
        
        // When & Then
        WeChatAuthException exception = assertThrows(
                WeChatAuthException.class,
                () -> weChatService.code2Session(code)
        );
        
        assertTrue(exception.getMessage().contains("missing openid"));
    }
    
    @Test
    void shouldHandleNetworkError() {
        // Given
        String code = "test-code";
        
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(500)
                .setBody("Internal Server Error"));
        
        // When & Then
        assertThrows(
                WeChatAuthException.class,
                () -> weChatService.code2Session(code)
        );
    }
    
    @Test
    void shouldHandleSuccessWithoutUnionId() {
        // Given
        String code = "test-code";
        String responseJson = """
            {
                "openid": "test-openid",
                "session_key": "test-session-key"
            }
            """;
        
        mockWebServer.enqueue(new MockResponse()
                .setBody(responseJson)
                .addHeader("Content-Type", "application/json"));
        
        // When
        WeChatSession session = weChatService.code2Session(code);
        
        // Then
        assertNotNull(session);
        assertEquals("test-openid", session.getOpenId());
        assertNull(session.getUnionId());
        assertEquals("test-session-key", session.getSessionKey());
    }
}