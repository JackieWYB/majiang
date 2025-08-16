package com.mahjong.service;

import com.mahjong.model.entity.User;
import com.mahjong.model.enums.UserStatus;
import com.mahjong.repository.UserRepository;
import com.mahjong.service.UserService.UserNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {
    
    @Mock
    private UserRepository userRepository;
    
    @InjectMocks
    private UserService userService;
    
    private User testUser;
    
    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setId(1L);
        testUser.setOpenId("test-openid");
        testUser.setUnionId("test-unionid");
        testUser.setNickname("Test User");
        testUser.setAvatar("test-avatar-url");
        testUser.setCoins(1000);
        testUser.setRoomCards(10);
        testUser.setStatus(UserStatus.ACTIVE);
        testUser.setCreatedAt(LocalDateTime.now());
        testUser.setUpdatedAt(LocalDateTime.now());
    }
    
    @Test
    void shouldFindUserByOpenId() {
        // Given
        when(userRepository.findByOpenId("test-openid")).thenReturn(Optional.of(testUser));
        
        // When
        Optional<User> result = userService.findByOpenId("test-openid");
        
        // Then
        assertTrue(result.isPresent());
        assertEquals(testUser.getId(), result.get().getId());
        assertEquals(testUser.getOpenId(), result.get().getOpenId());
    }
    
    @Test
    void shouldFindUserById() {
        // Given
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        
        // When
        Optional<User> result = userService.findById(1L);
        
        // Then
        assertTrue(result.isPresent());
        assertEquals(testUser.getId(), result.get().getId());
    }
    
    @Test
    void shouldCreateNewUser() {
        // Given
        String openId = "new-openid";
        String unionId = "new-unionid";
        String nickname = "New User";
        String avatar = "new-avatar-url";
        
        when(userRepository.findByOpenId(openId)).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(2L);
            return user;
        });
        
        // When
        User result = userService.createOrUpdateUser(openId, unionId, nickname, avatar);
        
        // Then
        assertNotNull(result);
        assertEquals(2L, result.getId());
        assertEquals(openId, result.getOpenId());
        assertEquals(unionId, result.getUnionId());
        assertEquals(nickname, result.getNickname());
        assertEquals(avatar, result.getAvatar());
        assertEquals(1000, result.getCoins());
        assertEquals(10, result.getRoomCards());
        assertEquals(UserStatus.ACTIVE, result.getStatus());
        
        verify(userRepository).save(any(User.class));
    }
    
    @Test
    void shouldUpdateExistingUser() {
        // Given
        String openId = "test-openid";
        String newNickname = "Updated User";
        String newAvatar = "updated-avatar-url";
        
        when(userRepository.findByOpenId(openId)).thenReturn(Optional.of(testUser));
        when(userRepository.save(any(User.class))).thenReturn(testUser);
        
        // When
        User result = userService.createOrUpdateUser(openId, null, newNickname, newAvatar);
        
        // Then
        assertEquals(testUser.getId(), result.getId());
        verify(userRepository).save(testUser);
    }
    
    @Test
    void shouldNotUpdateUserIfNoChanges() {
        // Given
        String openId = "test-openid";
        
        when(userRepository.findByOpenId(openId)).thenReturn(Optional.of(testUser));
        
        // When
        User result = userService.createOrUpdateUser(
                openId,
                testUser.getUnionId(),
                testUser.getNickname(),
                testUser.getAvatar()
        );
        
        // Then
        assertEquals(testUser.getId(), result.getId());
        verify(userRepository, never()).save(any(User.class));
    }
    
    @Test
    void shouldUpdateUserProfile() {
        // Given
        Long userId = 1L;
        String newNickname = "Updated Nickname";
        String newAvatar = "updated-avatar";
        
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(userRepository.save(any(User.class))).thenReturn(testUser);
        
        // When
        User result = userService.updateProfile(userId, newNickname, newAvatar);
        
        // Then
        assertNotNull(result);
        verify(userRepository).save(testUser);
    }
    
    @Test
    void shouldThrowExceptionWhenUpdatingNonExistentUser() {
        // Given
        Long userId = 999L;
        when(userRepository.findById(userId)).thenReturn(Optional.empty());
        
        // When & Then
        assertThrows(
                UserNotFoundException.class,
                () -> userService.updateProfile(userId, "New Name", "new-avatar")
        );
    }
    
    @Test
    void shouldUpdateUserCoins() {
        // Given
        Long userId = 1L;
        Integer newCoins = 2000;
        
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(userRepository.save(any(User.class))).thenReturn(testUser);
        
        // When
        User result = userService.updateCoins(userId, newCoins);
        
        // Then
        assertNotNull(result);
        verify(userRepository).save(testUser);
    }
    
    @Test
    void shouldNotAllowNegativeCoins() {
        // Given
        Long userId = 1L;
        Integer negativeCoins = -100;
        
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            assertEquals(0, user.getCoins());
            return user;
        });
        
        // When
        userService.updateCoins(userId, negativeCoins);
        
        // Then
        verify(userRepository).save(testUser);
    }
    
    @Test
    void shouldUpdateUserRoomCards() {
        // Given
        Long userId = 1L;
        Integer newRoomCards = 20;
        
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(userRepository.save(any(User.class))).thenReturn(testUser);
        
        // When
        User result = userService.updateRoomCards(userId, newRoomCards);
        
        // Then
        assertNotNull(result);
        verify(userRepository).save(testUser);
    }
    
    @Test
    void shouldNotAllowNegativeRoomCards() {
        // Given
        Long userId = 1L;
        Integer negativeRoomCards = -5;
        
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            assertEquals(0, user.getRoomCards());
            return user;
        });
        
        // When
        userService.updateRoomCards(userId, negativeRoomCards);
        
        // Then
        verify(userRepository).save(testUser);
    }
    
    @Test
    void shouldBanUser() {
        // Given
        Long userId = 1L;
        
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            assertEquals(UserStatus.BANNED, user.getStatus());
            return user;
        });
        
        // When
        User result = userService.banUser(userId);
        
        // Then
        assertNotNull(result);
        verify(userRepository).save(testUser);
    }
    
    @Test
    void shouldUnbanUser() {
        // Given
        Long userId = 1L;
        testUser.setStatus(UserStatus.BANNED);
        
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            assertEquals(UserStatus.ACTIVE, user.getStatus());
            return user;
        });
        
        // When
        User result = userService.unbanUser(userId);
        
        // Then
        assertNotNull(result);
        verify(userRepository).save(testUser);
    }
    
    @Test
    void shouldCheckIfUserIsActive() {
        // Given
        Long userId = 1L;
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        
        // When
        boolean isActive = userService.isUserActive(userId);
        
        // Then
        assertTrue(isActive);
    }
    
    @Test
    void shouldReturnFalseForBannedUser() {
        // Given
        Long userId = 1L;
        testUser.setStatus(UserStatus.BANNED);
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        
        // When
        boolean isActive = userService.isUserActive(userId);
        
        // Then
        assertFalse(isActive);
    }
    
    @Test
    void shouldReturnFalseForNonExistentUser() {
        // Given
        Long userId = 999L;
        when(userRepository.findById(userId)).thenReturn(Optional.empty());
        
        // When
        boolean isActive = userService.isUserActive(userId);
        
        // Then
        assertFalse(isActive);
    }
    
    @Test
    void shouldCreateUserWithDefaultNicknameWhenNoneProvided() {
        // Given
        String openId = "new-openid";
        
        when(userRepository.findByOpenId(openId)).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(2L);
            assertTrue(user.getNickname().startsWith("用户"));
            return user;
        });
        
        // When
        User result = userService.createOrUpdateUser(openId, null, null, null);
        
        // Then
        assertNotNull(result);
        assertTrue(result.getNickname().startsWith("用户"));
    }
}