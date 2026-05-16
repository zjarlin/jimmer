package site.addzero.lsi.jimmer.dto

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DtoGeneratorResidualAuditTest {

    @Test
    fun `dto generator raw code usage is limited to approved hotspots`() {
        val source = DtoTestSupport.readSource(
            "src/main/kotlin/site/addzero/lsi/jimmer/dto/DtoGenerator.kt",
            "project/compiler/dto/dto-metadata-generator/src/main/kotlin/site/addzero/lsi/jimmer/dto/DtoGenerator.kt",
        )
        val rawCodeExpressionCallCount = source
            .lineSequence()
            .map(String::trim)
            .count {
                it.contains("rawCodeExpression(") &&
                    !it.startsWith("private fun rawCodeExpression(")
            }
        val rawCodeBlockCallCount = source
            .lineSequence()
            .map(String::trim)
            .count {
                it.contains("rawCodeBlock(") &&
                    !it.startsWith("private fun rawCodeBlock(")
            }
        val buildStringCount = source
            .lineSequence()
            .count { it.contains("buildString {") }

        assertEquals(
            0,
            rawCodeExpressionCallCount,
            "DTO generator business path should no longer fall back to rawCodeExpression",
        )
        assertEquals(
            0,
            buildStringCount,
            "DTO generator should no longer keep ad-hoc buildString raw helpers",
        )
        assertEquals(
            0,
            rawCodeBlockCallCount,
            "DTO generator should no longer fall back to rawCodeBlock for default values",
        )
        assertTrue(
            source.contains("private fun buildMetadataFetcherExpression(): LsiExpression ="),
            "Metadata fetcher should now be rendered by structured LsiPoet expressions",
        )
        assertFalse(
            source.contains("private fun buildMetadataFetcherCode(): String ="),
            "Residual metadata fetcher raw builder should be removed after migration",
        )
        assertTrue(
            source.contains("private fun buildConverterArgumentExpressions("),
            "Converter constructor arguments should now be rendered by structured LsiPoet expressions",
        )
        assertFalse(
            source.contains("private fun buildConverterArgument(prop: DtoProp<ImmutableType, ImmutableProp>): String ="),
            "Residual converter argument raw builder should be removed after migration",
        )
        assertTrue(
            source.contains("private fun buildApplyToStatements(): List<LsiStatement> ="),
            "DTO applyTo should now be rendered by structured LsiPoet statements",
        )
        assertTrue(
            source.contains("private fun buildSpecificationConverterStatements("),
            "Specification converter should now be rendered by structured LsiPoet statements",
        )
        assertFalse(
            source.contains("private fun buildSpecificationConverterCode("),
            "Residual specification converter raw builder should be removed after migration",
        )
        assertTrue(
            source.contains("private fun buildAccessorInitializerExpression("),
            "Accessor initializer should now be rendered by structured LsiPoet expressions",
        )
        assertTrue(
            source.contains("private fun buildAccessorSlotArrayExpression("),
            "Accessor slot arrays should now be rendered by a dedicated Lsi int-array expression",
        )
        assertTrue(
            source.contains("LsiIntArrayExpression("),
            "Accessor slot arrays should use LsiIntArrayExpression instead of raw array literals",
        )
        assertTrue(
            source.contains("private fun buildConverterValueExpression("),
            "Converter value extraction should now be rendered by structured LsiPoet expressions",
        )
        assertTrue(
            source.contains("private fun buildConverterLoadedExpression("),
            "Converter loaded-state extraction should now be rendered by structured LsiPoet expressions",
        )
        assertTrue(
            source.contains("private fun defaultValueCodeBlock(prop:") &&
                source.contains("): LsiCodeBlock? ="),
            "DTO user property defaults should now be rendered through structured LsiPoet code blocks",
        )
        assertTrue(
            source.contains("private fun defaultValueExpression(prop:") &&
                source.contains("): LsiExpression? {"),
            "DTO user property defaults should now be derived from structured LsiPoet expressions",
        )
        assertTrue(
            source.contains("private fun explicitDefaultValueExpression("),
            "Explicit DTO default literals should now be parsed into LsiPoet expressions",
        )
        assertTrue(
            source.contains("private fun parseDtoStringLiteral(text: String): String {"),
            "DTO string default literals should now be decoded before entering LsiPoet",
        )
        assertTrue(
            source.contains("private fun buildConverterLoadingExpression("),
            "Converter loading should now stay on the structured expression path",
        )
        assertTrue(
            source.contains("private fun enumToValueHelperName("),
            "Enum conversion helper names should now be derived on the structured path",
        )
        assertTrue(
            source.contains("private fun valueToEnumHelperName("),
            "Enum reverse conversion helper names should now be derived on the structured path",
        )
        assertTrue(
            source.contains("private fun buildFilterStatements(cfg:") &&
                source.contains("): List<LsiStatement> ="),
            "Metadata fetcher filter body should now be rendered by structured LsiPoet statements",
        )
        assertTrue(
            source.contains("private fun buildPredicateExpression(predicate:") &&
                source.contains("): LsiExpression ="),
            "Metadata fetcher predicate should now be rendered by structured LsiPoet expressions",
        )
        assertTrue(
            source.contains("private fun buildHibernateValidatorEnhancementStatements(getter: Boolean): List<LsiStatement> ="),
            "Hibernate validator enhancement should now be rendered by structured LsiPoet statements",
        )
        assertFalse(
            source.contains("private fun buildHibernateValidatorEnhancementCode("),
            "Residual hibernate validator raw builder should be removed after migration",
        )
        assertFalse(
            source.contains("private fun buildAccessorInitializerCode("),
            "Residual accessor initializer raw builder should be removed after migration",
        )
        assertFalse(
            source.contains("private fun buildLambdaArgument("),
            "Residual lambda raw builder should be removed after migration",
        )
        assertFalse(
            source.contains("private fun buildConverterLoadingCode("),
            "Residual converter loading raw builder should be removed after migration",
        )
        assertFalse(
            source.contains("private fun defaultValue(prop: UserProp): String? {"),
            "String-based default value helper should be removed after migrating user defaults to LsiPoet",
        )
        assertFalse(
            source.contains("private fun rawCodeBlock(code: String): LsiCodeBlock ="),
            "rawCodeBlock helper should be removed once user defaults are migrated to structured LsiPoet",
        )
        assertFalse(
            source.contains("private fun String.quoted(): String ="),
            "Quoted helper should be removed once raw string helpers are no longer needed",
        )
    }

}
