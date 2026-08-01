package org.babyfish.jimmer.json.codec;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class JsonCodecDispatcherTest {

    @Test
    public void testRouteEachOperationByCapabilityAndPriority() throws Exception {
        JsonCodecProvider fallback = new TestProvider(100, false, "fallback", new AtomicInteger());
        JsonCodecProvider preferred = new TestProvider(200, true, "preferred", new AtomicInteger());
        JsonCodec codec = JsonCodecDispatcher.of(fallback, preferred);

        assertEquals("preferred:encode", codec.encode("value"));
        assertEquals("fallback:encode", codec.encode(1));
        assertEquals("preferred:decode", codec.decode("{}", String.class));
        assertEquals("fallback:decode", codec.decode("{}", Integer.class));
    }

    @Test
    public void testDoNotRetryAfterSelectedCodecFails() {
        AtomicInteger fallbackCalls = new AtomicInteger();
        JsonCodecProvider fallback = new TestProvider(100, false, "fallback", fallbackCalls);
        JsonCodecProvider failing = new TestProvider(200, true, "failing", new AtomicInteger()) {
            @Override
            public JsonCodec codec() {
                return new TestCodec("failing", new AtomicInteger()) {
                    @Override
                    public String encode(Object value, JsonType type, JsonCodecOptions options) {
                        throw new IllegalStateException("selected codec failed");
                    }
                };
            }
        };
        JsonCodec codec = JsonCodecDispatcher.of(fallback, failing);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> codec.encode("value")
        );

        assertEquals("selected codec failed", exception.getMessage());
        assertEquals(0, fallbackCalls.get());
    }

    @Test
    public void testProviderCapabilitiesRemainOptional() {
        JsonCodecProvider provider = new JsonCodecProvider() {
            @Override
            public int priority() {
                return 1;
            }

            @Override
            public JsonCodec codec() {
                return new TestCodec("legacy", new AtomicInteger());
            }
        };

        assertTrue(provider.supportsEncode(new Object(), JsonType.any()));
        assertTrue(provider.supportsDecode(JsonType.of(Object.class)));
    }

    private static class TestProvider implements JsonCodecProvider {

        private final int priority;

        private final boolean supportsString;

        private final String name;

        private final AtomicInteger calls;

        private TestProvider(int priority, boolean supportsString, String name, AtomicInteger calls) {
            this.priority = priority;
            this.supportsString = supportsString;
            this.name = name;
            this.calls = calls;
        }

        @Override
        public int priority() {
            return priority;
        }

        @Override
        public boolean supportsEncode(Object value, JsonType type) {
            return supportsString == (value instanceof String);
        }

        @Override
        public boolean supportsDecode(JsonType type) {
            return supportsString == (type.getType() == String.class);
        }

        @Override
        public JsonCodec codec() {
            return new TestCodec(name, calls);
        }
    }

    private static class TestCodec implements JsonCodec {

        private final String name;

        private final AtomicInteger calls;

        private TestCodec(String name, AtomicInteger calls) {
            this.name = name;
            this.calls = calls;
        }

        @Override
        public String encode(Object value, JsonType type, JsonCodecOptions options) {
            calls.incrementAndGet();
            return name + ":encode";
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T decode(String json, JsonType type, JsonCodecOptions options) {
            calls.incrementAndGet();
            return (T) (name + ":decode");
        }
    }
}
