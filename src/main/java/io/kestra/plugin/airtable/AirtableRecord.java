package io.kestra.plugin.airtable;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;

import java.util.Map;

/**
 * Data model representing an Airtable record.
 */
@AllArgsConstructor
@Getter
@ToString
public class AirtableRecord {

    /**
     * Unique record ID assigned by Airtable.
     */
    private final String id;

    /**
     * ISO timestamp when the record was created.
     */
    private final String createdTime;

    /**
     * Map of field names to field values.
     */
    private final Map<String, Object> fields;

    /**
     * Check if this record was deleted (deleted records only have ID).
     */
    public boolean isDeleted() {
        return fields == null && createdTime == null;
    }
}