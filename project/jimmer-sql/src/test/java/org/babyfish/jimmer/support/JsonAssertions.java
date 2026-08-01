package org.babyfish.jimmer.support;

import org.babyfish.jimmer.json.codec.JsonCodec;
import org.babyfish.jimmer.json.codec.Node;
import org.junit.jupiter.api.Assertions;

import static org.babyfish.jimmer.json.codec.JsonCodec.defaultCodec;

public class JsonAssertions {
    private static final JsonCodec CODEC = defaultCodec();

    public static void assertJsonEquals(String expected, String actual) {
        try {
            Assertions.assertEquals(
                    CODEC.decode(expected, Node.class),
                    CODEC.decode(actual, Node.class)
            );
        } catch (Exception e) {
            throw new IllegalArgumentException("Input strings are not json", e);
        }
    }
}
