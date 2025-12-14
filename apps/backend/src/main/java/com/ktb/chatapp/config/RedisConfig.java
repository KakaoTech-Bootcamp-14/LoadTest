package com.ktb.chatapp.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.codec.JsonJacksonCodec;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Redis Cluster configuration using Redisson client.
 * Supports multi-node deployments with distributed Socket.IO storage.
 */
@Slf4j
@Configuration
public class RedisConfig {

    @Value("${REDIS_CLUSTER_NODES}")
    private String clusterNodes;

    @Value("${REDIS_PASSWORD}")
    private String password;

    @Bean(destroyMethod = "shutdown")
    public RedissonClient redissonClient() {
        Config config = new Config();

        // Parse cluster nodes from comma-separated string
        String[] nodes = clusterNodes.split(",");
        String[] redisUrls = new String[nodes.length];

        for (int i = 0; i < nodes.length; i++) {
            // Add redis:// protocol if not present
            String node = nodes[i].trim();
            redisUrls[i] = node.startsWith("redis://") ? node : "redis://" + node;
        }

        // Configure Redis Cluster
        config.useClusterServers()
                .addNodeAddress(redisUrls)
                .setPassword(password)
                .setScanInterval(2000) // Cluster scan interval in milliseconds
                .setRetryAttempts(3)
                .setRetryInterval(1500) // Retry interval in milliseconds
                .setTimeout(3000) // Command timeout
                .setConnectTimeout(3000) // Connection timeout
                .setIdleConnectionTimeout(10000) // Idle connection timeout
                .setPingConnectionInterval(30000) // Ping interval to keep connection alive
                .setMasterConnectionMinimumIdleSize(5) // Minimum idle connections per master
                .setMasterConnectionPoolSize(10) // Maximum connections per master
                .setSlaveConnectionMinimumIdleSize(5) // Minimum idle connections per slave
                .setSlaveConnectionPoolSize(10) // Maximum connections per slave
                .setReadMode(org.redisson.config.ReadMode.SLAVE) // Read from slaves when possible
                .setSubscriptionMode(org.redisson.config.SubscriptionMode.SLAVE); // Subscribe from slaves

        // Configure JSON codec with JavaTimeModule for LocalDateTime support
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        config.setCodec(new JsonJacksonCodec(objectMapper));

        log.info("Redis Cluster configured with {} nodes", nodes.length);
        for (String url : redisUrls) {
            log.info("  - {}", url);
        }

        return Redisson.create(config);
    }
}
