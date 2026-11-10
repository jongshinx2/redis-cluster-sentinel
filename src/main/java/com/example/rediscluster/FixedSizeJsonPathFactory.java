package com.example.rediscluster;

import java.nio.charset.StandardCharsets;

public final class FixedSizeJsonPathFactory {

    public static final int TARGET_BYTES = 1_400_000;
    private static final String PREFIX = "$[?(@.id >= 0";
    private static final String SUFFIX = ")]";

    private FixedSizeJsonPathFactory() {
    }

    public static String create() {
        int paddingBytes = TARGET_BYTES - PREFIX.length() - SUFFIX.length();
        String path = PREFIX + " ".repeat(paddingBytes) + SUFFIX;
        int actualBytes = path.getBytes(StandardCharsets.UTF_8).length;
        if (actualBytes != TARGET_BYTES) {
            throw new IllegalStateException("Expected " + TARGET_BYTES + " query bytes, generated " + actualBytes);
        }
        return path;
    }
}
