package org.babyfish.jimmer.impl.util;

import org.babyfish.jimmer.json.codec.JsonType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class JsonCodecProviderUtilTest {

    @Test
    public void testNestedTypeAndValueDetection() {
        assertTrue(JsonCodecProviderUtil.containsType(
                JsonType.listOf(String.class).getType(),
                "java.lang."
        ));
        assertTrue(JsonCodecProviderUtil.containsValue(
                Collections.singletonList("value"),
                "java.lang."
        ));
    }

    @Test
    public void testCyclicContainer() {
        List<Object> values = new ArrayList<>();
        values.add(values);

        assertFalse(JsonCodecProviderUtil.containsValue(values, "missing.package."));
    }
}
