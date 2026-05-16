package site.addzero.lsi.jimmer.error.metadata.generator

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import site.addzero.lsi.jimmer.error.metadata.model.ErrorItemMetadata
import site.addzero.lsi.jimmer.error.metadata.model.ErrorTypeMetadata
import site.addzero.lsi.poet.renderJavaSource
import site.addzero.lsi.poet.renderKotlinSource

class ErrorMetadataGeneratorTest {

    @Test
    fun generates_expected_source_snapshot_for_simple_error_type() {
        val generator = ErrorMetadataGenerator()
        val metadata = simpleMetadata()

        val fileSpec = generator.generate(metadata, checkedException = true)

        assertEquals("test.OrderException", fileSpec.qualifiedName)
        assertEquals(
            """
            package test
            
            import com.fasterxml.jackson.`annotation`.JsonIgnore
            import java.lang.Throwable
            import java.util.Collections
            import java.util.Map
            import kotlin.Any
            import kotlin.String
            import kotlin.jvm.JvmStatic
            import org.babyfish.jimmer.ClientException
            import org.babyfish.jimmer.`internal`.GeneratedBy
            import org.babyfish.jimmer.error.CodeBasedException
            
            @GeneratedBy(type = OrderErrorCode::class)
            @ClientException(
                family = "ORDER",
                subTypes = [OrderException.NotFound::class],
            )
            public abstract class OrderException public constructor(
                message: String? = null,
                cause: Throwable? = null,
            ) : CodeBasedException(message, cause) {
                @get:JsonIgnore
                public abstract val orderErrorCode: OrderErrorCode
            
                public override val fields: Map<String, Any?>
                    get() = Collections.emptyMap()
            
                public companion object {
                    @JvmStatic
                    public fun notFound(message: String? = null, cause: Throwable? = null): NotFound = NotFound(message, cause)
                }
            
                @ClientException(
                    family = "ORDER",
                    code = "NOT_FOUND",
                )
                public class NotFound public constructor(
                    message: String? = null,
                    cause: Throwable? = null,
                ) : OrderException(message, cause) {
                    @get:JsonIgnore
                    public override val orderErrorCode: OrderErrorCode
                        get() = OrderErrorCode.NOT_FOUND
            
                    public override val fields: Map<String, Any?>
                        get() = Collections.emptyMap()
                }
            }
            
            """.trimIndent(),
            fileSpec.renderKotlinSource()
        )
    }

    @Test
    fun renders_expected_java_shape_for_simple_error_type() {
        val javaSource = ErrorMetadataGenerator()
            .generate(simpleMetadata(), checkedException = true)
            .renderJavaSource()

        assertTrue(javaSource.contains("@GeneratedBy"))
        assertTrue(javaSource.contains("OrderErrorCode.class"))
        assertTrue(javaSource.contains("public OrderException()"))
        assertTrue(javaSource.contains("this(null, null);"))
        assertTrue(javaSource.contains("public abstract OrderErrorCode getOrderErrorCode();"))
        assertTrue(javaSource.contains("@JsonIgnore"))
        assertTrue(javaSource.contains("public static NotFound notFound()"))
        assertTrue(javaSource.contains("return notFound(null, null);"))
        assertTrue(javaSource.contains("public static class NotFound extends OrderException"))
        assertTrue(javaSource.contains("public Map<String, Object> getFields()"))
    }

    private fun simpleMetadata(): ErrorTypeMetadata =
        ErrorTypeMetadata(
            id = "test.OrderErrorCode",
            enumSimpleName = "OrderErrorCode",
            enumQualifiedName = "test.OrderErrorCode",
            packageName = "test",
            family = "ORDER",
            exceptionSimpleName = "OrderException",
            exceptionQualifiedName = "test.OrderException",
            doc = null,
            declaredFields = emptyList(),
            items = listOf(
                ErrorItemMetadata(
                    id = "test.OrderErrorCode#NOT_FOUND",
                    ownerTypeId = "test.OrderErrorCode",
                    enumConstantName = "NOT_FOUND",
                    exceptionSimpleName = "NotFound",
                    code = "NOT_FOUND",
                    doc = null,
                    declaredFields = emptyList()
                )
            )
        )
}
