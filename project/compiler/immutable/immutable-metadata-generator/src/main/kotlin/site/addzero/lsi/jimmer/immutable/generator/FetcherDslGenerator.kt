package site.addzero.lsi.jimmer.immutable.generator

import site.addzero.lsi.codegen.DSL_SCOPE_LSI_CLASS_NAME
import site.addzero.lsi.codegen.FETCHER
import site.addzero.lsi.codegen.FETCHER_DSL
import site.addzero.lsi.codegen.FETCHER_LSI_CLASS_NAME
import site.addzero.lsi.codegen.ID_ONLY_FETCH_TYPE_LSI_CLASS_NAME
import site.addzero.lsi.codegen.JAVA_FIELD_CONFIG_UTILS_LSI_CLASS_NAME
import site.addzero.lsi.codegen.KOTLIN_BOOLEAN_LSI_CLASS_NAME
import site.addzero.lsi.codegen.REFERENCE_FETCH_TYPE_LSI_CLASS_NAME
import site.addzero.lsi.codegen.generatedAnnotation
import site.addzero.lsi.jimmer.immutable.metadata.model.ImmutableCallbackMetadata
import site.addzero.lsi.jimmer.immutable.metadata.model.ImmutableFetcherFieldKind
import site.addzero.lsi.jimmer.immutable.metadata.model.ImmutableFetcherPropMetadata
import site.addzero.lsi.jimmer.immutable.metadata.model.ImmutableFetcherTypeMetadata
import site.addzero.lsi.poet.LsiAnnotationSpec
import site.addzero.lsi.poet.LsiAssignmentStatement
import site.addzero.lsi.poet.LsiBinaryExpression
import site.addzero.lsi.poet.LsiBinaryOperator
import site.addzero.lsi.poet.LsiCallableSpec
import site.addzero.lsi.poet.LsiCallableSpecKind
import site.addzero.lsi.poet.LsiCallExpression
import site.addzero.lsi.poet.LsiClassName
import site.addzero.lsi.poet.LsiCodeBlock
import site.addzero.lsi.poet.LsiExpressionStatement
import site.addzero.lsi.poet.LsiIfStatement
import site.addzero.lsi.poet.LsiModifier
import site.addzero.lsi.poet.LsiNewExpression
import site.addzero.lsi.poet.LsiNullExpression
import site.addzero.lsi.poet.LsiParameterSpec
import site.addzero.lsi.poet.LsiPropertySpec
import site.addzero.lsi.poet.LsiReturnStatement
import site.addzero.lsi.poet.LsiStatement
import site.addzero.lsi.poet.LsiTypeName
import site.addzero.lsi.poet.LsiTypeSpec
import site.addzero.lsi.poet.LsiTypeSpecKind
import site.addzero.lsi.poet.LsiNameExpression
import site.addzero.lsi.poet.LsiTypeExpression
import site.addzero.lsi.poet.LsiVariableDeclarationStatement

