package com.ktb.chatapp.config;

import lombok.extern.slf4j.Slf4j;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;

@Slf4j
@Configuration
public class RedisConfig {

    @Value("${redis.cluster.nodes}")
    private String clusterNodes;

    @Value("${redis.password:#{null}}")
    private String password;

    @Bean(destroyMethod = "shutdown")
    @ConditionalOnProperty(name = "socketio.enabled", havingValue = "true", matchIfMissing = true)
    public RedissonClient redissonClient() {
        Config config = new Config();

        log.info("Configuring Redisson for Redis Cluster mode");
        String[] nodes = clusterNodes.split(",");
        String[] redisUrls = Arrays.stream(nodes)
                .map(node -> "redis://" + node.trim())
                .toArray(String[]::new);

        config.useClusterServers()
                .addNodeAddress(redisUrls)
                .setPassword(password)
                .setScanInterval(2000)
                .setConnectTimeout(10000)
                .setTimeout(3000)
                .setRetryAttempts(3)
                .setRetryInterval(1500)
                .setMasterConnectionMinimumIdleSize(2)
                .setMasterConnectionPoolSize(4)
                .setSlaveConnectionMinimumIdleSize(2)
                .setSlaveConnectionPoolSize(4)
                .setReadMode(org.redisson.config.ReadMode.SLAVE)
                .setSubscriptionMode(org.redisson.config.SubscriptionMode.MASTER);

        log.info("Redis Cluster nodes: {}", Arrays.toString(redisUrls));

        RedissonClient redissonClient = Redisson.create(config);
        log.info("Redisson client initialized successfully");
        return redissonClient;
    }
}
