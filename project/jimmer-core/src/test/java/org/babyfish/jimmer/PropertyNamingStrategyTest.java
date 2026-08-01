package org.babyfish.jimmer;

import org.babyfish.jimmer.json.codec.JsonCodec;
import org.babyfish.jimmer.json.codec.JsonCodecOptions;
import org.babyfish.jimmer.model.AssociationInput;
import org.babyfish.jimmer.model.AssociationInputDraft;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.babyfish.jimmer.json.codec.JsonCodec.defaultCodec;
import static org.babyfish.jimmer.json.codec.JsonCodecOptions.PropertyNaming;

public class PropertyNamingStrategyTest {

    private static final AssociationInput INPUT = AssociationInputDraft.$.produce(input -> {
        input.setParentId(1L).setChildIds(Arrays.asList(2L, 3L));
    });

    @Test
    public void testLowerCamel() throws Exception {
        JsonCodec codec = defaultCodec();
        JsonCodecOptions options = options(PropertyNaming.LOWER_CAMEL_CASE);
        String json = codec.encode(INPUT, options);
        Assertions.assertEquals(
                "{\"parentId\":1,\"childIds\":[2,3]}",
                json
        );
        Assertions.assertEquals(
                INPUT,
                codec.decode(json, AssociationInput.class, options)
        );
    }

    @Test
    public void testUpperCamel() throws Exception {
        JsonCodec codec = defaultCodec();
        JsonCodecOptions options = options(PropertyNaming.UPPER_CAMEL_CASE);
        String json = codec.encode(INPUT, options);
        Assertions.assertEquals(
                "{\"ParentId\":1,\"ChildIds\":[2,3]}",
                json
        );
        Assertions.assertEquals(
                INPUT,
                codec.decode(json, AssociationInput.class, options)
        );
    }

    @Test
    public void testLowerCase() throws Exception {
        JsonCodec codec = defaultCodec();
        JsonCodecOptions options = options(PropertyNaming.LOWER_CASE);
        String json = codec.encode(INPUT, options);
        Assertions.assertEquals(
                "{\"parentid\":1,\"childids\":[2,3]}",
                json
        );
        Assertions.assertEquals(
                INPUT,
                codec.decode(json, AssociationInput.class, options)
        );
    }

    @Test
    public void testSnakeCase() throws Exception {
        JsonCodec codec = defaultCodec();
        JsonCodecOptions options = options(PropertyNaming.SNAKE_CASE);
        String json = codec.encode(INPUT, options);
        Assertions.assertEquals(
                "{\"parent_id\":1,\"child_ids\":[2,3]}",
                json
        );
        Assertions.assertEquals(
                INPUT,
                codec.decode(json, AssociationInput.class, options)
        );
    }

    @Test
    public void testExplicitCodecForImmutableObjectsToString() {
        JsonCodec codec = defaultCodec();
        JsonCodecOptions options = options(PropertyNaming.SNAKE_CASE);
        Assertions.assertEquals(
                "{\"parent_id\":1,\"child_ids\":[2,3]}",
                ImmutableObjects.toString(INPUT, codec, options)
        );
    }

    @Test
    public void testKebabCase() throws Exception {
        JsonCodec codec = defaultCodec();
        JsonCodecOptions options = options(PropertyNaming.KEBAB_CASE);
        String json = codec.encode(INPUT, options);
        Assertions.assertEquals(
                "{\"parent-id\":1,\"child-ids\":[2,3]}",
                json
        );
        Assertions.assertEquals(
                INPUT,
                codec.decode(json, AssociationInput.class, options)
        );
    }

    @Test
    public void testLowerDot() throws Exception {
        JsonCodec codec = defaultCodec();
        JsonCodecOptions options = options(PropertyNaming.LOWER_DOT_CASE);
        String json = codec.encode(INPUT, options);
        Assertions.assertEquals(
                "{\"parent.id\":1,\"child.ids\":[2,3]}",
                json
        );
        Assertions.assertEquals(
                INPUT,
                codec.decode(json, AssociationInput.class, options)
        );
    }

    private static JsonCodecOptions options(PropertyNaming propertyNaming) {
        return JsonCodecOptions.newBuilder()
                .propertyNaming(propertyNaming)
                .build();
    }

}
