package org.babyfish.jimmer.json.jackson.v2;

import com.fasterxml.jackson.databind.ObjectWriter;

import java.io.OutputStream;
import java.io.Writer;

public class JsonWriterV2 {
    private final ObjectWriter objectWriter;

    public JsonWriterV2(ObjectWriter objectWriter) {
        this.objectWriter = objectWriter;
    }

    public JsonWriterV2 withDefaultPrettyPrinter() {
        return new JsonWriterV2(objectWriter.withDefaultPrettyPrinter());
    }

    public String writeAsString(Object obj) throws Exception {
        return objectWriter.writeValueAsString(obj);
    }

    public byte[] writeAsBytes(Object obj) throws Exception {
        return objectWriter.writeValueAsBytes(obj);
    }

    public void write(Writer writer, Object obj) throws Exception {
        objectWriter.writeValue(writer, obj);
    }

    public void write(OutputStream os, Object obj) throws Exception {
        objectWriter.writeValue(os, obj);
    }
}
