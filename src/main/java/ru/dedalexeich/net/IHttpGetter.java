package ru.dedalexeich.net;

import java.io.IOException;
import java.net.URI;
import java.util.Map;

public interface IHttpGetter {
    String get(URI uri, Map<String, String> headers) throws IOException, InterruptedException;
}
