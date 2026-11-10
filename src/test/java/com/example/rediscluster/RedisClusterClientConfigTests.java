package com.example.rediscluster;

import io.lettuce.core.cluster.ClusterClientOptions;
import io.lettuce.core.cluster.ClusterTopologyRefreshOptions;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class RedisClusterClientConfigTests {

    @Test
    void configuresKubernetesSafeTopologyRefresh() {
        RedisClusterClientConfig configuration = new RedisClusterClientConfig();
        RedisConnectionFactory connectionFactory = configuration.redisConnectionFactory(
                "redis-cluster.redis.svc.cluster.local", 6379, 5);

        assertThat(connectionFactory).isInstanceOf(LettuceConnectionFactory.class);
        LettuceConnectionFactory lettuceFactory = (LettuceConnectionFactory) connectionFactory;
        ClusterClientOptions clientOptions = (ClusterClientOptions) lettuceFactory.getClientConfiguration()
                .getClientOptions()
                .orElseThrow();
        ClusterTopologyRefreshOptions topology = clientOptions.getTopologyRefreshOptions();

        assertThat(topology.isPeriodicRefreshEnabled()).isTrue();
        assertThat(topology.getRefreshPeriod()).isEqualTo(Duration.ofSeconds(10));
        assertThat(topology.getAdaptiveRefreshTriggers()).containsExactlyInAnyOrder(
                ClusterTopologyRefreshOptions.RefreshTrigger.values());
        assertThat(topology.getAdaptiveRefreshTimeout()).isEqualTo(Duration.ofSeconds(5));
        assertThat(topology.getRefreshTriggersReconnectAttempts()).isEqualTo(2);
        assertThat(topology.useDynamicRefreshSources()).isFalse();
        assertThat(topology.isCloseStaleConnections()).isTrue();
        assertThat(clientOptions.isValidateClusterNodeMembership()).isFalse();
        assertThat(clientOptions.isAutoReconnect()).isTrue();
        assertThat(clientOptions.getMaxRedirects()).isEqualTo(5);
    }
}
