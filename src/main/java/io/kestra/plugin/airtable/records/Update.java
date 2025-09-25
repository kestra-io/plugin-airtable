package io.kestra.plugin.airtable.records;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
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
    title = "Update a record in an Airtable table",
    description = "Update fields in an existing record by its ID. Only specified fields will be updated (PATCH operation)."
)
@Plugin(
    examples = {
        @Example(
            title = "Update task status",
            full = true,
            code = """
                id: update_task_status
                namespace: company.airtable

                tasks:
                  - id: update_task
                    type: io.kestra.plugin.airtable.records.Update
                    baseId: "{{ secret('AIRTABLE_BASE_ID') }}"
                    tableId: "Tasks"
                    recordId: "recXXXXXXXXXXXXXX"
                    apiKey: "{{ secret('AIRTABLE_PERSONAL_ACCESS_TOKEN') }}"
                    fields:
                      "Status": "In Progress"
                      "Progress": 50
                      "Last Updated": "{{ now() }}"
                    typecast: true
                """
        ),
        @Example(
            title = "Update customer information",
            full = true,
            code = """
                id: update_customer
                namespace: company.airtable

                tasks:
                  - id: update_customer_info
                    type: io.kestra.plugin.airtable.records.Update
                    baseId: "{{ secret('AIRTABLE_BASE_ID') }}"
                    tableId: "Customers"
                    recordId: "{{ inputs.customer_record_id }}"
                    apiKey: "{{ secret('AIRTABLE_PERSONAL_ACCESS_TOKEN') }}"
                    fields:
                      "Email": "{{ inputs.new_email }}"
                      "Phone": "{{ inputs.new_phone }}"
                      "Last Contact": "{{ now() | date('yyyy-MM-dd') }}"
                """
        ),
        @Example(
            title = "Update with conditional data",
            full = true,
            code = """
                id: conditional_update
                namespace: company.airtable

                tasks:
                  - id: update_conditionally
                    type: io.kestra.plugin.airtable.records.Update
                    baseId: "{{ secret('AIRTABLE_BASE_ID') }}"
                    tableId: "Orders"
                    recordId: "{{ inputs.order_id }}"
                    apiKey: "{{ secret('AIRTABLE_PERSONAL_ACCESS_TOKEN') }}"
                    fields:
                      "Status": "{{ inputs.status }}"
                      "Completed Date": "{{ inputs.status == 'Completed' ? now() : null }}"
                      "Notes": "Updated via Kestra workflow"
                """
        )
    }
)
public class Update extends Task implements RunnableTask<Update.Output> {

    @Schema(
        title = "Airtable base ID",
        description = "The ID of the Airtable base (starts with 'app')"
    )
    @NotNull
    private Property<String> baseId;

    @Schema(
        title = "Table ID or name",
        description = "The ID or name of the table within the base"
    )
    @NotNull
    private Property<String> tableId;

    @Schema(
        title = "Record ID",
        description = "The ID of the record to update (starts with 'rec')"
    )
    @NotNull
    private Property<String> recordId;

    @Schema(
        title = "API key",
        description = "Airtable API key for authentication"
    )
    @NotNull
    private Property<String> apiKey;

    @Schema(
        title = "Fields to update",
        description = "Map of field names to new values. Only these fields will be updated."
    )
    @NotNull
    private Property<Map<String, Object>> fields;

    @Schema(
        title = "Typecast",
        description = "Enable automatic data conversion from string values"
    )
    @Builder.Default
    private Property<Boolean> typecast = Property.ofValue(false);

    @Override
    public Output run(RunContext runContext) throws Exception {
        Logger logger = runContext.logger();

        // Render properties
        String rBaseId = runContext.render(this.baseId).as(String.class).orElseThrow();
        String rTableId = runContext.render(this.tableId).as(String.class).orElseThrow();
        String rRecordId = runContext.render(this.recordId).as(String.class).orElseThrow();
        String rApiKey = runContext.render(this.apiKey).as(String.class).orElseThrow();
        Map<String, Object> rFields = runContext.render(this.fields).asMap(String.class, Object.class);
        Boolean rTypecast = runContext.render(this.typecast).as(Boolean.class).orElse(false);

        if (rFields == null || rFields.isEmpty()) {
            throw new IllegalArgumentException("Fields to update must be provided and cannot be empty");
        }

        logger.info("Updating record {} in Airtable base: {} table: {} with {} fields",
            rRecordId, rBaseId, rTableId, rFields.size());

        AirtableClient client = new AirtableClient(rApiKey, runContext);
        AirtableRecord updatedRecord = client.updateRecord(rBaseId, rTableId, rRecordId, rFields, rTypecast);

        logger.info("Successfully updated record: {}", updatedRecord.getId());

        // Convert record to output format
        Map<String, Object> recordMap = new HashMap<>();
        recordMap.put("id", updatedRecord.getId());
        if (updatedRecord.getCreatedTime() != null) {
            recordMap.put("createdTime", updatedRecord.getCreatedTime());
        }
        if (updatedRecord.getFields() != null) {
            recordMap.put("fields", updatedRecord.getFields());
        }

        return Output.builder()
            .record(recordMap)
            .recordId(updatedRecord.getId())
            .build();
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(
            title = "Updated record",
            description = "The updated Airtable record with id, createdTime, and all fields (including updated ones)"
        )
        private final Map<String, Object> record;

        @Schema(
            title = "Record ID",
            description = "The ID of the updated record"
        )
        private final String recordId;
    }
}