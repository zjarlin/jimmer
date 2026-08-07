package org.babyfish.jimmer.compiler.immutable

import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.model.LsiDeclaredType
import site.addzero.lsi.model.LsiPrimitiveKind
import site.addzero.lsi.model.LsiPrimitiveType
import site.addzero.lsi.model.LsiTypeRef
import site.addzero.lsi.poet.LsiPoetAccessor
import site.addzero.lsi.poet.LsiPoetAnnotation
import site.addzero.lsi.poet.LsiPoetAnnotationArgument
import site.addzero.lsi.poet.LsiPoetAnnotationArgumentLayout
import site.addzero.lsi.poet.LsiPoetAnnotationValue
import site.addzero.lsi.poet.LsiPoetBodyStyle
import site.addzero.lsi.poet.LsiPoetCodeBlock
import site.addzero.lsi.poet.LsiPoetCodeBuilder
import site.addzero.lsi.poet.LsiPoetConstructor
import site.addzero.lsi.poet.LsiPoetFunction
import site.addzero.lsi.poet.LsiPoetMember
import site.addzero.lsi.poet.LsiPoetModifier
import site.addzero.lsi.poet.LsiPoetNameStyle
import site.addzero.lsi.poet.LsiPoetParameter
import site.addzero.lsi.poet.LsiPoetProperty
import site.addzero.lsi.poet.LsiPoetType
import site.addzero.lsi.poet.LsiPoetTypeKind

/**
 * 将 Kotlin Draft 的运行时实现降低为纯 LSI Poet 类型。
 */
