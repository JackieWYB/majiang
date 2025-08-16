package com.mahjong.model.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Audit log entity for tracking administrative actions
 */
@Entity
@Table(name = "t_audit_log", indexes = {
    @Index(name = "idx_audit_admin_id", columnList = "adminId"),
    @Index(name = "idx_audit_target_type", columnList = "targetType"),
    @Index(name = "idx_audit_target_id", columnList = "targetId"),
    @Index(name = "idx_audit_action", columnList = "action"),
    @Index(name = "idx_audit_created_at", columnList = "createdAt")
})
public class AuditLog {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @NotNull
    @Column(name = "admin_id", nullable = false)
    private Long adminId;
    
    @NotBlank
    @Size(max = 50)
    @Column(name = "admin_name", nullable = false, length = 50)
    private String adminName;
    
    @NotBlank
    @Size(max = 50)
    @Column(name = "action", nullable = false, length = 50)
    private String action;
    
    @NotBlank
    @Size(max = 50)
    @Column(name = "target_type", nullable = false, length = 50)
    private String targetType;
    
    @Size(max = 100)
    @Column(name = "target_id", length = 100)
    private String targetId;
    
    @Size(max = 100)
    @Column(name = "target_name", length = 100)
    private String targetName;
    
    @Size(max = 1000)
    @Column(name = "details", length = 1000)
    private String details;
    
    @Size(max = 45)
    @Column(name = "ip_address", length = 45)
    private String ipAddress;
    
    @Size(max = 500)
    @Column(name = "user_agent", length = 500)
    private String userAgent;
    
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    // Constructors
    public AuditLog() {}
    
    public AuditLog(Long adminId, String adminName, String action, String targetType, String targetId, String targetName, String details) {
        this.adminId = adminId;
        this.adminName = adminName;
        this.action = action;
        this.targetType = targetType;
        this.targetId = targetId;
        this.targetName = targetName;
        this.details = details;
    }
    
    // Getters and Setters
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
    public Long getAdminId() {
        return adminId;
    }
    
    public void setAdminId(Long adminId) {
        this.adminId = adminId;
    }
    
    public String getAdminName() {
        return adminName;
    }
    
    public void setAdminName(String adminName) {
        this.adminName = adminName;
    }
    
    public String getAction() {
        return action;
    }
    
    public void setAction(String action) {
        this.action = action;
    }
    
    public String getTargetType() {
        return targetType;
    }
    
    public void setTargetType(String targetType) {
        this.targetType = targetType;
    }
    
    public String getTargetId() {
        return targetId;
    }
    
    public void setTargetId(String targetId) {
        this.targetId = targetId;
    }
    
    public String getTargetName() {
        return targetName;
    }
    
    public void setTargetName(String targetName) {
        this.targetName = targetName;
    }
    
    public String getDetails() {
        return details;
    }
    
    public void setDetails(String details) {
        this.details = details;
    }
    
    public String getIpAddress() {
        return ipAddress;
    }
    
    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }
    
    public String getUserAgent() {
        return userAgent;
    }
    
    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }
    
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
    
    // equals and hashCode
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AuditLog auditLog = (AuditLog) o;
        return Objects.equals(id, auditLog.id);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
    
    @Override
    public String toString() {
        return "AuditLog{" +
                "id=" + id +
                ", adminId=" + adminId +
                ", adminName='" + adminName + '\'' +
                ", action='" + action + '\'' +
                ", targetType='" + targetType + '\'' +
                ", targetId='" + targetId + '\'' +
                ", targetName='" + targetName + '\'' +
                ", createdAt=" + createdAt +
                '}';
    }
}