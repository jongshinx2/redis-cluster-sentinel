package com.example.rediscluster;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.RedisClusterConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class RedisDatasetService {

    private static final DefaultRedisScript<Long> CREATE_LIST_ONCE_SCRIPT = new DefaultRedisScript<>("""
            if redis.call('EXISTS', KEYS[1]) == 1 then
                return 0
            end
            redis.call('RPUSH', KEYS[1], unpack(ARGV))
            return #ARGV
            """, Long.class);

    private final StringRedisTemplate redisTemplate;
    private final FixedSizeDatasetFactory datasetFactory;
    private final JsonPathQueryFile jsonPathQueryFile;
    private final RedisConnectionFactory connectionFactory;

    @Value("${app.redis.dataset-key}")
    private String datasetKey;

    @Value("${app.redis.json-dataset-key}")
    private String jsonDatasetKey;

    public DatasetWriteResult createOnce() {
        List<String> elements = datasetFactory.createElements();
        long startedAt = System.nanoTime();
        Long inserted = redisTemplate.execute(CREATE_LIST_ONCE_SCRIPT, List.of(datasetKey), elements.toArray());
        boolean created = inserted != null && inserted == FixedSizeDatasetFactory.ITEM_COUNT;
        long elapsedMicros = elapsedMicros(startedAt);
        log.info("redis_rpush key={} created={} items={} jsonBytes={} elapsedMicros={}",
                datasetKey, created, FixedSizeDatasetFactory.ITEM_COUNT,
                FixedSizeDatasetFactory.TARGET_BYTES, elapsedMicros);
        return new DatasetWriteResult(created, datasetKey, FixedSizeDatasetFactory.ITEM_COUNT,
                FixedSizeDatasetFactory.TARGET_BYTES, elapsedMicros);
    }

    public DatasetWriteResult createJsonOnce() {
        String dataset = datasetFactory.create();
        long startedAt = System.nanoTime();
        Object response;
        try (RedisClusterConnection connection = connectionFactory.getClusterConnection()) {
            response = connection.execute("JSON.SET", utf8(jsonDatasetKey), List.of(
                    utf8("$"), utf8(dataset), utf8("NX")));
        }
        boolean created = response != null;
        long elapsedMicros = elapsedMicros(startedAt);
        log.info("redis_json_set key={} created={} items={} jsonBytes={} elapsedMicros={}",
                jsonDatasetKey, created, FixedSizeDatasetFactory.ITEM_COUNT,
                FixedSizeDatasetFactory.TARGET_BYTES, elapsedMicros);
        return new DatasetWriteResult(created, jsonDatasetKey, FixedSizeDatasetFactory.ITEM_COUNT,
                FixedSizeDatasetFactory.TARGET_BYTES, elapsedMicros);
    }

    public String get() {
        long startedAt = System.nanoTime();
        List<String> elements = redisTemplate.opsForList().range(datasetKey, 0, -1);
        long elapsedMicros = elapsedMicros(startedAt);
        if (elements == null || elements.isEmpty()) {
            log.warn("redis_lrange key={} found=false elapsedMicros={}", datasetKey, elapsedMicros);
            throw new DatasetNotFoundException(datasetKey);
        }
        String value = datasetFactory.toJson(elements);
        log.info("redis_lrange key={} found=true items={} jsonBytes={} elapsedMicros={}",
                datasetKey, elements.size(), utf8Size(value), elapsedMicros);
        return value;
    }

    public String query() {
        long startedAt = System.nanoTime();
        String path = jsonPathQueryFile.read();
        int queryBytes = utf8Size(path);
        log.info("redis_jsonpath_start key={} queryFile={} queryBytes={} nestingDepth={}",
                jsonDatasetKey, jsonPathQueryFile.path().toAbsolutePath(), queryBytes,
                FixedSizeJsonPathFactory.NESTING_DEPTH);
        Object response;
        try (RedisClusterConnection connection = connectionFactory.getClusterConnection()) {
            response = connection.execute("JSON.GET", utf8(jsonDatasetKey), List.of(utf8(path)));
        }
        if (response == null) {
            throw new DatasetNotFoundException(jsonDatasetKey);
        }
        if (!(response instanceof byte[] bytes)) {
            throw new IllegalStateException("Unexpected JSON.GET response type: " + response.getClass().getName());
        }
        String json = new String(bytes, StandardCharsets.UTF_8);
        long elapsedMicros = elapsedMicros(startedAt);
        log.info("redis_json_get key={} queryBytes={} responseBytes={} elapsedMicros={}",
                jsonDatasetKey, queryBytes, utf8Size(json), elapsedMicros);
        return json;
    }

    private byte[] utf8(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private int utf8Size(String value) {
        return value.getBytes(StandardCharsets.UTF_8).length;
    }

    private long elapsedMicros(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000;
    }

    public record DatasetWriteResult(boolean created, String key, int itemCount, int bytes, long elapsedMicros) {
    }
}
