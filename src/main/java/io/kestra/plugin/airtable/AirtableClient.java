package io.kestra.plugin.airtable;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.kestra.core.http.HttpRequest;
import io.kestra.core.http.HttpResponse;
import io.kestra.core.http.client.HttpClient;
import io.kestra.core.http.client.HttpClientResponseException;
import io.kestra.core.runners.RunContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
        StringBuilder urlBuilder = new StringBuilder(BASE_URL + "/" + baseId + "/" + URLEncoder.encode(tableId, StandardCharsets.UTF_8));
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
            String statusCode = e.getResponse() != null ? String.valueOf(e.getResponse().getStatus().getCode()) : "unknown";
            String responseBody = e.getResponse() != null ? String.valueOf(e.getResponse().getBody()) : "unknown";
            throw new AirtableException("Failed to list records: " + statusCode + " - " + responseBody);
        }
    }

    /**
     * Get a single record by ID.
     */
    public AirtableRecord getRecord(String baseId, String tableId, String recordId, List<String> fields)
            throws Exception {
        StringBuilder urlBuilder = new StringBuilder(BASE_URL + "/" + baseId + "/" + URLEncoder.encode(tableId, StandardCharsets.UTF_8) + "/" + recordId);
        boolean hasParams = false;

        if (fields != null && !fields.isEmpty()) {
            for (String field : fields) {
                urlBuilder.append(hasParams ? "&" : "?").append("fields[]=")
                    .append(URLEncoder.encode(field, StandardCharsets.UTF_8));
                hasParams = true;
            }
        }

        HttpRequest request = HttpRequest.builder()
            .method("GET")
            .uri(URI.create(urlBuilder.toString()))
            .addHeader("Authorization", "Bearer " + apiKey)
            .build();

        logger.debug("Making GET request to: {}", urlBuilder.toString());

        try {
            HttpResponse<String> response = httpClient.request(request, String.class);
            return parseRecordResponse(response.getBody());
        } catch (HttpClientResponseException e) {
            String statusCode = e.getResponse() != null ? String.valueOf(e.getResponse().getStatus().getCode()) : "unknown";
            String responseBody = e.getResponse() != null ? String.valueOf(e.getResponse().getBody()) : "unknown";
            throw new AirtableException("Failed to get record: " + statusCode + " - " + responseBody);
        }
    }

    /**
     * Create a new record.
     */
    public AirtableRecord createRecord(String baseId, String tableId, Map<String, Object> fields, Boolean typecast)
            throws Exception {
        String url = BASE_URL + "/" + baseId + "/" + URLEncoder.encode(tableId, StandardCharsets.UTF_8);

        Map<String, Object> requestBody = Map.of("fields", fields);
        if (typecast != null && typecast) {
            requestBody = Map.of("fields", fields, "typecast", true);
        }

        HttpRequest request = HttpRequest.builder()
            .method("POST")
            .uri(URI.create(url))
            .addHeader("Authorization", "Bearer " + apiKey)
            .body(HttpRequest.JsonRequestBody.builder()
                .content(requestBody)
                .build())
            .build();

        logger.debug("Making POST request to: {}", url);

        try {
            HttpResponse<String> response = httpClient.request(request, String.class);
            return parseRecordResponse(response.getBody());
        } catch (HttpClientResponseException e) {
            String statusCode = e.getResponse() != null ? String.valueOf(e.getResponse().getStatus().getCode()) : "unknown";
            String responseBody = e.getResponse() != null ? String.valueOf(e.getResponse().getBody()) : "unknown";
            throw new AirtableException("Failed to create record: " + statusCode + " - " + responseBody);
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

        String url = BASE_URL + "/" + baseId + "/" + URLEncoder.encode(tableId, StandardCharsets.UTF_8);

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
            .body(HttpRequest.JsonRequestBody.builder()
                .content(requestBody)
                .build())
            .build();

        logger.debug("Making POST request to: {}", url);

        try {
            HttpResponse<String> response = httpClient.request(request, String.class);
            return parseListResponse(response.getBody()).getRecords();
        } catch (HttpClientResponseException e) {
            String statusCode = e.getResponse() != null ? String.valueOf(e.getResponse().getStatus().getCode()) : "unknown";
            String responseBody = e.getResponse() != null ? String.valueOf(e.getResponse().getBody()) : "unknown";
            throw new AirtableException("Failed to create records: " + statusCode + " - " + responseBody);
        }
    }

    /**
     * Update a record.
     */
    public AirtableRecord updateRecord(String baseId, String tableId, String recordId,
                                     Map<String, Object> fields, Boolean typecast)
            throws Exception {
        String url = BASE_URL + "/" + baseId + "/" + URLEncoder.encode(tableId, StandardCharsets.UTF_8) + "/" + recordId;

        Map<String, Object> requestBody = Map.of("fields", fields);
        if (typecast != null && typecast) {
            requestBody = Map.of("fields", fields, "typecast", true);
        }

        HttpRequest request = HttpRequest.builder()
            .method("PATCH")
            .uri(URI.create(url))
            .addHeader("Authorization", "Bearer " + apiKey)
            .body(HttpRequest.JsonRequestBody.builder()
                .content(requestBody)
                .build())
            .build();

        logger.debug("Making PATCH request to: {}", url);

        try {
            HttpResponse<String> response = httpClient.request(request, String.class);
            return parseRecordResponse(response.getBody());
        } catch (HttpClientResponseException e) {
            String statusCode = e.getResponse() != null ? String.valueOf(e.getResponse().getStatus().getCode()) : "unknown";
            String responseBody = e.getResponse() != null ? String.valueOf(e.getResponse().getBody()) : "unknown";
            throw new AirtableException("Failed to update record: " + statusCode + " - " + responseBody);
        }
    }

    /**
     * Delete a record.
     */
    public AirtableRecord deleteRecord(String baseId, String tableId, String recordId)
            throws Exception {
        String url = BASE_URL + "/" + baseId + "/" + URLEncoder.encode(tableId, StandardCharsets.UTF_8) + "/" + recordId;

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
            String statusCode = e.getResponse() != null ? String.valueOf(e.getResponse().getStatus().getCode()) : "unknown";
            String responseBody = e.getResponse() != null ? String.valueOf(e.getResponse().getBody()) : "unknown";
            throw new AirtableException("Failed to delete record: " + statusCode + " - " + responseBody);
        }
    }

    /**
     * Parse list response from Airtable API.
     */
    private AirtableListResponse parseListResponse(String responseBody) throws JsonProcessingException {
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
    private AirtableRecord parseRecordResponse(String responseBody) throws JsonProcessingException {
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