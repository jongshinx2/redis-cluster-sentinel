package com.example.rediscluster;

public final class FixedSizeJsonPathFactory {

    public static final int TARGET_BYTES = 2_500_000;
    public static final int NESTING_DEPTH = 1_249_992;
    public static final String PREFIX = "$[?(";
    public static final String PREDICATE = "@.id >= 0 ";
    public static final String SUFFIX = ")]";
    public static final int QUERY_BYTES = PREFIX.length() + NESTING_DEPTH
            + PREDICATE.length() + NESTING_DEPTH + SUFFIX.length();

    private FixedSizeJsonPathFactory() {
    }

    public static String create() {
        String query = PREFIX
                + "(".repeat(NESTING_DEPTH)
                + PREDICATE
                + ")".repeat(NESTING_DEPTH)
                + SUFFIX;
        if (query.length() != TARGET_BYTES) {
            throw new IllegalStateException("Expected " + TARGET_BYTES + " query bytes, generated " + query.length());
        }
        return query;
    }
}
