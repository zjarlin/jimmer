package org.babyfish.jimmer.compiler.client

import java.io.StringReader
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.babyfish.jimmer.client.meta.TypeDefinition
import org.babyfish.jimmer.client.meta.TypeName
import org.babyfish.jimmer.client.meta.impl.Schemas
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.jimmer.client.ClientDeclaredTypeRef
import site.addzero.lsi.jimmer.client.ClientDefinitionError
import site.addzero.lsi.jimmer.client.ClientDefinitionKind
import site.addzero.lsi.jimmer.client.ClientDefinitionProperty
import site.addzero.lsi.jimmer.client.ClientEnumConstant
import site.addzero.lsi.jimmer.client.ClientFetchBy
import site.addzero.lsi.jimmer.client.ClientOperation
import site.addzero.lsi.jimmer.client.ClientParameter
import site.addzero.lsi.jimmer.client.ClientPrimitiveTypeRef
import site.addzero.lsi.jimmer.client.ClientSchema
import site.addzero.lsi.jimmer.client.ClientService
import site.addzero.lsi.jimmer.client.ClientTypeArgument
import site.addzero.lsi.jimmer.client.ClientTypeDefinition
import site.addzero.lsi.jimmer.client.ClientTypeName
import site.addzero.lsi.type.LsiPrimitiveKind
import site.addzero.lsi.type.LsiVariance

class ClientResourceRendererTest {

    @Test
    fun `renders complete service and definition resource`() {
        val content = ClientResourceRenderer().render(schema())
        val rendered = Schemas.readFrom(StringReader(content))

        val service = rendered.apiServiceMap.getValue(TypeName.parse("demo.BookService"))
        val operation = service.operations.single()
        assertEquals("find", operation.name)
        assertEquals(listOf("public"), operation.groups)
        assertEquals(listOf("id"), operation.parameters.map { parameter -> parameter.name })
        assertEquals(TypeName.parse("demo.Book"), assertNotNull(operation.returnType).typeName)
        assertEquals(
            listOf(TypeName.parse("demo.BookException")),
            operation.exceptionTypes.map { type -> type.typeName },
        )

        val book = rendered.typeDefinitionMap.getValue(TypeName.parse("demo.Book"))
        assertEquals(TypeDefinition.Kind.IMMUTABLE, book.kind)
        assertEquals(listOf("id", "category"), book.propMap.keys.toList())
        val category = rendered.typeDefinitionMap.getValue(TypeName.parse("demo.Category"))
        assertEquals(TypeDefinition.Kind.ENUM, category.kind)
        assertEquals(listOf("BOOK"), category.enumConstantMap.keys.toList())
        val exception = rendered.typeDefinitionMap.getValue(TypeName.parse("demo.BookException"))
        assertEquals(TypeDefinition.Error("BOOK", "NOT_FOUND"), exception.error)
        assertContains(content, "\"fetchBy\" : \"BOOK_FETCHER\"")
        assertContains(content, "\"fetcherOwner\" : \"demo.BookFetchers\"")
        assertTrue(content.endsWith("\n}"))
    }

    @Test
    fun `renders a top level service in the default package`() {
        val serviceId = LsiSymbolId.type("Service")
        val content = ClientResourceRenderer().render(
            ClientSchema(
                services = listOf(
                    ClientService(
                        id = serviceId,
                        qualifiedName = "Service",
                        groups = emptyList(),
                        doc = null,
                        operations = emptyList(),
                    )
                ),
                definitions = emptyList(),
            )
        )

        val rendered = Schemas.readFrom(StringReader(content))
        assertTrue(TypeName.parse("Service") in rendered.apiServiceMap)
    }

    @Test
    fun `unwraps optional return types as nullable while preserving fetch metadata`() {
        val baseSchema = schema()
        val service = baseSchema.services.single()
        val operation = service.operations.single()
        val bookId = LsiSymbolId.type("demo.Book")
        val bookType = ClientTypeName("demo", listOf("Book"))
        val fetchBy = ClientFetchBy(
            value = "BOOK_FETCHER",
            ownerTypeId = LsiSymbolId.type("demo.BookFetchers"),
            ownerTypeName = ClientTypeName("demo", listOf("BookFetchers")),
            targetEntityTypeId = bookId,
            documentation = "Book fetcher.",
        )
        val optionalType = ClientDeclaredTypeRef(
            typeId = LsiSymbolId.type("java.util.Optional"),
            typeName = ClientTypeName("java.util", listOf("Optional")),
            arguments = listOf(
                ClientTypeArgument(
                    variance = LsiVariance.INVARIANT,
                    type = ClientDeclaredTypeRef(
                        typeId = bookId,
                        typeName = bookType,
                        fetchBy = fetchBy,
                    ),
                ),
            ),
        )
        val content = ClientResourceRenderer().render(
            baseSchema.copy(
                services = listOf(
                    service.copy(
                        operations = listOf(operation.copy(returnType = optionalType)),
                    )
                ),
            )
        )

        val rendered = Schemas.readFrom(StringReader(content))
            .apiServiceMap
            .getValue(TypeName.parse("demo.BookService"))
            .operations
            .single()
            .returnType
        assertNotNull(rendered)
        assertEquals(TypeName.parse("demo.Book"), rendered.typeName)
        assertTrue(rendered.isNullable)
        assertEquals("BOOK_FETCHER", rendered.fetchBy)
        assertEquals(TypeName.parse("demo.BookFetchers"), rendered.fetcherOwner)
    }

