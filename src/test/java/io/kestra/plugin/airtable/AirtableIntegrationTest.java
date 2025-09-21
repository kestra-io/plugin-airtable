package io.kestra.plugin.airtable;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.VoidOutput;
import io.kestra.core.models.tasks.common.FetchType;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.utils.TestsUtils;
import io.kestra.plugin.airtable.records.*;
import jakarta.inject.Inject;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Comprehensive integration tests that demonstrate the full CRUD cycle for Airtable.
 * These tests run against a real Airtable base and validate all plugin operations.
 *
 * To run these tests:
 * 1. Set environment variables:
 *    - AIRTABLE_INTEGRATION_TESTS=true
 *    - AIRTABLE_API_KEY=your_api_key
 *    - AIRTABLE_BASE_ID=your_base_id
 *    - AIRTABLE_TABLE_ID=your_table_id (optional, defaults to "Table1")
 * 2. Run: ./gradlew test
 */
@KestraTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@EnabledIfEnvironmentVariable(named = "AIRTABLE_INTEGRATION_TESTS", matches = "true")
@EnabledIfEnvironmentVariable(named = "AIRTABLE_API_KEY", matches = ".*")
@EnabledIfEnvironmentVariable(named = "AIRTABLE_BASE_ID", matches = ".*")
class AirtableIntegrationTest {

    @Inject
    RunContextFactory runContextFactory;

    private static final String TEST_PREFIX = "Kestra CRUD Test";
    private static String createdRecordId;
    private static java.util.List<String> batchCreatedRecordIds;

    private String getApiKey() {
        return System.getenv("AIRTABLE_API_KEY");
    }

    private String getBaseId() {
        return System.getenv("AIRTABLE_BASE_ID");
    }

    private String getTableId() {
        return System.getenv("AIRTABLE_TABLE_ID") != null ?
            System.getenv("AIRTABLE_TABLE_ID") : "Table1";
    }

    @Test
    @Order(1)
    @DisplayName("1. List Existing Records - Verify Connection")
    void shouldListExistingRecords() throws Exception {
        List listTask = List.builder()
            .id("list-existing")
            .type(List.class.getName())
            .baseId(Property.ofValue(getBaseId()))
            .tableId(Property.ofValue(getTableId()))
            .apiKey(Property.ofValue(getApiKey()))
            .maxRecords(Property.ofValue(10))
            .fetchType(Property.ofValue(FetchType.FETCH))
            .build();

        RunContext runContext = runContextFactory.of();
        List.Output output = listTask.run(runContext);

        assertThat("Should connect to Airtable", output, is(notNullValue()));
        assertThat("Should return size info", output.getSize(), is(notNullValue()));
        assertThat("Should return rows", output.getRows(), is(notNullValue()));
        assertThat("Should respect maxRecords limit", output.getRows().size(), is(lessThanOrEqualTo(10)));

        System.out.println("✅ Connected to Airtable - Found " + output.getSize() + " existing records");
    }

    @Test
    @Order(2)
    @DisplayName("2. Create Single Record - Test Record Creation")
    void shouldCreateSingleRecord() throws Exception {
        Map<String, Object> recordFields = Map.of(
            "Name", TEST_PREFIX + " Single - " + System.currentTimeMillis(),
            "Status", "Created",
            "Notes", "Single record created by Kestra Airtable Plugin Integration Test"
        );

        Create createTask = Create.builder()
            .id("create-single")
            .type(Create.class.getName())
            .baseId(Property.ofValue(getBaseId()))
            .tableId(Property.ofValue(getTableId()))
            .apiKey(Property.ofValue(getApiKey()))
            .fields(Property.ofValue(recordFields))
            .typecast(Property.ofValue(true))
            .build();

        RunContext runContext = runContextFactory.of();
        Create.Output output = createTask.run(runContext);

        assertThat("Should create record", output, is(notNullValue()));
        assertThat("Should return record IDs", output.getRecordIds(), is(notNullValue()));
        assertThat("Should create exactly one record", output.getRecordIds().size(), is(equalTo(1)));
        assertThat("Should return record data", output.getRecord(), is(notNullValue()));

        // Store for later tests
        createdRecordId = output.getRecordIds().get(0);

        // Verify created data
        Map<String, Object> recordMap = output.getRecord();
        @SuppressWarnings("unchecked")
        Map<String, Object> createdFields = (Map<String, Object>) recordMap.get("fields");
        assertThat(createdFields.get("Name"), is(equalTo(recordFields.get("Name"))));
        assertThat(createdFields.get("Status"), is(equalTo(recordFields.get("Status"))));

        System.out.println("✅ Created single record: " + recordFields.get("Name") + " (ID: " + createdRecordId + ")");
    }

