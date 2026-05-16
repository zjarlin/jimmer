package site.addzero.lsi.jimmer.transactional.metadata.generator

import site.addzero.lsi.jimmer.transactional.metadata.model.TxAnnotationMetadata
import site.addzero.lsi.jimmer.transactional.metadata.model.TxAnnotationValueMetadata
import site.addzero.lsi.jimmer.transactional.metadata.model.TxConstructorMetadata
import site.addzero.lsi.jimmer.transactional.metadata.model.TxMethodMetadata
import site.addzero.lsi.jimmer.transactional.metadata.model.TxParameterMetadata
import site.addzero.lsi.jimmer.transactional.metadata.model.TxTypeMetadata
import site.addzero.lsi.jimmer.transactional.metadata.model.TxTypeRefMetadata
import site.addzero.lsi.poet.LsiAnnotationSpec
import site.addzero.lsi.poet.LsiAnnotationUseSiteTarget
import site.addzero.lsi.poet.LsiAnnotationValue
import site.addzero.lsi.poet.LsiArrayAnnotationValue
import site.addzero.lsi.poet.LsiCallExpression
import site.addzero.lsi.poet.LsiCallableSpec
import site.addzero.lsi.poet.LsiCallableSpecKind
import site.addzero.lsi.poet.LsiCharAnnotationValue
import site.addzero.lsi.poet.LsiClassAnnotationValue
import site.addzero.lsi.poet.LsiClassName
import site.addzero.lsi.poet.LsiConstructorDelegateCall
import site.addzero.lsi.poet.LsiConstructorDelegateKind
import site.addzero.lsi.poet.LsiEnumAnnotationValue
import site.addzero.lsi.poet.LsiEnumConstantExpression
import site.addzero.lsi.poet.LsiExpressionStatement
import site.addzero.lsi.poet.LsiFileSpec
import site.addzero.lsi.poet.LsiLambdaExpression
import site.addzero.lsi.poet.LsiLambdaMode
import site.addzero.lsi.poet.LsiLiteralAnnotationValue
import site.addzero.lsi.poet.LsiModifier
import site.addzero.lsi.poet.LsiNameExpression
import site.addzero.lsi.poet.LsiNestedAnnotationValue
import site.addzero.lsi.poet.LsiNullAnnotationValue
import site.addzero.lsi.poet.LsiParameterSpec
import site.addzero.lsi.poet.LsiPropertyAccessExpression
import site.addzero.lsi.poet.LsiReturnStatement
import site.addzero.lsi.poet.LsiStringAnnotationValue
import site.addzero.lsi.poet.LsiThisExpression
import site.addzero.lsi.poet.LsiTypeAnnotationValue
import site.addzero.lsi.poet.LsiTypeName
import site.addzero.lsi.poet.LsiTypeSpec
import site.addzero.lsi.poet.LsiTypeSpecKind
import site.addzero.lsi.poet.LsiArrayTypeName
import site.addzero.lsi.poet.LsiParameterizedTypeName
import site.addzero.lsi.poet.LsiTypeVariableName
import site.addzero.lsi.poet.isLsiVoidLikeQualifiedName
import site.addzero.lsi.poet.normalizedLsiCarrierQualifiedName

/**
 * Tx metadata -> LsiPoet 中间态生成器。
 *
 * 迁移说明：
 * - 不再直接依赖 KotlinPoet
 * - APT/KSP 共享同一份中间态生成逻辑
 * - 后端落地分别由 `lsi-ksp` / `lsi-apt` 负责渲染
 */
class TxMetadataGenerator {

    fun generate(
        metadata: TxTypeMetadata,
    ): LsiFileSpec =
        LsiFileSpec(
            packageName = metadata.packageName,
            name = metadata.generatedSimpleName,
            annotations = listOf(suppressWarningsAnnotation()),
            types = listOf(metadata.toTypeSpec()),
        )

