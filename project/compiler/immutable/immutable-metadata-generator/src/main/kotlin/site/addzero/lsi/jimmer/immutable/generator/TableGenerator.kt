package site.addzero.lsi.jimmer.immutable.generator

import site.addzero.lsi.codegen.ABSTRACT_TYPED_TABLE_LSI_CLASS_NAME
import site.addzero.lsi.codegen.BASE_TABLE_LSI_CLASS_NAME
import site.addzero.lsi.codegen.BASE_TABLE_OWNER_LSI_CLASS_NAME
import site.addzero.lsi.codegen.BASE_TABLE_SYMBOLS_LSI_CLASS_NAME
import site.addzero.lsi.codegen.BASE_TABLE_SYMBOL_LSI_CLASS_NAME
import site.addzero.lsi.codegen.DELAYED_OPERATION_LSI_CLASS_NAME
import site.addzero.lsi.codegen.FUNCTION_LSI_CLASS_NAME
import site.addzero.lsi.codegen.JOIN_TYPE_LSI_CLASS_NAME
import site.addzero.lsi.codegen.JAVA_CLASS_LSI_CLASS_NAME
import site.addzero.lsi.codegen.JAVA_STRING_LSI_CLASS_NAME
import site.addzero.lsi.codegen.J_WEAK_JOIN_LAMBDA_FACTORY_LSI_CLASS_NAME
import site.addzero.lsi.codegen.PREDICATE_LSI_CLASS_NAME
import site.addzero.lsi.codegen.TABLE_EX_LSI_CLASS_NAME
import site.addzero.lsi.codegen.TABLE_EX_PROXY_LSI_CLASS_NAME
import site.addzero.lsi.codegen.TABLE_IMPLEMENTOR_LSI_CLASS_NAME
import site.addzero.lsi.codegen.TABLE_LIKE_LSI_CLASS_NAME
import site.addzero.lsi.codegen.TABLE_LSI_CLASS_NAME
import site.addzero.lsi.codegen.TABLE_PROXIES_LSI_CLASS_NAME
import site.addzero.lsi.codegen.WEAK_JOIN_HANDLE_LSI_CLASS_NAME
import site.addzero.lsi.codegen.WEAK_JOIN_LAMBDA_LSI_CLASS_NAME
import site.addzero.lsi.codegen.WEAK_JOIN_LSI_CLASS_NAME
import site.addzero.lsi.codegen.generatedAnnotation
import site.addzero.lsi.codegen.suppressWarningsAllAnnotation
import site.addzero.lsi.jimmer.immutable.metadata.model.ImmutablePropsPropMetadata
import site.addzero.lsi.jimmer.immutable.metadata.model.ImmutablePropsTypeMetadata
import site.addzero.lsi.poet.LsiAnnotationSpec
import site.addzero.lsi.poet.LsiBinaryExpression
import site.addzero.lsi.poet.LsiBinaryOperator
import site.addzero.lsi.poet.LsiCallableSpec
import site.addzero.lsi.poet.LsiCallableSpecKind
import site.addzero.lsi.poet.LsiCallExpression
import site.addzero.lsi.poet.LsiCastExpression
import site.addzero.lsi.poet.LsiConstructorDelegateCall
import site.addzero.lsi.poet.LsiConstructorDelegateKind
import site.addzero.lsi.poet.LsiExpressionStatement
import site.addzero.lsi.poet.LsiFileSpec
import site.addzero.lsi.poet.LsiIfStatement
import site.addzero.lsi.poet.LsiJavaClassExpression
import site.addzero.lsi.poet.LsiLiteralExpression
import site.addzero.lsi.poet.LsiModifier
import site.addzero.lsi.poet.LsiNameExpression
import site.addzero.lsi.poet.LsiNewExpression
import site.addzero.lsi.poet.LsiNullExpression
import site.addzero.lsi.poet.LsiParameterSpec
import site.addzero.lsi.poet.LsiPropertySpec
import site.addzero.lsi.poet.LsiPropertyAccessExpression
import site.addzero.lsi.poet.LsiReturnStatement
import site.addzero.lsi.poet.LsiStatement
import site.addzero.lsi.poet.LsiThisExpression
import site.addzero.lsi.poet.LsiThrowStatement
import site.addzero.lsi.poet.LsiTypeName
import site.addzero.lsi.poet.LsiTypeExpression
import site.addzero.lsi.poet.LsiTypeSpec
import site.addzero.lsi.poet.LsiTypeSpecKind
import site.addzero.lsi.poet.LsiTypeVariableName
import site.addzero.lsi.poet.LsiVariableDeclarationStatement
import site.addzero.lsi.poet.LsiWildcardTypeName

