package site.addzero.lsi.jimmer.tuple.metadata.generator

import site.addzero.lsi.codegen.LsiClassName
import site.addzero.lsi.jimmer.tuple.metadata.model.TypedTupleConstructionMetadata
import site.addzero.lsi.jimmer.tuple.metadata.model.TypedTupleConstructorConstructionMetadata
import site.addzero.lsi.jimmer.tuple.metadata.model.TypedTupleMetadata
import site.addzero.lsi.jimmer.tuple.metadata.model.TypedTuplePropertyMetadata
import site.addzero.lsi.jimmer.tuple.metadata.model.TypedTupleSetterConstructionMetadata
import site.addzero.lsi.jimmer.tuple.metadata.model.TypedTupleTypeRefMetadata
import site.addzero.lsi.poet.LsiArrayOfNullsExpression
import site.addzero.lsi.poet.LsiArrayTypeName
import site.addzero.lsi.poet.LsiAssignmentStatement
import site.addzero.lsi.poet.LsiCallExpression
import site.addzero.lsi.poet.LsiCallableSpec
import site.addzero.lsi.poet.LsiCallableSpecKind
import site.addzero.lsi.poet.LsiCastExpression
import site.addzero.lsi.poet.LsiClassName as PoetClassName
import site.addzero.lsi.poet.LsiExpression
import site.addzero.lsi.poet.LsiExpressionStatement
import site.addzero.lsi.poet.LsiFileSpec
import site.addzero.lsi.poet.LsiIndexAccessExpression
import site.addzero.lsi.poet.LsiLiteralExpression
import site.addzero.lsi.poet.LsiModifier
import site.addzero.lsi.poet.LsiNameExpression
import site.addzero.lsi.poet.LsiNewExpression
import site.addzero.lsi.poet.LsiParameterSpec
import site.addzero.lsi.poet.LsiParameterizedTypeName
import site.addzero.lsi.poet.LsiPropertyAccessExpression
import site.addzero.lsi.poet.LsiPropertySpec
import site.addzero.lsi.poet.LsiReturnStatement
import site.addzero.lsi.poet.LsiStarTypeName
import site.addzero.lsi.poet.LsiStatement
import site.addzero.lsi.poet.LsiTypeExpression
import site.addzero.lsi.poet.LsiTypeName
import site.addzero.lsi.poet.LsiTypeSpec
import site.addzero.lsi.poet.LsiTypeSpecKind
import site.addzero.lsi.poet.LsiTypeVariableName
import site.addzero.lsi.poet.LsiVarargExpression
import site.addzero.lsi.poet.LsiVariableDeclarationStatement
import site.addzero.lsi.poet.normalizedLsiCarrierQualifiedName

/**
 * TypedTuple metadata -> LsiPoet 中间态生成器。
 *
 * 目标：
 * - 共享 tuple 生成逻辑
 * - 由 `lsi-ksp` / `lsi-apt` 分别把同一份 LsiPoet 落地到 KotlinPoet / JavaPoet
 */
class TypedTupleMetadataGenerator {

    fun generate(
        metadata: TypedTupleMetadata,
    ): LsiFileSpec =
        LsiFileSpec(
            packageName = metadata.packageName,
            name = metadata.generatedSimpleName,
            types = listOf(metadata.toTypeSpec()),
        )

    private fun TypedTupleMetadata.toTypeSpec(): LsiTypeSpec =
        LsiTypeSpec(
            name = generatedSimpleName,
            kind = LsiTypeSpecKind.CLASS,
            modifiers = setOf(LsiModifier.PUBLIC),
            superInterfaces = listOf(
                LsiParameterizedTypeName(
                    rawType = TUPLE_MAPPER_CLASS_NAME,
                    typeArguments = listOf(sourceClassName),
                )
            ),
            properties = listOf(selectionsProperty()),
            callables = listOf(
                constructor(),
                getSelectionsCallable(),
                createTupleCallable(this),
                firstSelectionCallable(this),
            ),
            nestedTypes = properties.drop(1).mapIndexed { offset, property ->
                builderType(
                    metadata = this,
                    index = offset + 1,
                    property = property,
                )
            },
            originatingClassName = sourceClassName,
        )

    private fun selectionsProperty(): LsiPropertySpec =
        LsiPropertySpec(
            name = SELECTIONS_FIELD_NAME,
            type = NULLABLE_SELECTION_ARRAY_TYPE,
            modifiers = setOf(LsiModifier.PRIVATE, LsiModifier.LATEINIT),
            mutable = true,
        )

