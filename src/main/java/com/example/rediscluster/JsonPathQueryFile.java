package com.example.rediscluster;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

@Slf4j
@Component
public class JsonPathQueryFile {

    private final Path queryFile;

    public JsonPathQueryFile(@Value("${app.jsonpath.query-file}") String queryFile) {
        this.queryFile = Path.of(queryFile);
    }

    @PostConstruct
    public void initialize() {
        try {
            Path absoluteFile = queryFile.toAbsolutePath();
            Path parent = absoluteFile.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }

            Path temporaryFile = Files.createTempFile(parent, "jsonpath-query-", ".tmp");
            Files.writeString(temporaryFile, FixedSizeJsonPathFactory.create(), StandardCharsets.UTF_8);
            moveIntoPlace(temporaryFile, absoluteFile);

            log.info("Generated nested JSONPath query file={} nestingDepth={} bytes={}",
                    absoluteFile, FixedSizeJsonPathFactory.NESTING_DEPTH, Files.size(absoluteFile));
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to generate JSONPath query file: " + queryFile, exception);
        }
    }

    public String read() {
        try {
            return Files.readString(queryFile, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read JSONPath query file: " + queryFile, exception);
        }
    }

    public Path path() {
        return queryFile;
    }

    private void moveIntoPlace(Path temporaryFile, Path absoluteFile) throws IOException {
        try {
            Files.move(temporaryFile, absoluteFile,
                    StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(temporaryFile, absoluteFile, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
