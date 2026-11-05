package ru.dedalexeich.aggregator;

import java.util.List;

public record FetchResult(int savedRecords, List<String> failedSources) {
    public FetchResult {
        failedSources = List.copyOf(failedSources);
    }
}