    private fun constructor(): LsiCallableSpec =
        LsiCallableSpec(
            kind = LsiCallableSpecKind.CONSTRUCTOR,
            modifiers = setOf(LsiModifier.PRIVATE),
            parameters = listOf(selectionsParameter()),
            statements = listOf(assignSelectionsStatement()),
        )

    private fun getSelectionsCallable(): LsiCallableSpec =
        LsiCallableSpec(
            kind = LsiCallableSpecKind.FUNCTION,
            name = "getSelections",
            modifiers = setOf(LsiModifier.PUBLIC, LsiModifier.OVERRIDE),
            returnType = LsiParameterizedTypeName(
                rawType = LIST_CLASS_NAME,
                typeArguments = listOf(SELECTION_STAR_TYPE),
            ),
            statements = listOf(
                LsiVariableDeclarationStatement(
                    name = "selectionItems",
                    type = NON_NULL_SELECTION_ARRAY_TYPE,
                    initializer = LsiCastExpression(
                        type = NON_NULL_SELECTION_ARRAY_TYPE,
                        expression = LsiNameExpression(SELECTIONS_FIELD_NAME),
                    ),
                ),
                LsiReturnStatement(
                    LsiCallExpression(
                        receiver = LsiTypeExpression(COLLECTIONS_CLASS_NAME),
                        name = "unmodifiableList",
                        arguments = listOf(
                            LsiCallExpression(
                                receiver = LsiTypeExpression(ARRAYS_CLASS_NAME),
                                name = "asList",
                                arguments = listOf(
                                    LsiVarargExpression(LsiNameExpression("selectionItems"))
                                ),
                            )
                        ),
                    )
                ),
            ),
        )

    private fun createTupleCallable(
        metadata: TypedTupleMetadata,
    ): LsiCallableSpec =
        LsiCallableSpec(
            kind = LsiCallableSpecKind.FUNCTION,
            name = "createTuple",
            modifiers = setOf(LsiModifier.PUBLIC, LsiModifier.OVERRIDE),
            parameters = listOf(
                LsiParameterSpec(
                    name = "args",
                    type = LsiArrayTypeName(ANY_CLASS_NAME.copyNullable(true)),
                )
            ),
            returnType = metadata.sourceClassName,
            statements = metadata.createTupleStatements(),
        )

    private fun TypedTupleMetadata.createTupleStatements(): List<LsiStatement> =
        when (val construction = construction) {
            is TypedTupleConstructorConstructionMetadata ->
                listOf(
                    LsiReturnStatement(
                        LsiNewExpression(
                            type = sourceClassName,
                            arguments = construction.argumentPropertyIndices.map { propertyIndex ->
                                propertyArgumentExpression(
                                    property = properties[propertyIndex],
                                    argumentIndex = propertyIndex,
                                )
                            },
                        )
                    )
                )
            is TypedTupleSetterConstructionMetadata ->
                buildList {
                    add(
                        LsiVariableDeclarationStatement(
                            name = "__tuple",
                            type = sourceClassName,
                            initializer = LsiNewExpression(sourceClassName),
                        )
                    )
                    properties.forEachIndexed { index, property ->
                        add(
                            LsiExpressionStatement(
                                LsiCallExpression(
                                    receiver = LsiNameExpression("__tuple"),
                                    name = construction.setterNames[index],
                                    arguments = listOf(
                                        propertyArgumentExpression(
                                            property = property,
                                            argumentIndex = index,
                                        )
                                    ),
                                )
                            )
                        )
                    }
                    add(LsiReturnStatement(LsiNameExpression("__tuple")))
                }
        }

    private fun firstSelectionCallable(
        metadata: TypedTupleMetadata,
    ): LsiCallableSpec {
        val firstProperty = metadata.properties.first()
        return LsiCallableSpec(
            kind = LsiCallableSpecKind.FUNCTION,
            name = firstProperty.name,
            modifiers = setOf(LsiModifier.PUBLIC, LsiModifier.STATIC),
            parameters = listOf(selectionParameter(firstProperty)),
            returnType = buildReturnTypeName(metadata, 0),
            statements = listOf(
                LsiVariableDeclarationStatement(
                    name = "selections",
                    type = NULLABLE_SELECTION_ARRAY_TYPE,
                    initializer = LsiArrayOfNullsExpression(
                        elementType = SELECTION_STAR_TYPE,
                        size = LsiLiteralExpression(metadata.properties.size),
                    ),
                ),
                selectionsAssignment(index = 0, expression = LsiNameExpression("selection")),
                LsiReturnStatement(
                    LsiNewExpression(
                        type = buildReturnTypeName(metadata, 0),
                        arguments = listOf(LsiNameExpression("selections")),
                    )
                ),
            ),
        )
    }

