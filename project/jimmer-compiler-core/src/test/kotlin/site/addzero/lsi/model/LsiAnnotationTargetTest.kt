package site.addzero.lsi.model

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import site.addzero.lsi.core.LsiLanguage
import site.addzero.lsi.core.LsiOrigin
import site.addzero.lsi.core.LsiOriginKind
import site.addzero.lsi.core.LsiSource
import site.addzero.lsi.core.LsiSymbolId

class LsiAnnotationTargetTest {

    @Test
    fun `allows every target when target annotation is absent`() {
        val policy = annotationDeclaration(emptyList()).annotationTargetPolicy()

        assertFalse(policy.declared)
        assertTrue(policy.allows(LsiAnnotationTarget.PARAMETER))
    }

    @Test
    fun `normalizes java and kotlin annotation targets`() {
        val policy = annotationDeclaration(
            listOf(
                targetAnnotation(
                    type = "java.lang.annotation.Target",
                    argumentName = "value",
                    enumType = "java.lang.annotation.ElementType",
                    entries = listOf("FIELD"),
                ),
                targetAnnotation(
                    type = "kotlin.annotation.Target",
                    argumentName = "allowedTargets",
                    enumType = "kotlin.annotation.AnnotationTarget",
                    entries = listOf("VALUE_PARAMETER"),
                ),
            ),
        ).annotationTargetPolicy()

        assertTrue(policy.declared)
        assertTrue(policy.allows(LsiAnnotationTarget.FIELD))
        assertTrue(policy.allows(LsiAnnotationTarget.PARAMETER))
        assertFalse(policy.allows(LsiAnnotationTarget.GETTER))
    }

    private fun annotationDeclaration(annotations: List<LsiAnnotation>): LsiTypeDeclaration {
        return LsiTypeDeclaration(
            id = LsiSymbolId.type("demo.Marker"),
            name = "Marker",
            qualifiedName = "demo.Marker",
            kind = LsiTypeDeclarationKind.ANNOTATION,
            annotations = annotations,
            origin = LsiOrigin(
                kind = LsiOriginKind.SOURCE,
                source = LsiSource.of("demo/Marker.kt", LsiLanguage.KOTLIN),
            ),
        )
    }

    private fun targetAnnotation(
        type: String,
        argumentName: String,
        enumType: String,
        entries: List<String>,
    ): LsiAnnotation {
        return LsiAnnotation(
            type = LsiSymbolId.type(type),
            arguments = mapOf(
                argumentName to LsiAnnotationArgument(
                    value = LsiAnnotationValue.ArrayValue(
                        entries.map { entry ->
                            LsiAnnotationValue.EnumValue(LsiSymbolId.type(enumType), entry)
                        },
                    ),
                    origin = LsiAnnotationArgumentOrigin.EXPLICIT,
                ),
            ),
        )
    }
}
