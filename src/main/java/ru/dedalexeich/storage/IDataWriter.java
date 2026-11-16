package ru.dedalexeich.storage;

import ru.dedalexeich.data.DataRecord;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public interface IDataWriter {
    void write(Path path, List<DataRecord> records, WriteMode mode) throws IOException;
}
