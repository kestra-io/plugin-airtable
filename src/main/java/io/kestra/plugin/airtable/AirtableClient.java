package io.kestra.plugin.airtable;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * HTTP client for interacting with Airtable REST API.
 * Handles authentication, request building, and response parsing.
 */
public class AirtableClient {

    private static final Logger logger = LoggerFactory.getLogger(AirtableClient.class);
    private static final String BASE_URL = "https://api.airtable.com/v0";
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;

    public AirtableClient(String apiKey) {
        this.apiKey = apiKey;
        this.objectMapper = new ObjectMapper();
        this.httpClient = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build();
    }

    /**
     * List records from a table with optional filtering and pagination.
     */
    public AirtableListResponse listRecords(String baseId, String tableId, String filterByFormula,
                                          List<String> fields, Integer maxRecords, String view,
                                          String offset) throws IOException, AirtableException {
        HttpUrl.Builder urlBuilder = HttpUrl.parse(BASE_URL + "/" + baseId + "/" + tableId).newBuilder();

        if (filterByFormula != null && !filterByFormula.trim().isEmpty()) {
            urlBuilder.addQueryParameter("filterByFormula", filterByFormula);
        }
        if (fields != null && !fields.isEmpty()) {
            for (String field : fields) {
                urlBuilder.addQueryParameter("fields[]", field);
            }
        }
        if (maxRecords != null) {
            urlBuilder.addQueryParameter("maxRecords", String.valueOf(maxRecords));
        }
        if (view != null && !view.trim().isEmpty()) {
            urlBuilder.addQueryParameter("view", view);
        }
        if (offset != null && !offset.trim().isEmpty()) {
            urlBuilder.addQueryParameter("offset", offset);
        }

        Request request = new Request.Builder()
            .url(urlBuilder.build())
            .header("Authorization", "Bearer " + apiKey)
            .get()
            .build();

        logger.debug("Making GET request to: {}", request.url());

        try (Response response = httpClient.newCall(request).execute()) {
            String responseBody = response.body().string();

            if (!response.isSuccessful()) {
                throw new AirtableException("Failed to list records: " + response.code() + " - " + responseBody);
            }

            return parseListResponse(responseBody);
        }
    }

    /**
     * Get a single record by ID.
     */
    public AirtableRecord getRecord(String baseId, String tableId, String recordId, List<String> fields)
            throws IOException, AirtableException {
        HttpUrl.Builder urlBuilder = HttpUrl.parse(BASE_URL + "/" + baseId + "/" + tableId + "/" + recordId).newBuilder();

        if (fields != null && !fields.isEmpty()) {
            for (String field : fields) {
                urlBuilder.addQueryParameter("fields[]", field);
            }
        }

        Request request = new Request.Builder()
            .url(urlBuilder.build())
            .header("Authorization", "Bearer " + apiKey)
            .get()
            .build();

        logger.debug("Making GET request to: {}", request.url());

        try (Response response = httpClient.newCall(request).execute()) {
            String responseBody = response.body().string();

            if (!response.isSuccessful()) {
                throw new AirtableException("Failed to get record: " + response.code() + " - " + responseBody);
            }

            return parseRecordResponse(responseBody);
        }
    }

    /**
     * Create a new record.
     */
    public AirtableRecord createRecord(String baseId, String tableId, Map<String, Object> fields, Boolean typecast)
            throws IOException, AirtableException {
        String url = BASE_URL + "/" + baseId + "/" + tableId;

        Map<String, Object> requestBody = Map.of("fields", fields);
        if (typecast != null && typecast) {
            requestBody = Map.of("fields", fields, "typecast", true);
        }

        String jsonBody = objectMapper.writeValueAsString(requestBody);
        RequestBody body = RequestBody.create(jsonBody, JSON);

        Request request = new Request.Builder()
            .url(url)
            .header("Authorization", "Bearer " + apiKey)
            .post(body)
            .build();

        logger.debug("Making POST request to: {} with body: {}", url, jsonBody);

        try (Response response = httpClient.newCall(request).execute()) {
            String responseBody = response.body().string();

            if (!response.isSuccessful()) {
                throw new AirtableException("Failed to create record: " + response.code() + " - " + responseBody);
            }

            return parseRecordResponse(responseBody);
        }
    }

    /**
     * Create multiple records at once (max 10).
     */
    public List<AirtableRecord> createRecords(String baseId, String tableId, List<Map<String, Object>> recordsFields,
                                            Boolean typecast) throws IOException, AirtableException {
        if (recordsFields.size() > 10) {
            throw new IllegalArgumentException("Cannot create more than 10 records at once");
        }

        String url = BASE_URL + "/" + baseId + "/" + tableId;

        List<Map<String, Object>> records = new ArrayList<>();
        for (Map<String, Object> fields : recordsFields) {
            records.add(Map.of("fields", fields));
        }

        Map<String, Object> requestBody = Map.of("records", records);
        if (typecast != null && typecast) {
            requestBody = Map.of("records", records, "typecast", true);
        }

        String jsonBody = objectMapper.writeValueAsString(requestBody);
        RequestBody body = RequestBody.create(jsonBody, JSON);

        Request request = new Request.Builder()
            .url(url)
            .header("Authorization", "Bearer " + apiKey)
            .post(body)
            .build();

        logger.debug("Making POST request to: {} with body: {}", url, jsonBody);

        try (Response response = httpClient.newCall(request).execute()) {
            String responseBody = response.body().string();

            if (!response.isSuccessful()) {
                throw new AirtableException("Failed to create records: " + response.code() + " - " + responseBody);
            }

            return parseListResponse(responseBody).getRecords();
        }
    }

    /**
     * Update a record.
     */
    public AirtableRecord updateRecord(String baseId, String tableId, String recordId,
                                     Map<String, Object> fields, Boolean typecast)
            throws IOException, AirtableException {
        String url = BASE_URL + "/" + baseId + "/" + tableId + "/" + recordId;

        Map<String, Object> requestBody = Map.of("fields", fields);
        if (typecast != null && typecast) {
            requestBody = Map.of("fields", fields, "typecast", true);
        }

        String jsonBody = objectMapper.writeValueAsString(requestBody);
        RequestBody body = RequestBody.create(jsonBody, JSON);

        Request request = new Request.Builder()
            .url(url)
            .header("Authorization", "Bearer " + apiKey)
            .patch(body)
            .build();

        logger.debug("Making PATCH request to: {} with body: {}", url, jsonBody);

        try (Response response = httpClient.newCall(request).execute()) {
            String responseBody = response.body().string();

            if (!response.isSuccessful()) {
                throw new AirtableException("Failed to update record: " + response.code() + " - " + responseBody);
            }

            return parseRecordResponse(responseBody);
        }
    }

    /**
     * Delete a record.
     */
    public AirtableRecord deleteRecord(String baseId, String tableId, String recordId)
            throws IOException, AirtableException {
        String url = BASE_URL + "/" + baseId + "/" + tableId + "/" + recordId;

        Request request = new Request.Builder()
            .url(url)
            .header("Authorization", "Bearer " + apiKey)
            .delete()
            .build();

        logger.debug("Making DELETE request to: {}", url);

        try (Response response = httpClient.newCall(request).execute()) {
            String responseBody = response.body().string();

            if (!response.isSuccessful()) {
                throw new AirtableException("Failed to delete record: " + response.code() + " - " + responseBody);
            }

            return parseRecordResponse(responseBody);
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