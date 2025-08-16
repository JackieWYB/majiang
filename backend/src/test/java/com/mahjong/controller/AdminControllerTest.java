package com.mahjong.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mahjong.model.dto.AdminUserRequest;
import com.mahjong.model.dto.AdminUserResponse;
import com.mahjong.model.dto.SystemHealthResponse;
import com.mahjong.model.entity.AuditLog;
import com.mahjong.model.entity.User;
import com.mahjong.model.enums.UserRole;
import com.mahjong.model.enums.UserStatus;
import com.mahjong.service.AdminService;
import com.mahjong.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AdminController.class)
class AdminControllerTest {
    
    @Autowired
    private MockMvc mockMvc;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @MockBean
    private AdminService adminService;
    
    @MockBean
    private UserService userService;
    
    private User admin;
    private AdminUserResponse userResponse;
    
    @BeforeEach
    void setUp() {
        admin = new User();
        admin.setId(1L);
        admin.setNickname("Admin User");
        admin.setRole(UserRole.ADMIN);
        admin.setStatus(UserStatus.ACTIVE);
        
        userResponse = new AdminUserResponse();
        userResponse.setId(2L);
        userResponse.setNickname("Test User");
        userResponse.setStatus(UserStatus.ACTIVE);
        userResponse.setRole(UserRole.USER);
        userResponse.setCoins(1000);
        userResponse.setRoomCards(10);
    }
    
