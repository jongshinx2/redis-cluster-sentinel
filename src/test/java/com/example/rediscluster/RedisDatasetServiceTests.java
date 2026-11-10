package com.example.rediscluster;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.data.redis.connection.RedisClusterConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class RedisDatasetServiceTests {

    @TempDir
    Path temporaryDirectory;

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

        RedisDatasetService service = new RedisDatasetService(redisTemplate, factory, null, null);
        ReflectionTestUtils.setField(service, "datasetKey", key);

        String result = service.get();

        assertThat(rangeCalled).isTrue();
        assertThat(result).isEqualTo(factory.create());
        assertThat(result.getBytes(StandardCharsets.UTF_8)).hasSize(FixedSizeDatasetFactory.TARGET_BYTES);
    }

    @Test
    void querySendsTheServerSideFileDirectlyToRedisJsonGet() {
        String jsonKey = "performance:dataset:json:1_4mb";
        JsonPathQueryFile queryFile = new JsonPathQueryFile(
                temporaryDirectory.resolve("jsonpath-query.txt").toString());
        queryFile.initialize();
        AtomicBoolean jsonGetCalled = new AtomicBoolean();

        RedisClusterConnection clusterConnection = (RedisClusterConnection) Proxy.newProxyInstance(
                RedisClusterConnection.class.getClassLoader(),
                new Class<?>[]{RedisClusterConnection.class},
                (proxy, method, arguments) -> {
                    if (method.getName().equals("execute") && arguments.length == 3) {
                        assertThat(arguments[0]).isEqualTo("JSON.GET");
                        assertThat((byte[]) arguments[1]).isEqualTo(jsonKey.getBytes(StandardCharsets.UTF_8));
                        @SuppressWarnings("unchecked")
                        Collection<byte[]> commandArguments = (Collection<byte[]>) arguments[2];
                        assertThat(commandArguments).hasSize(1);
                        assertThat(commandArguments.iterator().next())
                                .isEqualTo(queryFile.read().getBytes(StandardCharsets.UTF_8));
                        jsonGetCalled.set(true);
                        return "[]".getBytes(StandardCharsets.UTF_8);
                    }
                    if (method.getName().equals("close")) {
                        return null;
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
        RedisConnectionFactory connectionFactory = (RedisConnectionFactory) Proxy.newProxyInstance(
                RedisConnectionFactory.class.getClassLoader(),
                new Class<?>[]{RedisConnectionFactory.class},
                (proxy, method, arguments) -> {
                    if (method.getName().equals("getClusterConnection")) {
                        return clusterConnection;
                    }
                    throw new UnsupportedOperationException(method.getName());
                });

        RedisDatasetService service = new RedisDatasetService(
                new StringRedisTemplate(), new FixedSizeDatasetFactory(), queryFile, connectionFactory);
        ReflectionTestUtils.setField(service, "jsonDatasetKey", jsonKey);

        String result = service.query();

        assertThat(jsonGetCalled).isTrue();
        assertThat(result).isEqualTo("[]");
    }
}
