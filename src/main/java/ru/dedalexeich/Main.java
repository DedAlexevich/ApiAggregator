package ru.dedalexeich;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.dedalexeich.aggregator.Aggregator;
import ru.dedalexeich.cli.CommandLine;
import ru.dedalexeich.cli.CommandParser;
import ru.dedalexeich.net.ApiHttpClient;
import ru.dedalexeich.source.AutoDevSource;
import ru.dedalexeich.source.ExchangeRatesSource;
import ru.dedalexeich.source.IDataSource;
import ru.dedalexeich.source.OpenMeteoMarineSource;
import ru.dedalexeich.storage.CsvDataStorage;
import ru.dedalexeich.storage.IDataStorage;
import ru.dedalexeich.storage.JsonDataStorage;
import ru.dedalexeich.utils.ApiKeys;

import java.io.IOException;
import java.net.http.HttpClient;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.function.Supplier;

public class Main {

    private static final Logger log = LoggerFactory.getLogger(Main.class);

    static void main(String[] args) {
        int exitCode;
        log.info("application.started argumentCount={}", args.length);
        try {
            exitCode = createCommandLine().run(args);
        } catch (RuntimeException e) {
            log.error("application.failed reason={}", e.getMessage());
            exitCode = 1;
        }

        System.exit(exitCode);
    }

    private static CommandLine createCommandLine() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        Aggregator aggregator = getAggregator(client, mapper);
        aggregator.configure("meteo", Map.of(
                "latitude", "43.3",
                "longitude", "5.4",
                "hourly", "wave_height,wave_period,wave_direction,sea_surface_temperature",
                "forecast_days", "1"
        ));
        aggregator.configure("auto", Map.of("vehicle.make", "Toyota", "limit", "5"));
        aggregator.configure("exchange", Map.of("symbols", "USD,GBP,JPY"));

        return new CommandLine(aggregator, new CommandParser(), System.out);
    }

    private static Aggregator getAggregator(HttpClient client, ObjectMapper mapper) {
        ApiHttpClient http = new ApiHttpClient(client);

        Map<String, Supplier<IDataSource>> sources = Map.of(
                "meteo", () -> new OpenMeteoMarineSource(http, mapper),
                "auto", () -> new AutoDevSource(http, mapper, readKey("AUTO_DEV_API_KEY")),
                "exchange", () -> new ExchangeRatesSource(http, mapper, readKey("EXCHANGE_RATES_API_KEY"))
        );
        Map<String, IDataStorage> storages = Map.of(
                "json", new JsonDataStorage(mapper),
                "csv", new CsvDataStorage(mapper)
        );

        return new Aggregator(sources, storages);
    }

    private static String readKey(String name) {
        try {
            return new ApiKeys(Path.of(".env")).get(name);
        } catch (IOException e) {
            throw new IllegalStateException("Could not read .env", e);
        }
    }
}