    private fun schema(): ClientSchema {
        val bookType = ClientTypeName("demo", listOf("Book"))
        val categoryType = ClientTypeName("demo", listOf("Category"))
        val exceptionType = ClientTypeName("demo", listOf("BookException"))
        val serviceId = LsiSymbolId.type("demo.BookService")
        val operationId = LsiSymbolId.function(serviceId, "find", listOf("primitive:long"))
        val bookId = LsiSymbolId.type("demo.Book")
        val categoryId = LsiSymbolId.type("demo.Category")
        val exceptionId = LsiSymbolId.type("demo.BookException")
        return ClientSchema(
            services = listOf(
                ClientService(
                    id = serviceId,
                    qualifiedName = "demo.BookService",
                    groups = listOf("public"),
                    doc = "Book service.",
                    operations = listOf(
                        ClientOperation(
                            id = operationId,
                            name = "find",
                            groups = listOf("public"),
                            doc = "Find a book.",
                            parameters = listOf(
                                ClientParameter(
                                    id = LsiSymbolId.parameter(operationId, 0, "id"),
                                    name = "id",
                                    originalIndex = 0,
                                    type = ClientPrimitiveTypeRef(LsiPrimitiveKind.LONG),
                                )
                            ),
                            ignoredParameters = emptyList(),
                            returnType = ClientDeclaredTypeRef(
                                typeId = bookId,
                                typeName = bookType,
                                fetchBy = ClientFetchBy(
                                    value = "BOOK_FETCHER",
                                    ownerTypeId = LsiSymbolId.type("demo.BookFetchers"),
                                    ownerTypeName = ClientTypeName("demo", listOf("BookFetchers")),
                                    targetEntityTypeId = bookId,
                                    documentation = "Book fetcher.",
                                ),
                            ),
                            declaredExceptionTypeIds = listOf(exceptionId),
                            exceptionTypeIds = listOf(exceptionId),
                            exceptionMetadata = emptyList(),
                        )
                    ),
                )
            ),
            definitions = listOf(
                ClientTypeDefinition(
                    id = bookId,
                    typeName = bookType,
                    kind = ClientDefinitionKind.IMMUTABLE,
                    apiIgnore = false,
                    doc = "Book.",
                    error = null,
                    properties = listOf(
                        ClientDefinitionProperty(
                            id = LsiSymbolId.property(bookId, "id"),
                            name = "id",
                            type = ClientPrimitiveTypeRef(LsiPrimitiveKind.LONG),
                            doc = "Book id.",
                        ),
                        ClientDefinitionProperty(
                            id = LsiSymbolId.property(bookId, "category"),
                            name = "category",
                            type = ClientDeclaredTypeRef(categoryId, categoryType),
                            doc = null,
                        ),
                    ),
                    superTypes = emptyList(),
                    polymorphicBranches = emptyList(),
                    enumConstants = emptyList(),
                ),
                ClientTypeDefinition(
                    id = exceptionId,
                    typeName = exceptionType,
                    kind = ClientDefinitionKind.OBJECT,
                    apiIgnore = false,
                    doc = null,
                    error = ClientDefinitionError("BOOK", "NOT_FOUND"),
                    properties = emptyList(),
                    superTypes = emptyList(),
                    polymorphicBranches = emptyList(),
                    enumConstants = emptyList(),
                ),
                ClientTypeDefinition(
                    id = categoryId,
                    typeName = categoryType,
                    kind = ClientDefinitionKind.ENUM,
                    apiIgnore = false,
                    doc = null,
                    error = null,
                    properties = emptyList(),
                    superTypes = emptyList(),
                    polymorphicBranches = emptyList(),
                    enumConstants = listOf(
                        ClientEnumConstant(
                            id = LsiSymbolId("${categoryId.value}#BOOK"),
                            name = "BOOK",
                            doc = "Book category.",
                        )
                    ),
                ),
            ).sortedBy(ClientTypeDefinition::id),
        )
    }
}
