package com.mahjong.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SystemMetricsServiceTest {
    
    private MeterRegistry meterRegistry;
    private SystemMetricsService systemMetricsService;
    
    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        systemMetricsService = new SystemMetricsService(meterRegistry);
    }
    
    @Test
    void incrementGameStarted_Success() {
        // Given
        Counter counter = meterRegistry.find("mahjong.games.started").counter();
        assertNotNull(counter);
        double initialCount = counter.count();
        
        // When
        systemMetricsService.incrementGameStarted();
        
        // Then
        assertEquals(initialCount + 1, counter.count());
    }
    
    @Test
    void incrementGameCompleted_Success() {
        // Given
        Counter counter = meterRegistry.find("mahjong.games.completed").counter();
        assertNotNull(counter);
        double initialCount = counter.count();
        
        // When
        systemMetricsService.incrementGameCompleted();
        
        // Then
        assertEquals(initialCount + 1, counter.count());
    }
    
    @Test
    void incrementUserLogin_Success() {
        // Given
        Counter counter = meterRegistry.find("mahjong.users.login").counter();
        assertNotNull(counter);
        double initialCount = counter.count();
        
        // When
        systemMetricsService.incrementUserLogin();
        
        // Then
        assertEquals(initialCount + 1, counter.count());
    }
    
    @Test
    void incrementUserBanned_Success() {
        // Given
        Counter counter = meterRegistry.find("mahjong.users.banned").counter();
        assertNotNull(counter);
        double initialCount = counter.count();
        
        // When
        systemMetricsService.incrementUserBanned();
        
        // Then
        assertEquals(initialCount + 1, counter.count());
    }
    
    @Test
    void incrementRoomCreated_Success() {
        // Given
        Counter counter = meterRegistry.find("mahjong.rooms.created").counter();
        assertNotNull(counter);
        double initialCount = counter.count();
        
        // When
        systemMetricsService.incrementRoomCreated();
        
        // Then
        assertEquals(initialCount + 1, counter.count());
    }
    
    @Test
    void incrementRoomDissolved_Success() {
        // Given
        Counter counter = meterRegistry.find("mahjong.rooms.dissolved").counter();
        assertNotNull(counter);
        double initialCount = counter.count();
        
        // When
        systemMetricsService.incrementRoomDissolved();
        
        // Then
        assertEquals(initialCount + 1, counter.count());
    }
    
    @Test
    void incrementSuspiciousActivity_Success() {
        // Given
        Counter counter = meterRegistry.find("mahjong.security.suspicious_activity").counter();
        assertNotNull(counter);
        double initialCount = counter.count();
        
        // When
        systemMetricsService.incrementSuspiciousActivity();
        
        // Then
        assertEquals(initialCount + 1, counter.count());
    }
    
    @Test
    void setActiveRooms_Success() {
        // Given
        Gauge gauge = meterRegistry.find("mahjong.rooms.active").gauge();
        assertNotNull(gauge);
        
        // When
        systemMetricsService.setActiveRooms(50);
        
        // Then
        assertEquals(50.0, gauge.value());
    }
    
    @Test
    void setActivePlayers_Success() {
        // Given
        Gauge gauge = meterRegistry.find("mahjong.players.active").gauge();
        assertNotNull(gauge);
        
        // When
        systemMetricsService.setActivePlayers(150);
        
        // Then
        assertEquals(150.0, gauge.value());
    }
    
    @Test
    void setOnlineUsers_Success() {
        // Given
        Gauge gauge = meterRegistry.find("mahjong.users.online").gauge();
        assertNotNull(gauge);
        
        // When
        systemMetricsService.setOnlineUsers(200);
        
        // Then
        assertEquals(200.0, gauge.value());
    }
    
    @Test
    void gameActionTimer_Success() {
        // Given
        Timer timer = meterRegistry.find("mahjong.game.action.duration").timer();
        assertNotNull(timer);
        long initialCount = timer.count();
        
        // When
        Timer.Sample sample = systemMetricsService.startGameActionTimer();
        // Simulate some work
        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        systemMetricsService.recordGameActionTime(sample);
        
        // Then
        assertEquals(initialCount + 1, timer.count());
        assertTrue(timer.totalTime(java.util.concurrent.TimeUnit.MILLISECONDS) > 0);
    }
    
    @Test
    void databaseQueryTimer_Success() {
        // Given
        Timer timer = meterRegistry.find("mahjong.database.query.duration").timer();
        assertNotNull(timer);
        long initialCount = timer.count();
        
        // When
        Timer.Sample sample = systemMetricsService.startDatabaseQueryTimer();
        // Simulate some work
        try {
            Thread.sleep(5);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        systemMetricsService.recordDatabaseQueryTime(sample);
        
        // Then
        assertEquals(initialCount + 1, timer.count());
        assertTrue(timer.totalTime(java.util.concurrent.TimeUnit.MILLISECONDS) > 0);
    }
    
    @Test
    void redisOperationTimer_Success() {
        // Given
        Timer timer = meterRegistry.find("mahjong.redis.operation.duration").timer();
        assertNotNull(timer);
        long initialCount = timer.count();
        
        // When
        Timer.Sample sample = systemMetricsService.startRedisOperationTimer();
        // Simulate some work
        try {
            Thread.sleep(3);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        systemMetricsService.recordRedisOperationTime(sample);
        
        // Then
        assertEquals(initialCount + 1, timer.count());
        assertTrue(timer.totalTime(java.util.concurrent.TimeUnit.MILLISECONDS) > 0);
    }
    
    @Test
    void recordCustomMetric_Success() {
        // When
        systemMetricsService.recordCustomMetric("test.custom.metric", "Test custom metric", 42.0);
        
        // Then
        Gauge gauge = meterRegistry.find("test.custom.metric").gauge();
        assertNotNull(gauge);
        assertEquals(42.0, gauge.value());
    }
    
    @Test
    void incrementCustomCounter_Success() {
        // When
        systemMetricsService.incrementCustomCounter("test.custom.counter", "Test custom counter");
        
        // Then
        Counter counter = meterRegistry.find("test.custom.counter").counter();
        assertNotNull(counter);
        assertEquals(1.0, counter.count());
    }
    
    @Test
    void multipleIncrements_Success() {
        // When
        systemMetricsService.incrementGameStarted();
        systemMetricsService.incrementGameStarted();
        systemMetricsService.incrementGameCompleted();
        
        // Then
        Counter startedCounter = meterRegistry.find("mahjong.games.started").counter();
        Counter completedCounter = meterRegistry.find("mahjong.games.completed").counter();
        
        assertEquals(2.0, startedCounter.count());
        assertEquals(1.0, completedCounter.count());
    }
    
    @Test
    void gaugeUpdates_Success() {
        // When
        systemMetricsService.setActiveRooms(10);
        systemMetricsService.setActivePlayers(30);
        systemMetricsService.setOnlineUsers(100);
        
        // Update values
        systemMetricsService.setActiveRooms(15);
        systemMetricsService.setActivePlayers(45);
        systemMetricsService.setOnlineUsers(150);
        
        // Then
        Gauge roomsGauge = meterRegistry.find("mahjong.rooms.active").gauge();
        Gauge playersGauge = meterRegistry.find("mahjong.players.active").gauge();
        Gauge usersGauge = meterRegistry.find("mahjong.users.online").gauge();
        
        assertEquals(15.0, roomsGauge.value());
        assertEquals(45.0, playersGauge.value());
        assertEquals(150.0, usersGauge.value());
    }
}