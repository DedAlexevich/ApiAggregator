package ru.dedalexeich.net;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class ApiHttpClient {

    private static final Logger log = LoggerFactory.getLogger(ApiHttpClient.class);

    private final HttpClient client;

    public ApiHttpClient(HttpClient client) {
        this.client = client;
    }

    public String get(URI uri, Map<String, String> headers) throws IOException, InterruptedException {
        String endpoint = uri.getHost() + uri.getRawPath();
        long started = System.nanoTime();
        HttpResponse<String> response;

        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofSeconds(20))
                    .GET();
            headers.forEach(builder::header);
            response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException e) {
            log.warn("http.interrupted method=GET endpoint={} durationMs={}", endpoint, elapsedMs(started));
            throw e;
        } catch (IOException | RuntimeException e) {
            String type = e.getClass().getSimpleName();
            log.warn("http.failed method=GET endpoint={} errorType={} durationMs={}", endpoint, type, elapsedMs(started));
            throw new IOException("HTTP GET failed for " + endpoint + " (" + type + ")", e);
        }

        int status = response.statusCode();
        if (status < 200 || status >= 300) {
            log.warn("http.failed method=GET endpoint={} status={} durationMs={}", endpoint, status, elapsedMs(started));
            throw new IOException("HTTP GET returned status " + status + " for " + endpoint);
        }

        log.info("http.succeeded method=GET endpoint={} status={} durationMs={}", endpoint, status, elapsedMs(started));
        return response.body();
    }

    private long elapsedMs(long started) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
    }
}
