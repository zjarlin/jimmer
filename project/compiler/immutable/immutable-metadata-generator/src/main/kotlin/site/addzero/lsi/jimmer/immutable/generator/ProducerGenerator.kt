package site.addzero.lsi.jimmer.immutable.generator

import org.babyfish.jimmer.currentVersion
import site.addzero.lsi.codegen.IMMUTABLE_PROP_CATEGORY_LSI_CLASS_NAME
import site.addzero.lsi.codegen.IMMUTABLE_TYPE_LSI_CLASS_NAME
import site.addzero.lsi.codegen.INTERNAL_TYPE_LSI_CLASS_NAME
import site.addzero.lsi.codegen.JacksonTypes
import site.addzero.lsi.codegen.KOTLIN_BOOLEAN_LSI_CLASS_NAME
import site.addzero.lsi.codegen.KOTLIN_INT_LSI_CLASS_NAME
import site.addzero.lsi.codegen.PRODUCER
import site.addzero.lsi.codegen.generatedAnnotation
import site.addzero.lsi.poet.LsiCallableSpec
import site.addzero.lsi.poet.LsiCallableSpecKind
import site.addzero.lsi.poet.LsiCallExpression
import site.addzero.lsi.poet.LsiCastExpression
import site.addzero.lsi.poet.LsiClassName
import site.addzero.lsi.poet.LsiCodeBlock
import site.addzero.lsi.poet.LsiClassLiteralExpression
import site.addzero.lsi.poet.LsiEnumConstantExpression
import site.addzero.lsi.poet.LsiExpression
import site.addzero.lsi.poet.LsiLambdaExpression
import site.addzero.lsi.poet.LsiLambdaMode
import site.addzero.lsi.poet.LsiListExpression
import site.addzero.lsi.poet.LsiLiteralExpression
import site.addzero.lsi.poet.LsiModifier
import site.addzero.lsi.poet.LsiNameExpression
import site.addzero.lsi.poet.LsiNewExpression
import site.addzero.lsi.poet.LsiNullExpression
import site.addzero.lsi.poet.LsiParameterSpec
import site.addzero.lsi.poet.LsiPropertyAccessExpression
import site.addzero.lsi.poet.LsiPropertySpec
import site.addzero.lsi.poet.LsiReturnStatement
import site.addzero.lsi.poet.LsiTypeName
import site.addzero.lsi.poet.LsiTypeExpression
import site.addzero.lsi.poet.LsiTypeSpec
import site.addzero.lsi.poet.LsiTypeSpecKind

