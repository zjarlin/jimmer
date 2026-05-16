package site.addzero.lsi.jimmer.client.metadata.generator

import org.babyfish.jimmer.client.meta.TypeName
import org.babyfish.jimmer.client.meta.impl.ApiOperationImpl
import org.babyfish.jimmer.client.meta.impl.Schemas
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import site.addzero.lsi.jimmer.client.metadata.model.ClientOperationMetadata
import site.addzero.lsi.jimmer.client.metadata.model.ClientParameterMetadata
import site.addzero.lsi.jimmer.client.metadata.model.ClientPropertyMetadata
import site.addzero.lsi.jimmer.client.metadata.model.ClientSchemaMetadata
import site.addzero.lsi.jimmer.client.metadata.model.ClientServiceMetadata
import site.addzero.lsi.jimmer.client.metadata.model.ClientTypeDefinitionErrorMetadata
import site.addzero.lsi.jimmer.client.metadata.model.ClientTypeDefinitionKindMetadata
import site.addzero.lsi.jimmer.client.metadata.model.ClientTypeDefinitionMetadata
import site.addzero.lsi.jimmer.client.metadata.model.ClientTypeRefMetadata
import java.io.StringReader

class ClientSchemaMetadataGeneratorTest {

    @Test
    fun generates_client_resource_roundtrip_from_metadata() {
        val generator = ClientSchemaMetadataGenerator()
        val metadata = ClientSchemaMetadata(
            services = listOf(
                ClientServiceMetadata(
                    typeName = "test.BookService",
                    groups = listOf("book"),
                    doc = "service doc\n",
                    operations = listOf(
                        ClientOperationMetadata(
                            name = "findBook",
                            key = "findBook:long",
                            groups = listOf("book"),
                            doc = "op doc\n",
                            parameters = listOf(
                                ClientParameterMetadata(
                                    name = "id",
                                    originalIndex = 0,
                                    type = typeRef("long"),
                                ),
                            ),
                            returnType = typeRef("test.Book"),
                            exceptionTypeNames = listOf("test.BookError"),
                        ),
                    ),
                ),
            ),
            definitions = listOf(
                ClientTypeDefinitionMetadata(
                    typeName = "test.Book",
                    kind = ClientTypeDefinitionKindMetadata.OBJECT,
                    apiIgnore = false,
                    doc = "book doc\n",
                    error = null,
                    groups = listOf("book"),
                    properties = listOf(
                        ClientPropertyMetadata(
                            name = "name",
                            doc = "prop doc\n",
                            type = typeRef("java.lang.String"),
                        ),
                    ),
                    superTypes = emptyList(),
                    enumConstants = emptyList(),
                ),
                ClientTypeDefinitionMetadata(
                    typeName = "test.BookError",
                    kind = ClientTypeDefinitionKindMetadata.OBJECT,
                    apiIgnore = false,
                    doc = null,
                    error = ClientTypeDefinitionErrorMetadata(
                        family = "BOOK",
                        code = "NOT_FOUND",
                    ),
                    groups = emptyList(),
                    properties = emptyList(),
                    superTypes = emptyList(),
                    enumConstants = emptyList(),
                ),
            ),
        )

        val artifact = generator.generate(metadata)

        assertEquals("META-INF/jimmer/client", artifact.path)
        val schema = StringReader(artifact.content).use(Schemas::readFrom)
        assertEquals(1, schema.apiServiceMap.size)
        val service = schema.apiServiceMap.values.single()
        assertEquals("test.BookService", service.typeName.toString())
        assertEquals("service doc\n", service.doc.toString())
        val operation = service.operations.single() as ApiOperationImpl<*>
        assertEquals("findBook", operation.name)
        assertEquals("findBook:long", operation.key())
        assertEquals("op doc\n", operation.doc.toString())
        assertEquals("id", operation.parameters.single().name)
        assertEquals("long", operation.parameters.single().type.typeName.toString())
        assertEquals("test.Book", operation.returnType!!.typeName.toString())
        assertEquals("test.BookError", operation.exceptionTypes.single().typeName.toString())
        val definition = schema.typeDefinitionMap[TypeName.parse("test.Book")]!!
        assertEquals("prop doc\n", definition.propMap.values.single().doc.toString())
        val errorDefinition = schema.typeDefinitionMap[TypeName.parse("test.BookError")]!!
        assertNotNull(errorDefinition.error)
        assertEquals("BOOK", errorDefinition.error!!.family)
    }

    private fun typeRef(typeName: String): ClientTypeRefMetadata =
        ClientTypeRefMetadata(
            typeName = typeName,
            nullable = false,
            arguments = emptyList(),
            fetchBy = null,
            fetcherOwnerTypeName = null,
            fetcherDoc = null,
        )
}
