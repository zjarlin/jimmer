package site.addzero.lsi.jimmer.transactional.metadata.generator

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import site.addzero.lsi.jimmer.transactional.metadata.model.TxAnnotationArgumentMetadata
import site.addzero.lsi.jimmer.transactional.metadata.model.TxAnnotationMetadata
import site.addzero.lsi.jimmer.transactional.metadata.model.TxAnnotationValueMetadata
import site.addzero.lsi.jimmer.transactional.metadata.model.TxConstructorMetadata
import site.addzero.lsi.jimmer.transactional.metadata.model.TxMethodMetadata
import site.addzero.lsi.jimmer.transactional.metadata.model.TxParameterMetadata
import site.addzero.lsi.jimmer.transactional.metadata.model.TxTypeMetadata
import site.addzero.lsi.jimmer.transactional.metadata.model.TxTypeRefMetadata
import site.addzero.lsi.poet.renderKotlinSource

class TxMetadataGeneratorTest {

    @Test
    fun generates_expected_source_snapshot_for_simple_tx_type() {
        val generator = TxMetadataGenerator()
        val metadata = TxTypeMetadata(
            id = "test.BookService",
            sourceSimpleName = "BookService",
            sourceQualifiedName = "test.BookService",
            packageName = "test",
            generatedSimpleName = "BookServiceTx",
            generatedQualifiedName = "test.BookServiceTx",
            isInternal = false,
            isAbstract = false,
            superTypeQualifiedName = "test.BookService",
            copiedAnnotations = listOf(
                TxAnnotationMetadata(
                    qualifiedName = "kotlin.Deprecated",
                    arguments = listOf(
                        TxAnnotationArgumentMetadata(
                            name = "message",
                            value = TxAnnotationValueMetadata.StringValue("type"),
                        )
                    ),
                )
            ),
            targetAnnotationTypeQualifiedName = "test.MyTx",
            sqlClientPropertyName = "sqlClient",
            primaryConstructor = TxConstructorMetadata(
                id = "test.BookService#<init>(org.babyfish.jimmer.sql.kt.KSqlClient)",
                isProtected = false,
                isInternal = false,
                annotations = emptyList(),
                parameters = listOf(
                    TxParameterMetadata(
                        id = "test.BookService#<init>(org.babyfish.jimmer.sql.kt.KSqlClient)#0",
                        name = "sqlClient",
                        type = typeRef("org.babyfish.jimmer.sql.kt.KSqlClient"),
                    )
                ),
            ),
            secondaryConstructors = emptyList(),
            methods = listOf(
                TxMethodMetadata(
                    id = "test.BookService#findBook(kotlin.Long)",
                    name = "findBook",
                    propagation = "REQUIRED",
                    isProtected = false,
                    isInternal = false,
                    annotations = listOf(
                        TxAnnotationMetadata(
                            qualifiedName = "kotlin.jvm.JvmName",
                            arguments = listOf(
                                TxAnnotationArgumentMetadata(
                                    name = "name",
                                    value = TxAnnotationValueMetadata.StringValue("findBookTx"),
                                )
                            ),
                        )
                    ),
                    parameters = listOf(
                        TxParameterMetadata(
                            id = "test.BookService#findBook(kotlin.Long)#0",
                            name = "id",
                            type = typeRef("kotlin.Long"),
                        )
                    ),
                    returnType = typeRef("kotlin.String"),
                    thrownTypes = emptyList(),
                )
            ),
        )

        val fileSpec = generator.generate(metadata)

        assertEquals("test.BookServiceTx", fileSpec.qualifiedName)
        assertEquals(
            """
            @file:Suppress("warnings")
            
            package test
            
            import kotlin.Deprecated
            import kotlin.Long
            import kotlin.String
            import kotlin.Suppress
            import kotlin.jvm.JvmName
            import org.babyfish.jimmer.sql.kt.KSqlClient
            import org.babyfish.jimmer.sql.transaction.Propagation
            
            @Deprecated(message = "type")
            @MyTx
            public class BookServiceTx(
                sqlClient: KSqlClient,
            ) : BookService(sqlClient) {
                @JvmName(name = "findBookTx")
                override fun findBook(id: Long): String = this.sqlClient.transaction(Propagation.REQUIRED) { super.findBook(id) }
            }
            
            """.trimIndent(),
            fileSpec.renderKotlinSource(),
        )
    }

    private fun typeRef(
        qualifiedName: String,
    ): TxTypeRefMetadata =
        TxTypeRefMetadata(
            qualifiedName = qualifiedName,
            simpleName = qualifiedName.substringAfterLast('.'),
            presentableText = qualifiedName,
            nullable = false,
            primitive = false,
            array = false,
            typeArguments = emptyList(),
            componentType = null,
        )
}
