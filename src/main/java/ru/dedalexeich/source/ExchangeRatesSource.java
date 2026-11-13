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
import java.util.*;

public class ExchangeRatesSource implements IDataSource{

    private static final String URL = "https://api.exchangeratesapi.io/v1/latest";

    private final ApiHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;

    public ExchangeRatesSource(ApiHttpClient httpClient, ObjectMapper objectMapper, String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("ExchangeRates API key is missing");
        }
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.apiKey = apiKey;
    }

    @Override
    public String getName() {
        return "exchange";
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
        if (parameters.containsKey("access_key")) {
            throw new IllegalArgumentException("Pass the API key through the constructor");
        }

        Map<String, String> query = new HashMap<>(parameters);
        query.put("access_key", apiKey);

        URI uri = QueryUriBuilder.build(URL, query);
        String json = httpClient.get(uri, Map.of());

        return objectMapper.readTree(json);
    }

    private void validateResponse(JsonNode root) throws IOException {
        if (root == null || !root.isObject()) {
            throw new IOException("Expected an ExchangeRates JSON object");
        }

        if (!root.path("success").asBoolean(false)) {
            JsonNode codeNode = root.path("error").path("code");
            String code = codeNode.asText();
            throw new IOException("ExchangeRates API error, code: " + code);
        }

        if (!root.path("base").isTextual()
                || !root.path("date").isTextual()
                || !root.path("rates").isObject()) {
            throw new IOException("Invalid ExchangeRates response structure");
        }
    }

    private List<DataItem> getUsefulDataItems(JsonNode root) throws IOException {

        String base = root.path("base").asText();
        String date = root.path("date").asText();

        List<DataItem> items = new ArrayList<>();

        for (Map.Entry<String, JsonNode> field : root.path("rates").properties()) {
            items.add(toDataItem(base, field.getKey(), field.getValue(), date));
        }

        return items;
    }

    private DataItem toDataItem(String base, String quote, JsonNode rate, String date) throws IOException {

        if (!rate.isNumber()) {
            throw new IOException("ExchangeRates response contains a non-numeric rate");
        }

        Map<String, String> attributes = Map.of(
                "base", base,
                "quote", quote,
                "rate", rate.asText(),
                "rateDate", date
        );

        return new DataItem(base + "/" + quote, attributes);
    }
}
