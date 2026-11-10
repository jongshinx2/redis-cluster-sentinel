package com.example.rediscluster;

public class DatasetNotFoundException extends RuntimeException {
    public DatasetNotFoundException(String key) {
        super("Redis dataset does not exist: " + key);
    }
}
