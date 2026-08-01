package org.babyfish.jimmer.json.codec;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class JsonCodecContractTest {

    @Test
    public void testMinimalAbstractMethods() {
        List<Method> methods = Arrays.stream(JsonCodec.class.getDeclaredMethods())
                .filter(method -> Modifier.isAbstract(method.getModifiers()))
                .collect(Collectors.toList());
        Set<String> methodNames = methods.stream()
                .map(Method::getName)
                .collect(Collectors.toSet());
        assertEquals(2, methods.size());
        assertEquals(new HashSet<>(Arrays.asList("encode", "decode")), methodNames);
    }

    @Test
    public void testOptionsAreImmutable() {
        Map<Object, Object> attributes = new LinkedHashMap<>();
        attributes.put("tenant", "a");
        JsonCodecOptions options = JsonCodecOptions.newBuilder()
                .prettyPrint(true)
                .propertyNaming(JsonCodecOptions.PropertyNaming.SNAKE_CASE)
                .attributes(attributes)
                .build();
        attributes.put("tenant", "b");

        assertTrue(options.isPrettyPrint());
        assertEquals(JsonCodecOptions.PropertyNaming.SNAKE_CASE, options.getPropertyNaming());
        assertEquals("a", options.getAttributes().get("tenant"));
        assertThrows(
                UnsupportedOperationException.class,
                () -> options.getAttributes().put("tenant", "c")
        );
    }
}
