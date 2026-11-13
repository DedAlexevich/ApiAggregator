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

public class OpenMeteoMarineSource implements IDataSource {

    private static final String URL = "https://marine-api.open-meteo.com/v1/marine";

    private final ApiHttpClient httpClient;
    private final ObjectMapper objectMapper;

    public OpenMeteoMarineSource(ApiHttpClient httpClient, ObjectMapper objectMapper) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public String getName() {
        return "meteo";
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
        String json = httpClient.get(uri, Map.of());

        return objectMapper.readTree(json);
    }

    private void validateResponse(JsonNode root) throws IOException {
        if (root == null || !root.isObject()) {
            throw new IOException("Expected an Open-Meteo Marine JSON object");
        }

        if (!root.path("latitude").isNumber() || !root.path("longitude").isNumber()) {
            throw new IOException("Open-Meteo Marine response is missing valid coordinates");
        }

        JsonNode hourly = root.path("hourly");
        if (!hourly.isObject()) {
            throw new IOException("Open-Meteo Marine response is missing the hourly object");
        }

        JsonNode times = hourly.path("time");
        if (!times.isArray()) {
            throw new IOException("Open-Meteo Marine response is missing the hourly.time array");
        }
        boolean any = false;
        for (String name : List.of("wave_height", "wave_period", "wave_direction", "sea_surface_temperature")) {
            JsonNode values = hourly.path(name);
            if (!values.isMissingNode() && (!values.isArray() || values.size() != times.size())) {
                throw new IOException("Hourly array does not match hourly.time: " + name);
            }
            any = any || !values.isMissingNode();
        }
        if (!any) {
            throw new IOException("Response contains no requested hourly values");
        }
    }

    private List<DataItem> getUsefulDataItems(JsonNode root) throws IOException {
        List<DataItem> items = new ArrayList<>();
        items.add(toCoordinatesItem(root));

        JsonNode hourly = root.path("hourly");
        JsonNode times = hourly.path("time");
        for (int i = 0; i < times.size(); i++) {
            items.add(toHourlyItem(hourly, times.get(i), i));
        }

        return items;
    }

    private DataItem toCoordinatesItem(JsonNode root) {
        Map<String, String> attributes = new HashMap<>();

        putValue(attributes, "latitude", root.path("latitude"));
        putValue(attributes, "longitude", root.path("longitude"));

        return new DataItem("coordinates", attributes);
    }

    private DataItem toHourlyItem(JsonNode hourly, JsonNode time, int index) throws IOException {
        if (!time.isTextual() || time.asText().isBlank()) {
            throw new IOException("Hourly forecast contains an invalid time");
        }

        Map<String, String> attributes = new HashMap<>();

        putValue(attributes, "forecastTime", time);
        putValue(attributes, "waveHeight", hourly.path("wave_height").path(index));
        putValue(attributes, "wavePeriod", hourly.path("wave_period").path(index));
        putValue(attributes, "waveDirection", hourly.path("wave_direction").path(index));
        putValue(attributes, "seaSurfaceTemperature", hourly.path("sea_surface_temperature").path(index));

        return new DataItem(time.asText(), attributes);
    }

    private void putValue(Map<String, String> attributes, String name, JsonNode value) {
        if (value.isTextual() || value.isNumber() || value.isBoolean()) {
            attributes.put(name, value.asText());
        }
    }
}