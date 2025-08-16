package com.mahjong.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/**
 * Configuration for game-related scheduled executors
 */
@Configuration
@EnableScheduling
public class GameExecutorConfig {
    
    /**
     * Scheduled executor service for game timers and background tasks
     */
    @Bean(name = "gameScheduledExecutorService")
    public ScheduledExecutorService scheduledExecutorService() {
        return Executors.newScheduledThreadPool(10, r -> {
            Thread thread = new Thread(r, "game-timer-" + System.currentTimeMillis());
            thread.setDaemon(true);
            return thread;
        });
    }
}