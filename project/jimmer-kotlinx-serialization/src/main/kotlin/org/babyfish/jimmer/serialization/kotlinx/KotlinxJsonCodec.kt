package org.babyfish.jimmer.serialization.kotlinx

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonNamingStrategy
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.serializer
import kotlinx.serialization.serializerOrNull
import org.babyfish.jimmer.impl.util.JsonCodecProviderUtil
import org.babyfish.jimmer.json.codec.JsonCodec
import org.babyfish.jimmer.json.codec.JsonCodecOptions
import org.babyfish.jimmer.json.codec.JsonType
import org.babyfish.jimmer.json.codec.Node
import org.babyfish.jimmer.meta.ImmutableType
import org.babyfish.jimmer.runtime.DraftSpi
import org.babyfish.jimmer.runtime.ImmutableSpi
import org.babyfish.jimmer.runtime.Internal
import java.lang.reflect.GenericArrayType
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.lang.reflect.TypeVariable
import java.lang.reflect.WildcardType
import java.math.BigDecimal
import java.math.BigInteger
import java.util.UUID
import kotlin.reflect.KType
import kotlin.reflect.KTypeProjection
import kotlin.reflect.full.createType
import kotlin.reflect.full.starProjectedType
import kotlin.reflect.jvm.jvmErasure

/**
 * JSON codec backed by kotlinx.serialization.
 *
 * This codec is intentionally explicit: applications can pass it to Jimmer APIs such as
 * `JSqlClient.Builder#setJsonCodec` or `setDefaultSerializedTypeJsonCodec` when the
 * serialized application model is kotlinx-serializable.
 */