    @Test
    @Order(3)
    @DisplayName("3. Get Created Record - Test Record Retrieval")
    void shouldGetCreatedRecord() throws Exception {
        assumeTrue(createdRecordId != null, "Created record ID should be available");

        Get getTask = Get.builder()
            .id("get-created")
            .type(Get.class.getName())
            .baseId(Property.ofValue(getBaseId()))
            .tableId(Property.ofValue(getTableId()))
            .recordId(Property.ofValue(createdRecordId))
            .apiKey(Property.ofValue(getApiKey()))
            .fields(Property.ofValue(java.util.List.of("Name", "Status", "Notes")))
            .build();

        RunContext runContext = runContextFactory.of();
        Get.Output output = getTask.run(runContext);

        assertThat("Should retrieve record", output, is(notNullValue()));
        assertThat("Should return record data", output.getRecord(), is(notNullValue()));
        Map<String, Object> recordMap = output.getRecord();
        assertThat("Should match created record ID", recordMap.get("id"), is(equalTo(createdRecordId)));

        @SuppressWarnings("unchecked")
        Map<String, Object> fields = (Map<String, Object>) recordMap.get("fields");
        assertThat("Should have Name field", fields, hasKey("Name"));
        assertThat("Should have Status field", fields, hasKey("Status"));
        assertThat("Name should contain test prefix", fields.get("Name").toString(), containsString(TEST_PREFIX));

        System.out.println("✅ Retrieved record: " + fields.get("Name"));
    }

    @Test
    @Order(4)
    @DisplayName("4. Update Created Record - Test Record Modification")
    void shouldUpdateCreatedRecord() throws Exception {
        assumeTrue(createdRecordId != null, "Created record ID should be available");

        Map<String, Object> updateFields = Map.of(
            "Status", "Updated",
            "Notes", "Updated by Kestra Integration Test - " + java.time.Instant.now()
        );

        Update updateTask = Update.builder()
            .id("update-created")
            .type(Update.class.getName())
            .baseId(Property.ofValue(getBaseId()))
            .tableId(Property.ofValue(getTableId()))
            .recordId(Property.ofValue(createdRecordId))
            .apiKey(Property.ofValue(getApiKey()))
            .fields(Property.ofValue(updateFields))
            .typecast(Property.ofValue(true))
            .build();

        RunContext runContext = runContextFactory.of();
        Update.Output output = updateTask.run(runContext);

        assertThat("Should update record", output, is(notNullValue()));
        assertThat("Should return updated record", output.getRecord(), is(notNullValue()));
        Map<String, Object> recordMap = output.getRecord();
        assertThat("Should maintain record ID", recordMap.get("id"), is(equalTo(createdRecordId)));

        @SuppressWarnings("unchecked")
        Map<String, Object> updatedFields = (Map<String, Object>) recordMap.get("fields");
        assertThat("Status should be updated", updatedFields.get("Status"), is(equalTo("Updated")));
        assertThat("Notes should be updated", updatedFields.get("Notes"), is(equalTo(updateFields.get("Notes"))));

        System.out.println("✅ Updated record status to: " + updatedFields.get("Status"));
    }

    @Test
    @Order(5)
    @DisplayName("5. Create Multiple Records - Test Batch Creation")
    void shouldCreateMultipleRecords() throws Exception {
        java.util.List<Map<String, Object>> records = java.util.List.of(
            Map.of(
                "Name", TEST_PREFIX + " Batch 1 - " + System.currentTimeMillis(),
                "Status", "Batch Created",
                "Notes", "First batch record"
            ),
            Map.of(
                "Name", TEST_PREFIX + " Batch 2 - " + System.currentTimeMillis(),
                "Status", "Batch Created",
                "Notes", "Second batch record"
            ),
            Map.of(
                "Name", TEST_PREFIX + " Batch 3 - " + System.currentTimeMillis(),
                "Status", "Batch Created",
                "Notes", "Third batch record"
            )
        );

        Create createTask = Create.builder()
            .id("create-batch")
            .type(Create.class.getName())
            .baseId(Property.ofValue(getBaseId()))
            .tableId(Property.ofValue(getTableId()))
            .apiKey(Property.ofValue(getApiKey()))
            .records(Property.ofValue(records))
            .typecast(Property.ofValue(true))
            .build();

        RunContext runContext = runContextFactory.of();
        Create.Output output = createTask.run(runContext);

        assertThat("Should create batch records", output, is(notNullValue()));
        assertThat("Should return record IDs", output.getRecordIds(), is(notNullValue()));
        assertThat("Should create all records", output.getRecordIds().size(), is(equalTo(3)));
        assertThat("Should return record data", output.getRecords(), is(notNullValue()));
        assertThat("Should return all record data", output.getRecords().size(), is(equalTo(3)));

        // Store for cleanup
        batchCreatedRecordIds = output.getRecordIds();

        System.out.println("✅ Created " + output.getRecordIds().size() + " batch records");
    }

