package com.example.rediscluster;

import io.lettuce.core.cluster.ClusterClientOptions;
import io.lettuce.core.cluster.ClusterTopologyRefreshOptions;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisClusterConfiguration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisSentinelConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;

@RequiredArgsConstructor
@Configuration
public class RedisConfig {

    private final MyRedisClusterProperties myRedisClusterProperties;
    private final MyRedisSentinelProperties myRedisSentinelProperties;

    @Value("${spring.data.redis.password}")
    private String password;

//    @Bean
//    public RedisConnectionFactory redisConnectionFactory() {
//        // 1. Topology Refresh 옵션 설정 (핵심)
//        ClusterTopologyRefreshOptions topologyOptions = ClusterTopologyRefreshOptions.builder()
//                .enablePeriodicRefresh(Duration.ofSeconds(30)) // 30초마다 주기적 갱신
//                .enableAllAdaptiveRefreshTriggers()           // MOVED, ASK 등 에러 발생 시 즉시 갱신
//                .build();
//
//        // 2. Client 옵션에 추가
//        ClusterClientOptions clientOptions = ClusterClientOptions.builder()
//                .topologyRefreshOptions(topologyOptions)
//                .build();
//
//        // 3. Lettuce 전용 클라이언트 설정
//        LettuceClientConfiguration clientConfig = LettuceClientConfiguration.builder()
//                .clientOptions(clientOptions)
//                .commandTimeout(Duration.ofSeconds(5))
//                .build();
//
//        // 4. 클러스터 구성 및 팩토리 반환
//        RedisClusterConfiguration clusterConfig = new RedisClusterConfiguration(myRedisClusterProperties.getNodes());
//        clusterConfig.setPassword(RedisPassword.of(password));
//
//        return new LettuceConnectionFactory(clusterConfig, clientConfig);
//    }

//    @Bean
//    public RedisTemplate<String, Object> redisTemplate() {
//        RedisTemplate<String, Object> template = new RedisTemplate<>();
//        template.setConnectionFactory(redisConnectionFactory());
//        template.setKeySerializer(new StringRedisSerializer());
//        template.setValueSerializer(new StringRedisSerializer());
//        return template;
//    }

    // 공통 Lettuce Client 설정 (Topology Refresh 등)
    private LettuceClientConfiguration getClientConfig() {
        ClusterTopologyRefreshOptions topologyOptions = ClusterTopologyRefreshOptions.builder()
                .enableAllAdaptiveRefreshTriggers()
                .enablePeriodicRefresh(Duration.ofSeconds(30))
                .build();

        return LettuceClientConfiguration.builder()
                .clientOptions(ClusterClientOptions.builder()
                        .topologyRefreshOptions(topologyOptions)
                        .build())
                .commandTimeout(Duration.ofSeconds(5))
                .build();
    }

    // 1. Cluster 모드용 ConnectionFactory
    @Bean
    @ConditionalOnProperty(name = "spring.data.redis.mode", havingValue = "cluster")
    public RedisConnectionFactory clusterConnectionFactory() {
        RedisClusterConfiguration config = new RedisClusterConfiguration(
                myRedisClusterProperties.getNodes());
        config.setPassword(RedisPassword.of(password));
        return new LettuceConnectionFactory(config, getClientConfig());
    }

    // 2. Sentinel 모드용 ConnectionFactory
    @Bean
    @ConditionalOnProperty(name = "spring.data.redis.mode", havingValue = "sentinel")
    public RedisConnectionFactory sentinelConnectionFactory() {
        RedisSentinelConfiguration config = new RedisSentinelConfiguration()
                .master(myRedisSentinelProperties.getMaster());

        // YAML에 등록된 Sentinel 노드들을 추가
        myRedisSentinelProperties.getNodes().forEach(node -> {
            String[] parts = node.split(":");
            config.sentinel(parts[0], Integer.parseInt(parts[1]));
        });

        config.setPassword(RedisPassword.of(password));
        return new LettuceConnectionFactory(config, getClientConfig());
    }

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new StringRedisSerializer());
        return template;
    }

}