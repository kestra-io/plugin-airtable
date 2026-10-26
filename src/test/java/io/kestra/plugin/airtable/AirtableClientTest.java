package io.kestra.plugin.airtable;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpServer;

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
    void shouldEncodeSpacesInTableNameAndReportErrorBody() throws Exception {
        var requestPath = new AtomicReference<String>();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange ->
        {
            requestPath.set(exchange.getRequestURI().getRawPath());
            var body = """
                {"error":{"type":"INVALID_PERMISSIONS_OR_MODEL_NOT_FOUND","message":"Invalid permissions, or the requested model was not found."}}"""
                .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(403, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            var baseUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/v0";
            var client = new AirtableClient("fake-api-key", runContextFactory.of(), baseUrl);

            var exception = assertThrows(AirtableException.class, () -> client.listRecords("appTEST", "Table 1", null, null, null, null, null));

            assertThat(requestPath.get(), is("/v0/appTEST/Table%201"));
            assertThat(exception.getMessage(), containsString("INVALID_PERMISSIONS_OR_MODEL_NOT_FOUND"));
        } finally {
            server.stop(0);
        }
    }
}
