package site.addzero.lsi.jimmer.error.metadata.generator

import site.addzero.lsi.codegen.generatedAnnotation
import site.addzero.lsi.jimmer.CODE_BASED_EXCEPTION
import site.addzero.lsi.jimmer.CODE_BASED_RUNTIME_EXCEPTION
import site.addzero.lsi.jimmer.error.metadata.model.ErrorFieldMetadata
import site.addzero.lsi.jimmer.error.metadata.model.ErrorItemMetadata
import site.addzero.lsi.jimmer.error.metadata.model.ErrorTypeMetadata
import site.addzero.lsi.poet.LsiAnnotationSpec
import site.addzero.lsi.poet.LsiAnnotationUseSiteTarget
import site.addzero.lsi.poet.LsiArrayAnnotationValue
import site.addzero.lsi.poet.LsiCallExpression
import site.addzero.lsi.poet.LsiCallableSpec
import site.addzero.lsi.poet.LsiCallableSpecKind
import site.addzero.lsi.poet.LsiClassAnnotationValue
import site.addzero.lsi.poet.LsiClassName
import site.addzero.lsi.poet.LsiCodeBlock
import site.addzero.lsi.poet.LsiConstructorDelegateCall
import site.addzero.lsi.poet.LsiConstructorDelegateKind
import site.addzero.lsi.poet.LsiEnumConstantExpression
import site.addzero.lsi.poet.LsiExpressionStatement
import site.addzero.lsi.poet.LsiFileSpec
import site.addzero.lsi.poet.LsiLiteralExpression
import site.addzero.lsi.poet.LsiModifier
import site.addzero.lsi.poet.LsiNameExpression
import site.addzero.lsi.poet.LsiNewExpression
import site.addzero.lsi.poet.LsiParameterSpec
import site.addzero.lsi.poet.LsiParameterizedTypeName
import site.addzero.lsi.poet.LsiPropertySpec
import site.addzero.lsi.poet.LsiReturnStatement
import site.addzero.lsi.poet.LsiStatement
import site.addzero.lsi.poet.LsiStringAnnotationValue
import site.addzero.lsi.poet.LsiTypeExpression
import site.addzero.lsi.poet.LsiTypeName
import site.addzero.lsi.poet.LsiTypeSpec
import site.addzero.lsi.poet.LsiTypeSpecKind
import site.addzero.lsi.poet.LsiVariableDeclarationStatement

/**
 * Error metadata -> LsiPoet 中间态生成器。
 *
 * 迁移说明：
 * - compiler 主链路统一输出 `LsiFileSpec`
 * - Kotlin/Java 渲染收敛到 `lsi-ksp` / `lsi-apt` adapter
 * - 共享生成器只表达中间态，不直接暴露 KotlinPoet/JavaPoet
 */
class ErrorMetadataGenerator {

    fun generate(
        metadata: ErrorTypeMetadata,
        checkedException: Boolean,
    ): LsiFileSpec {
        val enumClassName = LsiClassName.bestGuess(metadata.enumQualifiedName)
        return LsiFileSpec(
            packageName = metadata.packageName,
            name = metadata.exceptionSimpleName,
            types = listOf(metadata.toTypeSpec(enumClassName, checkedException)),
        )
    }

    private fun ErrorTypeMetadata.toTypeSpec(
        enumClassName: LsiClassName,
        checkedException: Boolean,
    ): LsiTypeSpec {
        val exceptionClassName = LsiClassName.bestGuess(exceptionQualifiedName)
        return LsiTypeSpec(
            name = exceptionSimpleName,
            kind = LsiTypeSpecKind.CLASS,
            annotations = listOf(
                generatedAnnotation(enumClassName),
                clientExceptionAnnotation(
                    family = family,
                    subTypes = items.map { exceptionClassName.nested(it.exceptionSimpleName) },
                ),
            ),
            modifiers = setOf(LsiModifier.PUBLIC, LsiModifier.ABSTRACT),
            superClass = LsiClassName.bestGuess(
                if (checkedException) CODE_BASED_EXCEPTION else CODE_BASED_RUNTIME_EXCEPTION
            ),
            properties = declaredFieldProperties(declaredFields) + listOf(
                enumProperty(
                    enumSimpleName = enumSimpleName,
                    enumClassName = enumClassName,
                    abstract = true,
                ),
                fieldsProperty(
                    fields = declaredFields,
                    override = true,
                ),
            ),
            callables = listOf(constructor(declaredFields)) + items.map {
                creatorCallable(
                    item = it,
                    exceptionClassName = exceptionClassName,
                    sharedFields = declaredFields,
                )
            },
            nestedTypes = items.map { item ->
                item.toTypeSpec(
                    metadata = this,
                    enumClassName = enumClassName,
                    exceptionClassName = exceptionClassName,
                )
            },
            originatingClassName = enumClassName,
        )
    }

