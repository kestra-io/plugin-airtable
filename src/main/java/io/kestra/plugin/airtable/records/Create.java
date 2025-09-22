package io.kestra.plugin.airtable.records;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.runners.RunContext;
import io.kestra.plugin.airtable.AirtableClient;
import io.kestra.plugin.airtable.AirtableRecord;

import static io.kestra.plugin.airtable.AirtableClient.MAX_RECORDS_PER_BATCH;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Create a new record in an Airtable table",
    description = "Create one or more new records in an Airtable table. Can create a single record or multiple records (max 10) in one operation."
)
@Plugin(
    examples = {
        @Example(
            title = "Create a single task record",
            full = true,
            code = """
                id: create_airtable_task
                namespace: company.airtable

                tasks:
                  - id: create_task
                    type: io.kestra.plugin.airtable.records.Create
                    baseId: "{{ secret('AIRTABLE_BASE_ID') }}"
                    tableId: "Tasks"
                    apiKey: "{{ secret('AIRTABLE_API_KEY') }}"
                    fields:
                      "Task Name": "Implement new feature"
                      "Status": "Todo"
                      "Priority": "High"
                      "Due Date": "2024-12-31"
                    typecast: true
                """
        ),
        @Example(
            title = "Create multiple customer records",
            full = true,
            code = """
                id: create_customers
                namespace: company.airtable

                tasks:
                  - id: create_customer_records
                    type: io.kestra.plugin.airtable.records.Create
                    baseId: "{{ secret('AIRTABLE_BASE_ID') }}"
                    tableId: "Customers"
                    apiKey: "{{ secret('AIRTABLE_API_KEY') }}"
                    records:
                      - "Name": "Acme Corp"
                        "Email": "contact@acme.com"
                        "Status": "Active"
                      - "Name": "Beta Inc"
                        "Email": "hello@beta.com"
                        "Status": "Prospect"
                    typecast: true
                """
        ),
        @Example(
            title = "Create record from dynamic data",
            full = true,
            code = """
                id: create_from_input
                namespace: company.airtable

                inputs:
                  - id: customer_name
                    type: STRING
                    required: true
                  - id: product
                    type: STRING
                    required: true
                  - id: amount
                    type: FLOAT
                    required: true

                tasks:
                  - id: create_dynamic_record
                    type: io.kestra.plugin.airtable.records.Create
                    baseId: "{{ secret('AIRTABLE_BASE_ID') }}"
                    tableId: "Orders"
                    apiKey: "{{ secret('AIRTABLE_API_KEY') }}"
                    fields:
                      "Customer": "{{ inputs.customer_name }}"
                      "Product": "{{ inputs.product }}"
                      "Amount": "{{ inputs.amount }}"
                      "Order Date": "{{ now() }}"
                """
        )
    }
)
public class Create extends Task implements RunnableTask<Create.Output> {

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
        title = "API key",
        description = "Airtable API key for authentication"
    )
    @NotNull
    private Property<String> apiKey;

    @Schema(
        title = "Fields for single record",
        description = "Field values for creating a single record. Use this OR records, not both."
    )
    private Property<Map<String, Object>> fields;

    @Schema(
        title = "Multiple records",
        description = "List of records to create (max " + MAX_RECORDS_PER_BATCH + "). Each record is a map of field names to values. Use this OR fields, not both."
    )
    private Property<List<Map<String, Object>>> records;

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
        String rApiKey = runContext.render(this.apiKey).as(String.class).orElseThrow();
        Map<String, Object> rFields = runContext.render(this.fields).asMap(String.class, Object.class);
        List<Map<String, Object>> rRecords = runContext.render(this.records).asList(Map.class);
        Boolean rTypecast = runContext.render(this.typecast).as(Boolean.class).orElse(false);

        // Validate input - either fields or records should be provided, not both
        if ((rFields == null || rFields.isEmpty()) && (rRecords == null || rRecords.isEmpty())) {
            throw new IllegalArgumentException("Either 'fields' for single record or 'records' for multiple records must be provided");
        }

        if (rFields != null && !rFields.isEmpty() && rRecords != null && !rRecords.isEmpty()) {
            throw new IllegalArgumentException("Cannot specify both 'fields' and 'records'. Use one or the other.");
        }

        AirtableClient client = new AirtableClient(rApiKey, runContext);

        if (rFields != null && !rFields.isEmpty()) {
            // Create single record
            logger.info("Creating single record in Airtable base: {} table: {}", rBaseId, rTableId);

            AirtableRecord createdRecord = client.createRecord(rBaseId, rTableId, rFields, rTypecast);

            logger.info("Successfully created record: {}", createdRecord.getId());

            Map<String, Object> recordMap = convertRecordToMap(createdRecord);
            return Output.builder()
                .record(recordMap)
                .records(List.of(recordMap))
                .recordIds(List.of(createdRecord.getId()))
                .build();
        } else {
            // Create multiple records
            if (rRecords.size() > MAX_RECORDS_PER_BATCH) {
                throw new IllegalArgumentException("Cannot create more than " + MAX_RECORDS_PER_BATCH + " records at once");
            }

            logger.info("Creating {} records in Airtable base: {} table: {}", rRecords.size(), rBaseId, rTableId);

            List<AirtableRecord> createdRecords = client.createRecords(rBaseId, rTableId, rRecords, rTypecast);

            logger.info("Successfully created {} records", createdRecords.size());

            List<Map<String, Object>> recordMaps = new ArrayList<>();
            List<String> recordIds = new ArrayList<>();

            for (AirtableRecord record : createdRecords) {
                recordMaps.add(convertRecordToMap(record));
                recordIds.add(record.getId());
            }

            return Output.builder()
                .record(recordMaps.isEmpty() ? null : recordMaps.get(0))
                .records(recordMaps)
                .recordIds(recordIds)
                .build();
        }
    }

    private Map<String, Object> convertRecordToMap(AirtableRecord record) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", record.getId());
        if (record.getCreatedTime() != null) {
            map.put("createdTime", record.getCreatedTime());
        }
        if (record.getFields() != null) {
            map.put("fields", record.getFields());
        }
        return map;
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(
            title = "Created record",
            description = "The first created record (for single record creation or first of multiple)"
        )
        private final Map<String, Object> record;

        @Schema(
            title = "All created records",
            description = "List of all created records"
        )
        private final List<Map<String, Object>> records;

        @Schema(
            title = "Record IDs",
            description = "List of IDs of the created records"
        )
        private final List<String> recordIds;
    }
}