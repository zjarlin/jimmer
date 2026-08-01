package org.babyfish.jimmer.serialization.kotlinx

import kotlinx.serialization.SerialName
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.babyfish.jimmer.ImmutableObjects
import org.babyfish.jimmer.json.codec.JsonCodec
import org.babyfish.jimmer.json.codec.JsonCodecDispatcher
import org.babyfish.jimmer.json.codec.JsonCodecOptions
import org.babyfish.jimmer.json.codec.JsonCodecProvider
import org.babyfish.jimmer.json.codec.JsonType
import org.babyfish.jimmer.json.codec.Node
import org.babyfish.jimmer.kt.new
import org.babyfish.jimmer.serialization.kotlinx.model.SerializableBook
import org.babyfish.jimmer.serialization.kotlinx.model.by
import org.babyfish.jimmer.serialization.kotlinx.model.dto.SerializableBookView
import org.babyfish.jimmer.sql.JSqlClient
import org.babyfish.jimmer.sql.Serialized
import org.babyfish.jimmer.sql.runtime.JSqlClientImplementor
import org.babyfish.jimmer.sql.runtime.ScalarProvider
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.ServiceLoader
import java.util.UUID
import kotlin.test.assertFailsWith

class KotlinxJsonCodecTest {

    private val codec = KotlinxJsonCodec()

    @Test
    fun `reader and typed writer support serializable class`() {
        val payload = Payload(
            id = 1,
            name = "jimmer",
            tags = listOf("orm", "json")
        )

        val json = codec.encode(payload, JsonType.of(Payload::class.java))

        assertEquals("""{"id":1,"name":"jimmer","tags":["orm","json"]}""", json)
        assertEquals(payload, codec.decode(json, Payload::class.java))
    }

    @Test
    fun `untyped writer supports serializable class`() {
        val payload = Payload(
            id = 3,
            name = "cache",
            tags = listOf("serialized")
        )

        val json = codec.encode(payload)

        assertEquals("""{"id":3,"name":"cache","tags":["serialized"]}""", json)
        assertEquals(payload, codec.decode(json, Payload::class.java))
    }

    @Test
    fun `generic readers support list and map`() {
        val payloads: List<Payload> = codec.decode(
            """[{"id":1,"name":"a","tags":[]},{"id":2,"name":"b","tags":["x"]}]""",
            JsonType.listOf(Payload::class.java)
        )
        val map: Map<String, Payload> = codec.decode(
            """{"first":{"id":1,"name":"a","tags":[]}}""",
            JsonType.mapOf(String::class.java, Payload::class.java)
        )

        assertEquals(listOf("a", "b"), payloads.map { it.name })
        assertEquals(Payload(1, "a", emptyList()), map["first"])
    }

    @Test
    fun `tree reader exposes scalar casts and fields`() {
        val id = UUID.fromString("00000000-0000-0000-0000-000000000123")
        val node = codec.decode("""{"id":"$id","count":3,"enabled":true}""", Node::class.java)
        val fields = node.fieldsIterator().asSequence().map { it.key }.toList()

        assertEquals(id, node["id"].castTo(UUID::class.java))
        assertEquals(3, node["count"].castTo(Int::class.java))
        assertEquals(true, node["enabled"].castTo(Boolean::class.java))
        assertEquals(listOf("id", "count", "enabled"), fields)
    }

    @Test
    fun `options apply pretty printing and property naming`() {
        val payload = Payload(5, "options", emptyList())
        val options = JsonCodecOptions.newBuilder()
            .prettyPrint(true)
            .propertyNaming(JsonCodecOptions.PropertyNaming.UPPER_CAMEL_CASE)
            .build()

        val json = codec.encode(payload, JsonType.of(Payload::class.java), options)
        val decoded: Payload = codec.decode(json, JsonType.of(Payload::class.java), options)

        assertTrue(json.contains("\n"))
        assertTrue(json.contains("\"Id\""))
        assertEquals(payload, decoded)
    }

    @Test
    fun `service loader dynamically routes kotlinx jackson3 and jackson2`() {
        val providers = ServiceLoader.load(JsonCodecProvider::class.java)
            .associateBy { it.javaClass.name }
        val kotlinxProvider = KotlinxJsonCodecProvider()
        val defaultCodec = JsonCodec.defaultCodec()
        val jacksonPayload = JacksonOnlyPayload("jackson", 3)

        assertEquals(
            400,
            providers["org.babyfish.jimmer.serialization.kotlinx.KotlinxJsonCodecProvider"]?.priority()
        )
        assertEquals(
            300,
            providers["org.babyfish.jimmer.json.jackson.v3.JsonCodecProviderV3"]?.priority()
        )
        assertEquals(
            200,
            providers["org.babyfish.jimmer.json.jackson.v2.JsonCodecProviderV2"]?.priority()
        )
        assertTrue(defaultCodec is JsonCodecDispatcher)
        assertFalse(kotlinxProvider.supportsEncode(jacksonPayload, JsonType.any()))
        assertFalse(kotlinxProvider.supportsDecode(JsonType.of(JacksonOnlyPayload::class.java)))

        val json = defaultCodec.encode(jacksonPayload)

        assertEquals("""{"firstName":"jackson","count":3}""", json)
        assertEquals(jacksonPayload, defaultCodec.decode(json, JacksonOnlyPayload::class.java))
    }

