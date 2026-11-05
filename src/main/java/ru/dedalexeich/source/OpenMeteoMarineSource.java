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
    private final ApiHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private static final String URL = "https://marine-api.open-meteo.com/v1/marine";

    public OpenMeteoMarineSource(ApiHttpClient httpClient, ObjectMapper objectMapper) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public String getName() {
        return "Open-Meteo marine";
    }

    @Override
    public DataRecord fetch(Map<String, String> parameters) throws IOException, InterruptedException {

        URI uri = QueryUriBuilder.build(URL, parameters);

        String json = httpClient.get(uri, Map.of());
        JsonNode root = objectMapper.readTree(json);
        if (root == null || !root.isObject()) {
            throw new IOException("Expected an Open-Meteo Marine JSON object");
        }
        List<DataItem> dataItems = getUsefulDataItems(root);

        return new DataRecord(
                getName(),
                Instant.now(),
                Map.copyOf(parameters),
                dataItems
        );
    }

    private List<DataItem> getUsefulDataItems(JsonNode root) throws IOException {
        List<DataItem> items = new ArrayList<>();

        JsonNode latitudeNode = root.path("latitude");
        JsonNode longitudeNode = root.path("longitude");
        if (!latitudeNode.isNumber() || !longitudeNode.isNumber()) {
            throw new IOException("Open-Meteo Marine response is missing valid coordinates");
        }
        double latitude = latitudeNode.asDouble();
        double longitude = longitudeNode.asDouble();
        Map<String, String> coords = Map.of("latitude", String.valueOf(latitude),
                "longitude", String.valueOf(longitude));

        items.add(new DataItem("coordinates", coords));
        JsonNode hourly = root.path("hourly");
        if (!hourly.isObject()) {
            throw new IOException("Open-Meteo Marine response is missing the hourly object");
        }
        JsonNode times = hourly.path("time");
        if (!times.isArray()) {
            throw new IOException("Open-Meteo Marine response is missing the hourly.time array");
        }
        JsonNode heights = hourly.path("wave_height");
        JsonNode periods = hourly.path("wave_period");
        JsonNode directions = hourly.path("wave_direction");
        JsonNode seaSurfaceTemperature = hourly.path("sea_surface_temperature");

        for (JsonNode values : List.of(heights, periods, directions, seaSurfaceTemperature)) {
            if (values.isMissingNode()) {
                continue;
            }
            if (!values.isArray() || values.size() != times.size()) {
                throw new IOException("An hourly value array does not match hourly.time");
            }
        }

        for (int i = 0; i < times.size(); i++) {
            String time = times.get(i).asText();

            Map<String, String> attributes = new HashMap<>();
            attributes.put("forecastTime", time);

            putNumber(attributes, "waveHeight", heights, i);
            putNumber(attributes, "wavePeriod", periods, i);
            putNumber(attributes, "waveDirection", directions, i);
            putNumber(attributes, "seaSurfaceTemperature", seaSurfaceTemperature, i);

            items.add(new DataItem(time, attributes));
        }

        return items;
    }

    private void putNumber(Map<String, String> attributes, String name, JsonNode values, int index) {
        JsonNode value = values.path(index);

        if (value.isNumber()) {
            attributes.put(name, value.asText());
        }
    }

}
