package site.addzero.lsi.type

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import site.addzero.lsi.anno.LsiAnnotation
import site.addzero.lsi.clazz.LsiClass
import site.addzero.lsi.field.LsiField
import site.addzero.lsi.method.LsiMethod

class LsiTypeSubtypeSemanticTest {

    @Test
    fun `shared subtype helpers walk lsi super type graph`() {
        val numberRoot = fakeClass("kotlin.Number")
        val comparableRoot = fakeClass("kotlin.Comparable")
        val temporalRoot = fakeClass("java.time.temporal.Temporal")
        val legacyDateRoot = fakeClass("java.util.Date")
        val runtimeExceptionRoot = fakeClass("java.lang.RuntimeException")

        val bigDecimalType = fakeType(
            qualifiedName = "java.math.BigDecimal",
            superTypes = listOf(
                fakeType("java.lang.Number", numberRoot),
                fakeType("java.lang.Comparable", comparableRoot),
            ),
        )
        val localDateTimeType = fakeType(
            qualifiedName = "java.time.LocalDateTime",
            superTypes = listOf(
                fakeType("java.time.temporal.Temporal", temporalRoot),
                fakeType("java.lang.Comparable", comparableRoot),
            ),
        )
        val timestampType = fakeType(
            qualifiedName = "java.sql.Timestamp",
            superTypes = listOf(
                fakeType("java.util.Date", legacyDateRoot),
                fakeType("java.lang.Comparable", comparableRoot),
            ),
        )
        val illegalStateType = fakeType(
            qualifiedName = "java.lang.IllegalStateException",
            superTypes = listOf(
                fakeType("java.lang.RuntimeException", runtimeExceptionRoot),
            ),
        )

        assertTrue(bigDecimalType.isSubtypeOfNumberLike())
        assertTrue(bigDecimalType.isSubtypeOfComparableLike())
        assertFalse(bigDecimalType.isSubtypeOfTemporalLike())
        assertFalse(bigDecimalType.isSubtypeOfJavaUtilDateLike())

        assertTrue(localDateTimeType.isSubtypeOfTemporalLike())
        assertTrue(localDateTimeType.isSubtypeOfComparableLike())
        assertFalse(localDateTimeType.isSubtypeOfJavaUtilDateLike())
        assertFalse(localDateTimeType.isSubtypeOfNumberLike())

        assertTrue(timestampType.isSubtypeOfJavaUtilDateLike())
        assertTrue(timestampType.isSubtypeOfComparableLike())
        assertFalse(timestampType.isSubtypeOfTemporalLike())

        assertTrue(illegalStateType.isSubtypeOfRuntimeExceptionLike())
        assertFalse(bigDecimalType.isSubtypeOfRuntimeExceptionLike())
    }

    private fun fakeType(
        qualifiedName: String,
        lsiClass: LsiClass? = null,
        superTypes: List<LsiType> = emptyList(),
    ): LsiType {
        val effectiveClass = lsiClass ?: fakeClass(qualifiedName, superTypes)
        return object : LsiType {
            override val simpleName: String = qualifiedName.substringAfterLast('.')
            override val qualifiedName: String = qualifiedName
            override val presentableText: String = qualifiedName
            override val annotations: List<LsiAnnotation> = emptyList()
            override val isCollectionType: Boolean = false
            override val typeParameters: List<LsiType> = emptyList()
            override val isPrimitive: Boolean = false
            override val componentType: LsiType? = null
            override val isArray: Boolean = false
            override val lsiClass: LsiClass = effectiveClass
        }
    }

    private fun fakeClass(
        qualifiedName: String,
        superTypes: List<LsiType> = emptyList(),
    ): LsiClass =
        object : LsiClass {
            override val simpleName: String = qualifiedName.substringAfterLast('.')
            override val qualifiedName: String = qualifiedName
            override val comment: String? = null
            override val fields: List<LsiField> = emptyList()
            override val annotations: List<LsiAnnotation> = emptyList()
            override val isInterface: Boolean = false
            override val isClass: Boolean = true
            override val isEnum: Boolean = false
            override val isCollectionType: Boolean = false
            override val isPojo: Boolean = false
            override val superClasses: List<LsiClass> = emptyList()
            override val superTypes: List<LsiType> = superTypes
            override val interfaces: List<LsiClass> = emptyList()
            override val methods: List<LsiMethod> = emptyList()
        }
}