    @Test
    fun `default codec preserves sealed class discriminator with all providers present`() {
        val codec = JsonCodec.defaultCodec()
        val event: DomainEvent = DomainEvent.Created("event-1", "jimmer")
        val type = JsonType.of(DomainEvent::class.java)

        val typedJson = codec.encode(event, type)
        val inferredJson = codec.encode(event)

        assertEquals("""{"type":"created","id":"event-1","source":"jimmer"}""", typedJson)
        assertEquals(typedJson, inferredJson)
        assertEquals(event, codec.decode(typedJson, DomainEvent::class.java))
    }

    @Test
    fun `default codec round trips sealed class collection`() {
        val codec = JsonCodec.defaultCodec()
        val events: List<DomainEvent> = listOf(
            DomainEvent.Created("event-1", "jimmer"),
            DomainEvent.Deleted("event-2", "expired")
        )

        val json = codec.encode(events)
        val decoded: List<DomainEvent> = codec.decode(
            json,
            JsonType.listOf(DomainEvent::class.java)
        )

        assertEquals(
            """[{"type":"created","id":"event-1","source":"jimmer"},{"type":"deleted","id":"event-2","reason":"expired"}]""",
            json
        )
        assertEquals(events, decoded)
    }

    @Test
    fun `default codec preserves sealed discriminator in serializable owner`() {
        val codec = JsonCodec.defaultCodec()
        val envelope = EventEnvelope(DomainEvent.Deleted("event-3", "archived"))

        val json = codec.encode(envelope)

        assertEquals(
            """{"event":{"type":"deleted","id":"event-3","reason":"archived"}}""",
            json
        )
        assertEquals(envelope, codec.decode(json, EventEnvelope::class.java))
    }

    @Test
    fun `selected kotlinx codec error is not hidden by jackson fallback`() {
        assertFailsWith<SerializationException> {
            JsonCodec.defaultCodec().decode(
                """{"type":"unknown","id":"event-1"}""",
                DomainEvent::class.java
            )
        }
    }

    @Test
    fun `sql serialized type scalar provider uses kotlinx codec`() {
        val sqlClient = JSqlClient
            .newBuilder()
            .setDefaultSerializedTypeJsonCodec(codec)
            .build() as JSqlClientImplementor
        val provider: ScalarProvider<SerializedPayload, String> =
            sqlClient.getScalarProvider(SerializedPayload::class.java)
        val payload = SerializedPayload("sql", listOf(1, 2, 3))

        val sqlValue = provider.toSql(payload)

        assertEquals("""{"name":"sql","scores":[1,2,3]}""", sqlValue)
        assertEquals(payload, provider.toScalar(sqlValue))
    }

    @Test
    fun `generated kotlin dto is serializable when kotlinx dto generation is enabled`() {
        val view = SerializableBookView(id = 1L, name = "GraphQL in Action")

        val json = Json.encodeToString(view)

        assertEquals("""{"id":1,"name":"GraphQL in Action"}""", json)
        assertEquals(view, Json.decodeFromString<SerializableBookView>(json))
    }

    @Test
    fun `immutable objects use the service loaded kotlinx codec by default`() {
        val book = new(SerializableBook::class).by {
            id = 2L
            name = "Kotlinx in Action"
        }

        val json = ImmutableObjects.toString(book)
        val decoded = ImmutableObjects.fromString(SerializableBook::class.java, json)

        assertEquals("""{"id":2,"name":"Kotlinx in Action"}""", json)
        assertEquals(json, ImmutableObjects.toString(decoded))
    }

    @Serializable
    data class Payload(
        val id: Int,
        val name: String,
        val tags: List<String>
    )

    @Serialized
    @Serializable
    data class SerializedPayload(
        val name: String,
        val scores: List<Int>
    )

    data class JacksonOnlyPayload(
        val firstName: String,
        val count: Int
    )

    @Serializable
    data class EventEnvelope(
        val event: DomainEvent
    )

    @Serializable
    sealed class DomainEvent {

        abstract val id: String

        @Serializable
        @SerialName("created")
        data class Created(
            override val id: String,
            val source: String
        ) : DomainEvent()

        @Serializable
        @SerialName("deleted")
        data class Deleted(
            override val id: String,
            val reason: String
        ) : DomainEvent()
    }
}
