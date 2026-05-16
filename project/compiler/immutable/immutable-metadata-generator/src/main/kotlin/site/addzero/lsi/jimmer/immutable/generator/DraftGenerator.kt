package site.addzero.lsi.jimmer.immutable.generator

import site.addzero.lsi.codegen.DRAFT
import site.addzero.lsi.codegen.DRAFT_CLASS_LSI_CLASS_NAME
import site.addzero.lsi.codegen.DSL_SCOPE_LSI_CLASS_NAME
import site.addzero.lsi.codegen.JacksonTypes
import site.addzero.lsi.codegen.KOTLIN_BOOLEAN_LSI_CLASS_NAME as BOOLEAN_LSI_CLASS_NAME
import site.addzero.lsi.codegen.PRODUCER
import site.addzero.lsi.codegen.generatedAnnotation
import site.addzero.lsi.codegen.suppressAllAnnotation
import site.addzero.lsi.jimmer.immutable.metadata.model.ImmutableAssociatedIdMetadata
import site.addzero.lsi.jimmer.immutable.metadata.model.ImmutableCallbackMetadata
import site.addzero.lsi.jimmer.immutable.metadata.generator.immutableSourceFileSpecs
import site.addzero.lsi.poet.LsiAnnotationSpec
import site.addzero.lsi.poet.LsiCallableSpec
import site.addzero.lsi.poet.LsiCallableSpecKind
import site.addzero.lsi.poet.LsiCallExpression
import site.addzero.lsi.poet.LsiCastExpression
import site.addzero.lsi.poet.LsiCodeBlock
import site.addzero.lsi.poet.LsiExpressionStatement
import site.addzero.lsi.poet.LsiFileSpec
import site.addzero.lsi.poet.LsiLambdaExpression
import site.addzero.lsi.poet.LsiLambdaMode
import site.addzero.lsi.poet.LsiModifier
import site.addzero.lsi.poet.LsiNameExpression
import site.addzero.lsi.poet.LsiParameterSpec
import site.addzero.lsi.poet.LsiPropertyAccessExpression
import site.addzero.lsi.poet.LsiPropertySpec
import site.addzero.lsi.poet.LsiReturnStatement
import site.addzero.lsi.poet.LsiThisExpression
import site.addzero.lsi.poet.LsiTypeName
import site.addzero.lsi.poet.LsiTypeExpression
import site.addzero.lsi.poet.LsiTypeSpec
import site.addzero.lsi.poet.LsiTypeSpecKind
import site.addzero.lsi.poet.LsiVariableDeclarationStatement