    private fun builderType(
        metadata: TypedTupleMetadata,
        index: Int,
        property: TypedTuplePropertyMetadata,
    ): LsiTypeSpec =
        LsiTypeSpec(
            name = builderTypeName(property.name),
            kind = LsiTypeSpecKind.CLASS,
            modifiers = setOf(LsiModifier.PUBLIC, LsiModifier.STATIC),
            properties = listOf(selectionsProperty()),
            callables = listOf(
                LsiCallableSpec(
                    kind = LsiCallableSpecKind.CONSTRUCTOR,
                    modifiers = setOf(LsiModifier.INTERNAL),
                    parameters = listOf(selectionsParameter()),
                    statements = listOf(assignSelectionsStatement()),
                ),
                LsiCallableSpec(
                    kind = LsiCallableSpecKind.FUNCTION,
                    name = property.name,
                    modifiers = setOf(LsiModifier.PUBLIC),
                    parameters = listOf(selectionParameter(property)),
                    returnType = buildReturnTypeName(metadata, index),
                    statements = listOf(
                        selectionsAssignment(index = index, expression = LsiNameExpression("selection")),
                        LsiReturnStatement(
                            LsiNewExpression(
                                type = buildReturnTypeName(metadata, index),
                                arguments = listOf(LsiNameExpression(SELECTIONS_FIELD_NAME)),
                            )
                        ),
                    ),
                ),
            ),
        )

    private fun buildReturnTypeName(
        metadata: TypedTupleMetadata,
        index: Int,
    ): PoetClassName =
        if (index + 1 < metadata.properties.size) {
            metadata.generatedClassName.nested(builderTypeName(metadata.properties[index + 1].name))
        } else {
            metadata.generatedClassName
        }

    private fun selectionsParameter(): LsiParameterSpec =
        LsiParameterSpec(
            name = SELECTIONS_FIELD_NAME,
            type = NULLABLE_SELECTION_ARRAY_TYPE,
        )

    private fun assignSelectionsStatement(): LsiStatement =
        LsiAssignmentStatement(
            target = LsiPropertyAccessExpression(LsiNameExpression("this"), SELECTIONS_FIELD_NAME),
            expression = LsiNameExpression(SELECTIONS_FIELD_NAME),
        )

    private fun selectionParameter(
        property: TypedTuplePropertyMetadata,
    ): LsiParameterSpec =
        LsiParameterSpec(
            name = "selection",
            type = LsiParameterizedTypeName(
                rawType = SELECTION_CLASS_NAME,
                typeArguments = listOf(property.type.safeToLsiTypeName()),
            ),
        )

    private fun selectionsAssignment(
        index: Int,
        expression: LsiExpression,
    ): LsiStatement =
        LsiAssignmentStatement(
            target = LsiIndexAccessExpression(
                receiver = LsiNameExpression(SELECTIONS_FIELD_NAME),
                index = LsiLiteralExpression(index),
            ),
            expression = expression,
        )

    private fun propertyArgumentExpression(
        property: TypedTuplePropertyMetadata,
        argumentIndex: Int,
    ): LsiExpression =
        LsiCastExpression(
            type = property.type.safeToLsiTypeName(),
            expression = LsiIndexAccessExpression(
                receiver = LsiNameExpression("args"),
                index = LsiLiteralExpression(argumentIndex),
            ),
        )

    private fun TypedTupleTypeRefMetadata?.safeToLsiTypeName(): LsiTypeName {
        val type = this ?: return ANY_CLASS_NAME.copyNullable(true)
        return runCatching { type.toLsiTypeName() }
            .getOrElse {
                val fallbackName = type.normalizedQualifiedName()
                    ?: type.presentableText
                        ?.substringBefore('<')
                        ?.removeSuffix("?")
                        ?.removeSuffix("!")
                    ?: "kotlin.Any"
                when {
                    fallbackName == "*" -> LsiStarTypeName
                    fallbackName.contains('.') -> PoetClassName.bestGuess(fallbackName, nullable = type.nullable)
                    else -> LsiTypeVariableName(name = fallbackName, nullable = type.nullable)
                }
            }
    }

