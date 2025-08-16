package com.mahjong.service;

import org.owasp.encoder.Encode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.regex.Pattern;

/**
 * Service for input validation and sanitization
 */
@Service
public class InputValidationService {

    private static final Logger logger = LoggerFactory.getLogger(InputValidationService.class);

    // Regex patterns for validation
    private static final Pattern ROOM_ID_PATTERN = Pattern.compile("^[0-9]{6}$");
    private static final Pattern USER_ID_PATTERN = Pattern.compile("^[a-zA-Z0-9_-]{1,50}$");
    private static final Pattern NICKNAME_PATTERN = Pattern.compile("^[\\u4e00-\\u9fa5a-zA-Z0-9_\\s]{1,20}$");
    private static final Pattern TILE_PATTERN = Pattern.compile("^[1-9][WTC]$");
    private static final Pattern OPENID_PATTERN = Pattern.compile("^[a-zA-Z0-9_-]{28}$");
    
    // Suspicious patterns
    private static final Pattern SQL_INJECTION_PATTERN = Pattern.compile("(?i).*(union|select|insert|update|delete|drop|create|alter|exec|script).*");
    private static final Pattern XSS_PATTERN = Pattern.compile("(?i).*(<script|javascript:|on\\w+\\s*=|<iframe|<object|<embed).*");
    private static final Pattern PATH_TRAVERSAL_PATTERN = Pattern.compile(".*(\\.\\.[\\\\/]|[\\\\/]\\.\\.[\\\\/]).*");

    /**
     * Validate and sanitize room ID
     */
    public String validateRoomId(String roomId) {
        if (roomId == null || roomId.trim().isEmpty()) {
            throw new IllegalArgumentException("Room ID cannot be empty");
        }
        
        String sanitized = sanitizeInput(roomId.trim());
        if (!ROOM_ID_PATTERN.matcher(sanitized).matches()) {
            throw new IllegalArgumentException("Invalid room ID format. Must be 6 digits.");
        }
        
        return sanitized;
    }

    /**
     * Validate and sanitize user ID
     */
    public String validateUserId(String userId) {
        if (userId == null || userId.trim().isEmpty()) {
            throw new IllegalArgumentException("User ID cannot be empty");
        }
        
        String sanitized = sanitizeInput(userId.trim());
        if (!USER_ID_PATTERN.matcher(sanitized).matches()) {
            throw new IllegalArgumentException("Invalid user ID format");
        }
        
        return sanitized;
    }

    /**
     * Validate and sanitize nickname
     */
    public String validateNickname(String nickname) {
        if (nickname == null || nickname.trim().isEmpty()) {
            throw new IllegalArgumentException("Nickname cannot be empty");
        }
        
        String sanitized = sanitizeInput(nickname.trim());
        if (!NICKNAME_PATTERN.matcher(sanitized).matches()) {
            throw new IllegalArgumentException("Invalid nickname format. Only Chinese characters, letters, numbers, and underscores allowed.");
        }
        
        return sanitized;
    }

    /**
     * Validate tile format
     */
    public String validateTile(String tile) {
        if (tile == null || tile.trim().isEmpty()) {
            throw new IllegalArgumentException("Tile cannot be empty");
        }
        
        String sanitized = sanitizeInput(tile.trim().toUpperCase());
        if (!TILE_PATTERN.matcher(sanitized).matches()) {
            throw new IllegalArgumentException("Invalid tile format. Must be like '5W', '3T', '7C'");
        }
        
        return sanitized;
    }

    /**
     * Validate WeChat OpenID
     */
    public String validateOpenId(String openId) {
        if (openId == null || openId.trim().isEmpty()) {
            throw new IllegalArgumentException("OpenID cannot be empty");
        }
        
        String sanitized = sanitizeInput(openId.trim());
        if (!OPENID_PATTERN.matcher(sanitized).matches()) {
            throw new IllegalArgumentException("Invalid OpenID format");
        }
        
        return sanitized;
    }

    /**
     * Validate game action command
     */
    public String validateGameCommand(String command) {
        if (command == null || command.trim().isEmpty()) {
            throw new IllegalArgumentException("Game command cannot be empty");
        }
        
        String sanitized = sanitizeInput(command.trim().toUpperCase());
        
        // Only allow specific game commands
        if (!isValidGameCommand(sanitized)) {
            throw new IllegalArgumentException("Invalid game command: " + command);
        }
        
        return sanitized;
    }

    /**
     * Sanitize general input to prevent XSS and injection attacks
     */
    public String sanitizeInput(String input) {
        if (input == null) {
            return null;
        }
        
        // Check for suspicious patterns
        if (containsSuspiciousContent(input)) {
            logger.warn("Suspicious input detected and blocked: {}", input);
            throw new IllegalArgumentException("Input contains suspicious content");
        }
        
        // HTML encode to prevent XSS
        return Encode.forHtml(input);
    }

    /**
     * Check if input contains suspicious content
     */
    public boolean containsSuspiciousContent(String input) {
        if (input == null) {
            return false;
        }
        
        String lowerInput = input.toLowerCase();
        
        return SQL_INJECTION_PATTERN.matcher(lowerInput).matches() ||
               XSS_PATTERN.matcher(lowerInput).matches() ||
               PATH_TRAVERSAL_PATTERN.matcher(lowerInput).matches();
    }

    /**
     * Validate if command is a valid game command
     */
    private boolean isValidGameCommand(String command) {
        return command.equals("PLAY") ||
               command.equals("PENG") ||
               command.equals("GANG") ||
               command.equals("HU") ||
               command.equals("PASS") ||
               command.equals("READY") ||
               command.equals("LEAVE") ||
               command.equals("DISSOLVE");
    }

    /**
     * Validate numeric input within range
     */
    public int validateIntegerRange(Integer value, int min, int max, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " cannot be null");
        }
        
        if (value < min || value > max) {
            throw new IllegalArgumentException(fieldName + " must be between " + min + " and " + max);
        }
        
        return value;
    }

    /**
     * Validate string length
     */
    public String validateStringLength(String value, int maxLength, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " cannot be null");
        }
        
        if (value.length() > maxLength) {
            throw new IllegalArgumentException(fieldName + " cannot exceed " + maxLength + " characters");
        }
        
        return sanitizeInput(value);
    }
}