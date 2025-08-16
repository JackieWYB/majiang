-- Add admin features to user table and create audit log table

-- Add role and ban-related fields to user table
ALTER TABLE t_user 
ADD COLUMN role VARCHAR(20) NOT NULL DEFAULT 'USER' AFTER status,
ADD COLUMN ban_reason VARCHAR(500) NULL AFTER role,
ADD COLUMN banned_at DATETIME NULL AFTER ban_reason,
ADD COLUMN banned_by BIGINT NULL AFTER banned_at;

-- Add index for role
CREATE INDEX idx_user_role ON t_user(role);

-- Create audit log table
CREATE TABLE t_audit_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    admin_id BIGINT NOT NULL,
    admin_name VARCHAR(50) NOT NULL,
    action VARCHAR(50) NOT NULL,
    target_type VARCHAR(50) NOT NULL,
    target_id VARCHAR(100) NULL,
    target_name VARCHAR(100) NULL,
    details VARCHAR(1000) NULL,
    ip_address VARCHAR(45) NULL,
    user_agent VARCHAR(500) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    INDEX idx_audit_admin_id (admin_id),
    INDEX idx_audit_target_type (target_type),
    INDEX idx_audit_target_id (target_id),
    INDEX idx_audit_action (action),
    INDEX idx_audit_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Create a super admin user for testing (optional)
-- INSERT INTO t_user (open_id, nickname, coins, room_cards, status, role, created_at, updated_at)
-- VALUES ('admin_test_openid', 'Super Admin', 10000, 100, 'ACTIVE', 'SUPER_ADMIN', NOW(), NOW());