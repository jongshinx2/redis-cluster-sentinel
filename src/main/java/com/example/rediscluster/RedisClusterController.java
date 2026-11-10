package com.example.rediscluster;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/redis")
public class RedisClusterController {

    private final RedisDatasetService datasetService;

    @PostMapping("/dataset")
    public ResponseEntity<RedisDatasetService.DatasetWriteResult> createDataset() {
        RedisDatasetService.DatasetWriteResult result = datasetService.createOnce();
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.CONFLICT).body(result);
    }

    @PostMapping("/json-dataset")
    public ResponseEntity<RedisDatasetService.DatasetWriteResult> createJsonDataset() {
        RedisDatasetService.DatasetWriteResult result = datasetService.createJsonOnce();
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.CONFLICT).body(result);
    }

    @GetMapping(value = "/dataset", produces = MediaType.APPLICATION_JSON_VALUE)
    public String getDataset() {
        return datasetService.get();
    }

    @PostMapping(value = "/dataset/query", produces = MediaType.APPLICATION_JSON_VALUE)
    public String queryDataset() {
        return datasetService.query();
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "UP");
    }

    @ExceptionHandler(DatasetNotFoundException.class)
    public ResponseEntity<Map<String, String>> notFound(DatasetNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", exception.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> invalidQuerySize(IllegalArgumentException exception) {
        return ResponseEntity.badRequest().body(Map.of("error", exception.getMessage()));
    }
}
