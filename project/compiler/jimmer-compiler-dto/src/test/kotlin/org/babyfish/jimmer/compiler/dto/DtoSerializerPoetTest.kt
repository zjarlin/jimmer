package org.babyfish.jimmer.compiler.dto

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import org.babyfish.jimmer.compiler.JacksonFamily
import site.addzero.lsi.core.LsiLanguage
import site.addzero.lsi.core.LsiLocation
import site.addzero.lsi.core.LsiPosition
import site.addzero.lsi.core.LsiSource
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.jimmer.AssociationKind
import site.addzero.lsi.jimmer.AssociationStorageKind
import site.addzero.lsi.jimmer.FormulaKind
import site.addzero.lsi.jimmer.ImmutableProp
import site.addzero.lsi.jimmer.ImmutableSchema
import site.addzero.lsi.jimmer.ImmutableType
import site.addzero.lsi.jimmer.ImmutableTypeKind
import site.addzero.lsi.jimmer.PrimaryMapping
import site.addzero.lsi.jimmer.dto.DtoBaseProp
import site.addzero.lsi.jimmer.dto.DtoBasePropBinding
import site.addzero.lsi.jimmer.dto.DtoGraph
import org.babyfish.jimmer.dto.compiler.DtoModifier
import site.addzero.lsi.jimmer.dto.DtoProp
import site.addzero.lsi.jimmer.dto.DtoPropId
import site.addzero.lsi.jimmer.dto.DtoType
import site.addzero.lsi.jimmer.dto.DtoTypeId
import site.addzero.lsi.type.LsiDeclaredType
import site.addzero.lsi.type.LsiPrimitiveKind
import site.addzero.lsi.type.LsiPrimitiveType
import site.addzero.lsi.type.LsiType
import site.addzero.lsi.model.LsiTypeName
import site.addzero.lsi.poet.javapoet.LsiJavaPoetRenderer
import site.addzero.lsi.poet.kotlinpoet.LsiKotlinPoetRenderer

class DtoSerializerPoetTest {

    @Test
    fun `renders Java and Kotlin serializers for both Jackson generations`() {
        val graph = graph()
        val type = graph.types.single()
        val schema = immutableSchema()
        val dtoTypeName = LsiTypeName(
            LsiSymbolId.type("demo.BookInput"),
            "demo",
            listOf("BookInput"),
        )
        val dtoType = LsiDeclaredType(dtoTypeName.typeId)

        val java2 = LsiJavaPoetRenderer().renderType(
            type.toSerializerPoetType(
                graph,
                schema,
                LsiLanguage.JAVA,
                JacksonFamily.JACKSON_2,
                dtoType,
            ),
            JacksonFamily.JACKSON_2.serializerPoetTypeNames(dtoTypeName),
        ).toString()
        val java3 = LsiJavaPoetRenderer().renderType(
            type.toSerializerPoetType(
                graph,
                schema,
                LsiLanguage.JAVA,
                JacksonFamily.JACKSON_3,
                dtoType,
            ),
            JacksonFamily.JACKSON_3.serializerPoetTypeNames(dtoTypeName),
        ).toString()
        val kotlin2 = LsiKotlinPoetRenderer().renderType(
            type.toSerializerPoetType(
                graph,
                schema,
                LsiLanguage.KOTLIN,
                JacksonFamily.JACKSON_2,
                dtoType,
            ),
            JacksonFamily.JACKSON_2.serializerPoetTypeNames(dtoTypeName),
        ).toString()
        val kotlin3 = LsiKotlinPoetRenderer().renderType(
            type.toSerializerPoetType(
                graph,
                schema,
                LsiLanguage.KOTLIN,
                JacksonFamily.JACKSON_3,
                dtoType,
            ),
            JacksonFamily.JACKSON_3.serializerPoetTypeNames(dtoTypeName),
        ).toString()

        assertEquals(
            """
                public static class Serializer extends com.fasterxml.jackson.databind.JsonSerializer<demo.BookInput> {
                  @java.lang.Override
                  public void serialize(demo.BookInput input, com.fasterxml.jackson.core.JsonGenerator gen,
                      com.fasterxml.jackson.databind.SerializerProvider provider) throws java.io.IOException {
                    gen.writeStartObject();
                    if (input.isWhenLoaded()) {
                      provider.defaultSerializeField("when", input.getWhen(), gen);
                    }
                    provider.defaultSerializeField("enabled", input.isEnabled(), gen);
                    provider.defaultSerializeField("isbn", input.getIsbn(), gen);
                    provider.defaultSerializeField("price", input.getPrice(), gen);
                    gen.writeEndObject();
                  }
                }
            """.trimIndent() + "\n",
            java2,
        )
        assertEquals(
            """
                public static class Serializer extends tools.jackson.databind.ValueSerializer<demo.BookInput> {
                  @java.lang.Override
                  public void serialize(demo.BookInput input, tools.jackson.core.JsonGenerator gen,
                      tools.jackson.databind.SerializationContext provider) {
                    gen.writeStartObject();
                    if (input.isWhenLoaded()) {
                      provider.defaultSerializeProperty("when", input.getWhen(), gen);
                    }
                    provider.defaultSerializeProperty("enabled", input.isEnabled(), gen);
                    provider.defaultSerializeProperty("isbn", input.getIsbn(), gen);
                    provider.defaultSerializeProperty("price", input.getPrice(), gen);
                    gen.writeEndObject();
                  }
                }
            """.trimIndent() + "\n",
            java3,
        )
        assertEquals(
            """
                public class Serializer : com.fasterxml.jackson.databind.JsonSerializer<demo.BookInput>() {
                  override fun serialize(
                    input: demo.BookInput,
                    gen: com.fasterxml.jackson.core.JsonGenerator,
                    provider: com.fasterxml.jackson.databind.SerializerProvider,
                  ) {
                    gen.writeStartObject()
                    if (input.isWhenLoaded) {
                      provider.defaultSerializeField("when", input.`when`, gen)
                    }
                    provider.defaultSerializeField("enabled", input.enabled, gen)
                    provider.defaultSerializeField("isbn", input.isbn, gen)
                    provider.defaultSerializeField("price", input.price, gen)
                    gen.writeEndObject()
                  }
                }
            """.trimIndent() + "\n",
            kotlin2,
        )
        assertEquals(
            """
                public class Serializer : tools.jackson.databind.ValueSerializer<demo.BookInput>() {
                  override fun serialize(
                    input: demo.BookInput,
                    gen: tools.jackson.core.JsonGenerator,
                    provider: tools.jackson.databind.SerializationContext,
                  ) {
                    gen.writeStartObject()
                    if (input.isWhenLoaded) {
                      provider.defaultSerializeProperty("when", input.`when`, gen)
                    }
                    provider.defaultSerializeProperty("enabled", input.enabled, gen)
                    provider.defaultSerializeProperty("isbn", input.isbn, gen)
                    provider.defaultSerializeProperty("price", input.price, gen)
                    gen.writeEndObject()
                  }
                }
            """.trimIndent() + "\n",
            kotlin3,
        )
    }

