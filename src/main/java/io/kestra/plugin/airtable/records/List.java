package io.kestra.plugin.airtable.records;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.models.tasks.common.FetchType;
import io.kestra.core.runners.RunContext;
import io.kestra.plugin.airtable.AirtableClient;
import io.kestra.plugin.airtable.AirtableListResponse;
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

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "List records from an Airtable table",
    description = "Retrieve records from an Airtable table with support for filtering, field selection, pagination, and different output formats."
)
@Plugin(
    examples = {
        @Example(
            title = "List high-priority tasks with filtering",
            full = true,
            code = """
                id: list_airtable_tasks
                namespace: company.airtable

                tasks:
                  - id: get_high_priority_tasks
                    type: io.kestra.plugin.airtable.records.List
                    baseId: "{{ secret('AIRTABLE_BASE_ID') }}"
                    tableId: "Tasks"
                    apiKey: "{{ secret('AIRTABLE_PERSONAL_ACCESS_TOKEN') }}"
                    filterByFormula: "AND({Status} != 'Done', {Priority} = 'High')"
                    fields: ["Task Name", "Status", "Priority", "Due Date"]
                    maxRecords: 50
                    fetchType: FETCH
                """
        ),
        @Example(
            title = "List all customers from a view",
            full = true,
            code = """
                id: list_customers
                namespace: company.airtable

                tasks:
                  - id: get_customers
                    type: io.kestra.plugin.airtable.records.List
                    baseId: "{{ secret('AIRTABLE_BASE_ID') }}"
                    tableId: "Customers"
                    apiKey: "{{ secret('AIRTABLE_PERSONAL_ACCESS_TOKEN') }}"
                    view: "Active Customers"
                    fetchType: STORE
                """
        ),
        @Example(
            title = "List records with auto-pagination",
            full = true,
            code = """
                id: list_all_records
                namespace: company.airtable

                tasks:
                  - id: get_all_records
                    type: io.kestra.plugin.airtable.records.List
                    baseId: "{{ secret('AIRTABLE_BASE_ID') }}"
                    tableId: "Orders"
                    apiKey: "{{ secret('AIRTABLE_PERSONAL_ACCESS_TOKEN') }}"
                    maxRecords: 100
                    enableAutoPagination: true
                    fetchType: FETCH
                """
        )
    }
)
public class List extends Task implements RunnableTask<List.Output> {

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
        title = "Filter by formula",
        description = "Airtable formula to filter records. Example: AND({Status} != 'Done', {Priority} = 'High')"
    )
    private Property<String> filterByFormula;

    @Schema(
        title = "Fields",
        description = "List of field names to retrieve. If not specified, all fields are returned."
    )
    private Property<java.util.List<String>> fields;

    @Schema(
        title = "Maximum records",
        description = "Maximum number of records to return per page (max 100)"
    )
    private Property<Integer> maxRecords;

    @Schema(
        title = "View",
        description = "Name or ID of a view to use. Records will be returned in the order of the view."
    )
    private Property<String> view;

    @Schema(
        title = "Enable auto-pagination",
        description = "Whether to automatically fetch all pages of results"
    )
    @Builder.Default
    private Property<Boolean> enableAutoPagination = Property.ofValue(false);

    @Schema(
        title = "Fetch type",
        description = "How to handle query results"
    )
    @NotNull
    @Builder.Default
    private Property<FetchType> fetchType = Property.ofValue(FetchType.FETCH);

    @Override
    public Output run(RunContext runContext) throws Exception {
        Logger logger = runContext.logger();

        // Render properties
        String rBaseId = runContext.render(this.baseId).as(String.class).orElseThrow();
        String rTableId = runContext.render(this.tableId).as(String.class).orElseThrow();
        String rApiKey = runContext.render(this.apiKey).as(String.class).orElseThrow();
        String rFilterByFormula = runContext.render(this.filterByFormula).as(String.class).orElse(null);
        java.util.List<String> rFields = runContext.render(this.fields).asList(String.class);
        Integer rMaxRecords = runContext.render(this.maxRecords).as(Integer.class).orElse(null);
        String rView = runContext.render(this.view).as(String.class).orElse(null);
        Boolean rEnableAutoPagination = runContext.render(this.enableAutoPagination).as(Boolean.class).orElse(false);
        FetchType rFetchType = runContext.render(this.fetchType).as(FetchType.class).orElse(FetchType.FETCH);

        logger.info("Listing records from Airtable base: {} table: {}", rBaseId, rTableId);

        AirtableClient client = new AirtableClient(rApiKey, runContext);
        java.util.List<AirtableRecord> allRecords = new ArrayList<>();
        String offset = null;
        int totalFetched = 0;

        do {
            logger.debug("Fetching records with offset: {}", offset);

            AirtableListResponse response = client.listRecords(
                rBaseId,
                rTableId,
                rFilterByFormula,
                rFields,
                rMaxRecords,
                rView,
                offset
            );

            allRecords.addAll(response.getRecords());
            totalFetched += response.getRecords().size();

            logger.debug("Fetched {} records in this batch, total: {}", response.getRecords().size(), totalFetched);

            offset = response.getOffset();

            // Continue if auto-pagination is enabled and there are more pages
        } while (rEnableAutoPagination && offset != null);

        logger.info("Successfully retrieved {} records from Airtable", totalFetched);

        // Build output based on fetch type
        return buildOutput(runContext, allRecords, rFetchType, totalFetched);
    }

    private Output buildOutput(RunContext runContext, java.util.List<AirtableRecord> records,
                             FetchType fetchType, int totalFetched) throws Exception {
        Output.OutputBuilder outputBuilder = Output.builder().size((long) totalFetched);

        switch (fetchType) {
            case FETCH_ONE:
                if (!records.isEmpty()) {
                    AirtableRecord firstRecord = records.get(0);
                    Map<String, Object> row = convertRecordToMap(firstRecord);
                    outputBuilder.row(row);
                }
                break;

            case FETCH:
                java.util.List<Map<String, Object>> rows = new ArrayList<>();
                for (AirtableRecord record : records) {
                    rows.add(convertRecordToMap(record));
                }
                outputBuilder.rows(rows);
                break;

            case STORE:
                // Store records as ION file
                StringBuilder ionContent = new StringBuilder();
                for (AirtableRecord record : records) {
                    Map<String, Object> recordMap = convertRecordToMap(record);
                    ionContent.append(recordMap.toString()).append("\n");
                }

                URI uri = runContext.storage().putFile(
                    new ByteArrayInputStream(ionContent.toString().getBytes()),
                    "airtable_records.ion"
                );
                outputBuilder.uri(uri);
                break;

            case NONE:
            default:
                // No output data
                break;
        }

        return outputBuilder.build();
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
            title = "Single record",
            description = "The first record when fetchType is FETCH_ONE"
        )
        private final Map<String, Object> row;

        @Schema(
            title = "List of records",
            description = "All records when fetchType is FETCH"
        )
        private final java.util.List<Map<String, Object>> rows;

        @Schema(
            title = "Storage URI",
            description = "URI of stored records file when fetchType is STORE"
        )
        private final URI uri;

        @Schema(
            title = "Total records",
            description = "Total number of records retrieved"
        )
        private final Long size;
    }
}