internal class ImmutableDraftKotlinRuntimePoet(
    private val context: ImmutableDraftKotlinPoetContext,
) {

    private val type = context.type

    fun implementor(): LsiPoetType {
        return LsiPoetType(
            name = KOTLIN_DRAFT_IMPLEMENTOR,
            kind = LsiPoetTypeKind.INTERFACE,
            annotations = listOf(context.generatedByAnnotation(), propertyOrderAnnotation()),
            modifiers = setOf(LsiPoetModifier.PRIVATE, LsiPoetModifier.ABSTRACT),
            superInterfaces = listOf(context.modelType, IMMUTABLE_SPI_TYPE),
            members = buildList {
                add(dummyJacksonProperty())
                add(implementorGet(DraftPropertyArgument.ID))
                add(implementorGet(DraftPropertyArgument.NAME))
                add(
                    LsiPoetFunction(
                        name = "__type",
                        modifiers = setOf(LsiPoetModifier.OVERRIDE),
                        returnType = KOTLIN_DRAFT_IMMUTABLE_TYPE,
                        body = draftCode {
                            returnValue {
                                type(context.producerType)
                                text(".type")
                            }
                        },
                    )
                )
                implementorCompanion()?.let(::add)
            },
        )
    }

    fun impl(): LsiPoetType {
        return LsiPoetType(
            name = KOTLIN_DRAFT_IMPL,
            kind = LsiPoetTypeKind.CLASS,
            annotations = listOf(context.generatedByAnnotation()),
            modifiers = setOf(LsiPoetModifier.PRIVATE),
            superInterfaces = listOf(
                context.implementorType,
                KOTLIN_CLONEABLE_TYPE,
                KOTLIN_SERIALIZABLE_TYPE,
            ),
            members = buildList {
                add(visibilityProperty())
                context.propsInDeclarationOrder.forEach { prop -> addStorageProperties(prop) }
                visibilityConstructor()?.let(::add)
                context.propsInDeclarationOrder.forEach { prop -> implProperty(prop)?.let(::add) }
                add(cloneFunction())
                add(implIsLoaded(DraftPropertyArgument.ID))
                add(implIsLoaded(DraftPropertyArgument.NAME))
                add(implIsVisible(DraftPropertyArgument.ID))
                add(implIsVisible(DraftPropertyArgument.NAME))
                add(hashCodeFunction(shallow = true))
                add(hashCodeFunction(shallow = false))
                add(
                    LsiPoetFunction(
                        name = "__hashCode",
                        modifiers = setOf(LsiPoetModifier.OVERRIDE),
                        parameters = listOf(LsiPoetParameter("shallow", KOTLIN_DRAFT_BOOLEAN_TYPE)),
                        returnType = KOTLIN_DRAFT_INT_TYPE,
                        body = draftCode {
                            returnValue {
                                text("if (shallow) __shallowHashCode() else hashCode()")
                            }
                        },
                    )
                )
                add(equalsFunction(shallow = true))
                add(equalsFunction(shallow = false))
                add(
                    LsiPoetFunction(
                        name = "__equals",
                        modifiers = setOf(LsiPoetModifier.OVERRIDE),
                        parameters = listOf(
                            LsiPoetParameter("obj", KOTLIN_DRAFT_ANY_TYPE.withDraftRootNullability(true)),
                            LsiPoetParameter("shallow", KOTLIN_DRAFT_BOOLEAN_TYPE),
                        ),
                        returnType = KOTLIN_DRAFT_BOOLEAN_TYPE,
                        body = draftCode {
                            returnValue {
                                text("if (shallow) __shallowEquals(obj) else equals(obj)")
                            }
                        },
                    )
                )
                add(toStringFunction())
            },
        )
    }

    fun draftImpl(): LsiPoetType {
        return LsiPoetType(
            name = KOTLIN_DRAFT_DRAFT_IMPL,
            kind = LsiPoetTypeKind.CLASS,
            annotations = listOf(context.generatedByAnnotation()),
            modifiers = setOf(LsiPoetModifier.INTERNAL),
            superInterfaces = listOf(context.implementorType, context.draftType, DRAFT_SPI_TYPE),
            primaryConstructor = LsiPoetConstructor(
                parameters = listOf(
                    LsiPoetParameter("ctx", DRAFT_CONTEXT_TYPE.withDraftRootNullability(true)),
                    LsiPoetParameter("base", context.modelType.withDraftRootNullability(true)),
                ),
            ),
            members = buildList {
                addAll(draftFields())
                add(delegateBooleanFunction("__isLoaded", DraftPropertyArgument.ID))
                add(delegateBooleanFunction("__isLoaded", DraftPropertyArgument.NAME))
                add(delegateBooleanFunction("__isVisible", DraftPropertyArgument.ID))
                add(delegateBooleanFunction("__isVisible", DraftPropertyArgument.NAME))
                add(draftHashCodeFunction())
                add(draftSpiHashCodeFunction())
                add(draftEqualsFunction())
                add(draftSpiEqualsFunction())
                add(toStringFunction())
                context.propsInDeclarationOrder.forEach { prop ->
                    add(draftImplProperty(prop))
                    draftAutoCreateFunction(prop)?.let(::add)
                    draftReferenceFunction(prop)?.let(::add)
                    context.addAssociatedIdProperty(prop, withImplementation = true)?.let(::add)
                }
                add(unloadFunction(DraftPropertyArgument.ID))
                add(unloadFunction(DraftPropertyArgument.NAME))
                add(setFunction(DraftPropertyArgument.ID))
                add(setFunction(DraftPropertyArgument.NAME))
                add(showFunction(DraftPropertyArgument.ID))
                add(showFunction(DraftPropertyArgument.NAME))
                add(draftContextFunction())
                add(resolveFunction())
                add(isResolvedFunction())
                add(contextFunction())
                add(unwrapFunction())
                ImmutableDraftKotlinValidationPoet.companion(type)?.let(::add)
            },
        )
    }

    private fun dummyJacksonProperty(): LsiPoetProperty {
        return LsiPoetProperty(
            name = "dummyPropForJacksonError__",
            type = KOTLIN_DRAFT_INT_TYPE,
            mutable = false,
            getter = LsiPoetAccessor(
                body = draftCode {
                    text("throw ")
                    type(IMMUTABLE_MODULE_REQUIRED_EXCEPTION_TYPE)
                    text("()")
                },
                bodyStyle = LsiPoetBodyStyle.EXPRESSION,
            ),
        )
    }

    private fun propertyOrderAnnotation(): LsiPoetAnnotation {
        val propertyNames = buildList {
            add("dummyPropForJacksonError__")
            addAll(type.propsBySlot.map(JimmerImmutableDraftPropPlan::name))
        }
        return LsiPoetAnnotation(
            type = JSON_PROPERTY_ORDER_TYPE_ID,
            arguments = propertyNames.map { propertyName ->
                LsiPoetAnnotationArgument.Positional(
                    LsiPoetAnnotationValue.StringValue(propertyName)
                )
            },
            argumentLayout = LsiPoetAnnotationArgumentLayout.SINGLE_LINE,
        )
    }

    private fun implementorGet(argument: DraftPropertyArgument): LsiPoetFunction {
        return LsiPoetFunction(
            name = "__get",
            modifiers = setOf(LsiPoetModifier.OVERRIDE),
            parameters = listOf(LsiPoetParameter("prop", argument.type)),
            returnType = KOTLIN_DRAFT_ANY_TYPE.withDraftRootNullability(true),
            body = draftCode {
                addPropertyWhen(
                    argument = argument,
                    expression = { prop -> draftCode { name(prop.name) } },
                )
            },
            bodyStyle = LsiPoetBodyStyle.EXPRESSION,
        )
    }

    private fun implementorCompanion(): LsiPoetType? {
        val deeperProps = context.propsInDeclarationOrder.filter { prop ->
            prop.kotlinDeeperPropIdName != null
        }
        if (deeperProps.isEmpty()) {
            return null
        }
        return LsiPoetType(
            name = "Companion",
            kind = LsiPoetTypeKind.OBJECT,
            modifiers = setOf(LsiPoetModifier.COMPANION),
            members = deeperProps.map { prop ->
                LsiPoetProperty(
                    name = requireNotNull(prop.kotlinDeeperPropIdName),
                    nameStyle = LsiPoetNameStyle.KOTLIN_ESCAPED,
                    type = KOTLIN_DRAFT_PROP_ID_TYPE,
                    mutable = false,
                    initializer = draftCode {
                        type(context.producerType)
                        text(".type.getProp(")
                        string(prop.name)
                        text(").getManyToManyViewBaseDeeperProp().getId()")
                    },
                )
            },
        )
    }

    private fun visibilityProperty(): LsiPoetProperty {
        return LsiPoetProperty(
            name = VISIBILITY_FIELD,
            type = VISIBILITY_TYPE.withDraftRootNullability(true),
            mutable = true,
            annotations = listOf(context.jsonIgnoreAnnotation()),
            modifiers = setOf(LsiPoetModifier.INTERNAL),
            initializer = draftCode { text("null") },
        )
    }

    private fun MutableList<LsiPoetMember>.addStorageProperties(
        prop: JimmerImmutableDraftPropPlan,
    ) {
        prop.valueFieldName?.let { fieldName ->
            val fieldType = if (prop.list) {
                draftDeclaredType(NON_SHARED_LIST_TYPE_ID, context.propElementType(prop))
                    .withDraftRootNullability(true)
            } else {
                context.propType(prop).withDraftRootNullability(!prop.primitive)
            }
            add(
                LsiPoetProperty(
                    name = fieldName,
                    nameStyle = LsiPoetNameStyle.KOTLIN_ESCAPED,
                    type = fieldType,
                    mutable = true,
                    annotations = listOf(context.jsonIgnoreAnnotation()),
                    modifiers = setOf(LsiPoetModifier.INTERNAL),
                    initializer = primitiveDefault(prop),
                )
            )
        }
        prop.loadedStateFieldName?.let { fieldName ->
            add(
                LsiPoetProperty(
                    name = fieldName,
                    nameStyle = LsiPoetNameStyle.KOTLIN_ESCAPED,
                    type = KOTLIN_DRAFT_BOOLEAN_TYPE,
                    mutable = true,
                    annotations = listOf(context.jsonIgnoreAnnotation()),
                    modifiers = setOf(LsiPoetModifier.INTERNAL),
                    initializer = draftCode { text("false") },
                )
            )
        }
    }

    private fun visibilityConstructor(): LsiPoetConstructor? {
        val hiddenProps = context.propsInDeclarationOrder.filter { prop -> !prop.valueState.hasValue }
        if (hiddenProps.isEmpty()) {
            return null
        }
        return LsiPoetConstructor(
            body = draftCode {
                statement {
                    text("val __visibility = ")
                    type(VISIBILITY_TYPE)
                    text(".of(${type.propsBySlot.size})")
                }
                hiddenProps.forEach { prop ->
                    statement {
                        text("__visibility.show(")
                        name(prop.slotName)
                        text(", false)")
                    }
                }
                statement { text("this.$VISIBILITY_FIELD = __visibility") }
            },
        )
    }

    private fun implProperty(prop: JimmerImmutableDraftPropPlan): LsiPoetProperty? {
        if (prop.languageFormula) {
            return null
        }
        return LsiPoetProperty(
            name = prop.name,
            nameStyle = LsiPoetNameStyle.KOTLIN_ESCAPED,
            type = context.propType(prop),
            mutable = false,
            modifiers = setOf(LsiPoetModifier.OVERRIDE),
            getter = LsiPoetAccessor(body = implGetter(prop)),
        )
    }

    private fun implGetter(prop: JimmerImmutableDraftPropPlan): LsiPoetCodeBlock {
        val idViewBase = prop.idViewBasePropId?.let(context::prop)
        val manyToManyBase = prop.manyToManyBasePropId?.let(context::prop)
        return draftCode {
            when {
                idViewBase != null && prop.list -> {
                    val targetTypeId = requireNotNull(idViewBase.targetTypeId)
                    returnValue {
                        type(ID_VIEW_LIST_TYPE)
                        text("(")
                        type(context.producerType(targetTypeId))
                        text(".type, ")
                        name(idViewBase.name)
                        text(")")
                    }
                }
                idViewBase != null -> {
                    val targetId = context.type(requireNotNull(idViewBase.targetTypeId))
                        .propsById
                        .getValue(requireNotNull(idViewBase.targetIdPropId))
                    returnValue {
                        name(idViewBase.name)
                        text(if (prop.nullable) "?." else ".")
                        name(targetId.name)
                    }
                }
                manyToManyBase != null -> {
                    returnValue {
                        type(MANY_TO_MANY_VIEW_LIST_TYPE)
                        text("(")
                        type(context.implementorType)
                        text(".")
                        name(requireNotNull(prop.kotlinDeeperPropIdName))
                        text(", ")
                        name(manyToManyBase.name)
                        text(")")
                    }
                }
                else -> {
                    val valueField = requireNotNull(prop.valueFieldName)
                    if (prop.loadedStateFieldName == null) {
                        statement {
                            text("val ")
                            name(valueField)
                            text(" = this.")
                            name(valueField)
                        }
                    }
                    beginControlFlow {
                        val loadedState = prop.loadedStateFieldName
                        if (loadedState != null) {
                            text("if (!")
                            name(loadedState)
                            text(")")
                        } else {
                            text("if (")
                            name(valueField)
                            text(" === null)")
                        }
                    }
                    statement {
                        text("throw ")
                        type(UNLOADED_EXCEPTION_TYPE)
                        text("(")
                        type(context.modelType(prop.sourceDeclaringTypeId))
                        text("::class.java, ")
                        string(prop.name)
                        text(")")
                    }
                    endControlFlow()
                    returnValue { name(valueField) }
                }
            }
        }
    }

    private fun cloneFunction(): LsiPoetFunction {
        return LsiPoetFunction(
            name = "clone",
            modifiers = setOf(LsiPoetModifier.PUBLIC, LsiPoetModifier.OVERRIDE),
            returnType = context.implType,
            body = draftCode {
                statement {
                    text("val copy = super.clone() as ")
                    type(context.implType)
                }
                statement { text("val originalVisibility = this.$VISIBILITY_FIELD") }
                beginControlFlow { text("if (originalVisibility != null)") }
                statement {
                    text("val newVisibility = ")
                    type(VISIBILITY_TYPE)
                    text(".of(${type.propsBySlot.size})")
                }
                beginControlFlow { text("for (propId in 0 until ${type.propsBySlot.size})") }
                statement { text("newVisibility.show(propId, originalVisibility.visible(propId))") }
                endControlFlow()
                statement { text("copy.$VISIBILITY_FIELD = newVisibility") }
                nextControlFlow { text("else") }
                statement { text("copy.$VISIBILITY_FIELD = null") }
                endControlFlow()
                returnValue { text("copy") }
            },
        )
    }

    private fun implIsLoaded(argument: DraftPropertyArgument): LsiPoetFunction {
        return LsiPoetFunction(
            name = "__isLoaded",
            modifiers = setOf(LsiPoetModifier.OVERRIDE),
            parameters = listOf(LsiPoetParameter("prop", argument.type)),
            returnType = KOTLIN_DRAFT_BOOLEAN_TYPE,
            body = draftCode {
                addPropertyWhen(
                    argument = argument,
                    expression = ::loadedExpression,
                    idNameFallback = draftCode { text("__isLoaded(prop.asName())") },
                )
            },
            bodyStyle = LsiPoetBodyStyle.EXPRESSION,
        )
    }

    private fun loadedExpression(prop: JimmerImmutableDraftPropPlan): LsiPoetCodeBlock {
        val idViewBase = prop.idViewBasePropId?.let(context::prop)
        val manyToManyBase = prop.manyToManyBasePropId?.let(context::prop)
        return when {
            idViewBase != null && prop.list -> {
                val targetType = context.type(requireNotNull(idViewBase.targetTypeId))
                val targetId = targetType.propsById.getValue(requireNotNull(idViewBase.targetIdPropId))
                draftCode {
                    text("__isLoaded(")
                    type(KOTLIN_DRAFT_PROP_ID_TYPE)
                    text(".byIndex(")
                    name(idViewBase.slotName)
                    text(")) && ")
                    name(idViewBase.name)
                    text(".all { (it as ")
                    type(IMMUTABLE_SPI_TYPE)
                    text(").__isLoaded(")
                    type(KOTLIN_DRAFT_PROP_ID_TYPE)
                    text(".byIndex(")
                    type(context.producerType(targetType.typeId))
                    text(".")
                    name(targetId.slotName)
                    text(")) }")
                }
            }
            idViewBase != null -> {
                val targetType = context.type(requireNotNull(idViewBase.targetTypeId))
                val targetId = targetType.propsById.getValue(requireNotNull(idViewBase.targetIdPropId))
                draftCode {
                    text("__isLoaded(")
                    type(KOTLIN_DRAFT_PROP_ID_TYPE)
                    text(".byIndex(")
                    name(idViewBase.slotName)
                    text(")) && (")
                    name(idViewBase.name)
                    text(" as ")
                    type(IMMUTABLE_SPI_TYPE.withDraftRootNullability(idViewBase.nullable))
                    text(")")
                    text(if (idViewBase.nullable) "?." else ".")
                    text("__isLoaded(")
                    type(KOTLIN_DRAFT_PROP_ID_TYPE)
                    text(".byIndex(")
                    type(context.producerType(targetType.typeId))
                    text(".")
                    name(targetId.slotName)
                    text(")) ?: true")
                }
            }
            manyToManyBase != null -> draftCode {
                text("__isLoaded(")
                type(KOTLIN_DRAFT_PROP_ID_TYPE)
                text(".byIndex(")
                name(manyToManyBase.slotName)
                text(")) && ")
                name(manyToManyBase.name)
                text(".all { (it as ")
                type(IMMUTABLE_SPI_TYPE)
                text(").__isLoaded(")
                type(context.implementorType)
                text(".")
                name(requireNotNull(prop.kotlinDeeperPropIdName))
                text(") }")
            }
            prop.languageFormula -> formulaLoadedExpression(prop)
            prop.loadedStateFieldName != null -> draftCode { name(prop.loadedStateFieldName) }
            prop.valueFieldName != null -> draftCode {
                name(prop.valueFieldName)
                text(" !== null")
            }
            else -> draftCode { text("true") }
        }
    }

    private fun formulaLoadedExpression(prop: JimmerImmutableDraftPropPlan): LsiPoetCodeBlock {
        if (prop.formulaDependencyPaths.isEmpty()) {
            return draftCode { text("true") }
        }
        return draftCode {
            prop.formulaDependencyPaths.forEachIndexed { index, path ->
                if (index != 0) {
                    text(" && ")
                }
                if (path.size == 1) {
                    val (_, dependency) = context.globalProp(path.single())
                    text("__isLoaded(")
                    type(KOTLIN_DRAFT_PROP_ID_TYPE)
                    text(".byIndex(")
                    name(dependency.slotName)
                    text("))")
                } else {
                    type(IMMUTABLE_OBJECTS_TYPE)
                    text(".isLoadedChain(this")
                    path.forEach { dependencyId ->
                        text(", ")
                        type(KOTLIN_DRAFT_PROP_ID_TYPE)
                        text(".byIndex(")
                        add(context.ownerSlotReference(dependencyId))
                        text(")")
                    }
                    text(")")
                }
            }
        }
    }

    private fun implIsVisible(argument: DraftPropertyArgument): LsiPoetFunction {
        return LsiPoetFunction(
            name = "__isVisible",
            modifiers = setOf(LsiPoetModifier.OVERRIDE),
            parameters = listOf(LsiPoetParameter("prop", argument.type)),
            returnType = KOTLIN_DRAFT_BOOLEAN_TYPE,
            body = draftCode {
                statement { text("val __visibility = this.$VISIBILITY_FIELD ?: return true") }
                text("return ")
                addPropertyWhen(
                    argument = argument,
                    expression = { prop ->
                        draftCode {
                            text("__visibility.visible(")
                            name(prop.slotName)
                            text(")")
                        }
                    },
                    idNameFallback = draftCode { text("__isVisible(prop.asName())") },
                    elseExpression = draftCode { text("true") },
                    blankLineAfterElse = false,
                )
            },
        )
    }

    private fun hashCodeFunction(shallow: Boolean): LsiPoetFunction {
        return LsiPoetFunction(
            name = if (shallow) "__shallowHashCode" else "hashCode",
            modifiers = if (shallow) emptySet() else setOf(LsiPoetModifier.OVERRIDE),
            returnType = KOTLIN_DRAFT_INT_TYPE,
            body = draftCode {
                statement { text("var hash = $VISIBILITY_FIELD?.hashCode() ?: 0") }
                context.propsInDeclarationOrder.forEach { prop ->
                    val valueField = prop.valueFieldName ?: return@forEach
                    beginControlFlow {
                        text("if (")
                        add(loadedStateExpression(prop))
                        text(")")
                    }
                    statement {
                        text("hash = 31 * hash + ")
                        when {
                            shallow && prop.immutableReference -> {
                                type(SYSTEM_TYPE)
                                text(".identityHashCode(")
                                name(valueField)
                                text(")")
                            }
                            prop.nullable -> {
                                text("(")
                                name(valueField)
                                text("?.hashCode() ?: 0)")
                            }
                            else -> {
                                name(valueField)
                                text(".hashCode()")
                            }
                        }
                    }
                    if (!shallow && prop.propId == type.idPropId) {
                        returnValue { text("hash") }
                    }
                    endControlFlow()
                }
                returnValue { text("hash") }
            },
        )
    }

    private fun equalsFunction(shallow: Boolean): LsiPoetFunction {
        return LsiPoetFunction(
            name = if (shallow) "__shallowEquals" else "equals",
            modifiers = if (shallow) emptySet() else setOf(LsiPoetModifier.OVERRIDE),
            parameters = listOf(
                LsiPoetParameter("other", KOTLIN_DRAFT_ANY_TYPE.withDraftRootNullability(true))
            ),
            returnType = KOTLIN_DRAFT_BOOLEAN_TYPE,
            body = draftCode {
                statement {
                    text("val __other = other as? ")
                    type(context.implementorType)
                }
                beginControlFlow { text("if (__other === null)") }
                returnValue { text("false") }
                endControlFlow()
                context.propsInDeclarationOrder.forEach { prop ->
                    beginControlFlow {
                        text("if (__isVisible(")
                        type(KOTLIN_DRAFT_PROP_ID_TYPE)
                        text(".byIndex(")
                        name(prop.slotName)
                        text(")) != __other.__isVisible(")
                        type(KOTLIN_DRAFT_PROP_ID_TYPE)
                        text(".byIndex(")
                        name(prop.slotName)
                        text(")))")
                    }
                    returnValue { text("false") }
                    endControlFlow()
                    val valueField = prop.valueFieldName ?: return@forEach
                    val loadedName = "__${prop.codegenName}Loaded"
                    text("val ")
                    name(loadedName)
                    text(" = \n")
                    statement {
                        text("    this.")
                        add(loadedStateExpression(prop))
                    }
                    beginControlFlow {
                        text("if (")
                        name(loadedName)
                        text(" != (__other.__isLoaded(")
                        type(KOTLIN_DRAFT_PROP_ID_TYPE)
                        text(".byIndex(")
                        name(prop.slotName)
                        text("))))")
                    }
                    returnValue { text("false") }
                    endControlFlow()
                    if (!shallow && prop.propId == type.idPropId) {
                        beginControlFlow {
                            text("if (")
                            name(loadedName)
                            text(")")
                        }
                        returnValue {
                            text("this.")
                            name(valueField)
                            text(" == __other.")
                            name(prop.name)
                        }
                        endControlFlow()
                    } else {
                        beginControlFlow {
                            text("if (")
                            name(loadedName)
                            text(" && this.")
                            name(valueField)
                            text(if (shallow && prop.immutableReference) " !== " else " != ")
                            text("__other.")
                            name(prop.name)
                            text(")")
                        }
                        returnValue { text("false") }
                        endControlFlow()
                    }
                }
                returnValue { text("true") }
            },
        )
    }

    private fun toStringFunction(): LsiPoetFunction {
        return LsiPoetFunction(
            name = "toString",
            modifiers = setOf(LsiPoetModifier.OVERRIDE),
            returnType = KOTLIN_DRAFT_STRING_TYPE,
            body = draftCode {
                returnValue {
                    type(IMMUTABLE_OBJECTS_TYPE)
                    text(".toString(this)")
                }
            },
        )
    }

    private fun draftFields(): List<LsiPoetProperty> {
        return listOf(
            LsiPoetProperty(
                name = DRAFT_CONTEXT_FIELD,
                type = DRAFT_CONTEXT_TYPE.withDraftRootNullability(true),
                mutable = false,
                modifiers = setOf(LsiPoetModifier.PRIVATE),
                initializer = draftCode { text("ctx") },
            ),
            LsiPoetProperty(
                name = DRAFT_BASE_FIELD,
                type = context.implType.withDraftRootNullability(true),
                mutable = false,
                modifiers = setOf(LsiPoetModifier.PRIVATE),
                initializer = draftCode {
                    text("base as ")
                    type(context.implType.withDraftRootNullability(true))
                },
            ),
            LsiPoetProperty(
                name = DRAFT_MODIFIED_FIELD,
                type = context.implType.withDraftRootNullability(true),
                mutable = true,
                modifiers = setOf(LsiPoetModifier.PRIVATE),
                initializer = draftCode {
                    text("if (base === null) ")
                    type(context.implType)
                    text("() else null")
                },
            ),
            LsiPoetProperty(
                name = DRAFT_RESOLVING_FIELD,
                type = KOTLIN_DRAFT_BOOLEAN_TYPE,
                mutable = true,
                modifiers = setOf(LsiPoetModifier.PRIVATE),
                initializer = draftCode { text("false") },
            ),
            LsiPoetProperty(
                name = DRAFT_RESOLVED_FIELD,
                type = context.modelType.withDraftRootNullability(true),
                mutable = true,
                modifiers = setOf(LsiPoetModifier.PRIVATE),
                initializer = draftCode { text("null") },
            ),
        )
    }

    private fun delegateBooleanFunction(
        functionName: String,
        argument: DraftPropertyArgument,
    ): LsiPoetFunction {
        return LsiPoetFunction(
            name = functionName,
            modifiers = setOf(LsiPoetModifier.OVERRIDE),
            parameters = listOf(LsiPoetParameter("prop", argument.type)),
            returnType = KOTLIN_DRAFT_BOOLEAN_TYPE,
            body = draftCode {
                returnValue {
                    add(unmodifiedExpression)
                    text(".")
                    name(functionName)
                    text("(prop)")
                }
            },
        )
    }

    private fun draftHashCodeFunction(): LsiPoetFunction {
        return LsiPoetFunction(
            name = "hashCode",
            modifiers = setOf(LsiPoetModifier.OVERRIDE),
            returnType = KOTLIN_DRAFT_INT_TYPE,
            body = draftCode {
                returnValue {
                    add(unmodifiedExpression)
                    text(".hashCode()")
                }
            },
        )
    }

    private fun draftSpiHashCodeFunction(): LsiPoetFunction {
        return LsiPoetFunction(
            name = "__hashCode",
            modifiers = setOf(LsiPoetModifier.OVERRIDE),
            parameters = listOf(LsiPoetParameter("shallow", KOTLIN_DRAFT_BOOLEAN_TYPE)),
            returnType = KOTLIN_DRAFT_INT_TYPE,
            body = draftCode {
                returnValue {
                    add(unmodifiedExpression)
                    text(".__hashCode(shallow)")
                }
            },
        )
    }

    private fun draftEqualsFunction(): LsiPoetFunction {
        return LsiPoetFunction(
            name = "equals",
            modifiers = setOf(LsiPoetModifier.OVERRIDE),
            parameters = listOf(
                LsiPoetParameter("other", KOTLIN_DRAFT_ANY_TYPE.withDraftRootNullability(true))
            ),
            returnType = KOTLIN_DRAFT_BOOLEAN_TYPE,
            body = draftCode {
                returnValue {
                    add(unmodifiedExpression)
                    text(".equals(other)")
                }
            },
        )
    }

    private fun draftSpiEqualsFunction(): LsiPoetFunction {
        return LsiPoetFunction(
            name = "__equals",
            modifiers = setOf(LsiPoetModifier.OVERRIDE),
            parameters = listOf(
                LsiPoetParameter("other", KOTLIN_DRAFT_ANY_TYPE.withDraftRootNullability(true)),
                LsiPoetParameter("shallow", KOTLIN_DRAFT_BOOLEAN_TYPE),
            ),
            returnType = KOTLIN_DRAFT_BOOLEAN_TYPE,
            body = draftCode {
                returnValue {
                    add(unmodifiedExpression)
                    text(".__equals(other, shallow)")
                }
            },
        )
    }

    private fun draftImplProperty(prop: JimmerImmutableDraftPropPlan): LsiPoetProperty {
        val mutable = prop.writable || prop.isDiscriminator
        return LsiPoetProperty(
            name = prop.name,
            nameStyle = LsiPoetNameStyle.KOTLIN_ESCAPED,
            type = context.propType(prop),
            mutable = mutable,
            modifiers = setOf(LsiPoetModifier.OVERRIDE),
            getter = LsiPoetAccessor(body = draftGetter(prop)),
            setter = if (mutable) {
                LsiPoetAccessor(
                    setterParameterName = prop.name,
                    setterParameterNameStyle = LsiPoetNameStyle.KOTLIN_ESCAPED,
                    body = draftSetter(prop),
                )
            } else {
                null
            },
        )
    }

    private fun draftGetter(prop: JimmerImmutableDraftPropPlan): LsiPoetCodeBlock {
        val idViewBase = prop.idViewBasePropId?.let(context::prop)
        return draftCode {
            when {
                idViewBase != null && prop.list -> {
                    returnValue {
                        type(MUTABLE_ID_VIEW_LIST_TYPE)
                        text("(")
                        type(context.producerType(requireNotNull(idViewBase.targetTypeId)))
                        text(".type, ")
                        name(idViewBase.name)
                        text(")")
                    }
                }
                prop.list -> {
                    returnValue {
                        text("__ctx().toDraftList(")
                        add(unmodifiedExpression)
                        text(".")
                        name(prop.name)
                        text(", ")
                        type(context.propElementType(prop).toKotlinDraftListClassTokenType())
                        text("::class.java, ${prop.immutableReference})")
                    }
                }
                prop.immutableReference -> {
                    returnValue {
                        text("__ctx().toDraftObject(")
                        add(unmodifiedExpression)
                        text(".")
                        name(prop.name)
                        text(")")
                    }
                }
                else -> {
                    returnValue {
                        add(unmodifiedExpression)
                        text(".")
                        name(prop.name)
                    }
                }
            }
        }
    }

    private fun draftSetter(prop: JimmerImmutableDraftPropPlan): LsiPoetCodeBlock {
        val idViewBase = prop.idViewBasePropId?.let(context::prop)
        return draftCode {
            addFrozenCheck()
            if (idViewBase != null) {
                when {
                    idViewBase.list -> statement {
                        name(idViewBase.name)
                        text(" = ")
                        name(prop.name)
                        text(".map { ")
                        topLevelMember(MAKE_ID_ONLY_PACKAGE, MAKE_ID_ONLY_NAME, extension = false)
                        text("(it) }")
                    }
                    idViewBase.nullable -> statement {
                        name(idViewBase.name)
                        text(" = ")
                        name(prop.name)
                        text("?.let { ")
                        topLevelMember(MAKE_ID_ONLY_PACKAGE, MAKE_ID_ONLY_NAME, extension = false)
                        text("(it) }")
                    }
                    else -> statement {
                        name(idViewBase.name)
                        text(" = ")
                        topLevelMember(MAKE_ID_ONLY_PACKAGE, MAKE_ID_ONLY_NAME, extension = false)
                        text("(")
                        name(prop.name)
                        text(")")
                    }
                }
            } else {
                add(ImmutableDraftKotlinValidationPoet.validationCode(type, prop, prop.name))
                statement {
                    text("val __tmpModified = ")
                    add(modifiedExpression)
                }
                val valueField = requireNotNull(prop.valueFieldName)
                statement {
                    text("__tmpModified.")
                    name(valueField)
                    text(" = ")
                    if (prop.list) {
                        type(NON_SHARED_LIST_TYPE)
                        text(".of(__tmpModified.")
                        name(valueField)
                        text(", ")
                        name(prop.name)
                        text(")")
                    } else {
                        name(prop.name)
                    }
                }
                prop.loadedStateFieldName?.let { fieldName ->
                    statement {
                        text("__tmpModified.")
                        name(fieldName)
                        text(" = true")
                    }
                }
            }
        }
    }

    private fun draftAutoCreateFunction(prop: JimmerImmutableDraftPropPlan): LsiPoetFunction? {
        if (!prop.autoCreateSupported || prop.manyToManyBasePropId != null || prop.languageFormula) {
            return null
        }
        return LsiPoetFunction(
            name = prop.name,
            nameStyle = LsiPoetNameStyle.KOTLIN_ESCAPED,
            modifiers = setOf(LsiPoetModifier.OVERRIDE),
            returnType = context.propDraftType(prop, nullable = false),
            body = draftCode {
                beginControlFlow {
                    text("if (!__isLoaded(")
                    type(KOTLIN_DRAFT_PROP_ID_TYPE)
                    text(".byIndex(")
                    name(prop.slotName)
                    text("))")
                    if (prop.nullable) {
                        text(" || ")
                        name(prop.name)
                        text(" === null")
                    }
                    text(")")
                }
                statement {
                    name(prop.name)
                    text(" = ")
                    if (prop.list) {
                        text("mutableListOf()")
                    } else {
                        type(context.producerType(requireNotNull(prop.targetTypeId)))
                        text(".produce()")
                    }
                }
                endControlFlow()
                returnValue {
                    name(prop.name)
                    text(" as ")
                    type(context.propDraftType(prop, nullable = false))
                }
            },
        )
    }

    private fun draftReferenceFunction(prop: JimmerImmutableDraftPropPlan): LsiPoetFunction? {
        if (!prop.referenceMutationSupported || prop.list || prop.languageFormula) {
            return null
        }
        return LsiPoetFunction(
            name = prop.name,
            nameStyle = LsiPoetNameStyle.KOTLIN_ESCAPED,
            modifiers = setOf(LsiPoetModifier.OVERRIDE),
            parameters = listOf(
                LsiPoetParameter(
                    name = "block",
                    type = draftReceiverFunctionType(context.propDraftType(prop, nullable = false)),
                )
            ),
            body = draftCode {
                statement {
                    name(prop.name)
                    text("().apply(block)")
                }
            },
        )
    }

    private fun unloadFunction(argument: DraftPropertyArgument): LsiPoetFunction {
        return LsiPoetFunction(
            name = "__unload",
            modifiers = setOf(LsiPoetModifier.OVERRIDE),
            parameters = listOf(LsiPoetParameter("prop", argument.type)),
            body = draftCode {
                addFrozenCheck()
                addPropertyWhen(
                    argument = argument,
                    expression = ::unloadExpression,
                    idNameFallback = draftCode { text("__unload(prop.asName())") },
                )
            },
        )
    }

    private fun unloadExpression(prop: JimmerImmutableDraftPropPlan): LsiPoetCodeBlock {
        val basePropId = prop.idViewBasePropId ?: prop.manyToManyBasePropId
        return when {
            basePropId != null -> {
                val base = context.prop(basePropId)
                draftCode {
                    text("__unload(")
                    type(KOTLIN_DRAFT_PROP_ID_TYPE)
                    text(".byIndex(")
                    name(base.slotName)
                    text("))")
                }
            }
            prop.languageFormula -> draftCode { text("Unit") }
            prop.loadedStateFieldName != null -> draftCode {
                indent {
                    text(" {\n")
                    indent {
                        statement {
                            add(modifiedExpression)
                            text("\n.")
                            name(requireNotNull(prop.valueFieldName))
                            text(" = ")
                            add(primitiveDefault(prop))
                        }
                        statement {
                            add(modifiedExpression)
                            text("\n.")
                            name(prop.loadedStateFieldName)
                            text(" = false")
                        }
                    }
                    text("}")
                }
            }
            prop.valueFieldName != null -> draftCode {
                indent {
                    indent {
                        indent {
                            add(modifiedExpression)
                            text("\n.")
                            name(prop.valueFieldName)
                            text(" = null")
                        }
                    }
                }
            }
            else -> draftCode { text("Unit") }
        }
    }

    private fun setFunction(argument: DraftPropertyArgument): LsiPoetFunction {
        return LsiPoetFunction(
            name = "__set",
            modifiers = setOf(LsiPoetModifier.OVERRIDE),
            parameters = listOf(
                LsiPoetParameter("prop", argument.type),
                LsiPoetParameter("value", KOTLIN_DRAFT_ANY_TYPE.withDraftRootNullability(true)),
            ),
            body = draftCode {
                addPropertyWhen(
                    argument = argument,
                    expression = ::setExpression,
                    idNameFallback = draftCode { text("__set(prop.asName(), value)") },
                )
            },
        )
    }

    private fun setExpression(prop: JimmerImmutableDraftPropPlan): LsiPoetCodeBlock {
        if (!prop.writable && !prop.isDiscriminator) {
            return draftCode { text("Unit") }
        }
        return draftCode {
            text("this.")
            name(prop.name)
            text(" = value as ")
            type(context.propType(prop).withDraftRootNullability(true))
            if (!prop.nullable) {
                text("\n\t?: throw IllegalArgumentException(")
                string("'${prop.name} cannot be null")
                text(")")
            }
        }
    }

    private fun showFunction(argument: DraftPropertyArgument): LsiPoetFunction {
        return LsiPoetFunction(
            name = "__show",
            modifiers = setOf(LsiPoetModifier.OVERRIDE),
            parameters = listOf(
                LsiPoetParameter("prop", argument.type),
                LsiPoetParameter("visible", KOTLIN_DRAFT_BOOLEAN_TYPE),
            ),
            body = draftCode {
                addFrozenCheck()
                text("val __visibility = ")
                add(unmodifiedExpression)
                text(".$VISIBILITY_FIELD\n")
                indent {
                    text("?: if (visible) {\n")
                    indent { text("null\n") }
                    text("} else {\n")
                    indent {
                        type(VISIBILITY_TYPE)
                        text(".of(${type.propsBySlot.size}).also{\n")
                        indent {
                            add(modifiedExpression)
                            text(".$VISIBILITY_FIELD = it")
                        }
                        text("}\n")
                    }
                    text("}\n")
                    statement { text("?: return") }
                }
                addPropertyWhen(
                    argument = argument,
                    expression = { prop ->
                        draftCode {
                            text("__visibility.show(")
                            name(prop.slotName)
                            text(", visible)")
                        }
                    },
                    idNameFallback = draftCode { text("__show(prop.asName(), visible)") },
                    elseExpression = showIllegalPropertyCode(argument),
                    blankLineAfterElse = false,
                )
            },
        )
    }

    private fun draftContextFunction(): LsiPoetFunction {
        return LsiPoetFunction(
            name = "__draftContext",
            modifiers = setOf(LsiPoetModifier.OVERRIDE),
            returnType = DRAFT_CONTEXT_TYPE,
            body = draftCode { returnValue { text("__ctx()") } },
        )
    }

    private fun resolveFunction(): LsiPoetFunction {
        return LsiPoetFunction(
            name = "__resolve",
            modifiers = setOf(LsiPoetModifier.OVERRIDE),
            returnType = KOTLIN_DRAFT_ANY_TYPE,
            body = draftCode {
                statement { text("val __resolved = this.$DRAFT_RESOLVED_FIELD") }
                beginControlFlow { text("if (__resolved != null)") }
                returnValue { text("__resolved") }
                endControlFlow()
                beginControlFlow { text("if ($DRAFT_RESOLVING_FIELD)") }
                statement {
                    text("throw ")
                    type(CIRCULAR_REFERENCE_EXCEPTION_TYPE)
                    text("()")
                }
                endControlFlow()
                statement { text("$DRAFT_RESOLVING_FIELD = true") }
                statement { text("val __ctx = __ctx()") }
                beginControlFlow { text("try") }
                statement { text("val base = $DRAFT_BASE_FIELD") }
                statement { text("var __tmpModified = $DRAFT_MODIFIED_FIELD") }
                addAssociationResolution()
                beginControlFlow { text("if (base !== null && __tmpModified === null)") }
                statement { text("this.$DRAFT_RESOLVED_FIELD = base") }
                returnValue { text("base") }
                endControlFlow()
                add(ImmutableDraftKotlinValidationPoet.typeValidationCode(type, "__tmpModified"))
                statement { text("this.$DRAFT_RESOLVED_FIELD = __tmpModified") }
                returnValue { text("__tmpModified!!") }
                nextControlFlow { text("finally") }
                statement { text("$DRAFT_RESOLVING_FIELD = false") }
                endControlFlow()
            },
        )
    }

    private fun LsiPoetCodeBuilder.addAssociationResolution() {
        val resolvable = context.propsInDeclarationOrder.filter { prop ->
            prop.valueFieldName != null && (prop.immutableReference || prop.list)
        }
        if (resolvable.isEmpty()) {
            return
        }
        beginControlFlow { text("if (__tmpModified === null)") }
        resolvable.forEach { prop ->
            beginControlFlow {
                text("if (__isLoaded(")
                type(KOTLIN_DRAFT_PROP_ID_TYPE)
                text(".byIndex(")
                name(prop.slotName)
                text(")))")
            }
            statement {
                text("val oldValue = base!!.")
                name(prop.name)
            }
            statement {
                text("val newValue = __ctx.")
                text(if (prop.list) "resolveList" else "resolveObject")
                text("(oldValue)")
            }
            beginControlFlow { text("if (oldValue !== newValue)") }
            statement {
                text("this@")
                name(KOTLIN_DRAFT_DRAFT_IMPL)
                text(".")
                name(prop.name)
                text(" = newValue")
            }
            endControlFlow()
            endControlFlow()
        }
        statement { text("__tmpModified = $DRAFT_MODIFIED_FIELD") }
        nextControlFlow { text("else") }
        resolvable.forEach { prop ->
            val valueField = requireNotNull(prop.valueFieldName)
            statement {
                text("__tmpModified.")
                name(valueField)
                text(" = ")
                if (prop.list) {
                    type(NON_SHARED_LIST_TYPE)
                    text(".of(__tmpModified.")
                    name(valueField)
                    text(", __ctx.resolveList(__tmpModified.")
                    name(valueField)
                    text("))")
                } else if (prop.immutableReference) {
                    text("__ctx.resolveObject(__tmpModified.")
                    name(valueField)
                    text(")")
                }
            }
        }
        endControlFlow()
    }

    private fun isResolvedFunction(): LsiPoetFunction {
        return LsiPoetFunction(
            name = "__isResolved",
            modifiers = setOf(LsiPoetModifier.OVERRIDE),
            returnType = KOTLIN_DRAFT_BOOLEAN_TYPE,
            body = draftCode { returnValue { text("$DRAFT_RESOLVED_FIELD != null") } },
        )
    }

    private fun contextFunction(): LsiPoetFunction {
        return LsiPoetFunction(
            name = "__ctx",
            modifiers = setOf(LsiPoetModifier.PRIVATE),
            returnType = DRAFT_CONTEXT_TYPE,
            body = draftCode {
                returnValue {
                    text("$DRAFT_CONTEXT_FIELD ?: error(")
                    string(
                        "The current draft object is simple draft which does not support " +
                            "converting nested object to nested draft"
                    )
                    text(")")
                }
            },
        )
    }

    private fun unwrapFunction(): LsiPoetFunction {
        return LsiPoetFunction(
            name = "__unwrap",
            modifiers = setOf(LsiPoetModifier.INTERNAL),
            returnType = KOTLIN_DRAFT_ANY_TYPE,
            body = draftCode {
                returnValue {
                    text("$DRAFT_MODIFIED_FIELD ?: error(")
                    string("Internal bug, draft for builder must have `__modified`")
                    text(")")
                }
            },
        )
    }

    private fun LsiPoetCodeBuilder.addPropertyWhen(
        argument: DraftPropertyArgument,
        expression: (JimmerImmutableDraftPropPlan) -> LsiPoetCodeBlock,
        idNameFallback: LsiPoetCodeBlock = draftCode { text("__get(prop.asName())") },
        elseExpression: LsiPoetCodeBlock = context.illegalPropertyCode("prop"),
        blankLineAfterElse: Boolean = true,
    ) {
        beginControlFlow {
            text(if (argument == DraftPropertyArgument.ID) "when (prop.asIndex())" else "when (prop)")
        }
        if (argument == DraftPropertyArgument.ID) {
            text("-1 ->\n\t")
            add(idNameFallback)
            line()
        }
        type.propsBySlot.forEach { prop ->
            if (argument == DraftPropertyArgument.ID) {
                name(prop.slotName)
            } else {
                string(prop.name)
            }
            text(" ->\n\t")
            add(expression(prop))
            line()
        }
        text("else -> ")
        add(elseExpression)
        line()
        if (blankLineAfterElse) {
            line()
        }
        endControlFlow()
    }

    private fun showIllegalPropertyCode(argument: DraftPropertyArgument): LsiPoetCodeBlock {
        return draftCode {
            text("throw IllegalArgumentException(\n")
            indent {
                string(
                    if (argument == DraftPropertyArgument.ID) {
                        "Illegal property id: \""
                    } else {
                        "Illegal property name: \""
                    }
                )
                text(" + ")
                line()
                text("prop + ")
                line()
                string("\",it does not exists")
                line()
            }
            text(")")
        }
    }

    private fun loadedStateExpression(prop: JimmerImmutableDraftPropPlan): LsiPoetCodeBlock {
        return prop.loadedStateFieldName?.let { fieldName -> draftCode { name(fieldName) } }
            ?: draftCode {
                name(requireNotNull(prop.valueFieldName))
                text(" !== null")
            }
    }

    private fun primitiveDefault(prop: JimmerImmutableDraftPropPlan): LsiPoetCodeBlock {
        if (!prop.primitive) {
            return draftCode { text("null") }
        }
        val primitive = prop.type as LsiPrimitiveType
        return draftCode {
            when (primitive.kind) {
                LsiPrimitiveKind.BOOLEAN -> text("false")
                LsiPrimitiveKind.CHAR -> {
                    type(LsiPrimitiveType(LsiPrimitiveKind.CHAR))
                    text(".MIN_VALUE")
                }
                LsiPrimitiveKind.FLOAT -> text("0F")
                LsiPrimitiveKind.DOUBLE -> text("0.0")
                LsiPrimitiveKind.BYTE,
                LsiPrimitiveKind.SHORT,
                LsiPrimitiveKind.INT,
                LsiPrimitiveKind.LONG,
                -> text("0")
                LsiPrimitiveKind.UNIT,
                LsiPrimitiveKind.VOID,
                -> text("Unit")
            }
        }
    }

    private fun LsiPoetCodeBuilder.addFrozenCheck() {
        beginControlFlow { text("if ($DRAFT_RESOLVED_FIELD != null)") }
        statement {
            text("throw ")
            type(JAVA_ILLEGAL_STATE_EXCEPTION_TYPE)
            text("(")
            string(KOTLIN_DRAFT_FROZEN_EXCEPTION_MESSAGE)
            text(")")
        }
        endControlFlow()
    }

    private val unmodifiedExpression: LsiPoetCodeBlock
        get() = draftCode { text("($DRAFT_MODIFIED_FIELD ?: $DRAFT_BASE_FIELD!!)") }

    private val modifiedExpression: LsiPoetCodeBlock
        get() = draftCode {
            text("($DRAFT_MODIFIED_FIELD ?: $DRAFT_BASE_FIELD!!.clone())\n")
            text(".also { $DRAFT_MODIFIED_FIELD = it }")
        }
}

