package com.example.rediscluster;

import io.lettuce.core.SocketOptions;
import io.lettuce.core.cluster.ClusterClientOptions;
import io.lettuce.core.cluster.ClusterTopologyRefreshOptions;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisClusterConfiguration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisNode;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;

@Slf4j
@Configuration
public class RedisClusterClientConfig {

    private static final Duration PERIODIC_REFRESH_INTERVAL = Duration.ofSeconds(10);
    private static final Duration ADAPTIVE_REFRESH_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration COMMAND_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration SHUTDOWN_TIMEOUT = Duration.ofMillis(200);

    @Bean
    public RedisConnectionFactory redisConnectionFactory(
            @Value("${app.redis.cluster.seed-host}") String seedHost,
            @Value("${app.redis.cluster.seed-port}") int seedPort,
            @Value("${app.redis.cluster.max-redirects}") int maxRedirects) {

        RedisClusterConfiguration clusterConfiguration = new RedisClusterConfiguration();
        clusterConfiguration.addClusterNode(new RedisNode(seedHost, seedPort));
        clusterConfiguration.setMaxRedirects(maxRedirects);

        ClusterTopologyRefreshOptions topologyRefreshOptions = ClusterTopologyRefreshOptions.builder()
                .enablePeriodicRefresh(PERIODIC_REFRESH_INTERVAL)
                .enableAllAdaptiveRefreshTriggers()
                .adaptiveRefreshTriggersTimeout(ADAPTIVE_REFRESH_TIMEOUT)
                .refreshTriggersReconnectAttempts(2)
                // Always ask the stable Kubernetes Service seed instead of retaining old Pod IPs as refresh sources.
                .dynamicRefreshSources(false)
                // Connections for nodes removed from the refreshed topology must not keep reconnecting to old Pod IPs.
                .closeStaleConnections(true)
                .build();

        ClusterClientOptions clientOptions = ClusterClientOptions.builder()
                .topologyRefreshOptions(topologyRefreshOptions)
                .autoReconnect(true)
                .pingBeforeActivateConnection(true)
                // A restarted Redis Pod can appear at a new address before the local topology has caught up.
                // This is safe only because Redis is reachable solely through the trusted in-cluster network.
                .validateClusterNodeMembership(false)
                .maxRedirects(maxRedirects)
                .socketOptions(SocketOptions.builder()
                        .connectTimeout(CONNECT_TIMEOUT)
                        .keepAlive(true)
                        .build())
                .build();

        LettuceClientConfiguration clientConfiguration = LettuceClientConfiguration.builder()
                .clientOptions(clientOptions)
                .commandTimeout(COMMAND_TIMEOUT)
                .shutdownTimeout(SHUTDOWN_TIMEOUT)
                .build();

        log.info("Configuring Redis Cluster seed={}:{} periodicRefresh={} adaptiveRefreshTimeout={} "
                        + "dynamicRefreshSources=false closeStaleConnections=true",
                seedHost, seedPort, PERIODIC_REFRESH_INTERVAL, ADAPTIVE_REFRESH_TIMEOUT);

        return new LettuceConnectionFactory(clusterConfiguration, clientConfiguration);
    }

    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory connectionFactory) {
        return new StringRedisTemplate(connectionFactory);
    }
}
