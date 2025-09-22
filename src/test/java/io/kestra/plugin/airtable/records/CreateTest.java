package io.kestra.plugin.airtable.records;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.utils.TestsUtils;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

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



}