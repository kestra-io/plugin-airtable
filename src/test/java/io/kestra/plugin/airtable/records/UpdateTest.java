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
class UpdateTest {

    @Inject
    private RunContextFactory runContextFactory;

    @Test
    void shouldValidateRequiredProperties() {
        Update task = Update.builder()
            .id("test-task")
            .type(Update.class.getName())
            .baseId(Property.ofValue("appXXXXXXXXXXXXXX"))
            .tableId(Property.ofValue("Table1"))
            .recordId(Property.ofValue("recXXXXXXXXXXXXXX"))
            // Missing apiKey and fields
            .build();

        RunContext runContext = runContextFactory.of();

        assertThrows(Exception.class, () -> task.run(runContext));
    }

    @Test
    void shouldBuildTaskWithAllProperties() {
        Map<String, Object> fields = Map.of(
            "Name", "Updated Record",
            "Status", "Updated"
        );

        Update task = Update.builder()
            .id("test-update-full")
            .type(Update.class.getName())
            .baseId(Property.ofValue("appXXXXXXXXXXXXXX"))
            .tableId(Property.ofValue("Table1"))
            .recordId(Property.ofValue("recXXXXXXXXXXXXXX"))
            .apiKey(Property.ofValue("fake-api-key"))
            .fields(Property.ofValue(fields))
            .typecast(Property.ofValue(true))
            .build();

        assertThat(task.getBaseId(), is(notNullValue()));
        assertThat(task.getTableId(), is(notNullValue()));
        assertThat(task.getRecordId(), is(notNullValue()));
        assertThat(task.getApiKey(), is(notNullValue()));
        assertThat(task.getFields(), is(notNullValue()));
        assertThat(task.getTypecast(), is(notNullValue()));
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "AIRTABLE_INTEGRATION_TESTS", matches = "true")
    @EnabledIfEnvironmentVariable(named = "AIRTABLE_API_KEY", matches = ".*")
    @EnabledIfEnvironmentVariable(named = "AIRTABLE_BASE_ID", matches = ".*")
    void shouldUpdateRecord() throws Exception {
        String apiKey = System.getenv("AIRTABLE_API_KEY");
        String baseId = System.getenv("AIRTABLE_BASE_ID");
        String tableId = System.getenv("AIRTABLE_TABLE_ID") != null ?
            System.getenv("AIRTABLE_TABLE_ID") : "Table1";

        // First, create a record to update
        Map<String, Object> createFields = Map.of(
            "Name", "Record to Update - " + System.currentTimeMillis(),
            "Status", "Pending",
            "Notes", "Original record for update test"
        );

        Create createTask = Create.builder()
            .id("create-for-update")
            .type(Create.class.getName())
            .baseId(Property.ofValue(baseId))
            .tableId(Property.ofValue(tableId))
            .apiKey(Property.ofValue(apiKey))
            .fields(Property.ofValue(createFields))
            .typecast(Property.ofValue(true))
            .build();

        RunContext createContext = runContextFactory.of();
        Create.Output createOutput = createTask.run(createContext);

        String recordId = createOutput.getRecordIds().get(0);

        // Now update the record
        Map<String, Object> updateFields = Map.of(
            "Status", "Updated",
            "Notes", "Updated by Kestra Airtable Plugin Test - " + System.currentTimeMillis()
        );

        Update updateTask = Update.builder()
            .id("test-update-integration")
            .type(Update.class.getName())
            .baseId(Property.ofValue(baseId))
            .tableId(Property.ofValue(tableId))
            .recordId(Property.ofValue(recordId))
            .apiKey(Property.ofValue(apiKey))
            .fields(Property.ofValue(updateFields))
            .typecast(Property.ofValue(true))
            .build();

        RunContext updateContext = runContextFactory.of();
        Update.Output updateOutput = updateTask.run(updateContext);

        // Verify update results
        assertThat(updateOutput, is(notNullValue()));
        assertThat(updateOutput.getRecord(), is(notNullValue()));
        Map<String, Object> recordMap = updateOutput.getRecord();
        assertThat(recordMap.get("id"), is(equalTo(recordId)));
        assertThat(recordMap.get("fields"), is(notNullValue()));

        // Verify the updated fields
        @SuppressWarnings("unchecked")
        Map<String, Object> updatedFields = (Map<String, Object>) recordMap.get("fields");
        assertThat(updatedFields.get("Status"), is(equalTo("Updated")));
        assertThat(updatedFields.get("Notes"), is(equalTo(updateFields.get("Notes"))));
        // Original Name should still be present
        assertThat(updatedFields.get("Name"), is(equalTo(createFields.get("Name"))));
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "AIRTABLE_INTEGRATION_TESTS", matches = "true")
    @EnabledIfEnvironmentVariable(named = "AIRTABLE_API_KEY", matches = ".*")
    @EnabledIfEnvironmentVariable(named = "AIRTABLE_BASE_ID", matches = ".*")
    void shouldUpdateWithTypecast() throws Exception {
        String apiKey = System.getenv("AIRTABLE_API_KEY");
        String baseId = System.getenv("AIRTABLE_BASE_ID");
        String tableId = System.getenv("AIRTABLE_TABLE_ID") != null ?
            System.getenv("AIRTABLE_TABLE_ID") : "Table1";

        // Create a record first
        Map<String, Object> createFields = Map.of(
            "Name", "Typecast Update Test - " + System.currentTimeMillis(),
            "Status", "Initial"
        );

        Create createTask = Create.builder()
            .id("create-for-typecast-update")
            .type(Create.class.getName())
            .baseId(Property.ofValue(baseId))
            .tableId(Property.ofValue(tableId))
            .apiKey(Property.ofValue(apiKey))
            .fields(Property.ofValue(createFields))
            .typecast(Property.ofValue(true))
            .build();

        RunContext createContext = runContextFactory.of();
        Create.Output createOutput = createTask.run(createContext);

        String recordId = createOutput.getRecordIds().get(0);

        // Update with typecast enabled
        Map<String, Object> updateFields = Map.of(
            "Status", "Active",
            "Notes", "Typecast update test - " + System.currentTimeMillis()
        );

        Update updateTask = Update.builder()
            .id("test-typecast-update")
            .type(Update.class.getName())
            .baseId(Property.ofValue(baseId))
            .tableId(Property.ofValue(tableId))
            .recordId(Property.ofValue(recordId))
            .apiKey(Property.ofValue(apiKey))
            .fields(Property.ofValue(updateFields))
            .typecast(Property.ofValue(true))
            .build();

        RunContext updateContext = runContextFactory.of();
        Update.Output updateOutput = updateTask.run(updateContext);

        // Verify typecast update works
        assertThat(updateOutput, is(notNullValue()));
        assertThat(updateOutput.getRecord(), is(notNullValue()));
        Map<String, Object> recordMap = updateOutput.getRecord();
        assertThat(recordMap.get("id"), is(equalTo(recordId)));
        assertThat(recordMap.get("fields"), is(notNullValue()));

        @SuppressWarnings("unchecked")
        Map<String, Object> updatedFields = (Map<String, Object>) recordMap.get("fields");
        assertThat(updatedFields.get("Status"), is(equalTo("Active")));
        assertThat(updatedFields.get("Notes"), is(equalTo(updateFields.get("Notes"))));
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "AIRTABLE_INTEGRATION_TESTS", matches = "true")
    @EnabledIfEnvironmentVariable(named = "AIRTABLE_API_KEY", matches = ".*")
    @EnabledIfEnvironmentVariable(named = "AIRTABLE_BASE_ID", matches = ".*")
    void shouldPartiallyUpdateRecord() throws Exception {
        String apiKey = System.getenv("AIRTABLE_API_KEY");
        String baseId = System.getenv("AIRTABLE_BASE_ID");
        String tableId = System.getenv("AIRTABLE_TABLE_ID") != null ?
            System.getenv("AIRTABLE_TABLE_ID") : "Table1";

        // Create a record with multiple fields
        Map<String, Object> createFields = Map.of(
            "Name", "Partial Update Test - " + System.currentTimeMillis(),
            "Status", "Initial",
            "Notes", "Original notes"
        );

        Create createTask = Create.builder()
            .id("create-for-partial-update")
            .type(Create.class.getName())
            .baseId(Property.ofValue(baseId))
            .tableId(Property.ofValue(tableId))
            .apiKey(Property.ofValue(apiKey))
            .fields(Property.ofValue(createFields))
            .typecast(Property.ofValue(true))
            .build();

        RunContext createContext = runContextFactory.of();
        Create.Output createOutput = createTask.run(createContext);

        String recordId = createOutput.getRecordIds().get(0);

        // Update only one field
        Map<String, Object> updateFields = Map.of(
            "Status", "Partially Updated"
        );

        Update updateTask = Update.builder()
            .id("test-partial-update")
            .type(Update.class.getName())
            .baseId(Property.ofValue(baseId))
            .tableId(Property.ofValue(tableId))
            .recordId(Property.ofValue(recordId))
            .apiKey(Property.ofValue(apiKey))
            .fields(Property.ofValue(updateFields))
            .typecast(Property.ofValue(true))
            .build();

        RunContext updateContext = runContextFactory.of();
        Update.Output updateOutput = updateTask.run(updateContext);

        // Verify partial update
        assertThat(updateOutput, is(notNullValue()));
        assertThat(updateOutput.getRecord(), is(notNullValue()));
        Map<String, Object> recordMap = updateOutput.getRecord();
        assertThat(recordMap.get("fields"), is(notNullValue()));

        @SuppressWarnings("unchecked")
        Map<String, Object> updatedFields = (Map<String, Object>) recordMap.get("fields");
        // Updated field should change
        assertThat(updatedFields.get("Status"), is(equalTo("Partially Updated")));
        // Other fields should remain unchanged
        assertThat(updatedFields.get("Name"), is(equalTo(createFields.get("Name"))));
        assertThat(updatedFields.get("Notes"), is(equalTo(createFields.get("Notes"))));
    }
}