    private fun ErrorItemMetadata.toTypeSpec(
        metadata: ErrorTypeMetadata,
        enumClassName: LsiClassName,
        exceptionClassName: LsiClassName,
    ): LsiTypeSpec {
        val allFields = metadata.declaredFields + declaredFields
        return LsiTypeSpec(
            name = exceptionSimpleName,
            kind = LsiTypeSpecKind.CLASS,
            annotations = listOf(
                clientExceptionAnnotation(
                    family = metadata.family,
                    code = code,
                )
            ),
            modifiers = setOf(LsiModifier.PUBLIC, LsiModifier.STATIC),
            superClass = exceptionClassName,
            properties = declaredFieldProperties(declaredFields) + listOf(
                enumProperty(
                    enumSimpleName = metadata.enumSimpleName,
                    enumClassName = enumClassName,
                    abstract = false,
                    enumConstantName = enumConstantName,
                ),
                fieldsProperty(
                    fields = allFields,
                    override = true,
                ),
            ),
            callables = listOf(
                constructor(
                    fields = allFields,
                    superArguments = metadata.declaredFields.map { field ->
                        LsiNameExpression(field.name)
                    },
                )
            ),
            originatingClassName = enumClassName,
        )
    }

    private fun constructor(
        fields: List<ErrorFieldMetadata>,
        superArguments: List<LsiNameExpression> = emptyList(),
    ): LsiCallableSpec =
        LsiCallableSpec(
            kind = LsiCallableSpecKind.CONSTRUCTOR,
            primary = true,
            modifiers = setOf(LsiModifier.PUBLIC),
            parameters = listOf(
                messageParameter(),
                causeParameter(),
            ) + fields.map { it.toParameterSpec() },
            delegateCall = LsiConstructorDelegateCall(
                kind = LsiConstructorDelegateKind.SUPER,
                arguments = listOf(
                    LsiNameExpression("message"),
                    LsiNameExpression("cause"),
                ) + superArguments,
            ),
        )

    private fun declaredFieldProperties(
        fields: List<ErrorFieldMetadata>,
    ): List<LsiPropertySpec> =
        fields.map { field ->
            LsiPropertySpec(
                name = field.name,
                type = field.toTypeName(),
                modifiers = setOf(LsiModifier.PUBLIC),
                initializer = LsiNameExpression(field.name),
            )
        }

    private fun enumProperty(
        enumSimpleName: String,
        enumClassName: LsiClassName,
        abstract: Boolean,
        enumConstantName: String? = null,
    ): LsiPropertySpec =
        LsiPropertySpec(
            name = identifier(enumSimpleName),
            type = enumClassName,
            annotations = listOf(jsonIgnoreAnnotation()),
            modifiers = buildSet {
                add(LsiModifier.PUBLIC)
                if (abstract) {
                    add(LsiModifier.ABSTRACT)
                } else {
                    add(LsiModifier.OVERRIDE)
                }
            },
            getterStatements =
                if (enumConstantName == null) {
                    emptyList()
                } else {
                    listOf(
                        LsiReturnStatement(
                            LsiEnumConstantExpression(enumClassName, enumConstantName)
                        )
                    )
                },
        )