class FetcherDslGenerator(
    private val type: ImmutableFetcherTypeMetadata,
    private val fetcherClassName: LsiClassName,
) {

    fun generate(): LsiTypeSpec =
        LsiTypeSpec(
            name = "${type.simpleName}$FETCHER_DSL",
            kind = LsiTypeSpecKind.CLASS,
            annotations = listOf(
                LsiAnnotationSpec(type = DSL_SCOPE_LSI_CLASS_NAME),
                generatedAnnotation(type.className),
            ),
            properties = listOf(fetcherProperty()),
            callables = buildList {
                add(primaryConstructor())
                add(internallyGetFetcherFun())
                add(deleteFun("allScalarFields"))
                add(deleteFun("allTableFields"))
                for (prop in type.properties) {
                    if (!prop.isId) {
                        add(simplePropFun(prop))
                        propWithIdOnlyFetchType(prop)?.let(::add)
                        for (enabled in BOOLEAN_VALUES) {
                            for (lambda in BOOLEAN_VALUES) {
                                for (config in BOOLEAN_VALUES) {
                                    propWithCode(prop, enabled, lambda, config)?.let(::add)
                                }
                            }
                        }
                        propWithReferenceFetchType(prop, lambda = false)?.let(::add)
                        propWithReferenceFetchType(prop, lambda = true)?.let(::add)
                        recursiveProp(prop, config = false)?.let(::add)
                        recursiveProp(prop, config = true)?.let(::add)
                    }
                }
            },
        )

    private fun fetcherProperty(): LsiPropertySpec =
        LsiPropertySpec(
            name = "_fetcher",
            type = fetcherType(type.className),
            modifiers = setOf(LsiModifier.PRIVATE),
            mutable = true,
            initializer = LsiNameExpression("fetcher"),
        )

    private fun primaryConstructor(): LsiCallableSpec =
        LsiCallableSpec(
            kind = LsiCallableSpecKind.CONSTRUCTOR,
            primary = true,
            parameters = listOf(
                LsiParameterSpec(
                    name = "fetcher",
                    type = fetcherType(type.className),
                    defaultValue = LsiCodeBlock.of("%T.`\$`", fetcherClassName),
                )
            ),
        )

    private fun internallyGetFetcherFun(): LsiCallableSpec =
        LsiCallableSpec(
            kind = LsiCallableSpecKind.FUNCTION,
            name = "internallyGetFetcher",
            returnType = fetcherType(type.className),
            statements = listOf(
                LsiReturnStatement(LsiNameExpression("_fetcher"))
            ),
        )

    private fun deleteFun(funName: String): LsiCallableSpec =
        LsiCallableSpec(
            kind = LsiCallableSpecKind.FUNCTION,
            name = funName,
            statements = listOf(
                updateFetcher(funName)
            ),
        )

    private fun simplePropFun(prop: ImmutableFetcherPropMetadata): LsiCallableSpec =
        LsiCallableSpec(
            kind = LsiCallableSpecKind.FUNCTION,
            name = prop.name,
            parameters = listOf(
                LsiParameterSpec(
                    name = "enabled",
                    type = BOOLEAN_LSI_CLASS_NAME,
                    defaultValue = LsiCodeBlock.of("true"),
                )
            ),
            statements = listOf(
                LsiIfStatement(
                    condition = LsiNameExpression("enabled"),
                    thenStatements = listOf(addFetcher(prop.name)),
                    elseStatements = listOf(removeFetcher(prop.name)),
                )
            ),
        )

    private fun propWithIdOnlyFetchType(prop: ImmutableFetcherPropMetadata): LsiCallableSpec? {
        if (!prop.supportsIdOnlyFetchType) {
            return null
        }
        return LsiCallableSpec(
            kind = LsiCallableSpecKind.FUNCTION,
            name = prop.name,
            parameters = listOf(
                LsiParameterSpec(
                    name = "idOnlyFetchType",
                    type = ID_ONLY_FETCH_TYPE_LSI_CLASS_NAME,
                )
            ),
            statements = listOf(
                addFetcher(prop.name, LsiNameExpression("idOnlyFetchType"))
            ),
        )
    }

    private fun propWithReferenceFetchType(
        prop: ImmutableFetcherPropMetadata,
        lambda: Boolean,
    ): LsiCallableSpec? {
        if (!prop.supportsReferenceFetchType) {
            return null
        }
        val targetClassName = prop.targetClassName
            ?: error("Fetcher metadata bug: missing targetClassName for reference prop '${prop.name}'")
        val childBlockMetadata = prop.childBlockMetadata
            ?: error("Fetcher metadata bug: missing childBlockMetadata for reference prop '${prop.name}'")
        val parameters = buildList {
            add(
                LsiParameterSpec(
                    name = "fetchType",
                    type = REFERENCE_FETCH_TYPE_LSI_CLASS_NAME,
                )
            )
            if (lambda) {
                add(
                    LsiParameterSpec(
                        name = "childBlock",
                        type = childBlockMetadata.toLsiLambdaTypeName(),
                    )
                )
            } else {
                add(
                    LsiParameterSpec(
                        name = "childFetcher",
                        type = fetcherType(targetClassName),
                    )
                )
            }
        }
        return LsiCallableSpec(
            kind = LsiCallableSpecKind.FUNCTION,
            name = prop.name,
            parameters = parameters,
            statements =
                childFetcherPreparationStatements(
                    lambda = lambda,
                    targetClassName = targetClassName,
                    childBlockMetadata = childBlockMetadata,
                ) + addFetcher(
                    prop.name,
                    LsiNameExpression("childFetcher"),
                    referenceFetchTypeArgument(targetClassName),
                ),
        )
    }

    private fun propWithCode(
        prop: ImmutableFetcherPropMetadata,
        enabled: Boolean,
        lambda: Boolean,
        config: Boolean,
    ): LsiCallableSpec? {
        if (!prop.targetIsEntity && !prop.targetIsEmbeddable) {
            return null
        }
        if (!prop.configurable && config) {
            return null
        }
        val targetClassName = prop.targetClassName
            ?: error("Fetcher metadata bug: missing targetClassName for child prop '${prop.name}'")
        val childBlockMetadata = prop.childBlockMetadata
            ?: error("Fetcher metadata bug: missing childBlockMetadata for child prop '${prop.name}'")
        val fieldConfigBlockMetadata = prop.fieldConfigBlockMetadata
        val cfgTranName =
            when (prop.fieldKind) {
                ImmutableFetcherFieldKind.LIST -> "list"
                ImmutableFetcherFieldKind.REFERENCE -> "reference"
                ImmutableFetcherFieldKind.SIMPLE -> "simple"
            }
        val parameters = buildList {
            if (enabled) {
                add(
                    LsiParameterSpec(
                        name = "enabled",
                        type = BOOLEAN_LSI_CLASS_NAME,
                    )
                )
            }
            if (lambda) {
                if (config) {
                    add(
                        cfgBlockParameter(
                            fieldConfigBlockMetadata
                                ?: error("Fetcher metadata bug: missing fieldConfigBlockMetadata for '${prop.name}'"),
                        )
                    )
                }
                add(
                    LsiParameterSpec(
                        name = "childBlock",
                        type = childBlockMetadata.toLsiLambdaTypeName(),
                    )
                )
            } else {
                add(
                    LsiParameterSpec(
                        name = "childFetcher",
                        type = fetcherType(targetClassName),
                    )
                )
                if (config) {
                    add(
                        cfgBlockParameter(
                            fieldConfigBlockMetadata
                                ?: error("Fetcher metadata bug: missing fieldConfigBlockMetadata for '${prop.name}'"),
                        )
                    )
                }
            }
        }
        return LsiCallableSpec(
            kind = LsiCallableSpecKind.FUNCTION,
            name = prop.name,
            parameters = parameters,
            statements =
                if (enabled) {
                    listOf(
                        LsiIfStatement(
                            condition = LsiBinaryExpression(
                                left = LsiNameExpression("enabled"),
                                operator = LsiBinaryOperator.EQUALS,
                                right = site.addzero.lsi.poet.LsiLiteralExpression(false),
                            ),
                            thenStatements = listOf(removeFetcher(prop.name)),
                            elseStatements = listOf(delegateChildInvocation(prop.name, lambda, config)),
                        )
                    )
                } else {
                    childFetcherPreparationStatements(
                        lambda = lambda,
                        targetClassName = targetClassName,
                        childBlockMetadata = childBlockMetadata,
                    ) + listOf(
                        addFetcher(
                            prop.name,
                            LsiNameExpression("childFetcher"),
                            if (config) {
                                configExpression(cfgTranName)
                            } else {
                                null
                            },
                        )
                    )
                },
        )
    }

    private fun recursiveProp(
        prop: ImmutableFetcherPropMetadata,
        config: Boolean,
    ): LsiCallableSpec? {
        if (!prop.supportsRecursive) {
            return null
        }
        val recursiveConfigBlockMetadata = prop.recursiveConfigBlockMetadata
        val cfgTranName =
            if (prop.isList) {
                "recursiveList"
            } else {
                "recursiveReference"
            }
        return LsiCallableSpec(
            kind = LsiCallableSpecKind.FUNCTION,
            name = prop.name + '*',
            parameters = buildList {
                if (config) {
                    add(
                        cfgBlockParameter(
                            recursiveConfigBlockMetadata
                                ?: error("Fetcher metadata bug: missing recursiveConfigBlockMetadata for '${prop.name}'"),
                        )
                    )
                }
            },
            statements = listOf(
                addRecursion(
                    prop.name,
                    if (config) {
                        configExpression(cfgTranName)
                    } else {
                        LsiNullExpression
                    },
                )
            ),
        )
    }

    private fun cfgBlockParameter(
        callbackMetadata: ImmutableCallbackMetadata,
    ): LsiParameterSpec =
        LsiParameterSpec(
            name = "cfgBlock",
            type = callbackMetadata.toLsiLambdaTypeName(),
        )

    private fun childInvocationArguments(
        lambda: Boolean,
        config: Boolean,
    ): List<site.addzero.lsi.poet.LsiExpression> =
        buildList {
            if (lambda) {
                if (config) {
                    add(LsiNameExpression("cfgBlock"))
                }
                add(LsiNameExpression("childBlock"))
            } else {
                add(LsiNameExpression("childFetcher"))
                if (config) {
                    add(LsiNameExpression("cfgBlock"))
                }
            }
        }

    private fun childFetcherPreparationStatements(
        lambda: Boolean,
        targetClassName: LsiClassName,
        childBlockMetadata: ImmutableCallbackMetadata,
    ): List<LsiStatement> =
        if (lambda) {
            val targetFetcherDslClassName = childBlockMetadata.receiverTypeName as? LsiClassName
                ?: error("Fetcher metadata bug: childBlockMetadata receiver must be LsiClassName for '${type.simpleName}'")
            listOf(
                LsiVariableDeclarationStatement(
                    name = "childDsl",
                    type = targetFetcherDslClassName,
                    initializer = LsiNewExpression(targetFetcherDslClassName),
                ),
                LsiExpressionStatement(
                    LsiCallExpression(
                        receiver = LsiNameExpression("childBlock"),
                        name = "invoke",
                        arguments = listOf(LsiNameExpression("childDsl")),
                    )
                ),
                LsiVariableDeclarationStatement(
                    name = "childFetcher",
                    type = fetcherType(targetClassName),
                    initializer = LsiCallExpression(
                        receiver = LsiNameExpression("childDsl"),
                        name = "internallyGetFetcher",
                    ),
                ),
            )
        } else {
            emptyList()
        }

    private fun updateFetcher(
        functionName: String,
        vararg arguments: site.addzero.lsi.poet.LsiExpression,
    ): LsiAssignmentStatement =
        LsiAssignmentStatement(
            target = LsiNameExpression("_fetcher"),
            expression = LsiCallExpression(
                receiver = LsiNameExpression("_fetcher"),
                name = functionName,
                arguments = arguments.toList(),
            ),
        )

    private fun addFetcher(
        propName: String,
        childFetcher: site.addzero.lsi.poet.LsiExpression? = null,
        configExpression: site.addzero.lsi.poet.LsiExpression? = null,
    ): LsiAssignmentStatement =
        updateFetcher(
            "add",
            *buildList {
                add(site.addzero.lsi.poet.LsiLiteralExpression(propName))
                childFetcher?.let(::add)
                configExpression?.let(::add)
            }.toTypedArray(),
        )

    private fun removeFetcher(propName: String): LsiAssignmentStatement =
        updateFetcher("remove", site.addzero.lsi.poet.LsiLiteralExpression(propName))

    private fun addRecursion(
        propName: String,
        configExpression: site.addzero.lsi.poet.LsiExpression,
    ): LsiAssignmentStatement =
        updateFetcher(
            "addRecursion",
            site.addzero.lsi.poet.LsiLiteralExpression(propName),
            configExpression,
        )

    private fun referenceFetchTypeArgument(
        targetClassName: LsiClassName,
    ): LsiCallExpression =
        LsiCallExpression(
            receiver = LsiTypeExpression(JAVA_FIELD_CONFIG_UTILS_LSI_CLASS_NAME),
            name = "reference",
            typeArguments = listOf(targetClassName),
            arguments = listOf(LsiNameExpression("fetchType")),
        )

    private fun delegateChildInvocation(
        functionName: String,
        lambda: Boolean,
        config: Boolean,
    ): LsiExpressionStatement =
        LsiExpressionStatement(
            LsiCallExpression(
                name = functionName,
                arguments = childInvocationArguments(lambda, config),
            )
        )

    private fun configExpression(
        cfgTranName: String,
    ): site.addzero.lsi.poet.LsiExpression =
        LsiCallExpression(
            receiver = LsiTypeExpression(JAVA_FIELD_CONFIG_UTILS_LSI_CLASS_NAME),
            name = cfgTranName,
            arguments = listOf(LsiNameExpression("cfgBlock")),
        )

    companion object {
        private val BOOLEAN_VALUES = booleanArrayOf(false, true)
    }
}

private val BOOLEAN_LSI_CLASS_NAME = KOTLIN_BOOLEAN_LSI_CLASS_NAME

private fun fetcherType(className: LsiClassName): LsiTypeName =
    FETCHER_LSI_CLASS_NAME.parameterizedBy(className)
