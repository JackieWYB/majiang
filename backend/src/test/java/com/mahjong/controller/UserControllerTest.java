package com.mahjong.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mahjong.model.entity.User;
import com.mahjong.model.enums.UserRole;
import com.mahjong.model.enums.UserStatus;
import com.mahjong.service.GameHistoryService;
import com.mahjong.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(UserController.class)
class UserControllerTest {
    
    @Autowired
    private MockMvc mockMvc;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @MockBean
    private UserService userService;
    
    @MockBean
    private GameHistoryService gameHistoryService;
    
    private User testUser;
    private User adminUser;
    
    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setId(1L);
        testUser.setOpenId("test_open_id");
        testUser.setUnionId("test_union_id");
        testUser.setNickname("Test User");
        testUser.setAvatar("http://example.com/avatar.jpg");
        testUser.setCoins(1000);
        testUser.setRoomCards(10);
        testUser.setStatus(UserStatus.ACTIVE);
        testUser.setRole(UserRole.USER);
        
        adminUser = new User();
        adminUser.setId(2L);
        adminUser.setOpenId("admin_open_id");
        adminUser.setUnionId("admin_union_id");
        adminUser.setNickname("Admin User");
        adminUser.setAvatar("http://example.com/admin_avatar.jpg");
        adminUser.setCoins(5000);
        adminUser.setRoomCards(100);
        adminUser.setStatus(UserStatus.ACTIVE);
        adminUser.setRole(UserRole.ADMIN);
    }
    
    @Test
    @WithMockUser(username = "test_open_id")
    void getCurrentUserProfile_ShouldReturnUserProfile() throws Exception {
        when(userService.getCurrentUser(any())).thenReturn(testUser);
        
        mockMvc.perform(get("/api/users/profile"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.nickname").value("Test User"))
                .andExpect(jsonPath("$.data.coins").value(1000))
                .andExpect(jsonPath("$.data.roomCards").value(10))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.role").value("USER"));
        
        verify(userService).getCurrentUser(any());
    }
    
    @Test
    @WithMockUser(username = "test_open_id")
    void updateCurrentUserProfile_ShouldUpdateAndReturnProfile() throws Exception {
        UserController.UpdateUserProfileRequest request = new UserController.UpdateUserProfileRequest();
        request.setNickname("Updated User");
        request.setAvatar("http://example.com/new_avatar.jpg");
        
        User updatedUser = new User();
        updatedUser.setId(1L);
        updatedUser.setOpenId("test_open_id");
        updatedUser.setNickname("Updated User");
        updatedUser.setAvatar("http://example.com/new_avatar.jpg");
        updatedUser.setCoins(1000);
        updatedUser.setRoomCards(10);
        updatedUser.setStatus(UserStatus.ACTIVE);
        updatedUser.setRole(UserRole.USER);
        
        when(userService.getCurrentUser(any())).thenReturn(testUser);
        when(userService.updateProfile(eq(1L), eq("Updated User"), eq("http://example.com/new_avatar.jpg")))
                .thenReturn(updatedUser);
        
        mockMvc.perform(put("/api/users/profile")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.nickname").value("Updated User"))
                .andExpect(jsonPath("$.data.avatar").value("http://example.com/new_avatar.jpg"));
        
        verify(userService).getCurrentUser(any());
        verify(userService).updateProfile(eq(1L), eq("Updated User"), eq("http://example.com/new_avatar.jpg"));
    }
    
    @Test
    @WithMockUser(username = "test_open_id")
    void getUserProfile_ShouldReturnPublicProfile() throws Exception {
        when(userService.findById(1L)).thenReturn(Optional.of(testUser));
        
        mockMvc.perform(get("/api/users/1/profile"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.nickname").value("Test User"))
                .andExpect(jsonPath("$.data.avatar").value("http://example.com/avatar.jpg"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.openId").doesNotExist())
                .andExpect(jsonPath("$.data.coins").doesNotExist());
        
        verify(userService).findById(1L);
    }
    
    @Test
    @WithMockUser(username = "test_open_id")
    void getUserProfile_UserNotFound_ShouldReturn404() throws Exception {
        when(userService.findById(999L)).thenReturn(Optional.empty());
        
        mockMvc.perform(get("/api/users/999/profile"))
                .andExpect(status().isNotFound());
        
        verify(userService).findById(999L);
    }
    
    @Test
    @WithMockUser(username = "test_open_id")
    void getCurrentUserStatistics_ShouldReturnStatistics() throws Exception {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalGames", 50L);
        stats.put("wins", 30L);
        stats.put("losses", 15L);
        stats.put("draws", 5L);
        stats.put("winRate", 0.6);
        stats.put("totalScore", 1500L);
        stats.put("averageScore", 30.0);
        stats.put("dealerGames", 20L);
        stats.put("dealerWins", 15L);
        stats.put("dealerWinRate", 0.75);
        stats.put("selfDrawWins", 10L);
        stats.put("longestWinStreak", 5);
        stats.put("currentWinStreak", 2);
        
        when(userService.getCurrentUser(any())).thenReturn(testUser);
        when(gameHistoryService.getEnhancedUserStatistics(1L)).thenReturn(stats);
        
        mockMvc.perform(get("/api/users/statistics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.userId").value(1))
                .andExpect(jsonPath("$.data.totalGames").value(50))
                .andExpect(jsonPath("$.data.wins").value(30))
                .andExpect(jsonPath("$.data.winRate").value(0.6))
                .andExpect(jsonPath("$.data.totalScore").value(1500))
                .andExpect(jsonPath("$.data.longestWinStreak").value(5));
        
        verify(userService).getCurrentUser(any());
        verify(gameHistoryService).getEnhancedUserStatistics(1L);
    }
    
    @Test
    @WithMockUser(username = "test_open_id")
    void getUserStatistics_OwnData_ShouldReturnStatistics() throws Exception {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalGames", 25L);
        stats.put("wins", 15L);
        stats.put("winRate", 0.6);
        
        when(userService.getCurrentUser(any())).thenReturn(testUser);
        when(gameHistoryService.getEnhancedUserStatistics(1L)).thenReturn(stats);
        
        mockMvc.perform(get("/api/users/1/statistics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.userId").value(1))
                .andExpect(jsonPath("$.data.totalGames").value(25));
        
        verify(userService).getCurrentUser(any());
        verify(gameHistoryService).getEnhancedUserStatistics(1L);
    }
    
    @Test
    @WithMockUser(username = "test_open_id")
    void getUserStatistics_OtherUserData_ShouldReturn403() throws Exception {
        when(userService.getCurrentUser(any())).thenReturn(testUser);
        
        mockMvc.perform(get("/api/users/2/statistics"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Access denied"));
        
        verify(userService).getCurrentUser(any());
        verify(gameHistoryService, never()).getEnhancedUserStatistics(anyLong());
    }
    
    @Test
    @WithMockUser(username = "admin_open_id")
    void getUserStatistics_AdminAccess_ShouldReturnStatistics() throws Exception {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalGames", 25L);
        stats.put("wins", 15L);
        
        when(userService.getCurrentUser(any())).thenReturn(adminUser);
        when(gameHistoryService.getEnhancedUserStatistics(1L)).thenReturn(stats);
        
        mockMvc.perform(get("/api/users/1/statistics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.userId").value(1));
        
        verify(userService).getCurrentUser(any());
        verify(gameHistoryService).getEnhancedUserStatistics(1L);
    }
    
    @Test
    @WithMockUser(username = "admin_open_id")
    void updateUserCoins_AdminAccess_ShouldUpdateCoins() throws Exception {
        UserController.UpdateCoinsRequest request = new UserController.UpdateCoinsRequest();
        request.setCoins(2000);
        
        User updatedUser = new User();
        updatedUser.setId(1L);
        updatedUser.setCoins(2000);
        updatedUser.setNickname("Test User");
        updatedUser.setStatus(UserStatus.ACTIVE);
        updatedUser.setRole(UserRole.USER);
        
        when(userService.getCurrentUser(any())).thenReturn(adminUser);
        when(userService.updateCoins(1L, 2000)).thenReturn(updatedUser);
        
        mockMvc.perform(put("/api/users/1/coins")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.coins").value(2000));
        
        verify(userService).getCurrentUser(any());
        verify(userService).updateCoins(1L, 2000);
    }
    
    @Test
    @WithMockUser(username = "test_open_id")
    void updateUserCoins_NonAdminAccess_ShouldReturn403() throws Exception {
        UserController.UpdateCoinsRequest request = new UserController.UpdateCoinsRequest();
        request.setCoins(2000);
        
        when(userService.getCurrentUser(any())).thenReturn(testUser);
        
        mockMvc.perform(put("/api/users/2/coins")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Access denied"));
        
        verify(userService).getCurrentUser(any());
        verify(userService, never()).updateCoins(anyLong(), anyInt());
    }
    
    @Test
    @WithMockUser(username = "admin_open_id")
    void updateUserRoomCards_AdminAccess_ShouldUpdateRoomCards() throws Exception {
        UserController.UpdateRoomCardsRequest request = new UserController.UpdateRoomCardsRequest();
        request.setRoomCards(50);
        
        User updatedUser = new User();
        updatedUser.setId(1L);
        updatedUser.setRoomCards(50);
        updatedUser.setNickname("Test User");
        updatedUser.setStatus(UserStatus.ACTIVE);
        updatedUser.setRole(UserRole.USER);
        
        when(userService.getCurrentUser(any())).thenReturn(adminUser);
        when(userService.updateRoomCards(1L, 50)).thenReturn(updatedUser);
        
        mockMvc.perform(put("/api/users/1/room-cards")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.roomCards").value(50));
        
        verify(userService).getCurrentUser(any());
        verify(userService).updateRoomCards(1L, 50);
    }
    
    @Test
    @WithMockUser(username = "test_open_id")
    void updateUserProfile_InvalidData_ShouldReturn400() throws Exception {
        UserController.UpdateUserProfileRequest request = new UserController.UpdateUserProfileRequest();
        request.setNickname("A".repeat(51)); // Exceeds max length
        
        mockMvc.perform(put("/api/users/profile")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }
    
    @Test
    @WithMockUser(username = "test_open_id")
    void updateUserCoins_NegativeValue_ShouldReturn400() throws Exception {
        UserController.UpdateCoinsRequest request = new UserController.UpdateCoinsRequest();
        request.setCoins(-100);
        
        mockMvc.perform(put("/api/users/1/coins")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }
    
    @Test
    @WithMockUser(username = "test_open_id")
    void updateUserRoomCards_NegativeValue_ShouldReturn400() throws Exception {
        UserController.UpdateRoomCardsRequest request = new UserController.UpdateRoomCardsRequest();
        request.setRoomCards(-10);
        
        mockMvc.perform(put("/api/users/1/room-cards")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }
}