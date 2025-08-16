package com.mahjong.util;

/**
 * Application Constants
 * 
 * Defines constant values used throughout the application.
 */
public final class Constants {

    private Constants() {
        // Utility class
    }

    // Redis Keys
    public static final class Redis {
        public static final String GAME_STATE_PREFIX = "game:state:";
        public static final String USER_SESSION_PREFIX = "user:session:";
        public static final String ROOM_PREFIX = "room:";
        public static final String PLAYER_CONNECTION_PREFIX = "player:connection:";
    }

    // Game Constants
    public static final class Game {
        public static final int MAX_PLAYERS = 3;
        public static final int TILES_PER_PLAYER = 13;
        public static final int DEALER_TILES = 14;
        public static final int TOTAL_TILES = 108; // 36 * 3 suits
        public static final int DEFAULT_TURN_TIMEOUT_SECONDS = 15;
        public static final int ACTION_WINDOW_SECONDS = 2;
    }

    // Room Constants
    public static final class Room {
        public static final int ROOM_ID_LENGTH = 6;
        public static final int MAX_INACTIVE_MINUTES = 30;
        public static final int CLEANUP_INTERVAL_MINUTES = 5;
    }

    // WebSocket Topics
    public static final class WebSocket {
        public static final String ROOM_TOPIC_PREFIX = "/topic/room/";
        public static final String USER_QUEUE_PREFIX = "/queue/user/";
        public static final String GAME_DESTINATION_PREFIX = "/app/game/";
    }

    // JWT Constants
    public static final class JWT {
        public static final String TOKEN_PREFIX = "Bearer ";
        public static final String HEADER_STRING = "Authorization";
        public static final String AUTHORITIES_KEY = "authorities";
    }

    // WeChat Constants
    public static final class WeChat {
        public static final String CODE2SESSION_URL = "https://api.weixin.qq.com/sns/jscode2session";
        public static final String GRANT_TYPE = "authorization_code";
    }
}