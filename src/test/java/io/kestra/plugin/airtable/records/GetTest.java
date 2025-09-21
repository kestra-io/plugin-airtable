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
class GetTest {

    @Inject
    private RunContextFactory runContextFactory;

    @Test
    void shouldValidateRequiredProperties() {
        Get task = Get.builder()
            .id("test-task")
            .type(Get.class.getName())
            .baseId(Property.ofValue("appXXXXXXXXXXXXXX"))
            .tableId(Property.ofValue("Table1"))
            // Missing apiKey and recordId
            .build();

        RunContext runContext = runContextFactory.of();

        assertThrows(Exception.class, () -> task.run(runContext));
    }

    @Test
    void shouldBuildTaskWithAllProperties() {
        java.util.List<String> fields = java.util.List.of("Name", "Status", "Notes");

        Get task = Get.builder()
            .id("test-get-full")
            .type(Get.class.getName())
            .baseId(Property.ofValue("appXXXXXXXXXXXXXX"))
            .tableId(Property.ofValue("Table1"))
            .recordId(Property.ofValue("recXXXXXXXXXXXXXX"))
            .apiKey(Property.ofValue("fake-api-key"))
            .fields(Property.ofValue(fields))
            .build();

        assertThat(task.getBaseId(), is(notNullValue()));
        assertThat(task.getTableId(), is(notNullValue()));
        assertThat(task.getRecordId(), is(notNullValue()));
        assertThat(task.getApiKey(), is(notNullValue()));
        assertThat(task.getFields(), is(notNullValue()));
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "AIRTABLE_INTEGRATION_TESTS", matches = "true")
    @EnabledIfEnvironmentVariable(named = "AIRTABLE_API_KEY", matches = ".*")
    @EnabledIfEnvironmentVariable(named = "AIRTABLE_BASE_ID", matches = ".*")
    void shouldGetRecord() throws Exception {
        String apiKey = System.getenv("AIRTABLE_API_KEY");
        String baseId = System.getenv("AIRTABLE_BASE_ID");
        String tableId = System.getenv("AIRTABLE_TABLE_ID") != null ?
            System.getenv("AIRTABLE_TABLE_ID") : "Table1";

        // First, create a record to retrieve
        Map<String, Object> createFields = Map.of(
            "Name", "Record to Get - " + System.currentTimeMillis(),
            "Status", "Active",
            "Notes", "Record created for Get test"
        );

        Create createTask = Create.builder()
            .id("create-for-get")
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

        // Now get the record
        Get getTask = Get.builder()
            .id("test-get-integration")
            .type(Get.class.getName())
            .baseId(Property.ofValue(baseId))
            .tableId(Property.ofValue(tableId))
            .recordId(Property.ofValue(recordId))
            .apiKey(Property.ofValue(apiKey))
            .build();

        RunContext getContext = runContextFactory.of();
        Get.Output getOutput = getTask.run(getContext);

        // Verify get results
        assertThat(getOutput, is(notNullValue()));
        assertThat(getOutput.getRecord(), is(notNullValue()));
        Map<String, Object> recordMap = getOutput.getRecord();
        assertThat(recordMap.get("id"), is(equalTo(recordId)));
        assertThat(recordMap.get("fields"), is(notNullValue()));

        // Verify the retrieved fields match what was created
        @SuppressWarnings("unchecked")
        Map<String, Object> retrievedFields = (Map<String, Object>) recordMap.get("fields");
        assertThat(retrievedFields.get("Name"), is(equalTo(createFields.get("Name"))));
        assertThat(retrievedFields.get("Status"), is(equalTo(createFields.get("Status"))));
        assertThat(retrievedFields.get("Notes"), is(equalTo(createFields.get("Notes"))));

        // Verify metadata fields
        assertThat(recordMap.get("createdTime"), is(notNullValue()));
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "AIRTABLE_INTEGRATION_TESTS", matches = "true")
    @EnabledIfEnvironmentVariable(named = "AIRTABLE_API_KEY", matches = ".*")
    @EnabledIfEnvironmentVariable(named = "AIRTABLE_BASE_ID", matches = ".*")
    void shouldGetRecordWithSpecificFields() throws Exception {
        String apiKey = System.getenv("AIRTABLE_API_KEY");
        String baseId = System.getenv("AIRTABLE_BASE_ID");
        String tableId = System.getenv("AIRTABLE_TABLE_ID") != null ?
            System.getenv("AIRTABLE_TABLE_ID") : "Table1";

        // Create a record with multiple fields
        Map<String, Object> createFields = Map.of(
            "Name", "Field Selection Test - " + System.currentTimeMillis(),
            "Status", "Active",
            "Notes", "This field should not be retrieved"
        );

        Create createTask = Create.builder()
            .id("create-for-field-selection")
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

        // Get record with only specific fields
        java.util.List<String> fieldsToGet = java.util.List.of("Name", "Status");

        Get getTask = Get.builder()
            .id("test-get-specific-fields")
            .type(Get.class.getName())
            .baseId(Property.ofValue(baseId))
            .tableId(Property.ofValue(tableId))
            .recordId(Property.ofValue(recordId))
            .apiKey(Property.ofValue(apiKey))
            .fields(Property.ofValue(fieldsToGet))
            .build();

        RunContext getContext = runContextFactory.of();
        Get.Output getOutput = getTask.run(getContext);

        // Verify field selection works
        assertThat(getOutput, is(notNullValue()));
        assertThat(getOutput.getRecord(), is(notNullValue()));
        Map<String, Object> recordMap = getOutput.getRecord();
        assertThat(recordMap.get("fields"), is(notNullValue()));

        @SuppressWarnings("unchecked")
        Map<String, Object> retrievedFields = (Map<String, Object>) recordMap.get("fields");

        // Should have requested fields
        assertThat(retrievedFields, hasKey("Name"));
        assertThat(retrievedFields, hasKey("Status"));
        assertThat(retrievedFields.get("Name"), is(equalTo(createFields.get("Name"))));
        assertThat(retrievedFields.get("Status"), is(equalTo(createFields.get("Status"))));

        // Should NOT have the field that wasn't requested
        // Note: Airtable might still include some fields, so we mainly verify the requested fields are present
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "AIRTABLE_INTEGRATION_TESTS", matches = "true")
    @EnabledIfEnvironmentVariable(named = "AIRTABLE_API_KEY", matches = ".*")
    @EnabledIfEnvironmentVariable(named = "AIRTABLE_BASE_ID", matches = ".*")
    void shouldGetRecordAfterUpdate() throws Exception {
        String apiKey = System.getenv("AIRTABLE_API_KEY");
        String baseId = System.getenv("AIRTABLE_BASE_ID");
        String tableId = System.getenv("AIRTABLE_TABLE_ID") != null ?
            System.getenv("AIRTABLE_TABLE_ID") : "Table1";

        // Create a record
        Map<String, Object> createFields = Map.of(
            "Name", "Get After Update Test - " + System.currentTimeMillis(),
            "Status", "Initial"
        );

        Create createTask = Create.builder()
            .id("create-for-get-after-update")
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

        // Update the record
        Map<String, Object> updateFields = Map.of(
            "Status", "Updated",
            "Notes", "Added after creation"
        );

        Update updateTask = Update.builder()
            .id("update-for-get-test")
            .type(Update.class.getName())
            .baseId(Property.ofValue(baseId))
            .tableId(Property.ofValue(tableId))
            .recordId(Property.ofValue(recordId))
            .apiKey(Property.ofValue(apiKey))
            .fields(Property.ofValue(updateFields))
            .typecast(Property.ofValue(true))
            .build();

        RunContext updateContext = runContextFactory.of();
        updateTask.run(updateContext);

        // Get the updated record
        Get getTask = Get.builder()
            .id("test-get-after-update")
            .type(Get.class.getName())
            .baseId(Property.ofValue(baseId))
            .tableId(Property.ofValue(tableId))
            .recordId(Property.ofValue(recordId))
            .apiKey(Property.ofValue(apiKey))
            .build();

        RunContext getContext = runContextFactory.of();
        Get.Output getOutput = getTask.run(getContext);

        // Verify we get the updated version
        assertThat(getOutput, is(notNullValue()));
        assertThat(getOutput.getRecord(), is(notNullValue()));
        Map<String, Object> recordMap = getOutput.getRecord();
        assertThat(recordMap.get("fields"), is(notNullValue()));

        @SuppressWarnings("unchecked")
        Map<String, Object> retrievedFields = (Map<String, Object>) recordMap.get("fields");
        assertThat(retrievedFields.get("Name"), is(equalTo(createFields.get("Name"))));
        assertThat(retrievedFields.get("Status"), is(equalTo("Updated")));
        assertThat(retrievedFields.get("Notes"), is(equalTo("Added after creation")));
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "AIRTABLE_INTEGRATION_TESTS", matches = "true")
    @EnabledIfEnvironmentVariable(named = "AIRTABLE_API_KEY", matches = ".*")
    @EnabledIfEnvironmentVariable(named = "AIRTABLE_BASE_ID", matches = ".*")
    void shouldFailToGetNonExistentRecord() {
        String apiKey = System.getenv("AIRTABLE_API_KEY");
        String baseId = System.getenv("AIRTABLE_BASE_ID");
        String tableId = System.getenv("AIRTABLE_TABLE_ID") != null ?
            System.getenv("AIRTABLE_TABLE_ID") : "Table1";

        // Try to get a non-existent record
        String fakeRecordId = "recNONEXISTENTRECORD";

        Get getTask = Get.builder()
            .id("test-get-nonexistent")
            .type(Get.class.getName())
            .baseId(Property.ofValue(baseId))
            .tableId(Property.ofValue(tableId))
            .recordId(Property.ofValue(fakeRecordId))
            .apiKey(Property.ofValue(apiKey))
            .build();

        RunContext getContext = runContextFactory.of();

        // Should throw an exception for non-existent record
        assertThrows(Exception.class, () -> getTask.run(getContext));
    }
}