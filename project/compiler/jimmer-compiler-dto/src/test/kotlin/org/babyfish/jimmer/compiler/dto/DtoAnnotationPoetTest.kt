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
import site.addzero.lsi.jimmer.ImmutableSchema
import site.addzero.lsi.jimmer.dto.DtoAnnotation
import site.addzero.lsi.jimmer.dto.DtoAnnotationArgument
import site.addzero.lsi.jimmer.dto.DtoAnnotationApplication
import site.addzero.lsi.jimmer.dto.DtoAnnotationContract
import site.addzero.lsi.jimmer.dto.DtoAnnotationDeclaration
import site.addzero.lsi.jimmer.dto.DtoAnnotationOrigin
import site.addzero.lsi.jimmer.dto.DtoAnnotationPlacement
import site.addzero.lsi.jimmer.dto.DtoAnnotationValue
import site.addzero.lsi.jimmer.dto.DtoPropAnnotationPlan
import site.addzero.lsi.jimmer.dto.DtoPropId
import site.addzero.lsi.jimmer.dto.DtoType
import site.addzero.lsi.jimmer.dto.DtoTypeAnnotationPlan
import site.addzero.lsi.jimmer.dto.DtoTypeId
import site.addzero.lsi.jimmer.dto.DtoTypeRef
import site.addzero.lsi.jimmer.dto.DtoUserProp
import site.addzero.lsi.model.LsiAnnotation
import site.addzero.lsi.model.LsiAnnotationArgument
import site.addzero.lsi.model.LsiAnnotationArgumentOrigin
import site.addzero.lsi.model.LsiAnnotationValue
import site.addzero.lsi.type.LsiArrayType
import site.addzero.lsi.type.LsiDeclaredType
import site.addzero.lsi.model.LsiSourceAnnotationArgument
import site.addzero.lsi.model.LsiTypeName
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
                    language = LsiLanguage.JAVA,
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
                annotation.sourceArguments.map { argument ->
                    assertIs<LsiSourceAnnotationArgument.Named>(argument).name
                }
            },
        )
        assertEquals(
            listOf(
                listOf("first-z", "first-w", "first-a"),
                listOf("second-z", "second-w", "second-a"),
            ),
            annotations.map { annotation ->
                annotation.sourceArguments.map { argument ->
                    assertIs<LsiAnnotationValue.StringValue>(argument.value).value
                }
            },
        )
        val renderedKotlinAnnotation = LsiKotlinPoetRenderer().renderAnnotation(
            annotation = dtoType.typeAnnotationPoetAnnotations(contract, LsiLanguage.KOTLIN).first(),
            typeNames = listOf(
                LsiTypeName(REPEATED_ANNOTATION_TYPE_ID, "demo", listOf("Repeated")),
            ),
        )
        assertContains(renderedKotlinAnnotation.toString(), "`when` = \"first-w\"")
    }

    @Test
    fun `preserves repeated dto property annotation occurrences and source argument order`() {
        val dtoProp = DtoUserProp(
            id = DTO_PROP_ID,
            ownerTypeId = DTO_TYPE_ID,
            name = "value",
            alias = "value",
            nullable = false,
            annotations = listOf(
                sourceAnnotation("first-z", "first-w", "first-a"),
                sourceAnnotation("second-z", "second-w", "second-a"),
            ),
            documentation = null,
            aliasLocation = LOCATION,
            type = DtoTypeRef("java.lang.String", emptyList(), false, LOCATION),
            defaultValueText = null,
        )
        val contract = DtoAnnotationContract(
            declarations = listOf(repeatedAnnotationDeclaration(DtoAnnotationPlacement.FIELD)),
            typePlans = emptyList(),
            propPlans = listOf(
                DtoPropAnnotationPlan(
                    propId = DTO_PROP_ID,
                    propertyApplications = listOf(
                        application("first-z", "first-w", "first-a").copy(
                            placements = listOf(DtoAnnotationPlacement.FIELD),
                        ),
                        application("second-z", "second-w", "second-a").copy(
                            placements = listOf(DtoAnnotationPlacement.FIELD),
                        ),
                    ),
                    builderSetterApplications = emptyList(),
                ),
            ),
            diagnostics = emptyList(),
        )

        val annotations = dtoProp.propertyAnnotationPoetAnnotations(
            contract,
            ImmutableSchema(emptyList()),
            LsiLanguage.KOTLIN,
        )

        assertEquals(
            listOf(
                listOf("first-z", "first-w", "first-a"),
                listOf("second-z", "second-w", "second-a"),
            ),
            annotations.map { annotation ->
                annotation.sourceArguments.map { argument ->
                    assertIs<LsiAnnotationValue.StringValue>(argument.value).value
                }
            },
        )
        val rendered = LsiKotlinPoetRenderer().renderAnnotation(
            annotation = annotations.first(),
            typeNames = listOf(
                LsiTypeName(REPEATED_ANNOTATION_TYPE_ID, "demo", listOf("Repeated")),
            ),
        )
        assertContains(rendered.toString(), "`when` = \"first-w\"")
    }

    @Test
    fun `preserves named value for java array annotation copied to kotlin property`() {
        val annotationTypeId = LsiSymbolId.type("demo.Alias")
        val frozen = LsiAnnotation(
            type = annotationTypeId,
            arguments = mapOf(
                "value" to LsiAnnotationArgument(
                    value = LsiAnnotationValue.ArrayValue(
                        listOf(LsiAnnotationValue.StringValue("base-edition")),
                    ),
                    origin = LsiAnnotationArgumentOrigin.EXPLICIT,
                ),
            ),
            explicitArgumentNamesInSourceOrder = listOf("value"),
        )
        val contract = DtoAnnotationContract(
            declarations = listOf(
                DtoAnnotationDeclaration(
                    typeId = annotationTypeId,
                    language = LsiLanguage.JAVA,
                    targetDeclared = true,
                    allowedPlacements = listOf(DtoAnnotationPlacement.FIELD),
                    argumentTypes = sortedMapOf(
                        "value" to LsiArrayType(LsiDeclaredType(STRING_TYPE_ID)),
                    ),
                    kotlinValueVararg = false,
                ),
            ),
            typePlans = emptyList(),
            propPlans = emptyList(),
            diagnostics = emptyList(),
        )

        val annotation = DtoAnnotationApplication(
            annotation = frozen,
            origin = DtoAnnotationOrigin.IMMUTABLE,
            sourceSymbolId = LsiSymbolId.property(LsiSymbolId.type("demo.Book"), "edition"),
            placements = listOf(DtoAnnotationPlacement.FIELD),
        ).toDtoPoetAnnotation(
            dtoSourceAnnotation = null,
            annotationContract = contract,
            targetLanguage = LsiLanguage.KOTLIN,
            includeImmutableDefaultArguments = true,
        )

        assertEquals("value", assertIs<LsiSourceAnnotationArgument.Named>(annotation.sourceArguments.single()).name)
        val rendered = LsiKotlinPoetRenderer().renderAnnotation(
            annotation,
            listOf(LsiTypeName(annotationTypeId, "demo", listOf("Alias"))),
        )
        assertEquals("@demo.Alias(value = \"base-edition\")", rendered.toString())
    }

    @Test
    fun `escapes kotlin keyword in java annotation member copied with defaults`() {
        val annotationTypeId = LsiSymbolId.type("demo.Rule")
        val frozen = LsiAnnotation(
            type = annotationTypeId,
            arguments = mapOf(
                "when" to LsiAnnotationArgument(
                    value = LsiAnnotationValue.StringValue("default"),
                    origin = LsiAnnotationArgumentOrigin.DEFAULT,
                ),
            ),
        )
        val contract = DtoAnnotationContract(
            declarations = listOf(
                DtoAnnotationDeclaration(
                    typeId = annotationTypeId,
                    language = LsiLanguage.JAVA,
                    targetDeclared = true,
                    allowedPlacements = listOf(DtoAnnotationPlacement.FIELD),
                    argumentTypes = sortedMapOf("when" to LsiDeclaredType(STRING_TYPE_ID)),
                    kotlinValueVararg = false,
                ),
            ),
            typePlans = emptyList(),
            propPlans = emptyList(),
            diagnostics = emptyList(),
        )

        val annotation = DtoAnnotationApplication(
            annotation = frozen,
            origin = DtoAnnotationOrigin.IMMUTABLE,
            sourceSymbolId = LsiSymbolId.property(LsiSymbolId.type("demo.Book"), "name"),
            placements = listOf(DtoAnnotationPlacement.FIELD),
        ).toDtoPoetAnnotation(
            dtoSourceAnnotation = null,
            annotationContract = contract,
            targetLanguage = LsiLanguage.KOTLIN,
            includeImmutableDefaultArguments = true,
        )
        val rendered = LsiKotlinPoetRenderer().renderAnnotation(
            annotation,
            listOf(LsiTypeName(annotationTypeId, "demo", listOf("Rule"))),
        )

        assertEquals("@demo.Rule(`when` = \"default\")", rendered.toString())
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

    private fun repeatedAnnotationDeclaration(
        placement: DtoAnnotationPlacement,
    ): DtoAnnotationDeclaration {
        return DtoAnnotationDeclaration(
            typeId = REPEATED_ANNOTATION_TYPE_ID,
            language = LsiLanguage.JAVA,
            targetDeclared = true,
            allowedPlacements = listOf(placement),
            argumentTypes = sortedMapOf(
                "alpha" to LsiDeclaredType(STRING_TYPE_ID),
                "when" to LsiDeclaredType(STRING_TYPE_ID),
                "zeta" to LsiDeclaredType(STRING_TYPE_ID),
            ),
            kotlinValueVararg = false,
            argumentNamesInDeclarationOrder = listOf("alpha", "when", "zeta"),
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
        val DTO_PROP_ID = DtoPropId("dto#repeated#value")
        val REPEATED_ANNOTATION_TYPE_ID = LsiSymbolId.type("demo.Repeated")
        val STRING_TYPE_ID = LsiSymbolId.type("java.lang.String")
        val LOCATION = LsiLocation(
            source = LsiSource.of("demo/Repeated.dto"),
            start = LsiPosition(1, 1),
        )
    }
}
