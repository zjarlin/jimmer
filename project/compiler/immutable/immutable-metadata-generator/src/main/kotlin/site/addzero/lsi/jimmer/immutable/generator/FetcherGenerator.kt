package site.addzero.lsi.jimmer.immutable.generator

import site.addzero.lsi.codegen.ABSTRACT_TYPED_FETCHER_LSI_CLASS_NAME
import site.addzero.lsi.codegen.CONSUMER_LSI_CLASS_NAME
import site.addzero.lsi.codegen.FETCHER
import site.addzero.lsi.codegen.FETCHER_CREATOR_LSI_CLASS_NAME
import site.addzero.lsi.codegen.FETCHER_IMPL_LSI_CLASS_NAME
import site.addzero.lsi.codegen.FETCHER_LSI_CLASS_NAME
import site.addzero.lsi.codegen.FIELD_CONFIG_LSI_CLASS_NAME
import site.addzero.lsi.codegen.ID_ONLY_FETCH_TYPE_LSI_CLASS_NAME
import site.addzero.lsi.codegen.IMMUTABLE_PROP_LSI_CLASS_NAME
import site.addzero.lsi.codegen.KOTLIN_BOOLEAN_LSI_CLASS_NAME as BOOLEAN_LSI_CLASS_NAME
import site.addzero.lsi.codegen.LIST_FIELD_CONFIG_LSI_CLASS_NAME
import site.addzero.lsi.codegen.NEW_CHAIN_LSI_CLASS_NAME
import site.addzero.lsi.codegen.REFERENCE_FIELD_CONFIG_LSI_CLASS_NAME
import site.addzero.lsi.codegen.REFERENCE_FETCH_TYPE_LSI_CLASS_NAME
import site.addzero.lsi.codegen.RECURSIVE_LIST_FIELD_CONFIG_LSI_CLASS_NAME
import site.addzero.lsi.codegen.RECURSIVE_REFERENCE_FIELD_CONFIG_LSI_CLASS_NAME
import site.addzero.lsi.codegen.TABLE_LSI_CLASS_NAME
import site.addzero.lsi.codegen.generatedAnnotation
import site.addzero.lsi.codegen.suppressAllAnnotation
import site.addzero.lsi.jimmer.immutable.metadata.generator.immutableSourceFileSpecs
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
import site.addzero.lsi.poet.LsiExpression
import site.addzero.lsi.poet.LsiExpressionStatement
import site.addzero.lsi.poet.LsiFileSpec
import site.addzero.lsi.poet.LsiJavaClassExpression
import site.addzero.lsi.poet.LsiLambdaExpression
import site.addzero.lsi.poet.LsiLambdaMode
import site.addzero.lsi.poet.LsiModifier
import site.addzero.lsi.poet.LsiNameExpression
import site.addzero.lsi.poet.LsiNewExpression
import site.addzero.lsi.poet.LsiNullExpression
import site.addzero.lsi.poet.LsiParameterSpec
import site.addzero.lsi.poet.LsiPropertyAccessExpression
import site.addzero.lsi.poet.LsiPropertySpec
import site.addzero.lsi.poet.LsiReturnStatement
import site.addzero.lsi.poet.LsiSafeCastExpression
import site.addzero.lsi.poet.LsiStarTypeName
import site.addzero.lsi.poet.LsiTypeName
import site.addzero.lsi.poet.LsiTypeExpression
import site.addzero.lsi.poet.LsiTypeSpec
import site.addzero.lsi.poet.LsiTypeSpecKind
import site.addzero.lsi.poet.LsiVariableDeclarationStatement
import site.addzero.lsi.poet.LsiWildcardTypeName

