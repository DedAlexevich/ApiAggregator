package ru.dedalexeich.aggregator;

import ru.dedalexeich.data.DataRecord;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public interface IDataQuery {
    List<DataRecord> read(String format, Path path, String source) throws IOException;
}
