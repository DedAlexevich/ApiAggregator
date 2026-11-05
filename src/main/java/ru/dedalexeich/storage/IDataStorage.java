package ru.dedalexeich.storage;

import ru.dedalexeich.data.DataRecord;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public interface IDataStorage {

    void write(Path path, List<DataRecord> records, WriteMode mode) throws IOException;

    List<DataRecord> read(Path path) throws IOException;

}
