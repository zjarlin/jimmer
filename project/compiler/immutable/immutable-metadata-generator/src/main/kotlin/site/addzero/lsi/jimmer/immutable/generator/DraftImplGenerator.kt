package site.addzero.lsi.jimmer.immutable.generator

import site.addzero.lsi.anno.get
import site.addzero.lsi.codegen.CIRCULAR_REFERENCE_EXCEPTION_LSI_CLASS_NAME
import site.addzero.lsi.codegen.DRAFT_CONTEXT_LSI_CLASS_NAME
import site.addzero.lsi.codegen.DRAFT_FIELD_EMAIL_PATTERN
import site.addzero.lsi.codegen.DRAFT_IMPL
import site.addzero.lsi.codegen.DRAFT_SPI_LSI_CLASS_NAME
import site.addzero.lsi.codegen.EMAIL_PATTERN
import site.addzero.lsi.codegen.FROZEN_EXCEPTION_MESSAGE
import site.addzero.lsi.codegen.IMMUTABLE_OBJECTS_LSI_CLASS_NAME
import site.addzero.lsi.codegen.JAVA_PATTERN_LSI_CLASS_NAME
import site.addzero.lsi.codegen.JAVA_ARRAY_LIST_LSI_CLASS_NAME as ARRAY_LIST_LSI_CLASS_NAME
import site.addzero.lsi.codegen.JAVA_ILLEGAL_ARGUMENT_EXCEPTION_LSI_CLASS_NAME as ILLEGAL_ARGUMENT_EXCEPTION_LSI_CLASS_NAME
import site.addzero.lsi.codegen.JAVA_ILLEGAL_STATE_EXCEPTION_LSI_CLASS_NAME as ILLEGAL_STATE_EXCEPTION_LSI_CLASS_NAME
import site.addzero.lsi.codegen.JacksonTypes
import site.addzero.lsi.codegen.KOTLIN_ANY_LSI_CLASS_NAME as ANY_LSI_CLASS_NAME
import site.addzero.lsi.codegen.KOTLIN_BOOLEAN_LSI_CLASS_NAME as BOOLEAN_LSI_CLASS_NAME
import site.addzero.lsi.codegen.KOTLIN_INT_LSI_CLASS_NAME as INT_LSI_CLASS_NAME
import site.addzero.lsi.codegen.KOTLIN_STRING_LSI_CLASS_NAME as STRING_LSI_CLASS_NAME
import site.addzero.lsi.codegen.MUTABLE_ID_VIEW_LSI_CLASS_NAME
import site.addzero.lsi.codegen.NON_SHARED_LIST_LSI_CLASS_NAME
import site.addzero.lsi.codegen.PROP_ID_LSI_CLASS_NAME
import site.addzero.lsi.codegen.VALIDATOR_LSI_CLASS_NAME
import site.addzero.lsi.codegen.VISIBILITY_LSI_CLASS_NAME
import site.addzero.lsi.codegen.generatedAnnotation
import site.addzero.lsi.codegen.regexpPatternFieldName
import site.addzero.lsi.codegen.validatorFieldName
import site.addzero.lsi.jimmer.immutable.metadata.model.ImmutableAssociatedIdMetadata
import site.addzero.lsi.poet.LsiAssignmentStatement
import site.addzero.lsi.poet.LsiBinaryExpression
import site.addzero.lsi.poet.LsiBinaryOperator
import site.addzero.lsi.poet.LsiCallableSpec
import site.addzero.lsi.poet.LsiCallableSpecKind
import site.addzero.lsi.poet.LsiCallExpression
import site.addzero.lsi.poet.LsiCastExpression
import site.addzero.lsi.poet.LsiClassName
import site.addzero.lsi.poet.LsiCollectionElementExpression
import site.addzero.lsi.poet.LsiCollectionSizeExpression
import site.addzero.lsi.poet.LsiCodeBlock
import site.addzero.lsi.poet.LsiCodeExpression
import site.addzero.lsi.poet.LsiExpression
import site.addzero.lsi.poet.LsiExpressionStatement
import site.addzero.lsi.poet.LsiForRangeStatement
import site.addzero.lsi.poet.LsiJavaClassExpression
import site.addzero.lsi.poet.LsiIfStatement
import site.addzero.lsi.poet.LsiLiteralExpression
import site.addzero.lsi.poet.LsiMakeIdOnlyExpression
import site.addzero.lsi.poet.LsiModifier
import site.addzero.lsi.poet.LsiNameExpression
import site.addzero.lsi.poet.LsiNewExpression
import site.addzero.lsi.poet.LsiNullExpression
import site.addzero.lsi.poet.LsiParameterSpec
import site.addzero.lsi.poet.LsiParameterizedTypeName
import site.addzero.lsi.poet.LsiPropertyAccessExpression
import site.addzero.lsi.poet.LsiPropertyGetExpression
import site.addzero.lsi.poet.LsiPropertySetStatement
import site.addzero.lsi.poet.LsiPropertySpec
import site.addzero.lsi.poet.LsiReturnStatement
import site.addzero.lsi.poet.LsiStatement
import site.addzero.lsi.poet.LsiThisExpression
import site.addzero.lsi.poet.LsiTypeName
import site.addzero.lsi.poet.LsiTypeExpression
import site.addzero.lsi.poet.LsiTypeSpec
import site.addzero.lsi.poet.LsiTypeSpecKind
import site.addzero.lsi.poet.LsiThrowStatement
import site.addzero.lsi.poet.LsiTryStatement
import site.addzero.lsi.poet.LsiVariableDeclarationStatement
import site.addzero.lsi.poet.LsiWhenCase
import site.addzero.lsi.poet.LsiWhenStatement

private const val SIMPLE_DRAFT_MESSAGE =
    "The current draft object is simple draft which does not support converting nested object to nested draft"
private const val BUILDER_DRAFT_MESSAGE =
    "Internal bug, draft for builder must have `__modified`"
private const val RESOLVED_DRAFT_MESSAGE =
    "Internal bug, resolved draft must produce immutable object"

private data class DraftObjectBinding(
    val statements: List<LsiStatement>,
    val expression: LsiExpression,
)

