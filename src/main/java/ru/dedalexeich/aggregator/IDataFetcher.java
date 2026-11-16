package ru.dedalexeich.aggregator;

import ru.dedalexeich.storage.WriteMode;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public interface IDataFetcher {
    FetchResult fetch(List<String> names, String format, Path path, WriteMode mode) throws IOException, InterruptedException;
}
