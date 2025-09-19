package io.kestra.plugin.airtable;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AirtableClientTest {

    @Test
    void shouldCreateClientWithApiKey() {
        AirtableClient client = new AirtableClient("fake-api-key");
        assertThat(client, is(notNullValue()));
    }

    @Test
    void shouldThrowExceptionForInvalidCredentials() {
        // This would test with a mock HTTP client in a real test scenario
        AirtableClient client = new AirtableClient("invalid-key");

        assertThrows(Exception.class, () -> {
            client.listRecords("invalid-base", "invalid-table", null, null, null, null, null);
        });
    }

    @Test
    void shouldValidateRecordCreationLimit() {
        AirtableClient client = new AirtableClient("fake-api-key");

        // Create 11 records (should fail - max 10)
        List<Map<String, Object>> tooManyRecords = List.of(
            Map.of("Name", "Record1"),
            Map.of("Name", "Record2"),
            Map.of("Name", "Record3"),
            Map.of("Name", "Record4"),
            Map.of("Name", "Record5"),
            Map.of("Name", "Record6"),
            Map.of("Name", "Record7"),
            Map.of("Name", "Record8"),
            Map.of("Name", "Record9"),
            Map.of("Name", "Record10"),
            Map.of("Name", "Record11")
        );

        assertThrows(IllegalArgumentException.class, () -> {
            client.createRecords("fake-base", "fake-table", tooManyRecords, false);
        });
    }
}