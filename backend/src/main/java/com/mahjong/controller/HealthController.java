package com.mahjong.controller;

import com.mahjong.model.dto.SystemHealthResponse;
import com.mahjong.service.SystemMetricsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.Connection;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Health check controller for monitoring system status
 */
@RestController
@RequestMapping("/actuator")
public class HealthController implements HealthIndicator {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private SystemMetricsService systemMetricsService;

    @Override
    public Health health() {
        Map<String, Object> details = new HashMap<>();
        
        try {
            // Check database connectivity
            boolean dbHealthy = checkDatabaseHealth();
            details.put("database", dbHealthy ? "UP" : "DOWN");
            
            // Check Redis connectivity
            boolean redisHealthy = checkRedisHealth();
            details.put("redis", redisHealthy ? "UP" : "DOWN");
            
            // Check system metrics
            SystemHealthResponse systemHealth = systemMetricsService.getSystemHealth();
            details.put("activeRooms", systemHealth.getActiveRooms());
            details.put("activePlayers", systemHealth.getActivePlayers());
            details.put("memoryUsage", systemHealth.getMemoryUsage());
            details.put("cpuUsage", systemHealth.getCpuUsage());
            
            if (dbHealthy && redisHealthy) {
                return Health.up().withDetails(details).build();
            } else {
                return Health.down().withDetails(details).build();
            }
            
        } catch (Exception e) {
            details.put("error", e.getMessage());
            return Health.down().withDetails(details).build();
        }
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> healthCheck() {
        Health health = health();
        Map<String, Object> response = new HashMap<>();
        response.put("status", health.getStatus().getCode());
        response.put("timestamp", LocalDateTime.now());
        response.put("details", health.getDetails());
        
        if (health.getStatus().getCode().equals("UP")) {
            return ResponseEntity.ok(response);
        } else {
            return ResponseEntity.status(503).body(response);
        }
    }

    @GetMapping("/health/liveness")
    public ResponseEntity<Map<String, String>> livenessProbe() {
        Map<String, String> response = new HashMap<>();
        response.put("status", "UP");
        response.put("timestamp", LocalDateTime.now().toString());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/health/readiness")
    public ResponseEntity<Map<String, Object>> readinessProbe() {
        Map<String, Object> response = new HashMap<>();
        
        try {
            boolean dbReady = checkDatabaseHealth();
            boolean redisReady = checkRedisHealth();
            
            response.put("database", dbReady ? "READY" : "NOT_READY");
            response.put("redis", redisReady ? "READY" : "NOT_READY");
            response.put("timestamp", LocalDateTime.now());
            
            if (dbReady && redisReady) {
                response.put("status", "READY");
                return ResponseEntity.ok(response);
            } else {
                response.put("status", "NOT_READY");
                return ResponseEntity.status(503).body(response);
            }
            
        } catch (Exception e) {
            response.put("status", "NOT_READY");
            response.put("error", e.getMessage());
            response.put("timestamp", LocalDateTime.now());
            return ResponseEntity.status(503).body(response);
        }
    }

    @GetMapping("/metrics")
    public ResponseEntity<SystemHealthResponse> getMetrics() {
        try {
            SystemHealthResponse metrics = systemMetricsService.getSystemHealth();
            return ResponseEntity.ok(metrics);
        } catch (Exception e) {
            return ResponseEntity.status(500).build();
        }
    }

    private boolean checkDatabaseHealth() {
        try (Connection connection = dataSource.getConnection()) {
            return connection.isValid(5);
        } catch (Exception e) {
            return false;
        }
    }

    private boolean checkRedisHealth() {
        try {
            String pong = redisTemplate.getConnectionFactory()
                .getConnection()
                .ping();
            return "PONG".equals(pong);
        } catch (Exception e) {
            return false;
        }
    }
}