/**
 * Draft 列表的类型令牌必须与 Kotlin 属性元素类型一致，不能保留 JVM 装箱类型。
 */
private fun LsiTypeRef.toKotlinDraftListClassTokenType(): LsiTypeRef {
    val type = withDraftRootNullability(nullable = false)
    return if (type is LsiPrimitiveType) {
        type.copy(boxed = false)
    } else {
        type
    }
}

private enum class DraftPropertyArgument(
    val type: LsiTypeRef,
) {
    ID(KOTLIN_DRAFT_PROP_ID_TYPE),
    NAME(KOTLIN_DRAFT_STRING_TYPE),
}

private const val VISIBILITY_FIELD = "__visibility"
private const val DRAFT_CONTEXT_FIELD = "__ctx"
private const val DRAFT_BASE_FIELD = "__base"
private const val DRAFT_MODIFIED_FIELD = "__modified"
private const val DRAFT_RESOLVING_FIELD = "__resolving"
private const val DRAFT_RESOLVED_FIELD = "__resolved"
private const val MAKE_ID_ONLY_PACKAGE = "org.babyfish.jimmer.kt"
private const val MAKE_ID_ONLY_NAME = "makeIdOnly"

private val IMMUTABLE_SPI_TYPE = draftDeclaredType("org.babyfish.jimmer.runtime.ImmutableSpi")
private val IMMUTABLE_OBJECTS_TYPE = draftDeclaredType("org.babyfish.jimmer.ImmutableObjects")
private val UNLOADED_EXCEPTION_TYPE = draftDeclaredType("org.babyfish.jimmer.UnloadedException")
private val DRAFT_SPI_TYPE = draftDeclaredType("org.babyfish.jimmer.runtime.DraftSpi")
private val DRAFT_CONTEXT_TYPE = draftDeclaredType("org.babyfish.jimmer.runtime.DraftContext")
private val NON_SHARED_LIST_TYPE_ID = LsiSymbolId.type("org.babyfish.jimmer.runtime.NonSharedList")
private val NON_SHARED_LIST_TYPE = LsiDeclaredType(NON_SHARED_LIST_TYPE_ID)
private val VISIBILITY_TYPE = draftDeclaredType("org.babyfish.jimmer.runtime.Visibility")
private val CIRCULAR_REFERENCE_EXCEPTION_TYPE = draftDeclaredType("org.babyfish.jimmer.CircularReferenceException")
private val IMMUTABLE_MODULE_REQUIRED_EXCEPTION_TYPE = draftDeclaredType(
    "org.babyfish.jimmer.jackson.ImmutableModuleRequiredException"
)
private val ID_VIEW_LIST_TYPE = draftDeclaredType("org.babyfish.jimmer.sql.collection.IdViewList")
private val MUTABLE_ID_VIEW_LIST_TYPE = draftDeclaredType("org.babyfish.jimmer.sql.collection.MutableIdViewList")
private val MANY_TO_MANY_VIEW_LIST_TYPE = draftDeclaredType(
    "org.babyfish.jimmer.sql.collection.ManyToManyViewList"
)
private val SYSTEM_TYPE = draftDeclaredType("java.lang.System")
private val JAVA_ILLEGAL_STATE_EXCEPTION_TYPE = draftDeclaredType("java.lang.IllegalStateException")
private val JSON_PROPERTY_ORDER_TYPE_ID = LsiSymbolId.type("com.fasterxml.jackson.annotation.JsonPropertyOrder")

private fun draftDeclaredType(qualifiedName: String): LsiDeclaredType {
    return LsiDeclaredType(LsiSymbolId.type(qualifiedName))
}
