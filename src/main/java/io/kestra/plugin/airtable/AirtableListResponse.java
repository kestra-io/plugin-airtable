package io.kestra.plugin.airtable;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;

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