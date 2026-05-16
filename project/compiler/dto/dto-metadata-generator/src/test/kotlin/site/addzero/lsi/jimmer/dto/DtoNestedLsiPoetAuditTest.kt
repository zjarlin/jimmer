package site.addzero.lsi.jimmer.dto

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files

class DtoNestedLsiPoetAuditTest {

    @Test
    fun `input builder generator emits lsi type spec`() {
        val source = DtoTestSupport.readSource(
            "project/compiler/dto/dto-metadata-generator/src/main/kotlin/site/addzero/lsi/jimmer/dto/InputBuilderGenerator.kt"
        )
        assertTrue(source.contains("fun generate(): LsiTypeSpec"))
        assertTrue(source.contains("private fun field(prop: LsiDtoAbstractPropView): LsiPropertySpec"))
        assertTrue(source.contains("private fun setter(prop: LsiDtoAbstractPropView): LsiCallableSpec"))
        assertTrue(source.contains("for (anno in prop.tailFieldAnnotations)"))
        assertFalse(source.contains("import com.squareup.kotlinpoet"))
        assertFalse(source.contains("private fun field(prop: AbstractProp): LsiPropertySpec"))
        assertFalse(source.contains("private fun setter(prop: AbstractProp): LsiCallableSpec"))
        assertFalse(source.contains("prop.rawAbstractProp"))
        assertFalse(source.contains("prop.rawDtoProp.toTailProp().baseProp.lsiField.annotations"))
        assertFalse(source.contains("LsiCodeBlock.of("))
        assertFalse(source.contains("buildString {"))
        assertFalse(source.contains("rawCodeBlock("))
        assertTrue(source.contains("LsiNewExpression("))
        assertTrue(source.contains("LsiIfStatement("))
        assertTrue(source.contains("LsiThrowStatement("))
    }

    @Test
    fun `serializer generator emits lsi type spec`() {
        val source = DtoTestSupport.readSource(
            "project/compiler/dto/dto-metadata-generator/src/main/kotlin/site/addzero/lsi/jimmer/dto/SerializerGenerator.kt"
        )
        assertTrue(source.contains("fun generate(): LsiTypeSpec"))
        assertTrue(source.contains("private fun inputPropertyExpression(prop: LsiDtoPropView)"))
        assertFalse(source.contains("prop.rawDtoProp"))
        assertFalse(source.contains("import com.squareup.kotlinpoet"))
        assertFalse(source.contains("LsiCodeBlock.of("))
        assertFalse(source.contains("buildString {"))
    }

    @Test
    fun `legacy kotlinpoet compat is removed`() {
        assertFalse(
            DtoTestSupport.locateSource(
                "project/compiler/dto/dto-metadata-generator/src/main/kotlin/site/addzero/lsi/jimmer/dto/LegacyKotlinPoetCompat.kt"
            )?.let(Files::exists) ?: false
        )
    }

