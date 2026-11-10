package com.example.rediscluster;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Component
public class FixedSizeDatasetFactory {

    public static final int ITEM_COUNT = 4_600;
    public static final int TARGET_BYTES = 1_400_000;

    public String create() {
        int baseBytes = 2 + ITEM_COUNT - 1;
        for (int id = 0; id < ITEM_COUNT; id++) {
            baseBytes += itemPrefix(id).length() + 2;
        }

        int payloadBytes = TARGET_BYTES - baseBytes;
        if (payloadBytes < 0) {
            throw new IllegalStateException("Dataset metadata exceeds target size");
        }

        int payloadPerItem = payloadBytes / ITEM_COUNT;
        int remainder = payloadBytes % ITEM_COUNT;
        StringBuilder json = new StringBuilder(TARGET_BYTES);
        json.append('[');
        for (int id = 0; id < ITEM_COUNT; id++) {
            if (id > 0) {
                json.append(',');
            }
            json.append(itemPrefix(id));
            json.append("x".repeat(payloadPerItem + (id < remainder ? 1 : 0)));
            json.append("\"}");
        }
        json.append(']');

        int actualBytes = json.toString().getBytes(StandardCharsets.UTF_8).length;
        if (actualBytes != TARGET_BYTES) {
            throw new IllegalStateException("Expected " + TARGET_BYTES + " bytes, generated " + actualBytes);
        }
        return json.toString();
    }

    private String itemPrefix(int id) {
        return "{\"id\":" + id + ",\"value\":\"";
    }
}
