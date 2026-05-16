package site.addzero.lsi.jimmer.immutable.metadata.generator

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import site.addzero.lsi.poet.LsiCallableSpec
import site.addzero.lsi.poet.LsiCallableSpecKind
import site.addzero.lsi.poet.LsiClassName
import site.addzero.lsi.poet.LsiCodeBlock
import site.addzero.lsi.poet.LsiCodeExpression
import site.addzero.lsi.poet.LsiExpressionStatement
import site.addzero.lsi.poet.LsiFileSpec
import site.addzero.lsi.poet.LsiParameterSpec
import site.addzero.lsi.poet.LsiPropertySpec
import site.addzero.lsi.poet.LsiTypeSpec
import site.addzero.lsi.poet.LsiTypeSpecKind

class ImmutableJavaBoundaryExtensionAndRawCodeAuditTest {

    @Test
    fun `nested extension members are explicit java blockers`() {
        val fileSpec = LsiFileSpec(
            packageName = "test.model",
            name = "BadExtension",
            types = listOf(
                LsiTypeSpec(
                    name = "BadExtension",
                    kind = LsiTypeSpecKind.CLASS,
                    properties = listOf(
                        LsiPropertySpec(
                            name = "value",
                            type = LsiClassName.bestGuess("kotlin.Int"),
                            receiverType = LsiClassName.bestGuess("test.model.Host"),
                        )
                    ),
                    callables = listOf(
                        LsiCallableSpec(
                            kind = LsiCallableSpecKind.FUNCTION,
                            name = "touch",
                            receiverType = LsiClassName.bestGuess("test.model.Host"),
                            returnType = LsiClassName.bestGuess("kotlin.Unit"),
                        )
                    ),
                )
            ),
        )

        assertEquals(listOf("extension members"), fileSpec.javaBoundaryBlockers())
    }

    @Test
    fun `nested kotlin raw code is explicit java blocker`() {
        val fileSpec = LsiFileSpec(
            packageName = "test.model",
            name = "BadRawCode",
            types = listOf(
                LsiTypeSpec(
                    name = "BadRawCode",
                    kind = LsiTypeSpecKind.CLASS,
                    callables = listOf(
                        LsiCallableSpec(
                            kind = LsiCallableSpecKind.FUNCTION,
                            name = "touch",
                            statements = listOf(
                                LsiExpressionStatement(
                                    LsiCodeExpression(
                                        LsiCodeBlock.of("val tmp = other?.toString() ?: error(%S)", "boom")
                                    )
                                )
                            ),
                        )
                    ),
                )
            ),
        )

        assertEquals(listOf("Kotlin-only raw code"), fileSpec.javaBoundaryBlockers())
    }

    @Test
    fun `default argument raw code is explicit java blocker`() {
        val fileSpec = LsiFileSpec(
            packageName = "test.model",
            name = "BadDefaultValue",
            types = listOf(
                LsiTypeSpec(
                    name = "BadDefaultValue",
                    kind = LsiTypeSpecKind.CLASS,
                    callables = listOf(
                        LsiCallableSpec(
                            kind = LsiCallableSpecKind.FUNCTION,
                            name = "touch",
                            parameters = listOf(
                                LsiParameterSpec(
                                    name = "other",
                                    type = LsiClassName.bestGuess("java.lang.String"),
                                    defaultValue = LsiCodeBlock.of("other?.toString() ?: error(%S)", "boom"),
                                )
                            ),
                            returnType = LsiClassName.bestGuess("kotlin.Unit"),
                        )
                    ),
                )
            ),
        )

        assertEquals(listOf("Kotlin-only raw code"), fileSpec.javaBoundaryBlockers())
    }
}
