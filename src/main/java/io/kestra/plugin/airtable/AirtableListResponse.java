package io.kestra.plugin.airtable;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;

import java.util.List;

/**
 * Response from Airtable list records API containing records and pagination info.
 */
@AllArgsConstructor
@Getter
@ToString
public class AirtableListResponse {

    /**
     * List of records returned by the API.
     */
    private final List<AirtableRecord> records;

    /**
     * Offset for pagination. If present, there are more records to fetch.
     */
    private final String offset;

    /**
     * Check if there are more pages available.
     */
    public boolean hasMorePages() {
        return offset != null && !offset.trim().isEmpty();
    }
}