package com.mahjong.controller;

import com.mahjong.model.config.RoomConfig;
import com.mahjong.model.dto.ApiResponse;
import com.mahjong.model.entity.RoomRule;
import com.mahjong.model.entity.User;
import com.mahjong.repository.RoomRepository;
import com.mahjong.service.UserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import jakarta.transaction.Transactional;
import java.util.List;
import java.util.Optional;

/**
 * REST controller for game rule configuration management
 */
@RestController
@RequestMapping("/api/config")
public class ConfigController {
    
    private static final Logger logger = LoggerFactory.getLogger(ConfigController.class);
    
    @PersistenceContext
    private EntityManager entityManager;
    
    @Autowired
    private UserService userService;
    
    /**
     * Get all active room rules with pagination
     */
    @GetMapping("/rules")
    public ResponseEntity<ApiResponse<Page<RoomRuleResponse>>> getRoomRules(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "name") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir) {
        
        try {
            Sort sort = sortDir.equalsIgnoreCase("desc") ? 
                    Sort.by(sortBy).descending() : Sort.by(sortBy).ascending();
            Pageable pageable = PageRequest.of(page, size, sort);
            
            TypedQuery<RoomRule> query = entityManager.createQuery(
                    "SELECT r FROM RoomRule r WHERE r.isActive = true", RoomRule.class);
            
            List<RoomRule> allRules = query.getResultList();
            
            // Manual pagination since we're using EntityManager
            int start = (int) pageable.getOffset();
            int end = Math.min((start + pageable.getPageSize()), allRules.size());
            List<RoomRule> pageContent = allRules.subList(start, end);
            
            Page<RoomRuleResponse> response = new org.springframework.data.domain.PageImpl<>(
                    pageContent.stream().map(RoomRuleResponse::new).toList(),
                    pageable,
                    allRules.size()
            );
            
            return ResponseEntity.ok(ApiResponse.success(response));
            
        } catch (Exception e) {
            logger.error("Error getting room rules", e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Failed to get room rules"));
        }
    }
    
    /**
     * Get default room rules
     */
    @GetMapping("/rules/default")
    public ResponseEntity<ApiResponse<List<RoomRuleResponse>>> getDefaultRoomRules() {
        try {
            TypedQuery<RoomRule> query = entityManager.createQuery(
                    "SELECT r FROM RoomRule r WHERE r.isDefault = true AND r.isActive = true ORDER BY r.name", 
                    RoomRule.class);
            
            List<RoomRule> defaultRules = query.getResultList();
            List<RoomRuleResponse> response = defaultRules.stream()
                    .map(RoomRuleResponse::new)
                    .toList();
            
            return ResponseEntity.ok(ApiResponse.success(response));
            
        } catch (Exception e) {
            logger.error("Error getting default room rules", e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Failed to get default room rules"));
        }
    }
    
    /**
     * Get specific room rule by ID
     */
    @GetMapping("/rules/{ruleId}")
    public ResponseEntity<ApiResponse<RoomRuleResponse>> getRoomRule(@PathVariable Long ruleId) {
        try {
            RoomRule rule = entityManager.find(RoomRule.class, ruleId);
            
            if (rule == null || !rule.getIsActive()) {
                return ResponseEntity.notFound().build();
            }
            
            RoomRuleResponse response = new RoomRuleResponse(rule);
            return ResponseEntity.ok(ApiResponse.success(response));
            
        } catch (Exception e) {
            logger.error("Error getting room rule: {}", ruleId, e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Failed to get room rule"));
        }
    }
    
    /**
     * Create new room rule (admin only)
     */
    @PostMapping("/rules")
    @Transactional
    public ResponseEntity<ApiResponse<RoomRuleResponse>> createRoomRule(
            @Valid @RequestBody CreateRoomRuleRequest request,
            Authentication authentication) {
        
        try {
            User currentUser = userService.getCurrentUser(authentication);
            
            if (!currentUser.isAdmin()) {
                return ResponseEntity.status(403)
                        .body(ApiResponse.error("Access denied: Admin role required"));
            }
            
            // Validate configuration
            if (!isValidRoomConfig(request.getConfig())) {
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error("Invalid room configuration"));
            }
            
            RoomRule rule = new RoomRule();
            rule.setName(request.getName());
            rule.setDescription(request.getDescription());
            rule.setConfig(request.getConfig());
            rule.setIsDefault(request.getIsDefault());
            rule.setIsActive(true);
            
            entityManager.persist(rule);
            entityManager.flush();
            
            RoomRuleResponse response = new RoomRuleResponse(rule);
            
            logger.info("Created new room rule: {} by admin: {}", rule.getName(), currentUser.getId());
            
            return ResponseEntity.ok(ApiResponse.success(response));
            
        } catch (Exception e) {
            logger.error("Error creating room rule", e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Failed to create room rule"));
        }
    }
    
    /**
     * Update room rule (admin only)
     */
    @PutMapping("/rules/{ruleId}")
    @Transactional
    public ResponseEntity<ApiResponse<RoomRuleResponse>> updateRoomRule(
            @PathVariable Long ruleId,
            @Valid @RequestBody UpdateRoomRuleRequest request,
            Authentication authentication) {
        
        try {
            User currentUser = userService.getCurrentUser(authentication);
            
            if (!currentUser.isAdmin()) {
                return ResponseEntity.status(403)
                        .body(ApiResponse.error("Access denied: Admin role required"));
            }
            
            RoomRule rule = entityManager.find(RoomRule.class, ruleId);
            
            if (rule == null) {
                return ResponseEntity.notFound().build();
            }
            
            // Update fields if provided
            if (request.getName() != null) {
                rule.setName(request.getName());
            }
            if (request.getDescription() != null) {
                rule.setDescription(request.getDescription());
            }
            if (request.getConfig() != null) {
                if (!isValidRoomConfig(request.getConfig())) {
                    return ResponseEntity.badRequest()
                            .body(ApiResponse.error("Invalid room configuration"));
                }
                rule.setConfig(request.getConfig());
            }
            if (request.getIsDefault() != null) {
                rule.setIsDefault(request.getIsDefault());
            }
            if (request.getIsActive() != null) {
                rule.setIsActive(request.getIsActive());
            }
            
            entityManager.merge(rule);
            entityManager.flush();
            
            RoomRuleResponse response = new RoomRuleResponse(rule);
            
            logger.info("Updated room rule: {} by admin: {}", rule.getName(), currentUser.getId());
            
            return ResponseEntity.ok(ApiResponse.success(response));
            
        } catch (Exception e) {
            logger.error("Error updating room rule: {}", ruleId, e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Failed to update room rule"));
        }
    }
    
    /**
     * Delete room rule (admin only)
     */
    @DeleteMapping("/rules/{ruleId}")
    @Transactional
    public ResponseEntity<ApiResponse<Void>> deleteRoomRule(
            @PathVariable Long ruleId,
            Authentication authentication) {
        
        try {
            User currentUser = userService.getCurrentUser(authentication);
            
            if (!currentUser.isAdmin()) {
                return ResponseEntity.status(403)
                        .body(ApiResponse.error("Access denied: Admin role required"));
            }
            
            RoomRule rule = entityManager.find(RoomRule.class, ruleId);
            
            if (rule == null) {
                return ResponseEntity.notFound().build();
            }
            
            // Check if rule is being used by any active rooms
            TypedQuery<Long> query = entityManager.createQuery(
                    "SELECT COUNT(r) FROM Room r WHERE r.ruleId = :ruleId AND r.status != 'DISSOLVED'", 
                    Long.class);
            query.setParameter("ruleId", ruleId);
            Long activeRoomsCount = query.getSingleResult();
            
            if (activeRoomsCount > 0) {
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error("Cannot delete rule that is being used by active rooms"));
            }
            
            // Soft delete by setting isActive to false
            rule.setIsActive(false);
            entityManager.merge(rule);
            entityManager.flush();
            
            logger.info("Deleted room rule: {} by admin: {}", rule.getName(), currentUser.getId());
            
            return ResponseEntity.ok(ApiResponse.success(null));
            
        } catch (Exception e) {
            logger.error("Error deleting room rule: {}", ruleId, e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Failed to delete room rule"));
        }
    }
    
    /**
     * Get room configuration template
     */
    @GetMapping("/template")
    public ResponseEntity<ApiResponse<RoomConfig>> getConfigTemplate() {
        try {
            RoomConfig template = new RoomConfig();
            return ResponseEntity.ok(ApiResponse.success(template));
            
        } catch (Exception e) {
            logger.error("Error getting config template", e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Failed to get config template"));
        }
    }
    
    /**
     * Validate room configuration
     */
    @PostMapping("/validate")
    public ResponseEntity<ApiResponse<ConfigValidationResponse>> validateConfig(
            @Valid @RequestBody RoomConfig config) {
        
        try {
            ConfigValidationResponse response = new ConfigValidationResponse();
            response.setValid(isValidRoomConfig(config));
            
            if (!response.isValid()) {
                response.setErrors(getConfigValidationErrors(config));
            }
            
            return ResponseEntity.ok(ApiResponse.success(response));
            
        } catch (Exception e) {
            logger.error("Error validating config", e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Failed to validate config"));
        }
    }
    
    /**
     * Validate room configuration
     */
    private boolean isValidRoomConfig(RoomConfig config) {
        if (config == null) return false;
        
        // Basic validation
        if (config.getPlayers() == null || config.getPlayers() != 3) return false;
        if (config.getTiles() == null || (!config.getTiles().equals("WAN_ONLY") && !config.getTiles().equals("ALL_SUITS"))) return false;
        if (config.getAllowPeng() == null) return false;
        if (config.getAllowGang() == null) return false;
        if (config.getAllowChi() == null) return false;
        if (config.getReplay() == null) return false;
        
        // Validate nested configurations
        if (config.getHuTypes() == null) return false;
        if (config.getScore() == null) return false;
        if (config.getTurn() == null) return false;
        if (config.getDealer() == null) return false;
        if (config.getDismiss() == null) return false;
        
        return true;
    }
    
    /**
     * Get configuration validation errors
     */
    private List<String> getConfigValidationErrors(RoomConfig config) {
        List<String> errors = new java.util.ArrayList<>();
        
        if (config == null) {
            errors.add("Configuration cannot be null");
            return errors;
        }
        
        if (config.getPlayers() == null || config.getPlayers() != 3) {
            errors.add("Players must be exactly 3");
        }
        
        if (config.getTiles() == null || (!config.getTiles().equals("WAN_ONLY") && !config.getTiles().equals("ALL_SUITS"))) {
            errors.add("Tiles must be either 'WAN_ONLY' or 'ALL_SUITS'");
        }
        
        if (config.getAllowPeng() == null) {
            errors.add("AllowPeng must be specified");
        }
        
        if (config.getAllowGang() == null) {
            errors.add("AllowGang must be specified");
        }
        
        if (config.getAllowChi() == null) {
            errors.add("AllowChi must be specified");
        }
        
        if (config.getReplay() == null) {
            errors.add("Replay must be specified");
        }
        
        if (config.getHuTypes() == null) {
            errors.add("HuTypes configuration is required");
        }
        
        if (config.getScore() == null) {
            errors.add("Score configuration is required");
        }
        
        if (config.getTurn() == null) {
            errors.add("Turn configuration is required");
        }
        
        if (config.getDealer() == null) {
            errors.add("Dealer configuration is required");
        }
        
        if (config.getDismiss() == null) {
            errors.add("Dismiss configuration is required");
        }
        
        return errors;
    }
    
    // Request/Response DTOs
    
    public static class CreateRoomRuleRequest {
        @NotBlank(message = "Name is required")
        @Size(max = 100, message = "Name must not exceed 100 characters")
        private String name;
        
        @Size(max = 500, message = "Description must not exceed 500 characters")
        private String description;
        
        @Valid
        private RoomConfig config;
        
        private Boolean isDefault = false;
        
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public RoomConfig getConfig() { return config; }
        public void setConfig(RoomConfig config) { this.config = config; }
        public Boolean getIsDefault() { return isDefault; }
        public void setIsDefault(Boolean isDefault) { this.isDefault = isDefault; }
    }
    
    public static class UpdateRoomRuleRequest {
        @Size(max = 100, message = "Name must not exceed 100 characters")
        private String name;
        
        @Size(max = 500, message = "Description must not exceed 500 characters")
        private String description;
        
        @Valid
        private RoomConfig config;
        
        private Boolean isDefault;
        private Boolean isActive;
        
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public RoomConfig getConfig() { return config; }
        public void setConfig(RoomConfig config) { this.config = config; }
        public Boolean getIsDefault() { return isDefault; }
        public void setIsDefault(Boolean isDefault) { this.isDefault = isDefault; }
        public Boolean getIsActive() { return isActive; }
        public void setIsActive(Boolean isActive) { this.isActive = isActive; }
    }
    
    public static class RoomRuleResponse {
        private final Long id;
        private final String name;
        private final String description;
        private final RoomConfig config;
        private final Boolean isDefault;
        private final Boolean isActive;
        
        public RoomRuleResponse(RoomRule rule) {
            this.id = rule.getId();
            this.name = rule.getName();
            this.description = rule.getDescription();
            this.config = rule.getConfig();
            this.isDefault = rule.getIsDefault();
            this.isActive = rule.getIsActive();
        }
        
        public Long getId() { return id; }
        public String getName() { return name; }
        public String getDescription() { return description; }
        public RoomConfig getConfig() { return config; }
        public Boolean getIsDefault() { return isDefault; }
        public Boolean getIsActive() { return isActive; }
    }
    
    public static class ConfigValidationResponse {
        private boolean valid;
        private List<String> errors;
        
        public boolean isValid() { return valid; }
        public void setValid(boolean valid) { this.valid = valid; }
        public List<String> getErrors() { return errors; }
        public void setErrors(List<String> errors) { this.errors = errors; }
    }
}