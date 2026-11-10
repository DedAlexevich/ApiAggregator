package ru.dedalexeich.storage;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import ru.dedalexeich.data.DataItem;
import ru.dedalexeich.data.DataRecord;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class CsvDataStorage implements IDataStorage {

    private static final List<String> HEADERS = List.of("source", "fetchedAt", "parameters", "items");
    private static final CSVFormat READ_FORMAT = CSVFormat.RFC4180.builder().setHeader().setSkipHeaderRecord(true).get();
    private final ObjectMapper objectMapper;

    public CsvDataStorage(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void write(Path path, List<DataRecord> records, WriteMode mode) throws IOException {
        boolean append = mode == WriteMode.APPEND && Files.exists(path) && Files.size(path) > 0;
        if (append) {
            try (Reader reader = Files.newBufferedReader(path); CSVParser parser = READ_FORMAT.parse(reader)) {
                validateHeaders(parser);
            }
        }

        CSVFormat format = CSVFormat.RFC4180.builder().setHeader(HEADERS.toArray(String[]::new)).setSkipHeaderRecord(append).get();
        StringBuffer buffer = new StringBuffer();
        try (CSVPrinter printer = new CSVPrinter(buffer, format)) {
            for (DataRecord record : records) {
                printer.printRecord(record.source(), record.fetchedAt().toString(),
                        objectMapper.writeValueAsString(record.parameters()),
                        objectMapper.writeValueAsString(record.items()));
            }
        }
        StringBuilder sb = new StringBuilder(buffer);
        if (append && sb.isEmpty()) {
            return;
        }
        if (append && needsNewLine(path)) {
            sb.insert(0, "\r\n");
        }
        Path parent = path.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        if (append) {
            Files.writeString(path, sb, StandardOpenOption.WRITE, StandardOpenOption.APPEND);
        } else {
            Files.writeString(path, sb);
        }
    }

    private boolean needsNewLine(Path path) throws IOException {
        try (RandomAccessFile file = new RandomAccessFile(path.toFile(), "r")) {
            file.seek(file.length() - 1);
            int last = file.read();
            return last != '\n' && last != '\r';
        }
    }

    @Override
    public List<DataRecord> read(Path path) throws IOException {
        List<DataRecord> records = new ArrayList<>();
        try (Reader reader = Files.newBufferedReader(path); CSVParser parser = READ_FORMAT.parse(reader)) {
            validateHeaders(parser);
            for (CSVRecord row : parser.getRecords()) {
                if (!row.isConsistent()) {
                    throw new IOException("Incorrect CSV");
                }
                Map<String, String> parameters = objectMapper.readValue(row.get("parameters"),
                        new TypeReference<Map<String, String>>() {});
                List<DataItem> items = objectMapper.readValue(row.get("items"),
                        new TypeReference<List<DataItem>>() {});
                if (parameters == null || items == null) {
                    throw new IOException("CSV contains null parameters or items");
                }
                records.add(new DataRecord(row.get("source"), Instant.parse(row.get("fetchedAt")), parameters, items));
            }
        } catch (DateTimeParseException e) {
            throw new IOException("Incorrect fetchedAt in CSV", e);
        }
        return records;
    }

    private void validateHeaders(CSVParser parser) throws IOException {
        if (!parser.getHeaderNames().equals(HEADERS)) {
            throw new IOException("Incorrect CSV headers");
        }
    }
}
