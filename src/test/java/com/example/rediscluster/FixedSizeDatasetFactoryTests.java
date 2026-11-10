package com.example.rediscluster;

import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Configuration;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FixedSizeDatasetFactoryTests {

    private final FixedSizeDatasetFactory factory = new FixedSizeDatasetFactory();

    @Test
    void createsExactlyFourThousandSixHundredItemsAndOnePointFourMillionBytes() {
        List<String> elements = factory.createElements();
        String dataset = factory.toJson(elements);
        List<?> items = JsonPath.read(dataset, "$[*]");

        assertThat(elements).hasSize(FixedSizeDatasetFactory.ITEM_COUNT);
        assertThat(items).hasSize(FixedSizeDatasetFactory.ITEM_COUNT);
        assertThat(dataset.getBytes(StandardCharsets.UTF_8)).hasSize(FixedSizeDatasetFactory.TARGET_BYTES);

        String jsonPathResult = Configuration.defaultConfiguration().jsonProvider().toJson(items);
        assertThat(jsonPathResult.getBytes(StandardCharsets.UTF_8)).hasSize(FixedSizeDatasetFactory.TARGET_BYTES);
    }

    @Test
    void createsTheServerSideDeeplyNestedJsonPathQuery() {
        String query = FixedSizeJsonPathFactory.create();

        assertThat(query.getBytes(StandardCharsets.UTF_8)).hasSize(FixedSizeJsonPathFactory.QUERY_BYTES);
        assertThat(FixedSizeJsonPathFactory.QUERY_BYTES).isEqualTo(1_400_015);
        assertThat(query).startsWith("$[?(((");
        assertThat(query).contains("@.id >= 0");
        assertThat(query).endsWith(")))]");
    }
}
