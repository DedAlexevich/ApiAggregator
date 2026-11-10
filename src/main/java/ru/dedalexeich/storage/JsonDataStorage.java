package ru.dedalexeich.storage;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import ru.dedalexeich.data.DataRecord;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class JsonDataStorage implements IDataStorage {
    private static final TypeReference<List<DataRecord>> RECORDS_TYPE = new TypeReference<>() {};

    private final ObjectMapper objectMapper;

    public JsonDataStorage(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void write(Path path, List<DataRecord> records, WriteMode mode) throws IOException {
        List<DataRecord> result = new ArrayList<>();

        if (mode == WriteMode.APPEND && Files.exists(path) && Files.size(path) > 0) {
            result.addAll(read(path));
        }

        result.addAll(records);

        String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(result);
        Files.writeString(path, json);
    }

    @Override
    public List<DataRecord> read(Path path) throws IOException {
        String json = Files.readString(path, StandardCharsets.UTF_8);

        List<DataRecord> records = objectMapper.readValue(json, RECORDS_TYPE);

        if (records == null) {
            throw new IOException("Expected a JSON array of records");
        }

        return records;
    }
}