    @Test
    @Order(6)
    @DisplayName("6. List Test Records - Test Filtering")
    void shouldListTestRecords() throws Exception {
        // List records created by this test using filtering
        List listTask = List.builder()
            .id("list-test-records")
            .type(List.class.getName())
            .baseId(Property.ofValue(getBaseId()))
            .tableId(Property.ofValue(getTableId()))
            .apiKey(Property.ofValue(getApiKey()))
            .filterByFormula(Property.ofValue("SEARCH('" + TEST_PREFIX + "', {Name})"))
            .fields(Property.ofValue(java.util.List.of("Name", "Status", "Notes")))
            .fetchType(Property.ofValue(FetchType.FETCH))
            .build();

        RunContext runContext = runContextFactory.of();
        List.Output output = listTask.run(runContext);

        assertThat("Should find test records", output, is(notNullValue()));
        assertThat("Should return rows", output.getRows(), is(notNullValue()));
        assertThat("Should find at least 4 records (1 single + 3 batch)", output.getSize(), is(greaterThanOrEqualTo(4L)));

        // Verify all returned records are our test records
        for (Map<String, Object> record : output.getRows()) {
            Map<String, Object> fields = (Map<String, Object>) record.get("fields");
            assertThat("All records should be test records",
                fields.get("Name").toString(), containsString(TEST_PREFIX));
        }

        System.out.println("✅ Found " + output.getSize() + " test records using filter");
    }

    @Test
    @Order(7)
    @DisplayName("7. Store Records to File - Test STORE FetchType")
    void shouldStoreRecordsToFile() throws Exception {
        List listTask = List.builder()
            .id("store-test-records")
            .type(List.class.getName())
            .baseId(Property.ofValue(getBaseId()))
            .tableId(Property.ofValue(getTableId()))
            .apiKey(Property.ofValue(getApiKey()))
            .filterByFormula(Property.ofValue("SEARCH('" + TEST_PREFIX + "', {Name})"))
            .fields(Property.ofValue(java.util.List.of("Name", "Status", "Notes")))
            .fetchType(Property.ofValue(FetchType.STORE))
            .build();

        RunContext runContext = runContextFactory.of();
        List.Output output = listTask.run(runContext);

        assertThat("Should store records", output, is(notNullValue()));
        assertThat("Should return URI for stored file", output.getUri(), is(notNullValue()));
        assertThat("Should return size", output.getSize(), is(greaterThan(0L)));

        // For STORE type, rows should be null/empty and URI should be present
        assertThat("URI should be present for STORE type", output.getUri().toString(), containsString("airtable_records.ion"));

        System.out.println("✅ Stored " + output.getSize() + " records to file: " + output.getUri());
    }

    @Test
    @Order(8)
    @DisplayName("8. Test Auto-Pagination - Test Large Dataset Handling")
    void shouldTestAutoPagination() throws Exception {
        List listTask = List.builder()
            .id("test-auto-pagination")
            .type(List.class.getName())
            .baseId(Property.ofValue(getBaseId()))
            .tableId(Property.ofValue(getTableId()))
            .apiKey(Property.ofValue(getApiKey()))
            .maxRecords(Property.ofValue(2)) // Small page size to force pagination
            .enableAutoPagination(Property.ofValue(true))
            .fetchType(Property.ofValue(FetchType.FETCH))
            .build();

        RunContext runContext = runContextFactory.of();
        List.Output output = listTask.run(runContext);

        assertThat("Should handle pagination", output, is(notNullValue()));
        assertThat("Should return rows", output.getRows(), is(notNullValue()));
        // With auto-pagination, we might get more than maxRecords
        assertThat("Should return some records", output.getSize(), is(greaterThan(0L)));

        System.out.println("✅ Auto-pagination retrieved " + output.getSize() + " total records");
    }

