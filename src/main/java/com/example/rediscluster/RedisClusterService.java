package com.example.rediscluster;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@RequiredArgsConstructor
@Service
public class RedisClusterService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final RedisConnectionFactory connectionFactory;
    private volatile boolean isTesting = false;

    public void startTest() {
        if (isTesting) return;
        isTesting = true;

        new Thread(() -> {
            int count = 0;
            while (isTesting) {
                if (!isFactoryRunning()) break;

                try {
                    String key = "test:key:" + count;
                    redisTemplate.opsForValue().set(key, "data_" + System.currentTimeMillis());
                    log.info("[SUCCESS] Key: {} 입력 완료", key);
                    count++;
                } catch (Exception e) {
                    log.error("[ERROR] 장애 발생: {} | 타입: {}", e.getMessage(), e.getClass().getSimpleName());
                    sleep(2000);
                }
                sleep(500);
            }
        }).start();
    }

    public void stopTest() {
        this.isTesting = false;
    }

    private boolean isFactoryRunning() {
        if (connectionFactory instanceof LettuceConnectionFactory lettuceFactory) {
            if (!lettuceFactory.isRunning()) {
                log.error("LettuceConnectionFactory가 중단되었습니다.");
                return false;
            }
        }
        return true;
    }

    private void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) {}
    }
}
