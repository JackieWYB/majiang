package com.mahjong.service;

import com.mahjong.model.dto.AdminUserRequest;
import com.mahjong.model.dto.AdminUserResponse;
import com.mahjong.model.dto.SystemHealthResponse;
import com.mahjong.model.entity.AuditLog;
import com.mahjong.model.entity.Room;
import com.mahjong.model.entity.User;
import com.mahjong.model.enums.RoomStatus;
import com.mahjong.model.enums.UserStatus;
import com.mahjong.repository.AuditLogRepository;
import com.mahjong.repository.GameRecordRepository;
import com.mahjong.repository.RoomRepository;
import com.mahjong.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Service for administrative operations and monitoring
 */
@Service
@Transactional
public class AdminService {
    
    private static final Logger logger = LoggerFactory.getLogger(AdminService.class);
    
    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private RoomRepository roomRepository;
    
    @Autowired
    private GameRecordRepository gameRecordRepository;
    
    @Autowired
    private AuditLogRepository auditLogRepository;
    
    @Autowired
    private GameStateRedisService gameStateRedisService;
    
    @Autowired
    private WebSocketSessionService webSocketSessionService;
    
    /**
     * Ban a user
     */
    public AdminUserResponse banUser(Long userId, String reason, User admin, HttpServletRequest request) {
        logger.info("Admin {} attempting to ban user {}", admin.getId(), userId);
        
        Optional<User> userOpt = userRepository.findById(userId);
        if (userOpt.isEmpty()) {
            throw new IllegalArgumentException("User not found: " + userId);
        }
        
        User user = userOpt.get();
        if (user.isBanned()) {
            throw new IllegalArgumentException("User is already banned: " + userId);
        }
        
        // Prevent banning other admins unless super admin
        if (user.isAdmin() && !admin.isSuperAdmin()) {
            throw new IllegalArgumentException("Cannot ban admin users");
        }
        
        // Ban the user
        user.ban(reason, admin.getId());
        userRepository.save(user);
        
        // Disconnect user from all active sessions
        webSocketSessionService.disconnectUser(userId, "Account banned: " + reason);
        
        // Log the action
        logAdminAction(admin, "BAN_USER", "USER", userId.toString(), user.getNickname(), 
                      "Reason: " + reason, request);
        
        logger.info("User {} banned by admin {}", userId, admin.getId());
        return new AdminUserResponse(user);
    }
    
    /**
     * Unban a user
     */
    public AdminUserResponse unbanUser(Long userId, User admin, HttpServletRequest request) {
        logger.info("Admin {} attempting to unban user {}", admin.getId(), userId);
        
        Optional<User> userOpt = userRepository.findById(userId);
        if (userOpt.isEmpty()) {
            throw new IllegalArgumentException("User not found: " + userId);
        }
        
        User user = userOpt.get();
        if (!user.isBanned()) {
            throw new IllegalArgumentException("User is not banned: " + userId);
        }
        
        // Unban the user
        user.unban();
        userRepository.save(user);
        
        // Log the action
        logAdminAction(admin, "UNBAN_USER", "USER", userId.toString(), user.getNickname(), 
                      "User unbanned", request);
        
        logger.info("User {} unbanned by admin {}", userId, admin.getId());
        return new AdminUserResponse(user);
    }
    
    /**
     * Update user profile (admin only)
     */
    public AdminUserResponse updateUserProfile(Long userId, AdminUserRequest request, User admin, HttpServletRequest httpRequest) {
        logger.info("Admin {} updating profile for user {}", admin.getId(), userId);
        
        Optional<User> userOpt = userRepository.findById(userId);
        if (userOpt.isEmpty()) {
            throw new IllegalArgumentException("User not found: " + userId);
        }
        
        User user = userOpt.get();
        StringBuilder changes = new StringBuilder();
        
        // Update nickname if provided
        if (request.getNickname() != null && !request.getNickname().equals(user.getNickname())) {
            String oldNickname = user.getNickname();
            user.setNickname(request.getNickname());
            changes.append("Nickname: ").append(oldNickname).append(" -> ").append(request.getNickname()).append("; ");
        }
        
        // Update coins if provided
        if (request.getCoins() != null && !request.getCoins().equals(user.getCoins())) {
            Integer oldCoins = user.getCoins();
            user.setCoins(request.getCoins());
            changes.append("Coins: ").append(oldCoins).append(" -> ").append(request.getCoins()).append("; ");
        }
        
        // Update room cards if provided
        if (request.getRoomCards() != null && !request.getRoomCards().equals(user.getRoomCards())) {
            Integer oldCards = user.getRoomCards();
            user.setRoomCards(request.getRoomCards());
            changes.append("RoomCards: ").append(oldCards).append(" -> ").append(request.getRoomCards()).append("; ");
        }
        
        if (changes.length() > 0) {
            userRepository.save(user);
            
            // Log the action
            logAdminAction(admin, "UPDATE_USER", "USER", userId.toString(), user.getNickname(), 
                          changes.toString(), httpRequest);
            
            logger.info("User {} profile updated by admin {}: {}", userId, admin.getId(), changes);
        }
        
        return new AdminUserResponse(user);
    }
    
