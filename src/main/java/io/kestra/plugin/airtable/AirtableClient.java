package io.kestra.plugin.airtable;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.kestra.core.http.HttpRequest;
import io.kestra.core.http.HttpResponse;
import io.kestra.core.http.client.HttpClient;
import io.kestra.core.http.client.HttpClientResponseException;
import io.kestra.core.runners.RunContext;

/**
 * HTTP client for interacting with Airtable REST API.
 * Handles authentication, request building, and response parsing.
 */
public class AirtableClient {

    private static final Logger logger = LoggerFactory.getLogger(AirtableClient.class);
    private static final String BASE_URL = "https://api.airtable.com/v0";
    public static final int MAX_RECORDS_PER_BATCH = 10;

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;

    public AirtableClient(String apiKey, RunContext runContext) throws Exception {
        this.apiKey = apiKey;
        this.objectMapper = new ObjectMapper();
        this.httpClient = HttpClient.builder()
            .runContext(runContext)
            .build();
    }

    /**
     * List records from a table with optional filtering and pagination.
     */
    public AirtableListResponse listRecords(String baseId, String tableId, String filterByFormula,
        List<String> fields, Integer maxRecords, String view,
        String offset) throws Exception {
        StringBuilder urlBuilder = new StringBuilder(tableUrl(baseId, tableId));
        boolean hasParams = false;

        if (filterByFormula != null && !filterByFormula.trim().isEmpty()) {
            urlBuilder.append(hasParams ? "&" : "?").append("filterByFormula=")
                .append(URLEncoder.encode(filterByFormula, StandardCharsets.UTF_8));
            hasParams = true;
        }
        if (fields != null && !fields.isEmpty()) {
            for (String field : fields) {
                urlBuilder.append(hasParams ? "&" : "?").append("fields[]=")
                    .append(URLEncoder.encode(field, StandardCharsets.UTF_8));
                hasParams = true;
            }
        }
        if (maxRecords != null) {
            urlBuilder.append(hasParams ? "&" : "?").append("maxRecords=").append(maxRecords);
            hasParams = true;
        }
        if (view != null && !view.trim().isEmpty()) {
            urlBuilder.append(hasParams ? "&" : "?").append("view=")
                .append(URLEncoder.encode(view, StandardCharsets.UTF_8));
            hasParams = true;
        }
        if (offset != null && !offset.trim().isEmpty()) {
            urlBuilder.append(hasParams ? "&" : "?").append("offset=")
                .append(URLEncoder.encode(offset, StandardCharsets.UTF_8));
        }

        HttpRequest request = HttpRequest.builder()
            .method("GET")
            .uri(URI.create(urlBuilder.toString()))
            .addHeader("Authorization", "Bearer " + apiKey)
            .build();

        logger.debug("Making GET request to: {}", urlBuilder.toString());

        try {
            HttpResponse<String> response = httpClient.request(request, String.class);
            return parseListResponse(response.getBody());
        } catch (HttpClientResponseException e) {
            throw toAirtableException("list records", e);
        }
    }

    /**
     * Get a single record by ID.
     */
    public AirtableRecord getRecord(String baseId, String tableId, String recordId, List<String> fields)
        throws Exception {
        var url = tableUrl(baseId, tableId) + "/" + recordId;

        var request = HttpRequest.builder()
            .method("GET")
            .uri(URI.create(url))
            .addHeader("Authorization", "Bearer " + apiKey)
            .build();

        logger.debug("Making GET request to: {}", url);

        try {
            var response = httpClient.request(request, String.class);
            var rec = parseRecordResponse(response.getBody());
            return filterRecordFields(rec, fields);
        } catch (HttpClientResponseException e) {
            throw toAirtableException("get record", e);
        }
    }

    AirtableRecord filterRecordFields(AirtableRecord rec, List<String> fields) {
        if (fields != null && !fields.isEmpty()) {
            var filteredFields = new HashMap<String, Object>();
            if (rec.getFields() != null) {
                for (var field : fields) {
                    if (rec.getFields().containsKey(field)) {
                        filteredFields.put(field, rec.getFields().get(field));
                    }
                }
            }
            return new AirtableRecord(rec.getId(), rec.getCreatedTime(), filteredFields);
        }
        return rec;
    }

    /**
     * Create a new record.
     */
    public AirtableRecord createRecord(String baseId, String tableId, Map<String, Object> fields, Boolean typecast)
        throws Exception {
        String url = tableUrl(baseId, tableId);

        Map<String, Object> requestBody = Map.of("fields", fields);
        if (typecast != null && typecast) {
            requestBody = Map.of("fields", fields, "typecast", true);
        }

        HttpRequest request = HttpRequest.builder()
            .method("POST")
            .uri(URI.create(url))
            .addHeader("Authorization", "Bearer " + apiKey)
            .body(
                HttpRequest.JsonRequestBody.builder()
                    .content(requestBody)
                    .build()
            )
            .build();

        logger.debug("Making POST request to: {}", url);

        try {
            HttpResponse<String> response = httpClient.request(request, String.class);
            return parseRecordResponse(response.getBody());
        } catch (HttpClientResponseException e) {
            throw toAirtableException("create record", e);
        }
    }

