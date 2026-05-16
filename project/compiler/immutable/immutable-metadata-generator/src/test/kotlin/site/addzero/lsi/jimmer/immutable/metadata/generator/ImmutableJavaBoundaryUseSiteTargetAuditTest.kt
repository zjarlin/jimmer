package site.addzero.lsi.jimmer.immutable.metadata.generator

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import site.addzero.lsi.poet.LsiAnnotationSpec
import site.addzero.lsi.poet.LsiAnnotationUseSiteTarget
import site.addzero.lsi.poet.LsiClassName
import site.addzero.lsi.poet.LsiFileSpec
import site.addzero.lsi.poet.LsiLiteralExpression
import site.addzero.lsi.poet.LsiModifier
import site.addzero.lsi.poet.LsiPropertySpec
import site.addzero.lsi.poet.LsiReturnStatement
import site.addzero.lsi.poet.LsiTypeSpec
import site.addzero.lsi.poet.LsiTypeSpecKind

class ImmutableJavaBoundaryUseSiteTargetAuditTest {

    @Test
    fun `private field style property rejects getter use site target as java blocker`() {
        val fileSpec = LsiFileSpec(
            packageName = "test.model",
            name = "BadUseSite",
            types = listOf(
                LsiTypeSpec(
                    name = "BadUseSite",
                    kind = LsiTypeSpecKind.CLASS,
                    properties = listOf(
                        LsiPropertySpec(
                            name = "value",
                            type = LsiClassName.bestGuess("kotlin.Int"),
                            annotations = listOf(
                                LsiAnnotationSpec(
                                    type = LsiClassName.bestGuess("test.Generated"),
                                    useSiteTarget = LsiAnnotationUseSiteTarget.GET,
                                )
                            ),
                            modifiers = setOf(LsiModifier.PRIVATE),
                        )
                    ),
                )
            ),
        )

        assertEquals(listOf("unsupported use-site targets"), fileSpec.javaBoundaryBlockers())
    }

    @Test
    fun `accessor property keeps getter use site target java safe`() {
        val fileSpec = LsiFileSpec(
            packageName = "test.model",
            name = "AllowedUseSite",
            types = listOf(
                LsiTypeSpec(
                    name = "AllowedUseSite",
                    kind = LsiTypeSpecKind.CLASS,
                    properties = listOf(
                        LsiPropertySpec(
                            name = "value",
                            type = LsiClassName.bestGuess("kotlin.Int"),
                            annotations = listOf(
                                LsiAnnotationSpec(
                                    type = LsiClassName.bestGuess("test.Generated"),
                                    useSiteTarget = LsiAnnotationUseSiteTarget.GET,
                                )
                            ),
                            getterStatements = listOf(
                                LsiReturnStatement(LsiLiteralExpression(1))
                            ),
                        )
                    ),
                )
            ),
        )

        assertEquals(emptyList<String>(), fileSpec.javaBoundaryBlockers())
    }
}
