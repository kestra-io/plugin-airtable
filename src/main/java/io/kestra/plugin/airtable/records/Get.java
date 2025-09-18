package io.kestra.plugin.airtable.records;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.runners.RunContext;
import io.kestra.plugin.airtable.AirtableClient;
import io.kestra.plugin.airtable.AirtableRecord;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Map;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Get a single record from an Airtable table",
    description = "Retrieve a specific record by its ID from an Airtable table with optional field selection."
)
@Plugin(
    examples = {
        @Example(
            title = "Get a specific task record",
            full = true,
            code = """
                id: get_airtable_task
                namespace: company.airtable

                tasks:
                  - id: get_task
                    type: io.kestra.plugin.airtable.records.Get
                    baseId: "{{ secret('AIRTABLE_BASE_ID') }}"
                    tableId: "Tasks"
                    recordId: "recXXXXXXXXXXXXXX"
                    apiKey: "{{ secret('AIRTABLE_API_KEY') }}"
                    fields: ["Task Name", "Status", "Priority", "Due Date"]
                """
        ),
        @Example(
            title = "Get a customer record with all fields",
            full = true,
            code = """
                id: get_customer
                namespace: company.airtable

                tasks:
                  - id: get_customer_record
                    type: io.kestra.plugin.airtable.records.Get
                    baseId: "{{ secret('AIRTABLE_BASE_ID') }}"
                    tableId: "Customers"
                    recordId: "{{ inputs.customer_id }}"
                    apiKey: "{{ secret('AIRTABLE_API_KEY') }}"
                """
        )
    }
)
public class Get extends Task implements RunnableTask<Get.Output> {

    @Schema(
        title = "Airtable base ID",
        description = "The ID of the Airtable base (starts with 'app')"
    )
    @PluginProperty(dynamic = true)
    @NotNull
    private Property<String> baseId;

    @Schema(
        title = "Table ID or name",
        description = "The ID or name of the table within the base"
    )
    @PluginProperty(dynamic = true)
    @NotNull
    private Property<String> tableId;

    @Schema(
        title = "Record ID",
        description = "The ID of the record to retrieve (starts with 'rec')"
    )
    @PluginProperty(dynamic = true)
    @NotNull
    private Property<String> recordId;

    @Schema(
        title = "API key",
        description = "Airtable API key for authentication"
    )
    @PluginProperty(dynamic = true)
    @NotNull
    private Property<String> apiKey;

    @Schema(
        title = "Fields",
        description = "List of field names to retrieve. If not specified, all fields are returned."
    )
    @PluginProperty(dynamic = true)
    private Property<java.util.List<String>> fields;

    @Override
    public Output run(RunContext runContext) throws Exception {
        Logger logger = runContext.logger();

        // Render properties
        String rBaseId = runContext.render(this.baseId).as(String.class).orElseThrow();
        String rTableId = runContext.render(this.tableId).as(String.class).orElseThrow();
        String rRecordId = runContext.render(this.recordId).as(String.class).orElseThrow();
        String rApiKey = runContext.render(this.apiKey).as(String.class).orElseThrow();
        java.util.List<String> rFields = runContext.render(this.fields).asList(String.class);

        logger.info("Getting record {} from Airtable base: {} table: {}", rRecordId, rBaseId, rTableId);

        AirtableClient client = new AirtableClient(rApiKey);
        AirtableRecord record = client.getRecord(rBaseId, rTableId, rRecordId, rFields);

        logger.info("Successfully retrieved record: {}", record.getId());

        // Convert record to output format
        Map<String, Object> recordMap = new HashMap<>();
        recordMap.put("id", record.getId());
        if (record.getCreatedTime() != null) {
            recordMap.put("createdTime", record.getCreatedTime());
        }
        if (record.getFields() != null) {
            recordMap.put("fields", record.getFields());
        }

        return Output.builder()
            .record(recordMap)
            .build();
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(
            title = "Retrieved record",
            description = "The Airtable record with id, createdTime, and fields"
        )
        private final Map<String, Object> record;
    }
}