package site.addzero.lsi.jimmer.immutable.generator

import site.addzero.lsi.codegen.KOTLIN_LIST_LSI_CLASS_NAME
import site.addzero.lsi.codegen.K_EMBEDDED_PROP_EXPRESSION_LSI_CLASS_NAME
import site.addzero.lsi.codegen.K_NON_NULL_EMBEDDED_PROP_EXPRESSION_LSI_CLASS_NAME
import site.addzero.lsi.codegen.K_NON_NULL_PROP_EXPRESSION_LSI_CLASS_NAME
import site.addzero.lsi.codegen.K_NON_NULL_PROPS_LSI_CLASS_NAME
import site.addzero.lsi.codegen.K_NON_NULL_REMOTE_REF_LSI_CLASS_NAME
import site.addzero.lsi.codegen.K_NON_NULL_TABLE_EX_LSI_CLASS_NAME
import site.addzero.lsi.codegen.K_NON_NULL_TABLE_LSI_CLASS_NAME
import site.addzero.lsi.codegen.K_NULLABLE_EMBEDDED_PROP_EXPRESSION_LSI_CLASS_NAME
import site.addzero.lsi.codegen.K_NULLABLE_PROP_EXPRESSION_LSI_CLASS_NAME
import site.addzero.lsi.codegen.K_NULLABLE_PROPS_LSI_CLASS_NAME
import site.addzero.lsi.codegen.K_NULLABLE_REMOTE_REF_LSI_CLASS_NAME
import site.addzero.lsi.codegen.K_NULLABLE_TABLE_EX_LSI_CLASS_NAME
import site.addzero.lsi.codegen.K_NULLABLE_TABLE_LSI_CLASS_NAME
import site.addzero.lsi.codegen.K_PROPS_LSI_CLASS_NAME
import site.addzero.lsi.codegen.K_REMOTE_REF_IMPLEMENTOR_LSI_CLASS_NAME
import site.addzero.lsi.codegen.K_REMOTE_REF_LSI_CLASS_NAME
import site.addzero.lsi.codegen.K_TABLE_EX_LSI_CLASS_NAME
import site.addzero.lsi.codegen.IMMUTABLE_TYPE_LSI_CLASS_NAME
import site.addzero.lsi.codegen.NEW_FETCHER_FUN_LSI_CLASS_NAME
import site.addzero.lsi.codegen.PROPS
import site.addzero.lsi.codegen.SELECTION_LSI_CLASS_NAME
import site.addzero.lsi.codegen.TYPED_PROP_LSI_CLASS_NAME
import site.addzero.lsi.codegen.TYPED_PROP_REFERENCE_LIST_LSI_CLASS_NAME
import site.addzero.lsi.codegen.TYPED_PROP_REFERENCE_LSI_CLASS_NAME
import site.addzero.lsi.codegen.TYPED_PROP_SCALAR_LIST_LSI_CLASS_NAME
import site.addzero.lsi.codegen.TYPED_PROP_SCALAR_LSI_CLASS_NAME
import site.addzero.lsi.codegen.generatedAnnotation
import site.addzero.lsi.codegen.suppressAllAnnotation
import site.addzero.lsi.jimmer.immutable.metadata.generator.immutableSourceFileSpecs
import site.addzero.lsi.jimmer.immutable.metadata.model.ImmutablePropsIdMetadata
import site.addzero.lsi.jimmer.immutable.metadata.model.ImmutablePropsPropMetadata
import site.addzero.lsi.jimmer.immutable.metadata.model.ImmutablePropsTypeMetadata
import site.addzero.lsi.poet.LsiCallableSpec
import site.addzero.lsi.poet.LsiCallableSpecKind
import site.addzero.lsi.poet.LsiCallExpression
import site.addzero.lsi.poet.LsiCastExpression
import site.addzero.lsi.poet.LsiClassLiteralExpression
import site.addzero.lsi.poet.LsiFileSpec
import site.addzero.lsi.poet.LsiJavaClassExpression
import site.addzero.lsi.poet.LsiLiteralExpression
import site.addzero.lsi.poet.LsiModifier
import site.addzero.lsi.poet.LsiNameExpression
import site.addzero.lsi.poet.LsiParameterSpec
import site.addzero.lsi.poet.LsiParameterizedTypeName
import site.addzero.lsi.poet.LsiPropertySpec
import site.addzero.lsi.poet.LsiPropertyAccessExpression
import site.addzero.lsi.poet.LsiReturnStatement
import site.addzero.lsi.poet.LsiThisExpression
import site.addzero.lsi.poet.LsiTypeName
import site.addzero.lsi.poet.LsiTypeExpression
import site.addzero.lsi.poet.LsiTypeSpec
import site.addzero.lsi.poet.LsiTypeSpecKind
import site.addzero.lsi.poet.LsiWildcardTypeName

