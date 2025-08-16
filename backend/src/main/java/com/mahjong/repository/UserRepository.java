package com.mahjong.repository;

import com.mahjong.model.entity.User;
import com.mahjong.model.enums.UserStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository interface for User entity
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    
    /**
     * Find user by WeChat openId
     * 
     * @param openId WeChat openId
     * @return User if found
     */
    Optional<User> findByOpenId(String openId);
    
    /**
     * Find user by WeChat unionId
     * 
     * @param unionId WeChat unionId
     * @return User if found
     */
    Optional<User> findByUnionId(String unionId);
    
    /**
     * Find users by status
     * 
     * @param status User status
     * @return List of users with the specified status
     */
    List<User> findByStatus(UserStatus status);
    
    /**
     * Find users created after specified date
     * 
     * @param createdAfter Date threshold
     * @return List of users created after the date
     */
    List<User> findByCreatedAtAfter(LocalDateTime createdAfter);
    
    /**
     * Count users by status
     * 
     * @param status User status
     * @return Count of users with the specified status
     */
    long countByStatus(UserStatus status);
    
    /**
     * Find users with coins greater than specified amount
     * 
     * @param coins Minimum coin amount
     * @return List of users with coins greater than the amount
     */
    @Query("SELECT u FROM User u WHERE u.coins > :coins")
    List<User> findUsersWithCoinsGreaterThan(@Param("coins") Integer coins);
    
    /**
     * Find users with room cards greater than specified amount
     * 
     * @param roomCards Minimum room card amount
     * @return List of users with room cards greater than the amount
     */
    @Query("SELECT u FROM User u WHERE u.roomCards > :roomCards")
    List<User> findUsersWithRoomCardsGreaterThan(@Param("roomCards") Integer roomCards);
    
    /**
     * Check if user exists by openId
     * 
     * @param openId WeChat openId
     * @return true if user exists
     */
    boolean existsByOpenId(String openId);
    
    /**
     * Check if user exists by unionId
     * 
     * @param unionId WeChat unionId
     * @return true if user exists
     */
    boolean existsByUnionId(String unionId);
    
    /**
     * Find users by status with pagination
     * 
     * @param status User status
     * @param pageable Pagination information
     * @return Page of users with the specified status
     */
    Page<User> findByStatus(UserStatus status, Pageable pageable);
    
    /**
     * Search users by nickname or openId with pagination
     * 
     * @param nickname Nickname to search for
     * @param openId OpenId to search for
     * @param pageable Pagination information
     * @return Page of matching users
     */
    Page<User> findByNicknameContainingIgnoreCaseOrOpenIdContaining(String nickname, String openId, Pageable pageable);
}