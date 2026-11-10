package com.example.rediscluster;

import com.jayway.jsonpath.JsonPath;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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

    @Value("${app.redis.dataset-key}")
    private String datasetKey;

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

    public String query(String path) {
        int queryBytes = utf8Size(path);
        if (queryBytes != FixedSizeJsonPathFactory.TARGET_BYTES) {
            throw new IllegalArgumentException("JSONPath query must be exactly "
                    + FixedSizeJsonPathFactory.TARGET_BYTES + " UTF-8 bytes; actual=" + queryBytes);
        }
        long startedAt = System.nanoTime();
        String source = get();
        Object result = JsonPath.parse(source).read(path);
        String json = com.jayway.jsonpath.Configuration.defaultConfiguration().jsonProvider().toJson(result);
        long elapsedMicros = elapsedMicros(startedAt);
        log.info("redis_jsonpath key={} queryBytes={} sourceBytes={} responseBytes={} elapsedMicros={}",
                datasetKey, queryBytes, utf8Size(source), utf8Size(json), elapsedMicros);
        return json;
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
