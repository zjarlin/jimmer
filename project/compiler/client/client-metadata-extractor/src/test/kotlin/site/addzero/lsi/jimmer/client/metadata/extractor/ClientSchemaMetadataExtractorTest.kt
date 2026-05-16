package site.addzero.lsi.jimmer.client.metadata.extractor

import org.babyfish.jimmer.client.meta.Doc
import org.babyfish.jimmer.client.meta.TypeDefinition
import org.babyfish.jimmer.client.meta.TypeName
import org.babyfish.jimmer.client.meta.impl.EnumConstantImpl
import org.babyfish.jimmer.client.meta.impl.SchemaBuilder
import org.babyfish.jimmer.client.meta.impl.SchemaImpl
import org.babyfish.jimmer.client.meta.impl.TypeRefImpl
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import site.addzero.lsi.jimmer.client.metadata.model.ClientTypeDefinitionKindMetadata

class ClientSchemaMetadataExtractorTest {

    @Test
    fun extracts_client_schema_metadata_from_runtime_schema() {
        val schema = TestSchemaBuilder().buildSchema()

        val metadata = ClientSchemaMetadataExtractor().extract(schema)

        assertEquals(1, metadata.services.size)
        assertEquals("test.BookService", metadata.services.single().typeName)
        assertEquals("service doc\n", metadata.services.single().doc)
        assertEquals(1, metadata.services.single().operations.size)
        assertEquals("findBook", metadata.services.single().operations.single().name)
        assertEquals("findBook:long", metadata.services.single().operations.single().key)
        assertEquals("op doc\n", metadata.services.single().operations.single().doc)
        assertEquals("id", metadata.services.single().operations.single().parameters.single().name)
        assertEquals("long", metadata.services.single().operations.single().parameters.single().type.typeName)
        assertEquals(2, metadata.definitions.size)
        assertEquals(
            ClientTypeDefinitionKindMetadata.OBJECT,
            metadata.definitions.first { it.typeName == "test.Book" }.kind,
        )
        assertEquals(
            "prop doc\n",
            metadata.definitions.first { it.typeName == "test.Book" }.properties.single().doc,
        )
        assertEquals(
            "BOOK",
            metadata.definitions.first { it.typeName == "test.BookError" }.error?.family,
        )
    }

    private class TestSchemaBuilder : SchemaBuilder<String>(null) {

        override fun loadSource(typeName: String): String? = null

        override fun throwException(source: String, message: String) {
            throw IllegalStateException(message)
        }

        override fun fillDefinition(source: String) {
            throw UnsupportedOperationException()
        }

        fun buildSchema(): SchemaImpl<String> {
            val schema = current<SchemaImpl<String>>()
            api("test.BookService", TypeName.parse("test.BookService")) { service ->
                service.setGroups(listOf("book"))
                service.setDoc(Doc.parse("service doc"))
                operation("test.BookService#findBook", "findBook") { operation ->
                    operation.setGroups(listOf("book"))
                    operation.setDoc(Doc.parse("op doc"))
                    parameter("test.BookService#findBook#id", "id") { parameter ->
                        parameter.setOriginalIndex(0)
                        parameter.setType(simpleTypeRef("long"))
                        operation.addParameter(parameter)
                    }
                    operation.setReturnType(simpleTypeRef("test.Book"))
                    operation.setExceptionTypeNames(listOf(TypeName.parse("test.BookError")))
                    operation.setKey("findBook:long")
                    service.addOperation(operation)
                }
                schema.addApiService(service)
            }
            definition("test.Book", TypeName.parse("test.Book")) { definition ->
                definition.setKind(TypeDefinition.Kind.OBJECT)
                definition.setDoc(Doc.parse("book doc"))
                prop("test.Book#name", "name") { prop ->
                    prop.setDoc(Doc.parse("prop doc"))
                    prop.setType(simpleTypeRef("java.lang.String"))
                    definition.addProp(prop)
                }
                schema.addTypeDefinition(definition)
            }
            definition("test.BookError", TypeName.parse("test.BookError")) { definition ->
                definition.setKind(TypeDefinition.Kind.OBJECT)
                definition.setError(TypeDefinition.Error("BOOK", "NOT_FOUND"))
                schema.addTypeDefinition(definition)
            }
            return schema
        }

        private fun simpleTypeRef(typeName: String): TypeRefImpl<String> =
            TypeRefImpl<String>().apply {
                setTypeName(TypeName.parse(typeName))
            }
    }
}
