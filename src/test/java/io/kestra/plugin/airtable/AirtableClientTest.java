package io.kestra.plugin.airtable;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.kestra.core.http.HttpResponse;
import io.kestra.core.http.client.HttpClientResponseException;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;

import jakarta.inject.Inject;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertThrows;

@KestraTest
class AirtableClientTest {

    @Inject
    private RunContextFactory runContextFactory;

    @Test
    void shouldCreateClientWithApiKey() throws Exception {
        RunContext runContext = runContextFactory.of();
        AirtableClient client = new AirtableClient("fake-api-key", runContext);
        assertThat(client, is(notNullValue()));
    }

    @Test
    void shouldThrowExceptionForInvalidCredentials() throws Exception {
        // This would test with a mock HTTP client in a real test scenario
        RunContext runContext = runContextFactory.of();
        AirtableClient client = new AirtableClient("invalid-key", runContext);

        assertThrows(Exception.class, () ->
        {
            client.listRecords("invalid-base", "invalid-table", null, null, null, null, null);
        });
    }

    @Test
    void shouldValidateRecordCreationLimit() throws Exception {
        RunContext runContext = runContextFactory.of();
        AirtableClient client = new AirtableClient("fake-api-key", runContext);

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

        assertThrows(IllegalArgumentException.class, () ->
        {
            client.createRecords("fake-base", "fake-table", tooManyRecords, false);
        });
    }

    @Test
    void shouldEncodeSpaceInTableNameAsPercent20() {
        assertThat(AirtableClient.encodeTableId("Table 1"), is("Table%201"));
        assertThat(AirtableClient.encodeTableId("tblABC123"), is("tblABC123"));
    }

    @Test
    void shouldPercentEncodeReservedCharactersInTableName() {
        assertThat(AirtableClient.encodeTableId("a+b/c%d"), is("a%2Bb%2Fc%25d"));
    }

    @Test
    void shouldDecodeErrorBodyBytesInException() {
        var json = """
            {"error":{"type":"INVALID_PERMISSIONS_OR_MODEL_NOT_FOUND","message":"Invalid permissions, or the requested model was not found."}}""";
        var response = HttpResponse.of(HttpResponse.Status.builder().code(403).build(), json.getBytes(StandardCharsets.UTF_8));

        var exception = AirtableClient.toAirtableException("list records", new HttpClientResponseException("Forbidden", response));

        assertThat(exception.getMessage(), is("Failed to list records: 403 - " + json));
    }
}