    @Test
    fun `rejects unsupported language and DTOs without dynamic input properties`() {
        val graph = graph()
        val type = graph.types.single()
        val schema = immutableSchema()
        val dtoType = LsiDeclaredType(LsiSymbolId.type("demo.BookInput"))

        assertFailsWith<IllegalArgumentException> {
            type.toSerializerPoetType(
                graph,
                schema,
                LsiLanguage.UNKNOWN,
                JacksonFamily.JACKSON_2,
                dtoType,
            )
        }

        val fixedGraph = graph.copy(
            props = graph.props.map { prop ->
                if (prop.id == DYNAMIC_PROP_ID) {
                    (prop as DtoBaseProp).copy(inputModifier = DtoModifier.FIXED)
                } else {
                    prop
                }
            },
        )
        assertFailsWith<IllegalArgumentException> {
            type.toSerializerPoetType(
                fixedGraph,
                schema,
                LsiLanguage.JAVA,
                JacksonFamily.JACKSON_2,
                dtoType,
            )
        }
    }

    private fun graph(): DtoGraph {
        val dynamicProp = dtoProp(
            id = DYNAMIC_PROP_ID,
            name = "when",
            baseName = "title",
            nullable = true,
            modifier = DtoModifier.DYNAMIC,
        )
        val fixedProp = dtoProp(
            id = FIXED_PROP_ID,
            name = "enabled",
            baseName = "active",
            nullable = false,
            modifier = DtoModifier.FIXED,
        )
        val staticProp = dtoProp(
            id = STATIC_PROP_ID,
            name = "isbn",
            baseName = "isbn",
            nullable = true,
            modifier = DtoModifier.STATIC,
        )
        val fuzzyProp = dtoProp(
            id = FUZZY_PROP_ID,
            name = "price",
            baseName = "price",
            nullable = true,
            modifier = DtoModifier.FUZZY,
        )
        val type = DtoType(
            id = DTO_TYPE_ID,
            baseTypeId = IMMUTABLE_TYPE_ID,
            packageName = "demo",
            name = "BookInput",
            modifiers = setOf(DtoModifier.INPUT),
            annotations = emptyList(),
            superInterfaces = emptyList(),
            documentation = null,
            location = LOCATION,
            focusedRecursion = false,
            propIds = listOf(dynamicProp.id, fixedProp.id, staticProp.id, fuzzyProp.id),
            hiddenFlatPropIds = emptyList(),
            polymorphism = null,
        )
        return DtoGraph(
            source = SOURCE,
            rootTypeIds = listOf(type.id),
            types = listOf(type),
            props = listOf(dynamicProp, fixedProp, staticProp, fuzzyProp).sortedBy(DtoProp::id),
        )
    }