    private fun fieldsProperty(
        fields: List<ErrorFieldMetadata>,
        override: Boolean,
    ): LsiPropertySpec =
        LsiPropertySpec(
            name = "fields",
            type = FIELDS_MAP_TYPE,
            modifiers = buildSet {
                add(LsiModifier.PUBLIC)
                if (override) {
                    add(LsiModifier.OVERRIDE)
                }
            },
            getterStatements = fieldsGetterStatements(fields),
        )

    private fun creatorCallable(
        item: ErrorItemMetadata,
        exceptionClassName: LsiClassName,
        sharedFields: List<ErrorFieldMetadata>,
    ): LsiCallableSpec {
        val allFields = sharedFields + item.declaredFields
        val nestedClassName = exceptionClassName.nested(item.exceptionSimpleName)
        return LsiCallableSpec(
            kind = LsiCallableSpecKind.FUNCTION,
            name = ktName(item.enumConstantName, upperHead = false),
            annotations = listOf(jvmStaticAnnotation()),
            modifiers = setOf(LsiModifier.PUBLIC, LsiModifier.STATIC),
            parameters = listOf(
                messageParameter(),
                causeParameter(),
            ) + allFields.map { it.toParameterSpec() },
            returnType = nestedClassName,
            statements = listOf(
                LsiReturnStatement(
                    LsiNewExpression(
                        type = nestedClassName,
                        arguments = listOf(
                            LsiNameExpression("message"),
                            LsiNameExpression("cause"),
                        ) + allFields.map { field ->
                            LsiNameExpression(field.name)
                        },
                    )
                )
            ),
        )
    }

    private fun ErrorFieldMetadata.toParameterSpec(): LsiParameterSpec =
        LsiParameterSpec(
            name = name,
            type = toTypeName(),
            defaultValue = if (nullable) LsiCodeBlock.of("null") else null,
        )

    private fun ErrorFieldMetadata.toTypeName(): LsiTypeName {
        val baseType = LsiClassName.bestGuess(typeName).copyNullable(nullable)
        return if (list) {
            LsiParameterizedTypeName(
                rawType = LIST_CLASS_NAME,
                typeArguments = listOf(baseType),
            )
        } else {
            baseType
        }
    }

    private fun fieldsGetterStatements(
        fields: List<ErrorFieldMetadata>,
    ): List<LsiStatement> =
        when (fields.size) {
            0 -> listOf(
                LsiReturnStatement(
                    LsiCallExpression(
                        receiver = LsiTypeExpression(COLLECTIONS_CLASS_NAME),
                        name = "emptyMap",
                    )
                )
            )

            1 -> listOf(
                LsiReturnStatement(
                    LsiCallExpression(
                        receiver = LsiTypeExpression(COLLECTIONS_CLASS_NAME),
                        name = "singletonMap",
                        arguments = listOf(
                            LsiLiteralExpression(fields[0].name),
                            LsiNameExpression(fields[0].name),
                        ),
                    )
                )
            )

            else -> buildList {
                add(
                    LsiVariableDeclarationStatement(
                        name = "__fields",
                        type = LINKED_HASH_MAP_TYPE,
                        initializer = LsiNewExpression(LINKED_HASH_MAP_CLASS_NAME),
                    )
                )
                fields.forEach { field ->
                    add(
                        LsiExpressionStatement(
                            LsiCallExpression(
                                receiver = LsiNameExpression("__fields"),
                                name = "put",
                                arguments = listOf(
                                    LsiLiteralExpression(field.name),
                                    LsiNameExpression(field.name),
                                ),
                            )
                        )
                    )
                }
                add(LsiReturnStatement(LsiNameExpression("__fields")))
            }
        }

    private fun clientExceptionAnnotation(
        family: String,
        code: String? = null,
        subTypes: List<LsiClassName> = emptyList(),
    ): LsiAnnotationSpec =
        LsiAnnotationSpec(
            type = CLIENT_EXCEPTION_CLASS_NAME,
            members = linkedMapOf<String, site.addzero.lsi.poet.LsiAnnotationValue>().apply {
                put("family", LsiStringAnnotationValue(family))
                if (code != null) {
                    put("code", LsiStringAnnotationValue(code))
                }
                if (subTypes.isNotEmpty()) {
                    put(
                        "subTypes",
                        LsiArrayAnnotationValue(
                            subTypes.map { subType ->
                                LsiClassAnnotationValue(subType)
                            }
                        )
                    )
                }
            },
        )