internal class DraftImplGenerator(
    private val jacksonTypes: JacksonTypes,
    private val type: ImmutableDraftImplTypeMetadata,
) {

    fun generate(): LsiTypeSpec =
        LsiTypeSpec(
            name = DRAFT_IMPL,
            kind = LsiTypeSpecKind.CLASS,
            annotations = listOf(generatedAnnotation(type.className)),
            modifiers = setOf(LsiModifier.INTERNAL),
            superInterfaces = listOf(
                type.draftProducerImplementorClassName,
                type.draftClassName,
                DRAFT_SPI_LSI_CLASS_NAME,
            ),
            properties = buildProperties(),
            callables = buildCallables(),
        )

    private fun buildProperties(): List<LsiPropertySpec> =
        buildList {
            addAll(baseProperties())
            for (member in type.members) {
                add(property(member.property))
                addAssociatedIdProperty(member.associatedId)?.let(::add)
            }
            addAll(companionProperties())
        }

    private fun buildCallables(): List<LsiCallableSpec> =
        buildList {
            add(primaryConstructor())
            add(isLoadedCallable(PropertyDispatchArgKind.PROP_ID))
            add(isLoadedCallable(PropertyDispatchArgKind.PROP_NAME))
            add(isVisibleCallable(PropertyDispatchArgKind.PROP_ID))
            add(isVisibleCallable(PropertyDispatchArgKind.PROP_NAME))
            add(hashCodeCallable())
            add(parameterizedHashCodeCallable())
            add(equalsCallable())
            add(parameterizedEqualsCallable())
            add(toStringCallable())
            for (member in type.members) {
                member.propFun?.let { add(propFun(it)) }
                member.propRefFun?.let { add(propRefFun(it)) }
            }
            add(unloadCallable(PropertyDispatchArgKind.PROP_ID))
            add(unloadCallable(PropertyDispatchArgKind.PROP_NAME))
            add(setCallable(PropertyDispatchArgKind.PROP_ID))
            add(setCallable(PropertyDispatchArgKind.PROP_NAME))
            add(showCallable(PropertyDispatchArgKind.PROP_ID))
            add(showCallable(PropertyDispatchArgKind.PROP_NAME))
            add(draftContextCallable())
            add(resolveCallable())
            add(isResolvedCallable())
            add(contextCallable())
            add(unwrapCallable())
        }

    private fun primaryConstructor(): LsiCallableSpec =
        LsiCallableSpec(
            kind = LsiCallableSpecKind.CONSTRUCTOR,
            primary = true,
            parameters = listOf(
                LsiParameterSpec("ctx", DRAFT_CONTEXT_LSI_CLASS_NAME.copyNullable(true)),
                LsiParameterSpec("base", type.className.copyNullable(true)),
            ),
            statements = listOf(
                LsiIfStatement(
                    condition = LsiBinaryExpression(
                        left = LsiNameExpression("base"),
                        operator = LsiBinaryOperator.EQUALS,
                        right = LsiNullExpression,
                    ),
                    thenStatements = listOf(
                        LsiAssignmentStatement(
                            target = LsiNameExpression("__modified"),
                            expression = LsiNewExpression(type.draftProducerImplClassName),
                        )
                    ),
                    elseStatements = listOf(
                        LsiAssignmentStatement(
                            target = LsiNameExpression("__modified"),
                            expression = LsiNullExpression,
                        )
                    ),
                )
            ),
        )

    private fun baseProperties(): List<LsiPropertySpec> =
        listOf(
            LsiPropertySpec(
                name = "__ctx",
                type = DRAFT_CONTEXT_LSI_CLASS_NAME.copyNullable(true),
                modifiers = setOf(LsiModifier.PRIVATE),
                initializer = LsiNameExpression("ctx"),
            ),
            LsiPropertySpec(
                name = "__base",
                type = type.draftProducerImplClassName.copyNullable(true),
                modifiers = setOf(LsiModifier.PRIVATE),
                initializer = LsiCastExpression(
                    type.draftProducerImplClassName.copyNullable(true),
                    LsiNameExpression("base")
                ),
            ),
            LsiPropertySpec(
                name = "__modified",
                type = type.draftProducerImplClassName.copyNullable(true),
                modifiers = setOf(LsiModifier.PRIVATE),
                mutable = true,
                initializer = LsiNullExpression,
            ),
            LsiPropertySpec(
                name = "__resolving",
                type = BOOLEAN_LSI_CLASS_NAME,
                modifiers = setOf(LsiModifier.PRIVATE),
                mutable = true,
                initializer = LsiLiteralExpression(false),
            ),
            LsiPropertySpec(
                name = "__resolved",
                type = type.className.copyNullable(true),
                modifiers = setOf(LsiModifier.PRIVATE),
                mutable = true,
                initializer = LsiLiteralExpression(null),
            ),
        )

    private fun property(prop: ImmutableDraftImplPropertyMetadata): LsiPropertySpec =
        LsiPropertySpec(
            name = prop.name,
            type = prop.typeName,
            modifiers = setOf(LsiModifier.OVERRIDE),
            mutable = prop.isMutable,
            getterStatements = getterStatements(prop),
            setterStatements =
                if (prop.isMutable) {
                    setterStatements(prop)
                } else {
                    emptyList()
                },
        )

    private fun getterStatements(prop: ImmutableDraftImplPropertyMetadata): List<LsiStatement> =
        when (prop.getterKind) {
            ImmutableDraftImplPropertyGetterKind.ID_VIEW_LIST ->
                listOf(
                    LsiReturnStatement(
                        LsiNewExpression(
                            type = MUTABLE_ID_VIEW_LSI_CLASS_NAME,
                            arguments = listOf(
                                LsiPropertyAccessExpression(
                                    receiver = LsiTypeExpression(
                                        prop.idViewBaseTargetProducerClassName
                                            ?: error("Internal bug: missing id-view target producer for ${prop.name}"),
                                    ),
                                    name = "type",
                                ),
                                semanticPropertyGet(
                                    receiver = LsiThisExpression,
                                    name = prop.idViewBaseName
                                        ?: error("Internal bug: missing id-view base name for ${prop.name}"),
                                    type = prop.idViewBaseTypeName
                                        ?: error("Internal bug: missing id-view base type for ${prop.name}"),
                                ),
                            ),
                        )
                    )
                )

            ImmutableDraftImplPropertyGetterKind.DRAFT_LIST -> {
                val current = bindCurrentDraftObject("__currentDraftList")
                current.statements + listOf(
                    LsiReturnStatement(
                        LsiCallExpression(
                            receiver = LsiCallExpression(name = "__ctx"),
                            name = "toDraftList",
                            arguments = listOf(
                                semanticPropertyGet(current.expression, prop.name, prop.typeName),
                                LsiJavaClassExpression(
                                    prop.draftListElementTypeName
                                        ?: error("Internal bug: missing draft-list element type for ${prop.name}")
                                ),
                                LsiLiteralExpression(prop.draftListAssociation),
                            ),
                        )
                    )
                )
            }

            ImmutableDraftImplPropertyGetterKind.DRAFT_OBJECT -> {
                val current = bindCurrentDraftObject("__currentDraftObject")
                current.statements + listOf(
                    LsiReturnStatement(
                        LsiCallExpression(
                            receiver = LsiCallExpression(name = "__ctx"),
                            name = "toDraftObject",
                            arguments = listOf(semanticPropertyGet(current.expression, prop.name, prop.typeName)),
                        )
                    )
                )
            }

            ImmutableDraftImplPropertyGetterKind.PASSTHROUGH -> {
                val current = bindCurrentDraftObject("__currentValue")
                current.statements + listOf(
                    LsiReturnStatement(semanticPropertyGet(current.expression, prop.name, prop.typeName))
                )
            }
        }

    private fun semanticPropertyGet(
        receiver: LsiExpression,
        name: String,
        type: LsiTypeName,
    ): LsiPropertyGetExpression =
        LsiPropertyGetExpression(
            receiver = receiver,
            name = name,
            type = type,
        )

    private fun setterStatements(prop: ImmutableDraftImplPropertyMetadata): List<LsiStatement> =
        buildList {
            add(frozenGuardStatement())
            when (prop.setterKind) {
                ImmutableDraftImplPropertySetterKind.ID_VIEW_TRANSFORM ->
                    addAll(idViewTransformSetterStatements(prop))

                ImmutableDraftImplPropertySetterKind.ID_VIEW_DIRECT ->
                    addAll(idViewDirectSetterStatements(prop))

                ImmutableDraftImplPropertySetterKind.STANDARD -> {
                    addAll(
                        ValidationGenerator(
                            prop.validationPropMetadata
                                ?: error("Internal bug: missing validation metadata for ${prop.name}"),
                        ).generate()
                    )
                    val modified = bindMutableDraftObject("__tmpModified")
                    addAll(modified.statements)
                    if (prop.copyToNonSharedList) {
                        add(
                            LsiAssignmentStatement(
                                target = LsiPropertyAccessExpression(
                                    modified.expression,
                                    prop.modifiedValueFieldName
                                        ?: error("Internal bug: missing modified value field for ${prop.name}"),
                                ),
                                expression = LsiCallExpression(
                                    receiver = LsiTypeExpression(NON_SHARED_LIST_LSI_CLASS_NAME),
                                    name = "of",
                                    arguments = listOf(
                                        LsiPropertyAccessExpression(
                                            modified.expression,
                                            prop.modifiedValueFieldName,
                                        ),
                                        LsiNameExpression("value"),
                                    ),
                                ),
                            )
                        )
                    } else {
                        add(
                            LsiAssignmentStatement(
                                target = LsiPropertyAccessExpression(
                                    modified.expression,
                                    prop.modifiedValueFieldName
                                        ?: error("Internal bug: missing modified value field for ${prop.name}"),
                                ),
                                expression = LsiNameExpression("value"),
                            )
                        )
                    }
                    prop.modifiedLoadedFieldName?.let {
                        add(
                            LsiAssignmentStatement(
                                target = LsiPropertyAccessExpression(modified.expression, it),
                                expression = LsiLiteralExpression(true),
                            )
                        )
                    }
                }

                ImmutableDraftImplPropertySetterKind.NONE ->
                    error("Internal bug: setter should not be generated for ${prop.name}")
            }
        }

    private fun idViewTransformSetterStatements(
        prop: ImmutableDraftImplPropertyMetadata,
    ): List<LsiStatement> {
        val baseName =
            prop.idViewBaseName
                ?: error("Internal bug: missing id-view base name for ${prop.name}")
        val targetType = idViewTargetType(prop)
        val setBaseStatements =
            if (prop.idViewBaseList) {
                idViewListTransformStatements(baseName, targetType)
            } else {
                listOf(setIdViewBaseStatement(baseName, makeIdOnlyExpression(targetType, LsiNameExpression("value"))))
            }
        return if (prop.idViewBaseNullable) {
            listOf(
                LsiIfStatement(
                    condition = LsiBinaryExpression(
                        left = LsiNameExpression("value"),
                        operator = LsiBinaryOperator.EQUALS,
                        right = LsiNullExpression,
                    ),
                    thenStatements = listOf(setIdViewBaseStatement(baseName, LsiNullExpression)),
                    elseStatements = setBaseStatements,
                )
            )
        } else {
            setBaseStatements
        }
    }

    private fun idViewDirectSetterStatements(
        prop: ImmutableDraftImplPropertyMetadata,
    ): List<LsiStatement> =
        listOf(
            setIdViewBaseStatement(
                prop.idViewBaseName
                    ?: error("Internal bug: missing id-view base name for ${prop.name}"),
                makeIdOnlyExpression(idViewTargetType(prop), LsiNameExpression("value")),
            )
        )

    private fun idViewListTransformStatements(
        baseName: String,
        targetType: LsiTypeName,
    ): List<LsiStatement> {
        val targetsName = "__idViewTargets"
        val indexName = "__idViewIndex"
        val valueExpression = LsiNameExpression("value")
        val valueSizeExpression = LsiCollectionSizeExpression(valueExpression)
        return listOf(
            LsiVariableDeclarationStatement(
                name = targetsName,
                type = ARRAY_LIST_LSI_CLASS_NAME.parameterizedBy(targetType),
                initializer = LsiNewExpression(
                    type = ARRAY_LIST_LSI_CLASS_NAME,
                    arguments = listOf(valueSizeExpression),
                ),
            ),
            LsiForRangeStatement(
                variableName = indexName,
                from = LsiLiteralExpression(0),
                until = valueSizeExpression,
                statements = listOf(
                    LsiExpressionStatement(
                        LsiCallExpression(
                            receiver = LsiNameExpression(targetsName),
                            name = "add",
                            arguments = listOf(
                                makeIdOnlyExpression(
                                    targetType,
                                    LsiCollectionElementExpression(
                                        receiver = valueExpression,
                                        index = LsiNameExpression(indexName),
                                    ),
                                )
                            ),
                        )
                    )
                ),
            ),
            setIdViewBaseStatement(baseName, LsiNameExpression(targetsName)),
        )
    }

    private fun idViewTargetType(
        prop: ImmutableDraftImplPropertyMetadata,
    ): LsiTypeName {
        val baseType =
            prop.idViewBaseTypeName
                ?: error("Internal bug: missing id-view base type for ${prop.name}")
        val targetType =
            if (prop.idViewBaseList) {
                (baseType as? LsiParameterizedTypeName)
                    ?.typeArguments
                    ?.singleOrNull()
                    ?: error("Internal bug: missing id-view element type for ${prop.name}")
            } else {
                baseType
            }
        return targetType.copyNullable(false)
    }

    private fun setIdViewBaseStatement(
        baseName: String,
        expression: LsiExpression,
    ): LsiPropertySetStatement =
        LsiPropertySetStatement(
            receiver = LsiThisExpression,
            name = baseName,
            expression = expression,
        )

    private fun makeIdOnlyExpression(
        targetType: LsiTypeName,
        expression: LsiExpression,
    ): LsiMakeIdOnlyExpression =
        LsiMakeIdOnlyExpression(
            targetType = targetType,
            idExpression = expression,
        )

    private fun addAssociatedIdProperty(prop: ImmutableAssociatedIdMetadata?): LsiPropertySpec? =
        AssociatedIdGenerator(jacksonTypes, true).generate(prop)

    private fun isLoadedCallable(argKind: PropertyDispatchArgKind): LsiCallableSpec =
        functionStatements(
            name = "__isLoaded",
            returnType = BOOLEAN_LSI_CLASS_NAME,
            parameters = listOf(LsiParameterSpec("prop", argKind.typeName)),
            modifiers = setOf(LsiModifier.OVERRIDE),
            statements = bindCurrentDraftObject("__currentIsLoaded").run {
                statements + listOf(
                    LsiReturnStatement(
                        LsiCallExpression(
                            receiver = expression,
                            name = "__isLoaded",
                            arguments = listOf(LsiNameExpression("prop")),
                        )
                    )
                )
            },
        )

    private fun isVisibleCallable(argKind: PropertyDispatchArgKind): LsiCallableSpec =
        functionStatements(
            name = "__isVisible",
            returnType = BOOLEAN_LSI_CLASS_NAME,
            parameters = listOf(LsiParameterSpec("prop", argKind.typeName)),
            modifiers = setOf(LsiModifier.OVERRIDE),
            statements = bindCurrentDraftObject("__currentIsVisible").run {
                statements + listOf(
                    LsiReturnStatement(
                        LsiCallExpression(
                            receiver = expression,
                            name = "__isVisible",
                            arguments = listOf(LsiNameExpression("prop")),
                        )
                    )
                )
            },
        )

    private fun hashCodeCallable(): LsiCallableSpec =
        functionStatements(
            name = "hashCode",
            returnType = INT_LSI_CLASS_NAME,
            modifiers = setOf(LsiModifier.OVERRIDE),
            statements = bindCurrentDraftObject("__currentHash").run {
                statements + listOf(
                    LsiReturnStatement(
                        LsiCallExpression(
                            receiver = expression,
                            name = "hashCode",
                        )
                    )
                )
            },
        )

    private fun parameterizedHashCodeCallable(): LsiCallableSpec =
        functionStatements(
            name = "__hashCode",
            returnType = INT_LSI_CLASS_NAME,
            parameters = listOf(LsiParameterSpec("shallow", BOOLEAN_LSI_CLASS_NAME)),
            modifiers = setOf(LsiModifier.OVERRIDE),
            statements = bindCurrentDraftObject("__currentParameterizedHash").run {
                statements + listOf(
                    LsiReturnStatement(
                        LsiCallExpression(
                            receiver = expression,
                            name = "__hashCode",
                            arguments = listOf(LsiNameExpression("shallow")),
                        )
                    )
                )
            },
        )

    private fun equalsCallable(): LsiCallableSpec =
        functionStatements(
            name = "equals",
            returnType = BOOLEAN_LSI_CLASS_NAME,
            parameters = listOf(LsiParameterSpec("other", ANY_LSI_CLASS_NAME.copyNullable(true))),
            modifiers = setOf(LsiModifier.OVERRIDE),
            statements = bindCurrentDraftObject("__currentEquals").run {
                statements + listOf(
                    LsiReturnStatement(
                        LsiCallExpression(
                            receiver = expression,
                            name = "equals",
                            arguments = listOf(LsiNameExpression("other")),
                        )
                    )
                )
            },
        )

    private fun parameterizedEqualsCallable(): LsiCallableSpec =
        functionStatements(
            name = "__equals",
            returnType = BOOLEAN_LSI_CLASS_NAME,
            parameters = listOf(
                LsiParameterSpec("other", ANY_LSI_CLASS_NAME.copyNullable(true)),
                LsiParameterSpec("shallow", BOOLEAN_LSI_CLASS_NAME),
            ),
            modifiers = setOf(LsiModifier.OVERRIDE),
            statements = bindCurrentDraftObject("__currentParameterizedEquals").run {
                statements + listOf(
                    LsiReturnStatement(
                        LsiCallExpression(
                            receiver = expression,
                            name = "__equals",
                            arguments = listOf(
                                LsiNameExpression("other"),
                                LsiNameExpression("shallow"),
                            ),
                        )
                    )
                )
            },
        )

    private fun toStringCallable(): LsiCallableSpec =
        functionStatements(
            name = "toString",
            returnType = STRING_LSI_CLASS_NAME,
            modifiers = setOf(LsiModifier.OVERRIDE),
            statements = listOf(
                LsiReturnStatement(
                    LsiCallExpression(
                        receiver = LsiTypeExpression(IMMUTABLE_OBJECTS_LSI_CLASS_NAME),
                        name = "toString",
                        arguments = listOf(LsiThisExpression),
                    )
                )
            ),
        )

    private fun propFun(prop: ImmutableDraftImplPropFunMetadata): LsiCallableSpec =
        functionStatements(
            name = prop.name,
            returnType = prop.returnTypeName,
            modifiers = setOf(LsiModifier.OVERRIDE),
            statements = propFunStatements(prop),
        )

    private fun propFunStatements(prop: ImmutableDraftImplPropFunMetadata): List<LsiStatement> {
        val propIdExpression =
            LsiCallExpression(
                receiver = LsiTypeExpression(PROP_ID_LSI_CLASS_NAME),
                name = "byIndex",
                arguments = listOf(LsiLiteralExpression(prop.slotName)),
            )
        val currentValueExpression =
            semanticPropertyGet(
                receiver = LsiThisExpression,
                name = prop.name,
                type = prop.returnTypeName,
            )
        val needsInitCondition =
            if (prop.isNullable) {
                LsiBinaryExpression(
                    left = LsiBinaryExpression(
                        left = LsiCallExpression(
                            name = "__isLoaded",
                            arguments = listOf(propIdExpression),
                        ),
                        operator = LsiBinaryOperator.EQUALS,
                        right = LsiLiteralExpression(false),
                    ),
                    operator = LsiBinaryOperator.OR,
                    right = LsiBinaryExpression(
                        left = currentValueExpression,
                        operator = LsiBinaryOperator.EQUALS,
                        right = LsiNullExpression,
                    ),
                )
            } else {
                LsiBinaryExpression(
                    left = LsiCallExpression(
                        name = "__isLoaded",
                        arguments = listOf(propIdExpression),
                    ),
                    operator = LsiBinaryOperator.EQUALS,
                    right = LsiLiteralExpression(false),
                )
            }
        val initExpression =
            if (prop.isList) {
                LsiCallExpression(name = "mutableListOf")
            } else {
                LsiCallExpression(
                    receiver = LsiTypeExpression(
                        prop.targetProducerClassName
                            ?: error("Internal bug: missing target producer for ${prop.name}"),
                    ),
                    name = "produce",
                )
            }
        return listOf(
            LsiIfStatement(
                condition = needsInitCondition,
                thenStatements = listOf(
                    LsiPropertySetStatement(
                        receiver = LsiThisExpression,
                        name = prop.name,
                        expression = initExpression,
                    )
                ),
            ),
            LsiReturnStatement(
                LsiCastExpression(
                    prop.castTypeName,
                    semanticPropertyGet(
                        receiver = LsiThisExpression,
                        name = prop.name,
                        type = prop.returnTypeName,
                    ),
                )
            ),
        )
    }

    private fun propRefFun(prop: ImmutableDraftImplPropRefMetadata): LsiCallableSpec =
        functionStatements(
            name = prop.name,
            parameters = listOf(LsiParameterSpec("block", prop.blockMetadata.toLsiDraftConsumerTypeName())),
            modifiers = setOf(LsiModifier.OVERRIDE),
            statements = listOf(
                LsiExpressionStatement(
                    LsiCallExpression(
                        receiver = LsiNameExpression("block"),
                        name = "accept",
                        arguments = listOf(
                            LsiCallExpression(name = prop.name),
                        ),
                    )
                )
            ),
        )

    private fun frozenGuardStatement(): LsiIfStatement =
        LsiIfStatement(
            condition = LsiBinaryExpression(
                left = LsiNameExpression("__resolved"),
                operator = LsiBinaryOperator.NOT_EQUALS,
                right = LsiNullExpression,
            ),
            thenStatements = listOf(
                LsiThrowStatement(
                    LsiNewExpression(
                        type = ILLEGAL_STATE_EXCEPTION_LSI_CLASS_NAME,
                        arguments = listOf(LsiLiteralExpression(FROZEN_EXCEPTION_MESSAGE)),
                    )
                )
            )
        )

    private fun whenSubject(argKind: PropertyDispatchArgKind): LsiExpression =
        if (argKind.usesIndexedSubject) {
            LsiCallExpression(receiver = LsiNameExpression("prop"), name = "asIndex")
        } else {
            LsiNameExpression("prop")
        }

    private fun propCondition(
        argKind: PropertyDispatchArgKind,
        prop: ImmutableDraftImplDispatchPropMetadata,
    ): LsiExpression =
        if (argKind.usesIndexedSubject) {
            LsiNameExpression(prop.slotName)
        } else {
            LsiLiteralExpression(prop.name)
        }

    private fun illegalPropThrow(argKind: PropertyDispatchArgKind): LsiThrowStatement =
        LsiThrowStatement(
            LsiNewExpression(
                type = ILLEGAL_ARGUMENT_EXCEPTION_LSI_CLASS_NAME,
                arguments = listOf(
                    LsiBinaryExpression(
                        left = LsiBinaryExpression(
                            left = LsiLiteralExpression("Illegal property ${argKind.illegalKindLabel} "),
                            operator = LsiBinaryOperator.PLUS,
                            right = LsiLiteralExpression(" for \"${type.dispatchType.typeDescription}\": "),
                        ),
                        operator = LsiBinaryOperator.PLUS,
                        right = LsiNameExpression("prop"),
                    )
                )
            )
        )

    private fun illegalStateThrowStatement(message: String): LsiThrowStatement =
        LsiThrowStatement(
            LsiNewExpression(
                type = ILLEGAL_STATE_EXCEPTION_LSI_CLASS_NAME,
                arguments = listOf(LsiLiteralExpression(message)),
            )
        )

    private fun bindCurrentDraftObject(name: String): DraftObjectBinding {
        val nullableName = "${name}Nullable"
        return DraftObjectBinding(
            statements = buildList {
                add(
                    LsiVariableDeclarationStatement(
                        name = nullableName,
                        type = type.draftProducerImplClassName.copyNullable(true),
                        mutable = true,
                        initializer = LsiNameExpression("__modified"),
                    )
                )
                add(
                    LsiIfStatement(
                        condition = LsiBinaryExpression(
                            left = LsiNameExpression(nullableName),
                            operator = LsiBinaryOperator.EQUALS,
                            right = LsiNullExpression,
                        ),
                        thenStatements = listOf(
                            LsiAssignmentStatement(
                                target = LsiNameExpression(nullableName),
                                expression = LsiNameExpression("__base"),
                            )
                        ),
                    )
                )
                add(
                    LsiIfStatement(
                        condition = LsiBinaryExpression(
                            left = LsiNameExpression(nullableName),
                            operator = LsiBinaryOperator.EQUALS,
                            right = LsiNullExpression,
                        ),
                        thenStatements = listOf(illegalStateThrowStatement(BUILDER_DRAFT_MESSAGE)),
                    )
                )
                add(
                    LsiVariableDeclarationStatement(
                        name = name,
                        type = type.draftProducerImplClassName,
                        initializer = LsiCastExpression(
                            type = type.draftProducerImplClassName,
                            expression = LsiNameExpression(nullableName),
                        ),
                    )
                )
            },
            expression = LsiNameExpression(name),
        )
    }

    private fun bindMutableDraftObject(name: String): DraftObjectBinding {
        val nullableName = "${name}Nullable"
        val baseName = "${name}Base"
        val clonedName = "${name}Cloned"
        return DraftObjectBinding(
            statements = buildList {
                add(
                    LsiVariableDeclarationStatement(
                        name = nullableName,
                        type = type.draftProducerImplClassName.copyNullable(true),
                        mutable = true,
                        initializer = LsiNameExpression("__modified"),
                    )
                )
                add(
                    LsiIfStatement(
                        condition = LsiBinaryExpression(
                            left = LsiNameExpression(nullableName),
                            operator = LsiBinaryOperator.EQUALS,
                            right = LsiNullExpression,
                        ),
                        thenStatements = buildList {
                            add(
                                LsiVariableDeclarationStatement(
                                    name = baseName,
                                    type = type.draftProducerImplClassName.copyNullable(true),
                                    initializer = LsiNameExpression("__base"),
                                )
                            )
                            add(
                                LsiIfStatement(
                                    condition = LsiBinaryExpression(
                                        left = LsiNameExpression(baseName),
                                        operator = LsiBinaryOperator.EQUALS,
                                        right = LsiNullExpression,
                                    ),
                                    thenStatements = listOf(illegalStateThrowStatement(BUILDER_DRAFT_MESSAGE)),
                                )
                            )
                            add(
                                LsiVariableDeclarationStatement(
                                    name = clonedName,
                                    type = type.draftProducerImplClassName,
                                    initializer = LsiCallExpression(
                                        receiver = LsiCastExpression(
                                            type = type.draftProducerImplClassName,
                                            expression = LsiNameExpression(baseName),
                                        ),
                                        name = "clone",
                                    ),
                                )
                            )
                            add(
                                LsiAssignmentStatement(
                                    target = LsiNameExpression("__modified"),
                                    expression = LsiNameExpression(clonedName),
                                )
                            )
                            add(
                                LsiAssignmentStatement(
                                    target = LsiNameExpression(nullableName),
                                    expression = LsiNameExpression(clonedName),
                                )
                            )
                        },
                    )
                )
                add(
                    LsiIfStatement(
                        condition = LsiBinaryExpression(
                            left = LsiNameExpression(nullableName),
                            operator = LsiBinaryOperator.EQUALS,
                            right = LsiNullExpression,
                        ),
                        thenStatements = listOf(illegalStateThrowStatement(BUILDER_DRAFT_MESSAGE)),
                    )
                )
                add(
                    LsiVariableDeclarationStatement(
                        name = name,
                        type = type.draftProducerImplClassName,
                        initializer = LsiCastExpression(
                            type = type.draftProducerImplClassName,
                            expression = LsiNameExpression(nullableName),
                        ),
                    )
                )
            },
            expression = LsiNameExpression(name),
        )
    }

    private fun propIdByIndexExpression(slotName: String): LsiCallExpression =
        LsiCallExpression(
            receiver = LsiTypeExpression(PROP_ID_LSI_CLASS_NAME),
            name = "byIndex",
            arguments = listOf(LsiNameExpression(slotName)),
        )

    private fun unloadValueExpression(prop: ImmutableDraftImplDispatchPropMetadata): LsiExpression =
        when (prop.unloadValueKind) {
            ImmutableDraftImplUnloadValueKind.NULL -> LsiNullExpression
            ImmutableDraftImplUnloadValueKind.PRIMITIVE_DEFAULT ->
                prop.unloadValueTypeName?.primitiveDefaultValueExpression()
                    ?: error("Internal bug: missing unload value type for ${prop.name}")
            null -> error("Internal bug: missing unload value kind for ${prop.name}")
        }

    private fun propertyType(name: String): LsiTypeName =
        type.members
            .firstOrNull { it.property.name == name }
            ?.property
            ?.typeName
            ?: error("Internal bug: missing property metadata for $name")

    private fun unloadCallable(argKind: PropertyDispatchArgKind): LsiCallableSpec =
        functionStatements(
            name = "__unload",
            parameters = listOf(LsiParameterSpec("prop", argKind.typeName)),
            modifiers = setOf(LsiModifier.OVERRIDE),
            statements = buildList {
                add(frozenGuardStatement())
                add(
                    LsiWhenStatement(
                        subject = whenSubject(argKind),
                        cases = buildList {
                            if (argKind.usesIndexedSubject) {
                                add(
                                    LsiWhenCase(
                                        conditions = listOf(LsiLiteralExpression(-1)),
                                        statements = listOf(
                                            LsiExpressionStatement(
                                                LsiCallExpression(
                                                    name = "__unload",
                                                    arguments = listOf(
                                                        LsiCallExpression(
                                                            receiver = LsiNameExpression("prop"),
                                                            name = "asName",
                                                        )
                                                    )
                                                )
                                            )
                                        )
                                    )
                                )
                            }
                            for (prop in type.dispatchType.props) {
                                add(
                                    LsiWhenCase(
                                        conditions = listOf(propCondition(argKind, prop)),
                                        statements = when (prop.unloadKind) {
                                            ImmutableDraftImplUnloadKind.DELEGATE_BASE ->
                                                listOf(
                                                    LsiExpressionStatement(
                                                        LsiCallExpression(
                                                            name = "__unload",
                                                            arguments = listOf(
                                                                propIdByIndexExpression(
                                                                    prop.basePropSlotName
                                                                        ?: error("Internal bug: missing base prop slot for ${prop.name}"),
                                                                )
                                                            )
                                                        )
                                                    )
                                                )

                                            ImmutableDraftImplUnloadKind.NO_OP ->
                                                listOf(LsiReturnStatement(null))

                                            ImmutableDraftImplUnloadKind.RESET_LOADED -> {
                                                val modified = bindMutableDraftObject("__tmpModified${prop.slotName}")
                                                modified.statements + listOf(
                                                    LsiAssignmentStatement(
                                                        target = LsiPropertyAccessExpression(
                                                            modified.expression,
                                                            prop.valueFieldName
                                                                ?: error("Internal bug: missing unload value field for ${prop.name}"),
                                                        ),
                                                        expression = unloadValueExpression(prop),
                                                    ),
                                                    LsiAssignmentStatement(
                                                        target = LsiPropertyAccessExpression(
                                                            modified.expression,
                                                            prop.loadedFieldName
                                                                ?: error("Internal bug: missing unload loaded field for ${prop.name}"),
                                                        ),
                                                        expression = LsiLiteralExpression(false),
                                                    ),
                                                )
                                            }

                                            ImmutableDraftImplUnloadKind.RESET_VALUE -> {
                                                val modified = bindMutableDraftObject("__tmpModified${prop.slotName}")
                                                modified.statements + listOf(
                                                    LsiAssignmentStatement(
                                                        target = LsiPropertyAccessExpression(
                                                            modified.expression,
                                                            prop.valueFieldName
                                                                ?: error("Internal bug: missing unload value field for ${prop.name}"),
                                                        ),
                                                        expression = LsiNullExpression,
                                                    ),
                                                )
                                            }
                                        },
                                    )
                                )
                            }
                        },
                        elseStatements = listOf(illegalPropThrow(argKind)),
                    )
                )
            },
        )

    private fun setCallable(argKind: PropertyDispatchArgKind): LsiCallableSpec =
        functionStatements(
            name = "__set",
            parameters = listOf(
                LsiParameterSpec("prop", argKind.typeName),
                LsiParameterSpec("value", ANY_LSI_CLASS_NAME.copyNullable(true)),
            ),
            modifiers = setOf(LsiModifier.OVERRIDE),
            statements = listOf(
                LsiWhenStatement(
                    subject = whenSubject(argKind),
                    cases = buildList {
                        if (argKind.usesIndexedSubject) {
                            add(
                                LsiWhenCase(
                                    conditions = listOf(LsiLiteralExpression(-1)),
                                    statements = listOf(
                                        LsiExpressionStatement(
                                            LsiCallExpression(
                                                name = "__set",
                                                arguments = listOf(
                                                    LsiCallExpression(
                                                        receiver = LsiNameExpression("prop"),
                                                        name = "asName",
                                                    ),
                                                    LsiNameExpression("value"),
                                                )
                                            )
                                        )
                                    )
                                )
                            )
                        }
                        for (prop in type.dispatchType.props) {
                            add(
                                LsiWhenCase(
                                    conditions = listOf(propCondition(argKind, prop)),
                                    statements = if (prop.setKind == ImmutableDraftImplSetKind.READ_ONLY) {
                                        listOf(LsiReturnStatement(null))
                                    } else {
                                        buildList {
                                            if (!prop.isNullable) {
                                                add(
                                                    LsiIfStatement(
                                                        condition = LsiBinaryExpression(
                                                            left = LsiNameExpression("value"),
                                                            operator = LsiBinaryOperator.EQUALS,
                                                            right = LsiNullExpression,
                                                        ),
                                                        thenStatements = listOf(
                                                            LsiThrowStatement(
                                                                LsiNewExpression(
                                                                    type = ILLEGAL_ARGUMENT_EXCEPTION_LSI_CLASS_NAME,
                                                                    arguments = listOf(
                                                                        LsiLiteralExpression("'${prop.name} cannot be null")
                                                                    ),
                                                                )
                                                            )
                                                        )
                                                    )
                                                )
                                            }
                                            add(
                                                LsiPropertySetStatement(
                                                    receiver = LsiThisExpression,
                                                    name = prop.name,
                                                    expression = LsiCastExpression(
                                                        if (prop.isNullable) {
                                                            prop.setTypeName?.copyNullable(true)
                                                        } else {
                                                            prop.setTypeName
                                                        }
                                                            ?: error("Internal bug: missing set type for ${prop.name}"),
                                                        LsiNameExpression("value"),
                                                    ),
                                                )
                                            )
                                        }
                                    },
                                )
                            )
                        }
                    },
                    elseStatements = listOf(illegalPropThrow(argKind)),
                )
            ),
        )

    private fun showCallable(argKind: PropertyDispatchArgKind): LsiCallableSpec =
        functionStatements(
            name = "__show",
            parameters = listOf(
                LsiParameterSpec("prop", argKind.typeName),
                LsiParameterSpec("visible", BOOLEAN_LSI_CLASS_NAME),
            ),
            modifiers = setOf(LsiModifier.OVERRIDE),
            statements = buildList {
                add(frozenGuardStatement())
                val current = bindCurrentDraftObject("__currentVisibilityOwner")
                addAll(current.statements)
                add(
                    LsiVariableDeclarationStatement(
                        name = "__visibility",
                        type = VISIBILITY_LSI_CLASS_NAME.copyNullable(true),
                        mutable = true,
                        initializer = LsiPropertyAccessExpression(current.expression, "__visibility"),
                    )
                )
                add(
                    LsiIfStatement(
                        condition = LsiBinaryExpression(
                            left = LsiNameExpression("__visibility"),
                            operator = LsiBinaryOperator.EQUALS,
                            right = LsiNullExpression,
                        ),
                        thenStatements = listOf(
                            LsiIfStatement(
                                condition = LsiNameExpression("visible"),
                                thenStatements = listOf(LsiReturnStatement(null)),
                                elseStatements = buildList {
                                    val modified = bindMutableDraftObject("__tmpModified")
                                    addAll(modified.statements)
                                    add(
                                        LsiVariableDeclarationStatement(
                                            name = "__newVisibility",
                                            type = VISIBILITY_LSI_CLASS_NAME,
                                            initializer = LsiCallExpression(
                                                receiver = LsiTypeExpression(VISIBILITY_LSI_CLASS_NAME),
                                                name = "of",
                                                arguments = listOf(LsiLiteralExpression(type.dispatchType.propsSize)),
                                            ),
                                        )
                                    )
                                    add(
                                        LsiAssignmentStatement(
                                            target = LsiPropertyAccessExpression(
                                                modified.expression,
                                                "__visibility",
                                            ),
                                            expression = LsiNameExpression("__newVisibility"),
                                        )
                                    )
                                    add(
                                        LsiAssignmentStatement(
                                            target = LsiNameExpression("__visibility"),
                                            expression = LsiNameExpression("__newVisibility"),
                                        ),
                                    )
                                },
                            )
                        ),
                    )
                )
                add(
                    LsiWhenStatement(
                        subject = whenSubject(argKind),
                        cases = buildList {
                            if (argKind.usesIndexedSubject) {
                                add(
                                    LsiWhenCase(
                                        conditions = listOf(LsiLiteralExpression(-1)),
                                        statements = listOf(
                                            LsiExpressionStatement(
                                                LsiCallExpression(
                                                    name = "__show",
                                                    arguments = listOf(
                                                        LsiCallExpression(
                                                            receiver = LsiNameExpression("prop"),
                                                            name = "asName",
                                                        ),
                                                        LsiNameExpression("visible"),
                                                    )
                                                )
                                            )
                                        )
                                    )
                                )
                            }
                            for (prop in type.dispatchType.props) {
                                add(
                                    LsiWhenCase(
                                        conditions = listOf(propCondition(argKind, prop)),
                                        statements = listOf(
                                            LsiExpressionStatement(
                                                LsiCallExpression(
                                                    receiver = LsiNameExpression("__visibility"),
                                                    name = "show",
                                                    arguments = listOf(
                                                        LsiNameExpression(prop.slotName),
                                                        LsiNameExpression("visible"),
                                                    ),
                                                )
                                            )
                                        ),
                                    )
                                )
                            }
                        },
                        elseStatements = listOf(illegalPropThrow(argKind)),
                    )
                )
            },
        )

    private fun draftContextCallable(): LsiCallableSpec =
        functionStatements(
            name = "__draftContext",
            returnType = DRAFT_CONTEXT_LSI_CLASS_NAME,
            modifiers = setOf(LsiModifier.OVERRIDE),
            statements = listOf(
                LsiReturnStatement(
                    LsiCallExpression(name = "__ctx")
                )
            ),
        )

    private fun resolveCallable(): LsiCallableSpec =
        functionStatements(
            name = "__resolve",
            returnType = ANY_LSI_CLASS_NAME,
            modifiers = setOf(LsiModifier.OVERRIDE),
            statements = buildList {
                add(
                    LsiVariableDeclarationStatement(
                        name = "__resolvedValue",
                        type = type.className.copyNullable(true),
                        initializer = LsiPropertyAccessExpression(LsiThisExpression, "__resolved"),
                    )
                )
                add(
                    LsiIfStatement(
                        condition = LsiBinaryExpression(
                            left = LsiNameExpression("__resolvedValue"),
                            operator = LsiBinaryOperator.NOT_EQUALS,
                            right = LsiNullExpression,
                        ),
                        thenStatements = listOf(LsiReturnStatement(LsiNameExpression("__resolvedValue"))),
                    )
                )
                add(
                    LsiIfStatement(
                        condition = LsiNameExpression("__resolving"),
                        thenStatements = listOf(
                            LsiThrowStatement(
                                LsiNewExpression(type = CIRCULAR_REFERENCE_EXCEPTION_LSI_CLASS_NAME)
                            )
                        ),
                    )
                )
                add(
                    LsiAssignmentStatement(
                        target = LsiNameExpression("__resolving"),
                        expression = LsiLiteralExpression(true),
                    )
                )
                add(
                    LsiVariableDeclarationStatement(
                        name = "__ctx",
                        type = DRAFT_CONTEXT_LSI_CLASS_NAME,
                        initializer = LsiCallExpression(name = "__ctx"),
                    )
                )
                add(
                    LsiTryStatement(
                        tryStatements = buildList {
                            add(
                                LsiVariableDeclarationStatement(
                                    name = "base",
                                    type = type.draftProducerImplClassName.copyNullable(true),
                                    initializer = LsiNameExpression("__base"),
                                )
                            )
                            add(
                                LsiVariableDeclarationStatement(
                                    name = "__tmpModified",
                                    type = type.draftProducerImplClassName.copyNullable(true),
                                    mutable = true,
                                    initializer = LsiNameExpression("__modified"),
                                )
                            )
                            if (type.resolveProps.any { it.baseResolveKind != ImmutableDraftImplResolveKind.NONE }) {
                                add(
                                    LsiIfStatement(
                                        condition = LsiBinaryExpression(
                                            left = LsiNameExpression("__tmpModified"),
                                            operator = LsiBinaryOperator.EQUALS,
                                            right = LsiNullExpression,
                                        ),
                                        thenStatements = buildList {
                                            add(
                                                LsiIfStatement(
                                                    condition = LsiBinaryExpression(
                                                        left = LsiNameExpression("base"),
                                                        operator = LsiBinaryOperator.NOT_EQUALS,
                                                        right = LsiNullExpression,
                                                    ),
                                                    thenStatements = buildList {
                                                        add(
                                                            LsiVariableDeclarationStatement(
                                                                name = "__baseValue",
                                                                type = type.draftProducerImplClassName,
                                                                initializer = LsiNameExpression("base"),
                                                            )
                                                        )
                                                        for (prop in type.resolveProps) {
                                                            if (prop.baseResolveKind == ImmutableDraftImplResolveKind.NONE) {
                                                                continue
                                                            }
                                                            val propType = propertyType(prop.name)
                                                            val oldValueName = "__oldValue${prop.slotName}"
                                                            val newValueName = "__newValue${prop.slotName}"
                                                            add(
                                                                LsiIfStatement(
                                                                    condition = LsiCallExpression(
                                                                        name = "__isLoaded",
                                                                        arguments = listOf(propIdByIndexExpression(prop.slotName)),
                                                                    ),
                                                                    thenStatements = listOf(
                                                                        LsiVariableDeclarationStatement(
                                                                            name = oldValueName,
                                                                            type = propType,
                                                                            initializer = LsiPropertyAccessExpression(
                                                                                LsiNameExpression("__baseValue"),
                                                                                prop.name,
                                                                            ),
                                                                        ),
                                                                        LsiVariableDeclarationStatement(
                                                                            name = newValueName,
                                                                            type = propType,
                                                                            initializer = LsiCallExpression(
                                                                                receiver = LsiNameExpression("__ctx"),
                                                                                name = if (prop.baseResolveKind == ImmutableDraftImplResolveKind.LIST) {
                                                                                    "resolveList"
                                                                                } else {
                                                                                    "resolveObject"
                                                                                },
                                                                                arguments = listOf(LsiNameExpression(oldValueName)),
                                                                            ),
                                                                        ),
                                                                        LsiIfStatement(
                                                                            condition = LsiBinaryExpression(
                                                                                left = LsiNameExpression(oldValueName),
                                                                                operator = LsiBinaryOperator.IDENTITY_NOT_EQUALS,
                                                                                right = LsiNameExpression(newValueName),
                                                                            ),
                                                                            thenStatements = listOf(
                                                                                LsiPropertySetStatement(
                                                                                    receiver = LsiThisExpression,
                                                                                    name = prop.name,
                                                                                    expression = LsiNameExpression(newValueName),
                                                                                )
                                                                            ),
                                                                        ),
                                                                    ),
                                                                )
                                                            )
                                                        }
                                                    },
                                                )
                                            )
                                            add(
                                                LsiAssignmentStatement(
                                                    target = LsiNameExpression("__tmpModified"),
                                                    expression = LsiNameExpression("__modified"),
                                                )
                                            )
                                        },
                                        elseStatements = buildList {
                                            add(
                                                LsiVariableDeclarationStatement(
                                                    name = "__modifiedValue",
                                                    type = type.draftProducerImplClassName,
                                                    initializer = LsiNameExpression("__tmpModified"),
                                                )
                                            )
                                            for (prop in type.resolveProps) {
                                                when (prop.modifiedResolveKind) {
                                                    ImmutableDraftImplResolveKind.LIST ->
                                                        add(
                                                            LsiAssignmentStatement(
                                                                target = LsiPropertyAccessExpression(
                                                                    LsiNameExpression("__modifiedValue"),
                                                                    prop.valueFieldName,
                                                                ),
                                                                expression = LsiCallExpression(
                                                                    receiver = LsiTypeExpression(NON_SHARED_LIST_LSI_CLASS_NAME),
                                                                    name = "of",
                                                                    arguments = listOf(
                                                                        LsiPropertyAccessExpression(
                                                                            LsiNameExpression("__modifiedValue"),
                                                                            prop.valueFieldName,
                                                                        ),
                                                                        LsiCallExpression(
                                                                            receiver = LsiNameExpression("__ctx"),
                                                                            name = "resolveList",
                                                                            arguments = listOf(
                                                                                LsiPropertyAccessExpression(
                                                                                    LsiNameExpression("__modifiedValue"),
                                                                                    prop.valueFieldName,
                                                                                )
                                                                            ),
                                                                        ),
                                                                    ),
                                                                ),
                                                            )
                                                        )

                                                    ImmutableDraftImplResolveKind.OBJECT ->
                                                        add(
                                                            LsiAssignmentStatement(
                                                                target = LsiPropertyAccessExpression(
                                                                    LsiNameExpression("__modifiedValue"),
                                                                    prop.valueFieldName,
                                                                ),
                                                                expression = LsiCallExpression(
                                                                    receiver = LsiNameExpression("__ctx"),
                                                                    name = "resolveObject",
                                                                    arguments = listOf(
                                                                        LsiPropertyAccessExpression(
                                                                            LsiNameExpression("__modifiedValue"),
                                                                            prop.valueFieldName,
                                                                        )
                                                                    ),
                                                                ),
                                                            )
                                                        )

                                                    ImmutableDraftImplResolveKind.NONE -> Unit
                                                }
                                            }
                                        },
                                    )
                                )
                            }
                            add(
                                LsiIfStatement(
                                    condition = LsiBinaryExpression(
                                        left = LsiBinaryExpression(
                                            left = LsiNameExpression("base"),
                                            operator = LsiBinaryOperator.NOT_EQUALS,
                                            right = LsiNullExpression,
                                        ),
                                        operator = LsiBinaryOperator.AND,
                                        right = LsiBinaryExpression(
                                            left = LsiNameExpression("__tmpModified"),
                                            operator = LsiBinaryOperator.EQUALS,
                                            right = LsiNullExpression,
                                        ),
                                    ),
                                    thenStatements = listOf(
                                        LsiAssignmentStatement(
                                            target = LsiPropertyAccessExpression(LsiThisExpression, "__resolved"),
                                            expression = LsiNameExpression("base"),
                                        ),
                                        LsiReturnStatement(LsiNameExpression("base")),
                                    ),
                                )
                            )
                            for (validator in type.typeValidators) {
                                add(
                                    LsiExpressionStatement(
                                        LsiCallExpression(
                                            receiver = LsiNameExpression(validatorFieldName(validator.className)),
                                            name = "validate",
                                            arguments = listOf(LsiNameExpression("__tmpModified")),
                                        )
                                    )
                                )
                            }
                            add(
                                LsiIfStatement(
                                    condition = LsiBinaryExpression(
                                        left = LsiNameExpression("__tmpModified"),
                                        operator = LsiBinaryOperator.EQUALS,
                                        right = LsiNullExpression,
                                    ),
                                    thenStatements = listOf(
                                        LsiThrowStatement(
                                            LsiNewExpression(
                                                type = ILLEGAL_STATE_EXCEPTION_LSI_CLASS_NAME,
                                                arguments = listOf(LsiLiteralExpression(RESOLVED_DRAFT_MESSAGE)),
                                            )
                                        )
                                    ),
                                )
                            )
                            add(
                                LsiVariableDeclarationStatement(
                                    name = "__resolvedResult",
                                    type = type.className,
                                    initializer = LsiNameExpression("__tmpModified"),
                                )
                            )
                            add(
                                LsiAssignmentStatement(
                                    target = LsiPropertyAccessExpression(LsiThisExpression, "__resolved"),
                                    expression = LsiNameExpression("__resolvedResult"),
                                )
                            )
                            add(LsiReturnStatement(LsiNameExpression("__resolvedResult")))
                        },
                        finallyStatements = listOf(
                            LsiAssignmentStatement(
                                target = LsiNameExpression("__resolving"),
                                expression = LsiLiteralExpression(false),
                            )
                        ),
                    )
                )
            },
        )

    private fun isResolvedCallable(): LsiCallableSpec =
        functionStatements(
            name = "__isResolved",
            returnType = BOOLEAN_LSI_CLASS_NAME,
            modifiers = setOf(LsiModifier.OVERRIDE),
            statements = listOf(
                LsiReturnStatement(
                    LsiBinaryExpression(
                        left = LsiNameExpression("__resolved"),
                        operator = LsiBinaryOperator.NOT_EQUALS,
                        right = LsiNullExpression,
                    )
                )
            ),
        )

    private fun contextCallable(): LsiCallableSpec =
        functionStatements(
            name = "__ctx",
            returnType = DRAFT_CONTEXT_LSI_CLASS_NAME,
            modifiers = setOf(LsiModifier.PRIVATE),
            statements = listOf(
                LsiIfStatement(
                    condition = LsiBinaryExpression(
                        left = LsiNameExpression("__ctx"),
                        operator = LsiBinaryOperator.NOT_EQUALS,
                        right = LsiNullExpression,
                    ),
                    thenStatements = listOf(LsiReturnStatement(LsiNameExpression("__ctx"))),
                    elseStatements = listOf(
                        LsiThrowStatement(
                            LsiNewExpression(
                                type = ILLEGAL_STATE_EXCEPTION_LSI_CLASS_NAME,
                                arguments = listOf(LsiLiteralExpression(SIMPLE_DRAFT_MESSAGE)),
                            )
                        )
                    )
                )
            ),
        )

    private fun unwrapCallable(): LsiCallableSpec =
        functionStatements(
            name = "__unwrap",
            returnType = ANY_LSI_CLASS_NAME,
            modifiers = setOf(LsiModifier.INTERNAL),
            statements = listOf(
                LsiIfStatement(
                    condition = LsiBinaryExpression(
                        left = LsiNameExpression("__modified"),
                        operator = LsiBinaryOperator.NOT_EQUALS,
                        right = LsiNullExpression,
                    ),
                    thenStatements = listOf(LsiReturnStatement(LsiNameExpression("__modified"))),
                    elseStatements = listOf(
                        LsiThrowStatement(
                            LsiNewExpression(
                                type = ILLEGAL_STATE_EXCEPTION_LSI_CLASS_NAME,
                                arguments = listOf(LsiLiteralExpression(BUILDER_DRAFT_MESSAGE)),
                            )
                        )
                    )
                )
            ),
        )

    private fun companionProperties(): List<LsiPropertySpec> = buildList {
        val hasEmailPattern = type.validationProps.any {
            it.validationAnnotationMirrorMultiMap["Email"]?.isNotEmpty() == true
        }
        if (hasEmailPattern) {
            add(
                LsiPropertySpec(
                    name = DRAFT_FIELD_EMAIL_PATTERN,
                    type = JAVA_PATTERN_LSI_CLASS_NAME,
                    modifiers = setOf(LsiModifier.PRIVATE, LsiModifier.STATIC),
                    initializer = LsiCallExpression(
                        receiver = LsiTypeExpression(JAVA_PATTERN_LSI_CLASS_NAME),
                        name = "compile",
                        arguments = listOf(LsiLiteralExpression(EMAIL_PATTERN)),
                    ),
                ),
            )
        }
        for (prop in type.validationProps) {
            val patterns = prop.validationAnnotationMirrorMultiMap["Pattern"].orEmpty()
            for (index in patterns.indices) {
                add(
                    LsiPropertySpec(
                        name = regexpPatternFieldName(prop.name, index),
                        type = JAVA_PATTERN_LSI_CLASS_NAME,
                        modifiers = setOf(LsiModifier.PRIVATE, LsiModifier.STATIC),
                        initializer = LsiCallExpression(
                            receiver = LsiTypeExpression(JAVA_PATTERN_LSI_CLASS_NAME),
                            name = "compile",
                            arguments = listOf(
                                LsiLiteralExpression(patterns[index]["regexp"]),
                            ),
                        ),
                    ),
                )
            }
        }
        for (validator in type.typeValidators) {
            add(
                LsiPropertySpec(
                    name = validatorFieldName(validator.className),
                    type = VALIDATOR_LSI_CLASS_NAME.parameterizedBy(type.className),
                    modifiers = setOf(LsiModifier.PRIVATE, LsiModifier.STATIC),
                    initializer = LsiNewExpression(
                        type = VALIDATOR_LSI_CLASS_NAME,
                        arguments = listOf(
                            LsiJavaClassExpression(validator.className),
                            LsiLiteralExpression(validator.message),
                            LsiJavaClassExpression(type.className),
                            LsiNullExpression,
                        ),
                    ),
                ),
            )
        }
        for (prop in type.validationProps) {
            for ((className, message) in prop.validationMessages) {
                add(
                    LsiPropertySpec(
                        name = validatorFieldName(prop.name, className),
                        type = VALIDATOR_LSI_CLASS_NAME.parameterizedBy(prop.lsiTypeName()),
                        modifiers = setOf(LsiModifier.PRIVATE, LsiModifier.STATIC),
                        initializer = LsiNewExpression(
                            type = VALIDATOR_LSI_CLASS_NAME,
                            arguments = listOf(
                                LsiJavaClassExpression(className),
                                LsiLiteralExpression(message),
                                LsiJavaClassExpression(type.className),
                                LsiCallExpression(
                                    receiver = LsiTypeExpression(PROP_ID_LSI_CLASS_NAME),
                                    name = "byIndex",
                                    arguments = listOf(LsiLiteralExpression(prop.slotName)),
                                ),
                            ),
                        ),
                    ),
                )
            }
        }
    }

    private fun functionStatements(
        name: String,
        returnType: LsiTypeName? = null,
        parameters: List<LsiParameterSpec> = emptyList(),
        modifiers: Set<LsiModifier> = emptySet(),
        statements: List<LsiStatement>,
    ): LsiCallableSpec =
        LsiCallableSpec(
            kind = LsiCallableSpecKind.FUNCTION,
            name = name,
            returnType = returnType,
            parameters = parameters,
            modifiers = modifiers,
            statements = statements,
        )

}