class DraftGenerator(
    private val jacksonTypes: JacksonTypes,
    private val sourcePackageName: String,
    private val sourceFileName: String,
    private val modelTypes: List<ImmutableDraftTypeMetadata>,
    private val currentVersionValue: String = org.babyfish.jimmer.currentVersion(),
) {
    fun generate(mode: ImmutableGenerationMode): List<LsiFileSpec> =
        immutableSourceFileSpecs(
            coreFileSpec = coreFileSpec(),
            generationMode = mode,
        ) {
            val topLevelCallables = buildTopLevelCallables()
            if (topLevelCallables.isEmpty()) {
                null
            } else {
                dslFileSpec(topLevelCallables)
            }
        }

    private fun coreFileSpec(): LsiFileSpec {
        val draftFileName = "${sourceFileName}$DRAFT"
        return LsiFileSpec(
            packageName = sourcePackageName,
            name = draftFileName,
            annotations = listOf(suppressAllAnnotation()),
            types = modelTypes.map(::draftType),
        )
    }

    private fun dslFileSpec(topLevelCallables: List<LsiCallableSpec>): LsiFileSpec {
        val draftFileName = "${sourceFileName}$DRAFT"
        return LsiFileSpec(
            packageName = sourcePackageName,
            name = "${draftFileName}Dsl",
            annotations = listOf(suppressAllAnnotation()),
            topLevelCallables = topLevelCallables,
        )
    }

    private fun buildTopLevelCallables(): List<LsiCallableSpec> = buildList {
        for (type in modelTypes) {
            type.producerTypeMetadata
                .takeUnless { it.isMappedSuperclass }
                ?.let { add(produceCallable(it)) }
            type.declaredProps.forEach { prop ->
                prop.refBlockMetadata?.let { add(refBlockCallable(type, prop.name, it)) }
            }
            type.newFunMetadatas.forEach { add(newByCallable(it)) }
            type.addFunMetadatas.forEach { add(addByCallable(it)) }
            type.copyFunMetadata?.let { add(copyCallable(it)) }
        }
    }

    private fun draftType(type: ImmutableDraftTypeMetadata): LsiTypeSpec =
        LsiTypeSpec(
            name = "${type.simpleName}$DRAFT",
            kind = LsiTypeSpecKind.INTERFACE,
            annotations = listOf(
                LsiAnnotationSpec(type = DSL_SCOPE_LSI_CLASS_NAME),
                generatedAnnotation(type.className),
            ),
            superInterfaces = buildList {
                add(type.className)
                if (type.superDraftClassNames.isEmpty()) {
                    add(DRAFT_CLASS_LSI_CLASS_NAME)
                } else {
                    addAll(type.superDraftClassNames)
                }
            },
            properties = buildProperties(type),
            callables = buildCallables(type),
            nestedTypes = buildNestedTypes(type),
        )

    private fun buildProperties(type: ImmutableDraftTypeMetadata): List<LsiPropertySpec> = buildList {
        for (prop in type.declaredProps) {
            add(
                LsiPropertySpec(
                    name = prop.name,
                    type = prop.typeName,
                    modifiers = setOf(LsiModifier.OVERRIDE, LsiModifier.ABSTRACT),
                    mutable = prop.isMutable,
                ),
            )
            addAssociatedIdProperty(prop.associatedIdMetadata)?.let(::add)
        }
    }

    private fun buildCallables(type: ImmutableDraftTypeMetadata): List<LsiCallableSpec> = buildList {
        for (prop in type.declaredProps) {
            prop.funReturnTypeName?.let {
                add(
                    LsiCallableSpec(
                        kind = LsiCallableSpecKind.FUNCTION,
                        name = prop.name,
                        returnType = it,
                        modifiers = setOf(LsiModifier.ABSTRACT),
                    ),
                )
            }
            prop.refBlockMetadata?.let {
                add(
                    LsiCallableSpec(
                        kind = LsiCallableSpecKind.FUNCTION,
                        name = prop.name,
                        parameters = listOf(LsiParameterSpec("block", it.toLsiDraftConsumerTypeName())),
                        modifiers = setOf(LsiModifier.ABSTRACT),
                    ),
                )
            }
        }
    }

    private fun buildNestedTypes(type: ImmutableDraftTypeMetadata): List<LsiTypeSpec> = buildList {
        add(ProducerGenerator(jacksonTypes, type.producerTypeMetadata, currentVersionValue).generate())
        type.builderTypeMetadata?.let { add(BuilderGenerator(it).generate()) }
    }

    private fun addAssociatedIdProperty(prop: ImmutableAssociatedIdMetadata?): LsiPropertySpec? =
        AssociatedIdGenerator(jacksonTypes, false).generate(prop)

    private fun produceCallable(type: ImmutableProducerTypeMetadata): LsiCallableSpec =
        LsiCallableSpec(
            kind = LsiCallableSpecKind.FUNCTION,
            name = "produce",
            annotations = listOf(generatedAnnotation(type.className)),
            receiverType = type.draftClassName.nested(PRODUCER, "Companion"),
            parameters = listOf(
                LsiParameterSpec(
                    name = "base",
                    type = type.className.copyNullable(true),
                    defaultValue = LsiCodeBlock.of("null"),
                ),
                LsiParameterSpec(
                    name = "resolveImmediately",
                    type = BOOLEAN_LSI_CLASS_NAME,
                    defaultValue = LsiCodeBlock.of("false"),
                ),
                LsiParameterSpec(
                    name = "block",
                    type = type.draftCallbackMetadata.toLsiLambdaTypeName(),
                ),
            ),
            returnType = type.className,
            statements = listOf(
                LsiVariableDeclarationStatement(
                    name = "consumer",
                    type = type.draftCallbackMetadata.toLsiDraftConsumerTypeName(),
                    initializer = lambdaConsumer(type.draftCallbackMetadata),
                ),
                LsiReturnStatement(
                    LsiCallExpression(
                        name = "produce",
                        arguments = listOf(
                            LsiNameExpression("base"),
                            LsiNameExpression("resolveImmediately"),
                            LsiNameExpression("consumer"),
                        ),
                    ),
                ),
            ),
        )

    private fun refBlockCallable(
        type: ImmutableDraftTypeMetadata,
        name: String,
        blockMetadata: ImmutableCallbackMetadata,
    ): LsiCallableSpec =
        LsiCallableSpec(
            kind = LsiCallableSpecKind.FUNCTION,
            name = name,
            annotations = listOf(generatedAnnotation(type.className)),
            receiverType = type.draftClassName,
            parameters = listOf(
                LsiParameterSpec(
                    name = "block",
                    type = blockMetadata.toLsiLambdaTypeName(),
                ),
            ),
            statements = listOf(
                LsiVariableDeclarationStatement(
                    name = "consumer",
                    type = blockMetadata.toLsiDraftConsumerTypeName(),
                    initializer = lambdaConsumer(blockMetadata),
                ),
                LsiExpressionStatement(
                    LsiCallExpression(
                        name = name,
                        arguments = listOf(LsiNameExpression("consumer")),
                    )
                ),
            ),
        )

    private fun addByCallable(type: ImmutableDraftAddFunMetadata): LsiCallableSpec =
        LsiCallableSpec(
            kind = LsiCallableSpecKind.FUNCTION,
            name = "addBy",
            annotations = listOf(generatedAnnotation(type.annotationClassName)),
            receiverType = type.receiverTypeName,
            parameters = buildList {
                type.baseParameterTypeName?.let { add(LsiParameterSpec("base", it)) }
                add(
                    LsiParameterSpec(
                        name = "resolveImmediately",
                        type = BOOLEAN_LSI_CLASS_NAME,
                        defaultValue = LsiCodeBlock.of("false"),
                    ),
                )
                type.blockMetadata?.let { add(LsiParameterSpec("block", it.toLsiLambdaTypeName())) }
            },
            returnType = type.returnTypeName,
            statements = listOf(
                LsiExpressionStatement(
                    LsiCallExpression(
                        name = "add",
                        arguments = listOf(
                            LsiCastExpression(
                                type.draftClassName,
                                producerProduceCall(
                                    producerClassName = type.producerClassName,
                                    withBase = type.baseParameterTypeName != null,
                                    withBlock = type.blockMetadata != null,
                                ),
                            )
                        ),
                    )
                ),
                LsiReturnStatement(LsiThisExpression),
            ),
        )

    private fun newByCallable(type: ImmutableDraftNewFunMetadata): LsiCallableSpec =
        LsiCallableSpec(
            kind = LsiCallableSpecKind.FUNCTION,
            name = type.name,
            annotations = listOf(generatedAnnotation(type.annotationClassName)),
            receiverType = type.receiverTypeName,
            parameters = buildList {
                type.baseParameterTypeName?.let { add(LsiParameterSpec("base", it)) }
                add(
                    LsiParameterSpec(
                        name = "resolveImmediately",
                        type = BOOLEAN_LSI_CLASS_NAME,
                        defaultValue = LsiCodeBlock.of("false"),
                    ),
                )
                type.blockMetadata?.let { add(LsiParameterSpec("block", it.toLsiLambdaTypeName())) }
            },
            returnType = type.returnTypeName,
            statements = listOf(
                LsiReturnStatement(
                    producerProduceCall(
                        producerClassName = type.producerClassName,
                        withBase = type.baseParameterTypeName != null,
                        withBlock = type.blockMetadata != null,
                    ),
                ),
            ),
        )

    private fun copyCallable(type: ImmutableDraftCopyFunMetadata): LsiCallableSpec =
        LsiCallableSpec(
            kind = LsiCallableSpecKind.FUNCTION,
            name = "copy",
            annotations = listOf(generatedAnnotation(type.annotationClassName)),
            receiverType = type.receiverTypeName,
            parameters = listOf(
                LsiParameterSpec(
                    name = "resolveImmediately",
                    type = BOOLEAN_LSI_CLASS_NAME,
                    defaultValue = LsiCodeBlock.of("false"),
                ),
                LsiParameterSpec("block", type.blockMetadata.toLsiLambdaTypeName()),
            ),
            returnType = type.returnTypeName,
            statements = listOf(
                LsiReturnStatement(
                    LsiCallExpression(
                        receiver = LsiPropertyAccessExpression(
                            receiver = LsiTypeExpression(type.draftClassName),
                            name = PRODUCER,
                        ),
                        name = "produce",
                        arguments = listOf(
                            LsiThisExpression,
                            LsiNameExpression("resolveImmediately"),
                            LsiNameExpression("block"),
                        ),
                    ),
                ),
            ),
        )

    private fun producerProduceCall(
        producerClassName: site.addzero.lsi.poet.LsiClassName,
        withBase: Boolean,
        withBlock: Boolean,
    ): LsiCallExpression =
        LsiCallExpression(
            receiver = LsiTypeExpression(producerClassName),
            name = "produce",
            arguments = buildList {
                add(
                    if (withBase) {
                        LsiNameExpression("base")
                    } else {
                        site.addzero.lsi.poet.LsiNullExpression
                    }
                )
                add(LsiNameExpression("resolveImmediately"))
                if (withBlock) {
                    add(LsiNameExpression("block"))
                }
            },
        )
}

private fun lambdaConsumer(blockMetadata: ImmutableCallbackMetadata) =
    LsiLambdaExpression(
        mode = LsiLambdaMode.BLOCK,
        parameterNames = listOf("it"),
        statements = listOf(
            LsiExpressionStatement(
                LsiCallExpression(
                    receiver = LsiNameExpression("block"),
                    name = "invoke",
                    arguments = listOf(LsiNameExpression("it")),
                )
            )
        ),
    )
