package io.kestra.plugin.airtable;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.kestra.core.http.HttpRequest;
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
    void shouldKeepEncodedTableNameInRequestPath() throws Exception {
        var runContext = runContextFactory.of();

        var spaced = HttpRequest.of(URI.create(AirtableClient.tableUrl("appTEST", "Table 1"))).to(runContext);
        var reserved = HttpRequest.of(URI.create(AirtableClient.tableUrl("appTEST", "a+b/c%d"))).to(runContext);

        assertThat(spaced.getPath(), is("/v0/appTEST/Table%201"));
        assertThat(reserved.getPath(), is("/v0/appTEST/a%2Bb%2Fc%25d"));
    }

    @Test
    void shouldDecodeErrorBodyBytesInException() {
        var json = """
            {"error":{"type":"INVALID_PERMISSIONS_OR_MODEL_NOT_FOUND","message":"Invalid permissions, or the requested model was not found."}}""";
        var response = HttpResponse.of(HttpResponse.Status.builder().code(403).build(), json.getBytes(StandardCharsets.UTF_8));

        var exception = AirtableClient.toAirtableException("list records", new HttpClientResponseException("Forbidden", response));

        assertThat(exception.getMessage(), is("Failed to list records: 403 - " + json));
    }

    @Test
    void shouldParseRecordsFromListResponse() throws Exception {
        var client = new AirtableClient("fake-api-key", runContextFactory.of());
        var body = """
            {"records":[{"id":"rec1","createdTime":"2026-10-23T17:00:00.000Z","fields":{"Name":"First"}}],"offset":"itr1"}""";

        var response = client.parseListResponse(body);

        assertThat(response.getRecords(), hasSize(1));
        assertThat(response.getRecords().getFirst().getId(), is("rec1"));
        assertThat(response.getRecords().getFirst().getFields(), hasEntry("Name", (Object) "First"));
        assertThat(response.getOffset(), is("itr1"));
    }

    @Test
    void shouldFilterFieldsSafelyWhenFieldHasNullValue() throws Exception {
        var runContext = runContextFactory.of();
        var client = new AirtableClient("fake-api-key", runContext);

        var fields = new HashMap<String, Object>();
        fields.put("Task Name", "Test Task");
        fields.put("Due Date", null);

        var rec = new AirtableRecord("rec123", "2024-01-01T00:00:00.000Z", fields);
        var filtered = client.filterRecordFields(rec, List.of("Task Name", "Due Date"));

        assertThat(filtered.getFields(), is(notNullValue()));
        assertThat(filtered.getFields().get("Task Name"), is("Test Task"));
        assertThat(filtered.getFields().containsKey("Due Date"), is(true));
        assertThat(filtered.getFields().get("Due Date"), is(nullValue()));
    }

    @Test
    void shouldFilterFieldsSafelyWhenRecordHasNoFields() throws Exception {
        var runContext = runContextFactory.of();
        var client = new AirtableClient("fake-api-key", runContext);

        var rec = new AirtableRecord("rec123", "2024-01-01T00:00:00.000Z", null);
        var filtered = client.filterRecordFields(rec, List.of("Task Name", "Due Date"));

        assertThat(filtered.getFields(), is(notNullValue()));
        assertThat(filtered.getFields().isEmpty(), is(true));
    }

    @Test
    void shouldParseRecordResponseWithNullAndEmptyFields() throws Exception {
        var runContext = runContextFactory.of();
        var client = new AirtableClient("fake-api-key", runContext);

        var jsonWithNullField = """
            {
              "id": "rec123",
              "createdTime": "2024-01-01T00:00:00.000Z",
              "fields": {
                "Task Name": "Test Task",
                "Due Date": null
              }
            }
            """;
        var rec = client.parseRecordResponse(jsonWithNullField);
        assertThat(rec.getId(), is("rec123"));
        assertThat(rec.getFields().get("Task Name"), is("Test Task"));
        assertThat(rec.getFields().containsKey("Due Date"), is(true));
        assertThat(rec.getFields().get("Due Date"), is(nullValue()));

        var filtered = client.filterRecordFields(rec, List.of("Task Name", "Due Date"));
        assertThat(filtered.getFields().get("Task Name"), is("Test Task"));
        assertThat(filtered.getFields().containsKey("Due Date"), is(true));
        assertThat(filtered.getFields().get("Due Date"), is(nullValue()));

        var jsonWithoutFields = """
            {
              "id": "rec456",
              "createdTime": "2024-01-01T00:00:00.000Z"
            }
            """;
        var recNoFields = client.parseRecordResponse(jsonWithoutFields);
        assertThat(recNoFields.getId(), is("rec456"));
        assertThat(recNoFields.getFields(), is(nullValue()));

        var filteredNoFields = client.filterRecordFields(recNoFields, List.of("Task Name"));
        assertThat(filteredNoFields.getFields(), is(notNullValue()));
        assertThat(filteredNoFields.getFields().isEmpty(), is(true));
    }
}