    @Test
    fun `dto generator keeps top level output on lsi file spec`() {
        val source = DtoTestSupport.readSource(
            "project/compiler/dto/dto-metadata-generator/src/main/kotlin/site/addzero/lsi/jimmer/dto/DtoGenerator.kt"
        )
        assertTrue(source.contains("private fun propLsiTypeName(prop: LsiDtoPropView): LsiTypeName"))
        assertTrue(source.contains("private fun propElementLsiTypeName(prop: LsiDtoPropView): LsiTypeName"))
        assertTrue(source.contains("private fun mutableBackingPropName(prop: LsiDtoAbstractPropView): String"))
        assertTrue(source.contains("fun lsiTypeName(typeRef: TypeRef?): LsiTypeName"))
        assertTrue(source.contains("private fun LsiTypeName.isArrayType(): Boolean"))
        assertFalse(source.contains("metadata.targetTypeName.toKotlinPoet()"))
        assertFalse(source.contains("standardSpec("))
        assertFalse(source.contains("toLegacyUseSiteTarget("))
        assertFalse(source.contains("private fun TypeName.isArray(): Boolean"))
        assertFalse(source.contains("toKotlinPoet("))
        assertFalse(source.contains("FileSpec.builder("))
        assertFalse(source.contains("FunSpec.builder("))
        assertFalse(source.contains("PropertySpec.builder("))
        assertFalse(source.contains("import com.squareup.kotlinpoet"))
        assertFalse(source.contains("reflectionName("))
        assertFalse(source.contains("_typeBuilder"))
        assertFalse(source.contains("TypeSpec.classBuilder("))
        assertFalse(source.contains("createSourceFile("))
        assertFalse(source.contains("MemberName(\""))
        assertFalse(source.contains("CodeBlock.builder("))
        assertFalse(source.contains("rawCodeExpression(code: CodeBlock)"))
        assertFalse(source.contains("private fun CodeBlock.Builder.metadataFetcherExpr()"))
        assertFalse(source.contains("private fun CodeBlock.Builder.addFetcherField("))
        assertFalse(source.contains("private fun CodeBlock.Builder.addConfigLambda("))
        assertFalse(source.contains("private fun CodeBlock.Builder.addPredicate("))
        assertFalse(source.contains("private fun CodeBlock.Builder.addPropPath("))
        assertFalse(source.contains("private fun CodeBlock.Builder.addValueToEnum("))
        assertFalse(source.contains("private fun CodeBlock.Builder.addConverterLoading("))
        assertTrue(source.contains("fun generate(): LsiFileSpec?"))
        assertTrue(source.contains("LsiFileSpec("))
        assertTrue(source.contains("topLevelCallables = buildTopLevelCallables()"))
        assertTrue(source.contains("private fun buildTopLevelCallables(): List<LsiCallableSpec>"))
        assertTrue(source.contains("private fun buildToEntitiesCallable(): LsiCallableSpec"))
        assertTrue(source.contains("private fun buildToEntitiesExCallable(): LsiCallableSpec"))
        assertTrue(source.contains("LsiClassLiteralExpression(dtoType.baseType.lsiClassName)"))
        assertTrue(source.contains("LsiCallableReferenceExpression("))
        assertTrue(source.contains("receiver = LsiNameExpression(\"block\")"))
        assertTrue(source.contains("private fun buildHashCodeStatements(): List<LsiStatement>"))
        assertFalse(source.contains("private fun buildHashCodeBody(): String"))
        assertFalse(source.contains("rawCodeExpression(buildHashCodeBody())"))
        assertTrue(source.contains("private fun buildEqualsStatements(): List<LsiStatement>"))
        assertFalse(source.contains("private fun buildEqualsBody(): String"))
        assertFalse(source.contains("rawCodeExpression(buildEqualsBody())"))
        assertTrue(source.contains("private fun buildToStringStatements(): List<LsiStatement>"))
        assertFalse(source.contains("private fun buildToStringBody(): String"))
        assertFalse(source.contains("rawCodeExpression(buildToStringBody())"))
        assertFalse(source.contains("%L(%T::class).by(null, false, this@%L::%L)"))
        assertTrue(source.contains("private fun buildApplyToStatements(): List<LsiStatement>"))
        assertTrue(source.contains("LsiArrayExpression("))
        assertTrue(source.contains("private fun ImmutableProp.unwrapExpression(): LsiExpression"))
        assertFalse(source.contains("private fun buildApplyToCode(): String"))
        assertTrue(source.contains("private fun buildMetadataFetcherExpression(): LsiExpression"))
        assertTrue(source.contains("private fun buildMetadataFetcherStatements(): List<LsiStatement>"))
        assertTrue(source.contains("private fun buildFetcherFieldStatement(\n        prop: LsiDtoPropView,"))
        assertTrue(source.contains("private fun buildHiddenFetcherFieldStatement(\n        prop: LsiDtoPropView,"))
        assertTrue(source.contains("private fun buildFilterStatements(cfg: LsiDtoPropConfigView): List<LsiStatement>"))
        assertTrue(source.contains("private fun buildPredicateExpression(predicate: LsiDtoPredicateView): LsiExpression"))
        assertTrue(source.contains("private fun propPathExpression(pathNodes: List<LsiDtoPathNodeView>): LsiExpression"))
        assertFalse(source.contains("private fun buildMetadataFetcherCode(): String"))
        assertTrue(source.contains("private fun buildConverterArgumentExpressions("))
        assertTrue(source.contains("private fun buildConverterValueExpression("))
        assertTrue(source.contains("private fun buildConverterLoadedExpression("))
        assertTrue(source.contains("private fun buildAccessorInitializerExpression("))
        assertTrue(source.contains("private fun buildDraftAssignmentStatement(\n        prop: LsiDtoPropView,"))
        assertTrue(source.contains("private fun buildPredicateOperationStatements(\n        prop: LsiDtoPropView"))
        assertTrue(source.contains("private fun buildAccessorSlotArrayExpression("))
        assertTrue(source.contains("LsiIntArrayExpression("))
        assertTrue(source.contains("private fun defaultValueCodeBlock(prop: LsiUserPropView): LsiCodeBlock? ="))
        assertTrue(source.contains("private fun defaultValueExpression(prop: LsiUserPropView): LsiExpression? {"))
        assertTrue(source.contains("private fun explicitDefaultValueExpression("))
        assertTrue(source.contains("private fun parseDtoStringLiteral(text: String): String {"))
        assertTrue(source.contains("private fun buildConverterLoadingExpression("))
        assertTrue(source.contains("private fun enumToValueHelperName("))
        assertTrue(source.contains("private fun valueToEnumHelperName("))
        assertFalse(source.contains("private fun buildConverterArgument(prop: DtoProp<ImmutableType, ImmutableProp>): String"))
        assertFalse(source.contains("private fun buildAccessorInitializerCode("))
        assertTrue(source.contains("private fun buildSpecificationConverterStatements("))
        assertFalse(source.contains("private fun buildSpecificationConverterCode("))
        assertTrue(source.contains("private fun buildHibernateValidatorEnhancementStatements(getter: Boolean): List<LsiStatement>"))
        assertFalse(source.contains("private fun buildHibernateValidatorEnhancementCode("))
        assertTrue(source.contains("LsiWhenStatement("))
        assertTrue(source.contains("LsiThrowStatement("))
        assertFalse(source.contains("private fun buildValueToEnumCode("))
        assertFalse(source.contains("private fun buildConverterLoadingCode("))
        assertFalse(source.contains("private class RawCodeBuilder"))
        assertFalse(source.contains("rawCodeExpression("))
        assertFalse(source.contains("rawCodeBlock("))
        assertFalse(source.contains("private fun defaultValueCodeBlock(prop: UserProp): LsiCodeBlock? ="))
        assertFalse(source.contains("private fun defaultValueExpression(prop: UserProp): LsiExpression? {"))
        assertFalse(source.contains("private fun defaultValue(prop: UserProp): String? {"))
        assertFalse(source.contains("buildString {"))
        assertFalse(source.contains("private fun buildFilterStatements(cfg: PropConfig<ImmutableProp>): List<LsiStatement>"))
        assertFalse(source.contains("private fun buildPredicateExpression(predicate: Predicate): LsiExpression"))
        assertFalse(source.contains("private fun propPathExpression(pathNodes: List<PathNode<ImmutableProp>>): LsiExpression"))
        assertFalse(source.contains("private fun buildFetcherFieldStatement(\n        prop: DtoProp<ImmutableType, ImmutableProp>,"))
        assertFalse(source.contains("private fun buildHiddenFetcherFieldStatement(\n        prop: DtoProp<ImmutableType, ImmutableProp>,"))
        assertFalse(source.contains("private fun buildDraftAssignmentStatement(\n        prop: DtoProp<ImmutableType, ImmutableProp>,"))
        assertFalse(source.contains("private fun buildPredicateOperationStatements(\n        prop: DtoProp<ImmutableType, ImmutableProp>"))
    }
}
