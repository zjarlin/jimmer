package site.addzero.lsi.jimmer.client

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import site.addzero.lsi.core.LsiLocation
import site.addzero.lsi.core.LsiOrigin
import site.addzero.lsi.core.LsiOriginKind
import site.addzero.lsi.core.LsiPosition
import site.addzero.lsi.core.LsiSource
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.jimmer.ImmutableSchema
import site.addzero.lsi.jimmer.dto.DtoBaseProp
import site.addzero.lsi.jimmer.dto.DtoBasePropBinding
import site.addzero.lsi.jimmer.dto.DtoGraph
import site.addzero.lsi.jimmer.dto.DtoModifier
import site.addzero.lsi.jimmer.dto.DtoProp
import site.addzero.lsi.jimmer.dto.DtoPropId
import site.addzero.lsi.jimmer.dto.DtoType
import site.addzero.lsi.jimmer.dto.DtoTypeId
import site.addzero.lsi.jimmer.dto.DtoTypeRef
import site.addzero.lsi.jimmer.dto.DtoUserProp
import site.addzero.lsi.jimmer.toImmutableSchema
import site.addzero.lsi.model.LsiAnnotation
import site.addzero.lsi.model.LsiDeclaredType
import site.addzero.lsi.model.LsiProperty
import site.addzero.lsi.model.LsiTypeDeclaration
import site.addzero.lsi.model.LsiTypeDeclarationKind
import site.addzero.lsi.model.LsiWorkspace

class ClientDtoDocumentationTest {

    @Test
    fun `freezes dto and immutable property documentation`() {
        val fallback = listOf(graph(typeDocumentation = "Book view."))
            .toClientDefinitionDocumentation(immutableSchema())
            .getValue(CLIENT_TYPE_ID)

        assertEquals("Book view.", fallback.type)
        assertEquals(
            mapOf(
                "title" to "Immutable title.",
                "display" to "Display label.",
            ),
            fallback.properties,
        )

        val explicit = listOf(
            graph(
                typeDocumentation = "Book view.",
                dtoPropertyDocumentation = "DTO title.",
            )
        ).toClientDefinitionDocumentation(immutableSchema())
        assertEquals("DTO title.", explicit.getValue(CLIENT_TYPE_ID).properties.getValue("title"))
    }

    @Test
    fun `rejects conflicting documentation for the same client type`() {
        val exception = assertFailsWith<IllegalArgumentException> {
            listOf(
                graph(typeDocumentation = "First."),
                graph(typeDocumentation = "Second."),
            ).toClientDefinitionDocumentation(immutableSchema())
        }

        assertTrue(exception.message.orEmpty().contains(CLIENT_TYPE_ID.value))
    }

    private fun graph(
        typeDocumentation: String,
        dtoPropertyDocumentation: String? = null,
    ): DtoGraph {
        val rootTypeId = DtoTypeId("${SOURCE.path}#root")
        val basePropId = DtoPropId("${SOURCE.path}#root/prop:title")
        val userPropId = DtoPropId("${SOURCE.path}#root/prop:user")
        val type = DtoType(
            id = rootTypeId,
            baseTypeId = IMMUTABLE_TYPE_ID,
            packageName = "demo",
            name = "BookView",
            modifiers = emptySet(),
            annotations = emptyList(),
            superInterfaces = emptyList(),
            documentation = typeDocumentation,
            location = LOCATION,
            focusedRecursion = false,
            propIds = listOf(basePropId, userPropId),
            hiddenFlatPropIds = emptyList(),
            polymorphism = null,
        )
        val baseProp = DtoBaseProp(
            id = basePropId,
            ownerTypeId = rootTypeId,
            name = "title",
            alias = null,
            nullable = false,
            annotations = emptyList(),
            documentation = "Raw DTO property documentation.",
            aliasLocation = LOCATION,
            baseLocation = LOCATION,
            baseProps = listOf(DtoBasePropBinding("title", IMMUTABLE_PROP_ID)),
            basePath = "title",
            nextPropId = null,
            tailPropId = basePropId,
            baseNullable = false,
            inputModifier = DtoModifier.STATIC,
            functionName = null,
            targetTypeId = null,
            enumType = null,
            config = null,
            recursive = false,
            likeOptions = emptySet(),
            dtoDocumentation = dtoPropertyDocumentation,
        )
        val userProp = DtoUserProp(
            id = userPropId,
            ownerTypeId = rootTypeId,
            name = "display",
            alias = "display",
            nullable = false,
            annotations = emptyList(),
            documentation = "Display label.",
            aliasLocation = LOCATION,
            type = DtoTypeRef(
                typeName = "kotlin.String",
                arguments = emptyList(),
                nullable = false,
                location = LOCATION,
            ),
            defaultValueText = null,
        )
        return DtoGraph(
            source = SOURCE,
            rootTypeIds = listOf(rootTypeId),
            types = listOf(type),
            props = listOf<DtoProp>(baseProp, userProp).sortedBy(DtoProp::id),
        )
    }

    private fun immutableSchema(): ImmutableSchema {
        val property = LsiProperty(
            id = IMMUTABLE_PROP_ID,
            name = "title",
            ownerId = IMMUTABLE_TYPE_ID,
            type = LsiDeclaredType(LsiSymbolId.type("java.lang.String")),
            documentation = "Immutable title.",
            origin = ORIGIN,
        )
        val type = LsiTypeDeclaration(
            id = IMMUTABLE_TYPE_ID,
            name = "Book",
            qualifiedName = IMMUTABLE_TYPE_ID.requireTypeQualifiedName(),
            kind = LsiTypeDeclarationKind.INTERFACE,
            memberIds = listOf(property.id),
            annotations = listOf(LsiAnnotation(IMMUTABLE_ANNOTATION)),
            origin = ORIGIN,
        )
        return LsiWorkspace(declarations = listOf(type, property))
            .toImmutableSchema(setOf(IMMUTABLE_TYPE_ID))
    }

    companion object {
        private val SOURCE = LsiSource.of("demo/src/main/dto/Book.dto")
        private val LOCATION = LsiLocation(SOURCE, LsiPosition(1, 1))
        private val ORIGIN = LsiOrigin(LsiOriginKind.SYNTHETIC)
        private val CLIENT_TYPE_ID = LsiSymbolId.type("demo.BookView")
        private val IMMUTABLE_TYPE_ID = LsiSymbolId.type("demo.Book")
        private val IMMUTABLE_PROP_ID = LsiSymbolId.property(IMMUTABLE_TYPE_ID, "title")
        private val IMMUTABLE_ANNOTATION = LsiSymbolId.type("org.babyfish.jimmer.Immutable")
    }
}
