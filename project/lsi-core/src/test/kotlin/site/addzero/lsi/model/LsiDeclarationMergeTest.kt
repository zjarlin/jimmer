package site.addzero.lsi.model

import kotlin.test.Test
import kotlin.test.assertEquals
import site.addzero.lsi.core.LsiOrigin
import site.addzero.lsi.core.LsiOriginKind
import site.addzero.lsi.core.LsiSymbolId

class LsiDeclarationMergeTest {

    @Test
    fun `merges bare and java bean getters into one property`() {
        val ownerId = LsiSymbolId.type("demo.CourseDraft.Producer.Implementor")
        val propertyId = LsiSymbolId.property(ownerId, "students")
        val type = LsiDeclaredType(LsiSymbolId.type("java.util.List"))
        val annotationA = LsiAnnotation(LsiSymbolId.type("demo.A"))
        val annotationB = LsiAnnotation(LsiSymbolId.type("demo.B"))
        val overriddenId = LsiSymbolId.property(LsiSymbolId.type("demo.Course"), "students")
        val bareGetter = LsiProperty(
            id = propertyId,
            name = "students",
            ownerId = ownerId,
            getterName = "students",
            type = type,
            sourceDocumentation = "bare getter documentation",
            annotations = listOf(annotationA),
            overrides = listOf(LsiOverride(overriddenId)),
            origin = ORIGIN,
        )
        val beanGetter = bareGetter.copy(
            getterName = "getStudents",
            sourceDocumentation = "bean getter documentation",
            annotations = listOf(annotationB),
            overrides = emptyList(),
        )

        val merged = listOf(beanGetter, bareGetter)
            .mergeDeclarationsById()
            .single() as LsiProperty

        assertEquals("students", merged.getterName)
        assertEquals("bare getter documentation", merged.sourceDocumentation)
        assertEquals(listOf(annotationB, annotationA), merged.annotations)
        assertEquals(listOf(LsiOverride(overriddenId)), merged.overrides)
    }

    @Test
    fun `prefers primitive boolean is getter over boxed bean getter`() {
        val ownerId = LsiSymbolId.type("demo.Options")
        val propertyId = LsiSymbolId.property(ownerId, "lenient")
        val getGetter = LsiProperty(
            id = propertyId,
            name = "lenient",
            ownerId = ownerId,
            getterName = "getLenient",
            type = LsiDeclaredType(
                LsiSymbolId.type("java.lang.Boolean"),
                nullability = LsiNullability.PLATFORM,
            ),
            origin = ORIGIN,
        )
        val isGetter = getGetter.copy(
            getterName = "isLenient",
            type = LsiPrimitiveType(LsiPrimitiveKind.BOOLEAN),
        )

        val merged = listOf(getGetter, isGetter)
            .mergeDeclarationsById()
            .single() as LsiProperty

        assertEquals("isLenient", merged.getterName)
        assertEquals(LsiPrimitiveType(LsiPrimitiveKind.BOOLEAN), merged.type)
    }

    private companion object {
        val ORIGIN = LsiOrigin(LsiOriginKind.SYNTHETIC)
    }
}
