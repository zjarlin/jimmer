package org.babyfish.jimmer.client.meta.impl;

import org.babyfish.jimmer.client.meta.Schema;
import org.babyfish.jimmer.json.codec.JsonCodec;
import org.babyfish.jimmer.json.codec.JsonCodecOptions;

import java.io.Reader;
import java.io.Writer;
import java.util.Collection;
import java.util.Set;

import static org.babyfish.jimmer.json.codec.JsonCodec.defaultCodec;

public class Schemas {

    public static final Object IGNORE_DEFINITIONS = new Object();

    public static final Object GROUPS = new Object();

    private Schemas() {
    }

    public static void writeTo(Schema schema, Writer writer) throws Exception {
        writeTo(schema, writer, defaultCodec());
    }

    public static void writeTo(Schema schema, Writer writer, JsonCodec jsonCodec) throws Exception {
        JsonCodecOptions options = JsonCodecOptions.newBuilder()
                .prettyPrint(true)
                .build();
        writer.write(jsonCodec.encode(schema, options));
    }

    public static Schema readFrom(Reader reader) throws Exception {
        return readFrom(reader, null);
    }

    public static Schema readFrom(Reader reader, Set<String> groups) throws Exception {
        return readFrom(reader, groups, defaultCodec());
    }

    public static Schema readFrom(Reader reader, Set<String> groups, JsonCodec jsonCodec) throws Exception {
        JsonCodecOptions options = JsonCodecOptions.newBuilder()
                .attribute(GROUPS, groups)
                .build();
        return jsonCodec.decode(readAll(reader), SchemaImpl.class, options);
    }

    public static Schema readServicesFrom(Reader reader) throws Exception {
        return readServicesFrom(reader, defaultCodec());
    }

    public static Schema readServicesFrom(Reader reader, JsonCodec jsonCodec) throws Exception {
        JsonCodecOptions options = JsonCodecOptions.newBuilder()
                .attribute(IGNORE_DEFINITIONS, true)
                .build();
        return jsonCodec.decode(readAll(reader), SchemaImpl.class, options);
    }

    private static String readAll(Reader reader) throws Exception {
        char[] buffer = new char[4096];
        StringBuilder builder = new StringBuilder();
        int count;
        while ((count = reader.read(buffer)) != -1) {
            builder.append(buffer, 0, count);
        }
        return builder.toString();
    }

    public static boolean isAllowed(Collection<String> elementGroups, Set<String> allowedGroups) {
        if (elementGroups == null || elementGroups.isEmpty()) {
            return true;
        }
        if (allowedGroups == null || allowedGroups.isEmpty()) {
            return true;
        }
        for (String elementGroup : elementGroups) {
            if (allowedGroups.contains(elementGroup)) {
                return true;
            }
        }
        return false;
    }
}
