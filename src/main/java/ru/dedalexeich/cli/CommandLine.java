package ru.dedalexeich.cli;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.dedalexeich.aggregator.Aggregator;
import ru.dedalexeich.aggregator.FetchResult;
import ru.dedalexeich.data.DataItem;
import ru.dedalexeich.data.DataRecord;

import java.io.IOException;
import java.io.PrintStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CommandLine {

    private static final Logger log = LoggerFactory.getLogger(CommandLine.class);

    private final Aggregator aggregator;
    private final CommandParser parser;
    private final PrintStream out;

    public CommandLine(Aggregator aggregator, CommandParser parser, PrintStream out) {
        this.aggregator = aggregator;
        this.parser = parser;
        this.out = out;
    }

    public int run(String[] args) {
        try {
            CommandParser.Command command = parser.parse(args);
            logLaunchOptions(command);
            return switch (command.action()) {
                case "run" -> fetch(command);
                case "read" -> {
                    printRecords(aggregator.read(command.format(), command.file(), command.sourceFilter()));
                    yield 0;
                }
                case "help" -> {
                    printHelp();
                    yield 0;
                }
                default -> throw new IllegalArgumentException("Unknown command");
            };
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            out.println("Execution interrupted");
            log.warn("command.interrupted");
            return 130;
        } catch (IOException | RuntimeException e) {
            out.println("Error: " + e.getMessage());
            log.error("command.failed reason={}", e.getMessage());
            return 1;
        }
    }

    private void logLaunchOptions(CommandParser.Command command) {

        Map<String, Map<String, String>> overrides = new HashMap<>(command.parameters());

        log.info("command.options action={} sources={} format={} mode={} file={} sourceFilter={} overrides={}",
                command.action(), command.sources().toString(), command.format(), command.mode(),
                command.file().toString(), command.sourceFilter(), overrides);
    }

    private int fetch(CommandParser.Command command) throws IOException, InterruptedException {
        command.parameters().forEach(aggregator::configure);
        FetchResult result = aggregator.fetch(command.sources(), command.format(), command.file(), command.mode());

        if (result.savedRecords() == 0) {
            out.println("No data saved: all selected sources failed.");
        } else {
            out.printf("Saved %d result(s) to %s%n", result.savedRecords(), command.file());
        }
        if (!result.failedSources().isEmpty()) {
            out.println("Failed sources: " + String.join(", ", result.failedSources()));
            return 2;
        }
        return 0;
    }

    private void printRecords(List<DataRecord> records) {
        if (records.isEmpty()) {
            out.println("No records found.");
        }
        for (DataRecord record : records) {
            out.printf("Source: %s%nFetched at: %s%nParameters: %s%n",
                    record.source(), record.fetchedAt(), record.parameters());
            for (DataItem item : record.items()) {
                out.println("  " + item.id());
                item.attributes().forEach((key, value) -> out.printf("    %s: %s%n", key, value));
            }
            out.println();
        }
    }

    private void printHelp() {
        out.println("""
                -sources meteo,auto,exchange [-format json|csv] [-mode overwrite|append]
                    [-file path] [-config source.parameter=value ...] run
                [-format json|csv] [-file path] [-source "stored source name"] read
                help

                Stored source names: "Open-Meteo marine", "auto", "Exchange rates".
                """);
    }
}
