package io.kestra.plugin.airtable.records;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.VoidOutput;
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
class DeleteTest {

    @Inject
    private RunContextFactory runContextFactory;

    @Test
    void shouldValidateRequiredProperties() {
        Delete task = Delete.builder()
            .id("test-task")
            .type(Delete.class.getName())
            .baseId(Property.ofValue("appXXXXXXXXXXXXXX"))
            .tableId(Property.ofValue("Table1"))
            // Missing apiKey and recordId
            .build();

        RunContext runContext = runContextFactory.of();

        assertThrows(Exception.class, () -> task.run(runContext));
    }

    @Test
    void shouldBuildTaskWithAllProperties() {
        Delete task = Delete.builder()
            .id("test-delete-full")
            .type(Delete.class.getName())
            .baseId(Property.ofValue("appXXXXXXXXXXXXXX"))
            .tableId(Property.ofValue("Table1"))
            .recordId(Property.ofValue("recXXXXXXXXXXXXXX"))
            .apiKey(Property.ofValue("fake-api-key"))
            .build();

        // Verify all properties are properly set
        assertThat(task.getBaseId(), is(notNullValue()));
        assertThat(task.getTableId(), is(notNullValue()));
        assertThat(task.getRecordId(), is(notNullValue()));
        assertThat(task.getApiKey(), is(notNullValue()));

        // Verify the task can be executed (should fail with fake credentials)
        RunContext runContext = runContextFactory.of();

        // Task should fail due to invalid API key, but this proves it's properly built and runnable
        Exception exception = assertThrows(Exception.class, () -> task.run(runContext));
        assertThat("Should fail with authentication or API error due to fake credentials",
            exception.getMessage(), anyOf(
                containsString("INVALID_AUTHORIZATION"),
                containsString("NOT_FOUND"),
                containsString("403"),
                containsString("401"),
                containsString("authentication"),
                containsString("authorization")
            ));
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "AIRTABLE_INTEGRATION_TESTS", matches = "true")
    @EnabledIfEnvironmentVariable(named = "AIRTABLE_API_KEY", matches = ".*")
    @EnabledIfEnvironmentVariable(named = "AIRTABLE_BASE_ID", matches = ".*")
    void shouldDeleteRecord() throws Exception {
        String apiKey = System.getenv("AIRTABLE_API_KEY");
        String baseId = System.getenv("AIRTABLE_BASE_ID");
        String tableId = System.getenv("AIRTABLE_TABLE_ID") != null ?
            System.getenv("AIRTABLE_TABLE_ID") : "Table1";

        // First, create a record to delete
        Map<String, Object> createFields = Map.of(
            "Name", "Record to Delete - " + System.currentTimeMillis(),
            "Status", "Pending Deletion",
            "Notes", "This record will be deleted by test"
        );

        Create createTask = Create.builder()
            .id("create-for-delete")
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

        // Verify the record exists before deletion
        Get getTask = Get.builder()
            .id("verify-record-exists")
            .type(Get.class.getName())
            .baseId(Property.ofValue(baseId))
            .tableId(Property.ofValue(tableId))
            .recordId(Property.ofValue(recordId))
            .apiKey(Property.ofValue(apiKey))
            .build();

        RunContext getContext = runContextFactory.of();
        Get.Output getOutput = getTask.run(getContext);

        assertThat(getOutput.getRecord(), is(notNullValue()));
        Map<String, Object> recordMap = getOutput.getRecord();
        assertThat(recordMap.get("id"), is(equalTo(recordId)));

        // Now delete the record
        Delete deleteTask = Delete.builder()
            .id("test-delete-integration")
            .type(Delete.class.getName())
            .baseId(Property.ofValue(baseId))
            .tableId(Property.ofValue(tableId))
            .recordId(Property.ofValue(recordId))
            .apiKey(Property.ofValue(apiKey))
            .build();

        RunContext deleteContext = runContextFactory.of();
        VoidOutput deleteOutput = deleteTask.run(deleteContext);

        // Verify delete returns VoidOutput
        assertThat(deleteOutput, is(notNullValue()));

        // Verify the record no longer exists
        Get verifyDeleteTask = Get.builder()
            .id("verify-record-deleted")
            .type(Get.class.getName())
            .baseId(Property.ofValue(baseId))
            .tableId(Property.ofValue(tableId))
            .recordId(Property.ofValue(recordId))
            .apiKey(Property.ofValue(apiKey))
            .build();

        RunContext verifyContext = runContextFactory.of();

        // Should throw an exception when trying to get a deleted record
        assertThrows(Exception.class, () -> verifyDeleteTask.run(verifyContext));
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "AIRTABLE_INTEGRATION_TESTS", matches = "true")
    @EnabledIfEnvironmentVariable(named = "AIRTABLE_API_KEY", matches = ".*")
    @EnabledIfEnvironmentVariable(named = "AIRTABLE_BASE_ID", matches = ".*")
    void shouldFailToDeleteNonExistentRecord() {
        String apiKey = System.getenv("AIRTABLE_API_KEY");
        String baseId = System.getenv("AIRTABLE_BASE_ID");
        String tableId = System.getenv("AIRTABLE_TABLE_ID") != null ?
            System.getenv("AIRTABLE_TABLE_ID") : "Table1";

        // Try to delete a non-existent record
        String fakeRecordId = "recNONEXISTENTRECORD";

        Delete deleteTask = Delete.builder()
            .id("test-delete-nonexistent")
            .type(Delete.class.getName())
            .baseId(Property.ofValue(baseId))
            .tableId(Property.ofValue(tableId))
            .recordId(Property.ofValue(fakeRecordId))
            .apiKey(Property.ofValue(apiKey))
            .build();

        RunContext deleteContext = runContextFactory.of();

        // Should throw an exception when trying to delete a non-existent record
        assertThrows(Exception.class, () -> deleteTask.run(deleteContext));
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "AIRTABLE_INTEGRATION_TESTS", matches = "true")
    @EnabledIfEnvironmentVariable(named = "AIRTABLE_API_KEY", matches = ".*")
    @EnabledIfEnvironmentVariable(named = "AIRTABLE_BASE_ID", matches = ".*")
    void shouldDeleteRecordAfterUpdate() throws Exception {
        String apiKey = System.getenv("AIRTABLE_API_KEY");
        String baseId = System.getenv("AIRTABLE_BASE_ID");
        String tableId = System.getenv("AIRTABLE_TABLE_ID") != null ?
            System.getenv("AIRTABLE_TABLE_ID") : "Table1";

        // Create a record
        Map<String, Object> createFields = Map.of(
            "Name", "Delete After Update Test - " + System.currentTimeMillis(),
            "Status", "Initial"
        );

        Create createTask = Create.builder()
            .id("create-for-delete-after-update")
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
            "Status", "Updated Before Deletion",
            "Notes", "This record was updated before deletion"
        );

