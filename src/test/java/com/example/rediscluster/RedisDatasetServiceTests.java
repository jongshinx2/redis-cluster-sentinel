package com.example.rediscluster;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class RedisDatasetServiceTests {

    @Test
    void getUsesLrangeAndReconstructsTheExactJsonDocument() {
        String key = "performance:dataset:list:1_4mb";
        FixedSizeDatasetFactory factory = new FixedSizeDatasetFactory();
        List<String> elements = factory.createElements();
        AtomicBoolean rangeCalled = new AtomicBoolean();
        @SuppressWarnings("unchecked")
        ListOperations<String, String> listOperations = (ListOperations<String, String>) Proxy.newProxyInstance(
                ListOperations.class.getClassLoader(),
                new Class<?>[]{ListOperations.class},
                (proxy, method, arguments) -> {
                    if (method.getName().equals("range")) {
                        assertThat(arguments).containsExactly(key, 0L, -1L);
                        rangeCalled.set(true);
                        return elements;
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
        StringRedisTemplate redisTemplate = new StringRedisTemplate() {
            @Override
            public ListOperations<String, String> opsForList() {
                return listOperations;
            }
        };

        RedisDatasetService service = new RedisDatasetService(redisTemplate, factory);
        ReflectionTestUtils.setField(service, "datasetKey", key);

        String result = service.get();

        assertThat(rangeCalled).isTrue();
        assertThat(result).isEqualTo(factory.create());
        assertThat(result.getBytes(StandardCharsets.UTF_8)).hasSize(FixedSizeDatasetFactory.TARGET_BYTES);
    }
}
