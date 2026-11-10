package com.example.rediscluster;

import java.nio.charset.StandardCharsets;

public final class FixedSizeJsonPathFactory {

    public static final int TARGET_BYTES = 3_000_000;
    private static final String PREFIX = "$[?(@.id >= 0 || @.value == '";
    private static final String SUFFIX = "')]";

    private FixedSizeJsonPathFactory() {
    }

    public static String create() {
        int dummyValueBytes = TARGET_BYTES - PREFIX.length() - SUFFIX.length();
        String path = PREFIX + "q".repeat(dummyValueBytes) + SUFFIX;
        int actualBytes = path.getBytes(StandardCharsets.UTF_8).length;
        if (actualBytes != TARGET_BYTES) {
            throw new IllegalStateException("Expected " + TARGET_BYTES + " query bytes, generated " + actualBytes);
        }
        return path;
    }
}