    private fun jsonIgnoreAnnotation(): LsiAnnotationSpec =
        LsiAnnotationSpec(
            type = JSON_IGNORE_CLASS_NAME,
            useSiteTarget = LsiAnnotationUseSiteTarget.GET,
        )

    private fun jvmStaticAnnotation(): LsiAnnotationSpec =
        LsiAnnotationSpec(type = JVM_STATIC_CLASS_NAME)

    private fun messageParameter(): LsiParameterSpec =
        LsiParameterSpec(
            name = "message",
            type = KOTLIN_STRING_NULLABLE,
            defaultValue = LsiCodeBlock.of("null"),
        )

    private fun causeParameter(): LsiParameterSpec =
        LsiParameterSpec(
            name = "cause",
            type = THROWABLE_NULLABLE,
            defaultValue = LsiCodeBlock.of("null"),
        )

    companion object {
        private val CLIENT_EXCEPTION_CLASS_NAME = LsiClassName.bestGuess("org.babyfish.jimmer.ClientException")
        private val JSON_IGNORE_CLASS_NAME = LsiClassName.bestGuess("com.fasterxml.jackson.annotation.JsonIgnore")
        private val JVM_STATIC_CLASS_NAME = LsiClassName.bestGuess("kotlin.jvm.JvmStatic")
        private val COLLECTIONS_CLASS_NAME = LsiClassName.bestGuess("java.util.Collections")
        private val LINKED_HASH_MAP_CLASS_NAME = LsiClassName.bestGuess("java.util.LinkedHashMap")
        private val LIST_CLASS_NAME = LsiClassName.bestGuess("java.util.List")
        private val KOTLIN_STRING_TYPE = LsiClassName.bestGuess("kotlin.String")
        private val KOTLIN_STRING_NULLABLE = KOTLIN_STRING_TYPE.copyNullable(true)
        private val THROWABLE_NULLABLE = LsiClassName.bestGuess("java.lang.Throwable", nullable = true)
        private val NULLABLE_ANY_TYPE = LsiClassName.bestGuess("kotlin.Any", nullable = true)
        private val FIELDS_MAP_TYPE = LsiParameterizedTypeName(
            rawType = LsiClassName.bestGuess("java.util.Map"),
            typeArguments = listOf(KOTLIN_STRING_TYPE, NULLABLE_ANY_TYPE),
        )
        private val LINKED_HASH_MAP_TYPE = LsiParameterizedTypeName(
            rawType = LINKED_HASH_MAP_CLASS_NAME,
            typeArguments = listOf(KOTLIN_STRING_TYPE, NULLABLE_ANY_TYPE),
        )

        private fun ktName(simpleName: String, upperHead: Boolean): String {
            var toUpper = upperHead
            return buildString {
                for (char in simpleName) {
                    toUpper = if (char == '_') {
                        true
                    } else {
                        append(
                            if (toUpper) {
                                char.uppercaseChar()
                            } else {
                                char.lowercaseChar()
                            }
                        )
                        false
                    }
                }
            }
        }

        private fun identifier(vararg parts: String): String {
            val builder = StringBuilder()
            var previousPartEndsWithLower = false
            for (part in parts) {
                if (part.isEmpty()) {
                    continue
                }
                if (previousPartEndsWithLower) {
                    if (part.first().isUpperCase()) {
                        builder.append(part)
                    } else {
                        builder.append(part.first().uppercaseChar()).append(part.substring(1))
                    }
                } else {
                    if (part.first().isLowerCase()) {
                        builder.append(part)
                    } else {
                        val chars = part.toCharArray()
                        for (index in chars.indices) {
                            if (chars[index].isLowerCase()) {
                                break
                            }
                            chars[index] = chars[index].lowercaseChar()
                        }
                        builder.append(chars)
                    }
                }
                previousPartEndsWithLower = part.last().isLowerCase()
            }
            return builder.toString()
        }
    }
}
