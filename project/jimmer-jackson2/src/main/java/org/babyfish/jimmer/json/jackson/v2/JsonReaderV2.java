package org.babyfish.jimmer.json.jackson.v2;

import com.fasterxml.jackson.databind.ObjectReader;

import java.io.InputStream;
import java.io.Reader;

public class JsonReaderV2<T> {
    private final ObjectReader objectReader;

    public JsonReaderV2(ObjectReader objectReader) {
        this.objectReader = objectReader;
    }

    public T read(String json) throws Exception {
        return objectReader.readValue(json);
    }

    public T read(byte[] json) throws Exception {
        return objectReader.readValue(json);
    }

    public T read(Reader reader) throws Exception {
        return objectReader.readValue(reader);
    }

    public T read(InputStream is) throws Exception {
        return objectReader.readValue(is);
    }
}
