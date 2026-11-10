package com.example.rediscluster;

import com.jayway.jsonpath.JsonPath;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;

@Slf4j
@Service
@RequiredArgsConstructor
public class RedisDatasetService {

    private final StringRedisTemplate redisTemplate;
    private final FixedSizeDatasetFactory datasetFactory;

    @Value("${app.redis.dataset-key}")
    private String datasetKey;

    public DatasetWriteResult createOnce() {
        String dataset = datasetFactory.create();
        long startedAt = System.nanoTime();
        boolean created = Boolean.TRUE.equals(redisTemplate.opsForValue().setIfAbsent(datasetKey, dataset));
        long elapsedMicros = elapsedMicros(startedAt);
        log.info("redis_set key={} created={} items={} bytes={} elapsedMicros={}",
                datasetKey, created, FixedSizeDatasetFactory.ITEM_COUNT,
                FixedSizeDatasetFactory.TARGET_BYTES, elapsedMicros);
        return new DatasetWriteResult(created, datasetKey, FixedSizeDatasetFactory.ITEM_COUNT,
                FixedSizeDatasetFactory.TARGET_BYTES, elapsedMicros);
    }

    public String get() {
        long startedAt = System.nanoTime();
        String value = redisTemplate.opsForValue().get(datasetKey);
        long elapsedMicros = elapsedMicros(startedAt);
        if (value == null) {
            log.warn("redis_get key={} found=false elapsedMicros={}", datasetKey, elapsedMicros);
            throw new DatasetNotFoundException(datasetKey);
        }
        log.info("redis_get key={} found=true bytes={} elapsedMicros={}", datasetKey, utf8Size(value), elapsedMicros);
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
