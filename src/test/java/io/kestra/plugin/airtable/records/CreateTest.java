package io.kestra.plugin.airtable.records;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.utils.TestsUtils;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertThrows;

@KestraTest
class CreateTest {

    @Inject
    private RunContextFactory runContextFactory;

    @Test
    void shouldValidateRequiredProperties() {
        Create task = Create.builder()
            .id("test-task")
            .type(Create.class.getName())
            .baseId(Property.ofValue("appXXXXXXXXXXXXXX"))
            .tableId(Property.ofValue("Table1"))
            // Missing apiKey and fields
            .build();

        RunContext runContext = runContextFactory.of();

        assertThrows(Exception.class, () -> task.run(runContext));
    }

    @Test
    void shouldBuildTaskWithAllProperties() {
        Map<String, Object> fields = Map.of(
            "Name", "Test Record",
            "Status", "Active"
        );

        Create task = Create.builder()
            .id("test-create-full")
            .type(Create.class.getName())
            .baseId(Property.ofValue("appXXXXXXXXXXXXXX"))
            .tableId(Property.ofValue("Table1"))
            .apiKey(Property.ofValue("fake-api-key"))
            .fields(Property.ofValue(fields))
            .typecast(Property.ofValue(true))
            .build();

        assertThat(task.getBaseId(), is(notNullValue()));
        assertThat(task.getTableId(), is(notNullValue()));
        assertThat(task.getApiKey(), is(notNullValue()));
        assertThat(task.getFields(), is(notNullValue()));
        assertThat(task.getTypecast(), is(notNullValue()));
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "AIRTABLE_INTEGRATION_TESTS", matches = "true")
    @EnabledIfEnvironmentVariable(named = "AIRTABLE_API_KEY", matches = ".*")
    @EnabledIfEnvironmentVariable(named = "AIRTABLE_BASE_ID", matches = ".*")
    void shouldCreateSingleRecord() throws Exception {
        String apiKey = System.getenv("AIRTABLE_API_KEY");
        String baseId = System.getenv("AIRTABLE_BASE_ID");
        String tableId = System.getenv("AIRTABLE_TABLE_ID") != null ?
            System.getenv("AIRTABLE_TABLE_ID") : "Table1";

        Map<String, Object> recordFields = Map.of(
            "Name", "Kestra Test Record - " + System.currentTimeMillis(),
            "Notes", "Created by Kestra Airtable Plugin Test",
            "Status", "Test"
        );

        Create task = Create.builder()
            .id("test-create-integration")
            .type(Create.class.getName())
            .baseId(Property.ofValue(baseId))
            .tableId(Property.ofValue(tableId))
            .apiKey(Property.ofValue(apiKey))
            .fields(Property.ofValue(recordFields))
            .typecast(Property.ofValue(true))
            .build();

        RunContext runContext = runContextFactory.of();
        Create.Output output = task.run(runContext);

        // Verify creation results
        assertThat(output, is(notNullValue()));
        assertThat(output.getRecordIds(), is(notNullValue()));
        assertThat(output.getRecordIds().size(), is(equalTo(1)));
        assertThat(output.getRecord(), is(notNullValue()));
        Map<String, Object> recordMap = output.getRecord();
        assertThat(recordMap.get("id"), is(notNullValue()));
        assertThat(recordMap.get("fields"), is(notNullValue()));

        // Verify the record was created with correct data
        @SuppressWarnings("unchecked")
        Map<String, Object> createdFields = (Map<String, Object>) recordMap.get("fields");
        assertThat(createdFields.get("Name"), is(equalTo(recordFields.get("Name"))));
        assertThat(createdFields.get("Notes"), is(equalTo(recordFields.get("Notes"))));
        assertThat(createdFields.get("Status"), is(equalTo(recordFields.get("Status"))));
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "AIRTABLE_INTEGRATION_TESTS", matches = "true")
    @EnabledIfEnvironmentVariable(named = "AIRTABLE_API_KEY", matches = ".*")
    @EnabledIfEnvironmentVariable(named = "AIRTABLE_BASE_ID", matches = ".*")
    void shouldCreateMultipleRecords() throws Exception {
        String apiKey = System.getenv("AIRTABLE_API_KEY");
        String baseId = System.getenv("AIRTABLE_BASE_ID");
        String tableId = System.getenv("AIRTABLE_TABLE_ID") != null ?
            System.getenv("AIRTABLE_TABLE_ID") : "Table1";

        java.util.List<Map<String, Object>> records = java.util.List.of(
            Map.of(
                "Name", "Batch Record 1 - " + System.currentTimeMillis(),
                "Status", "Test",
                "Notes", "Batch creation test 1"
            ),
            Map.of(
                "Name", "Batch Record 2 - " + System.currentTimeMillis(),
                "Status", "Test",
                "Notes", "Batch creation test 2"
            )
        );

        Create task = Create.builder()
            .id("test-create-batch")
            .type(Create.class.getName())
            .baseId(Property.ofValue(baseId))
            .tableId(Property.ofValue(tableId))
            .apiKey(Property.ofValue(apiKey))
            .records(Property.ofValue(records))
            .typecast(Property.ofValue(true))
            .build();

        RunContext runContext = runContextFactory.of();
        Create.Output output = task.run(runContext);

        // Verify batch creation results
        assertThat(output, is(notNullValue()));
        assertThat(output.getRecordIds(), is(notNullValue()));
        assertThat(output.getRecordIds().size(), is(equalTo(2)));
        assertThat(output.getRecords(), is(notNullValue()));
        assertThat(output.getRecords().size(), is(equalTo(2)));

        // Verify each created record
        for (int i = 0; i < output.getRecords().size(); i++) {
            Map<String, Object> recordMap = output.getRecords().get(i);
            assertThat(recordMap.get("id"), is(notNullValue()));
            assertThat(recordMap.get("fields"), is(notNullValue()));

            @SuppressWarnings("unchecked")
            Map<String, Object> createdFields = (Map<String, Object>) recordMap.get("fields");
            Map<String, Object> originalFields = records.get(i);

            assertThat(createdFields.get("Name"), is(equalTo(originalFields.get("Name"))));
            assertThat(createdFields.get("Status"), is(equalTo(originalFields.get("Status"))));
            assertThat(createdFields.get("Notes"), is(equalTo(originalFields.get("Notes"))));
        }
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "AIRTABLE_INTEGRATION_TESTS", matches = "true")
    @EnabledIfEnvironmentVariable(named = "AIRTABLE_API_KEY", matches = ".*")
    @EnabledIfEnvironmentVariable(named = "AIRTABLE_BASE_ID", matches = ".*")
    void shouldHandleTypecasting() throws Exception {
        String apiKey = System.getenv("AIRTABLE_API_KEY");
        String baseId = System.getenv("AIRTABLE_BASE_ID");
        String tableId = System.getenv("AIRTABLE_TABLE_ID") != null ?
            System.getenv("AIRTABLE_TABLE_ID") : "Table1";

        // Use fields that might need typecasting (numbers as strings, dates, etc.)
        Map<String, Object> recordFields = Map.of(
            "Name", "Typecast Test - " + System.currentTimeMillis(),
            "Status", "Active",
            "Notes", "Testing typecast functionality"
        );

        Create task = Create.builder()
            .id("test-create-typecast")
            .type(Create.class.getName())
            .baseId(Property.ofValue(baseId))
            .tableId(Property.ofValue(tableId))
            .apiKey(Property.ofValue(apiKey))
            .fields(Property.ofValue(recordFields))
            .typecast(Property.ofValue(true))
            .build();

        RunContext runContext = runContextFactory.of();
        Create.Output output = task.run(runContext);

        // Verify typecast creation works
        assertThat(output, is(notNullValue()));
        assertThat(output.getRecordIds(), is(notNullValue()));
        assertThat(output.getRecordIds().size(), is(equalTo(1)));
        assertThat(output.getRecord(), is(notNullValue()));
        assertThat(output.getRecord().get("fields"), is(notNullValue()));
    }
}