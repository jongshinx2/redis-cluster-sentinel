package com.example.rediscluster;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConfigurationProperties(prefix = "spring.data.redis.sentinel")
@Data
public class MyRedisSentinelProperties {
    private String master;
    private List<String> nodes;
}
