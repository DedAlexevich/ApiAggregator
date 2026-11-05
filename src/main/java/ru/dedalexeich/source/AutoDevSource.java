package ru.dedalexeich.source;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import ru.dedalexeich.data.DataItem;
import ru.dedalexeich.data.DataRecord;
import ru.dedalexeich.net.ApiHttpClient;
import ru.dedalexeich.net.QueryUriBuilder;

import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AutoDevSource implements IDataSource {

    private static final String URL = "https://api.auto.dev/listings";

    private final ApiHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;

    public AutoDevSource(ApiHttpClient httpClient, ObjectMapper objectMapper, String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("Auto.dev API key is missing");
        }
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.apiKey = apiKey;
    }

    @Override
    public String getName() {
        return "auto";
    }

    @Override
    public DataRecord fetch(Map<String, String> parameters) throws IOException, InterruptedException {
        JsonNode root = requestData(parameters);
        validateResponse(root);
        List<DataItem> items = getUsefulDataItems(root);

        return new DataRecord(
                getName(),
                Instant.now(),
                Map.copyOf(parameters),
                items
        );
    }

    private JsonNode requestData(Map<String, String> parameters) throws IOException, InterruptedException {
        URI uri = QueryUriBuilder.build(URL, parameters);
        Map<String, String> headers = Map.of("Authorization", "Bearer " + apiKey);
        String json = httpClient.get(uri, headers);

        return objectMapper.readTree(json);
    }

    private void validateResponse(JsonNode root) throws IOException {
        if (root == null || !root.isObject()) {
            throw new IOException("Expected an Auto.dev JSON object");
        }

        if (!root.path("data").isArray()) {
            throw new IOException("Response does not contain a data array");
        }
    }

    private List<DataItem> getUsefulDataItems(JsonNode root) throws IOException {
        List<DataItem> items = new ArrayList<>();
        for (JsonNode listing : root.path("data")) {
            items.add(toDataItem(listing));
        }

        return items;
    }

    private DataItem toDataItem(JsonNode listing) throws IOException {
        JsonNode vehicle = listing.path("vehicle");

        if (!vehicle.isObject()) {
            throw new IOException("Listing does not contain a vehicle");
        }

        String vin = getVin(listing);
        Map<String, String> attributes = new HashMap<>();

        addVehicleAttributes(attributes, vehicle);
        addListingAttributes(attributes, listing.path("retailListing"));

        return new DataItem(vin, attributes);
    }

    private String getVin(JsonNode listing) throws IOException {
        JsonNode vin = listing.path("vin");

        if (!vin.isTextual() || vin.asText().isBlank()) {
            vin = listing.path("vehicle").path("vin");
        }

        if (!vin.isTextual() || vin.asText().isBlank()) {
            throw new IOException("Listing does not contain a valid VIN");
        }

        return vin.asText();
    }

    private void addVehicleAttributes(Map<String, String> attributes, JsonNode vehicle) {
        putValue(attributes, "make", vehicle.path("make"));
        putValue(attributes, "model", vehicle.path("model"));
        putValue(attributes, "year", vehicle.path("year"));
        putValue(attributes, "trim", vehicle.path("trim"));
        putValue(attributes, "fuel", vehicle.path("fuel"));
        putValue(attributes, "transmission", vehicle.path("transmission"));
    }

    private void addListingAttributes(Map<String, String> attributes, JsonNode listing) {
        putValue(attributes, "price", listing.path("price"));
        putValue(attributes, "miles", listing.path("miles"));
        putValue(attributes, "dealer", listing.path("dealer"));
        putValue(attributes, "city", listing.path("city"));
        putValue(attributes, "state", listing.path("state"));
        putValue(attributes, "listingUrl", listing.path("vdp"));
    }

    private void putValue(Map<String, String> attributes, String name, JsonNode value) {
        if (value.isTextual() || value.isNumber() || value.isBoolean()) {
            attributes.put(name, value.asText());
        }
    }
}