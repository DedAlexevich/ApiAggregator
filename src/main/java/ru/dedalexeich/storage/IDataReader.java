package ru.dedalexeich.storage;

import ru.dedalexeich.data.DataRecord;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public interface IDataReader {
    List<DataRecord> read(Path path) throws IOException;
}
