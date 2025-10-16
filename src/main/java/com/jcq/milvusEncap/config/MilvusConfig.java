package com.jcq.milvusEncap.config;

import io.milvus.pool.MilvusClientV2Pool;
import io.milvus.pool.PoolConfig;
import io.milvus.v2.client.ConnectConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * 定义Milvus连接池
 *
 * @author : jucunqi
 * @since : 2025/10/16
 */
@Configuration
public class MilvusConfig {


    @Value("${milvus.host:localhost}")
    private String host;

    @Value("${milvus.port:19530}")
    private int port;

    @Value("${milvus.token}")
    private String token;

    @Value("${milvus.pool.max-idle-per-key:10}")
    private int maxIdlePerKey;

    @Value("${milvus.pool.max-total-per-key:20}")
    private int maxTotalPerKey;

    @Value("${milvus.pool.max-total:100}")
    private int maxTotal;

    @Value("${milvus.pool.wait-duration:5}")
    private long waitDuration;

    @Value("${milvus.pool.evictable-duration:30}")
    private long evictableDuration;

    /**
     * 初始化Milvus连接池
     */
    @Bean(destroyMethod = "close")
    public MilvusClientV2Pool milvusClientPool() {
        try {
            // 创建连接参数
            ConnectConfig connectConfig = ConnectConfig.builder()
                    .uri(host + ":" + port)
                    .token(token)
                    .build();

            // 配置连接池参数
            PoolConfig poolConfig = PoolConfig.builder()
                    .maxIdlePerKey(maxIdlePerKey)
                    .maxTotalPerKey(maxTotalPerKey)
                    .maxTotal(maxTotal)
                    .maxBlockWaitDuration(Duration.ofSeconds(waitDuration))
                    .minEvictableIdleDuration(Duration.ofSeconds(evictableDuration))
                    .build();

            return new MilvusClientV2Pool(poolConfig, connectConfig);
        } catch (ClassNotFoundException | NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }
}
