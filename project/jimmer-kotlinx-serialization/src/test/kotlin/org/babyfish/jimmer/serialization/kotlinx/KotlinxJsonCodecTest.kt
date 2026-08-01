package org.babyfish.jimmer.serialization.kotlinx

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.babyfish.jimmer.ImmutableObjects
import org.babyfish.jimmer.json.codec.JsonCodec
import org.babyfish.jimmer.json.codec.JsonCodecOptions
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
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

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
    fun `service loader can make kotlinx codec the default json codec`() {
        assertTrue(JsonCodec.defaultCodec() is KotlinxJsonCodec)
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
}
