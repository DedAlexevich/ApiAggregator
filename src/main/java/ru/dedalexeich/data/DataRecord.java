package ru.dedalexeich.data;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record DataRecord(String source, Instant fetchedAt, Map<String, String> parameters, List<DataItem> items) {
    public DataRecord {
        parameters = Map.copyOf(parameters);
        items = List.copyOf(items);
    }
}