    private fun TxTypeMetadata.toTypeSpec(): LsiTypeSpec =
        LsiTypeSpec(
            name = generatedSimpleName,
            kind = LsiTypeSpecKind.CLASS,
            annotations = copiedAnnotations.map { it.toLsiPoet() } +
                listOfNotNull(
                    targetAnnotationTypeQualifiedName?.let { targetAnnotation ->
                        LsiAnnotationSpec(type = LsiClassName.bestGuess(targetAnnotation))
                    }
                ),
            modifiers = buildSet {
                if (isInternal) {
                    add(LsiModifier.INTERNAL)
                }
                if (isAbstract) {
                    add(LsiModifier.ABSTRACT)
                }
            },
            superTypes = listOf(LsiClassName.bestGuess(superTypeQualifiedName)),
            callables = buildList {
                primaryConstructor?.let { add(it.toCallableSpec(primary = true)) }
                secondaryConstructors.forEach { add(it.toCallableSpec(primary = false)) }
                methods.forEach { add(it.toCallableSpec(sqlClientPropertyName)) }
            },
            originatingClassName = LsiClassName.bestGuess(sourceQualifiedName),
        )

    private fun TxConstructorMetadata.toCallableSpec(
        primary: Boolean,
    ): LsiCallableSpec =
        LsiCallableSpec(
            kind = LsiCallableSpecKind.CONSTRUCTOR,
            primary = primary,
            annotations = annotations.map { it.toLsiPoet() },
            modifiers = buildSet {
                if (isProtected) {
                    add(LsiModifier.PROTECTED)
                }
                if (isInternal) {
                    add(LsiModifier.INTERNAL)
                }
            },
            parameters = parameters.map { it.toParameterSpec() },
            delegateCall = LsiConstructorDelegateCall(
                kind = LsiConstructorDelegateKind.SUPER,
                arguments = parameters.map { LsiNameExpression(it.name) },
            ),
        )

    private fun TxMethodMetadata.toCallableSpec(
        sqlClientPropertyName: String,
    ): LsiCallableSpec {
        val returnTypeName = returnType?.takeUnless { it.isVoidLike() }?.toLsiTypeName()
        val superCall = LsiCallExpression(
            receiver = site.addzero.lsi.poet.LsiSuperExpression,
            name = name,
            arguments = parameters.map { LsiNameExpression(it.name) },
        )
        val transactionCall = LsiCallExpression(
            receiver = LsiPropertyAccessExpression(LsiThisExpression, sqlClientPropertyName),
            name = "transaction",
            arguments = listOf(
                LsiEnumConstantExpression(PROPAGATION_CLASS_NAME, propagation),
                if (returnTypeName != null) {
                    LsiLambdaExpression(
                        mode = LsiLambdaMode.EXPRESSION,
                        expression = superCall,
                    )
                } else {
                    LsiLambdaExpression(
                        mode = LsiLambdaMode.UNIT,
                        statements = listOf(
                            LsiExpressionStatement(superCall)
                        ),
                    )
                },
            ),
        )
        return LsiCallableSpec(
            kind = LsiCallableSpecKind.FUNCTION,
            name = name,
            annotations = annotations.map { it.toLsiPoet() },
            modifiers = buildSet {
                add(LsiModifier.OVERRIDE)
                if (isProtected) {
                    add(LsiModifier.PROTECTED)
                } else if (isInternal) {
                    add(LsiModifier.INTERNAL)
                }
            },
            parameters = parameters.map { it.toParameterSpec() },
            returnType = returnTypeName,
            thrownTypes = thrownTypes.map { it.toLsiTypeName() },
            statements = listOf(
                if (returnTypeName != null) {
                    LsiReturnStatement(transactionCall)
                } else {
                    LsiExpressionStatement(transactionCall)
                }
            ),
        )
    }

    private fun TxParameterMetadata.toParameterSpec(): LsiParameterSpec =
        LsiParameterSpec(
            name = name,
            type = type.toLsiTypeName(),
        )

