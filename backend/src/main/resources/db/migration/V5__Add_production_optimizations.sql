-- Production optimizations and additional indexes
-- Add composite indexes for common query patterns

-- Index for room queries by status and owner
CREATE INDEX idx_room_status_owner ON t_room(status, owner_id);

-- Index for game records by user and date
CREATE INDEX idx_game_record_user_date ON t_game_record(created_at DESC);
CREATE INDEX idx_game_player_record_user_game ON t_game_player_record(user_id, game_record_id);

-- Index for audit logs by timestamp and action
CREATE INDEX idx_audit_log_timestamp_action ON t_audit_log(created_at DESC, action);

-- Add database constraints for data integrity
ALTER TABLE t_room ADD CONSTRAINT chk_room_max_rounds CHECK (max_rounds > 0 AND max_rounds <= 16);
ALTER TABLE t_user ADD CONSTRAINT chk_user_coins CHECK (coins >= 0);
ALTER TABLE t_user ADD CONSTRAINT chk_user_room_cards CHECK (room_cards >= 0);

-- Create stored procedure for cleanup old records
DELIMITER //
CREATE PROCEDURE CleanupOldRecords()
BEGIN
    -- Delete game records older than 1 year
    DELETE FROM t_game_record WHERE created_at < DATE_SUB(NOW(), INTERVAL 1 YEAR);
    
    -- Delete audit logs older than 6 months
    DELETE FROM t_audit_log WHERE created_at < DATE_SUB(NOW(), INTERVAL 6 MONTH);
    
    -- Delete inactive rooms older than 1 day
    DELETE FROM t_room WHERE status = 'DISSOLVED' AND updated_at < DATE_SUB(NOW(), INTERVAL 1 DAY);
END //
DELIMITER ;

-- Create event scheduler for automatic cleanup (if enabled)
-- SET GLOBAL event_scheduler = ON;
-- CREATE EVENT IF NOT EXISTS cleanup_old_records
-- ON SCHEDULE EVERY 1 DAY
-- STARTS CURRENT_TIMESTAMP
-- DO CALL CleanupOldRecords();