class PropsGenerator(
    private val sourcePackageName: String,
    private val sourceFileName: String,
    private val type: ImmutablePropsTypeMetadata,
) {
    fun generate(mode: ImmutableGenerationMode): List<LsiFileSpec> =
        immutableSourceFileSpecs(
            coreFileSpec = coreFileSpec(),
            generationMode = mode,
        ) {
            val topLevelProperties = topLevelProperties()
            val topLevelCallables = topLevelCallables()
            if (topLevelProperties.isEmpty() && topLevelCallables.isEmpty()) {
                null
            } else {
                dslFileSpec(topLevelProperties, topLevelCallables)
            }
        }

    private fun coreFileSpec(): LsiFileSpec {
        val outputFileName = "${sourceFileName}$PROPS"
        return LsiFileSpec(
            packageName = sourcePackageName,
            name = outputFileName,
            annotations = listOf(
                suppressAllAnnotation(),
                generatedAnnotation(type.className),
            ),
            types = listOf(objectMeta()),
        )
    }

    private fun dslFileSpec(
        topLevelProperties: List<LsiPropertySpec>,
        topLevelCallables: List<LsiCallableSpec>,
    ): LsiFileSpec {
        val outputFileName = "${sourceFileName}$PROPS"
        return LsiFileSpec(
            packageName = sourcePackageName,
            name = "${outputFileName}Dsl",
            annotations = listOf(
                suppressAllAnnotation(),
                generatedAnnotation(type.className),
            ),
            topLevelProperties = topLevelProperties,
            topLevelCallables = topLevelCallables,
        )
    }

    private fun topLevelProperties(): List<LsiPropertySpec> = buildList {
        if (type.isEmbeddable) {
            for (prop in type.properties) {
                addEmbeddableProp(prop, nullable = false)?.let(::add)
                addEmbeddableProp(prop, nullable = true)?.let(::add)
            }
        } else {
            for (prop in type.properties) {
                for (nonNullTable in BOOLEAN_VALUES) {
                    for (outerJoin in BOOLEAN_VALUES) {
                        for (isTableEx in BOOLEAN_VALUES) {
                            addProp(prop, nonNullTable, outerJoin, isTableEx)?.let(::add)
                        }
                    }
                }
                for (nonNullTable in BOOLEAN_VALUES) {
                    for (isTableEx in BOOLEAN_VALUES) {
                        addIdProp(prop, nonNullTable, isTableEx)?.let(::add)
                    }
                }
            }
        }
        if (type.isEntity) {
            addRemoteId(type.idProp, nullable = false)?.let(::add)
            addRemoteId(type.idProp, nullable = true)?.let(::add)
        }
    }

    private fun topLevelCallables(): List<LsiCallableSpec> = buildList {
        if (!type.isEmbeddable) {
            for (prop in type.properties) {
                addPropLambda(prop)?.let(::add)
            }
        }
        if (type.isEntity || type.isEmbeddable) {
            add(addFetchByFun(type.isEmbeddable, nullable = false))
            add(addFetchByFun(type.isEmbeddable, nullable = true))
        }
    }

    private fun addProp(
        prop: ImmutablePropsPropMetadata,
        nonNullTable: Boolean,
        outerJoin: Boolean,
        isTableEx: Boolean,
    ): LsiPropertySpec? {
        if (!(if (isTableEx) prop.isDslTableEx else prop.isDslTable)) {
            return null
        }
        if (outerJoin && !prop.isAssociation) {
            return null
        }
        if (nonNullTable && (prop.isAssociation || prop.isNullable)) {
            return null
        }
        if (isTableEx && !prop.isAssociation) {
            return null
        }
        if (prop.isList && prop.isAssociation && !isTableEx) {
            return null
        }
        val receiverType = when {
            isTableEx -> K_TABLE_EX_LSI_CLASS_NAME
            prop.isAssociation || prop.isNullable -> K_PROPS_LSI_CLASS_NAME
            nonNullTable -> K_NON_NULL_PROPS_LSI_CLASS_NAME
            else -> K_NULLABLE_PROPS_LSI_CLASS_NAME
        }.parameterizedBy(type.className)
        val returnType = when {
            prop.isRemote ->
                if (outerJoin) {
                    K_NULLABLE_REMOTE_REF_LSI_CLASS_NAME
                } else {
                    K_NON_NULL_REMOTE_REF_LSI_CLASS_NAME
                }
            prop.isAssociation && isTableEx ->
                if (outerJoin) {
                    K_NULLABLE_TABLE_EX_LSI_CLASS_NAME
                } else {
                    K_NON_NULL_TABLE_EX_LSI_CLASS_NAME
                }
            !prop.isList && prop.isAssociation && !isTableEx ->
                if (outerJoin) {
                    K_NULLABLE_TABLE_LSI_CLASS_NAME
                } else {
                    K_NON_NULL_TABLE_LSI_CLASS_NAME
                }
            prop.isEmbedded ->
                if (nonNullTable) {
                    K_NON_NULL_EMBEDDED_PROP_EXPRESSION_LSI_CLASS_NAME
                } else {
                    K_NULLABLE_EMBEDDED_PROP_EXPRESSION_LSI_CLASS_NAME
                }
            else ->
                if (nonNullTable) {
                    K_NON_NULL_PROP_EXPRESSION_LSI_CLASS_NAME
                } else {
                    K_NULLABLE_PROP_EXPRESSION_LSI_CLASS_NAME
                }
        }.parameterizedBy(
            prop.targetType.toLsiTypeName(nullableOverride = false).let {
                if (prop.isList && !prop.isAssociation) {
                    LIST_LSI_CLASS_NAME.parameterizedBy(it)
                } else {
                    it
                }
            }
        )
        val getter =
            if (prop.isRemote) {
                LsiReturnStatement(
                    LsiCallExpression(
                        receiver = LsiTypeExpression(K_REMOTE_REF_LSI_CLASS_NAME),
                        name = "protect",
                        arguments = listOf(propNavigationExpression(prop, outerJoin)),
                    )
                )
            } else if (innerFunName(prop, outerJoin) == "get") {
                LsiReturnStatement(
                    LsiCastExpression(
                        returnType,
                        propGetExpression(
                            targetType = prop.targetType.toLsiTypeName(nullableOverride = false),
                            prop = prop,
                        )
                    )
                )
            } else {
                LsiReturnStatement(propNavigationExpression(prop, outerJoin))
            }
        return LsiPropertySpec(
            name = if (outerJoin) "${prop.name}?" else prop.name,
            type = returnType,
            receiverType = receiverType,
            annotations = listOf(generatedAnnotation(type.className)),
            getterStatements = listOf(getter),
        )
    }

    private fun addPropLambda(prop: ImmutablePropsPropMetadata): LsiCallableSpec? {
        if (!(prop.isList && prop.isAssociation)) {
            return null
        }
        val predicateBlockMetadata = prop.predicateBlockMetadata
            ?: error("Props metadata bug: missing predicateBlockMetadata for '${prop.name}'")
        val nullableBooleanExpression = predicateBlockMetadata.returnTypeName
        return LsiCallableSpec(
            kind = LsiCallableSpecKind.FUNCTION,
            name = prop.name,
            receiverType = K_PROPS_LSI_CLASS_NAME.parameterizedBy(type.className),
            parameters = listOf(
                LsiParameterSpec(
                    name = "block",
                    type = predicateBlockMetadata.toLsiLambdaTypeName(),
                )
            ),
            returnType = nullableBooleanExpression,
            statements = listOf(
                LsiReturnStatement(
                    LsiCallExpression(
                        name = "exists",
                        arguments = listOf(
                            propUnwrapExpression(prop),
                            LsiNameExpression("block"),
                        ),
                    )
                )
            ),
        )
    }

    private fun addIdProp(
        prop: ImmutablePropsPropMetadata,
        nonNullTable: Boolean,
        isTableEx: Boolean,
    ): LsiPropertySpec? {
        val idPropName = prop.generatedIdPropName ?: return null
        if (nonNullTable && prop.isNullable) {
            return null
        }
        if (prop.isTransient || !prop.isAssociation || prop.isList != isTableEx) {
            return null
        }
        val receiverType = when {
            prop.isNullable -> K_PROPS_LSI_CLASS_NAME
            isTableEx && nonNullTable -> K_NON_NULL_TABLE_EX_LSI_CLASS_NAME
            isTableEx && !nonNullTable -> K_NULLABLE_TABLE_EX_LSI_CLASS_NAME
            !isTableEx && nonNullTable -> K_NON_NULL_TABLE_LSI_CLASS_NAME
            else -> K_NULLABLE_PROPS_LSI_CLASS_NAME
        }.parameterizedBy(type.className)
        val returnType = if (prop.targetIdIsEmbedded) {
            if (nonNullTable) {
                K_NON_NULL_EMBEDDED_PROP_EXPRESSION_LSI_CLASS_NAME
            } else {
                K_NULLABLE_EMBEDDED_PROP_EXPRESSION_LSI_CLASS_NAME
            }
        } else {
            if (nonNullTable) {
                K_NON_NULL_PROP_EXPRESSION_LSI_CLASS_NAME
            } else {
                K_NULLABLE_PROP_EXPRESSION_LSI_CLASS_NAME
            }
        }.parameterizedBy(
            prop.targetIdType?.toLsiTypeName()
                ?: error("Props metadata bug: missing targetIdType for '${prop.name}'"),
        )
        return LsiPropertySpec(
            name = idPropName,
            type = returnType,
            receiverType = receiverType,
            annotations = listOf(generatedAnnotation(type.className)),
            getterStatements = listOf(
                LsiReturnStatement(
                    LsiCastExpression(
                        returnType,
                        associatedIdExpression(
                            prop = prop,
                            targetType = prop.targetIdTargetType?.toLsiTypeName(nullableOverride = false)
                                ?: error("Props metadata bug: missing targetIdTargetType for '${prop.name}'"),
                        )
                    )
                )
            ),
        )
    }

    private fun addEmbeddableProp(
        prop: ImmutablePropsPropMetadata,
        nullable: Boolean,
    ): LsiPropertySpec? {
        if (!nullable && prop.isNullable) {
            return null
        }
        val receiverType =
            if (prop.isNullable) {
                K_EMBEDDED_PROP_EXPRESSION_LSI_CLASS_NAME.parameterizedBy(type.className)
            } else {
                (if (nullable) {
                    K_NULLABLE_EMBEDDED_PROP_EXPRESSION_LSI_CLASS_NAME
                } else {
                    K_NON_NULL_EMBEDDED_PROP_EXPRESSION_LSI_CLASS_NAME
                }).parameterizedBy(type.className)
            }
        val returnType =
            if (prop.isEmbedded) {
                if (nullable) {
                    K_NULLABLE_EMBEDDED_PROP_EXPRESSION_LSI_CLASS_NAME.parameterizedBy(
                        prop.type.toLsiTypeName(nullableOverride = false)
                    )
                } else {
                    K_NON_NULL_EMBEDDED_PROP_EXPRESSION_LSI_CLASS_NAME.parameterizedBy(
                        prop.type.toLsiTypeName(nullableOverride = false)
                    )
                }
            } else {
                if (nullable) {
                    K_NULLABLE_PROP_EXPRESSION_LSI_CLASS_NAME.parameterizedBy(
                        prop.type.toLsiTypeName(nullableOverride = false)
                    )
                } else {
                    K_NON_NULL_PROP_EXPRESSION_LSI_CLASS_NAME.parameterizedBy(
                        prop.type.toLsiTypeName(nullableOverride = false)
                    )
                }
            }
        val getter =
            if (prop.isEmbedded || !nullable || prop.isNullable) {
                LsiReturnStatement(
                    LsiCastExpression(
                        returnType,
                        propGetExpression(
                            targetType = prop.type.toLsiTypeName(nullableOverride = false),
                            prop = prop,
                        )
                    )
                )
            } else {
                LsiReturnStatement(propNavigationExpression(prop, outerJoin = false))
            }
        return LsiPropertySpec(
            name = prop.name,
            type = returnType,
            receiverType = receiverType,
            annotations = listOf(generatedAnnotation(type.className)),
            getterStatements = listOf(getter),
        )
    }

    private fun addRemoteId(
        idProp: ImmutablePropsIdMetadata?,
        nullable: Boolean,
    ): LsiPropertySpec? {
        val idPropMetadata = idProp
            ?: error("Props metadata bug: idProp must exist for remote id generation of '${type.simpleName}'")
        val returnType =
            if (nullable) {
                K_NULLABLE_PROP_EXPRESSION_LSI_CLASS_NAME
            } else {
                K_NON_NULL_PROP_EXPRESSION_LSI_CLASS_NAME
            }.parameterizedBy(idPropMetadata.type.toLsiTypeName())
        return LsiPropertySpec(
            name = idPropMetadata.name,
            type = returnType,
            receiverType =
                if (nullable) {
                    K_NULLABLE_REMOTE_REF_LSI_CLASS_NAME
                } else {
                    K_NON_NULL_REMOTE_REF_LSI_CLASS_NAME
                }.parameterizedBy(type.className),
            annotations = listOf(generatedAnnotation(type.className)),
            getterStatements = listOf(
                LsiReturnStatement(
                    LsiCastExpression(
                        returnType,
                        LsiCallExpression(
                            receiver = LsiCastExpression(
                                K_REMOTE_REF_IMPLEMENTOR_LSI_CLASS_NAME.parameterizedBy(LsiWildcardTypeName()),
                                LsiThisExpression
                            ),
                            name = "id",
                            typeArguments = listOf(idPropMetadata.targetType.toLsiTypeName()),
                        )
                    )
                )
            ),
        )
    }

    private fun objectMeta(): LsiTypeSpec =
        LsiTypeSpec(
            name = type.propsClassName.simpleName,
            kind = LsiTypeSpecKind.CLASS,
            annotations = listOf(generatedAnnotation(type.className)),
            callables = listOf(
                LsiCallableSpec(
                    kind = LsiCallableSpecKind.CONSTRUCTOR,
                    primary = true,
                    modifiers = setOf(LsiModifier.PRIVATE),
                )
            ),
            properties = type.properties.map { prop ->
                LsiPropertySpec(
                    name = prop.constantName,
                    type = when {
                        prop.isReferenceList -> TYPED_PROP_REFERENCE_LIST_LSI_CLASS_NAME
                        prop.isReference -> TYPED_PROP_REFERENCE_LSI_CLASS_NAME
                        prop.isScalarList -> TYPED_PROP_SCALAR_LIST_LSI_CLASS_NAME
                        else -> TYPED_PROP_SCALAR_LSI_CLASS_NAME
                    }.parameterizedBy(
                        type.className,
                        prop.targetType.toLsiTypeName(),
                    ),
                    modifiers = setOf(LsiModifier.PUBLIC, LsiModifier.STATIC),
                    initializer = typedPropInitializer(prop),
                )
            },
        )

    private fun typedPropInitializer(prop: ImmutablePropsPropMetadata): LsiCallExpression =
        LsiCallExpression(
            receiver = LsiTypeExpression(TYPED_PROP_LSI_CLASS_NAME),
            name =
                when {
                    prop.isReferenceList -> "referenceList"
                    prop.isReference -> "reference"
                    prop.isScalarList -> "scalarList"
                    else -> "scalar"
                },
            arguments = listOf(
                LsiCallExpression(
                    receiver = immutableTypeExpression(),
                    name = "getProp",
                    arguments = listOf(LsiLiteralExpression(prop.name)),
                )
            ),
        )

    private fun immutableTypeExpression(): LsiCallExpression =
        LsiCallExpression(
            receiver = LsiTypeExpression(IMMUTABLE_TYPE_LSI_CLASS_NAME),
            name = "get",
            arguments = listOf(LsiJavaClassExpression(type.className)),
        )

    private fun addFetchByFun(
        embeddable: Boolean,
        nullable: Boolean,
    ): LsiCallableSpec =
        LsiCallableSpec(
            kind = LsiCallableSpecKind.FUNCTION,
            name = "fetchBy",
            receiverType =
                if (embeddable) {
                    if (nullable) {
                        K_NULLABLE_EMBEDDED_PROP_EXPRESSION_LSI_CLASS_NAME
                    } else {
                        K_NON_NULL_EMBEDDED_PROP_EXPRESSION_LSI_CLASS_NAME
                    }
                } else {
                    if (nullable) {
                        K_NULLABLE_TABLE_LSI_CLASS_NAME
                    } else {
                        K_NON_NULL_TABLE_LSI_CLASS_NAME
                    }
                }.parameterizedBy(type.className),
            annotations = listOf(generatedAnnotation(type.className)),
            parameters = listOf(
                LsiParameterSpec(
                    name = "block",
                    type = type.fetchByBlockMetadata.toLsiLambdaTypeName(),
                )
            ),
            returnType = SELECTION_LSI_CLASS_NAME.parameterizedBy(type.className.copyNullable(nullable)),
            statements = listOf(
                LsiReturnStatement(
                    LsiCallExpression(
                        name = "fetch",
                        arguments = listOf(fetchByExpression()),
                    )
                )
            ),
        )

    private fun innerFunName(
        prop: ImmutablePropsPropMetadata,
        outerJoin: Boolean,
    ): String = when {
        outerJoin -> "outerJoin"
        prop.isAssociation -> "join"
        else -> "get"
    }

    private fun propConstantExpression(prop: ImmutablePropsPropMetadata): LsiPropertyAccessExpression =
        LsiPropertyAccessExpression(
            receiver = LsiTypeExpression(type.propsClassName),
            name = prop.constantName,
        )

    private fun propUnwrapExpression(prop: ImmutablePropsPropMetadata): LsiCallExpression =
        LsiCallExpression(
            receiver = propConstantExpression(prop),
            name = "unwrap",
        )

    private fun propNavigationExpression(
        prop: ImmutablePropsPropMetadata,
        outerJoin: Boolean,
    ): LsiCallExpression =
        LsiCallExpression(
            name = innerFunName(prop, outerJoin),
            arguments = listOf(propUnwrapExpression(prop)),
        )

    private fun propGetExpression(
        targetType: LsiTypeName,
        prop: ImmutablePropsPropMetadata,
    ): LsiCallExpression =
        LsiCallExpression(
            name = "get",
            typeArguments = listOf(targetType),
            arguments = listOf(propUnwrapExpression(prop)),
        )

    private fun associatedIdExpression(
        prop: ImmutablePropsPropMetadata,
        targetType: LsiTypeName,
    ): LsiCallExpression =
        LsiCallExpression(
            name = "getAssociatedId",
            typeArguments = listOf(targetType),
            arguments = listOf(propUnwrapExpression(prop)),
        )

    private fun fetchByExpression(): LsiCallExpression =
        LsiCallExpression(
            receiver = LsiCallExpression(
                receiver = LsiNameExpression(NEW_FETCHER_FUN_LSI_CLASS_NAME.packageName),
                name = NEW_FETCHER_FUN_LSI_CLASS_NAME.simpleName,
                arguments = listOf(LsiClassLiteralExpression(type.className)),
            ),
            name = "by",
            arguments = listOf(LsiNameExpression("block")),
        )
}

private val BOOLEAN_VALUES = listOf(true, false)

private val LIST_LSI_CLASS_NAME = KOTLIN_LIST_LSI_CLASS_NAME
