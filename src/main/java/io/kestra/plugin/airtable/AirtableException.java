package io.kestra.plugin.airtable;

/**
 * Exception thrown when Airtable API operations fail.
 */
public class AirtableException extends Exception {

    public AirtableException(String message) {
        super(message);
    }

    public AirtableException(String message, Throwable cause) {
        super(message, cause);
    }
}