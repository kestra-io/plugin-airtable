package io.kestra.plugin.airtable.records;

import java.util.Map;

import org.junit.jupiter.api.Test;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.serializers.FileSerde;
import io.kestra.plugin.airtable.AirtableRecord;

import jakarta.inject.Inject;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

@KestraTest
class ListStoreTest {

    @Inject
    private RunContextFactory runContextFactory;

    @Test
    @SuppressWarnings("unchecked")
    void storeWritesIonThatIonReadersCanReadBack() throws Exception {
        var runContext = runContextFactory.of();
        var records = java.util.List.of(
            new AirtableRecord("rec1", "2024-01-01T00:00:00.000Z", Map.of("Name", "Alice, Bob", "Note", "a=b {c}")),
            new AirtableRecord("rec2", "2024-01-02T00:00:00.000Z", Map.of("Name", "Zoë", "Count", 3))
        );

        var uri = List.storeRecords(runContext, records);

        assertThat(uri.toString(), containsString("airtable_records.ion"));
        try (var input = runContext.storage().getFile(uri)) {
            var rows = FileSerde.readAll(input).collectList().block();

            assertThat(rows, hasSize(2));
            var first = (Map<String, Object>) rows.getFirst();
            assertThat(first.get("id"), is("rec1"));
            var fields = (Map<String, Object>) first.get("fields");
            assertThat(fields.get("Name"), is("Alice, Bob"));
            assertThat(fields.get("Note"), is("a=b {c}"));
            var second = (Map<String, Object>) rows.get(1);
            assertThat(((Map<String, Object>) second.get("fields")).get("Name"), is("Zoë"));
        }
    }
}
