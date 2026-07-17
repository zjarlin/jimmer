package org.babyfish.jimmer.compiler.lsi

import kotlin.test.Test
import kotlin.test.assertEquals
import site.addzero.lsi.core.LsiOrigin
import site.addzero.lsi.core.LsiOriginKind
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.model.LsiAnnotation
import site.addzero.lsi.model.LsiDeclaredType
import site.addzero.lsi.model.LsiOverride
import site.addzero.lsi.model.LsiProperty

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
            annotations = listOf(annotationA),
            overrides = listOf(LsiOverride(overriddenId)),
            origin = ORIGIN,
        )
        val beanGetter = bareGetter.copy(
            getterName = "getStudents",
            annotations = listOf(annotationB),
            overrides = emptyList(),
        )

        val merged = listOf(bareGetter, beanGetter)
            .mergeDeclarationsById()
            .single() as LsiProperty

        assertEquals("students", merged.getterName)
        assertEquals(listOf(annotationA, annotationB), merged.annotations)
        assertEquals(listOf(LsiOverride(overriddenId)), merged.overrides)
    }

    private companion object {
        val ORIGIN = LsiOrigin(LsiOriginKind.SYNTHETIC)
    }
}
