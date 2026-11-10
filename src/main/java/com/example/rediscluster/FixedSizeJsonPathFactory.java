package com.example.rediscluster;

public final class FixedSizeJsonPathFactory {

    public static final int NESTING_DEPTH = 700_000;
    public static final String PREFIX = "$[?(";
    public static final String PREDICATE = "@.id >= 0";
    public static final String SUFFIX = ")]";
    public static final int QUERY_BYTES = PREFIX.length() + NESTING_DEPTH
            + PREDICATE.length() + NESTING_DEPTH + SUFFIX.length();

    private FixedSizeJsonPathFactory() {
    }

    public static String create() {
        return PREFIX
                + "(".repeat(NESTING_DEPTH)
                + PREDICATE
                + ")".repeat(NESTING_DEPTH)
                + SUFFIX;
    }
}