        Update updateTask = Update.builder()
            .id("update-before-delete")
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

        // Delete the updated record
        Delete deleteTask = Delete.builder()
            .id("delete-after-update")
            .type(Delete.class.getName())
            .baseId(Property.ofValue(baseId))
            .tableId(Property.ofValue(tableId))
            .recordId(Property.ofValue(recordId))
            .apiKey(Property.ofValue(apiKey))
            .build();

        RunContext deleteContext = runContextFactory.of();
        VoidOutput deleteOutput = deleteTask.run(deleteContext);

        // Verify delete succeeded
        assertThat(deleteOutput, is(notNullValue()));

        // Verify the record is gone
        Get verifyDeleteTask = Get.builder()
            .id("verify-updated-record-deleted")
            .type(Get.class.getName())
            .baseId(Property.ofValue(baseId))
            .tableId(Property.ofValue(tableId))
            .recordId(Property.ofValue(recordId))
            .apiKey(Property.ofValue(apiKey))
            .build();

        RunContext verifyContext = runContextFactory.of();

        assertThrows(Exception.class, () -> verifyDeleteTask.run(verifyContext));
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "AIRTABLE_INTEGRATION_TESTS", matches = "true")
    @EnabledIfEnvironmentVariable(named = "AIRTABLE_API_KEY", matches = ".*")
    @EnabledIfEnvironmentVariable(named = "AIRTABLE_BASE_ID", matches = ".*")
    void shouldDeleteMultipleRecordsSequentially() throws Exception {
        String apiKey = System.getenv("AIRTABLE_API_KEY");
        String baseId = System.getenv("AIRTABLE_BASE_ID");
        String tableId = System.getenv("AIRTABLE_TABLE_ID") != null ?
            System.getenv("AIRTABLE_TABLE_ID") : "Table1";

        // Create multiple records
        java.util.List<Map<String, Object>> recordsToCreate = java.util.List.of(
            Map.of(
                "Name", "Sequential Delete Test 1 - " + System.currentTimeMillis(),
                "Status", "To Delete"
            ),
            Map.of(
                "Name", "Sequential Delete Test 2 - " + System.currentTimeMillis(),
                "Status", "To Delete"
            )
        );

        Create createTask = Create.builder()
            .id("create-multiple-for-delete")
            .type(Create.class.getName())
            .baseId(Property.ofValue(baseId))
            .tableId(Property.ofValue(tableId))
            .apiKey(Property.ofValue(apiKey))
            .records(Property.ofValue(recordsToCreate))
            .typecast(Property.ofValue(true))
            .build();

        RunContext createContext = runContextFactory.of();
        Create.Output createOutput = createTask.run(createContext);

        java.util.List<String> recordIds = createOutput.getRecordIds();
        assertThat(recordIds.size(), is(equalTo(2)));

        // Delete each record sequentially
        for (String recordId : recordIds) {
            Delete deleteTask = Delete.builder()
                .id("delete-sequential-" + recordId)
                .type(Delete.class.getName())
                .baseId(Property.ofValue(baseId))
                .tableId(Property.ofValue(tableId))
                .recordId(Property.ofValue(recordId))
                .apiKey(Property.ofValue(apiKey))
                .build();

            RunContext deleteContext = runContextFactory.of();
            VoidOutput deleteOutput = deleteTask.run(deleteContext);

            assertThat(deleteOutput, is(notNullValue()));
        }

        // Verify all records are deleted
        for (String recordId : recordIds) {
            Get verifyDeleteTask = Get.builder()
                .id("verify-sequential-delete-" + recordId)
                .type(Get.class.getName())
                .baseId(Property.ofValue(baseId))
                .tableId(Property.ofValue(tableId))
                .recordId(Property.ofValue(recordId))
                .apiKey(Property.ofValue(apiKey))
                .build();

            RunContext verifyContext = runContextFactory.of();

            assertThrows(Exception.class, () -> verifyDeleteTask.run(verifyContext));
        }
    }
}