internal class ProducerGenerator(
    private val jacksonTypes: JacksonTypes,
    private val type: ImmutableProducerTypeMetadata,
    private val currentVersionValue: String = currentVersion(),
) {

    fun generate(): LsiTypeSpec =
        LsiTypeSpec(
            name = PRODUCER,
            kind = LsiTypeSpecKind.CLASS,
            annotations = listOf(generatedAnnotation(type.className)),
            modifiers = setOf(LsiModifier.PUBLIC, LsiModifier.STATIC),
            properties = buildProperties().map { it.asStaticCarrierProperty() },
            callables = listOf(
                LsiCallableSpec(
                    kind = LsiCallableSpecKind.CONSTRUCTOR,
                    primary = true,
                    modifiers = setOf(LsiModifier.PRIVATE),
                )
            ) + buildCallables().map { it.asStaticCarrierCallable() },
            nestedTypes = buildNestedTypes().map { it.asStaticCarrierNestedType() },
        )

    private fun buildProperties(): List<LsiPropertySpec> = buildList {
        if (!type.isMappedSuperclass) {
            addAll(slotProperties())
        }
        add(typeProperty())
    }

    private fun buildCallables(): List<LsiCallableSpec> =
        if (type.isMappedSuperclass) {
            emptyList()
        } else {
            listOf(produceCallable())
        }

    private fun buildNestedTypes(): List<LsiTypeSpec> =
        if (type.isMappedSuperclass) {
            emptyList()
        } else {
            listOf(
                ImplementorGenerator(
                    jacksonTypes,
                    type.implementorTypeMetadata
                        ?: error("Internal bug: missing implementor metadata for ${type.className}"),
                ).generate(),
                ImplGenerator(
                    jacksonTypes,
                    type.implTypeMetadata
                        ?: error("Internal bug: missing impl metadata for ${type.className}"),
                ).generate(),
                DraftImplGenerator(
                    jacksonTypes,
                    type.draftImplTypeMetadata
                        ?: error("Internal bug: missing draft-impl metadata for ${type.className}"),
                ).generate(),
            )
        }

    private fun slotProperties(): List<LsiPropertySpec> =
        type.slots.map { prop ->
            LsiPropertySpec(
                name = prop.slotName,
                type = KOTLIN_INT_LSI_CLASS_NAME,
                modifiers = setOf(LsiModifier.CONST),
                initializer =
                    if (prop.localId != null) {
                        LsiLiteralExpression(prop.localId)
                    } else {
                        LsiPropertyAccessExpression(
                            receiver =
                                LsiTypeExpression(
                                    prop.inheritedOwnerProducerClassName
                                        ?: error("Internal bug: missing inherited owner for ${prop.slotName}"),
                                ),
                            name = prop.slotName,
                        )
                    },
            )
        }

    private fun typeProperty(): LsiPropertySpec =
        LsiPropertySpec(
            name = "type",
            type = IMMUTABLE_TYPE_LSI_CLASS_NAME,
            initializer = typeInitializer(),
        )

    private fun typeInitializer(): LsiExpression {
        var builder: LsiExpression =
            builderCall(
                receiver = LsiTypeExpression(IMMUTABLE_TYPE_LSI_CLASS_NAME),
                name = "newBuilder",
                arguments = listOf(
                    LsiLiteralExpression(currentVersionValue),
                    LsiClassLiteralExpression(type.className),
                    LsiListExpression(
                        type.superProducerClassNames.map { superProducerClassName ->
                            LsiPropertyAccessExpression(
                                receiver = LsiTypeExpression(superProducerClassName),
                                name = "type",
                            )
                        }
                    ),
                    typeFactoryExpression(),
                ),
            )
        if (!type.isMappedSuperclass) {
            for (prop in type.redefinedProps) {
                builder =
                    builderCall(
                        receiver = builder,
                        name = "redefine",
                        arguments = listOf(
                            LsiLiteralExpression(prop.name),
                            LsiNameExpression(prop.slotName),
                        ),
                    )
            }
        }
        for (prop in type.declaredProps) {
            builder = addProp(builder, prop)
        }
        return builderCall(receiver = builder, name = "build")
    }

    private fun typeFactoryExpression(): LsiExpression =
        if (type.isMappedSuperclass) {
            LsiNullExpression
        } else {
            LsiLambdaExpression(
                mode = LsiLambdaMode.EXPRESSION,
                parameterNames = listOf("ctx", "base"),
                expression =
                    LsiNewExpression(
                        type = type.draftImplClassName,
                        arguments = listOf(
                            LsiNameExpression("ctx"),
                            LsiCastExpression(type.className.copyNullable(true), LsiNameExpression("base")),
                        ),
                    ),
            )
        }

    private fun addProp(
        receiver: LsiExpression,
        prop: ImmutableProducerPropMetadata,
    ): LsiExpression =
        when (prop.kind) {
            ImmutableProducerPropKind.ID ->
                builderCall(
                    receiver = receiver,
                    name = "id",
                    arguments = listOf(
                        propIdExpression(prop),
                        LsiLiteralExpression(prop.name),
                        LsiClassLiteralExpression(prop.targetClassName),
                    ),
                )

            ImmutableProducerPropKind.VERSION ->
                builderCall(
                    receiver = receiver,
                    name = "version",
                    arguments = listOf(
                        propIdExpression(prop),
                        LsiLiteralExpression(prop.name),
                    ),
                )

            ImmutableProducerPropKind.LOGICAL_DELETED ->
                builderCall(
                    receiver = receiver,
                    name = "logicalDeleted",
                    arguments = listOf(
                        propIdExpression(prop),
                        LsiLiteralExpression(prop.name),
                        LsiClassLiteralExpression(prop.targetClassName),
                        LsiLiteralExpression(prop.isNullable),
                    ),
                )

            ImmutableProducerPropKind.KEY_REFERENCE ->
                builderCall(
                    receiver = receiver,
                    name = "keyReference",
                    arguments = listOf(
                        propIdExpression(prop),
                        LsiLiteralExpression(prop.name),
                        LsiClassLiteralExpression(
                            prop.annotationClassName
                                ?: error("Internal bug: missing key-reference annotation for ${prop.name}"),
                        ),
                        LsiClassLiteralExpression(prop.targetClassName),
                        LsiLiteralExpression(prop.isNullable),
                    ),
                )

            ImmutableProducerPropKind.KEY ->
                builderCall(
                    receiver = receiver,
                    name = "key",
                    arguments = listOf(
                        propIdExpression(prop),
                        LsiLiteralExpression(prop.name),
                        LsiClassLiteralExpression(prop.targetClassName),
                        LsiLiteralExpression(prop.isNullable),
                    ),
                )

            ImmutableProducerPropKind.ID_VIEW ->
                builderCall(
                    receiver = receiver,
                    name = "add",
                    arguments = listOf(
                        propIdExpression(prop),
                        LsiLiteralExpression(prop.name),
                        categoryExpression(
                            prop.categoryName
                                ?: error("Internal bug: missing id-view category for ${prop.name}"),
                        ),
                        LsiClassLiteralExpression(prop.targetClassName),
                        LsiLiteralExpression(prop.isNullable),
                    ),
                )

            ImmutableProducerPropKind.SQL ->
                builderCall(
                    receiver = receiver,
                    name = "add",
                    arguments = listOf(
                        propIdExpression(prop),
                        LsiLiteralExpression(prop.name),
                        LsiClassLiteralExpression(
                            prop.annotationClassName
                                ?: error("Internal bug: missing sql annotation for ${prop.name}"),
                        ),
                        LsiClassLiteralExpression(prop.targetClassName),
                        LsiLiteralExpression(prop.isNullable),
                    ),
                )

            ImmutableProducerPropKind.ADD ->
                builderCall(
                    receiver = receiver,
                    name = "add",
                    arguments = listOf(
                        propIdExpression(prop),
                        LsiLiteralExpression(prop.name),
                        categoryExpression(
                            prop.categoryName
                                ?: error("Internal bug: missing prop category for ${prop.name}"),
                        ),
                        LsiClassLiteralExpression(prop.targetClassName),
                        LsiLiteralExpression(prop.isNullable),
                    ),
                )
        }

    private fun propIdExpression(prop: ImmutableProducerPropMetadata): LsiExpression =
        prop.propIdLiteral.toIntOrNull()?.let(::LsiLiteralExpression)
            ?: LsiNameExpression(prop.propIdLiteral)

    private fun categoryExpression(categoryName: String): LsiExpression =
        LsiEnumConstantExpression(
            type = IMMUTABLE_PROP_CATEGORY_LSI_CLASS_NAME,
            constantName = categoryName,
        )

    private fun builderCall(
        receiver: LsiExpression,
        name: String,
        arguments: List<LsiExpression> = emptyList(),
    ): LsiCallExpression =
        LsiCallExpression(
            receiver = receiver,
            name = name,
            arguments = arguments,
        )

    private fun produceCallable(): LsiCallableSpec =
        LsiCallableSpec(
            kind = LsiCallableSpecKind.FUNCTION,
            name = "produce",
            parameters = listOf(
                LsiParameterSpec(
                    name = "base",
                    type = type.className.copyNullable(true),
                    defaultValue = LsiCodeBlock.of("null"),
                ),
                LsiParameterSpec(
                    name = "resolveImmediately",
                    type = KOTLIN_BOOLEAN_LSI_CLASS_NAME,
                    defaultValue = LsiCodeBlock.of("false"),
                ),
                LsiParameterSpec(
                    name = "block",
                    type = type.draftCallbackMetadata.toLsiDraftConsumerTypeName(),
                ),
            ),
            returnType = type.className,
            statements = listOf(
                LsiReturnStatement(
                    LsiCastExpression(
                        type = type.className,
                        expression =
                            LsiCallExpression(
                                receiver = LsiTypeExpression(INTERNAL_TYPE_LSI_CLASS_NAME),
                                name = "produce",
                                arguments = listOf(
                                    LsiNameExpression("type"),
                                    LsiNameExpression("base"),
                                    LsiNameExpression("resolveImmediately"),
                                    LsiNameExpression("block"),
                                ),
                            ),
                    ),
                ),
            ),
            modifiers = emptySet(),
        )
}

private fun LsiPropertySpec.asStaticCarrierProperty(): LsiPropertySpec =
    copy(modifiers = modifiers + setOf(LsiModifier.PUBLIC, LsiModifier.STATIC))

private fun LsiCallableSpec.asStaticCarrierCallable(): LsiCallableSpec =
    copy(modifiers = modifiers + setOf(LsiModifier.PUBLIC, LsiModifier.STATIC))

private fun LsiTypeSpec.asStaticCarrierNestedType(): LsiTypeSpec =
    when (kind) {
        LsiTypeSpecKind.CLASS,
        LsiTypeSpecKind.ENUM -> copy(modifiers = modifiers + setOf(LsiModifier.PUBLIC, LsiModifier.STATIC))
        else -> copy(modifiers = modifiers + LsiModifier.PUBLIC)
    }
