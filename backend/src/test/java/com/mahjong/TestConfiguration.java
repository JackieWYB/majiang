package com.mahjong;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

import javax.sql.DataSource;
import org.springframework.boot.jdbc.DataSourceBuilder;

/**
 * Test configuration for comprehensive testing suite
 * Provides test-specific beans and configurations
 */
@TestConfiguration
public class TestConfiguration {

    /**
     * Test-specific Redis template with optimized settings
     */
    @Bean
    @Primary
    @Profile("test")
    public RedisTemplate<String, Object> testRedisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        
        // Use String serializer for keys
        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        
        // Use JSON serializer for values
        template.setValueSerializer(new GenericJackson2JsonRedisSerializer());
        template.setHashValueSerializer(new GenericJackson2JsonRedisSerializer());
        
        template.setEnableTransactionSupport(false); // Disable for better performance in tests
        template.afterPropertiesSet();
        
        return template;
    }

    /**
     * Test-specific data source with optimized connection pool
     */
    @Bean
    @Primary
    @Profile("test")
    public DataSource testDataSource() {
        return DataSourceBuilder.create()
                .driverClassName("com.mysql.cj.jdbc.Driver")
                .url("jdbc:mysql://localhost:3307/mahjong_test?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC")
                .username("test")
                .password("test")
                .build();
    }

    /**
     * Testcontainers configuration for integration tests
     */
    public static class TestContainersConfiguration {
        
        static final MySQLContainer<?> mysql = new MySQLContainer<>(DockerImageName.parse("mysql:8.0"))
                .withDatabaseName("mahjong_test")
                .withUsername("test")
                .withPassword("test")
                .withReuse(true);

        static final GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:6-alpine"))
                .withExposedPorts(6379)
                .withReuse(true);

        static {
            mysql.start();
            redis.start();
        }

        @DynamicPropertySource
        static void configureProperties(DynamicPropertyRegistry registry) {
            registry.add("spring.datasource.url", mysql::getJdbcUrl);
            registry.add("spring.datasource.username", mysql::getUsername);
            registry.add("spring.datasource.password", mysql::getPassword);
            registry.add("spring.data.redis.host", redis::getHost);
            registry.add("spring.data.redis.port", redis::getFirstMappedPort);
        }
    }

    /**
     * Performance test configuration
     */
    @TestConfiguration
    @Profile("performance")
    public static class PerformanceTestConfiguration {

        @Bean
        @Primary
        public RedisConnectionFactory performanceRedisConnectionFactory() {
            LettuceConnectionFactory factory = new LettuceConnectionFactory();
            factory.getPoolConfig().setMaxTotal(100);
            factory.getPoolConfig().setMaxIdle(50);
            factory.getPoolConfig().setMinIdle(10);
            return factory;
        }
    }

    /**
     * Load test configuration
     */
    @TestConfiguration
    @Profile("load")
    public static class LoadTestConfiguration {

        @Bean
        @Primary
        public RedisConnectionFactory loadTestRedisConnectionFactory() {
            LettuceConnectionFactory factory = new LettuceConnectionFactory();
            factory.getPoolConfig().setMaxTotal(200);
            factory.getPoolConfig().setMaxIdle(100);
            factory.getPoolConfig().setMinIdle(20);
            return factory;
        }
    }
}