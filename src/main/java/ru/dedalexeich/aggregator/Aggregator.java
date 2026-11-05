package ru.dedalexeich.aggregator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.dedalexeich.data.DataRecord;
import ru.dedalexeich.source.IDataSource;
import ru.dedalexeich.storage.IDataStorage;
import ru.dedalexeich.storage.WriteMode;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public class Aggregator {

    private static final Logger log = LoggerFactory.getLogger(Aggregator.class);

    private final Map<String, Supplier<IDataSource>> sources;
    private final Map<String, IDataStorage> storages;
    private final Map<String, Map<String, String>> configurations;

    public Aggregator(Map<String, Supplier<IDataSource>> sources, Map<String, IDataStorage> storages) {
        this.sources = Map.copyOf(sources);
        this.storages = Map.copyOf(storages);
        this.configurations = new HashMap<>();
    }

    public void configure(String source, Map<String, String> parameters) {
        if (!sources.containsKey(source)) {
            throw new IllegalArgumentException("Unknown source: " + source);
        }
        Map<String, String> updated = new HashMap<>(configurations.getOrDefault(source, Map.of()));
        updated.putAll(parameters);
        configurations.put(source, Map.copyOf(updated));
    }

    public FetchResult fetch(List<String> names, String format, Path path, WriteMode mode) throws IOException, InterruptedException {
        validateSources(names);
        IDataStorage storage = getStorage(format);
        List<DataRecord> records = new ArrayList<>();
        List<String> failed = new ArrayList<>();

        for (String name : names) {
            Map<String, String> parameters = configurations.get(name);
            log.info("source.started source={} parameters={}", name, parameters);
            try {
                IDataSource source = sources.get(name).get();
                DataRecord record = source.fetch(parameters);
                if (record == null || record.items() == null) {
                    throw new IllegalStateException("The source returned an invalid result");
                }
                records.add(record);
                log.info("source.succeeded source={} items={}", name, record.items().size());
            } catch (InterruptedException e) {
                log.warn("source.interrupted source={}", name);
                throw e;
            } catch (IOException | RuntimeException e) {
                failed.add(name);
                log.warn("source.failed source={} reason={}", name, e.getMessage());
            }
        }

        if (!records.isEmpty()) {
            try {
                storage.write(path, records, mode);
            } catch (IOException | RuntimeException e) {
                log.error("storage.write_failed format={} mode={} file={} reason={}",
                        format, mode, path.toString(), e.getMessage());
                throw e;
            }
            log.info("storage.written format={} mode={} file={} records={}",
                    format, mode, path.toString(), records.size());
        } else {
            log.error("fetch.no_results failedSources={}", failed);
        }
        log.info("fetch.finished savedRecords={} failedSources={}", records.size(), failed);
        return new FetchResult(records.size(), failed);
    }

    public List<DataRecord> read(String format, Path path, String source) throws IOException {
        try {
            List<DataRecord> records = getStorage(format).read(path);
            if (source != null) {
                records = records.stream().filter(record -> source.equals(record.source())).toList();
            }
            log.info("storage.read format={} file={} sourceFilter={} records={}",
                    format, path.toString(), source, records.size());
            return records;
        } catch (IOException | RuntimeException e) {
            log.error("storage.read_failed format={} file={} reason={}",
                    format, path.toString(), e.getMessage());
            throw e;
        }
    }

    private void validateSources(List<String> names) {
        if (names.isEmpty()) {
            throw new IllegalArgumentException("No sources selected");
        }
        if (names.stream().distinct().count() != names.size()) {
            throw new IllegalArgumentException("The source list contains duplicates");
        }
        for (String name : names) {
            if (!sources.containsKey(name)) {
                throw new IllegalArgumentException("Unknown source: " + name);
            }
            if (!configurations.containsKey(name)) {
                throw new IllegalStateException("No configuration for source: " + name);
            }
        }
    }

    private IDataStorage getStorage(String format) {
        IDataStorage storage = storages.get(format);
        if (storage == null) {
            throw new IllegalArgumentException("Unknown output format: " + format);
        }
        return storage;
    }
}