    private fun TxAnnotationMetadata.toLsiPoet(): LsiAnnotationSpec =
        LsiAnnotationSpec(
            type = LsiClassName.bestGuess(qualifiedName),
            members = LinkedHashMap<String, LsiAnnotationValue>().apply {
                arguments.forEach { argument ->
                    put(argument.name, argument.value.toLsiPoet())
                }
            },
        )

    private fun TxAnnotationValueMetadata.toLsiPoet(): LsiAnnotationValue =
        when (this) {
            TxAnnotationValueMetadata.NullValue -> LsiNullAnnotationValue
            is TxAnnotationValueMetadata.StringValue -> LsiStringAnnotationValue(value)
            is TxAnnotationValueMetadata.BooleanValue -> LsiLiteralAnnotationValue(value)
            is TxAnnotationValueMetadata.NumberValue -> LsiLiteralAnnotationValue(value)
            is TxAnnotationValueMetadata.CharValue -> LsiCharAnnotationValue(value)
            is TxAnnotationValueMetadata.EnumValue -> LsiEnumAnnotationValue(
                enumType = LsiClassName.bestGuess(typeQualifiedName),
                constantName = entryName,
            )
            is TxAnnotationValueMetadata.ClassValue -> LsiClassAnnotationValue(type.toLsiClassName())
            is TxAnnotationValueMetadata.TypeValue -> LsiTypeAnnotationValue(type.toLsiTypeName())
            is TxAnnotationValueMetadata.AnnotationValue -> LsiNestedAnnotationValue(annotation.toLsiPoet())
            is TxAnnotationValueMetadata.ListValue -> LsiArrayAnnotationValue(values.map { it.toLsiPoet() })
        }

    private fun TxTypeRefMetadata.toLsiClassName(): LsiClassName =
        when {
            isVoidLike() -> LsiClassName.bestGuess("kotlin.Unit")
            else -> LsiClassName.bestGuess(normalizedQualifiedName())
        }

    private fun TxTypeRefMetadata.toLsiTypeName(): LsiTypeName {
        if (array) {
            return LsiArrayTypeName(
                componentType = componentType?.toLsiTypeName()
                    ?: error("Array type must define componentType"),
                nullable = nullable,
            )
        }
        val normalizedName = normalizedQualifiedName()
        val baseType = if (normalizedName.contains('.')) {
            LsiClassName.bestGuess(normalizedName)
        } else {
            LsiTypeVariableName(
                name = normalizedName,
                nullable = nullable,
            )
        }
        if (baseType is LsiClassName && typeArguments.isNotEmpty()) {
            return LsiParameterizedTypeName(
                rawType = baseType,
                typeArguments = typeArguments.map { it.toLsiTypeName() },
                nullable = nullable,
            )
        }
        return baseType.copyNullable(nullable)
    }

    private fun TxTypeRefMetadata.normalizedQualifiedName(): String {
        val rawName = qualifiedName ?: presentableText ?: simpleName
        require(!rawName.isNullOrBlank()) {
            "TxTypeRefMetadata must define qualifiedName, presentableText or simpleName"
        }
        return rawName
            .substringBefore('<')
            .removeSuffix("?")
            .normalizedLsiCarrierQualifiedName()
    }

    private fun TxTypeRefMetadata.isVoidLike(): Boolean =
        normalizedQualifiedName().isLsiVoidLikeQualifiedName()

    private fun suppressWarningsAnnotation(): LsiAnnotationSpec =
        LsiAnnotationSpec(
            type = LsiClassName.bestGuess("kotlin.Suppress"),
            positionalArguments = listOf(LsiStringAnnotationValue("warnings")),
            useSiteTarget = LsiAnnotationUseSiteTarget.FILE,
        )

    companion object {
        private val PROPAGATION_CLASS_NAME =
            LsiClassName.bestGuess("org.babyfish.jimmer.sql.transaction.Propagation")
    }
}
