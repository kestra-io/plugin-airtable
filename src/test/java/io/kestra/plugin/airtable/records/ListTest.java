package io.kestra.plugin.airtable.records;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.common.FetchType;
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
class ListTest {

    @Inject
    private RunContextFactory runContextFactory;

    @Test
    void shouldValidateRequiredProperties() {
        List task = List.builder()
            .id("test-task")
            .type(List.class.getName())
            .baseId(Property.ofValue("appXXXXXXXXXXXXXX"))
            .tableId(Property.ofValue("Table1"))
            // Missing apiKey
            .build();

        RunContext runContext = runContextFactory.of();

        assertThrows(Exception.class, () -> task.run(runContext));
    }

    @Test
    void shouldBuildTaskWithAllProperties() {
        List task = List.builder()
            .id("test-task-full")
            .type(List.class.getName())
            .baseId(Property.ofValue("appXXXXXXXXXXXXXX"))
            .tableId(Property.ofValue("Table1"))
            .apiKey(Property.ofValue("fake-api-key"))
            .filterByFormula(Property.ofValue("AND({Status} != 'Done', {Priority} = 'High')"))
            .fields(Property.ofValue(java.util.List.of("Name", "Status", "Priority")))
            .maxRecords(Property.ofValue(50))
            .view(Property.ofValue("Active Tasks"))
            .enableAutoPagination(Property.ofValue(true))
            .fetchType(Property.ofValue(FetchType.FETCH))
            .build();

        // Verify all properties are properly set
        assertThat(task.getBaseId(), is(notNullValue()));
        assertThat(task.getTableId(), is(notNullValue()));
        assertThat(task.getApiKey(), is(notNullValue()));
        assertThat(task.getFilterByFormula(), is(notNullValue()));
        assertThat(task.getFields(), is(notNullValue()));
        assertThat(task.getMaxRecords(), is(notNullValue()));
        assertThat(task.getView(), is(notNullValue()));
        assertThat(task.getEnableAutoPagination(), is(notNullValue()));
        assertThat(task.getFetchType(), is(notNullValue()));

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
    @EnabledIfEnvironmentVariable(named = "AIRTABLE_PERSONAL_ACCESS_TOKEN", matches = ".*")
    @EnabledIfEnvironmentVariable(named = "AIRTABLE_BASE_ID", matches = ".*")
    void shouldExecuteRealAirtableRequest() throws Exception {
        // This test requires real Airtable credentials
        String apiKey = System.getenv("AIRTABLE_PERSONAL_ACCESS_TOKEN");
        String baseId = System.getenv("AIRTABLE_BASE_ID");
        String tableId = System.getenv("AIRTABLE_TABLE_ID") != null ?
            System.getenv("AIRTABLE_TABLE_ID") : "Table1";

        List task = List.builder()
            .id("test-integration")
            .type(List.class.getName())
            .baseId(Property.ofValue(baseId))
            .tableId(Property.ofValue(tableId))
            .apiKey(Property.ofValue(apiKey))
            .maxRecords(Property.ofValue(5))
            .fetchType(Property.ofValue(FetchType.FETCH))
            .build();

        RunContext runContext = runContextFactory.of();
        List.Output output = task.run(runContext);

        // Verify actual API execution results
        assertThat(output, is(notNullValue()));
        assertThat(output.getSize(), is(notNullValue()));
        assertThat(output.getSize(), is(greaterThanOrEqualTo(0L)));
        assertThat(output.getRows(), is(notNullValue()));

        // Verify response structure
        if (output.getSize() > 0) {
            java.util.List<Map<String, Object>> rows = output.getRows();
            assertThat(rows.size(), is(lessThanOrEqualTo(5))); // Respects maxRecords

            Map<String, Object> firstRecord = rows.get(0);
            assertThat(firstRecord, hasKey("id"));
            assertThat(firstRecord, hasKey("fields"));
        }
    }
}