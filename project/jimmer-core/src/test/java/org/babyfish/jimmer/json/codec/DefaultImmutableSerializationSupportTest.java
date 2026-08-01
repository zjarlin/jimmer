package org.babyfish.jimmer.json.codec;

import org.babyfish.jimmer.model.BookDraft;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class DefaultImmutableSerializationSupportTest {

    @Test
    public void test() throws Exception {
        JsonCodec codec = JsonCodecDetector.loadJsonCodecProvider().codec();
        String json = codec.encode(BookDraft.$.produce(draft -> {
        }));
        assertEquals("{}", json);
    }
}
