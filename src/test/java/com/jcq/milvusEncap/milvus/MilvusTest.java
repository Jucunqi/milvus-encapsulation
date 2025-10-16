package com.jcq.milvusEncap.milvus;

import io.milvus.pool.MilvusClientV2Pool;
import io.milvus.pool.PoolConfig;
import io.milvus.v2.client.ConnectConfig;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.service.collection.response.ListCollectionsResp;
import org.junit.jupiter.api.Test;

import java.time.Duration;

public class MilvusTest {

    @Test
    public void getClientTest() {

        try {
            // 创建连接参数
            ConnectConfig connectConfig = ConnectConfig.builder()
                    .uri("url")
                    .token("token")
                    .build();

            // 配置连接池参数
            PoolConfig poolConfig = PoolConfig.builder()
                    .maxIdlePerKey(20)
                    .maxTotalPerKey(50)
                    .maxTotal(100)
                    .maxBlockWaitDuration(Duration.ofSeconds(60))
                    .minEvictableIdleDuration(Duration.ofSeconds(120))
                    .build();

            MilvusClientV2Pool pool = new MilvusClientV2Pool(poolConfig, connectConfig);
            MilvusClientV2 client = pool.getClient("test");

            ListCollectionsResp listAliasResp = client.listCollections();
            System.out.println(listAliasResp);

        } catch (ClassNotFoundException | NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }
}
