package org.babyfish.jimmer.serialization.kotlinx;

import org.babyfish.jimmer.ImmutableObjects;
import org.babyfish.jimmer.json.codec.JsonType;
import org.babyfish.jimmer.meta.ImmutableType;
import org.babyfish.jimmer.serialization.kotlinx.model.SerializableBook;
import org.babyfish.jimmer.serialization.kotlinx.model.dto.SerializableBookView;
import org.babyfish.jimmer.sql.cache.ValueSerializer;
import org.babyfish.jimmer.sql.model.inheritance.enumdiscriminator.ClientType;
import org.babyfish.jimmer.sql.model.inheritance.enumdiscriminator.EnumClient;
import org.babyfish.jimmer.sql.model.inheritance.enumdiscriminator.EnumOrganization;
import org.babyfish.jimmer.sql.model.inheritance.enumdiscriminator.EnumOrganizationDraft;
import org.babyfish.jimmer.sql.model.inheritance.singletable.Client;
import org.babyfish.jimmer.sql.model.inheritance.singletable.Organization;
import org.babyfish.jimmer.sql.model.inheritance.singletable.OrganizationDraft;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

public class KotlinxJsonCodecJavaTest {

    @Test
    public void javaCodeCanUseKotlinxCodecForImmutableObjects() {
        KotlinxJsonCodec codec = new KotlinxJsonCodec();
        SerializableBook book = ImmutableObjects.fromString(
                SerializableBook.class,
                "{\"id\":3,\"name\":\"Java caller\"}",
                codec
        );

        assertEquals(
                "{\"id\":3,\"name\":\"Java caller\"}",
                ImmutableObjects.toString(book, codec)
        );
    }

    @Test
    public void javaCodeCanUseKotlinxCodecForGeneratedDto() throws Exception {
        KotlinxJsonCodec codec = new KotlinxJsonCodec();
        SerializableBookView view = new SerializableBookView(4L, "Java DTO");

        String json = codec.encode(view, JsonType.of(SerializableBookView.class));
        SerializableBookView decoded = codec.decode(json, SerializableBookView.class);

        assertEquals("{\"id\":4,\"name\":\"Java DTO\"}", json);
        assertEquals(view, decoded);
    }

    @Test
    public void valueSerializerPreservesStringDiscriminator() {
        Organization organization = OrganizationDraft.$.produce(draft -> draft
                .setId(1L)
                .setName("Acme")
                .setTaxCode("ACME-001")
        );
        ValueSerializer<Client> serializer = new ValueSerializer<>(
                ImmutableType.get(Client.class),
                new KotlinxJsonCodec()
        );

        Client decoded = serializer.deserialize(serializer.serialize(organization));

        Organization decodedOrganization = assertInstanceOf(Organization.class, decoded);
        assertEquals("ORG", decodedOrganization.type());
        assertEquals("ACME-001", decodedOrganization.taxCode());
    }

    @Test
    public void valueSerializerPreservesEnumDiscriminator() {
        EnumOrganization organization = EnumOrganizationDraft.$.produce(draft -> draft
                .setId(2L)
                .setName("Enum Acme")
        );
        ValueSerializer<EnumClient> serializer = new ValueSerializer<>(
                ImmutableType.get(EnumClient.class),
                new KotlinxJsonCodec()
        );

        EnumClient decoded = serializer.deserialize(serializer.serialize(organization));

        assertInstanceOf(EnumOrganization.class, decoded);
        assertEquals(ClientType.ORG, decoded.type());
        assertEquals("Enum Acme", decoded.name());
    }
}
