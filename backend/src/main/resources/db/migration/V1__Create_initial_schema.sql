-- Create initial database schema for Mahjong game

-- Create t_user table
CREATE TABLE t_user (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    open_id VARCHAR(100) NOT NULL UNIQUE,
    union_id VARCHAR(100),
    nickname VARCHAR(50) NOT NULL,
    avatar VARCHAR(500),
    coins INT NOT NULL DEFAULT 0,
    room_cards INT NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- Create indexes for t_user
CREATE INDEX idx_user_open_id ON t_user (open_id);
CREATE INDEX idx_user_union_id ON t_user (union_id);
CREATE INDEX idx_user_status ON t_user (status);

-- Create t_room_rule table
CREATE TABLE t_room_rule (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    config CLOB NOT NULL,
    is_default BOOLEAN NOT NULL DEFAULT FALSE,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- Create indexes for t_room_rule
CREATE INDEX idx_room_rule_name ON t_room_rule (name);

-- Create t_room table
CREATE TABLE t_room (
    id VARCHAR(6) PRIMARY KEY,
    owner_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'WAITING',
    rule_id BIGINT NOT NULL,
    current_dealer_user_id BIGINT,
    round_index INT NOT NULL DEFAULT 0,
    max_rounds INT NOT NULL DEFAULT 8,
    current_game_id VARCHAR(36),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    last_activity_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    FOREIGN KEY (owner_id) REFERENCES t_user(id) ON DELETE CASCADE,
    FOREIGN KEY (rule_id) REFERENCES t_room_rule(id) ON DELETE RESTRICT,
    FOREIGN KEY (current_dealer_user_id) REFERENCES t_user(id) ON DELETE SET NULL
);

-- Create indexes for t_room
CREATE INDEX idx_room_owner_id ON t_room (owner_id);
CREATE INDEX idx_room_status ON t_room (status);
CREATE INDEX idx_room_created_at ON t_room (created_at);

-- Create t_room_player table
CREATE TABLE t_room_player (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    room_id VARCHAR(6) NOT NULL,
    user_id BIGINT NOT NULL,
    seat_index INT NOT NULL,
    total_score INT NOT NULL DEFAULT 0,
    is_ready BOOLEAN NOT NULL DEFAULT FALSE,
    is_online BOOLEAN NOT NULL DEFAULT TRUE,
    joined_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_seen_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    FOREIGN KEY (room_id) REFERENCES t_room(id) ON DELETE CASCADE,
    FOREIGN KEY (user_id) REFERENCES t_user(id) ON DELETE CASCADE,
    
    CONSTRAINT chk_seat_index CHECK (seat_index >= 0 AND seat_index <= 2)
);

-- Create indexes and unique constraints for t_room_player
CREATE INDEX idx_room_player_room_id ON t_room_player (room_id);
CREATE INDEX idx_room_player_user_id ON t_room_player (user_id);
CREATE UNIQUE INDEX uk_room_player ON t_room_player (room_id, user_id);
CREATE UNIQUE INDEX uk_room_seat ON t_room_player (room_id, seat_index);

-- Create t_game_record table
CREATE TABLE t_game_record (
    id VARCHAR(36) PRIMARY KEY,
    room_id VARCHAR(6) NOT NULL,
    round_index INT NOT NULL,
    winner_id BIGINT,
    result VARCHAR(10) NOT NULL,
    duration_seconds INT NOT NULL,
    winning_tile VARCHAR(10),
    winning_type VARCHAR(50),
    base_score INT NOT NULL DEFAULT 0,
    multiplier DOUBLE NOT NULL DEFAULT 1.0,
    final_score INT NOT NULL DEFAULT 0,
    dealer_user_id BIGINT,
    random_seed VARCHAR(20),
    game_actions CLOB,
    final_hands CLOB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    FOREIGN KEY (room_id) REFERENCES t_room(id) ON DELETE CASCADE,
    FOREIGN KEY (winner_id) REFERENCES t_user(id) ON DELETE SET NULL,
    FOREIGN KEY (dealer_user_id) REFERENCES t_user(id) ON DELETE SET NULL
);

-- Create indexes for t_game_record
CREATE INDEX idx_game_record_room_id ON t_game_record (room_id);
CREATE INDEX idx_game_record_created_at ON t_game_record (created_at);
CREATE INDEX idx_game_record_winner_id ON t_game_record (winner_id);

-- Create t_game_player_record table
CREATE TABLE t_game_player_record (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    game_record_id VARCHAR(36) NOT NULL,
    user_id BIGINT NOT NULL,
    seat_index INT NOT NULL,
    result VARCHAR(10) NOT NULL,
    score INT NOT NULL DEFAULT 0,
    base_score INT NOT NULL DEFAULT 0,
    gang_score INT NOT NULL DEFAULT 0,
    multiplier DOUBLE NOT NULL DEFAULT 1.0,
    is_dealer BOOLEAN NOT NULL DEFAULT FALSE,
    is_self_draw BOOLEAN NOT NULL DEFAULT FALSE,
    winning_hand CLOB,
    final_hand CLOB,
    melds CLOB,
    
    FOREIGN KEY (game_record_id) REFERENCES t_game_record(id) ON DELETE CASCADE,
    FOREIGN KEY (user_id) REFERENCES t_user(id) ON DELETE CASCADE,
    
    CONSTRAINT chk_player_seat_index CHECK (seat_index >= 0 AND seat_index <= 2)
);

-- Create indexes and unique constraints for t_game_player_record
CREATE INDEX idx_game_player_record_game_id ON t_game_player_record (game_record_id);
CREATE INDEX idx_game_player_record_user_id ON t_game_player_record (user_id);
CREATE UNIQUE INDEX uk_game_player ON t_game_player_record (game_record_id, user_id);