    /**
     * Create multiple records at once (max " + MAX_RECORDS_PER_BATCH + ").
     */
    public List<AirtableRecord> createRecords(String baseId, String tableId, List<Map<String, Object>> recordsFields,
        Boolean typecast) throws Exception {
        if (recordsFields.size() > MAX_RECORDS_PER_BATCH) {
            throw new IllegalArgumentException("Cannot create more than " + MAX_RECORDS_PER_BATCH + " records at once");
        }

        String url = tableUrl(baseId, tableId);

        List<Map<String, Object>> records = new ArrayList<>();
        for (Map<String, Object> fields : recordsFields) {
            records.add(Map.of("fields", fields));
        }

        Map<String, Object> requestBody = Map.of("records", records);
        if (typecast != null && typecast) {
            requestBody = Map.of("records", records, "typecast", true);
        }

        HttpRequest request = HttpRequest.builder()
            .method("POST")
            .uri(URI.create(url))
            .addHeader("Authorization", "Bearer " + apiKey)
            .body(
                HttpRequest.JsonRequestBody.builder()
                    .content(requestBody)
                    .build()
            )
            .build();

        logger.debug("Making POST request to: {}", url);

        try {
            HttpResponse<String> response = httpClient.request(request, String.class);
            return parseListResponse(response.getBody()).getRecords();
        } catch (HttpClientResponseException e) {
            throw toAirtableException("create records", e);
        }
    }

    /**
     * Update a record.
     */
    public AirtableRecord updateRecord(String baseId, String tableId, String recordId,
        Map<String, Object> fields, Boolean typecast)
        throws Exception {
        String url = tableUrl(baseId, tableId) + "/" + recordId;

        Map<String, Object> requestBody = Map.of("fields", fields);
        if (typecast != null && typecast) {
            requestBody = Map.of("fields", fields, "typecast", true);
        }

        HttpRequest request = HttpRequest.builder()
            .method("PATCH")
            .uri(URI.create(url))
            .addHeader("Authorization", "Bearer " + apiKey)
            .body(
                HttpRequest.JsonRequestBody.builder()
                    .content(requestBody)
                    .build()
            )
            .build();

        logger.debug("Making PATCH request to: {}", url);

        try {
            HttpResponse<String> response = httpClient.request(request, String.class);
            return parseRecordResponse(response.getBody());
        } catch (HttpClientResponseException e) {
            throw toAirtableException("update record", e);
        }
    }

    /**
     * Delete a record.
     */
    public AirtableRecord deleteRecord(String baseId, String tableId, String recordId)
        throws Exception {
        String url = tableUrl(baseId, tableId) + "/" + recordId;

        HttpRequest request = HttpRequest.builder()
            .method("DELETE")
            .uri(URI.create(url))
            .addHeader("Authorization", "Bearer " + apiKey)
            .build();

        logger.debug("Making DELETE request to: {}", url);

        try {
            HttpResponse<String> response = httpClient.request(request, String.class);
            return parseRecordResponse(response.getBody());
        } catch (HttpClientResponseException e) {
            throw toAirtableException("delete record", e);
        }
    }

    static String tableUrl(String baseId, String tableId) {
        return BASE_URL + "/" + baseId + "/" + encodeTableId(tableId);
    }

    static String encodeTableId(String tableId) {
        return URLEncoder.encode(tableId, StandardCharsets.UTF_8).replace("+", "%20");
    }

    static AirtableException toAirtableException(String action, HttpClientResponseException e) {
        var response = e.getResponse();
        if (response == null) {
            return new AirtableException("Failed to " + action + ": unknown - unknown");
        }
        var body = response.getBody() instanceof byte[] bytes ? new String(bytes, StandardCharsets.UTF_8) : String.valueOf(response.getBody());
        return new AirtableException("Failed to " + action + ": " + response.getStatus().getCode() + " - " + body);
    }

    /**
     * Parse list response from Airtable API.
     */
    AirtableListResponse parseListResponse(String responseBody) throws JsonProcessingException {
        JsonNode jsonNode = objectMapper.readTree(responseBody);

        List<AirtableRecord> records = new ArrayList<>();
        JsonNode recordsNode = jsonNode.get("records");
        if (recordsNode != null && recordsNode.isArray()) {
            for (JsonNode recordNode : recordsNode) {
                records.add(parseRecord(recordNode));
            }
        }

        String offset = null;
        JsonNode offsetNode = jsonNode.get("offset");
        if (offsetNode != null && !offsetNode.isNull()) {
            offset = offsetNode.asText();
        }

        return new AirtableListResponse(records, offset);
    }

    /**
     * Parse single record response from Airtable API.
     */
    AirtableRecord parseRecordResponse(String responseBody) throws JsonProcessingException {
        JsonNode jsonNode = objectMapper.readTree(responseBody);
        return parseRecord(jsonNode);
    }

    /**
     * Parse individual record from JSON node.
     */
    private AirtableRecord parseRecord(JsonNode recordNode) throws JsonProcessingException {
        String id = recordNode.get("id").asText();
        String createdTime = null;
        if (recordNode.has("createdTime")) {
            createdTime = recordNode.get("createdTime").asText();
        }

        Map<String, Object> fields = null;
        if (recordNode.has("fields")) {
            fields = objectMapper.convertValue(recordNode.get("fields"), Map.class);
        }

        return new AirtableRecord(id, createdTime, fields);
    }
}