class FetcherGenerator(
    private val sourcePackageName: String,
    private val sourceFileName: String,
    private val type: ImmutableFetcherTypeMetadata,
) {
    private val fetcherClassName =
        LsiClassName(
            packageName = type.className.packageName,
            simpleNames = listOf("${type.simpleName}$FETCHER"),
        )

    fun generate(mode: ImmutableGenerationMode): List<LsiFileSpec> {
        val outputFileName =
            // 覆盖来源：project/compiler/immutable/jimmer-ksp-immutable/.../FetcherGenerator.generate 的 `file.fileName`
            // 迁移说明：Fetcher 输出拆成 shared core + Kotlin DSL，APT/KSP 共用核心类，DSL 保持在 Kotlin 边界
            "${sourceFileName}$FETCHER"
        return immutableSourceFileSpecs(
            coreFileSpec = coreFileSpec(outputFileName),
            generationMode = mode,
        ) {
            dslFileSpec(outputFileName)
        }
    }

    private fun coreFileSpec(outputFileName: String): LsiFileSpec =
        LsiFileSpec(
            packageName = sourcePackageName,
            name = outputFileName,
            annotations = listOf(
                suppressAllAnnotation(),
                generatedAnnotation(type.className),
            ),
            types = listOf(coreFetcherType()),
        )

    private fun dslFileSpec(outputFileName: String): LsiFileSpec =
        LsiFileSpec(
            packageName = sourcePackageName,
            name = "${outputFileName}Dsl",
            annotations = listOf(
                suppressAllAnnotation(),
                generatedAnnotation(type.className),
            ),
            topLevelCallables = listOf(
                createFun(withBase = false),
                createFun(withBase = true),
            ),
            types = listOf(FetcherDslGenerator(type, fetcherClassName).generate()),
        )

    private fun coreFetcherType(): LsiTypeSpec =
        LsiTypeSpec(
            name = fetcherClassName.simpleName,
            kind = LsiTypeSpecKind.CLASS,
            annotations = listOf(generatedAnnotation(type.className)),
            superClass = ABSTRACT_TYPED_FETCHER_LSI_CLASS_NAME.parameterizedBy(
                type.className,
                fetcherClassName,
            ),
            properties = listOf(rootFetcherProperty()),
            callables = buildList {
                add(rootFetcherFactory())
                add(baseFetcherConstructor())
                add(chainedFetcherConstructor())
                add(configuredFetcherConstructor())
                for (prop in type.properties) {
                    if (prop.isId) {
                        continue
                    }
                    add(simplePropFun(prop))
                    add(simplePropToggleFun(prop))
                    childFetcherFun(prop)?.let(::add)
                    idOnlyFetcherFun(prop)?.let(::add)
                    configuredChildFetcherFun(prop)?.let(::add)
                    referenceFetchTypeFun(prop)?.let(::add)
                    recursiveFetcherFun(prop, withConfig = false)?.let(::add)
                    recursiveFetcherFun(prop, withConfig = true)?.let(::add)
                }
                add(createFetcherByIdOnlyFun())
                add(createFetcherByFieldConfigFun())
            },
            originatingClassName = type.className,
        )

    private fun rootFetcherProperty(): LsiPropertySpec =
        LsiPropertySpec(
            name = "\$",
            type = fetcherClassName,
            modifiers = setOf(LsiModifier.PUBLIC, LsiModifier.STATIC),
            initializer = LsiNewExpression(
                type = fetcherClassName,
                arguments = listOf(LsiNullExpression),
            ),
        )

    private fun rootFetcherFactory(): LsiCallableSpec =
        LsiCallableSpec(
            kind = LsiCallableSpecKind.FUNCTION,
            name = "\$from",
            modifiers = setOf(LsiModifier.PUBLIC, LsiModifier.STATIC),
            parameters = listOf(
                LsiParameterSpec(
                    name = "base",
                    type = fetcherType(type.className),
                )
            ),
            returnType = fetcherClassName,
            statements = listOf(
                LsiVariableDeclarationStatement(
                    name = "typed",
                    type = fetcherClassName.copyNullable(true),
                    initializer = LsiSafeCastExpression(
                        type = fetcherClassName,
                        expression = LsiNameExpression("base"),
                    ),
                ),
                site.addzero.lsi.poet.LsiIfStatement(
                    condition = LsiBinaryExpression(
                        left = LsiNameExpression("typed"),
                        operator = LsiBinaryOperator.NOT_EQUALS,
                        right = LsiNullExpression,
                    ),
                    thenStatements = listOf(
                        LsiReturnStatement(LsiNameExpression("typed"))
                    ),
                ),
                LsiReturnStatement(
                    LsiNewExpression(
                        type = fetcherClassName,
                        arguments = listOf(
                            site.addzero.lsi.poet.LsiCastExpression(
                                type = fetcherImplType(type.className),
                                expression = LsiNameExpression("base"),
                            )
                        ),
                    )
                ),
            ),
        )

    private fun baseFetcherConstructor(): LsiCallableSpec =
        LsiCallableSpec(
            kind = LsiCallableSpecKind.CONSTRUCTOR,
            modifiers = setOf(LsiModifier.PRIVATE),
            parameters = listOf(
                LsiParameterSpec(
                    name = "base",
                    type = fetcherImplType(type.className).copyNullable(true),
                )
            ),
            delegateCall = site.addzero.lsi.poet.LsiConstructorDelegateCall(
                kind = site.addzero.lsi.poet.LsiConstructorDelegateKind.SUPER,
                arguments = listOf(
                    LsiJavaClassExpression(type.className),
                    LsiNameExpression("base"),
                ),
            ),
        )

    private fun chainedFetcherConstructor(): LsiCallableSpec =
        LsiCallableSpec(
            kind = LsiCallableSpecKind.CONSTRUCTOR,
            modifiers = setOf(LsiModifier.PRIVATE),
            parameters = listOf(
                LsiParameterSpec("prev", fetcherClassName),
                LsiParameterSpec("prop", IMMUTABLE_PROP_LSI_CLASS_NAME),
                LsiParameterSpec("negative", BOOLEAN_LSI_CLASS_NAME),
                LsiParameterSpec("idOnlyFetchType", ID_ONLY_FETCH_TYPE_LSI_CLASS_NAME),
            ),
            delegateCall = site.addzero.lsi.poet.LsiConstructorDelegateCall(
                kind = site.addzero.lsi.poet.LsiConstructorDelegateKind.SUPER,
                arguments = listOf(
                    LsiNameExpression("prev"),
                    LsiNameExpression("prop"),
                    LsiNameExpression("negative"),
                    LsiNameExpression("idOnlyFetchType"),
                ),
            ),
        )

    private fun configuredFetcherConstructor(): LsiCallableSpec =
        LsiCallableSpec(
            kind = LsiCallableSpecKind.CONSTRUCTOR,
            modifiers = setOf(LsiModifier.PRIVATE),
            parameters = listOf(
                LsiParameterSpec("prev", fetcherClassName),
                LsiParameterSpec("prop", IMMUTABLE_PROP_LSI_CLASS_NAME),
                LsiParameterSpec("fieldConfig", wildcardFieldConfigType()),
            ),
            delegateCall = site.addzero.lsi.poet.LsiConstructorDelegateCall(
                kind = site.addzero.lsi.poet.LsiConstructorDelegateKind.SUPER,
                arguments = listOf(
                    LsiNameExpression("prev"),
                    LsiNameExpression("prop"),
                    LsiNameExpression("fieldConfig"),
                ),
            ),
        )

    private fun createFun(withBase: Boolean): LsiCallableSpec {
        val fetcherType = fetcherType(type.className)
        val parameters = buildList {
            if (withBase) {
                add(
                    LsiParameterSpec(
                        name = "base",
                        type = fetcherType.copyNullable(true),
                    )
                )
            }
            add(
                LsiParameterSpec(
                    name = "block",
                    type = type.byBlockMetadata.toLsiLambdaTypeName(),
                )
            )
        }
        return LsiCallableSpec(
            kind = LsiCallableSpecKind.FUNCTION,
            name = "by",
            receiverType = fetcherCreatorType(type.className),
            annotations = listOf(generatedAnnotation(type.className)),
            parameters = parameters,
            returnType = fetcherType,
            statements = createStatements(withBase),
        )
    }

    private fun simplePropFun(prop: ImmutableFetcherPropMetadata): LsiCallableSpec =
        LsiCallableSpec(
            kind = LsiCallableSpecKind.FUNCTION,
            name = prop.name,
            annotations = listOf(newChainAnnotation()),
            returnType = fetcherClassName,
            statements = listOf(
                LsiReturnStatement(
                    LsiCallExpression(
                        name = "add",
                        arguments = listOf(site.addzero.lsi.poet.LsiLiteralExpression(prop.name)),
                    )
                )
            ),
        )

    private fun simplePropToggleFun(prop: ImmutableFetcherPropMetadata): LsiCallableSpec =
        LsiCallableSpec(
            kind = LsiCallableSpecKind.FUNCTION,
            name = prop.name,
            annotations = listOf(newChainAnnotation()),
            parameters = listOf(
                LsiParameterSpec("enabled", BOOLEAN_LSI_CLASS_NAME),
            ),
            returnType = fetcherClassName,
            statements = listOf(
                site.addzero.lsi.poet.LsiIfStatement(
                    condition = LsiNameExpression("enabled"),
                    thenStatements = listOf(
                        LsiReturnStatement(
                            LsiCallExpression(
                                name = "add",
                                arguments = listOf(site.addzero.lsi.poet.LsiLiteralExpression(prop.name)),
                            )
                        )
                    ),
                    elseStatements = listOf(
                        LsiReturnStatement(
                            LsiCallExpression(
                                name = "remove",
                                arguments = listOf(site.addzero.lsi.poet.LsiLiteralExpression(prop.name)),
                            )
                        )
                    ),
                )
            ),
        )

    private fun childFetcherFun(prop: ImmutableFetcherPropMetadata): LsiCallableSpec? {
        if (!prop.targetIsEntity && !prop.targetIsEmbeddable) {
            return null
        }
        val targetClassName = prop.targetClassName
            ?: error("Fetcher metadata bug: missing targetClassName for child prop '${prop.name}'")
        return LsiCallableSpec(
            kind = LsiCallableSpecKind.FUNCTION,
            name = prop.name,
            annotations = listOf(newChainAnnotation()),
            parameters = listOf(
                LsiParameterSpec(
                    name = "childFetcher",
                    type = fetcherType(targetClassName),
                )
            ),
            returnType = fetcherClassName,
            statements = listOf(
                LsiReturnStatement(
                    LsiCallExpression(
                        name = "add",
                        arguments = listOf(
                            site.addzero.lsi.poet.LsiLiteralExpression(prop.name),
                            LsiNameExpression("childFetcher"),
                        ),
                    )
                )
            ),
        )
    }

    private fun idOnlyFetcherFun(prop: ImmutableFetcherPropMetadata): LsiCallableSpec? {
        if (!prop.supportsIdOnlyFetchType) {
            return null
        }
        return LsiCallableSpec(
            kind = LsiCallableSpecKind.FUNCTION,
            name = prop.name,
            annotations = listOf(newChainAnnotation()),
            parameters = listOf(
                LsiParameterSpec("idOnlyFetchType", ID_ONLY_FETCH_TYPE_LSI_CLASS_NAME),
            ),
            returnType = fetcherClassName,
            statements = listOf(
                LsiReturnStatement(
                    LsiCallExpression(
                        name = "add",
                        arguments = listOf(
                            site.addzero.lsi.poet.LsiLiteralExpression(prop.name),
                            LsiNameExpression("idOnlyFetchType"),
                        ),
                    )
                )
            ),
        )
    }

    private fun configuredChildFetcherFun(prop: ImmutableFetcherPropMetadata): LsiCallableSpec? {
        if (!prop.configurable) {
            return null
        }
        val targetClassName = prop.targetClassName
            ?: error("Fetcher metadata bug: missing targetClassName for configured prop '${prop.name}'")
        return LsiCallableSpec(
            kind = LsiCallableSpecKind.FUNCTION,
            name = prop.name,
            annotations = listOf(newChainAnnotation()),
            parameters = listOf(
                LsiParameterSpec(
                    name = "childFetcher",
                    type = fetcherType(targetClassName),
                ),
                LsiParameterSpec(
                    name = "fieldConfig",
                    type = configuredFieldConfigConsumerType(prop, recursive = false),
                ),
            ),
            returnType = fetcherClassName,
            statements = listOf(
                LsiReturnStatement(
                    LsiCallExpression(
                        name = "add",
                        arguments = listOf(
                            site.addzero.lsi.poet.LsiLiteralExpression(prop.name),
                            LsiNameExpression("childFetcher"),
                            LsiNameExpression("fieldConfig"),
                        ),
                    )
                )
            ),
        )
    }

    private fun referenceFetchTypeFun(prop: ImmutableFetcherPropMetadata): LsiCallableSpec? {
        if (!prop.supportsReferenceFetchType || !prop.configurable) {
            return null
        }
        val targetClassName = prop.targetClassName
            ?: error("Fetcher metadata bug: missing targetClassName for reference prop '${prop.name}'")
        return LsiCallableSpec(
            kind = LsiCallableSpecKind.FUNCTION,
            name = prop.name,
            annotations = listOf(newChainAnnotation()),
            parameters = listOf(
                LsiParameterSpec("fetchType", REFERENCE_FETCH_TYPE_LSI_CLASS_NAME),
                LsiParameterSpec(
                    name = "childFetcher",
                    type = fetcherType(targetClassName),
                ),
            ),
            returnType = fetcherClassName,
            statements = listOf(
                LsiReturnStatement(
                    LsiCallExpression(
                        name = prop.name,
                        arguments = listOf(
                            LsiNameExpression("childFetcher"),
                            LsiLambdaExpression(
                                mode = LsiLambdaMode.BLOCK,
                                parameterNames = listOf("cfg"),
                                statements = listOf(
                                    LsiExpressionStatement(
                                        LsiCallExpression(
                                            receiver = LsiNameExpression("cfg"),
                                            name = "fetchType",
                                            arguments = listOf(LsiNameExpression("fetchType")),
                                        )
                                    )
                                ),
                            ),
                        ),
                    )
                )
            ),
        )
    }

    private fun recursiveFetcherFun(
        prop: ImmutableFetcherPropMetadata,
        withConfig: Boolean,
    ): LsiCallableSpec? {
        if (!prop.supportsRecursive || !prop.configurable) {
            return null
        }
        return LsiCallableSpec(
            kind = LsiCallableSpecKind.FUNCTION,
            name = prop.name.recursiveFetcherMethodName(),
            annotations = listOf(newChainAnnotation()),
            parameters =
                if (withConfig) {
                    listOf(
                        LsiParameterSpec(
                            name = "fieldConfig",
                            type = configuredFieldConfigConsumerType(prop, recursive = true),
                        )
                    )
                } else {
                    emptyList()
                },
            returnType = fetcherClassName,
            statements = listOf(
                LsiReturnStatement(
                    LsiCallExpression(
                        name = "addRecursion",
                        arguments = listOf(
                            site.addzero.lsi.poet.LsiLiteralExpression(prop.name),
                            if (withConfig) {
                                LsiNameExpression("fieldConfig")
                            } else {
                                LsiNullExpression
                            },
                        ),
                    )
                )
            ),
        )
    }

    private fun createFetcherByIdOnlyFun(): LsiCallableSpec =
        LsiCallableSpec(
            kind = LsiCallableSpecKind.FUNCTION,
            name = "createFetcher",
            modifiers = setOf(LsiModifier.PROTECTED, LsiModifier.OVERRIDE),
            parameters = listOf(
                LsiParameterSpec("prop", IMMUTABLE_PROP_LSI_CLASS_NAME),
                LsiParameterSpec("negative", BOOLEAN_LSI_CLASS_NAME),
                LsiParameterSpec("idOnlyFetchType", ID_ONLY_FETCH_TYPE_LSI_CLASS_NAME),
            ),
            returnType = fetcherClassName,
            statements = listOf(
                LsiReturnStatement(
                    LsiNewExpression(
                        type = fetcherClassName,
                        arguments = listOf(
                            site.addzero.lsi.poet.LsiThisExpression,
                            LsiNameExpression("prop"),
                            LsiNameExpression("negative"),
                            LsiNameExpression("idOnlyFetchType"),
                        ),
                    )
                )
            ),
        )

    private fun createFetcherByFieldConfigFun(): LsiCallableSpec =
        LsiCallableSpec(
            kind = LsiCallableSpecKind.FUNCTION,
            name = "createFetcher",
            modifiers = setOf(LsiModifier.PROTECTED, LsiModifier.OVERRIDE),
            parameters = listOf(
                LsiParameterSpec("prop", IMMUTABLE_PROP_LSI_CLASS_NAME),
                LsiParameterSpec("fieldConfig", wildcardFieldConfigType()),
            ),
            returnType = fetcherClassName,
            statements = listOf(
                LsiReturnStatement(
                    LsiNewExpression(
                        type = fetcherClassName,
                        arguments = listOf(
                            site.addzero.lsi.poet.LsiThisExpression,
                            LsiNameExpression("prop"),
                            LsiNameExpression("fieldConfig"),
                        ),
                    )
                )
            ),
        )

    private fun createStatements(withBase: Boolean) = buildList {
        add(
            LsiVariableDeclarationStatement(
                name = "dsl",
                type = type.fetcherDslClassName,
                mutable = withBase,
                initializer = fetcherDslInstance(rootFetcherExpression()),
            )
        )
        if (withBase) {
            add(
                site.addzero.lsi.poet.LsiIfStatement(
                    condition = LsiBinaryExpression(
                        left = LsiNameExpression("base"),
                        operator = LsiBinaryOperator.NOT_EQUALS,
                        right = LsiNullExpression,
                    ),
                    thenStatements = listOf(
                        LsiAssignmentStatement(
                            target = LsiNameExpression("dsl"),
                            expression = fetcherDslInstance(LsiNameExpression("base")),
                        )
                    ),
                )
            )
        }
        add(
            LsiExpressionStatement(
                LsiCallExpression(
                    receiver = LsiNameExpression("block"),
                    name = "invoke",
                    arguments = listOf(LsiNameExpression("dsl")),
                )
            )
        )
        add(
            LsiReturnStatement(
                LsiCallExpression(
                    receiver = LsiNameExpression("dsl"),
                    name = "internallyGetFetcher",
                )
            )
        )
    }

    private fun fetcherDslInstance(fetcherExpression: LsiExpression): LsiNewExpression =
        LsiNewExpression(
            type = type.fetcherDslClassName,
            arguments = listOf(fetcherExpression),
        )

    private fun rootFetcherExpression(): LsiExpression =
        LsiPropertyAccessExpression(
            receiver = LsiTypeExpression(fetcherClassName),
            name = "\$",
        )

    private fun configuredFieldConfigConsumerType(
        prop: ImmutableFetcherPropMetadata,
        recursive: Boolean,
    ): LsiTypeName {
        val targetClassName = prop.targetClassName
            ?: error("Fetcher metadata bug: missing targetClassName for configured prop '${prop.name}'")
        val targetTableClassName = prop.targetTableClassName
            ?: error("Fetcher metadata bug: missing targetTableClassName for configured prop '${prop.name}'")
        val configType =
            when {
                recursive && prop.fieldKind == ImmutableFetcherFieldKind.LIST ->
                    RECURSIVE_LIST_FIELD_CONFIG_LSI_CLASS_NAME
                recursive ->
                    RECURSIVE_REFERENCE_FIELD_CONFIG_LSI_CLASS_NAME
                prop.fieldKind == ImmutableFetcherFieldKind.LIST ->
                    LIST_FIELD_CONFIG_LSI_CLASS_NAME
                prop.fieldKind == ImmutableFetcherFieldKind.REFERENCE ->
                    REFERENCE_FIELD_CONFIG_LSI_CLASS_NAME
                else ->
                    FIELD_CONFIG_LSI_CLASS_NAME
            }.parameterizedBy(targetClassName, targetTableClassName)
        return CONSUMER_LSI_CLASS_NAME.parameterizedBy(
            LsiWildcardTypeName(producerTypes = listOf(configType))
        )
    }

    private fun wildcardFieldConfigType(): LsiTypeName =
        FIELD_CONFIG_LSI_CLASS_NAME.parameterizedBy(
            LsiStarTypeName,
            LsiWildcardTypeName(
                producerTypes = listOf(
                    TABLE_LSI_CLASS_NAME.parameterizedBy(LsiStarTypeName)
                )
            ),
        )

    private fun newChainAnnotation(): LsiAnnotationSpec =
        LsiAnnotationSpec(type = NEW_CHAIN_LSI_CLASS_NAME)
}

private fun fetcherType(className: LsiClassName): LsiTypeName =
    FETCHER_LSI_CLASS_NAME.parameterizedBy(className)

private fun fetcherCreatorType(className: LsiClassName): LsiTypeName =
    FETCHER_CREATOR_LSI_CLASS_NAME.parameterizedBy(className)

private fun fetcherImplType(className: LsiClassName): LsiTypeName =
    FETCHER_IMPL_LSI_CLASS_NAME.parameterizedBy(className)

private fun String.recursiveFetcherMethodName(): String =
    "recursive" + replaceFirstChar { char ->
        if (char.isLowerCase()) {
            char.titlecase()
        } else {
            char.toString()
        }
    }
