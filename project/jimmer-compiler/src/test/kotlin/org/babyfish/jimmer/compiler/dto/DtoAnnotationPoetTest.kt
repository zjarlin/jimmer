package org.babyfish.jimmer.compiler.dto

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertIs
import site.addzero.lsi.core.LsiLanguage
import site.addzero.lsi.core.LsiLocation
import site.addzero.lsi.core.LsiPosition
import site.addzero.lsi.core.LsiSource
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.jimmer.dto.DtoAnnotation
import site.addzero.lsi.jimmer.dto.DtoAnnotationArgument
import site.addzero.lsi.jimmer.dto.DtoAnnotationApplication
import site.addzero.lsi.jimmer.dto.DtoAnnotationContract
import site.addzero.lsi.jimmer.dto.DtoAnnotationDeclaration
import site.addzero.lsi.jimmer.dto.DtoAnnotationDeclarationKind
import site.addzero.lsi.jimmer.dto.DtoAnnotationOrigin
import site.addzero.lsi.jimmer.dto.DtoAnnotationPlacement
import site.addzero.lsi.jimmer.dto.DtoAnnotationValue
import site.addzero.lsi.jimmer.dto.DtoType
import site.addzero.lsi.jimmer.dto.DtoTypeAnnotationPlan
import site.addzero.lsi.jimmer.dto.DtoTypeId
import site.addzero.lsi.model.LsiAnnotation
import site.addzero.lsi.model.LsiAnnotationArgument
import site.addzero.lsi.model.LsiAnnotationArgumentOrigin
import site.addzero.lsi.model.LsiAnnotationValue
import site.addzero.lsi.model.LsiDeclaredType
import site.addzero.lsi.poet.LsiPoetAnnotationArgument
import site.addzero.lsi.poet.LsiPoetAnnotationValue
import site.addzero.lsi.poet.LsiPoetTypeName
import site.addzero.lsi.poet.kotlinpoet.LsiKotlinPoetRenderer

class DtoAnnotationPoetTest {

    @Test
    fun `preserves repeated dto annotation occurrences and source argument order`() {
        val dtoType = DtoType(
            id = DTO_TYPE_ID,
            baseTypeId = null,
            packageName = "demo",
            name = "RepeatedDto",
            modifiers = emptySet(),
            annotations = listOf(
                sourceAnnotation("first-z", "first-w", "first-a"),
                sourceAnnotation("second-z", "second-w", "second-a"),
            ),
            superInterfaces = emptyList(),
            documentation = null,
            location = LOCATION,
            focusedRecursion = false,
            propIds = emptyList(),
            hiddenFlatPropIds = emptyList(),
            polymorphism = null,
        )
        val contract = DtoAnnotationContract(
            declarations = listOf(
                DtoAnnotationDeclaration(
                    typeId = REPEATED_ANNOTATION_TYPE_ID,
                    kind = DtoAnnotationDeclarationKind.JAVA,
                    targetDeclared = true,
                    allowedPlacements = listOf(DtoAnnotationPlacement.TYPE),
                    argumentTypes = sortedMapOf(
                        "alpha" to LsiDeclaredType(STRING_TYPE_ID),
                        "when" to LsiDeclaredType(STRING_TYPE_ID),
                        "zeta" to LsiDeclaredType(STRING_TYPE_ID),
                    ),
                    kotlinValueVararg = false,
                    argumentNamesInDeclarationOrder = listOf("alpha", "when", "zeta"),
                ),
            ),
            typePlans = listOf(
                DtoTypeAnnotationPlan(
                    typeId = DTO_TYPE_ID,
                    applications = listOf(
                        application("first-z", "first-w", "first-a"),
                        application("second-z", "second-w", "second-a"),
                    ),
                ),
            ),
            propPlans = emptyList(),
            diagnostics = emptyList(),
        )

        val annotations = dtoType.typeAnnotationPoetAnnotations(contract, LsiLanguage.JAVA)

        assertEquals(2, annotations.size)
        assertEquals(
            listOf(listOf("zeta", "when", "alpha"), listOf("zeta", "when", "alpha")),
            annotations.map { annotation ->
                annotation.arguments.map { argument ->
                    assertIs<LsiPoetAnnotationArgument.Named>(argument).name
                }
            },
        )
        assertEquals(
            listOf(
                listOf("first-z", "first-w", "first-a"),
                listOf("second-z", "second-w", "second-a"),
            ),
            annotations.map { annotation ->
                annotation.arguments.map { argument ->
                    assertIs<LsiPoetAnnotationValue.StringValue>(argument.value).value
                }
            },
        )
        val renderedKotlinAnnotation = LsiKotlinPoetRenderer().renderAnnotation(
            annotation = dtoType.typeAnnotationPoetAnnotations(contract, LsiLanguage.KOTLIN).first(),
            typeNames = listOf(
                LsiPoetTypeName(REPEATED_ANNOTATION_TYPE_ID, "demo", listOf("Repeated")),
            ),
        )
        assertContains(renderedKotlinAnnotation.toString(), "`when` = \"first-w\"")
    }

    private fun sourceAnnotation(zeta: String, whenValue: String, alpha: String): DtoAnnotation {
        return DtoAnnotation(
            typeId = REPEATED_ANNOTATION_TYPE_ID,
            arguments = listOf(
                DtoAnnotationArgument("zeta", DtoAnnotationValue.LiteralValue(zeta)),
                DtoAnnotationArgument("when", DtoAnnotationValue.LiteralValue(whenValue)),
                DtoAnnotationArgument("alpha", DtoAnnotationValue.LiteralValue(alpha)),
            ),
        )
    }

    private fun application(zeta: String, whenValue: String, alpha: String): DtoAnnotationApplication {
        return DtoAnnotationApplication(
            annotation = LsiAnnotation(
                type = REPEATED_ANNOTATION_TYPE_ID,
                arguments = mapOf(
                    "alpha" to explicit(alpha),
                    "when" to explicit(whenValue),
                    "zeta" to explicit(zeta),
                ),
            ),
            origin = DtoAnnotationOrigin.DTO,
            sourceSymbolId = null,
            placements = listOf(DtoAnnotationPlacement.TYPE),
        )
    }

    private fun explicit(value: String): LsiAnnotationArgument {
        return LsiAnnotationArgument(
            value = LsiAnnotationValue.StringValue(value),
            origin = LsiAnnotationArgumentOrigin.EXPLICIT,
        )
    }

    private companion object {
        val DTO_TYPE_ID = DtoTypeId("dto#repeated")
        val REPEATED_ANNOTATION_TYPE_ID = LsiSymbolId.type("demo.Repeated")
        val STRING_TYPE_ID = LsiSymbolId.type("java.lang.String")
        val LOCATION = LsiLocation(
            source = LsiSource.of("demo/Repeated.dto"),
            start = LsiPosition(1, 1),
        )
    }
}
