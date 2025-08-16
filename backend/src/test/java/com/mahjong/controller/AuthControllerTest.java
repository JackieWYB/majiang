package com.mahjong.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mahjong.controller.AuthController.LoginRequest;
import com.mahjong.controller.AuthController.RefreshRequest;
import com.mahjong.controller.AuthController.UpdateProfileRequest;
import com.mahjong.model.entity.User;
import com.mahjong.model.enums.UserStatus;
import com.mahjong.service.JwtTokenService;
import com.mahjong.service.JwtTokenService.TokenPair;
import com.mahjong.service.UserService;
import com.mahjong.service.WeChatService;
import com.mahjong.service.WeChatService.WeChatSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuthController.class)
class AuthControllerTest {
    
    @Autowired
    private MockMvc mockMvc;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @MockBean
    private WeChatService weChatService;
    
    @MockBean
    private UserService userService;
    
    @MockBean
    private JwtTokenService jwtTokenService;
    
    private User testUser;
    private WeChatSession testSession;
    private TokenPair testTokenPair;
    
    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setId(1L);
        testUser.setOpenId("test-openid");
        testUser.setUnionId("test-unionid");
        testUser.setNickname("Test User");
        testUser.setAvatar("test-avatar");
        testUser.setCoins(1000);
        testUser.setRoomCards(10);
        testUser.setStatus(UserStatus.ACTIVE);
        testUser.setCreatedAt(LocalDateTime.now());
        testUser.setUpdatedAt(LocalDateTime.now());
        
