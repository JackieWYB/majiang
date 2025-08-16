package com.mahjong;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * 3-Player Mahjong Game Application
 * 
 * Main application class for the Mahjong online battle game system.
 * Supports real-time multiplayer gameplay with WeChat Mini Program integration.
 */
@SpringBootApplication
@EnableAsync
@EnableScheduling
@EnableTransactionManagement
public class MahjongGameApplication {

    public static void main(String[] args) {
        SpringApplication.run(MahjongGameApplication.class, args);
    }
}