    private fun dtoProp(
        id: DtoPropId,
        name: String,
        baseName: String,
        nullable: Boolean,
        modifier: DtoModifier,
    ): DtoBaseProp {
        return DtoBaseProp(
            id = id,
            ownerTypeId = DTO_TYPE_ID,
            name = name,
            alias = name,
            nullable = nullable,
            annotations = emptyList(),
            documentation = null,
            aliasLocation = LOCATION,
            baseLocation = LOCATION,
            baseProps = listOf(
                DtoBasePropBinding(
                    name = baseName,
                    propId = LsiSymbolId.property(IMMUTABLE_TYPE_ID, baseName),
                ),
            ),
            basePath = baseName,
            nextPropId = null,
            tailPropId = id,
            baseNullable = nullable,
            inputModifier = modifier,
            functionName = null,
            targetTypeId = null,
            enumType = null,
            config = null,
            recursive = false,
            likeOptions = emptySet(),
        )
    }

    private fun immutableSchema(): ImmutableSchema {
        val props = listOf(
            immutableProp("title", LsiDeclaredType(LsiSymbolId.type("java.lang.String"))),
            immutableProp("active", LsiPrimitiveType(LsiPrimitiveKind.BOOLEAN)),
            immutableProp("isbn", LsiDeclaredType(LsiSymbolId.type("java.lang.String"))),
            immutableProp("price", LsiDeclaredType(LsiSymbolId.type("java.math.BigDecimal"))),
        )
        return ImmutableSchema(
            listOf(
                ImmutableType(
                    id = IMMUTABLE_TYPE_ID,
                    qualifiedName = IMMUTABLE_TYPE_ID.requireTypeQualifiedName(),
                    kind = ImmutableTypeKind.IMMUTABLE,
                    documentation = null,
                    annotations = emptyList(),
                    typeParameterIds = emptyList(),
                    superTypeIds = emptyList(),
                    props = props,
                    primarySuperTypeId = null,
                    inheritanceRootTypeId = null,
                    inheritanceStrategy = null,
                    joinedTableDissociateAction = null,
                    instantiable = false,
                    discriminatorValue = null,
                    discriminatorPropId = null,
                    idPropId = null,
                    versionPropId = null,
                    logicalDeletedPropId = null,
                    acrossMicroServices = false,
                    microServiceName = "",
                ),
            ),
        )
    }

    private fun immutableProp(
        name: String,
        type: LsiType,
    ): ImmutableProp {
        val id = LsiSymbolId.property(IMMUTABLE_TYPE_ID, name)
        return ImmutableProp(
            id = id,
            declarationId = id,
            ownerTypeId = IMMUTABLE_TYPE_ID,
            declaringTypeId = IMMUTABLE_TYPE_ID,
            name = name,
            documentation = null,
            type = type,
            annotations = emptyList(),
            overrideChain = emptyList(),
            inherited = false,
            overridden = false,
            nullable = false,
            list = false,
            association = false,
            embedded = false,
            targetTypeId = null,
            primaryMapping = PrimaryMapping.SCALAR,
            primaryAnnotationTypeId = null,
            defaultContract = null,
            associationKind = AssociationKind.NONE,
            formulaKind = FormulaKind.NONE,
            mappedBy = null,
            associationStorage = AssociationStorageKind.NONE,
            transientResolver = null,
            view = null,
            genericTarget = false,
            remote = false,
            recursive = false,
            validations = emptyList(),
            converter = null,
        )
    }

    private companion object {
        val SOURCE = LsiSource.of("demo/src/main/dto/Book.dto")
        val LOCATION = LsiLocation(SOURCE, LsiPosition(1, 1))
        val DTO_TYPE_ID = DtoTypeId("dto#book-input")
        val DYNAMIC_PROP_ID = DtoPropId("dto#z-dynamic")
        val FIXED_PROP_ID = DtoPropId("dto#a-fixed")
        val STATIC_PROP_ID = DtoPropId("dto#m-static")
        val FUZZY_PROP_ID = DtoPropId("dto#b-fuzzy")
        val IMMUTABLE_TYPE_ID = LsiSymbolId.type("demo.Book")
    }
}
