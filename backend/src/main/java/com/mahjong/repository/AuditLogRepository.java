package com.mahjong.repository;

import com.mahjong.model.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Repository for AuditLog entity
 */
@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
    
    /**
     * Find audit logs by admin ID
     */
    Page<AuditLog> findByAdminIdOrderByCreatedAtDesc(Long adminId, Pageable pageable);
    
    /**
     * Find audit logs by target type and ID
     */
    Page<AuditLog> findByTargetTypeAndTargetIdOrderByCreatedAtDesc(String targetType, String targetId, Pageable pageable);
    
    /**
     * Find audit logs by action
     */
    Page<AuditLog> findByActionOrderByCreatedAtDesc(String action, Pageable pageable);
    
    /**
     * Find audit logs within date range
     */
    Page<AuditLog> findByCreatedAtBetweenOrderByCreatedAtDesc(LocalDateTime startDate, LocalDateTime endDate, Pageable pageable);
    
    /**
     * Find recent audit logs for a specific target
     */
    @Query("SELECT a FROM AuditLog a WHERE a.targetType = :targetType AND a.targetId = :targetId " +
           "AND a.createdAt >= :since ORDER BY a.createdAt DESC")
    List<AuditLog> findRecentLogsByTarget(@Param("targetType") String targetType, 
                                         @Param("targetId") String targetId, 
                                         @Param("since") LocalDateTime since);
    
    /**
     * Count logs by admin in date range
     */
    @Query("SELECT COUNT(a) FROM AuditLog a WHERE a.adminId = :adminId " +
           "AND a.createdAt >= :startDate AND a.createdAt <= :endDate")
    Long countByAdminInDateRange(@Param("adminId") Long adminId, 
                                @Param("startDate") LocalDateTime startDate, 
                                @Param("endDate") LocalDateTime endDate);
    
    /**
     * Find suspicious activity (multiple actions on same target by same admin)
     */
    @Query("SELECT a FROM AuditLog a WHERE a.adminId = :adminId AND a.targetType = :targetType " +
           "AND a.targetId = :targetId AND a.createdAt >= :since ORDER BY a.createdAt DESC")
    List<AuditLog> findSuspiciousActivity(@Param("adminId") Long adminId, 
                                         @Param("targetType") String targetType, 
                                         @Param("targetId") String targetId, 
                                         @Param("since") LocalDateTime since);
}