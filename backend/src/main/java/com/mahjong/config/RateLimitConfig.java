package com.mahjong.config;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.BucketConfiguration;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * Rate limiting configuration using Bucket4j
 */
@Configuration
public class RateLimitConfig {

    /**
     * API rate limit: 100 requests per minute per user
     */
    public Supplier<BucketConfiguration> apiRateLimitConfig() {
        return () -> BucketConfiguration.builder()
                .addLimit(Bandwidth.simple(100, Duration.ofMinutes(1)))
                .build();
    }

    /**
     * WebSocket message rate limit: 30 messages per minute per user
     */
    public Supplier<BucketConfiguration> websocketRateLimitConfig() {
        return () -> BucketConfiguration.builder()
                .addLimit(Bandwidth.simple(30, Duration.ofMinutes(1)))
                .build();
    }

    /**
     * Login rate limit: 5 attempts per 15 minutes per IP
     */
    public Supplier<BucketConfiguration> loginRateLimitConfig() {
        return () -> BucketConfiguration.builder()
                .addLimit(Bandwidth.simple(5, Duration.ofMinutes(15)))
                .build();
    }

    /**
     * Room creation rate limit: 10 rooms per hour per user
     */
    public Supplier<BucketConfiguration> roomCreationRateLimitConfig() {
        return () -> BucketConfiguration.builder()
                .addLimit(Bandwidth.simple(10, Duration.ofHours(1)))
                .build();
    }
}