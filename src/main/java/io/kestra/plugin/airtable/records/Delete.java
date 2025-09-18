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

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Delete a record from an Airtable table",
    description = "Delete a specific record by its ID from an Airtable table. This operation cannot be undone."
)
@Plugin(
    examples = {
        @Example(
            title = "Delete a completed task",
            full = true,
            code = """
                id: delete_completed_task
                namespace: company.airtable

                tasks:
                  - id: delete_task
                    type: io.kestra.plugin.airtable.records.Delete
                    baseId: "{{ secret('AIRTABLE_BASE_ID') }}"
                    tableId: "Tasks"
                    recordId: "recXXXXXXXXXXXXXX"
                    apiKey: "{{ secret('AIRTABLE_API_KEY') }}"
                """
        ),
        @Example(
            title = "Delete record from workflow output",
            full = true,
            code = """
                id: delete_from_previous_output
                namespace: company.airtable

                tasks:
                  - id: find_record_to_delete
                    type: io.kestra.plugin.airtable.records.List
                    baseId: "{{ secret('AIRTABLE_BASE_ID') }}"
                    tableId: "Temporary"
                    apiKey: "{{ secret('AIRTABLE_API_KEY') }}"
                    filterByFormula: "{Status} = 'To Delete'"
                    fetchType: FETCH_ONE

                  - id: delete_found_record
                    type: io.kestra.plugin.airtable.records.Delete
                    baseId: "{{ secret('AIRTABLE_BASE_ID') }}"
                    tableId: "Temporary"
                    recordId: "{{ outputs.find_record_to_delete.row.id }}"
                    apiKey: "{{ secret('AIRTABLE_API_KEY') }}"
                """
        ),
        @Example(
            title = "Delete with confirmation using If flow",
            full = true,
            code = """
                id: delete_with_confirmation
                namespace: company.airtable

                tasks:
                  - id: confirm_deletion
                    type: io.kestra.plugin.core.flow.If
                    condition: "{{ inputs.confirm_delete == 'yes' }}"
                    then:
                      - id: delete_record
                        type: io.kestra.plugin.airtable.records.Delete
                        baseId: "{{ secret('AIRTABLE_BASE_ID') }}"
                        tableId: "Orders"
                        recordId: "{{ inputs.order_id }}"
                        apiKey: "{{ secret('AIRTABLE_API_KEY') }}"
                    else:
                      - id: skip_deletion
                        type: io.kestra.plugin.core.log.Log
                        message: "Deletion cancelled - confirmation not provided"
                """
        )
    }
)
public class Delete extends Task implements RunnableTask<Delete.Output> {

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
        description = "The ID of the record to delete (starts with 'rec')"
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

    @Override
    public Output run(RunContext runContext) throws Exception {
        Logger logger = runContext.logger();

        // Render properties
        String rBaseId = runContext.render(this.baseId).as(String.class).orElseThrow();
        String rTableId = runContext.render(this.tableId).as(String.class).orElseThrow();
        String rRecordId = runContext.render(this.recordId).as(String.class).orElseThrow();
        String rApiKey = runContext.render(this.apiKey).as(String.class).orElseThrow();

        logger.info("Deleting record {} from Airtable base: {} table: {}", rRecordId, rBaseId, rTableId);

        AirtableClient client = new AirtableClient(rApiKey);
        AirtableRecord deletedRecord = client.deleteRecord(rBaseId, rTableId, rRecordId);

        logger.info("Successfully deleted record: {}", deletedRecord.getId());

        return Output.builder()
            .recordId(deletedRecord.getId())
            .deleted(true)
            .build();
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(
            title = "Record ID",
            description = "The ID of the deleted record"
        )
        private final String recordId;

        @Schema(
            title = "Deleted",
            description = "Confirmation that the record was deleted"
        )
        private final Boolean deleted;
    }
}