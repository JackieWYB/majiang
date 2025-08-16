package com.mahjong.controller;

import com.mahjong.model.dto.AdminUserRequest;
import com.mahjong.model.dto.AdminUserResponse;
import com.mahjong.model.dto.ApiResponse;
import com.mahjong.model.dto.SystemHealthResponse;
import com.mahjong.model.entity.AuditLog;
import com.mahjong.model.entity.User;
import com.mahjong.service.AdminService;
import com.mahjong.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Controller for administrative operations
 */
@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {
    
    private static final Logger logger = LoggerFactory.getLogger(AdminController.class);
    
    @Autowired
    private AdminService adminService;
    
    @Autowired
    private UserService userService;
    
    /**
     * Get system health status
     */
    @GetMapping("/health")
    public ResponseEntity<ApiResponse<SystemHealthResponse>> getSystemHealth() {
        try {
            SystemHealthResponse health = adminService.getSystemHealth();
            return ResponseEntity.ok(ApiResponse.success(health));
        } catch (Exception e) {
            logger.error("Error getting system health", e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Failed to get system health: " + e.getMessage()));
        }
    }
    
    /**
     * Get user list with pagination
     */
    @GetMapping("/users")
    public ResponseEntity<ApiResponse<Page<AdminUserResponse>>> getUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {
        
        try {
            Sort sort = sortDir.equalsIgnoreCase("desc") ? 
                    Sort.by(sortBy).descending() : Sort.by(sortBy).ascending();
            Pageable pageable = PageRequest.of(page, size, sort);
            
            Page<AdminUserResponse> users = adminService.getUsers(pageable);
            return ResponseEntity.ok(ApiResponse.success(users));
        } catch (Exception e) {
            logger.error("Error getting users", e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Failed to get users: " + e.getMessage()));
        }
    }
    
    /**
     * Search users by nickname or openId
     */
    @GetMapping("/users/search")
    public ResponseEntity<ApiResponse<Page<AdminUserResponse>>> searchUsers(
            @RequestParam String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        
        try {
            Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
            Page<AdminUserResponse> users = adminService.searchUsers(query, pageable);
            return ResponseEntity.ok(ApiResponse.success(users));
        } catch (Exception e) {
            logger.error("Error searching users", e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Failed to search users: " + e.getMessage()));
        }
    }
    
    /**
     * Get banned users
     */
    @GetMapping("/users/banned")
    public ResponseEntity<ApiResponse<Page<AdminUserResponse>>> getBannedUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        
        try {
            Pageable pageable = PageRequest.of(page, size, Sort.by("bannedAt").descending());
            Page<AdminUserResponse> users = adminService.getBannedUsers(pageable);
            return ResponseEntity.ok(ApiResponse.success(users));
        } catch (Exception e) {
            logger.error("Error getting banned users", e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Failed to get banned users: " + e.getMessage()));
        }
    }
    
    /**
     * Ban a user
     */
    @PostMapping("/users/{userId}/ban")
    public ResponseEntity<ApiResponse<AdminUserResponse>> banUser(
            @PathVariable Long userId,
            @Valid @RequestBody AdminUserRequest request,
            Authentication authentication,
            HttpServletRequest httpRequest) {
        
        try {
            User admin = userService.getCurrentUser(authentication);
            if (!admin.isAdmin()) {
                return ResponseEntity.status(403)
                        .body(ApiResponse.error("Access denied: Admin role required"));
            }
            
            AdminUserResponse response = adminService.banUser(userId, request.getReason(), admin, httpRequest);
            return ResponseEntity.ok(ApiResponse.success(response));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            logger.error("Error banning user", e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Failed to ban user: " + e.getMessage()));
        }
    }
    
    /**
     * Unban a user
     */
    @PostMapping("/users/{userId}/unban")
    public ResponseEntity<ApiResponse<AdminUserResponse>> unbanUser(
            @PathVariable Long userId,
            Authentication authentication,
            HttpServletRequest httpRequest) {
        
        try {
            User admin = userService.getCurrentUser(authentication);
            if (!admin.isAdmin()) {
                return ResponseEntity.status(403)
                        .body(ApiResponse.error("Access denied: Admin role required"));
            }
            
            AdminUserResponse response = adminService.unbanUser(userId, admin, httpRequest);
            return ResponseEntity.ok(ApiResponse.success(response));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            logger.error("Error unbanning user", e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Failed to unban user: " + e.getMessage()));
        }
    }
    
    /**
     * Update user profile (admin only)
     */
    @PutMapping("/users/{userId}")
    public ResponseEntity<ApiResponse<AdminUserResponse>> updateUserProfile(
            @PathVariable Long userId,
            @Valid @RequestBody AdminUserRequest request,
            Authentication authentication,
            HttpServletRequest httpRequest) {
        
        try {
            User admin = userService.getCurrentUser(authentication);
            if (!admin.isAdmin()) {
                return ResponseEntity.status(403)
                        .body(ApiResponse.error("Access denied: Admin role required"));
            }
            
            AdminUserResponse response = adminService.updateUserProfile(userId, request, admin, httpRequest);
            return ResponseEntity.ok(ApiResponse.success(response));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            logger.error("Error updating user profile", e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Failed to update user profile: " + e.getMessage()));
        }
    }
    
    /**
     * Force dissolve a room
     */
    @PostMapping("/rooms/{roomId}/dissolve")
    public ResponseEntity<ApiResponse<String>> forceDissolveRoom(
            @PathVariable String roomId,
            @RequestParam(required = false, defaultValue = "Administrative action") String reason,
            Authentication authentication,
            HttpServletRequest httpRequest) {
        
        try {
            User admin = userService.getCurrentUser(authentication);
            if (!admin.isAdmin()) {
                return ResponseEntity.status(403)
                        .body(ApiResponse.error("Access denied: Admin role required"));
            }
            
            adminService.forceDissolveRoom(roomId, reason, admin, httpRequest);
            return ResponseEntity.ok(ApiResponse.success("Room dissolved successfully"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            logger.error("Error dissolving room", e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Failed to dissolve room: " + e.getMessage()));
        }
    }
    
    /**
     * Get audit logs
     */
    @GetMapping("/audit-logs")
    public ResponseEntity<ApiResponse<Page<AuditLog>>> getAuditLogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(required = false) Long adminId,
            @RequestParam(required = false) String targetType,
            @RequestParam(required = false) String targetId) {
        
        try {
            Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
            Page<AuditLog> logs;
            
            if (adminId != null) {
                logs = adminService.getAuditLogsByAdmin(adminId, pageable);
            } else if (targetType != null && targetId != null) {
                logs = adminService.getAuditLogsByTarget(targetType, targetId, pageable);
            } else {
                logs = adminService.getAuditLogs(pageable);
            }
            
            return ResponseEntity.ok(ApiResponse.success(logs));
        } catch (Exception e) {
            logger.error("Error getting audit logs", e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Failed to get audit logs: " + e.getMessage()));
        }
    }
    
    /**
     * Detect suspicious activity
     */
    @GetMapping("/audit-logs/suspicious")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<List<AuditLog>>> detectSuspiciousActivity(
            @RequestParam Long adminId,
            @RequestParam String targetType,
            @RequestParam String targetId,
            @RequestParam(defaultValue = "24") int hours) {
        
        try {
            List<AuditLog> suspiciousLogs = adminService.detectSuspiciousActivity(adminId, targetType, targetId, hours);
            return ResponseEntity.ok(ApiResponse.success(suspiciousLogs));
        } catch (Exception e) {
            logger.error("Error detecting suspicious activity", e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Failed to detect suspicious activity: " + e.getMessage()));
        }
    }
}