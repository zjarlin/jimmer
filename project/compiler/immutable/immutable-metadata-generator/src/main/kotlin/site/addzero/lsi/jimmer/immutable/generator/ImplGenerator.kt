package site.addzero.lsi.jimmer.immutable.generator

import site.addzero.lsi.codegen.IMPL
import site.addzero.lsi.codegen.IMMUTABLE_OBJECTS_LSI_CLASS_NAME
import site.addzero.lsi.codegen.IMMUTABLE_SPI_LSI_CLASS_NAME
import site.addzero.lsi.codegen.JacksonTypes
import site.addzero.lsi.codegen.JAVA_SERIALIZABLE_LSI_CLASS_NAME as SERIALIZABLE_LSI_CLASS_NAME
import site.addzero.lsi.codegen.JAVA_SYSTEM_LSI_CLASS_NAME as SYSTEM_LSI_CLASS_NAME
import site.addzero.lsi.codegen.KOTLIN_ANY_LSI_CLASS_NAME as ANY_LSI_CLASS_NAME
import site.addzero.lsi.codegen.KOTLIN_BOOLEAN_LSI_CLASS_NAME as BOOLEAN_LSI_CLASS_NAME
import site.addzero.lsi.codegen.KOTLIN_CLONEABLE_LSI_CLASS_NAME as CLONEABLE_LSI_CLASS_NAME
import site.addzero.lsi.codegen.KOTLIN_INT_LSI_CLASS_NAME as INT_LSI_CLASS_NAME
import site.addzero.lsi.codegen.KOTLIN_STRING_LSI_CLASS_NAME as STRING_LSI_CLASS_NAME
import site.addzero.lsi.codegen.PROP_ID_LSI_CLASS_NAME
import site.addzero.lsi.codegen.UNLOADED_EXCEPTION_LSI_CLASS_NAME
import site.addzero.lsi.codegen.VISIBILITY_LSI_CLASS_NAME
import site.addzero.lsi.codegen.generatedAnnotation
import site.addzero.lsi.jimmer.immutable.generator.ImmutableImplGetterPropKind.ID_VIEW_LIST
import site.addzero.lsi.jimmer.immutable.generator.ImmutableImplGetterPropKind.ID_VIEW_SCALAR
import site.addzero.lsi.jimmer.immutable.generator.ImmutableImplGetterPropKind.MANY_TO_MANY_VIEW
import site.addzero.lsi.jimmer.immutable.generator.ImmutableImplGetterPropKind.STANDARD
import site.addzero.lsi.jimmer.immutable.generator.ImmutableImplLoadKind.FORMULA
import site.addzero.lsi.jimmer.immutable.generator.ImmutableImplLoadKind.ID_VIEW_LIST as LOAD_ID_VIEW_LIST
import site.addzero.lsi.jimmer.immutable.generator.ImmutableImplLoadKind.ID_VIEW_SCALAR as LOAD_ID_VIEW_SCALAR
import site.addzero.lsi.jimmer.immutable.generator.ImmutableImplLoadKind.MANY_TO_MANY_VIEW as LOAD_MANY_TO_MANY_VIEW
import site.addzero.lsi.jimmer.immutable.generator.ImmutableImplLoadKind.STANDARD as LOAD_STANDARD
import site.addzero.lsi.poet.LsiAnnotationSpec
import site.addzero.lsi.poet.LsiAnnotationUseSiteTarget
import site.addzero.lsi.poet.LsiAssignmentStatement
import site.addzero.lsi.poet.LsiBinaryExpression
import site.addzero.lsi.poet.LsiBinaryOperator
import site.addzero.lsi.poet.LsiCallableSpec
import site.addzero.lsi.poet.LsiCallableSpecKind
import site.addzero.lsi.poet.LsiCastExpression
import site.addzero.lsi.poet.LsiCallExpression
import site.addzero.lsi.poet.LsiExpressionStatement
import site.addzero.lsi.poet.LsiForRangeStatement
import site.addzero.lsi.poet.LsiIfStatement
import site.addzero.lsi.poet.LsiJavaClassExpression
import site.addzero.lsi.poet.LsiLambdaExpression
import site.addzero.lsi.poet.LsiLambdaMode
import site.addzero.lsi.poet.LsiLiteralExpression
import site.addzero.lsi.poet.LsiModifier
import site.addzero.lsi.poet.LsiNameExpression
import site.addzero.lsi.poet.LsiNewExpression
import site.addzero.lsi.poet.LsiNullExpression
import site.addzero.lsi.poet.LsiParameterSpec
import site.addzero.lsi.poet.LsiPropertyAccessExpression
import site.addzero.lsi.poet.LsiPropertyGetExpression
import site.addzero.lsi.poet.LsiPropertySpec
import site.addzero.lsi.poet.LsiReturnStatement
import site.addzero.lsi.poet.LsiSafeCastExpression
import site.addzero.lsi.poet.LsiStatement
import site.addzero.lsi.poet.LsiStringAnnotationValue
import site.addzero.lsi.poet.LsiSuperExpression
import site.addzero.lsi.poet.LsiThisExpression
import site.addzero.lsi.poet.LsiThrowStatement
import site.addzero.lsi.poet.LsiTypeName
import site.addzero.lsi.poet.LsiTypeExpression
import site.addzero.lsi.poet.LsiTypeSpec
import site.addzero.lsi.poet.LsiTypeSpecKind
import site.addzero.lsi.poet.LsiVariableDeclarationStatement
import site.addzero.lsi.poet.LsiWhenCase
import site.addzero.lsi.poet.LsiWhenStatement

private val DESCRIPTION_LSI_CLASS_NAME =
    site.addzero.lsi.poet.LsiClassName.bestGuess("org.babyfish.jimmer.client.Description")
