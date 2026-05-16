package site.addzero.lsi.jimmer.immutable.generator

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import site.addzero.lsi.anno.LsiAnnotation
import site.addzero.lsi.codegen.LsiClassName
import site.addzero.lsi.codegen.validatorFieldName
import site.addzero.lsi.clazz.LsiClass
import site.addzero.lsi.field.LsiField
import site.addzero.lsi.jimmer.immutable.metadata.model.ImmutableValidationPropMetadata
import site.addzero.lsi.poet.LsiBinaryExpression
import site.addzero.lsi.poet.LsiBinaryOperator
import site.addzero.lsi.poet.LsiCallExpression
import site.addzero.lsi.poet.LsiExpressionStatement
import site.addzero.lsi.poet.LsiIfStatement
import site.addzero.lsi.poet.LsiNameExpression
import site.addzero.lsi.poet.LsiNewExpression
import site.addzero.lsi.poet.LsiNullExpression
import site.addzero.lsi.poet.LsiParameterizedTypeName
import site.addzero.lsi.poet.LsiThrowStatement
import site.addzero.lsi.poet.LsiTypeName
import site.addzero.lsi.type.LsiType

class ValidationGeneratorStructureTest {

    @Test
    fun `nullable not blank validation is emitted as guarded throw statement`() {
        val statements = ValidationGenerator(nullableStringProp(notBlankAnnotation())).generate()

        assertEquals(1, statements.size)

        val validationIf = statements.single() as LsiIfStatement
        val condition = validationIf.condition as LsiBinaryExpression
        assertEquals(LsiBinaryOperator.AND, condition.operator)
        assertEquals(
            LsiBinaryExpression(
                left = LsiNameExpression("name"),
                operator = LsiBinaryOperator.NOT_EQUALS,
                right = LsiNullExpression,
            ),
            condition.left,
        )
        assertFalse(condition.right is site.addzero.lsi.poet.LsiCodeExpression)
        val isEmptyCall = condition.right as LsiCallExpression
        assertEquals("isEmpty", isEmptyCall.name)
        assertTrue(isEmptyCall.arguments.isEmpty())
        val trimCall = isEmptyCall.receiver as LsiCallExpression
        assertEquals("trim", trimCall.name)
        assertEquals(LsiNameExpression("name"), trimCall.receiver)
        assertTrue(trimCall.arguments.isEmpty())

        val throwStatement = validationIf.thenStatements.single() as LsiThrowStatement
        val exception = throwStatement.expression as LsiNewExpression
        assertEquals("jakarta.validation.ValidationException", exception.type.canonicalName)

        val message = exception.arguments.single() as LsiBinaryExpression
        assertEquals(LsiBinaryOperator.PLUS, message.operator)
    }

    @Test
    fun `constraint validator invocation is emitted as lsi call statement`() {
        val validatorType = ImmutableGeneratorTestFixtures.className("test.validation.BookNameValidator")
        val statements =
            ValidationGenerator(
                validationProp(
                    validationMessages = mapOf(validatorType to "validator message"),
                )
            ).generate()

        assertEquals(1, statements.size)

        val expression = (statements.single() as LsiExpressionStatement).expression as LsiCallExpression
        assertEquals(LsiNameExpression(validatorFieldName("name", validatorType)), expression.receiver)
        assertEquals("validate", expression.name)
        assertEquals(listOf(LsiNameExpression("name")), expression.arguments)
    }

    private fun nullableStringProp(vararg annotations: LsiAnnotation): ImmutableValidationPropMetadata =
        validationProp(
            validationAnnotationMirrorMultiMap = annotations
                .groupBy { it.simpleName ?: error("Annotation simpleName is required") },
            isNullable = true,
            lsiTypeName = ImmutableGeneratorTestFixtures.className("kotlin.String").copyNullable(true),
            nonNullLsiTypeName = ImmutableGeneratorTestFixtures.className("kotlin.String"),
        )

    private fun validationProp(
        validationMessages: Map<LsiClassName, String> = emptyMap(),
        validationAnnotationMirrorMultiMap: Map<String, List<LsiAnnotation>> = emptyMap(),
        isNullable: Boolean = false,
        lsiTypeName: LsiTypeName = ImmutableGeneratorTestFixtures.className("kotlin.String"),
        nonNullLsiTypeName: LsiTypeName = ImmutableGeneratorTestFixtures.className("kotlin.String"),
    ): ImmutableValidationPropMetadata =
        ImmutableValidationPropMetadata(
            name = "name",
            slotName = "NAME",
            lsiField = TestLsiField(),
            validationMessages = validationMessages,
            validationAnnotationMirrorMultiMap = validationAnnotationMirrorMultiMap,
            lsiTypeName = lsiTypeName,
            nonNullLsiTypeName = nonNullLsiTypeName,
            description = "Book.name",
            isNullable = isNullable,
        )

    private fun notBlankAnnotation(): LsiAnnotation =
        TestLsiAnnotation(
            qualifiedName = "jakarta.validation.constraints.NotBlank",
            simpleName = "NotBlank",
            attributes =
                mapOf(
                    "message" to "{jakarta.validation.constraints.NotBlank.message}",
                ),
        )

    private data class TestLsiAnnotation(
        override val qualifiedName: String?,
        override val simpleName: String?,
        override val attributes: Map<String, Any?>,
    ) : LsiAnnotation {
        override fun getAttribute(name: String): Any? = attributes[name]

        override fun hasAttribute(name: String): Boolean = attributes.containsKey(name)
    }

    private class TestLsiField : LsiField {
        override val name: String? = "name"
        override val type: LsiType? = null
        override val typeName: String? = "kotlin.String"
        override val comment: String? = null
        override val annotations: List<LsiAnnotation> = emptyList()
        override val isStatic: Boolean = false
        override val isConstant: Boolean = false
        override val isEnum: Boolean = false
        override val isVar: Boolean = true
        override val isLateInit: Boolean = false
        override val isCollectionType: Boolean = false
        override val defaultValue: String? = null
        override val columnName: String? = null
        override val declaringClass: LsiClass? = null
        override val fieldTypeClass: LsiClass? = null
        override val isNestedObject: Boolean = false
        override val children: List<LsiField> = emptyList()
    }
}