    @Test
    @WithMockUser(roles = "ADMIN")
    void getSystemHealth_Success() throws Exception {
        // Given
        SystemHealthResponse health = new SystemHealthResponse("HEALTHY");
        health.setTotalUsers(1000);
        health.setActiveRooms(50);
        health.setOnlineUsers(200);
        
        when(adminService.getSystemHealth()).thenReturn(health);
        
        // When & Then
        mockMvc.perform(get("/api/admin/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("HEALTHY"))
                .andExpect(jsonPath("$.data.totalUsers").value(1000))
                .andExpect(jsonPath("$.data.activeRooms").value(50))
                .andExpect(jsonPath("$.data.onlineUsers").value(200));
    }
    
    @Test
    @WithMockUser(roles = "USER")
    void getSystemHealth_AccessDenied() throws Exception {
        // When & Then
        mockMvc.perform(get("/api/admin/health"))
                .andExpect(status().isForbidden());
    }
    
    @Test
    @WithMockUser(roles = "ADMIN")
    void getUsers_Success() throws Exception {
        // Given
        List<AdminUserResponse> users = List.of(userResponse);
        Page<AdminUserResponse> userPage = new PageImpl<>(users);
        
        when(adminService.getUsers(any(Pageable.class))).thenReturn(userPage);
        
        // When & Then
        mockMvc.perform(get("/api/admin/users")
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content").isArray())
                .andExpect(jsonPath("$.data.content[0].id").value(2))
                .andExpect(jsonPath("$.data.content[0].nickname").value("Test User"));
    }
    
    @Test
    @WithMockUser(roles = "ADMIN")
    void searchUsers_Success() throws Exception {
        // Given
        List<AdminUserResponse> users = List.of(userResponse);
        Page<AdminUserResponse> userPage = new PageImpl<>(users);
        
        when(adminService.searchUsers(eq("Test"), any(Pageable.class))).thenReturn(userPage);
        
        // When & Then
        mockMvc.perform(get("/api/admin/users/search")
                        .param("query", "Test")
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content[0].nickname").value("Test User"));
    }
    
    @Test
    @WithMockUser(roles = "ADMIN")
    void getBannedUsers_Success() throws Exception {
        // Given
        userResponse.setStatus(UserStatus.BANNED);
        userResponse.setBanReason("Test ban");
        userResponse.setBannedAt(LocalDateTime.now());
        
        List<AdminUserResponse> bannedUsers = List.of(userResponse);
        Page<AdminUserResponse> userPage = new PageImpl<>(bannedUsers);
        
        when(adminService.getBannedUsers(any(Pageable.class))).thenReturn(userPage);
        
        // When & Then
        mockMvc.perform(get("/api/admin/users/banned")
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content[0].status").value("BANNED"))
                .andExpect(jsonPath("$.data.content[0].banReason").value("Test ban"));
    }
    
    @Test
    @WithMockUser(roles = "ADMIN")
    void banUser_Success() throws Exception {
        // Given
        AdminUserRequest request = new AdminUserRequest();
        request.setUserId(2L);
        request.setReason("Violation of terms");
        
        userResponse.setStatus(UserStatus.BANNED);
        userResponse.setBanReason("Violation of terms");
        userResponse.setBannedAt(LocalDateTime.now());
        userResponse.setBannedBy(1L);
        
        when(userService.getCurrentUser(any())).thenReturn(admin);
        when(adminService.banUser(eq(2L), eq("Violation of terms"), eq(admin), any()))
                .thenReturn(userResponse);
        
        // When & Then
        mockMvc.perform(post("/api/admin/users/2/ban")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("BANNED"))
                .andExpect(jsonPath("$.data.banReason").value("Violation of terms"));
    }
    
    @Test
    @WithMockUser(roles = "ADMIN")
    void banUser_UserNotFound() throws Exception {
        // Given
        AdminUserRequest request = new AdminUserRequest();
        request.setUserId(999L);
        request.setReason("Test reason");
        
        when(userService.getCurrentUser(any())).thenReturn(admin);
        when(adminService.banUser(eq(999L), eq("Test reason"), eq(admin), any()))
                .thenThrow(new IllegalArgumentException("User not found: 999"));
        
        // When & Then
        mockMvc.perform(post("/api/admin/users/999/ban")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("User not found: 999"));
    }
    
    @Test
    @WithMockUser(roles = "ADMIN")
    void unbanUser_Success() throws Exception {
        // Given
        userResponse.setStatus(UserStatus.ACTIVE);
        userResponse.setBanReason(null);
        userResponse.setBannedAt(null);
        userResponse.setBannedBy(null);
        
        when(userService.getCurrentUser(any())).thenReturn(admin);
        when(adminService.unbanUser(eq(2L), eq(admin), any())).thenReturn(userResponse);
        
        // When & Then
        mockMvc.perform(post("/api/admin/users/2/unban")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.banReason").isEmpty());
    }
    
    @Test
    @WithMockUser(roles = "ADMIN")
    void updateUserProfile_Success() throws Exception {
        // Given
        AdminUserRequest request = new AdminUserRequest();
        request.setUserId(2L);
        request.setNickname("Updated Name");
        request.setCoins(2000);
        request.setRoomCards(20);
        
        userResponse.setNickname("Updated Name");
        userResponse.setCoins(2000);
        userResponse.setRoomCards(20);
        
        when(userService.getCurrentUser(any())).thenReturn(admin);
        when(adminService.updateUserProfile(eq(2L), any(AdminUserRequest.class), eq(admin), any()))
                .thenReturn(userResponse);
        
        // When & Then
        mockMvc.perform(put("/api/admin/users/2")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.nickname").value("Updated Name"))
                .andExpect(jsonPath("$.data.coins").value(2000))
                .andExpect(jsonPath("$.data.roomCards").value(20));
    }
    
    @Test
    @WithMockUser(roles = "ADMIN")
    void forceDissolveRoom_Success() throws Exception {
        // Given
        when(userService.getCurrentUser(any())).thenReturn(admin);
        
        // When & Then
        mockMvc.perform(post("/api/admin/rooms/123456/dissolve")
                        .with(csrf())
                        .param("reason", "Admin intervention"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").value("Room dissolved successfully"));
    }
    
    @Test
    @WithMockUser(roles = "ADMIN")
    void getAuditLogs_Success() throws Exception {
        // Given
        AuditLog log = new AuditLog(1L, "Admin", "BAN_USER", "USER", "2", "Test User", "Test ban");
        List<AuditLog> logs = List.of(log);
        Page<AuditLog> logPage = new PageImpl<>(logs);
        
        when(adminService.getAuditLogs(any(Pageable.class))).thenReturn(logPage);
        
        // When & Then
        mockMvc.perform(get("/api/admin/audit-logs")
                        .param("page", "0")
                        .param("size", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content[0].action").value("BAN_USER"))
                .andExpect(jsonPath("$.data.content[0].adminName").value("Admin"));
    }
    
    @Test
    @WithMockUser(roles = "ADMIN")
    void getAuditLogsByAdmin_Success() throws Exception {
        // Given
        AuditLog log = new AuditLog(1L, "Admin", "BAN_USER", "USER", "2", "Test User", "Test ban");
        List<AuditLog> logs = List.of(log);
        Page<AuditLog> logPage = new PageImpl<>(logs);
        
        when(adminService.getAuditLogsByAdmin(eq(1L), any(Pageable.class))).thenReturn(logPage);
        
        // When & Then
        mockMvc.perform(get("/api/admin/audit-logs")
                        .param("adminId", "1")
                        .param("page", "0")
                        .param("size", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content[0].adminId").value(1));
    }
    
    @Test
    @WithMockUser(roles = "SUPER_ADMIN")
    void detectSuspiciousActivity_Success() throws Exception {
        // Given
        AuditLog log1 = new AuditLog(1L, "Admin", "BAN_USER", "USER", "2", "Test User", "First ban");
        AuditLog log2 = new AuditLog(1L, "Admin", "UNBAN_USER", "USER", "2", "Test User", "Unban");
        List<AuditLog> suspiciousLogs = List.of(log1, log2);
        
        when(adminService.detectSuspiciousActivity(eq(1L), eq("USER"), eq("2"), eq(24)))
                .thenReturn(suspiciousLogs);
        
        // When & Then
        mockMvc.perform(get("/api/admin/audit-logs/suspicious")
                        .param("adminId", "1")
                        .param("targetType", "USER")
                        .param("targetId", "2")
                        .param("hours", "24"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(2));
    }
    
    @Test
    @WithMockUser(roles = "ADMIN")
    void detectSuspiciousActivity_AccessDenied() throws Exception {
        // When & Then
        mockMvc.perform(get("/api/admin/audit-logs/suspicious")
                        .param("adminId", "1")
                        .param("targetType", "USER")
                        .param("targetId", "2"))
                .andExpect(status().isForbidden());
    }
}