    /**
     * Force dissolve a room
     */
    public void forceDissolveRoom(String roomId, String reason, User admin, HttpServletRequest request) {
        logger.info("Admin {} attempting to force dissolve room {}", admin.getId(), roomId);
        
        Optional<Room> roomOpt = roomRepository.findById(roomId);
        if (roomOpt.isEmpty()) {
            throw new IllegalArgumentException("Room not found: " + roomId);
        }
        
        Room room = roomOpt.get();
        if (room.getStatus() == RoomStatus.DISSOLVED) {
            throw new IllegalArgumentException("Room is already dissolved: " + roomId);
        }
        
        // Update room status
        room.setStatus(RoomStatus.DISSOLVED);
        roomRepository.save(room);
        
        // Clear game state from Redis
        gameStateRedisService.clearGameState(roomId);
        
        // Notify all players in the room
        webSocketSessionService.notifyRoomPlayers(roomId, "Room dissolved by administrator: " + reason);
        
        // Log the action
        logAdminAction(admin, "DISSOLVE_ROOM", "ROOM", roomId, roomId, 
                      "Reason: " + reason, request);
        
        logger.info("Room {} force dissolved by admin {}", roomId, admin.getId());
    }
    
    /**
     * Get system health status
     */
    @Transactional(readOnly = true)
    public SystemHealthResponse getSystemHealth() {
        SystemHealthResponse health = new SystemHealthResponse("HEALTHY");
        
        try {
            // Get basic statistics
            health.setTotalUsers(userRepository.count());
            health.setActiveRooms(roomRepository.countByStatus(RoomStatus.PLAYING));
            health.setOnlineUsers(webSocketSessionService.getOnlineUserCount());
            health.setTotalGamesPlayed(gameRecordRepository.count());
            
            // Get today's games
            LocalDateTime startOfDay = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0);
            LocalDateTime endOfDay = startOfDay.plusDays(1);
            health.setGamesPlayedToday(gameRecordRepository.countByCreatedAtBetween(startOfDay, endOfDay));
            
            // Get system metrics
            Map<String, Object> metrics = new HashMap<>();
            metrics.put("jvm.memory.used", Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory());
            metrics.put("jvm.memory.max", Runtime.getRuntime().maxMemory());
            metrics.put("jvm.memory.free", Runtime.getRuntime().freeMemory());
            health.setSystemMetrics(metrics);
            
            // Check component status
            Map<String, String> componentStatus = new HashMap<>();
            componentStatus.put("database", checkDatabaseHealth());
            componentStatus.put("redis", checkRedisHealth());
            componentStatus.put("websocket", "UP");
            health.setComponentStatus(componentStatus);
            
            // Determine overall status
            boolean allHealthy = componentStatus.values().stream().allMatch("UP"::equals);
            health.setStatus(allHealthy ? "HEALTHY" : "DEGRADED");
            
        } catch (Exception e) {
            logger.error("Error getting system health", e);
            health.setStatus("UNHEALTHY");
        }
        
        return health;
    }
    
    /**
     * Get user list with pagination
     */
    @Transactional(readOnly = true)
    public Page<AdminUserResponse> getUsers(Pageable pageable) {
        return userRepository.findAll(pageable)
                .map(AdminUserResponse::new);
    }
    
    /**
     * Search users by nickname or openId
     */
    @Transactional(readOnly = true)
    public Page<AdminUserResponse> searchUsers(String query, Pageable pageable) {
        return userRepository.findByNicknameContainingIgnoreCaseOrOpenIdContaining(query, query, pageable)
                .map(AdminUserResponse::new);
    }
    
    /**
     * Get banned users
     */
    @Transactional(readOnly = true)
    public Page<AdminUserResponse> getBannedUsers(Pageable pageable) {
        return userRepository.findByStatus(UserStatus.BANNED, pageable)
                .map(AdminUserResponse::new);
    }
    
    /**
     * Get audit logs
     */
    @Transactional(readOnly = true)
    public Page<AuditLog> getAuditLogs(Pageable pageable) {
        return auditLogRepository.findAll(pageable);
    }
    
    /**
     * Get audit logs for specific admin
     */
    @Transactional(readOnly = true)
    public Page<AuditLog> getAuditLogsByAdmin(Long adminId, Pageable pageable) {
        return auditLogRepository.findByAdminIdOrderByCreatedAtDesc(adminId, pageable);
    }
    
    /**
     * Get audit logs for specific target
     */
    @Transactional(readOnly = true)
    public Page<AuditLog> getAuditLogsByTarget(String targetType, String targetId, Pageable pageable) {
        return auditLogRepository.findByTargetTypeAndTargetIdOrderByCreatedAtDesc(targetType, targetId, pageable);
    }
    
    /**
     * Detect suspicious activity
     */
    @Transactional(readOnly = true)
    public List<AuditLog> detectSuspiciousActivity(Long adminId, String targetType, String targetId, int hours) {
        LocalDateTime since = LocalDateTime.now().minusHours(hours);
        return auditLogRepository.findSuspiciousActivity(adminId, targetType, targetId, since);
    }
    
    // Private helper methods
    
    private void logAdminAction(User admin, String action, String targetType, String targetId, 
                               String targetName, String details, HttpServletRequest request) {
        AuditLog log = new AuditLog(admin.getId(), admin.getNickname(), action, targetType, targetId, targetName, details);
        
        if (request != null) {
            log.setIpAddress(getClientIpAddress(request));
            log.setUserAgent(request.getHeader("User-Agent"));
        }
        
        auditLogRepository.save(log);
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
    
    private String checkDatabaseHealth() {
        try {
            userRepository.count();
            return "UP";
        } catch (Exception e) {
            logger.error("Database health check failed", e);
            return "DOWN";
        }
    }
    
    private String checkRedisHealth() {
        try {
            gameStateRedisService.ping();
            return "UP";
        } catch (Exception e) {
            logger.error("Redis health check failed", e);
            return "DOWN";
        }
    }
}