        testSession = new WeChatSession("test-openid", "test-unionid", "test-session-key");
        testTokenPair = new TokenPair("test-access-token", "test-refresh-token");
    }
    
    @Test
    void shouldLoginSuccessfully() throws Exception {
        // Given
        LoginRequest request = new LoginRequest();
        request.setCode("test-code");
        request.setNickname("Test User");
        request.setAvatar("test-avatar");
        
        when(weChatService.code2Session("test-code")).thenReturn(testSession);
        when(userService.createOrUpdateUser(
                eq("test-openid"),
                eq("test-unionid"),
                eq("Test User"),
                eq("test-avatar")
        )).thenReturn(testUser);
        when(userService.isUserActive(1L)).thenReturn(true);
        when(jwtTokenService.generateTokenPair(1L, "test-openid")).thenReturn(testTokenPair);
        
        // When & Then
        mockMvc.perform(post("/auth/login")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").value("test-access-token"))
                .andExpect(jsonPath("$.data.refreshToken").value("test-refresh-token"))
                .andExpect(jsonPath("$.data.user.id").value(1))
                .andExpect(jsonPath("$.data.user.openId").value("test-openid"))
                .andExpect(jsonPath("$.data.user.nickname").value("Test User"));
    }
    
    @Test
    void shouldRejectLoginForBannedUser() throws Exception {
        // Given
        LoginRequest request = new LoginRequest();
        request.setCode("test-code");
        
        testUser.setStatus(UserStatus.BANNED);
        
        when(weChatService.code2Session("test-code")).thenReturn(testSession);
        when(userService.createOrUpdateUser(any(), any(), any(), any())).thenReturn(testUser);
        when(userService.isUserActive(1L)).thenReturn(false);
        
        // When & Then
        mockMvc.perform(post("/auth/login")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("USER_BANNED"));
    }
    
    @Test
    void shouldHandleWeChatAuthFailure() throws Exception {
        // Given
        LoginRequest request = new LoginRequest();
        request.setCode("invalid-code");
        
        when(weChatService.code2Session("invalid-code"))
                .thenThrow(new WeChatService.WeChatAuthException("Invalid code"));
        
        // When & Then
        mockMvc.perform(post("/auth/login")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("WECHAT_AUTH_FAILED"));
    }
    
    @Test
    void shouldRefreshTokensSuccessfully() throws Exception {
        // Given
        RefreshRequest request = new RefreshRequest();
        request.setRefreshToken("test-refresh-token");
        
        TokenPair newTokenPair = new TokenPair("new-access-token", "new-refresh-token");
        when(jwtTokenService.refreshTokens("test-refresh-token")).thenReturn(newTokenPair);
        
        // When & Then
        mockMvc.perform(post("/auth/refresh")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").value("new-access-token"))
                .andExpect(jsonPath("$.data.refreshToken").value("new-refresh-token"));
    }
    
    @Test
    void shouldHandleInvalidRefreshToken() throws Exception {
        // Given
        RefreshRequest request = new RefreshRequest();
        request.setRefreshToken("invalid-refresh-token");
        
        when(jwtTokenService.refreshTokens("invalid-refresh-token"))
                .thenThrow(new JwtTokenService.JwtTokenException("Invalid refresh token"));
        
        // When & Then
        mockMvc.perform(post("/auth/refresh")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("TOKEN_REFRESH_FAILED"));
    }
    
    @Test
    @WithMockUser(username = "1")
    void shouldGetUserProfile() throws Exception {
        // Given
        when(userService.findById(1L)).thenReturn(Optional.of(testUser));
        
        // When & Then
        mockMvc.perform(get("/auth/profile"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.openId").value("test-openid"))
                .andExpect(jsonPath("$.data.nickname").value("Test User"))
                .andExpect(jsonPath("$.data.coins").value(1000))
                .andExpect(jsonPath("$.data.roomCards").value(10));
    }
    
    @Test
    @WithMockUser(username = "999")
    void shouldReturn404ForNonExistentUser() throws Exception {
        // Given
        when(userService.findById(999L)).thenReturn(Optional.empty());
        
        // When & Then
        mockMvc.perform(get("/auth/profile"))
                .andExpect(status().isNotFound());
    }
    
    @Test
    @WithMockUser(username = "1")
    void shouldUpdateUserProfile() throws Exception {
        // Given
        UpdateProfileRequest request = new UpdateProfileRequest();
        request.setNickname("Updated Name");
        request.setAvatar("updated-avatar");
        
        User updatedUser = new User();
        updatedUser.setId(1L);
        updatedUser.setOpenId("test-openid");
        updatedUser.setNickname("Updated Name");
        updatedUser.setAvatar("updated-avatar");
        updatedUser.setCoins(1000);
        updatedUser.setRoomCards(10);
        updatedUser.setStatus(UserStatus.ACTIVE);
        
        when(userService.updateProfile(1L, "Updated Name", "updated-avatar"))
                .thenReturn(updatedUser);
        
        // When & Then
        mockMvc.perform(put("/auth/profile")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.nickname").value("Updated Name"))
                .andExpect(jsonPath("$.data.avatar").value("updated-avatar"));
    }
    
    @Test
    @WithMockUser(username = "1")
    void shouldLogoutSuccessfully() throws Exception {
        // When & Then
        mockMvc.perform(post("/auth/logout")
                .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }
    
    @Test
    void shouldRejectLoginWithoutCode() throws Exception {
        // Given
        LoginRequest request = new LoginRequest();
        // No code set
        
        // When & Then
        mockMvc.perform(post("/auth/login")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }
    
    @Test
    void shouldRejectRefreshWithoutToken() throws Exception {
        // Given
        RefreshRequest request = new RefreshRequest();
        // No refresh token set
        
        // When & Then
        mockMvc.perform(post("/auth/refresh")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }
    
    @Test
    void shouldRequireAuthenticationForProfile() throws Exception {
        // When & Then
        mockMvc.perform(get("/auth/profile"))
                .andExpect(status().isUnauthorized());
    }
    
    @Test
    void shouldRequireAuthenticationForProfileUpdate() throws Exception {
        // Given
        UpdateProfileRequest request = new UpdateProfileRequest();
        request.setNickname("New Name");
        
        // When & Then
        mockMvc.perform(put("/auth/profile")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }
}