    private fun TypedTupleTypeRefMetadata.toLsiTypeName(): LsiTypeName =
        when {
            array && componentType != null -> {
                val componentTypeMetadata = componentType
                    ?: error("componentType must not be null when array is true")
                LsiArrayTypeName(
                    componentType = componentTypeMetadata.toLsiTypeName(),
                    nullable = nullable,
                )
            }
            else -> nonArrayTypeName().copyNullable(nullable)
        }

    private fun TypedTupleTypeRefMetadata.nonArrayTypeName(): LsiTypeName {
        val normalizedQualifiedName = normalizedQualifiedName()
        val normalizedSimpleName = normalizedSimpleName()
        primitiveTypeName(normalizedQualifiedName ?: normalizedSimpleName)?.let { return it }
        if (normalizedSimpleName == "*") {
            return LsiStarTypeName
        }
        val rawType = when {
            normalizedQualifiedName != null -> PoetClassName.bestGuess(normalizedQualifiedName)
            normalizedSimpleName != null && normalizedSimpleName.contains('.') ->
                PoetClassName.bestGuess(normalizedSimpleName)
            normalizedSimpleName != null -> LsiTypeVariableName(normalizedSimpleName)
            else -> ANY_CLASS_NAME.copyNullable(true)
        }
        if (rawType is PoetClassName && typeArguments.isNotEmpty()) {
            return LsiParameterizedTypeName(
                rawType = rawType,
                typeArguments = typeArguments.map { argument ->
                    if (argument.normalizedSimpleName() == "*" && argument.qualifiedName == null) {
                        LsiStarTypeName
                    } else {
                        argument.toLsiTypeName()
                    }
                },
            )
        }
        return rawType
    }

    private fun TypedTupleTypeRefMetadata.normalizedQualifiedName(): String? =
        qualifiedName
            ?.substringBefore('<')
            ?.removeSuffix("?")
            ?.removeSuffix("!")
            ?.normalizedLsiCarrierQualifiedName()

    private fun TypedTupleTypeRefMetadata.normalizedSimpleName(): String? =
        simpleName
            ?.removeSuffix("?")
            ?.removeSuffix("!")
            ?.normalizedLsiCarrierQualifiedName()

    private fun primitiveTypeName(name: String?): LsiTypeName? =
        when (val normalizedName = name?.normalizedLsiCarrierQualifiedName()) {
            "kotlin.Boolean",
            "kotlin.Byte",
            "kotlin.Short",
            "kotlin.Int",
            "kotlin.Long",
            "kotlin.Float",
            "kotlin.Double",
            "kotlin.Char",
            "kotlin.String",
            "kotlin.Any" -> PoetClassName.bestGuess(normalizedName)
            else -> null
        }

    private fun builderTypeName(propertyName: String): String =
        typeName(propertyName, "Builder")

    private fun typeName(vararg parts: String): String {
        val builder = StringBuilder()
        var previousPartEndsWithLower = true
        for (part in parts) {
            if (part.isEmpty()) {
                continue
            }
            if (previousPartEndsWithLower) {
                if (part[0].isUpperCase()) {
                    builder.append(part)
                } else {
                    builder.append(part[0].uppercaseChar()).append(part.substring(1))
                }
            } else {
                if (part[0].isLowerCase()) {
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

    companion object {
        private const val SELECTIONS_FIELD_NAME = "selections"

        private val TUPLE_MAPPER_CLASS_NAME =
            PoetClassName.bestGuess("org.babyfish.jimmer.sql.runtime.TupleMapper")
        private val SELECTION_CLASS_NAME =
            PoetClassName.bestGuess("org.babyfish.jimmer.sql.ast.Selection")
        private val COLLECTIONS_CLASS_NAME =
            PoetClassName.bestGuess("java.util.Collections")
        private val ARRAYS_CLASS_NAME =
            PoetClassName.bestGuess("java.util.Arrays")
        private val LIST_CLASS_NAME =
            PoetClassName.bestGuess("java.util.List")
        private val ANY_CLASS_NAME =
            PoetClassName.bestGuess("kotlin.Any")

        private val SELECTION_STAR_TYPE =
            LsiParameterizedTypeName(
                rawType = SELECTION_CLASS_NAME,
                typeArguments = listOf(LsiStarTypeName),
            )

        private val NULLABLE_SELECTION_ARRAY_TYPE =
            LsiArrayTypeName(
                componentType = SELECTION_STAR_TYPE.copy(nullable = true),
            )

        private val NON_NULL_SELECTION_ARRAY_TYPE =
            LsiArrayTypeName(
                componentType = SELECTION_STAR_TYPE,
            )
    }
}