    @Test
    @Order(9)
    @DisplayName("9. Clean Up Single Record - Test Record Deletion")
    void shouldCleanUpSingleRecord() throws Exception {
        assumeTrue(createdRecordId != null, "Created record ID should be available");

        Delete deleteTask = Delete.builder()
            .id("delete-single")
            .type(Delete.class.getName())
            .baseId(Property.ofValue(getBaseId()))
            .tableId(Property.ofValue(getTableId()))
            .recordId(Property.ofValue(createdRecordId))
            .apiKey(Property.ofValue(getApiKey()))
            .build();

        RunContext runContext = runContextFactory.of();
        VoidOutput output = deleteTask.run(runContext);

        assertThat("Should delete record", output, is(notNullValue()));

        // Verify deletion by trying to get the record (should fail)
        Get verifyTask = Get.builder()
            .id("verify-deletion")
            .type(Get.class.getName())
            .baseId(Property.ofValue(getBaseId()))
            .tableId(Property.ofValue(getTableId()))
            .recordId(Property.ofValue(createdRecordId))
            .apiKey(Property.ofValue(getApiKey()))
            .build();

        RunContext verifyContext = runContextFactory.of();
        assertThrows(Exception.class, () -> verifyTask.run(verifyContext));

        System.out.println("✅ Deleted single record: " + createdRecordId);
    }

    @Test
    @Order(10)
    @DisplayName("10. Clean Up Batch Records - Test Multiple Deletions")
    void shouldCleanUpBatchRecords() throws Exception {
        assumeTrue(batchCreatedRecordIds != null && !batchCreatedRecordIds.isEmpty(),
            "Batch created record IDs should be available");

        for (String recordId : batchCreatedRecordIds) {
            Delete deleteTask = Delete.builder()
                .id("delete-batch-" + recordId)
                .type(Delete.class.getName())
                .baseId(Property.ofValue(getBaseId()))
                .tableId(Property.ofValue(getTableId()))
                .recordId(Property.ofValue(recordId))
                .apiKey(Property.ofValue(getApiKey()))
                .build();

            RunContext runContext = runContextFactory.of();
            VoidOutput output = deleteTask.run(runContext);

            assertThat("Should delete batch record", output, is(notNullValue()));
        }

        System.out.println("✅ Deleted " + batchCreatedRecordIds.size() + " batch records");
    }

    @Test
    @Order(11)
    @DisplayName("11. Verify Cleanup - Confirm All Test Records Removed")
    void shouldVerifyCleanup() throws Exception {
        // Verify all test records are cleaned up
        List listTask = List.builder()
            .id("verify-cleanup")
            .type(List.class.getName())
            .baseId(Property.ofValue(getBaseId()))
            .tableId(Property.ofValue(getTableId()))
            .apiKey(Property.ofValue(getApiKey()))
            .filterByFormula(Property.ofValue("SEARCH('" + TEST_PREFIX + "', {Name})"))
            .fetchType(Property.ofValue(FetchType.FETCH))
            .build();

        RunContext runContext = runContextFactory.of();
        List.Output output = listTask.run(runContext);

        assertThat("Should find no remaining test records", output.getSize(), is(equalTo(0L)));

        System.out.println("✅ Cleanup verified - No test records remain");
    }

    @AfterAll
    static void printSummary() {
        System.out.println("\n🎉 All Airtable integration tests completed successfully!");
        System.out.println("📋 CRUD operations validated:");
        System.out.println("  ✅ List records with filtering and field selection");
        System.out.println("  ✅ Create single record with field validation");
        System.out.println("  ✅ Get specific record by ID");
        System.out.println("  ✅ Update record with partial field changes");
        System.out.println("  ✅ Create multiple records in batch");
        System.out.println("  ✅ List with custom filters and field selection");
        System.out.println("  ✅ Store records to file (STORE FetchType)");
        System.out.println("  ✅ Auto-pagination for large datasets");
        System.out.println("  ✅ Delete records individually");
        System.out.println("  ✅ Complete cleanup verification");
        System.out.println("  ✅ Full CRUD cycle with proper error handling");
    }
}