private val ID_VIEW_LIST_LSI_CLASS_NAME =
    site.addzero.lsi.poet.LsiClassName.bestGuess("org.babyfish.jimmer.sql.collection.IdViewList")
private val MANY_TO_MANY_VIEW_LIST_LSI_CLASS_NAME =
    site.addzero.lsi.poet.LsiClassName.bestGuess("org.babyfish.jimmer.sql.collection.ManyToManyViewList")
internal class ImplGenerator(
    private val jacksonTypes: JacksonTypes,
    private val type: ImmutableImplTypeMetadata,
) {

    fun generate(): LsiTypeSpec =
        LsiTypeSpec(
            name = IMPL,
            kind = LsiTypeSpecKind.CLASS,
            modifiers = setOf(LsiModifier.PRIVATE),
            annotations = listOf(generatedAnnotation(type.className)),
            superInterfaces = listOf(
                type.implementorClassName,
                CLONEABLE_LSI_CLASS_NAME,
                SERIALIZABLE_LSI_CLASS_NAME
            ),
            properties = buildProperties(),
            callables = buildCallables()
        )

    private fun buildProperties(): List<LsiPropertySpec> =
        buildList {
            add(visibilityProperty())
            type.fieldProps.forEach { addAll(fields(it)) }
            type.getterProps.forEach { add(getterProperty(it)) }
        }

    private fun buildCallables(): List<LsiCallableSpec> =
        buildList {
            initConstructor()?.let(::add)
            add(cloneCallable())
            add(isLoadedCallable(PropertyDispatchArgKind.PROP_ID))
            add(isLoadedCallable(PropertyDispatchArgKind.PROP_NAME))
            add(isVisibleCallable(PropertyDispatchArgKind.PROP_ID))
            add(isVisibleCallable(PropertyDispatchArgKind.PROP_NAME))
            add(hashCodeCallable(shallow = true))
            add(hashCodeCallable(shallow = false))
            add(parameterizedHashCodeCallable())
            add(equalsCallable(shallow = true))
            add(equalsCallable(shallow = false))
            add(parameterizedEqualsCallable())
            add(toStringCallable())
        }

    private fun visibilityProperty(): LsiPropertySpec =
        LsiPropertySpec(
            name = "__visibility",
            type = VISIBILITY_LSI_CLASS_NAME.copyNullable(true),
            annotations = listOf(jsonIgnoreGetAnnotation()),
            modifiers = setOf(LsiModifier.INTERNAL),
            mutable = true,
            initializer = LsiLiteralExpression(null)
        )

    private fun fields(prop: ImmutableImplFieldMetadata): List<LsiPropertySpec> =
        buildList {
            prop.valueFieldName?.let { valueFieldName ->
                add(
                    LsiPropertySpec(
                        name = valueFieldName,
                        type = prop.valueFieldTypeName
                            ?: error("Internal bug: missing value field type for $valueFieldName"),
                        annotations = listOf(jsonIgnoreGetAnnotation()),
                        modifiers = setOf(LsiModifier.INTERNAL),
                        mutable = true,
                        initializer = valueFieldDefaultValueExpression(prop, valueFieldName)
                    )
                )
            }
            prop.loadedFieldName?.let { loadedFieldName ->
                add(
                    LsiPropertySpec(
                        name = loadedFieldName,
                        type = BOOLEAN_LSI_CLASS_NAME,
                        annotations = listOf(jsonIgnoreGetAnnotation()),
                        modifiers = setOf(LsiModifier.INTERNAL),
                        mutable = true,
                        initializer = LsiLiteralExpression(false)
                    )
                )
            }
        }

    private fun valueFieldDefaultValueExpression(
        prop: ImmutableImplFieldMetadata,
        valueFieldName: String,
    ) = when (prop.valueFieldDefaultValueKind) {
        ImmutableImplDefaultValueKind.NULL -> LsiNullExpression
        ImmutableImplDefaultValueKind.PRIMITIVE_DEFAULT ->
            prop.valueFieldDefaultValueTypeName?.primitiveDefaultValueExpression()
                ?: error("Internal bug: missing primitive default type for $valueFieldName")
        null -> error("Internal bug: missing value field default kind for $valueFieldName")
    }

    private fun getterProperty(prop: ImmutableImplGetterPropMetadata): LsiPropertySpec =
        LsiPropertySpec(
            name = prop.name,
            type = prop.typeName,
            annotations = buildList {
                prop.description?.let {
                    add(
                        LsiAnnotationSpec(
                            type = DESCRIPTION_LSI_CLASS_NAME,
                            members = mapOf("value" to LsiStringAnnotationValue(it))
                        )
                    )
                }
            },
            modifiers = setOf(LsiModifier.OVERRIDE),
            getterStatements = getterStatements(prop)
        )

    private fun getterStatements(prop: ImmutableImplGetterPropMetadata): List<LsiStatement> =
        when (prop.kind) {
            ID_VIEW_LIST ->
                listOf(
                    LsiReturnStatement(
                        LsiNewExpression(
                            type = ID_VIEW_LIST_LSI_CLASS_NAME,
                            arguments = listOf(
                                LsiPropertyAccessExpression(
                                    LsiTypeExpression(
                                        prop.idViewBaseTargetProducerClassName
                                            ?: error("Internal bug: missing id-view target producer for ${prop.name}")
                                    ),
                                    "type",
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

            ID_VIEW_SCALAR -> idViewScalarGetterStatements(prop)

            MANY_TO_MANY_VIEW ->
                listOf(
                    LsiReturnStatement(
                        LsiNewExpression(
                            type = MANY_TO_MANY_VIEW_LIST_LSI_CLASS_NAME,
                            arguments = listOf(
                                LsiPropertyAccessExpression(
                                    LsiTypeExpression(
                                        type.draftProducerImplementorClassName
                                    ),
                                    prop.deeperPropConstantName
                                        ?: error("Internal bug: missing deeper prop constant for ${prop.name}"),
                                ),
                                semanticPropertyGet(
                                    receiver = LsiThisExpression,
                                    name = prop.manyToManyViewBaseName
                                        ?: error("Internal bug: missing many-to-many view base name for ${prop.name}"),
                                    type = prop.manyToManyViewBaseTypeName
                                        ?: error("Internal bug: missing many-to-many view base type for ${prop.name}"),
                                ),
                            ),
                        )
                    )
                )

            STANDARD -> standardGetterStatements(prop)
        }

    private fun initConstructor(): LsiCallableSpec? {
        if (type.hiddenSlotNames.isEmpty()) {
            return null
        }
        return LsiCallableSpec(
            kind = LsiCallableSpecKind.CONSTRUCTOR,
            statements = buildList {
                add(
                    LsiVariableDeclarationStatement(
                        name = "__visibility",
                        type = VISIBILITY_LSI_CLASS_NAME,
                        initializer = LsiCallExpression(
                            receiver = LsiTypeExpression(VISIBILITY_LSI_CLASS_NAME),
                            name = "of",
                            arguments = listOf(LsiLiteralExpression(type.propsSize)),
                        )
                    )
                )
                for (slotName in type.hiddenSlotNames) {
                    add(
                        LsiExpressionStatement(
                            LsiCallExpression(
                                receiver = LsiNameExpression("__visibility"),
                                name = "show",
                                arguments = listOf(
                                    LsiNameExpression(slotName),
                                    LsiLiteralExpression(false),
                                )
                            )
                        )
                    )
                }
                add(
                    LsiAssignmentStatement(
                        target = LsiPropertyAccessExpression(LsiThisExpression, "__visibility"),
                        expression = LsiNameExpression("__visibility"),
                    )
                )
            }
        )
    }

    private fun cloneCallable(): LsiCallableSpec =
        functionStatements(
            name = "clone",
            returnType = type.draftProducerImplClassName,
            modifiers = setOf(LsiModifier.PUBLIC, LsiModifier.OVERRIDE),
            statements = buildList {
                add(
                    LsiVariableDeclarationStatement(
                        name = "copy",
                        type = type.draftProducerImplClassName,
                        initializer = LsiCastExpression(
                            type.draftProducerImplClassName,
                            LsiCallExpression(
                                receiver = LsiSuperExpression,
                                name = "clone",
                            )
                        )
                    )
                )
                add(
                    LsiVariableDeclarationStatement(
                        name = "originalVisibility",
                        type = VISIBILITY_LSI_CLASS_NAME.copyNullable(true),
                        initializer = LsiPropertyAccessExpression(LsiThisExpression, "__visibility"),
                    )
                )
                add(
                    LsiIfStatement(
                        condition = LsiBinaryExpression(
                            left = LsiNameExpression("originalVisibility"),
                            operator = LsiBinaryOperator.NOT_EQUALS,
                            right = LsiNullExpression,
                        ),
                        thenStatements = listOf(
                            LsiVariableDeclarationStatement(
                                name = "newVisibility",
                                type = VISIBILITY_LSI_CLASS_NAME,
                                initializer = LsiCallExpression(
                                    receiver = LsiTypeExpression(VISIBILITY_LSI_CLASS_NAME),
                                    name = "of",
                                    arguments = listOf(LsiLiteralExpression(type.propsSize)),
                                )
                            ),
                            LsiForRangeStatement(
                                variableName = "propId",
                                from = LsiLiteralExpression(0),
                                until = LsiLiteralExpression(type.propsSize),
                                statements = listOf(
                                    LsiExpressionStatement(
                                        LsiCallExpression(
                                            receiver = LsiNameExpression("newVisibility"),
                                            name = "show",
                                            arguments = listOf(
                                                LsiNameExpression("propId"),
                                                LsiCallExpression(
                                                    receiver = LsiCastExpression(
                                                        type = VISIBILITY_LSI_CLASS_NAME,
                                                        expression = LsiNameExpression("originalVisibility"),
                                                    ),
                                                    name = "visible",
                                                    arguments = listOf(LsiNameExpression("propId")),
                                                ),
                                            ),
                                        )
                                    )
                                )
                            ),
                            LsiAssignmentStatement(
                                target = LsiPropertyAccessExpression(
                                    LsiNameExpression("copy"),
                                    "__visibility",
                                ),
                                expression = LsiNameExpression("newVisibility"),
                            ),
                        ),
                        elseStatements = listOf(
                            LsiAssignmentStatement(
                                target = LsiPropertyAccessExpression(
                                    LsiNameExpression("copy"),
                                    "__visibility",
                                ),
                                expression = LsiNullExpression,
                            )
                        ),
                    )
                )
                add(LsiReturnStatement(LsiNameExpression("copy")))
            }
        )

    private fun isLoadedCallable(argKind: PropertyDispatchArgKind): LsiCallableSpec =
        functionStatements(
            name = "__isLoaded",
            returnType = BOOLEAN_LSI_CLASS_NAME,
            parameters = listOf(LsiParameterSpec("prop", argKind.typeName)),
            modifiers = setOf(LsiModifier.OVERRIDE),
            statements = listOf(isLoadedWhenStatement(argKind))
        )

    private fun hashCodeCallable(shallow: Boolean): LsiCallableSpec =
        functionStatements(
            name = if (shallow) "__shallowHashCode" else "hashCode",
            returnType = INT_LSI_CLASS_NAME,
            modifiers = if (shallow) {
                emptySet()
            } else {
                setOf(LsiModifier.OVERRIDE)
            },
            statements = hashCodeStatements(shallow)
        )

    private fun parameterizedHashCodeCallable(): LsiCallableSpec =
        functionStatements(
            name = "__hashCode",
            returnType = INT_LSI_CLASS_NAME,
            parameters = listOf(LsiParameterSpec("shallow", BOOLEAN_LSI_CLASS_NAME)),
            modifiers = setOf(LsiModifier.OVERRIDE),
            statements = listOf(
                LsiIfStatement(
                    condition = LsiNameExpression("shallow"),
                    thenStatements = listOf(
                        LsiReturnStatement(
                            LsiCallExpression(name = "__shallowHashCode")
                        )
                    ),
                    elseStatements = listOf(
                        LsiReturnStatement(
                            LsiCallExpression(name = "hashCode")
                        )
                    )
                )
            )
        )

    private fun equalsCallable(shallow: Boolean): LsiCallableSpec =
        functionStatements(
            name = if (shallow) "__shallowEquals" else "equals",
            returnType = BOOLEAN_LSI_CLASS_NAME,
            parameters = listOf(LsiParameterSpec("other", ANY_LSI_CLASS_NAME.copyNullable(true))),
            modifiers = if (shallow) {
                emptySet()
            } else {
                setOf(LsiModifier.OVERRIDE)
            },
            statements = equalsStatements(shallow)
        )

    private fun parameterizedEqualsCallable(): LsiCallableSpec =
        functionStatements(
            name = "__equals",
            returnType = BOOLEAN_LSI_CLASS_NAME,
            parameters = listOf(
                LsiParameterSpec("obj", ANY_LSI_CLASS_NAME.copyNullable(true)),
                LsiParameterSpec("shallow", BOOLEAN_LSI_CLASS_NAME)
            ),
            modifiers = setOf(LsiModifier.OVERRIDE),
            statements = listOf(
                LsiIfStatement(
                    condition = LsiNameExpression("shallow"),
                    thenStatements = listOf(
                        LsiReturnStatement(
                            LsiCallExpression(
                                name = "__shallowEquals",
                                arguments = listOf(LsiNameExpression("obj"))
                            )
                        )
                    ),
                    elseStatements = listOf(
                        LsiReturnStatement(
                            LsiCallExpression(
                                name = "equals",
                                arguments = listOf(LsiNameExpression("obj"))
                            )
                        )
                    )
                )
            )
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
            )
        )

    private fun functionStatements(
        name: String,
        returnType: LsiTypeName,
        parameters: List<LsiParameterSpec> = emptyList(),
        modifiers: Set<LsiModifier> = emptySet(),
        statements: List<LsiStatement>,
    ): LsiCallableSpec =
        LsiCallableSpec(
            kind = LsiCallableSpecKind.FUNCTION,
            name = name,
            parameters = parameters,
            returnType = returnType,
            modifiers = modifiers,
            statements = statements,
        )

    private fun whenSubject(argKind: PropertyDispatchArgKind): site.addzero.lsi.poet.LsiExpression =
        if (argKind.usesIndexedSubject) {
            LsiCallExpression(
                receiver = LsiNameExpression("prop"),
                name = "asIndex",
            )
        } else {
            LsiNameExpression("prop")
        }

    private fun propCondition(
        argKind: PropertyDispatchArgKind,
        prop: ImmutableImplStatePropMetadata,
    ): site.addzero.lsi.poet.LsiExpression =
        if (argKind.usesIndexedSubject) {
            LsiNameExpression(prop.slotName)
        } else {
            LsiLiteralExpression(prop.name)
        }

    private fun illegalPropThrow(argKind: PropertyDispatchArgKind): LsiThrowStatement =
        LsiThrowStatement(
            LsiNewExpression(
                type = site.addzero.lsi.poet.LsiClassName.bestGuess(IllegalArgumentException::class.java.name),
                arguments = listOf(
                    LsiBinaryExpression(
                        left = LsiBinaryExpression(
                            left = LsiLiteralExpression("Illegal property ${argKind.illegalKindLabel} "),
                            operator = LsiBinaryOperator.PLUS,
                            right = LsiLiteralExpression(" for \"${type.typeDescription}\": "),
                        ),
                        operator = LsiBinaryOperator.PLUS,
                        right = LsiNameExpression("prop"),
                    )
                )
            )
        )

    private fun propIdByIndexExpression(slotName: String): LsiCallExpression =
        LsiCallExpression(
            receiver = LsiTypeExpression(PROP_ID_LSI_CLASS_NAME),
            name = "byIndex",
            arguments = listOf(LsiNameExpression(slotName)),
        )

    private fun isLoadedCaseStatements(prop: ImmutableImplStatePropMetadata): List<LsiStatement> =
        when (prop.loadKind) {
            LOAD_ID_VIEW_LIST ->
                listOf(
                    LsiReturnStatement(
                        LsiBinaryExpression(
                            left = LsiCallExpression(
                                name = "__isLoaded",
                                arguments = listOf(
                                    propIdByIndexExpression(
                                        prop.basePropSlotName
                                            ?: error("Internal bug: missing base prop slot for ${prop.name}")
                                    )
                                ),
                            ),
                            operator = LsiBinaryOperator.AND,
                            right = LsiCallExpression(
                                receiver = semanticPropertyGet(
                                    receiver = LsiThisExpression,
                                    name = prop.basePropName
                                        ?: error("Internal bug: missing base prop name for ${prop.name}"),
                                    type = prop.basePropTypeName
                                        ?: error("Internal bug: missing base prop type for ${prop.name}"),
                                ),
                                name = "all",
                                arguments = listOf(
                                    LsiLambdaExpression(
                                        mode = LsiLambdaMode.EXPRESSION,
                                        parameterNames = listOf("it"),
                                        expression = LsiCallExpression(
                                            receiver = LsiCastExpression(
                                                type = IMMUTABLE_SPI_LSI_CLASS_NAME,
                                                expression = LsiNameExpression("it"),
                                            ),
                                            name = "__isLoaded",
                                            arguments = listOf(
                                                LsiCallExpression(
                                                    receiver = LsiTypeExpression(PROP_ID_LSI_CLASS_NAME),
                                                    name = "byIndex",
                                                    arguments = listOf(
                                                        staticMemberReference(
                                                            prop.baseTargetDraftClassName
                                                                ?: error("Internal bug: missing base target draft class for ${prop.name}"),
                                                            prop.baseTargetIdSlotName
                                                                ?: error("Internal bug: missing base target id slot for ${prop.name}"),
                                                        )
                                                    ),
                                                )
                                            ),
                                        ),
                                    )
                                ),
                            ),
                        )
                    )
                )

            LOAD_ID_VIEW_SCALAR -> buildIdViewScalarLoadedStatements(prop)

            LOAD_MANY_TO_MANY_VIEW ->
                listOf(
                    LsiReturnStatement(
                        LsiBinaryExpression(
                            left = LsiCallExpression(
                                name = "__isLoaded",
                                arguments = listOf(
                                    propIdByIndexExpression(
                                        prop.basePropSlotName
                                            ?: error("Internal bug: missing many-to-many base slot for ${prop.name}")
                                    )
                                ),
                            ),
                            operator = LsiBinaryOperator.AND,
                            right = LsiCallExpression(
                                receiver = semanticPropertyGet(
                                    receiver = LsiThisExpression,
                                    name = prop.basePropName
                                        ?: error("Internal bug: missing many-to-many base name for ${prop.name}"),
                                    type = prop.basePropTypeName
                                        ?: error("Internal bug: missing many-to-many base type for ${prop.name}"),
                                ),
                                name = "all",
                                arguments = listOf(
                                    LsiLambdaExpression(
                                        mode = LsiLambdaMode.EXPRESSION,
                                        parameterNames = listOf("it"),
                                        expression = LsiCallExpression(
                                            receiver = LsiCastExpression(
                                                type = IMMUTABLE_SPI_LSI_CLASS_NAME,
                                                expression = LsiNameExpression("it"),
                                            ),
                                            name = "__isLoaded",
                                            arguments = listOf(
                                                staticMemberReference(
                                                    type.draftProducerImplementorClassName,
                                                    prop.deeperPropConstantName
                                                        ?: error("Internal bug: missing deeper prop constant for ${prop.name}"),
                                                )
                                            ),
                                        ),
                                    )
                                ),
                            ),
                        )
                    )
                )

            FORMULA ->
                listOf(
                    LsiReturnStatement(
                        andExpressions(
                            prop.formulaDependencies.map { dependency ->
                                if (dependency.slotRefs.size == 1) {
                                    LsiCallExpression(
                                        name = "__isLoaded",
                                        arguments = listOf(
                                            propIdByIndexExpression(dependency.slotRefs[0].slotName)
                                        ),
                                    )
                                } else {
                                    LsiCallExpression(
                                        receiver = LsiTypeExpression(IMMUTABLE_OBJECTS_LSI_CLASS_NAME),
                                        name = "isLoadedChain",
                                        arguments = buildList {
                                            add(LsiThisExpression)
                                            dependency.slotRefs.forEach { depProp ->
                                                add(
                                                    LsiCallExpression(
                                                        receiver = LsiTypeExpression(PROP_ID_LSI_CLASS_NAME),
                                                        name = "byIndex",
                                                        arguments = listOf(
                                                            staticMemberReference(
                                                                depProp.declaringTypeDraftClassName,
                                                                depProp.slotName,
                                                            )
                                                        ),
                                                    )
                                                )
                                            }
                                        },
                                    )
                                }
                            }
                        )
                    )
                )

            LOAD_STANDARD ->
                listOf(
                    LsiReturnStatement(
                        loadedExpression(prop)
                    )
                )
        }

    private fun buildIdViewScalarLoadedStatements(
        prop: ImmutableImplStatePropMetadata,
    ): List<LsiStatement> {
        val basePropName =
            prop.basePropName
                ?: error("Internal bug: missing base prop name for ${prop.name}")
        val basePropExpression =
            semanticPropertyGet(
                receiver = LsiThisExpression,
                name = basePropName,
                type = prop.basePropTypeName
                    ?: error("Internal bug: missing base prop type for ${prop.name}"),
            )
        val baseLoadedCall =
            LsiCallExpression(
                name = "__isLoaded",
                arguments = listOf(
                    propIdByIndexExpression(
                        prop.basePropSlotName
                            ?: error("Internal bug: missing base prop slot for ${prop.name}")
                    )
                ),
            )
        val targetLoadedCall = LsiCallExpression(
            receiver = LsiCastExpression(
                type = IMMUTABLE_SPI_LSI_CLASS_NAME.copyNullable(prop.basePropNullable),
                expression = basePropExpression,
            ),
            name = "__isLoaded",
            arguments = listOf(
                LsiCallExpression(
                    receiver = LsiTypeExpression(PROP_ID_LSI_CLASS_NAME),
                    name = "byIndex",
                    arguments = listOf(
                        staticMemberReference(
                            prop.baseTargetDraftClassName
                                ?: error("Internal bug: missing base target draft class for ${prop.name}"),
                            prop.baseTargetIdSlotName
                                ?: error("Internal bug: missing base target id slot for ${prop.name}"),
                        )
                    ),
                )
            ),
        )
        if (!prop.basePropNullable) {
            return listOf(
                LsiReturnStatement(
                    LsiBinaryExpression(
                        left = baseLoadedCall,
                        operator = LsiBinaryOperator.AND,
                        right = targetLoadedCall,
                    )
                )
            )
        }
        return listOf(
            LsiIfStatement(
                condition = LsiBinaryExpression(
                    left = baseLoadedCall,
                    operator = LsiBinaryOperator.EQUALS,
                    right = LsiLiteralExpression(false),
                ),
                thenStatements = listOf(LsiReturnStatement(LsiLiteralExpression(false))),
            ),
            LsiVariableDeclarationStatement(
                name = "__baseRef",
                type = IMMUTABLE_SPI_LSI_CLASS_NAME.copyNullable(true),
                initializer = LsiCastExpression(
                    type = IMMUTABLE_SPI_LSI_CLASS_NAME.copyNullable(true),
                    expression = basePropExpression,
                ),
            ),
            LsiIfStatement(
                condition = LsiBinaryExpression(
                    left = LsiNameExpression("__baseRef"),
                    operator = LsiBinaryOperator.EQUALS,
                    right = LsiNullExpression,
                ),
                thenStatements = listOf(LsiReturnStatement(LsiLiteralExpression(true))),
            ),
            LsiReturnStatement(
                LsiCallExpression(
                    receiver = LsiCastExpression(
                        type = IMMUTABLE_SPI_LSI_CLASS_NAME,
                        expression = LsiNameExpression("__baseRef"),
                    ),
                    name = "__isLoaded",
                    arguments = targetLoadedCall.arguments,
                )
            ),
        )
    }

    private fun andExpressions(expressions: List<site.addzero.lsi.poet.LsiExpression>): site.addzero.lsi.poet.LsiExpression =
        expressions.reduce { acc, expression ->
            LsiBinaryExpression(
                left = acc,
                operator = LsiBinaryOperator.AND,
                right = expression,
            )
        }

    private fun idViewScalarGetterStatements(
        prop: ImmutableImplGetterPropMetadata,
    ): List<LsiStatement> {
        val baseName =
            prop.idViewBaseName
                ?: error("Internal bug: missing id-view base name for ${prop.name}")
        val targetIdName =
            prop.idViewTargetIdPropName
                ?: error("Internal bug: missing id-view target id prop for ${prop.name}")
        if (!prop.isNullable) {
            return listOf(
                LsiReturnStatement(
                    semanticPropertyGet(
                        receiver = semanticPropertyGet(
                            receiver = LsiThisExpression,
                            name = baseName,
                            type = prop.idViewBaseTypeName
                                ?: error("Internal bug: missing id-view base type for ${prop.name}"),
                        ),
                        name = targetIdName,
                        type = prop.typeName,
                    )
                )
            )
        }
        return listOf(
            LsiVariableDeclarationStatement(
                name = "__base",
                type = prop.idViewBaseTypeName
                    ?: error("Internal bug: missing id-view base type for ${prop.name}"),
                initializer = semanticPropertyGet(
                    receiver = LsiThisExpression,
                    name = baseName,
                    type = prop.idViewBaseTypeName,
                ),
            ),
            LsiIfStatement(
                condition = LsiBinaryExpression(
                    left = LsiNameExpression("__base"),
                    operator = LsiBinaryOperator.EQUALS,
                    right = LsiNullExpression,
                ),
                thenStatements = listOf(
                    LsiReturnStatement(LsiNullExpression)
                ),
            ),
            LsiReturnStatement(
                semanticPropertyGet(
                    receiver = LsiNameExpression("__base"),
                    name = targetIdName,
                    type = prop.typeName,
                )
            ),
        )
    }

    private fun semanticPropertyGet(
        receiver: site.addzero.lsi.poet.LsiExpression,
        name: String,
        type: LsiTypeName,
    ): LsiPropertyGetExpression =
        LsiPropertyGetExpression(
            receiver = receiver,
            name = name,
            type = type,
        )

    private fun standardGetterStatements(
        prop: ImmutableImplGetterPropMetadata,
    ): List<LsiStatement> =
        listOf(
            LsiIfStatement(
                condition = getterUnloadedExpression(prop),
                thenStatements = listOf(
                    LsiThrowStatement(
                        LsiNewExpression(
                            type = UNLOADED_EXCEPTION_LSI_CLASS_NAME,
                            arguments = listOf(
                                LsiJavaClassExpression(prop.declaringTypeClassName),
                                LsiLiteralExpression(prop.name),
                            ),
                        )
                    )
                ),
            ),
            LsiReturnStatement(
                LsiPropertyAccessExpression(
                    LsiThisExpression,
                    prop.valueFieldName
                        ?: error("Internal bug: missing value field for ${prop.name}"),
                )
            ),
        )

    private fun getterUnloadedExpression(
        prop: ImmutableImplGetterPropMetadata,
    ): site.addzero.lsi.poet.LsiExpression =
        when {
            prop.loadedFieldName != null ->
                LsiBinaryExpression(
                    left = LsiPropertyAccessExpression(LsiThisExpression, prop.loadedFieldName),
                    operator = LsiBinaryOperator.EQUALS,
                    right = LsiLiteralExpression(false),
                )

            prop.valueFieldName != null ->
                LsiBinaryExpression(
                    left = LsiPropertyAccessExpression(LsiThisExpression, prop.valueFieldName),
                    operator = LsiBinaryOperator.EQUALS,
                    right = LsiNullExpression,
                )

            else -> error("Internal bug: missing unloaded state for ${prop.name}")
        }

    private fun loadedExpression(
        prop: ImmutableImplStatePropMetadata,
    ): site.addzero.lsi.poet.LsiExpression =
        when {
            prop.loadedFieldName != null ->
                LsiPropertyAccessExpression(LsiThisExpression, prop.loadedFieldName)

            prop.valueFieldName != null ->
                LsiBinaryExpression(
                    left = LsiPropertyAccessExpression(LsiThisExpression, prop.valueFieldName),
                    operator = LsiBinaryOperator.NOT_EQUALS,
                    right = LsiNullExpression,
                )

            else -> error("Internal bug: missing loaded state for ${prop.name}")
        }

    private fun isLoadedWhenStatement(argKind: PropertyDispatchArgKind): LsiWhenStatement =
        LsiWhenStatement(
            subject = whenSubject(argKind),
            cases = buildList {
                if (argKind.usesIndexedSubject) {
                    add(
                        LsiWhenCase(
                            conditions = listOf(LsiLiteralExpression(-1)),
                            statements = listOf(
                                LsiReturnStatement(
                                    LsiCallExpression(
                                        name = "__isLoaded",
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
                for (prop in type.stateProps) {
                    add(
                        LsiWhenCase(
                            conditions = listOf(propCondition(argKind, prop)),
                            statements = isLoadedCaseStatements(prop),
                        )
                    )
                }
            },
            elseStatements = listOf(illegalPropThrow(argKind)),
        )

    private fun isVisibleCallable(argKind: PropertyDispatchArgKind): LsiCallableSpec =
        functionStatements(
            name = "__isVisible",
            returnType = BOOLEAN_LSI_CLASS_NAME,
            parameters = listOf(LsiParameterSpec("prop", argKind.typeName)),
            modifiers = setOf(LsiModifier.OVERRIDE),
            statements = isVisibleStatements(argKind),
        )

    private fun isVisibleStatements(argKind: PropertyDispatchArgKind): List<LsiStatement> =
        buildList {
            add(
                LsiVariableDeclarationStatement(
                    name = "__visibility",
                    type = VISIBILITY_LSI_CLASS_NAME.copyNullable(true),
                    initializer = LsiPropertyAccessExpression(LsiThisExpression, "__visibility"),
                )
            )
            add(
                LsiIfStatement(
                    condition = LsiBinaryExpression(
                        left = LsiNameExpression("__visibility"),
                        operator = LsiBinaryOperator.EQUALS,
                        right = LsiNullExpression,
                    ),
                    thenStatements = listOf(LsiReturnStatement(LsiLiteralExpression(true))),
                )
            )
            add(
                LsiWhenStatement(
                    subject = if (argKind.usesIndexedSubject) {
                        LsiCallExpression(
                            receiver = LsiNameExpression("prop"),
                            name = "asIndex",
                        )
                    } else {
                        LsiNameExpression("prop")
                    },
                    cases = buildList {
                        if (argKind.usesIndexedSubject) {
                            add(
                                LsiWhenCase(
                                    conditions = listOf(LsiLiteralExpression(-1)),
                                    statements = listOf(
                                        LsiReturnStatement(
                                            LsiCallExpression(
                                                name = "__isVisible",
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
                        for (prop in type.stateProps) {
                            add(
                                LsiWhenCase(
                                    conditions = listOf(
                                        if (argKind.usesIndexedSubject) {
                                            LsiNameExpression(prop.slotName)
                                        } else {
                                            LsiLiteralExpression(prop.name)
                                        }
                                    ),
                                    statements = listOf(
                                        LsiReturnStatement(
                                            LsiCallExpression(
                                                receiver = LsiNameExpression("__visibility"),
                                                name = "visible",
                                                arguments = listOf(LsiNameExpression(prop.slotName)),
                                            )
                                        )
                                    )
                                )
                            )
                        }
                    },
                    elseStatements = listOf(LsiReturnStatement(LsiLiteralExpression(true))),
                )
            )
        }

    private fun hashCodeStatements(shallow: Boolean): List<LsiStatement> =
        buildList {
            add(
                LsiVariableDeclarationStatement(
                    name = "hash",
                    type = INT_LSI_CLASS_NAME,
                    mutable = true,
                    initializer = LsiLiteralExpression(0),
                )
            )
            add(
                LsiIfStatement(
                    condition = LsiBinaryExpression(
                        left = LsiPropertyAccessExpression(LsiThisExpression, "__visibility"),
                        operator = LsiBinaryOperator.NOT_EQUALS,
                        right = LsiNullExpression,
                    ),
                    thenStatements = listOf(
                        LsiAssignmentStatement(
                            target = LsiNameExpression("hash"),
                            expression = LsiCallExpression(
                                receiver = LsiPropertyAccessExpression(LsiThisExpression, "__visibility"),
                                name = "hashCode",
                            )
                        )
                    )
                )
            )
            for (prop in type.stateProps) {
                val valueFieldName = prop.valueFieldName ?: continue
                val valueExpression = LsiPropertyAccessExpression(LsiThisExpression, valueFieldName)
                val propHashExpression =
                    if (shallow && prop.isAssociation) {
                        LsiCallExpression(
                            receiver = LsiTypeExpression(SYSTEM_LSI_CLASS_NAME),
                            name = "identityHashCode",
                            arguments = listOf(valueExpression),
                        )
                    } else if (prop.isNullable) {
                        val hashVarName = "__${prop.name}Hash"
                        add(
                            LsiVariableDeclarationStatement(
                                name = hashVarName,
                                type = INT_LSI_CLASS_NAME,
                                mutable = true,
                                initializer = LsiLiteralExpression(0),
                            )
                        )
                        add(
                            LsiIfStatement(
                                condition = LsiBinaryExpression(
                                    left = valueExpression,
                                    operator = LsiBinaryOperator.NOT_EQUALS,
                                    right = LsiNullExpression,
                                ),
                                thenStatements = listOf(
                                    LsiAssignmentStatement(
                                        target = LsiNameExpression(hashVarName),
                                        expression = LsiCallExpression(
                                            receiver = valueExpression,
                                            name = "hashCode",
                                        )
                                    )
                                )
                            )
                        )
                        LsiNameExpression(hashVarName)
                    } else {
                        LsiCallExpression(receiver = valueExpression, name = "hashCode")
                    }
                add(
                    LsiIfStatement(
                        condition = loadedExpression(prop),
                        thenStatements = buildList {
                            add(
                                LsiAssignmentStatement(
                                    target = LsiNameExpression("hash"),
                                    expression = LsiBinaryExpression(
                                        left = LsiBinaryExpression(
                                            left = LsiLiteralExpression(31),
                                            operator = LsiBinaryOperator.TIMES,
                                            right = LsiNameExpression("hash"),
                                        ),
                                        operator = LsiBinaryOperator.PLUS,
                                        right = propHashExpression,
                                    )
                                )
                            )
                            if (!shallow && prop.isId) {
                                add(LsiReturnStatement(LsiNameExpression("hash")))
                            }
                        }
                    )
                )
            }
            add(LsiReturnStatement(LsiNameExpression("hash")))
        }

    private fun equalsStatements(shallow: Boolean): List<LsiStatement> =
        buildList {
            add(
                LsiVariableDeclarationStatement(
                    name = "__other",
                    type = type.draftProducerImplementorClassName.copyNullable(true),
                    initializer = LsiSafeCastExpression(
                        type.draftProducerImplementorClassName.copyNullable(true),
                        LsiNameExpression("other")
                    )
                )
            )
            add(
                LsiIfStatement(
                    condition = LsiBinaryExpression(
                        left = LsiNameExpression("__other"),
                        operator = LsiBinaryOperator.EQUALS,
                        right = LsiNullExpression,
                    ),
                    thenStatements = listOf(LsiReturnStatement(LsiLiteralExpression(false))),
                )
            )
            for (prop in type.stateProps) {
                val propIdExpression = LsiCallExpression(
                    receiver = LsiTypeExpression(PROP_ID_LSI_CLASS_NAME),
                    name = "byIndex",
                    arguments = listOf(LsiNameExpression(prop.slotName)),
                )
                add(
                    LsiIfStatement(
                        condition = LsiBinaryExpression(
                            left = LsiCallExpression(
                                name = "__isVisible",
                                arguments = listOf(propIdExpression),
                            ),
                            operator = LsiBinaryOperator.NOT_EQUALS,
                            right = LsiCallExpression(
                                receiver = LsiNameExpression("__other"),
                                name = "__isVisible",
                                arguments = listOf(propIdExpression),
                            ),
                        ),
                        thenStatements = listOf(LsiReturnStatement(LsiLiteralExpression(false))),
                    )
                )
                val valueFieldName = prop.valueFieldName ?: continue
                val localLoadedName = "__${prop.name}Loaded"
                add(
                    LsiVariableDeclarationStatement(
                        name = localLoadedName,
                        type = BOOLEAN_LSI_CLASS_NAME,
                        initializer = loadedExpression(prop)
                    )
                )
                add(
                    LsiIfStatement(
                        condition = LsiBinaryExpression(
                            left = LsiNameExpression(localLoadedName),
                            operator = LsiBinaryOperator.NOT_EQUALS,
                            right = LsiCallExpression(
                                receiver = LsiNameExpression("__other"),
                                name = "__isLoaded",
                                arguments = listOf(propIdExpression),
                            )
                        ),
                        thenStatements = listOf(LsiReturnStatement(LsiLiteralExpression(false))),
                    )
                )
                val thisValue = LsiPropertyAccessExpression(LsiThisExpression, valueFieldName)
                val otherValue = semanticPropertyGet(
                    receiver = LsiNameExpression("__other"),
                    name = prop.name,
                    type = prop.typeName,
                )
                if (prop.isId && !shallow) {
                    add(
                        LsiIfStatement(
                            condition = LsiNameExpression(localLoadedName),
                            thenStatements = listOf(
                                LsiReturnStatement(
                                    LsiBinaryExpression(
                                        left = thisValue,
                                        operator = LsiBinaryOperator.EQUALS,
                                        right = otherValue,
                                    )
                                )
                            )
                        )
                    )
                } else {
                    add(
                        LsiIfStatement(
                            condition = LsiBinaryExpression(
                                left = LsiNameExpression(localLoadedName),
                                operator = LsiBinaryOperator.AND,
                                right = LsiBinaryExpression(
                                    left = thisValue,
                                    operator = if (shallow && prop.isAssociation) {
                                        LsiBinaryOperator.IDENTITY_NOT_EQUALS
                                    } else {
                                        LsiBinaryOperator.NOT_EQUALS
                                    },
                                    right = otherValue,
                                )
                            ),
                            thenStatements = listOf(LsiReturnStatement(LsiLiteralExpression(false))),
                        )
                    )
                }
            }
            add(LsiReturnStatement(LsiLiteralExpression(true)))
        }

    private fun jsonIgnoreGetAnnotation(): LsiAnnotationSpec =
        LsiAnnotationSpec(
            type = jacksonTypes.jsonIgnore,
            useSiteTarget = LsiAnnotationUseSiteTarget.GET
        )

    private fun staticMemberReference(
        ownerType: site.addzero.lsi.poet.LsiClassName,
        memberName: String,
    ): LsiPropertyAccessExpression =
        LsiPropertyAccessExpression(
            receiver = LsiTypeExpression(ownerType),
            name = memberName,
        )
}
