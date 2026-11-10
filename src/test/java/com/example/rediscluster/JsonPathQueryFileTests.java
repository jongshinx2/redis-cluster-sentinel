package com.example.rediscluster;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class JsonPathQueryFileTests {

    @TempDir
    Path temporaryDirectory;

    @Test
    void generatesTheNestedQueryAsAFile() throws Exception {
        Path path = temporaryDirectory.resolve("jsonpath-query.txt");
        JsonPathQueryFile queryFile = new JsonPathQueryFile(path.toString());

        queryFile.initialize();

        assertThat(path).exists();
        assertThat(Files.size(path)).isEqualTo(FixedSizeJsonPathFactory.QUERY_BYTES);
        assertThat(queryFile.read()).isEqualTo(FixedSizeJsonPathFactory.create());
    }
}
