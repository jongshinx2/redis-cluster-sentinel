package com.example.rediscluster;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConfigurationProperties(prefix = "spring.data.redis.cluster")
@Data
public class MyRedisClusterProperties {
    private List<String> nodes;
}