@OptIn(ExperimentalSerializationApi::class)
class KotlinxJsonCodec @JvmOverloads constructor(
    private val json: Json = DEFAULT_JSON
) : JsonCodec {

    override fun encode(value: Any?, type: JsonType, options: JsonCodecOptions): String {
        val effectiveJson = json.withOptions(options)
        val element = if (type.isAny) {
            KotlinxJsonSupport.encodeUntyped(effectiveJson, value, options)
        } else {
            KotlinxJsonSupport.encodeElement(
                effectiveJson,
                value,
                KotlinxJsonTypes.constructType(type),
                options
            )
        }
        return effectiveJson.encodeToString(JsonElement.serializer(), element)
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T : Any?> decode(json: String, type: JsonType, options: JsonCodecOptions): T {
        val effectiveJson = this.json.withOptions(options)
        val element = effectiveJson.parseToJsonElement(json)
        if (type.type == Node::class.java) {
            return KotlinxJsonNode(element) as T
        }
        val value = if (type.isAny) {
            KotlinxJsonSupport.decodeUntyped(element)
        } else {
            KotlinxJsonSupport.decodeElement(
                effectiveJson,
                element,
                KotlinxJsonTypes.constructType(type),
                options
            )
        }
        return value as T
    }

    companion object {
        @JvmField
        val DEFAULT_JSON: Json = Json {
            ignoreUnknownKeys = true
        }
    }
}

class KotlinxJsonCodecProvider @JvmOverloads constructor(
    private val json: Json = KotlinxJsonCodec.DEFAULT_JSON
) : org.babyfish.jimmer.json.codec.JsonCodecProvider {

    private val codec = KotlinxJsonCodec(json)

    override fun priority(): Int =
        400

    override fun supportsEncode(value: Any?, type: JsonType): Boolean =
        KotlinxJsonSupport.supportsEncode(json, value, type)

    override fun supportsDecode(type: JsonType): Boolean =
        KotlinxJsonSupport.supportsDecode(json, type)

    override fun codec(): JsonCodec =
        codec
}

@OptIn(ExperimentalSerializationApi::class)
private object KotlinxJsonSupport {

    fun supportsEncode(json: Json, value: Any?, type: JsonType): Boolean {
        if (value == null) {
            return true
        }
        if (!type.isAny) {
            if (value is ImmutableSpi) {
                return true
            }
            return hasSerializer(json) { KotlinxJsonTypes.constructType(type) }
        }
        return supportsUntypedValue(json, value)
    }

    fun supportsDecode(json: Json, type: JsonType): Boolean {
        if (type.isAny || type.type == Node::class.java) {
            return true
        }
        val rawType = type.type as? Class<*>
        if (rawType != null && ImmutableType.tryGet(rawType) != null) {
            return true
        }
        return hasSerializer(json) { KotlinxJsonTypes.constructType(type) }
    }

    fun encodeUntyped(
        json: Json,
        value: Any?,
        options: JsonCodecOptions
    ): JsonElement =
        when (value) {
            null -> JsonNull
            is JsonElement -> value
            is ImmutableSpi -> encodeImmutable(json, value, options)
            is Boolean -> JsonPrimitive(value)
            is Number -> JsonPrimitive(value)
            is String -> JsonPrimitive(value)
            is Char -> JsonPrimitive(value.toString())
            is UUID -> JsonPrimitive(value.toString())
            is Enum<*> -> JsonPrimitive(value.name)
            is Iterable<*> -> JsonArray(value.map { encodeUntyped(json, it, options) })
            is Array<*> -> JsonArray(value.map { encodeUntyped(json, it, options) })
            is BooleanArray -> JsonArray(value.map { JsonPrimitive(it) })
            is ByteArray -> JsonArray(value.map { JsonPrimitive(it) })
            is ShortArray -> JsonArray(value.map { JsonPrimitive(it) })
            is IntArray -> JsonArray(value.map { JsonPrimitive(it) })
            is LongArray -> JsonArray(value.map { JsonPrimitive(it) })
            is FloatArray -> JsonArray(value.map { JsonPrimitive(it) })
            is DoubleArray -> JsonArray(value.map { JsonPrimitive(it) })
            is CharArray -> JsonArray(value.map { JsonPrimitive(it.toString()) })
            is Map<*, *> -> JsonObject(
                value.entries.associate { (key, entryValue) ->
                    key.toString() to encodeUntyped(json, entryValue, options)
                }
            )
            else -> encodeElement(
                json,
                value,
                KotlinxJsonTypes.constructRuntimeType(json, value),
                options
            )
        }

    private fun supportsUntypedValue(json: Json, value: Any?): Boolean {
        if (isJacksonValue(value)) {
            return false
        }
        return when (value) {
            null,
            is JsonElement,
            is ImmutableSpi,
            is Boolean,
            is Number,
            is String,
            is Char,
            is UUID,
            is Enum<*>,
            is BooleanArray,
            is ByteArray,
            is ShortArray,
            is IntArray,
            is LongArray,
            is FloatArray,
            is DoubleArray,
            is CharArray -> true
            is Iterable<*> -> value.all { supportsUntypedValue(json, it) }
            is Array<*> -> value.all { supportsUntypedValue(json, it) }
            is Map<*, *> -> value.values.all { supportsUntypedValue(json, it) }
            else -> hasSerializer(json) { KotlinxJsonTypes.constructRuntimeType(json, value) }
        }
    }

    private fun isJacksonValue(value: Any?): Boolean {
        val type = value?.javaClass ?: return false
        return JsonCodecProviderUtil.containsType(
            type,
            "com.fasterxml.jackson.core.",
            "com.fasterxml.jackson.databind.",
            "tools.jackson."
        )
    }

    private fun hasSerializer(json: Json, typeProvider: () -> KType): Boolean =
        try {
            json.serializersModule.serializerOrNull(typeProvider()) != null
        } catch (_: IllegalArgumentException) {
            false
        }

    fun encodeElement(
        json: Json,
        value: Any?,
        type: KType,
        options: JsonCodecOptions
    ): JsonElement {
        if (value == null) {
            return JsonNull
        }
        if (value is ImmutableSpi) {
            return encodeImmutable(json, value, options)
        }
        val serializer = json.serializersModule.serializer(type) as KSerializer<Any?>
        return json.encodeToJsonElement(serializer, value)
    }

    fun decodeElement(
        json: Json,
        element: JsonElement,
        type: KType,
        options: JsonCodecOptions
    ): Any? {
        if (element is JsonNull) {
            return null
        }
        val immutableType = type.javaClassOrNull()?.let(ImmutableType::tryGet)
        if (immutableType !== null && element is JsonObject) {
            return decodeImmutable(json, element, immutableType, options)
        }
        val serializer = json.serializersModule.serializer(type) as KSerializer<Any?>
        return json.decodeFromJsonElement(serializer, element)
    }

    fun decodeUntyped(element: JsonElement): Any? =
        when (element) {
            JsonNull -> null
            is JsonObject -> element.mapValues { (_, value) -> decodeUntyped(value) }
            is JsonArray -> element.map(::decodeUntyped)
            is JsonPrimitive -> when {
                element.isString -> element.content
                element.booleanOrNull !== null -> element.booleanOrNull
                element.longOrNull !== null -> element.longOrNull
                else -> element.doubleOrNull ?: element.content
            }
        }

    private fun encodeImmutable(
        json: Json,
        value: ImmutableSpi,
        options: JsonCodecOptions
    ): JsonObject {
        val fields = linkedMapOf<String, JsonElement>()
        for (prop in value.__type().props.values) {
            val propId = prop.id
            if (value.__isLoaded(propId) && value.__isVisible(propId)) {
                val name = propertyName(prop.name, options.propertyNaming)
                fields[name] = encodeUntyped(json, value.__get(propId), options)
            }
        }
        return JsonObject(fields)
    }

    private fun decodeImmutable(
        json: Json,
        element: JsonObject,
        immutableType: ImmutableType,
        options: JsonCodecOptions
    ): Any =
        Internal.produce(immutableType, null) { draft ->
            val spi = draft as DraftSpi
            for (prop in immutableType.props.values) {
                val name = propertyName(prop.name, options.propertyNaming)
                val propElement = element[name] ?: continue
                val value = decodeElement(
                    json,
                    propElement,
                    KotlinxJsonTypes.constructType(prop.genericType),
                    options
                )
                spi.__set(prop.id, value)
            }
        }

    private fun KType.javaClassOrNull(): Class<*>? =
        jvmErasure.java
}

@OptIn(ExperimentalSerializationApi::class)
private fun Json.withOptions(options: JsonCodecOptions): Json {
    if (!options.isPrettyPrint && options.propertyNaming === null) {
        return this
    }
    return Json(this) {
        if (options.isPrettyPrint) {
            prettyPrint = true
        }
        options.propertyNaming?.let { propertyNaming ->
            namingStrategy = CodecNamingStrategy(propertyNaming)
        }
    }
}

@OptIn(ExperimentalSerializationApi::class)
private class CodecNamingStrategy(
    private val propertyNaming: JsonCodecOptions.PropertyNaming
) : JsonNamingStrategy {
    override fun serialNameForJson(
        descriptor: kotlinx.serialization.descriptors.SerialDescriptor,
        elementIndex: Int,
        serialName: String
    ): String =
        propertyName(serialName, propertyNaming)
}

private fun propertyName(
    name: String,
    propertyNaming: JsonCodecOptions.PropertyNaming?
): String =
    when (propertyNaming) {
        null ->
            name
        JsonCodecOptions.PropertyNaming.LOWER_CAMEL_CASE ->
            name.replaceFirstChar { it.lowercaseChar() }
        JsonCodecOptions.PropertyNaming.UPPER_CAMEL_CASE ->
            name.replaceFirstChar { it.uppercaseChar() }
        JsonCodecOptions.PropertyNaming.LOWER_CASE ->
            name.lowercase()
        JsonCodecOptions.PropertyNaming.SNAKE_CASE ->
            separatedPropertyName(name, '_')
        JsonCodecOptions.PropertyNaming.KEBAB_CASE ->
            separatedPropertyName(name, '-')
        JsonCodecOptions.PropertyNaming.LOWER_DOT_CASE ->
            separatedPropertyName(name, '.')
    }

private fun separatedPropertyName(name: String, separator: Char): String {
    val result = StringBuilder(name.length + 4)
    name.forEachIndexed { index, character ->
        val previous = name.getOrNull(index - 1)
        val next = name.getOrNull(index + 1)
        if (
            character.isUpperCase() &&
            index > 0 &&
            (previous?.isLowerCase() == true || previous?.isDigit() == true || next?.isLowerCase() == true)
        ) {
            result.append(separator)
        }
        result.append(character.lowercaseChar())
    }
    return result.toString()
}

private object KotlinxJsonTypes {

    fun constructType(type: JsonType): KType =
        constructType(type.type)

    fun constructType(type: Type): KType =
        type.toKType()

    fun constructRuntimeType(json: Json, value: Any): KType {
        val runtimeType = value::class.createType()
        return value::class.supertypes.firstOrNull { superType ->
            val classifier = superType.classifier as? kotlin.reflect.KClass<*>
            classifier?.isSealed == true &&
                json.serializersModule.serializerOrNull(superType) != null
        } ?: runtimeType
    }

    fun constructArrayType(componentType: KType): KType =
        Array<Any?>::class.createType(listOf(KTypeProjection.invariant(componentType)))

    private fun Type.toKType(): KType =
        when (this) {
            is Class<*> -> toKType()
            is ParameterizedType -> {
                val rawClass = rawType as? Class<*>
                    ?: throw IllegalArgumentException("Parameterized raw type \"$rawType\" is not a class")
                rawClass.kotlin.createType(
                    actualTypeArguments.map { argument ->
                        KTypeProjection.invariant(argument.toKType())
                    }
                )
            }
            is GenericArrayType -> constructArrayType(genericComponentType.toKType())
            is WildcardType -> upperBounds.firstOrNull()?.toKType() ?: Any::class.starProjectedType
            is TypeVariable<*> -> bounds.firstOrNull()?.toKType() ?: Any::class.starProjectedType
            else -> throw IllegalArgumentException("Unsupported type: $this")
        }

    private fun Class<*>.toKType(): KType =
        kotlin.createType()
}

private class KotlinxJsonNode(
    internal val element: JsonElement
) : Node {

    override fun get(index: Int): Node? =
        (element as? JsonArray)?.getOrNull(index)?.let(::KotlinxJsonNode)

    override fun get(fieldName: String): Node? =
        (element as? JsonObject)?.get(fieldName)?.let(::KotlinxJsonNode)

    override fun fieldsIterator(): Iterator<Map.Entry<String, Node>> {
        val jsonObject = element as? JsonObject ?: return emptyMap<String, Node>().entries.iterator()
        return jsonObject.entries
            .map { (key, value) -> mapEntry(key, KotlinxJsonNode(value)) }
            .iterator()
    }

    override fun isNull(): Boolean =
        element is JsonNull

    override fun canCastTo(type: Class<*>): Boolean =
        CASTER_MAP.containsKey(type)

    @Suppress("UNCHECKED_CAST")
    override fun <T : Any?> castTo(type: Class<T>): T {
        val caster = CASTER_MAP[type]
            ?: throw IllegalArgumentException("Cannot cast node to type ${type.name}")
        return caster(element) as T
    }

    override fun equals(other: Any?): Boolean =
        other is KotlinxJsonNode && element == other.element

    override fun hashCode(): Int =
        element.hashCode()

    override fun toString(): String =
        element.toString()

    private fun mapEntry(key: String, value: Node): Map.Entry<String, Node> =
        object : Map.Entry<String, Node> {
            override val key: String = key
            override val value: Node = value
        }

    companion object {
        private val CASTER_MAP: Map<Class<*>, (JsonElement) -> Any?> = mapOf(
            Boolean::class.javaPrimitiveType!! to { it.jsonPrimitive().booleanOrNull },
            Boolean::class.java to { it.jsonPrimitive().booleanOrNull },
            Char::class.javaPrimitiveType!! to { it.jsonPrimitive().contentOrNull?.firstOrNull() },
            Char::class.java to { it.jsonPrimitive().contentOrNull?.firstOrNull() },
            Byte::class.javaPrimitiveType!! to { it.jsonPrimitive().intOrNull?.toByte() },
            Byte::class.java to { it.jsonPrimitive().intOrNull?.toByte() },
            Short::class.javaPrimitiveType!! to { it.jsonPrimitive().intOrNull?.toShort() },
            Short::class.java to { it.jsonPrimitive().intOrNull?.toShort() },
            Int::class.javaPrimitiveType!! to { it.jsonPrimitive().intOrNull },
            Int::class.java to { it.jsonPrimitive().intOrNull },
            Long::class.javaPrimitiveType!! to { it.jsonPrimitive().longOrNull },
            Long::class.java to { it.jsonPrimitive().longOrNull },
            Float::class.javaPrimitiveType!! to { it.jsonPrimitive().floatOrNull },
            Float::class.java to { it.jsonPrimitive().floatOrNull },
            Double::class.javaPrimitiveType!! to { it.jsonPrimitive().doubleOrNull },
            Double::class.java to { it.jsonPrimitive().doubleOrNull },
            BigInteger::class.java to { it.jsonPrimitive().contentOrNull?.toBigInteger() },
            BigDecimal::class.java to { it.jsonPrimitive().contentOrNull?.toBigDecimal() },
            String::class.java to { it.jsonPrimitive().contentOrNull },
            UUID::class.java to { it.jsonPrimitive().contentOrNull?.let(UUID::fromString) }
        )

        private fun JsonElement.jsonPrimitive(): JsonPrimitive =
            this as? JsonPrimitive ?: throw SerializationException("JSON element is not a primitive: $this")
    }
}
