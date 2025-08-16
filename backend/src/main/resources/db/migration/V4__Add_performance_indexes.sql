-- Performance optimization indexes for Mahjong game database
-- This migration adds indexes to improve query performance for frequently accessed data

-- User table indexes
CREATE INDEX IF NOT EXISTS idx_user_open_id ON t_user(open_id);
CREATE INDEX IF NOT EXISTS idx_user_status ON t_user(status);
CREATE INDEX IF NOT EXISTS idx_user_created_at ON t_user(created_at);
CREATE INDEX IF NOT EXISTS idx_user_coins ON t_user(coins);

-- Room table indexes
CREATE INDEX IF NOT EXISTS idx_room_owner_id ON t_room(owner_id);
CREATE INDEX IF NOT EXISTS idx_room_status ON t_room(status);
CREATE INDEX IF NOT EXISTS idx_room_created_at ON t_room(created_at);
CREATE INDEX IF NOT EXISTS idx_room_status_created ON t_room(status, created_at);

-- Room player table indexes
CREATE INDEX IF NOT EXISTS idx_room_player_room_id ON t_room_player(room_id);
CREATE INDEX IF NOT EXISTS idx_room_player_user_id ON t_room_player(user_id);
CREATE INDEX IF NOT EXISTS idx_room_player_joined_at ON t_room_player(joined_at);
CREATE INDEX IF NOT EXISTS idx_room_player_room_user ON t_room_player(room_id, user_id);

-- Game record table indexes
CREATE INDEX IF NOT EXISTS idx_game_record_room_id ON t_game_record(room_id);
CREATE INDEX IF NOT EXISTS idx_game_record_created_at ON t_game_record(created_at);
CREATE INDEX IF NOT EXISTS idx_game_record_status ON t_game_record(status);
CREATE INDEX IF NOT EXISTS idx_game_record_room_created ON t_game_record(room_id, created_at);

-- Game player record table indexes
CREATE INDEX IF NOT EXISTS idx_game_player_record_game_id ON t_game_player_record(game_record_id);
CREATE INDEX IF NOT EXISTS idx_game_player_record_user_id ON t_game_player_record(user_id);
CREATE INDEX IF NOT EXISTS idx_game_player_record_score ON t_game_player_record(final_score);
CREATE INDEX IF NOT EXISTS idx_game_player_record_user_created ON t_game_player_record(user_id, created_at);

-- Audit log table indexes
CREATE INDEX IF NOT EXISTS idx_audit_log_user_id ON t_audit_log(user_id);
CREATE INDEX IF NOT EXISTS idx_audit_log_action ON t_audit_log(action);
CREATE INDEX IF NOT EXISTS idx_audit_log_created_at ON t_audit_log(created_at);
CREATE INDEX IF NOT EXISTS idx_audit_log_user_action ON t_audit_log(user_id, action);
CREATE INDEX IF NOT EXISTS idx_audit_log_action_created ON t_audit_log(action, created_at);

-- Room rule table indexes
CREATE INDEX IF NOT EXISTS idx_room_rule_name ON t_room_rule(name);

-- Composite indexes for common query patterns
CREATE INDEX IF NOT EXISTS idx_user_status_coins ON t_user(status, coins DESC);
CREATE INDEX IF NOT EXISTS idx_room_status_owner ON t_room(status, owner_id);
CREATE INDEX IF NOT EXISTS idx_game_record_status_created ON t_game_record(status, created_at DESC);

-- Partial indexes for active data (PostgreSQL specific, will be ignored on MySQL)
-- CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_active_rooms ON t_room(id, status, created_at) WHERE status IN ('WAITING', 'PLAYING');
-- CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_active_users ON t_user(id, open_id) WHERE status = 'ACTIVE';

-- Add comments for documentation
COMMENT ON INDEX idx_user_open_id IS 'Index for WeChat OpenID lookups';
COMMENT ON INDEX idx_room_status IS 'Index for filtering rooms by status';
COMMENT ON INDEX idx_game_record_room_created IS 'Composite index for room game history queries';
COMMENT ON INDEX idx_game_player_record_user_created IS 'Composite index for user game history queries';
COMMENT ON INDEX idx_audit_log_action_created IS 'Composite index for audit log queries by action and time';