package ru.dedalexeich.utils;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public final class ApiKeys {

    private final Properties values = new Properties();

    public ApiKeys(Path envFile) throws IOException {
        try (Reader reader = Files.newBufferedReader(envFile)) {
            values.load(reader);
        }
    }

    public String get(String name) {
        String value = values.getProperty(name);

        if (value == null || value.isBlank()) {
            throw new IllegalStateException("API key is missing: " + name);
        }

        return value.trim();
    }
}
