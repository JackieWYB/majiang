package com.mahjong.service;

import com.mahjong.model.dto.AdminUserRequest;
import com.mahjong.model.dto.AdminUserResponse;
import com.mahjong.model.dto.SystemHealthResponse;
import com.mahjong.model.entity.AuditLog;
import com.mahjong.model.entity.Room;
import com.mahjong.model.entity.User;
import com.mahjong.model.enums.RoomStatus;
import com.mahjong.model.enums.UserRole;
import com.mahjong.model.enums.UserStatus;
import com.mahjong.repository.AuditLogRepository;
import com.mahjong.repository.GameRecordRepository;
import com.mahjong.repository.RoomRepository;
import com.mahjong.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminServiceTest {
    
    @Mock
    private UserRepository userRepository;
    
    @Mock
    private RoomRepository roomRepository;
    
    @Mock
    private GameRecordRepository gameRecordRepository;
    
    @Mock
    private AuditLogRepository auditLogRepository;
    
    @Mock
    private GameStateRedisService gameStateRedisService;
    
    @Mock
    private WebSocketSessionService webSocketSessionService;
    
    @Mock
    private HttpServletRequest httpRequest;
    
    @InjectMocks
    private AdminService adminService;
    
    private User admin;
    private User regularUser;
    private User targetUser;
    
    @BeforeEach
    void setUp() {
        admin = new User();
        admin.setId(1L);
        admin.setNickname("Admin User");
        admin.setRole(UserRole.ADMIN);
        admin.setStatus(UserStatus.ACTIVE);
        
        regularUser = new User();
        regularUser.setId(2L);
        regularUser.setNickname("Regular User");
        regularUser.setRole(UserRole.USER);
        regularUser.setStatus(UserStatus.ACTIVE);
        
        targetUser = new User();
        targetUser.setId(3L);
        targetUser.setNickname("Target User");
        targetUser.setRole(UserRole.USER);
        targetUser.setStatus(UserStatus.ACTIVE);
        targetUser.setCoins(1000);
        targetUser.setRoomCards(10);
    }
    
    @Test
    void banUser_Success() {
        // Given
        when(userRepository.findById(3L)).thenReturn(Optional.of(targetUser));
        when(userRepository.save(any(User.class))).thenReturn(targetUser);
        when(httpRequest.getRemoteAddr()).thenReturn("127.0.0.1");
        when(httpRequest.getHeader("User-Agent")).thenReturn("Test Agent");
        when(httpRequest.getHeader("X-Forwarded-For")).thenReturn(null);
        when(httpRequest.getHeader("X-Real-IP")).thenReturn(null);
        
        AdminUserRequest request = new AdminUserRequest();
        request.setUserId(3L);
        request.setReason("Violation of terms");
        
        // When
        AdminUserResponse response = adminService.banUser(3L, "Violation of terms", admin, httpRequest);
        
        // Then
        assertNotNull(response);
        assertEquals(3L, response.getId());
        assertEquals(UserStatus.BANNED, response.getStatus());
        assertEquals("Violation of terms", response.getBanReason());
        assertNotNull(response.getBannedAt());
        assertEquals(1L, response.getBannedBy());
        
        verify(userRepository).save(targetUser);
        verify(webSocketSessionService).disconnectUser(3L, "Account banned: Violation of terms");
        verify(auditLogRepository).save(any(AuditLog.class));
    }
    
    @Test
    void banUser_UserNotFound() {
        // Given
        when(userRepository.findById(999L)).thenReturn(Optional.empty());
        
        // When & Then
        assertThrows(IllegalArgumentException.class, () -> 
            adminService.banUser(999L, "Test reason", admin, httpRequest));
    }
    
    @Test
    void banUser_AlreadyBanned() {
        // Given
        targetUser.setStatus(UserStatus.BANNED);
        when(userRepository.findById(3L)).thenReturn(Optional.of(targetUser));
        
        // When & Then
        assertThrows(IllegalArgumentException.class, () -> 
            adminService.banUser(3L, "Test reason", admin, httpRequest));
    }
    
    @Test
    void banUser_CannotBanAdmin() {
        // Given
        User adminTarget = new User();
        adminTarget.setId(4L);
        adminTarget.setRole(UserRole.ADMIN);
        adminTarget.setStatus(UserStatus.ACTIVE);
        
        when(userRepository.findById(4L)).thenReturn(Optional.of(adminTarget));
        
        // When & Then
        assertThrows(IllegalArgumentException.class, () -> 
            adminService.banUser(4L, "Test reason", admin, httpRequest));
    }
    
    @Test
    void unbanUser_Success() {
        // Given
        targetUser.setStatus(UserStatus.BANNED);
        targetUser.setBanReason("Previous violation");
        targetUser.setBannedAt(LocalDateTime.now());
        targetUser.setBannedBy(1L);
        
        when(userRepository.findById(3L)).thenReturn(Optional.of(targetUser));
        when(userRepository.save(any(User.class))).thenReturn(targetUser);
        when(httpRequest.getRemoteAddr()).thenReturn("127.0.0.1");
        when(httpRequest.getHeader("X-Forwarded-For")).thenReturn(null);
        when(httpRequest.getHeader("X-Real-IP")).thenReturn(null);
        when(httpRequest.getHeader("User-Agent")).thenReturn("Test Agent");
        
        // When
        AdminUserResponse response = adminService.unbanUser(3L, admin, httpRequest);
        
        // Then
        assertNotNull(response);
        assertEquals(3L, response.getId());
        assertEquals(UserStatus.ACTIVE, response.getStatus());
        assertNull(response.getBanReason());
        assertNull(response.getBannedAt());
        assertNull(response.getBannedBy());
        
        verify(userRepository).save(targetUser);
        verify(auditLogRepository).save(any(AuditLog.class));
    }
    
    @Test
    void unbanUser_NotBanned() {
        // Given
        when(userRepository.findById(3L)).thenReturn(Optional.of(targetUser));
        
        // When & Then
        assertThrows(IllegalArgumentException.class, () -> 
            adminService.unbanUser(3L, admin, httpRequest));
    }
    
    @Test
    void updateUserProfile_Success() {
        // Given
        when(userRepository.findById(3L)).thenReturn(Optional.of(targetUser));
        when(userRepository.save(any(User.class))).thenReturn(targetUser);
        when(httpRequest.getRemoteAddr()).thenReturn("127.0.0.1");
        when(httpRequest.getHeader("X-Forwarded-For")).thenReturn(null);
        when(httpRequest.getHeader("X-Real-IP")).thenReturn(null);
        when(httpRequest.getHeader("User-Agent")).thenReturn("Test Agent");
        
        AdminUserRequest request = new AdminUserRequest();
        request.setUserId(3L);
        request.setNickname("Updated Name");
        request.setCoins(2000);
        request.setRoomCards(20);
        
        // When
        AdminUserResponse response = adminService.updateUserProfile(3L, request, admin, httpRequest);
        
        // Then
        assertNotNull(response);
        assertEquals("Updated Name", response.getNickname());
        assertEquals(2000, response.getCoins());
        assertEquals(20, response.getRoomCards());
        
        verify(userRepository).save(targetUser);
        verify(auditLogRepository).save(any(AuditLog.class));
    }
    
    @Test
    void forceDissolveRoom_Success() {
        // Given
        Room room = new Room();
        room.setId("123456");
        room.setStatus(RoomStatus.PLAYING);
        
        when(roomRepository.findById("123456")).thenReturn(Optional.of(room));
        when(roomRepository.save(any(Room.class))).thenReturn(room);
        when(httpRequest.getRemoteAddr()).thenReturn("127.0.0.1");
        when(httpRequest.getHeader("X-Forwarded-For")).thenReturn(null);
        when(httpRequest.getHeader("X-Real-IP")).thenReturn(null);
        when(httpRequest.getHeader("User-Agent")).thenReturn("Test Agent");
        
        // When
        adminService.forceDissolveRoom("123456", "Admin intervention", admin, httpRequest);
        
        // Then
        assertEquals(RoomStatus.DISSOLVED, room.getStatus());
        verify(roomRepository).save(room);
        verify(gameStateRedisService).clearGameState("123456");
        verify(webSocketSessionService).notifyRoomPlayers("123456", "Room dissolved by administrator: Admin intervention");
        verify(auditLogRepository).save(any(AuditLog.class));
    }
    
    @Test
    void forceDissolveRoom_RoomNotFound() {
        // Given
        when(roomRepository.findById("999999")).thenReturn(Optional.empty());
        
        // When & Then
        assertThrows(IllegalArgumentException.class, () -> 
            adminService.forceDissolveRoom("999999", "Test reason", admin, httpRequest));
    }
    
    @Test
    void forceDissolveRoom_AlreadyDissolved() {
        // Given
        Room room = new Room();
        room.setId("123456");
        room.setStatus(RoomStatus.DISSOLVED);
        
        when(roomRepository.findById("123456")).thenReturn(Optional.of(room));
        
        // When & Then
        assertThrows(IllegalArgumentException.class, () -> 
            adminService.forceDissolveRoom("123456", "Test reason", admin, httpRequest));
    }
    
    @Test
    void getSystemHealth_Success() {
        // Given
        when(userRepository.count()).thenReturn(1000L);
        when(roomRepository.countByStatus(RoomStatus.PLAYING)).thenReturn(50L);
        when(webSocketSessionService.getOnlineUserCount()).thenReturn(200L);
        when(gameRecordRepository.count()).thenReturn(5000L);
        when(gameRecordRepository.countByCreatedAtBetween(any(), any())).thenReturn(100L);
        
        // When
        SystemHealthResponse health = adminService.getSystemHealth();
        
        // Then
        assertNotNull(health);
        assertEquals("HEALTHY", health.getStatus());
        assertEquals(1000L, health.getTotalUsers());
        assertEquals(50L, health.getActiveRooms());
        assertEquals(200L, health.getOnlineUsers());
        assertEquals(5000L, health.getTotalGamesPlayed());
        assertEquals(100L, health.getGamesPlayedToday());
        assertNotNull(health.getSystemMetrics());
        assertNotNull(health.getComponentStatus());
    }
    
    @Test
    void getUsers_Success() {
        // Given
        List<User> users = List.of(targetUser, regularUser);
        Page<User> userPage = new PageImpl<>(users);
        when(userRepository.findAll(any(Pageable.class))).thenReturn(userPage);
        
        // When
        Page<AdminUserResponse> result = adminService.getUsers(Pageable.unpaged());
        
        // Then
        assertNotNull(result);
        assertEquals(2, result.getContent().size());
        assertEquals("Target User", result.getContent().get(0).getNickname());
        assertEquals("Regular User", result.getContent().get(1).getNickname());
    }
    
    @Test
    void searchUsers_Success() {
        // Given
        List<User> users = List.of(targetUser);
        Page<User> userPage = new PageImpl<>(users);
        when(userRepository.findByNicknameContainingIgnoreCaseOrOpenIdContaining(eq("Target"), eq("Target"), any(Pageable.class)))
                .thenReturn(userPage);
        
        // When
        Page<AdminUserResponse> result = adminService.searchUsers("Target", Pageable.unpaged());
        
        // Then
        assertNotNull(result);
        assertEquals(1, result.getContent().size());
        assertEquals("Target User", result.getContent().get(0).getNickname());
    }
    
    @Test
    void getBannedUsers_Success() {
        // Given
        targetUser.setStatus(UserStatus.BANNED);
        List<User> bannedUsers = List.of(targetUser);
        Page<User> userPage = new PageImpl<>(bannedUsers);
        when(userRepository.findByStatus(eq(UserStatus.BANNED), any(Pageable.class))).thenReturn(userPage);
        
        // When
        Page<AdminUserResponse> result = adminService.getBannedUsers(Pageable.unpaged());
        
        // Then
        assertNotNull(result);
        assertEquals(1, result.getContent().size());
        assertEquals(UserStatus.BANNED, result.getContent().get(0).getStatus());
    }
    
    @Test
    void getAuditLogs_Success() {
        // Given
        AuditLog log = new AuditLog(1L, "Admin", "BAN_USER", "USER", "3", "Target User", "Test ban");
        List<AuditLog> logs = List.of(log);
        Page<AuditLog> logPage = new PageImpl<>(logs);
        when(auditLogRepository.findAll(any(Pageable.class))).thenReturn(logPage);
        
        // When
        Page<AuditLog> result = adminService.getAuditLogs(Pageable.unpaged());
        
        // Then
        assertNotNull(result);
        assertEquals(1, result.getContent().size());
        assertEquals("BAN_USER", result.getContent().get(0).getAction());
    }
    
    @Test
    void detectSuspiciousActivity_Success() {
        // Given
        AuditLog log1 = new AuditLog(1L, "Admin", "BAN_USER", "USER", "3", "Target User", "First ban");
        AuditLog log2 = new AuditLog(1L, "Admin", "UNBAN_USER", "USER", "3", "Target User", "Unban");
        AuditLog log3 = new AuditLog(1L, "Admin", "BAN_USER", "USER", "3", "Target User", "Second ban");
        List<AuditLog> suspiciousLogs = List.of(log1, log2, log3);
        
        when(auditLogRepository.findSuspiciousActivity(eq(1L), eq("USER"), eq("3"), any(LocalDateTime.class)))
                .thenReturn(suspiciousLogs);
        
        // When
        List<AuditLog> result = adminService.detectSuspiciousActivity(1L, "USER", "3", 24);
        
        // Then
        assertNotNull(result);
        assertEquals(3, result.size());
        assertTrue(result.stream().allMatch(log -> log.getAdminId().equals(1L)));
        assertTrue(result.stream().allMatch(log -> log.getTargetId().equals("3")));
    }
}