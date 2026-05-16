package site.addzero.lsi.jimmer.client

import org.babyfish.jimmer.client.meta.TypeName
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import site.addzero.lsi.anno.LsiAnnotation
import site.addzero.lsi.clazz.LsiClass
import site.addzero.lsi.field.LsiField
import site.addzero.lsi.jimmer.CLIENT_EXCEPTION
import site.addzero.lsi.method.LsiMethod
import site.addzero.lsi.method.LsiParameter
import site.addzero.lsi.type.LsiType

class ResolveClientExceptionTypeNamesTest {

    @Test
    fun `resolve client exception type names only depends on thrown types`() {
        val exceptionClass = FakeClass(
            simpleName = "OrderException",
            qualifiedName = "test.OrderException",
            annotations = listOf(
                FakeAnnotation(
                    qualifiedName = CLIENT_EXCEPTION,
                    attributes = mapOf(
                        "family" to "ORDER",
                        "code" to "NOT_FOUND",
                    ),
                )
            ),
        )
        val method = FakeMethod(
            thrownTypes = listOf(
                FakeType(
                    simpleName = exceptionClass.simpleName,
                    qualifiedName = exceptionClass.qualifiedName,
                    lsiClassValue = exceptionClass,
                )
            )
        )

        val result = resolveClientExceptionTypeNames(method, ClientExceptionContext())

        assertEquals(setOf(TypeName.of("test", listOf("OrderException"))), result)
    }

    private data class FakeAnnotation(
        override val qualifiedName: String?,
        override val attributes: Map<String, Any?>,
        override val annotations: List<LsiAnnotation> = emptyList(),
    ) : LsiAnnotation {
        override val simpleName: String?
            get() = qualifiedName?.substringAfterLast('.')

        override fun getAttribute(name: String): Any? =
            attributes[name]

        override fun hasAttribute(name: String): Boolean =
            attributes.containsKey(name)
    }

    private data class FakeClass(
        override val simpleName: String?,
        override val qualifiedName: String?,
        override val annotations: List<LsiAnnotation>,
    ) : LsiClass {
        override val comment: String? = null
        override val fields: List<LsiField> = emptyList()
        override val isInterface: Boolean = false
        override val isClass: Boolean = true
        override val isEnum: Boolean = false
        override val isCollectionType: Boolean = false
        override val isPojo: Boolean = false
        override val superClasses: List<LsiClass> = emptyList()
        override val interfaces: List<LsiClass> = emptyList()
        override val methods: List<LsiMethod> = emptyList()
    }

    private data class FakeType(
        override val simpleName: String?,
        override val qualifiedName: String?,
        val lsiClassValue: LsiClass?,
    ) : LsiType {
        override val presentableText: String? = qualifiedName
        override val annotations: List<LsiAnnotation> = emptyList()
        override val isCollectionType: Boolean = false
        override val typeParameters: List<LsiType> = emptyList()
        override val isPrimitive: Boolean = false
        override val componentType: LsiType? = null
        override val isArray: Boolean = false
        override val lsiClass: LsiClass? = lsiClassValue
    }

    private data class FakeMethod(
        override val thrownTypes: List<LsiType>,
    ) : LsiMethod {
        override val name: String? = "find"
        override val returnType: LsiType? = null
        override val returnTypeName: String? = null
        override val comment: String? = null
        override val annotations: List<LsiAnnotation> = emptyList()
        override val isStatic: Boolean = false
        override val isAbstract: Boolean = false
        override val parameters: List<LsiParameter> = emptyList()
        override val declaringClass: LsiClass? = null
    }
}