class TableGenerator(
    private val type: ImmutablePropsTypeMetadata,
) {
    init {
        require(type.isEntity) {
            "TableGenerator requires entity metadata: ${type.className.canonicalName}"
        }
    }

    fun generate(): List<LsiFileSpec> =
        listOf(
            LsiFileSpec(
                packageName = type.tableClassName.packageName,
                name = type.tableClassName.simpleName,
                types = listOf(tableType(isTableEx = false)),
            ),
            LsiFileSpec(
                packageName = type.tableExClassName.packageName,
                name = type.tableExClassName.simpleName,
                types = listOf(tableType(isTableEx = true)),
            ),
        )

    private fun tableType(isTableEx: Boolean): LsiTypeSpec {
        val selfClassName = if (isTableEx) type.tableExClassName else type.tableClassName
        return LsiTypeSpec(
            name = selfClassName.simpleName,
            kind = LsiTypeSpecKind.CLASS,
            annotations = listOf(generatedAnnotation(type.className)),
            modifiers = setOf(LsiModifier.PUBLIC),
            superClass =
                if (isTableEx) {
                    type.tableClassName
                } else {
                    ABSTRACT_TYPED_TABLE_LSI_CLASS_NAME.parameterizedBy(type.className)
                },
            superInterfaces =
                if (isTableEx) {
                    listOf(
                        TABLE_EX_PROXY_LSI_CLASS_NAME.parameterizedBy(
                            type.className,
                            type.tableClassName,
                        )
                    )
                } else {
                    listOf(type.propsClassName)
                },
            properties = listOf(instanceField(isTableEx)),
            callables = constructors(isTableEx) + propertyCallables(isTableEx) + commonCallables(isTableEx),
            nestedTypes = if (isTableEx) emptyList() else listOf(remoteType()),
            originatingClassName = type.className,
        )
    }

    private fun instanceField(isTableEx: Boolean): LsiPropertySpec {
        val selfClassName = if (isTableEx) type.tableExClassName else type.tableClassName
        return LsiPropertySpec(
            name = "$",
            type = selfClassName,
            modifiers = setOf(LsiModifier.PUBLIC, LsiModifier.STATIC),
            initializer =
                if (isTableEx) {
                    LsiNewExpression(
                        type = selfClassName,
                        arguments = listOf(
                            tableInstanceExpression(),
                            nullStringExpression(),
                        ),
                    )
                } else {
                    LsiNewExpression(type.tableClassName)
                },
        )
    }

    private fun constructors(isTableEx: Boolean): List<LsiCallableSpec> =
        listOf(
            defaultConstructor(isTableEx),
            delayedConstructor(isTableEx),
            wrapperConstructor(),
            disableJoinConstructor(),
            baseTableOwnerConstructor(),
        )

    private fun defaultConstructor(isTableEx: Boolean): LsiCallableSpec =
        LsiCallableSpec(
            kind = LsiCallableSpecKind.CONSTRUCTOR,
            modifiers = setOf(LsiModifier.PUBLIC),
            delegateCall =
                if (isTableEx) {
                    LsiConstructorDelegateCall(LsiConstructorDelegateKind.SUPER)
                } else {
                    LsiConstructorDelegateCall(
                        kind = LsiConstructorDelegateKind.SUPER,
                        arguments = listOf(LsiJavaClassExpression(type.className)),
                    )
                },
        )

    private fun delayedConstructor(isTableEx: Boolean): LsiCallableSpec =
        LsiCallableSpec(
            kind = LsiCallableSpecKind.CONSTRUCTOR,
            modifiers = setOf(LsiModifier.PUBLIC),
            parameters = listOf(
                LsiParameterSpec(
                    name = "delayedOperation",
                    type = DELAYED_OPERATION_LSI_CLASS_NAME.parameterizedBy(type.className),
                )
            ),
            delegateCall =
                if (isTableEx) {
                    LsiConstructorDelegateCall(
                        kind = LsiConstructorDelegateKind.SUPER,
                        arguments = listOf(LsiNameExpression("delayedOperation")),
                    )
                } else {
                    LsiConstructorDelegateCall(
                        kind = LsiConstructorDelegateKind.SUPER,
                        arguments = listOf(
                            LsiJavaClassExpression(type.className),
                            LsiNameExpression("delayedOperation"),
                        ),
                    )
                },
        )

    private fun wrapperConstructor(): LsiCallableSpec =
        LsiCallableSpec(
            kind = LsiCallableSpecKind.CONSTRUCTOR,
            modifiers = setOf(LsiModifier.PUBLIC),
            parameters = listOf(
                LsiParameterSpec(
                    name = "table",
                    type = TABLE_IMPLEMENTOR_LSI_CLASS_NAME.parameterizedBy(type.className),
                )
            ),
            delegateCall = LsiConstructorDelegateCall(
                kind = LsiConstructorDelegateKind.SUPER,
                arguments = listOf(LsiNameExpression("table")),
            ),
        )

    private fun disableJoinConstructor(): LsiCallableSpec =
        LsiCallableSpec(
            kind = LsiCallableSpecKind.CONSTRUCTOR,
            modifiers = setOf(LsiModifier.PROTECTED),
            parameters = listOf(
                LsiParameterSpec("base", type.tableClassName),
                LsiParameterSpec("joinDisabledReason", JAVA_STRING_LSI_CLASS_NAME),
            ),
            delegateCall = LsiConstructorDelegateCall(
                kind = LsiConstructorDelegateKind.SUPER,
                arguments = listOf(
                    LsiNameExpression("base"),
                    LsiNameExpression("joinDisabledReason"),
                ),
            ),
        )

    private fun baseTableOwnerConstructor(): LsiCallableSpec =
        LsiCallableSpec(
            kind = LsiCallableSpecKind.CONSTRUCTOR,
            modifiers = setOf(LsiModifier.PROTECTED),
            parameters = listOf(
                LsiParameterSpec("base", type.tableClassName),
                LsiParameterSpec("baseTableOwner", BASE_TABLE_OWNER_LSI_CLASS_NAME),
            ),
            delegateCall = LsiConstructorDelegateCall(
                kind = LsiConstructorDelegateKind.SUPER,
                arguments = listOf(
                    LsiNameExpression("base"),
                    LsiNameExpression("baseTableOwner"),
                ),
            ),
        )

    private fun propertyCallables(isTableEx: Boolean): List<LsiCallableSpec> =
        buildList {
            for (prop in type.properties) {
                if ((if (isTableEx) prop.isDslTableEx else prop.isDslTable)) {
                    propertyCallable(prop, isTableEx, withJoinType = false)?.let(::add)
                    propertyCallable(prop, isTableEx, withJoinType = true)?.let(::add)
                }
                existsCallable(prop)?.let(::add)
                associatedIdCallable(prop, isTableEx)?.let(::add)
            }
        }

    private fun propertyCallable(
        prop: ImmutablePropsPropMetadata,
        isTableEx: Boolean,
        withJoinType: Boolean,
    ): LsiCallableSpec? {
        if (withJoinType && !prop.isAssociation) {
            return null
        }
        val returnType = propertyReturnType(prop, isTableEx)
        return LsiCallableSpec(
            kind = LsiCallableSpecKind.FUNCTION,
            name = prop.name,
            modifiers = buildSet {
                add(LsiModifier.PUBLIC)
                if (!isTableEx) {
                    add(LsiModifier.OVERRIDE)
                }
            },
            parameters = listOfNotNull(
                if (withJoinType) {
                    LsiParameterSpec("joinType", JOIN_TYPE_LSI_CLASS_NAME)
                } else {
                    null
                }
            ),
            returnType = returnType,
            statements =
                when {
                    prop.isAssociation -> associationPropertyStatements(prop, returnType, withJoinType)
                    prop.isEmbedded -> listOf(
                        LsiReturnStatement(
                            LsiNewExpression(
                                type = returnType.asClassName("association/embedded table property"),
                                arguments = listOf(propertyGetExpression(prop)),
                            )
                        )
                    )
                    else -> listOf(
                        LsiReturnStatement(propertyGetExpression(prop))
                    )
                },
        )
    }

    private fun associationPropertyStatements(
        prop: ImmutablePropsPropMetadata,
        returnType: LsiTypeName,
        withJoinType: Boolean,
    ): List<LsiStatement> {
        val returnClassName = returnType.asClassName("association table property")
        return listOf(
            beforeJoinStatement(),
            LsiIfStatement(
                condition = rawAvailableCondition(),
                thenStatements = listOf(
                    LsiReturnStatement(
                        LsiNewExpression(
                            type = returnClassName,
                            arguments = listOf(joinImplementorExpression(prop, withJoinType)),
                        )
                    )
                ),
            ),
            LsiReturnStatement(
                LsiNewExpression(
                    type = returnClassName,
                    arguments = listOf(joinOperationExpression(prop, withJoinType)),
                )
            ),
        )
    }

    private fun existsCallable(prop: ImmutablePropsPropMetadata): LsiCallableSpec? {
        if (!prop.isAssociation || !prop.isList) {
            return null
        }
        return LsiCallableSpec(
            kind = LsiCallableSpecKind.FUNCTION,
            name = prop.name,
            modifiers = setOf(LsiModifier.PUBLIC, LsiModifier.OVERRIDE),
            parameters = listOf(
                LsiParameterSpec(
                    name = "block",
                    type = FUNCTION_LSI_CLASS_NAME.parameterizedBy(
                        prop.targetType.toTableExClassName(),
                        PREDICATE_LSI_CLASS_NAME,
                    ),
                )
            ),
            returnType = PREDICATE_LSI_CLASS_NAME,
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

    private fun associatedIdCallable(
        prop: ImmutablePropsPropMetadata,
        isTableEx: Boolean,
    ): LsiCallableSpec? {
        val idPropName = prop.generatedIdPropName ?: return null
        if (prop.isTransient || !prop.isAssociation || prop.isList != isTableEx) {
            return null
        }
        return LsiCallableSpec(
            kind = LsiCallableSpecKind.FUNCTION,
            name = idPropName,
            modifiers = buildSet {
                add(LsiModifier.PUBLIC)
                if (!isTableEx) {
                    add(LsiModifier.OVERRIDE)
                }
            },
            returnType = prop.targetIdType?.toPropExpressionTypeName()
                ?: error("Props metadata bug: missing targetIdType for '${prop.name}'"),
            statements = listOf(
                LsiReturnStatement(associatedIdExpression(prop))
            ),
        )
    }

    private fun commonCallables(isTableEx: Boolean): List<LsiCallableSpec> =
        buildList {
            add(asTableExCallable(isTableEx))
            add(disableJoinCallable(isTableEx))
            add(baseTableOwnerCallable(isTableEx))
            if (isTableEx) {
                addAll(weakJoinCallables())
            }
        }

    private fun asTableExCallable(isTableEx: Boolean): LsiCallableSpec =
        LsiCallableSpec(
            kind = LsiCallableSpecKind.FUNCTION,
            name = "asTableEx",
            modifiers = setOf(LsiModifier.PUBLIC, LsiModifier.OVERRIDE),
            returnType = type.tableExClassName,
            statements = listOf(
                if (isTableEx) {
                    LsiReturnStatement(LsiThisExpression)
                } else {
                    LsiReturnStatement(
                        LsiNewExpression(
                            type = type.tableExClassName,
                            arguments = listOf(
                                LsiThisExpression,
                                nullStringExpression(),
                            ),
                        )
                    )
                }
            ),
        )

    private fun disableJoinCallable(isTableEx: Boolean): LsiCallableSpec {
        val selfClassName = if (isTableEx) type.tableExClassName else type.tableClassName
        return LsiCallableSpec(
            kind = LsiCallableSpecKind.FUNCTION,
            name = "__disableJoin",
            modifiers = setOf(LsiModifier.PUBLIC, LsiModifier.OVERRIDE),
            parameters = listOf(LsiParameterSpec("reason", JAVA_STRING_LSI_CLASS_NAME)),
            returnType = selfClassName,
            statements = listOf(
                LsiReturnStatement(
                    LsiNewExpression(
                        type = selfClassName,
                        arguments = listOf(
                            LsiThisExpression,
                            LsiNameExpression("reason"),
                        ),
                    )
                )
            ),
        )
    }

    private fun baseTableOwnerCallable(isTableEx: Boolean): LsiCallableSpec {
        val selfClassName = if (isTableEx) type.tableExClassName else type.tableClassName
        return LsiCallableSpec(
            kind = LsiCallableSpecKind.FUNCTION,
            name = "__baseTableOwner",
            modifiers = setOf(LsiModifier.PUBLIC, LsiModifier.OVERRIDE),
            parameters = listOf(
                LsiParameterSpec("baseTableOwner", BASE_TABLE_OWNER_LSI_CLASS_NAME)
            ),
            returnType = selfClassName,
            statements = listOf(
                LsiReturnStatement(
                    LsiNewExpression(
                        type = selfClassName,
                        arguments = listOf(
                            LsiThisExpression,
                            LsiNameExpression("baseTableOwner"),
                        ),
                    )
                )
            ),
        )
    }

    private fun weakJoinCallables(): List<LsiCallableSpec> =
        listOf(
            weakJoinByTypeCallable(withJoinType = false),
            weakJoinByTypeCallable(withJoinType = true),
            weakJoinByLambdaCallable(withJoinType = false),
            weakJoinByLambdaCallable(withJoinType = true),
            weakJoinByBaseTableCallable(withJoinType = false),
            weakJoinByBaseTableCallable(withJoinType = true),
        )

    private fun weakJoinByTypeCallable(withJoinType: Boolean): LsiCallableSpec {
        val tt = tableTypeVariable()
        val wj = weakJoinTypeVariable(tt)
        return LsiCallableSpec(
            kind = LsiCallableSpecKind.FUNCTION,
            name = "weakJoin",
            annotations = if (withJoinType) listOf(suppressWarningsAllAnnotation()) else emptyList(),
            modifiers = setOf(LsiModifier.PUBLIC),
            typeVariables = listOf(tt, wj),
            parameters = listOfNotNull(
                LsiParameterSpec(
                    name = "weakJoinType",
                    type = JAVA_CLASS_LSI_CLASS_NAME.parameterizedBy(LsiTypeVariableName("WJ")),
                ),
                if (withJoinType) LsiParameterSpec("joinType", JOIN_TYPE_LSI_CLASS_NAME) else null,
            ),
            returnType = LsiTypeVariableName("TT"),
            statements =
                if (withJoinType) {
                    listOf(
                        beforeJoinStatement(),
                        LsiIfStatement(
                            condition = rawAvailableCondition(),
                            thenStatements = listOf(
                                LsiReturnStatement(
                                    weakJoinResultCast(
                                        tableProxiesCall(
                                            name = "wrap",
                                            expression = rawWeakJoinImplementorExpression(
                                                LsiNameExpression("weakJoinType"),
                                                LsiNameExpression("joinType"),
                                            ),
                                        )
                                    )
                                )
                            ),
                        ),
                        LsiReturnStatement(
                            weakJoinResultCast(
                                tableProxiesCall(
                                    name = "fluent",
                                    expression = weakJoinOperationExpression(
                                        LsiNameExpression("weakJoinType"),
                                        LsiNameExpression("joinType"),
                                    ),
                                )
                            )
                        ),
                    )
                } else {
                    listOf(
                        LsiReturnStatement(
                            LsiCallExpression(
                                name = "weakJoin",
                                arguments = listOf(
                                    LsiNameExpression("weakJoinType"),
                                    joinTypeInnerExpression(),
                                ),
                            )
                        )
                    )
                },
        )
    }

    private fun weakJoinByLambdaCallable(withJoinType: Boolean): LsiCallableSpec {
        val tt = tableTypeVariable()
        return LsiCallableSpec(
            kind = LsiCallableSpecKind.FUNCTION,
            name = "weakJoin",
            annotations = if (withJoinType) listOf(suppressWarningsAllAnnotation()) else emptyList(),
            modifiers = setOf(LsiModifier.PUBLIC),
            typeVariables = listOf(tt),
            parameters = listOfNotNull(
                LsiParameterSpec(
                    name = "targetTableType",
                    type = JAVA_CLASS_LSI_CLASS_NAME.parameterizedBy(LsiTypeVariableName("TT")),
                ),
                if (withJoinType) LsiParameterSpec("joinType", JOIN_TYPE_LSI_CLASS_NAME) else null,
                LsiParameterSpec(
                    name = "weakJoinLambda",
                    type = WEAK_JOIN_LSI_CLASS_NAME.parameterizedBy(type.tableClassName, LsiTypeVariableName("TT")),
                ),
            ),
            returnType = LsiTypeVariableName("TT"),
            statements =
                if (withJoinType) {
                    listOf(
                        beforeJoinStatement(),
                        LsiIfStatement(
                            condition = rawAvailableCondition(),
                            thenStatements = listOf(
                                LsiReturnStatement(
                                    weakJoinResultCast(
                                        tableProxiesCall(
                                            name = "wrap",
                                            expression = rawWeakJoinImplementorExpression(
                                                LsiNameExpression("targetTableType"),
                                                LsiNameExpression("joinType"),
                                                LsiNameExpression("weakJoinLambda"),
                                            ),
                                        )
                                    )
                                )
                            ),
                        ),
                        LsiReturnStatement(
                            weakJoinResultCast(
                                tableProxiesCall(
                                    name = "fluent",
                                    expression = weakJoinOperationExpression(
                                        LsiNameExpression("targetTableType"),
                                        LsiNameExpression("joinType"),
                                        LsiNameExpression("weakJoinLambda"),
                                    ),
                                )
                            )
                        ),
                    )
                } else {
                    listOf(
                        LsiReturnStatement(
                            LsiCallExpression(
                                name = "weakJoin",
                                arguments = listOf(
                                    LsiNameExpression("targetTableType"),
                                    joinTypeInnerExpression(),
                                    LsiNameExpression("weakJoinLambda"),
                                ),
                            )
                        )
                    )
                },
        )
    }

    private fun weakJoinByBaseTableCallable(withJoinType: Boolean): LsiCallableSpec {
        val tt = LsiTypeVariableName("TT", bounds = listOf(BASE_TABLE_LSI_CLASS_NAME))
        val tableLikeWildcard = TABLE_LIKE_LSI_CLASS_NAME.parameterizedBy(LsiWildcardTypeName())
        val weakJoinTableLike = WEAK_JOIN_LSI_CLASS_NAME.parameterizedBy(tableLikeWildcard, tableLikeWildcard)
        val weakJoinWildcard = WEAK_JOIN_LSI_CLASS_NAME.parameterizedBy(LsiWildcardTypeName(), LsiWildcardTypeName())
        return LsiCallableSpec(
            kind = LsiCallableSpecKind.FUNCTION,
            name = "weakJoin",
            modifiers = setOf(LsiModifier.PUBLIC),
            typeVariables = listOf(tt),
            parameters = listOfNotNull(
                LsiParameterSpec("targetBaseTable", LsiTypeVariableName("TT")),
                if (withJoinType) LsiParameterSpec("joinType", JOIN_TYPE_LSI_CLASS_NAME) else null,
                LsiParameterSpec(
                    name = "weakJoinLambda",
                    type = WEAK_JOIN_LSI_CLASS_NAME.parameterizedBy(type.tableClassName, LsiTypeVariableName("TT")),
                ),
            ),
            returnType = LsiTypeVariableName("TT"),
            statements =
                if (!withJoinType) {
                    listOf(
                        LsiReturnStatement(
                            LsiCallExpression(
                                name = "weakJoin",
                                arguments = listOf(
                                    LsiNameExpression("targetBaseTable"),
                                    joinTypeInnerExpression(),
                                    LsiNameExpression("weakJoinLambda"),
                                ),
                            )
                        )
                    )
                } else {
                    listOf(
                        LsiVariableDeclarationStatement(
                            name = "lambda",
                            type = WEAK_JOIN_LAMBDA_LSI_CLASS_NAME,
                            initializer = LsiCallExpression(
                                receiver = LsiTypeExpression(J_WEAK_JOIN_LAMBDA_FACTORY_LSI_CLASS_NAME),
                                name = "get",
                                arguments = listOf(LsiNameExpression("weakJoinLambda")),
                            ),
                        ),
                        LsiVariableDeclarationStatement(
                            name = "handle",
                            type = WEAK_JOIN_HANDLE_LSI_CLASS_NAME,
                            initializer = LsiCallExpression(
                                receiver = LsiTypeExpression(WEAK_JOIN_HANDLE_LSI_CLASS_NAME),
                                name = "of",
                                arguments = listOf(
                                    LsiNameExpression("lambda"),
                                    LsiLiteralExpression(true),
                                    LsiLiteralExpression(true),
                                    LsiCastExpression(
                                        type = weakJoinTableLike,
                                        expression = LsiCastExpression(
                                            type = weakJoinWildcard,
                                            expression = LsiNameExpression("weakJoinLambda"),
                                        ),
                                    ),
                                ),
                            ),
                        ),
                        LsiReturnStatement(
                            weakJoinResultCast(
                                LsiCallExpression(
                                    receiver = LsiTypeExpression(BASE_TABLE_SYMBOLS_LSI_CLASS_NAME),
                                    name = "of",
                                    arguments = listOf(
                                        LsiCastExpression(
                                            type = BASE_TABLE_SYMBOL_LSI_CLASS_NAME,
                                            expression = LsiNameExpression("targetBaseTable"),
                                        ),
                                        LsiThisExpression,
                                        LsiNameExpression("handle"),
                                        LsiNameExpression("joinType"),
                                    ),
                                )
                            )
                        ),
                    )
                },
        )
    }

    private fun remoteType(): LsiTypeSpec =
        LsiTypeSpec(
            name = "Remote",
            kind = LsiTypeSpecKind.CLASS,
            annotations = listOf(generatedAnnotation(type.className)),
            modifiers = setOf(LsiModifier.PUBLIC, LsiModifier.STATIC),
            superClass = ABSTRACT_TYPED_TABLE_LSI_CLASS_NAME.parameterizedBy(type.className),
            callables = remoteConstructors() + listOf(
                remoteIdCallable(),
                remoteAsTableExCallable(),
                remoteDisableJoinCallable(),
                remoteBaseTableOwnerCallable(),
            ),
            originatingClassName = type.className,
        )

    private fun remoteConstructors(): List<LsiCallableSpec> =
        listOf(
            LsiCallableSpec(
                kind = LsiCallableSpecKind.CONSTRUCTOR,
                modifiers = setOf(LsiModifier.PUBLIC),
                parameters = listOf(
                    LsiParameterSpec(
                        name = "delayedOperation",
                        type = DELAYED_OPERATION_LSI_CLASS_NAME.parameterizedBy(type.className),
                    )
                ),
                delegateCall = LsiConstructorDelegateCall(
                    kind = LsiConstructorDelegateKind.SUPER,
                    arguments = listOf(
                        LsiJavaClassExpression(type.className),
                        LsiNameExpression("delayedOperation"),
                    ),
                ),
            ),
            LsiCallableSpec(
                kind = LsiCallableSpecKind.CONSTRUCTOR,
                modifiers = setOf(LsiModifier.PUBLIC),
                parameters = listOf(
                    LsiParameterSpec(
                        name = "table",
                        type = TABLE_IMPLEMENTOR_LSI_CLASS_NAME.parameterizedBy(type.className),
                    )
                ),
                delegateCall = LsiConstructorDelegateCall(
                    kind = LsiConstructorDelegateKind.SUPER,
                    arguments = listOf(LsiNameExpression("table")),
                ),
            ),
            LsiCallableSpec(
                kind = LsiCallableSpecKind.CONSTRUCTOR,
                modifiers = setOf(LsiModifier.PUBLIC),
                parameters = listOf(
                    LsiParameterSpec("base", type.remoteTableClassName),
                    LsiParameterSpec("baseTableOwner", BASE_TABLE_OWNER_LSI_CLASS_NAME),
                ),
                delegateCall = LsiConstructorDelegateCall(
                    kind = LsiConstructorDelegateKind.SUPER,
                    arguments = listOf(
                        LsiNameExpression("base"),
                        LsiNameExpression("baseTableOwner"),
                    ),
                ),
            ),
        )

    private fun remoteIdCallable(): LsiCallableSpec {
        val idPropMetadata = type.idProp
            ?: error("Props metadata bug: entity '${type.className.canonicalName}' must define idProp")
        val idConstantName = idPropConstantName()
        val idReturnType = propertyReturnType(idPropertyMetadata(), isTableEx = false)
        return LsiCallableSpec(
            kind = LsiCallableSpecKind.FUNCTION,
            name = idPropMetadata.name,
            modifiers = setOf(LsiModifier.PUBLIC),
            returnType = idReturnType,
            statements = listOf(
                LsiReturnStatement(
                    LsiCastExpression(
                        type = idReturnType,
                        expression = LsiCallExpression(
                            receiver = LsiThisExpression,
                            name = "get",
                            typeArguments = listOf(idPropMetadata.type.toLsiTypeName(nullableOverride = false)),
                            arguments = listOf(
                                propUnwrapExpression(
                                    idPropertyMetadata().copy(constantName = idConstantName)
                                )
                            ),
                        ),
                    )
                )
            ),
        )
    }

    private fun remoteAsTableExCallable(): LsiCallableSpec =
        LsiCallableSpec(
            kind = LsiCallableSpecKind.FUNCTION,
            name = "asTableEx",
            annotations = listOf(
                LsiAnnotationSpec(type = Deprecated::class.toLsiClassName())
            ),
            modifiers = setOf(LsiModifier.PUBLIC, LsiModifier.OVERRIDE),
            returnType = TABLE_EX_LSI_CLASS_NAME.parameterizedBy(type.className),
            statements = listOf(
                LsiThrowStatement(
                    LsiNewExpression(type = UnsupportedOperationException::class.toLsiClassName())
                )
            ),
        )

    private fun remoteDisableJoinCallable(): LsiCallableSpec =
        LsiCallableSpec(
            kind = LsiCallableSpecKind.FUNCTION,
            name = "__disableJoin",
            modifiers = setOf(LsiModifier.PUBLIC, LsiModifier.OVERRIDE),
            parameters = listOf(LsiParameterSpec("reason", JAVA_STRING_LSI_CLASS_NAME)),
            returnType = type.remoteTableClassName,
            statements = listOf(LsiReturnStatement(LsiThisExpression)),
        )

    private fun remoteBaseTableOwnerCallable(): LsiCallableSpec =
        LsiCallableSpec(
            kind = LsiCallableSpecKind.FUNCTION,
            name = "__baseTableOwner",
            modifiers = setOf(LsiModifier.PUBLIC, LsiModifier.OVERRIDE),
            parameters = listOf(
                LsiParameterSpec("baseTableOwner", BASE_TABLE_OWNER_LSI_CLASS_NAME)
            ),
            returnType = type.remoteTableClassName,
            statements = listOf(
                LsiReturnStatement(
                    LsiNewExpression(
                        type = type.remoteTableClassName,
                        arguments = listOf(LsiThisExpression, LsiNameExpression("baseTableOwner")),
                    )
                )
            ),
        )

    private fun propertyReturnType(
        prop: ImmutablePropsPropMetadata,
        isTableEx: Boolean,
    ): LsiTypeName =
        when {
            prop.isAssociation && prop.isRemote -> prop.targetType.toRemoteTableClassName()
            prop.isAssociation && isTableEx -> prop.targetType.toTableExClassName()
            prop.isAssociation -> prop.targetType.toTableClassName()
            prop.isEmbedded -> prop.type.toPropExpressionClassName()
            else -> prop.type.toPropExpressionTypeName()
        }

    private fun tableTypeVariable(): LsiTypeVariableName =
        LsiTypeVariableName(
            name = "TT",
            bounds = listOf(TABLE_LSI_CLASS_NAME.parameterizedBy(LsiWildcardTypeName())),
        )

    private fun weakJoinTypeVariable(tableTypeVariable: LsiTypeVariableName): LsiTypeVariableName =
        LsiTypeVariableName(
            name = "WJ",
            bounds = listOf(
                WEAK_JOIN_LSI_CLASS_NAME.parameterizedBy(
                    type.tableClassName,
                    tableTypeVariable,
                )
            ),
        )

    private fun idPropConstantName(): String =
        idPropertyMetadata().constantName

    private fun idPropertyMetadata(): ImmutablePropsPropMetadata =
        type.properties.firstOrNull { it.name == type.idProp?.name }
            ?: error("Props metadata bug: missing property metadata for id prop '${type.idProp?.name}'")

    private fun tableInstanceExpression(): LsiPropertyAccessExpression =
        LsiPropertyAccessExpression(
            receiver = LsiTypeExpression(type.tableClassName),
            name = "$",
        )

    private fun nullStringExpression(): LsiCastExpression =
        LsiCastExpression(
            type = JAVA_STRING_LSI_CLASS_NAME.copyNullable(true),
            expression = LsiNullExpression,
        )

    private fun beforeJoinStatement(): LsiExpressionStatement =
        LsiExpressionStatement(
            LsiCallExpression(name = "__beforeJoin")
        )

    private fun rawAvailableCondition(): LsiBinaryExpression =
        LsiBinaryExpression(
            left = LsiNameExpression("raw"),
            operator = LsiBinaryOperator.NOT_EQUALS,
            right = LsiNullExpression,
        )

    private fun joinImplementorExpression(
        prop: ImmutablePropsPropMetadata,
        withJoinType: Boolean,
    ): LsiCallExpression =
        LsiCallExpression(
            receiver = LsiNameExpression("raw"),
            name = "joinImplementor",
            arguments = joinArguments(prop, withJoinType),
        )

    private fun joinOperationExpression(
        prop: ImmutablePropsPropMetadata,
        withJoinType: Boolean,
    ): LsiCallExpression =
        LsiCallExpression(
            name = "joinOperation",
            arguments = joinArguments(prop, withJoinType),
        )

    private fun joinArguments(
        prop: ImmutablePropsPropMetadata,
        withJoinType: Boolean,
    ): List<site.addzero.lsi.poet.LsiExpression> =
        listOfNotNull(
            propUnwrapExpression(prop),
            if (withJoinType) LsiNameExpression("joinType") else null,
        )

    private fun joinTypeInnerExpression(): LsiPropertyAccessExpression =
        LsiPropertyAccessExpression(
            receiver = LsiTypeExpression(JOIN_TYPE_LSI_CLASS_NAME),
            name = "INNER",
        )

    private fun rawWeakJoinImplementorExpression(
        vararg arguments: site.addzero.lsi.poet.LsiExpression,
    ): LsiCallExpression =
        LsiCallExpression(
            receiver = LsiNameExpression("raw"),
            name = "weakJoinImplementor",
            arguments = arguments.toList(),
        )

    private fun weakJoinOperationExpression(
        vararg arguments: site.addzero.lsi.poet.LsiExpression,
    ): LsiCallExpression =
        LsiCallExpression(
            name = "joinOperation",
            arguments = arguments.toList(),
        )

    private fun tableProxiesCall(
        name: String,
        expression: site.addzero.lsi.poet.LsiExpression,
    ): LsiCallExpression =
        LsiCallExpression(
            receiver = LsiTypeExpression(TABLE_PROXIES_LSI_CLASS_NAME),
            name = name,
            arguments = listOf(expression),
        )

    private fun weakJoinResultCast(
        expression: site.addzero.lsi.poet.LsiExpression,
    ): LsiCastExpression =
        LsiCastExpression(
            type = LsiTypeVariableName("TT"),
            expression = expression,
        )

    private fun propertyGetExpression(prop: ImmutablePropsPropMetadata): LsiCallExpression =
        LsiCallExpression(
            name = "__get",
            arguments = listOf(propUnwrapExpression(prop)),
        )

    private fun associatedIdExpression(prop: ImmutablePropsPropMetadata): LsiCallExpression =
        LsiCallExpression(
            name = "__getAssociatedId",
            arguments = listOf(propUnwrapExpression(prop)),
        )

    private fun propUnwrapExpression(prop: ImmutablePropsPropMetadata): LsiCallExpression =
        LsiCallExpression(
            receiver = LsiPropertyAccessExpression(
                receiver = LsiTypeExpression(type.propsClassName),
                name = prop.constantName,
            ),
            name = "unwrap",
        )

    private fun LsiTypeName.asClassName(context: String): site.addzero.lsi.poet.LsiClassName =
        this as? site.addzero.lsi.poet.LsiClassName
            ?: error("Internal bug: expected class return type for $context, got $this")
}
