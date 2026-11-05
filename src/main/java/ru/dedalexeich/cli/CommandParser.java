package ru.dedalexeich.cli;

import ru.dedalexeich.storage.WriteMode;

import java.nio.file.Path;
import java.util.*;

public class CommandParser {

    public record Command(String action, List<String> sources, String format, WriteMode mode,
                          Path file, String sourceFilter, Map<String, Map<String, String>> parameters) {
    }

    public Command parse(String[] args) {
        if (args.length == 0) {
            return new Command("help", List.of(), "json", WriteMode.OVERWRITE,
                    Path.of("data.json"), null, Map.of());
        }

        String action = args[args.length - 1];
        if (!Set.of("run", "read", "help").contains(action)) {
            throw new IllegalArgumentException("The last argument must be run, read, or help");
        }
        if (action.equals("help") && args.length != 1) {
            throw new IllegalArgumentException("The help command does not accept options");
        }

        Map<String, String> options = new HashMap<>();
        Map<String, Map<String, String>> parameters = new HashMap<>();
        Set<String> allowed = action.equals("run")
                ? Set.of("-sources", "-format", "-mode", "-file")
                : Set.of("-format", "-file", "-source");

        for (int i = 0; i < args.length - 1; i++) {
            String name = args[i];
            if (name.equals("-config") && action.equals("run")) {
                int start = i + 1;
                while (i + 1 < args.length - 1 && !args[i + 1].startsWith("-")) {
                    addParameter(parameters, args[++i]);
                }
                if (i + 1 == start) {
                    throw new IllegalArgumentException("Expected source.parameter=value after -config");
                }
                continue;
            }
            if (!allowed.contains(name)) {
                throw new IllegalArgumentException("Unknown command-line option");
            }
            if (i + 1 >= args.length - 1 || args[i + 1].startsWith("-")) {
                throw new IllegalArgumentException("Missing value for " + name);
            }
            String value = args[++i];
            if (value.isBlank() || options.putIfAbsent(name, value) != null) {
                throw new IllegalArgumentException("Empty or repeated option: " + name);
            }
        }

        List<String> sources = action.equals("run") ? parseSources(options.get("-sources")) : List.of();
        parameters.keySet().retainAll(sources);
        String format = options.getOrDefault("-format", "json").toLowerCase();
        if (!Set.of("json", "csv").contains(format)) {
            throw new IllegalArgumentException("Format must be json or csv");
        }
        WriteMode mode = switch (options.getOrDefault("-mode", "overwrite").toLowerCase()) {
            case "overwrite", "create" -> WriteMode.OVERWRITE;
            case "append" -> WriteMode.APPEND;
            default -> throw new IllegalArgumentException("Mode must be overwrite or append");
        };

        Map<String, Map<String, String>> copied = new HashMap<>();
        parameters.forEach((name, values) -> copied.put(name, Map.copyOf(values)));
        return new Command(action, sources, format, mode,
                Path.of(options.getOrDefault("-file", "data." + format)),
                options.get("-source"), Map.copyOf(copied));
    }

    private List<String> parseSources(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing required option: -sources");
        }
        List<String> sources = Arrays.stream(value.split(",", -1)).map(String::trim).toList();
        if (sources.stream().anyMatch(String::isBlank)) {
            throw new IllegalArgumentException("Empty source name");
        }
        if (sources.stream().distinct().count() != sources.size()) {
            throw new IllegalArgumentException("The source list contains duplicates");
        }
        return sources;
    }

    private void addParameter(Map<String, Map<String, String>> parameters, String argument) {
        int equals = argument.indexOf('=');
        int dot = argument.indexOf('.');
        if (dot <= 0 || equals <= dot + 1 || equals == argument.length() - 1) {
            throw new IllegalArgumentException("Expected source.parameter=value in -config");
        }
        String source = argument.substring(0, dot);
        String name = argument.substring(dot + 1, equals);
        String value = argument.substring(equals + 1);
        if (value.isBlank()) {
            throw new IllegalArgumentException("Empty value for parameter: " + name);
        }
        Map<String, String> values = parameters.computeIfAbsent(source, key -> new HashMap<>());
        if (values.putIfAbsent(name, value) != null) {
            throw new IllegalArgumentException("Repeated parameter: " + source + "." + name);
        }
    }
}
