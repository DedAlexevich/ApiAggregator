package ru.dedalexeich.source;

import ru.dedalexeich.data.DataRecord;

import java.io.IOException;
import java.util.Map;

public interface IDataSource {
    String getName();

    DataRecord fetch(Map<String, String> parameters) throws IOException, InterruptedException;
}
