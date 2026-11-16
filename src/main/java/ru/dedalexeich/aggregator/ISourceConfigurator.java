package ru.dedalexeich.aggregator;

import java.util.Map;

public interface ISourceConfigurator {
    void configure(String source, Map<String, String> parameters);
}
