package site.addzero.lsi.jimmer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.model.LsiAnnotation
import site.addzero.lsi.model.LsiAnnotationArgument
import site.addzero.lsi.model.LsiAnnotationArgumentOrigin
import site.addzero.lsi.model.LsiAnnotationUseSiteTarget
import site.addzero.lsi.model.LsiAnnotationValue
import site.addzero.lsi.model.LsiDeclaredType
import site.addzero.lsi.model.LsiWorkspace

class ImmutableDraftValidationTest {

    @Test
    fun `normalizes required built-in and custom validations`() {
        val prop = validationProp(
            annotations = listOf(
                annotation(NOT_BLANK),
                annotation(
                    SIZE,
                    "min" to LsiAnnotationValue.IntValue(2),
                    "max" to LsiAnnotationValue.IntValue(20),
                ),
                annotation(
                    PATTERN,
                    "regexp" to LsiAnnotationValue.StringValue("[A-Z]+"),
                    "flags" to LsiAnnotationValue.ArrayValue(
                        listOf(LsiAnnotationValue.EnumValue(PATTERN_FLAG, "CASE_INSENSITIVE")),
                    ),
                ),
            ),
            validations = listOf(
                ImmutableValidation(
                    annotationTypeId = CUSTOM_VALIDATION,
                    validatorTypeIds = listOf(CUSTOM_VALIDATOR),
                    message = "invalid title",
                    sourceAnnotationUseSiteTarget = LsiAnnotationUseSiteTarget.GETTER,
                ),
            ),
        )

        val plan = prop.toDraftValidationPlan(LsiWorkspace())

        assertEquals(prop.id, plan.propId)
        assertNotNull(plan.requiredNullCheck)
        assertEquals(
            listOf(
                ImmutableDraftValidationStep.NotBlank::class,
                ImmutableDraftValidationStep.Size::class,
                ImmutableDraftValidationStep.Size::class,
                ImmutableDraftValidationStep.Pattern::class,
                ImmutableDraftValidationStep.CustomValidator::class,
            ),
            plan.steps.map { step -> step::class },
        )
        val pattern = plan.builtInSteps.filterIsInstance<ImmutableDraftValidationStep.Pattern>().single()
        assertEquals(listOf(ImmutableDraftPatternFlag.CASE_INSENSITIVE), pattern.flags)
        assertEquals(CUSTOM_VALIDATION, plan.customValidatorSteps.single().annotationTypeId)
    }

    @Test
    fun `nullable reference skips required check and built-in failures skip null`() {
        val prop = validationProp(
            nullable = true,
            annotations = listOf(annotation(NOT_BLANK)),
        )

        val plan = prop.toDraftValidationPlan(LsiWorkspace())

        assertEquals(null, plan.requiredNullCheck)
        assertTrue(plan.builtInSteps.single().failure.skipWhenNull)
    }

    @Test
    fun `rejects an impossible size interval`() {
        val prop = validationProp(
            annotations = listOf(
                annotation(
                    SIZE,
                    "min" to LsiAnnotationValue.IntValue(10),
                    "max" to LsiAnnotationValue.IntValue(2),
                ),
            ),
        )

        assertFailsWith<ImmutablePrecompileException> {
            prop.toDraftValidationPlan(LsiWorkspace())
        }
    }
}

private fun annotation(
    type: LsiSymbolId,
    vararg arguments: Pair<String, LsiAnnotationValue>,
): LsiAnnotation {
    return LsiAnnotation(
        type = type,
        arguments = arguments.associate { (name, value) ->
            name to LsiAnnotationArgument(value, LsiAnnotationArgumentOrigin.EXPLICIT)
        },
    )
}

private fun validationProp(
    nullable: Boolean = false,
    annotations: List<LsiAnnotation>,
    validations: List<ImmutableValidation> = emptyList(),
): ImmutableProp {
    val id = LsiSymbolId.property(OWNER, "title")
    return ImmutableProp(
        id = id,
        declarationId = id,
        ownerTypeId = OWNER,
        declaringTypeId = OWNER,
        name = "title",
        documentation = null,
        type = LsiDeclaredType(LsiSymbolId.type("java.lang.String")),
        annotations = annotations,
        overrideChain = emptyList(),
        inherited = false,
        overridden = false,
        nullable = nullable,
        list = false,
        association = false,
        embedded = false,
        targetTypeId = null,
        primaryMapping = PrimaryMapping.SCALAR,
        primaryAnnotationTypeId = null,
        defaultContract = null,
        associationKind = AssociationKind.NONE,
        formulaKind = FormulaKind.NONE,
        mappedBy = null,
        associationStorage = AssociationStorageKind.NONE,
        transientResolver = null,
        view = null,
        genericTarget = false,
        remote = false,
        recursive = false,
        validations = validations,
        converter = null,
    )
}

private val OWNER = LsiSymbolId.type("demo.Book")
private val NOT_BLANK = LsiSymbolId.type("jakarta.validation.constraints.NotBlank")
private val SIZE = LsiSymbolId.type("jakarta.validation.constraints.Size")
private val PATTERN = LsiSymbolId.type("jakarta.validation.constraints.Pattern")
private val PATTERN_FLAG = LsiSymbolId.type("jakarta.validation.constraints.Pattern.Flag")
private val CUSTOM_VALIDATION = LsiSymbolId.type("demo.ValidTitle")
private val CUSTOM_VALIDATOR = LsiSymbolId.type("demo.ValidTitleValidator")
