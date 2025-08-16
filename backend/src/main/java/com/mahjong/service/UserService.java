package com.mahjong.service;

import com.mahjong.model.entity.User;
import com.mahjong.model.enums.UserStatus;
import com.mahjong.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * User service for managing user profiles and authentication
 */
@Service
@Transactional
public class UserService {
    
    private static final Logger logger = LoggerFactory.getLogger(UserService.class);
    
    private final UserRepository userRepository;
    
    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }
    
    /**
     * Find user by openId
     * 
     * @param openId WeChat openId
     * @return User if found
     */
    @Transactional(readOnly = true)
    public Optional<User> findByOpenId(String openId) {
        return userRepository.findByOpenId(openId);
    }
    
    /**
     * Find user by ID
     * 
     * @param userId User ID
     * @return User if found
     */
    @Transactional(readOnly = true)
    public Optional<User> findById(Long userId) {
        return userRepository.findById(userId);
    }
    
    /**
     * Create or update user from WeChat session
     * 
     * @param openId WeChat openId
     * @param unionId WeChat unionId (optional)
     * @param nickname User nickname (optional)
     * @param avatar User avatar URL (optional)
     * @return Created or updated user
     */
    public User createOrUpdateUser(String openId, String unionId, String nickname, String avatar) {
        logger.debug("Creating or updating user with openId: {}", openId);
        
        Optional<User> existingUser = findByOpenId(openId);
        
        if (existingUser.isPresent()) {
            User user = existingUser.get();
            
            // Update user information if provided
            boolean updated = false;
            
            if (unionId != null && !unionId.equals(user.getUnionId())) {
                user.setUnionId(unionId);
                updated = true;
            }
            
            if (nickname != null && !nickname.equals(user.getNickname())) {
                user.setNickname(nickname);
                updated = true;
            }
            
            if (avatar != null && !avatar.equals(user.getAvatar())) {
                user.setAvatar(avatar);
                updated = true;
            }
            
            if (updated) {
                user.setUpdatedAt(LocalDateTime.now());
                user = userRepository.save(user);
                logger.debug("Updated existing user: {}", user.getId());
            }
            
            return user;
        } else {
            // Create new user
            User newUser = new User();
            newUser.setOpenId(openId);
            newUser.setUnionId(unionId);
            newUser.setNickname(nickname != null ? nickname : "用户" + System.currentTimeMillis() % 10000);
            newUser.setAvatar(avatar);
            newUser.setCoins(1000); // Initial coins
            newUser.setRoomCards(10); // Initial room cards
            newUser.setStatus(UserStatus.ACTIVE);
            newUser.setCreatedAt(LocalDateTime.now());
            newUser.setUpdatedAt(LocalDateTime.now());
            
            newUser = userRepository.save(newUser);
            logger.info("Created new user: {} with openId: {}", newUser.getId(), openId);
            
            return newUser;
        }
    }
    
    /**
     * Update user profile
     * 
     * @param userId User ID
     * @param nickname New nickname (optional)
     * @param avatar New avatar URL (optional)
     * @return Updated user
     * @throws UserNotFoundException if user not found
     */
    public User updateProfile(Long userId, String nickname, String avatar) {
        logger.debug("Updating profile for user: {}", userId);
        
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found: " + userId));
        
        boolean updated = false;
        
        if (nickname != null && !nickname.trim().isEmpty() && !nickname.equals(user.getNickname())) {
            user.setNickname(nickname.trim());
            updated = true;
        }
        
        if (avatar != null && !avatar.equals(user.getAvatar())) {
            user.setAvatar(avatar);
            updated = true;
        }
        
        if (updated) {
            user.setUpdatedAt(LocalDateTime.now());
            user = userRepository.save(user);
            logger.debug("Updated profile for user: {}", userId);
        }
        
        return user;
    }
    
    /**
     * Update user coins
     * 
     * @param userId User ID
     * @param coins New coin amount
     * @return Updated user
     * @throws UserNotFoundException if user not found
     */
    public User updateCoins(Long userId, Integer coins) {
        logger.debug("Updating coins for user: {} to {}", userId, coins);
        
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found: " + userId));
        
        user.setCoins(Math.max(0, coins)); // Ensure coins are not negative
        user.setUpdatedAt(LocalDateTime.now());
        
        user = userRepository.save(user);
        logger.debug("Updated coins for user: {} to {}", userId, user.getCoins());
        
        return user;
    }
    
    /**
     * Update user room cards
     * 
     * @param userId User ID
     * @param roomCards New room card amount
     * @return Updated user
     * @throws UserNotFoundException if user not found
     */
    public User updateRoomCards(Long userId, Integer roomCards) {
        logger.debug("Updating room cards for user: {} to {}", userId, roomCards);
        
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found: " + userId));
        
        user.setRoomCards(Math.max(0, roomCards)); // Ensure room cards are not negative
        user.setUpdatedAt(LocalDateTime.now());
        
        user = userRepository.save(user);
        logger.debug("Updated room cards for user: {} to {}", userId, user.getRoomCards());
        
        return user;
    }
    
    /**
     * Ban user
     * 
     * @param userId User ID
     * @return Updated user
     * @throws UserNotFoundException if user not found
     */
    public User banUser(Long userId) {
        logger.info("Banning user: {}", userId);
        
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found: " + userId));
        
        user.setStatus(UserStatus.BANNED);
        user.setUpdatedAt(LocalDateTime.now());
        
        user = userRepository.save(user);
        logger.info("Banned user: {}", userId);
        
        return user;
    }
    
    /**
     * Unban user
     * 
     * @param userId User ID
     * @return Updated user
     * @throws UserNotFoundException if user not found
     */
    public User unbanUser(Long userId) {
        logger.info("Unbanning user: {}", userId);
        
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found: " + userId));
        
        user.setStatus(UserStatus.ACTIVE);
        user.setUpdatedAt(LocalDateTime.now());
        
        user = userRepository.save(user);
        logger.info("Unbanned user: {}", userId);
        
        return user;
    }
    
    /**
     * Check if user is active (not banned)
     * 
     * @param userId User ID
     * @return true if user is active
     */
    @Transactional(readOnly = true)
    public boolean isUserActive(Long userId) {
        return userRepository.findById(userId)
                .map(user -> user.getStatus() == UserStatus.ACTIVE)
                .orElse(false);
    }
    
    /**
     * Get current user from authentication
     * 
     * @param authentication Spring Security authentication
     * @return Current user
     * @throws UserNotFoundException if user not found
     */
    @Transactional(readOnly = true)
    public User getCurrentUser(Authentication authentication) {
        if (authentication == null || authentication.getPrincipal() == null) {
            throw new UserNotFoundException("No authenticated user");
        }
        
        String userIdStr = authentication.getPrincipal().toString();
        Long userId = Long.parseLong(userIdStr);
        
        return userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found: " + userId));
    }
    
    /**
     * User not found exception
     */
    public static class UserNotFoundException extends RuntimeException {
        public UserNotFoundException(String message) {
            super(message);
        }
    }
}