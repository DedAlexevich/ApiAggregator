package ru.dedalexeich.data;

import java.util.Map;

public record DataItem(String id, Map<String, String> attributes) {
    public DataItem {
        attributes = Map.copyOf(attributes);
    }
}
