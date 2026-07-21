package org.babyfish.jimmer.compiler.immutable.ksp

import com.squareup.kotlinpoet.ANY
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.BOOLEAN
import com.squareup.kotlinpoet.CHAR
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.DOUBLE
import com.squareup.kotlinpoet.FLOAT
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.INT
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.LambdaTypeName
import com.squareup.kotlinpoet.MemberName
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.STRING
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.UNIT
import org.babyfish.jimmer.compiler.immutable.JimmerImmutableDraftPropPlan
import org.babyfish.jimmer.compiler.immutable.JimmerImmutableDraftValueState
import org.babyfish.jimmer.compiler.render.ksp.toKotlinTypeName
import site.addzero.lsi.model.LsiPrimitiveKind
import site.addzero.lsi.model.LsiPrimitiveType

internal class JimmerImmutableDraftKotlinRuntimeRenderer(
    private val context: JimmerImmutableDraftKotlinRenderContext,
) {

    private val type = context.type

    fun implementor(): TypeSpec {
        return TypeSpec.interfaceBuilder(IMPLEMENTOR)
            .addAnnotation(context.generatedByAnnotation())
            .addModifiers(KModifier.PRIVATE, KModifier.ABSTRACT)
            .addSuperinterface(context.modelClass)
            .addSuperinterface(IMMUTABLE_SPI)
            .addAnnotation(propertyOrderAnnotation())
            .addFunction(implementorGet(DraftPropertyArgument.ID))
            .addFunction(implementorGet(DraftPropertyArgument.NAME))
            .addFunction(
                FunSpec.builder("__type")
                    .addModifiers(KModifier.OVERRIDE)
                    .returns(IMMUTABLE_TYPE)
                    .addStatement("return %T.type", context.producerClass)
                    .build()
            )
            .addProperty(
                PropertySpec.builder("dummyPropForJacksonError__", INT)
                    .getter(
                        FunSpec.getterBuilder()
                            .addStatement("throw %T()", IMMUTABLE_MODULE_REQUIRED_EXCEPTION)
                            .build()
                    )
                    .build()
            )
            .apply { addImplementorCompanion() }
            .build()
    }

    fun impl(): TypeSpec {
        return TypeSpec.classBuilder(IMPL)
            .addModifiers(KModifier.PRIVATE)
            .addAnnotation(context.generatedByAnnotation())
            .addSuperinterface(context.implementorClass)
            .addSuperinterface(CLONEABLE)
            .addSuperinterface(SERIALIZABLE)
            .addProperty(
                PropertySpec.builder("__visibility", VISIBILITY.copy(nullable = true))
                    .addModifiers(KModifier.INTERNAL)
                    .mutable()
                    .addAnnotation(context.jsonIgnoreAnnotation())
                    .initializer("null")
                    .build()
            )
            .apply {
                context.propsInDeclarationOrder.forEach { prop -> addStorageFields(prop) }
                addVisibilityConstructor()
                context.propsInDeclarationOrder.forEach { prop -> addImplProperty(prop) }
                addFunction(cloneFunction())
                addFunction(implIsLoaded(DraftPropertyArgument.ID))
                addFunction(implIsLoaded(DraftPropertyArgument.NAME))
                addFunction(implIsVisible(DraftPropertyArgument.ID))
                addFunction(implIsVisible(DraftPropertyArgument.NAME))
                addFunction(hashCodeFunction(shallow = true))
                addFunction(hashCodeFunction(shallow = false))
                addFunction(
                    FunSpec.builder("__hashCode")
                        .addParameter("shallow", BOOLEAN)
                        .returns(INT)
                        .addModifiers(KModifier.OVERRIDE)
                        .addStatement("return if (shallow) __shallowHashCode() else hashCode()")
                        .build()
                )
                addFunction(equalsFunction(shallow = true))
                addFunction(equalsFunction(shallow = false))
                addFunction(
                    FunSpec.builder("__equals")
                        .addParameter("obj", ANY.copy(nullable = true))
                        .addParameter("shallow", BOOLEAN)
                        .returns(BOOLEAN)
                        .addModifiers(KModifier.OVERRIDE)
                        .addStatement("return if (shallow) __shallowEquals(obj) else equals(obj)")
                        .build()
                )
                addFunction(toStringFunction())
            }
            .build()
    }

    fun draftImpl(): TypeSpec {
        return TypeSpec.classBuilder(DRAFT_IMPL)
            .addAnnotation(context.generatedByAnnotation())
            .addModifiers(KModifier.INTERNAL)
            .addSuperinterface(context.implementorClass)
            .addSuperinterface(context.draftClass)
            .addSuperinterface(DRAFT_SPI)
            .primaryConstructor(
                FunSpec.constructorBuilder()
                    .addParameter("ctx", DRAFT_CONTEXT.copy(nullable = true))
                    .addParameter("base", context.modelClass.copy(nullable = true))
                    .build()
            )
            .apply {
                addDraftFields()
                addFunction(delegateBooleanFunction("__isLoaded", DraftPropertyArgument.ID))
                addFunction(delegateBooleanFunction("__isLoaded", DraftPropertyArgument.NAME))
                addFunction(delegateBooleanFunction("__isVisible", DraftPropertyArgument.ID))
                addFunction(delegateBooleanFunction("__isVisible", DraftPropertyArgument.NAME))
                addFunction(
                    FunSpec.builder("hashCode")
                        .returns(INT)
                        .addModifiers(KModifier.OVERRIDE)
                        .addStatement("return %L.hashCode()", unmodified())
                        .build()
                )
                addFunction(
                    FunSpec.builder("__hashCode")
                        .addParameter("shallow", BOOLEAN)
                        .returns(INT)
                        .addModifiers(KModifier.OVERRIDE)
                        .addStatement("return %L.__hashCode(shallow)", unmodified())
                        .build()
                )
                addFunction(
                    FunSpec.builder("equals")
                        .addParameter("other", ANY.copy(nullable = true))
                        .returns(BOOLEAN)
                        .addModifiers(KModifier.OVERRIDE)
                        .addStatement("return %L.equals(other)", unmodified())
                        .build()
                )
                addFunction(
                    FunSpec.builder("__equals")
                        .addParameter("other", ANY.copy(nullable = true))
                        .addParameter("shallow", BOOLEAN)
                        .returns(BOOLEAN)
                        .addModifiers(KModifier.OVERRIDE)
                        .addStatement("return %L.__equals(other, shallow)", unmodified())
                        .build()
                )
                addFunction(toStringFunction())
                context.propsInDeclarationOrder.forEach { prop ->
                    addDraftImplProperty(prop)
                    addDraftAutoCreateFunction(prop)
                    addDraftReferenceFunction(prop)
                    context.addAssociatedIdPropertyTo(this, prop, withImplementation = true)
                }
                addFunction(unloadFunction(DraftPropertyArgument.ID))
                addFunction(unloadFunction(DraftPropertyArgument.NAME))
                addFunction(setFunction(DraftPropertyArgument.ID))
                addFunction(setFunction(DraftPropertyArgument.NAME))
                addFunction(showFunction(DraftPropertyArgument.ID))
                addFunction(showFunction(DraftPropertyArgument.NAME))
                addFunction(
                    FunSpec.builder("__draftContext")
                        .returns(DRAFT_CONTEXT)
                        .addModifiers(KModifier.OVERRIDE)
                        .addStatement("return __ctx()")
                        .build()
                )
                addFunction(resolveFunction())
                addFunction(
                    FunSpec.builder("__isResolved")
                        .addModifiers(KModifier.OVERRIDE)
                        .returns(BOOLEAN)
                        .addStatement("return __resolved != null")
                        .build()
                )
                addFunction(
                    FunSpec.builder("__ctx")
                        .addModifiers(KModifier.PRIVATE)
                        .returns(DRAFT_CONTEXT)
                        .addStatement(
                            "return __ctx ?: error(%S)",
                            "The current draft object is simple draft which does not support converting nested object to nested draft",
                        )
                        .build()
                )
                addFunction(
                    FunSpec.builder("__unwrap")
                        .addModifiers(KModifier.INTERNAL)
                        .returns(ANY)
                        .addStatement(
                            "return __modified ?: error(%S)",
                            "Internal bug, draft for builder must have `__modified`",
                        )
                        .build()
                )
                JimmerImmutableDraftKotlinValidationRenderer(context).addCompanion(this)
            }
            .build()
    }

    private fun propertyOrderAnnotation(): AnnotationSpec {
        val members = CodeBlock.builder().add("%S", "dummyPropForJacksonError__")
        type.propsBySlot.forEach { prop -> members.add(", %S", prop.name) }
        return AnnotationSpec.builder(JSON_PROPERTY_ORDER)
            .addMember(members.build())
            .build()
    }

    private fun implementorGet(argument: DraftPropertyArgument): FunSpec {
        return FunSpec.builder("__get")
            .addParameter("prop", argument.type)
            .addModifiers(KModifier.OVERRIDE)
            .returns(ANY.copy(nullable = true))
            .addCode(
                CodeBlock.builder()
                    .add("return ")
                    .addPropertyWhen(
                        argument,
                        expression = { prop -> CodeBlock.of("%N", prop.name) },
                    )
                    .build()
            )
            .build()
    }

    private fun TypeSpec.Builder.addImplementorCompanion() {
        val deeperProps = context.propsInDeclarationOrder.filter { prop -> prop.kotlinDeeperPropIdName != null }
        if (deeperProps.isEmpty()) {
            return
        }
        addType(
            TypeSpec.companionObjectBuilder()
                .apply {
                    deeperProps.forEach { prop ->
                        addProperty(
                            PropertySpec.builder(requireNotNull(prop.kotlinDeeperPropIdName), PROP_ID)
                                .initializer(
                                    "%T.type.getProp(%S).getManyToManyViewBaseDeeperProp().getId()",
                                    context.producerClass,
                                    prop.name,
                                )
                                .build()
                        )
                    }
                }
                .build()
        )
    }

    private fun TypeSpec.Builder.addStorageFields(prop: JimmerImmutableDraftPropPlan) {
        prop.valueFieldName?.let { fieldName ->
            val fieldType = if (prop.list) {
                NON_SHARED_LIST.parameterizedBy(context.propElementType(prop))
                    .copy(nullable = true)
            } else {
                context.propType(prop).copy(nullable = !prop.primitive)
            }
            addProperty(
                PropertySpec.builder(fieldName, fieldType)
                    .addModifiers(KModifier.INTERNAL)
                    .addAnnotation(context.jsonIgnoreAnnotation())
                    .initializer(primitiveDefault(prop))
                    .mutable()
                    .build()
            )
        }
        prop.loadedStateFieldName?.let { fieldName ->
            addProperty(
                PropertySpec.builder(fieldName, BOOLEAN)
                    .addModifiers(KModifier.INTERNAL)
                    .addAnnotation(context.jsonIgnoreAnnotation())
                    .initializer("false")
                    .mutable()
                    .build()
            )
        }
    }

    private fun TypeSpec.Builder.addVisibilityConstructor() {
        val hiddenProps = context.propsInDeclarationOrder.filter { prop -> !prop.valueState.hasValue }
        if (hiddenProps.isEmpty()) {
            return
        }
        addFunction(
            FunSpec.constructorBuilder()
                .addStatement("val __visibility = %T.of(%L)", VISIBILITY, type.propsBySlot.size)
                .apply {
                    hiddenProps.forEach { prop ->
                        addStatement("__visibility.show(%L, false)", prop.slotName)
                    }
                }
                .addStatement("this.__visibility = __visibility")
                .build()
        )
    }

    private fun TypeSpec.Builder.addImplProperty(prop: JimmerImmutableDraftPropPlan) {
        if (prop.languageFormula) {
            return
        }
        addProperty(
            PropertySpec.builder(prop.name, context.propType(prop))
                .addModifiers(KModifier.OVERRIDE)
                .apply {
                    (prop.sourceDocumentation ?: prop.documentation)?.let { documentation ->
                        addAnnotation(context.descriptionAnnotation(documentation))
                    }
                }
                .getter(
                    FunSpec.getterBuilder()
                        .addCode(implGetter(prop))
                        .build()
                )
                .build()
        )
    }

    private fun implGetter(prop: JimmerImmutableDraftPropPlan): CodeBlock {
        val idViewBase = prop.idViewBasePropId?.let(context::prop)
        val manyToManyBase = prop.manyToManyBasePropId?.let(context::prop)
        return CodeBlock.builder()
            .apply {
                when {
                    idViewBase != null && prop.list -> {
                        val targetTypeId = requireNotNull(idViewBase.targetTypeId)
                        addStatement(
                            "return %T(%T.type, %N)",
                            ID_VIEW_LIST,
                            context.producerClass(targetTypeId),
                            idViewBase.name,
                        )
                    }
                    idViewBase != null -> {
                        val targetId = context.type(requireNotNull(idViewBase.targetTypeId))
                            .propsById
                            .getValue(requireNotNull(idViewBase.targetIdPropId))
                        addStatement(
                            "return %N%L%N",
                            idViewBase.name,
                            if (prop.nullable) "?." else ".",
                            targetId.name,
                        )
                    }
                    manyToManyBase != null -> addStatement(
                        "return %T(%T.%L, %N)",
                        MANY_TO_MANY_VIEW_LIST,
                        context.implementorClass,
                        requireNotNull(prop.kotlinDeeperPropIdName),
                        manyToManyBase.name,
                    )
                    else -> {
                        val valueField = requireNotNull(prop.valueFieldName)
                        if (prop.loadedStateFieldName == null) {
                            addStatement("val %N = this.%N", valueField, valueField)
                        }
                        beginControlFlow(
                            if (prop.loadedStateFieldName != null) {
                                "if (!${prop.loadedStateFieldName})"
                            } else {
                                "if ($valueField === null)"
                            }
                        )
                        addStatement(
                            "throw %T(%T::class.java, %S)",
                            UNLOADED_EXCEPTION,
                            context.modelClass(prop.sourceDeclaringTypeId),
                            prop.name,
                        )
                        endControlFlow()
                        addStatement("return %N", valueField)
                    }
                }
            }
            .build()
    }

    private fun cloneFunction(): FunSpec {
        return FunSpec.builder("clone")
            .addModifiers(KModifier.PUBLIC, KModifier.OVERRIDE)
            .returns(context.implClass)
            .addStatement("val copy = super.clone() as %T", context.implClass)
            .addStatement("val originalVisibility = this.__visibility")
            .beginControlFlow("if (originalVisibility != null)")
            .addStatement("val newVisibility = %T.of(%L)", VISIBILITY, type.propsBySlot.size)
            .beginControlFlow("for (propId in 0 until %L)", type.propsBySlot.size)
            .addStatement("newVisibility.show(propId, originalVisibility.visible(propId))")
            .endControlFlow()
            .addStatement("copy.__visibility = newVisibility")
            .nextControlFlow("else")
            .addStatement("copy.__visibility = null")
            .endControlFlow()
            .addStatement("return copy")
            .build()
    }

    private fun implIsLoaded(argument: DraftPropertyArgument): FunSpec {
        return FunSpec.builder("__isLoaded")
            .addModifiers(KModifier.OVERRIDE)
            .addParameter("prop", argument.type)
            .returns(BOOLEAN)
            .addCode(
                CodeBlock.builder()
                    .add("return ")
                    .addPropertyWhen(
                        argument,
                        ::loadedExpression,
                        idNameFallback = CodeBlock.of("__isLoaded(prop.asName())"),
                    )
                    .build()
            )
            .build()
    }

    private fun loadedExpression(prop: JimmerImmutableDraftPropPlan): CodeBlock {
        val idViewBase = prop.idViewBasePropId?.let(context::prop)
        val manyToManyBase = prop.manyToManyBasePropId?.let(context::prop)
        return when {
            idViewBase != null && prop.list -> {
                val targetType = context.type(requireNotNull(idViewBase.targetTypeId))
                val targetId = targetType.propsById.getValue(requireNotNull(idViewBase.targetIdPropId))
                CodeBlock.builder()
                    .add(
                        "__isLoaded(%T.byIndex(%L)) && %N.all { ",
                        PROP_ID,
                        idViewBase.slotName,
                        idViewBase.name,
                    )
                    .add(
                        "(it as %T).__isLoaded(%T.byIndex(%T.%L))",
                        IMMUTABLE_SPI,
                        PROP_ID,
                        context.producerClass(targetType.typeId),
                        targetId.slotName,
                    )
                    .add(" }")
                    .build()
            }
            idViewBase != null -> {
                val targetType = context.type(requireNotNull(idViewBase.targetTypeId))
                val targetId = targetType.propsById.getValue(requireNotNull(idViewBase.targetIdPropId))
                CodeBlock.of(
                    "__isLoaded(%T.byIndex(%L)) && (%N as %T)%L__isLoaded(%T.byIndex(%T.%L)) ?: true",
                    PROP_ID,
                    idViewBase.slotName,
                    idViewBase.name,
                    IMMUTABLE_SPI.copy(nullable = idViewBase.nullable),
                    if (idViewBase.nullable) "?." else ".",
                    PROP_ID,
                    context.producerClass(targetType.typeId),
                    targetId.slotName,
                )
            }
            manyToManyBase != null -> CodeBlock.builder()
                .add(
                    "__isLoaded(%T.byIndex(%L)) && %N.all { ",
                    PROP_ID,
                    manyToManyBase.slotName,
                    manyToManyBase.name,
                )
                .add(
                    "(it as %T).__isLoaded(%T.%L)",
                    IMMUTABLE_SPI,
                    context.implementorClass,
                    requireNotNull(prop.kotlinDeeperPropIdName),
                )
                .add(" }")
                .build()
            prop.languageFormula -> formulaLoadedExpression(prop)
            prop.loadedStateFieldName != null -> CodeBlock.of("%N", prop.loadedStateFieldName)
            prop.valueFieldName != null -> CodeBlock.of("%N !== null", prop.valueFieldName)
            else -> CodeBlock.of("true")
        }
    }

    private fun formulaLoadedExpression(prop: JimmerImmutableDraftPropPlan): CodeBlock {
        if (prop.formulaDependencyPaths.isEmpty()) {
            return CodeBlock.of("true")
        }
        return CodeBlock.builder()
            .apply {
                prop.formulaDependencyPaths.forEachIndexed { index, path ->
                    if (index != 0) {
                        add(" && ")
                    }
                    if (path.size == 1) {
                        val (_, dependency) = context.globalProp(path.single())
                        add(
                            "__isLoaded(%T.byIndex(%L))",
                            PROP_ID,
                            dependency.slotName,
                        )
                    } else {
                        add("%T.isLoadedChain(this", IMMUTABLE_OBJECTS)
                        path.forEach { dependencyId ->
                            add(", %T.byIndex(%L)", PROP_ID, context.ownerSlotReference(dependencyId))
                        }
                        add(")")
                    }
                }
            }
            .build()
    }

    private fun implIsVisible(argument: DraftPropertyArgument): FunSpec {
        return FunSpec.builder("__isVisible")
            .addModifiers(KModifier.OVERRIDE)
            .addParameter("prop", argument.type)
            .returns(BOOLEAN)
            .addStatement("val __visibility = this.__visibility ?: return true")
            .addCode(
                CodeBlock.builder()
                    .add("return ")
                    .addPropertyWhen(
                        argument,
                        expression = { prop ->
                            CodeBlock.of("__visibility.visible(%L)", prop.slotName)
                        },
                        idNameFallback = CodeBlock.of("__isVisible(prop.asName())"),
                        elseExpression = CodeBlock.of("true"),
                        blankLineAfterElse = false,
                    )
                    .build()
            )
            .build()
    }

    private fun hashCodeFunction(shallow: Boolean): FunSpec {
        return FunSpec.builder(if (shallow) "__shallowHashCode" else "hashCode")
            .apply {
                if (!shallow) {
                    addModifiers(KModifier.OVERRIDE)
                }
            }
            .returns(INT)
            .addStatement("var hash = __visibility?.hashCode() ?: 0")
            .apply {
                context.propsInDeclarationOrder.forEach { prop ->
                    val valueField = prop.valueFieldName ?: return@forEach
                    beginControlFlow("if (%L)", loadedStateExpression(prop))
                    if (shallow && prop.immutableReference) {
                        addStatement("hash = 31 * hash + %T.identityHashCode(%N)", SYSTEM, valueField)
                    } else if (prop.nullable) {
                        addStatement("hash = 31 * hash + (%N?.hashCode() ?: 0)", valueField)
                    } else {
                        addStatement("hash = 31 * hash + %N.hashCode()", valueField)
                    }
                    if (!shallow && prop.propId == type.idPropId) {
                        addStatement("return hash")
                    }
                    endControlFlow()
                }
            }
            .addStatement("return hash")
            .build()
    }

    private fun equalsFunction(shallow: Boolean): FunSpec {
        return FunSpec.builder(if (shallow) "__shallowEquals" else "equals")
            .addParameter("other", ANY.copy(nullable = true))
            .apply {
                if (!shallow) {
                    addModifiers(KModifier.OVERRIDE)
                }
            }
            .returns(BOOLEAN)
            .addStatement("val __other = other as? %T", context.implementorClass)
            .beginControlFlow("if (__other === null)")
            .addStatement("return false")
            .endControlFlow()
            .apply {
                context.propsInDeclarationOrder.forEach { prop ->
                    beginControlFlow(
                        "if (__isVisible(%T.byIndex(%L)) != __other.__isVisible(%T.byIndex(%L)))",
                        PROP_ID,
                        prop.slotName,
                        PROP_ID,
                        prop.slotName,
                    )
                    addStatement("return false")
                    endControlFlow()
                    val valueField = prop.valueFieldName ?: return@forEach
                    val loadedName = "__${prop.name}Loaded"
                    addCode("val %N = \n", loadedName)
                    addStatement("    this.%L", loadedStateExpression(prop))
                    beginControlFlow(
                        "if (%N != (__other.__isLoaded(%T.byIndex(%L))))",
                        loadedName,
                        PROP_ID,
                        prop.slotName,
                    )
                    addStatement("return false")
                    endControlFlow()
                    if (!shallow && prop.propId == type.idPropId) {
                        beginControlFlow("if (%N)", loadedName)
                        addStatement("return this.%N == __other.%N", valueField, prop.name)
                        endControlFlow()
                    } else {
                        beginControlFlow(
                            "if (%N && this.%N %L __other.%N)",
                            loadedName,
                            valueField,
                            if (shallow && prop.immutableReference) "!==" else "!=",
                            prop.name,
                        )
                        addStatement("return false")
                        endControlFlow()
                    }
                }
            }
            .addStatement("return true")
            .build()
    }

    private fun toStringFunction(): FunSpec {
        return FunSpec.builder("toString")
            .addModifiers(KModifier.OVERRIDE)
            .returns(STRING)
            .addStatement("return %T.toString(this)", IMMUTABLE_OBJECTS)
            .build()
    }

    private fun TypeSpec.Builder.addDraftFields() {
        addProperty(
            PropertySpec.builder("__ctx", DRAFT_CONTEXT.copy(nullable = true))
                .addModifiers(KModifier.PRIVATE)
                .initializer("ctx")
                .build()
        )
        addProperty(
            PropertySpec.builder("__base", context.implClass.copy(nullable = true))
                .addModifiers(KModifier.PRIVATE)
                .initializer("base as %T?", context.implClass)
                .build()
        )
        addProperty(
            PropertySpec.builder("__modified", context.implClass.copy(nullable = true))
                .addModifiers(KModifier.PRIVATE)
                .mutable()
                .initializer("if (base === null) %T() else null", context.implClass)
                .build()
        )
        addProperty(
            PropertySpec.builder("__resolving", BOOLEAN)
                .addModifiers(KModifier.PRIVATE)
                .mutable()
                .initializer("false")
                .build()
        )
        addProperty(
            PropertySpec.builder("__resolved", context.modelClass.copy(nullable = true))
                .addModifiers(KModifier.PRIVATE)
                .mutable()
                .initializer("null")
                .build()
        )
    }

    private fun delegateBooleanFunction(
        name: String,
        argument: DraftPropertyArgument,
    ): FunSpec {
        return FunSpec.builder(name)
            .addParameter("prop", argument.type)
            .returns(BOOLEAN)
            .addModifiers(KModifier.OVERRIDE)
            .addStatement("return %L.%L(prop)", unmodified(), name)
            .build()
    }

    private fun TypeSpec.Builder.addDraftImplProperty(prop: JimmerImmutableDraftPropPlan) {
        addProperty(
            PropertySpec.builder(prop.name, context.propType(prop), KModifier.OVERRIDE)
                .mutable(prop.writable)
                .getter(
                    FunSpec.getterBuilder()
                        .addCode(draftGetter(prop))
                        .build()
                )
                .apply {
                    if (prop.writable) {
                        setter(
                            FunSpec.setterBuilder()
                                .addParameter(prop.name, context.propType(prop))
                                .addCode(draftSetter(prop))
                                .build()
                        )
                    }
                }
                .build()
        )
    }

    private fun draftGetter(prop: JimmerImmutableDraftPropPlan): CodeBlock {
        val idViewBase = prop.idViewBasePropId?.let(context::prop)
        return CodeBlock.builder()
            .apply {
                when {
                    idViewBase != null && prop.list -> addStatement(
                        "return %T(%T.type, %N)",
                        MUTABLE_ID_VIEW_LIST,
                        context.producerClass(requireNotNull(idViewBase.targetTypeId)),
                        idViewBase.name,
                    )
                    prop.list -> addStatement(
                        "return __ctx().toDraftList(%L.%N, %T::class.java, %L)",
                        unmodified(),
                        prop.name,
                        context.propElementType(prop).copy(nullable = false),
                        prop.immutableReference,
                    )
                    prop.immutableReference -> addStatement(
                        "return __ctx().toDraftObject(%L.%N)",
                        unmodified(),
                        prop.name,
                    )
                    else -> addStatement("return %L.%N", unmodified(), prop.name)
                }
            }
            .build()
    }

    private fun draftSetter(prop: JimmerImmutableDraftPropPlan): CodeBlock {
        val idViewBase = prop.idViewBasePropId?.let(context::prop)
        return CodeBlock.builder()
            .addFrozenCheck()
            .apply {
                if (idViewBase != null) {
                    when {
                        idViewBase.list -> addStatement(
                            "%N = %N.map { %M(it) }",
                            idViewBase.name,
                            prop.name,
                            MAKE_ID_ONLY,
                        )
                        idViewBase.nullable -> addStatement(
                            "%N = %N?.let { %M(it) }",
                            idViewBase.name,
                            prop.name,
                            MAKE_ID_ONLY,
                        )
                        else -> addStatement(
                            "%N = %M(%N)",
                            idViewBase.name,
                            MAKE_ID_ONLY,
                            prop.name,
                        )
                    }
                } else {
                    JimmerImmutableDraftKotlinValidationRenderer(context).addValidation(this, prop, prop.name)
                    addStatement("val __tmpModified = %L", modified())
                    val valueField = requireNotNull(prop.valueFieldName)
                    if (prop.list) {
                        addStatement(
                            "__tmpModified.%N = %T.of(__tmpModified.%N, %N)",
                            valueField,
                            NON_SHARED_LIST,
                            valueField,
                            prop.name,
                        )
                    } else {
                        addStatement("__tmpModified.%N = %N", valueField, prop.name)
                    }
                    prop.loadedStateFieldName?.let { fieldName ->
                        addStatement("__tmpModified.%N = true", fieldName)
                    }
                }
            }
            .build()
    }

    private fun TypeSpec.Builder.addDraftAutoCreateFunction(prop: JimmerImmutableDraftPropPlan) {
        if (!prop.autoCreateSupported || prop.manyToManyBasePropId != null || prop.languageFormula) {
            return
        }
        addFunction(
            FunSpec.builder(prop.name)
                .returns(context.propDraftType(prop, nullable = false))
                .addModifiers(KModifier.OVERRIDE)
                .apply {
                    if (prop.nullable) {
                        beginControlFlow(
                            "if (!__isLoaded(%T.byIndex(%L)) || %N === null)",
                            PROP_ID,
                            prop.slotName,
                            prop.name,
                        )
                    } else {
                        beginControlFlow(
                            "if (!__isLoaded(%T.byIndex(%L)))",
                            PROP_ID,
                            prop.slotName,
                        )
                    }
                    if (prop.list) {
                        addStatement("%N = mutableListOf()", prop.name)
                    } else {
                        addStatement(
                            "%N = %T.produce()",
                            prop.name,
                            context.producerClass(requireNotNull(prop.targetTypeId)),
                        )
                    }
                    endControlFlow()
                    addStatement("return %N as %T", prop.name, context.propDraftType(prop, nullable = false))
                }
                .build()
        )
    }

    private fun TypeSpec.Builder.addDraftReferenceFunction(prop: JimmerImmutableDraftPropPlan) {
        if (!prop.referenceMutationSupported || prop.list || prop.languageFormula) {
            return
        }
        addFunction(
            FunSpec.builder(prop.name)
                .addModifiers(KModifier.OVERRIDE)
                .addParameter(
                    ParameterSpec.builder(
                        "block",
                        LambdaTypeName.get(
                            receiver = context.propDraftType(prop, nullable = false),
                            parameters = emptyList(),
                            returnType = UNIT,
                        ),
                    ).build()
                )
                .addStatement("%N().apply(block)", prop.name)
                .build()
        )
    }

    private fun unloadFunction(argument: DraftPropertyArgument): FunSpec {
        return FunSpec.builder("__unload")
            .addParameter("prop", argument.type)
            .addModifiers(KModifier.OVERRIDE)
            .addCode(
                CodeBlock.builder()
                    .addFrozenCheck()
                    .addPropertyWhen(
                        argument,
                        ::unloadExpression,
                        idNameFallback = CodeBlock.of("__unload(prop.asName())"),
                    )
                    .build()
            )
            .build()
    }

    private fun unloadExpression(prop: JimmerImmutableDraftPropPlan): CodeBlock {
        val basePropId = prop.idViewBasePropId ?: prop.manyToManyBasePropId
        return when {
            basePropId != null -> {
                val base = context.prop(basePropId)
                CodeBlock.of(
                    "__unload(%T.byIndex(%L))",
                    PROP_ID,
                    base.slotName,
                )
            }
            prop.languageFormula -> CodeBlock.of("Unit")
            prop.loadedStateFieldName != null -> CodeBlock.builder()
                .indent()
                .add(" {\n")
                .indent()
                .addStatement(
                    "%L\n.%N = %L",
                    modified(),
                    requireNotNull(prop.valueFieldName),
                    primitiveDefault(prop),
                )
                .addStatement("%L\n.%N = false", modified(), prop.loadedStateFieldName)
                .unindent()
                .add("}")
                .unindent()
                .build()
            prop.valueFieldName != null -> CodeBlock.of(
                "(__modified ?: __base!!.clone())\n" +
                    "            .also { __modified = it }\n" +
                    "            .%N = null",
                prop.valueFieldName,
            )
            else -> CodeBlock.of("Unit")
        }
    }

    private fun setFunction(argument: DraftPropertyArgument): FunSpec {
        return FunSpec.builder("__set")
            .addParameter("prop", argument.type)
            .addParameter("value", ANY.copy(nullable = true))
            .addModifiers(KModifier.OVERRIDE)
            .addCode(
                CodeBlock.builder()
                    .addPropertyWhen(
                        argument,
                        expression = { prop -> setExpression(prop) },
                        idNameFallback = CodeBlock.of("__set(prop.asName(), value)"),
                    )
                    .build()
            )
            .build()
    }

    private fun setExpression(prop: JimmerImmutableDraftPropPlan): CodeBlock {
        if (!prop.writable) {
            return CodeBlock.of("Unit")
        }
        val castType = context.propType(prop).copy(nullable = true)
        return CodeBlock.builder()
            .add("this.%N = value as %T", prop.name, castType)
            .apply {
                if (!prop.nullable) {
                    add("\n\t?: throw IllegalArgumentException(%S)", "'${prop.name} cannot be null")
                }
            }
            .build()
    }

    private fun showFunction(argument: DraftPropertyArgument): FunSpec {
        return FunSpec.builder("__show")
            .addParameter("prop", argument.type)
            .addParameter("visible", BOOLEAN)
            .addModifiers(KModifier.OVERRIDE)
            .addCode(
                CodeBlock.builder()
                    .addFrozenCheck()
                    .add("val __visibility = %L.__visibility\n", unmodified())
                    .indent()
                    .add("?: if (visible) {\n")
                    .indent()
                    .add("null\n")
                    .unindent()
                    .add("} else {\n")
                    .indent()
                    .add("%T.of(%L).also{\n", VISIBILITY, type.propsBySlot.size)
                    .indent()
                    .add("%L.__visibility = it", modified())
                    .unindent()
                    .add("}\n")
                    .unindent()
                    .add("}\n")
                    .addStatement("?: return")
                    .unindent()
                    .addPropertyWhen(
                        argument,
                        expression = { prop ->
                            CodeBlock.of("__visibility.show(%L, visible)", prop.slotName)
                        },
                        idNameFallback = CodeBlock.of("__show(prop.asName(), visible)"),
                        elseExpression = showIllegalPropertyCode(argument),
                        blankLineAfterElse = false,
                    )
                    .build()
            )
            .build()
    }

    private fun resolveFunction(): FunSpec {
        return FunSpec.builder("__resolve")
            .returns(ANY)
            .addModifiers(KModifier.OVERRIDE)
            .addStatement("val __resolved = this.__resolved")
            .beginControlFlow("if (__resolved != null)")
            .addStatement("return __resolved")
            .endControlFlow()
            .beginControlFlow("if (__resolving)")
            .addStatement("throw %T()", CIRCULAR_REFERENCE_EXCEPTION)
            .endControlFlow()
            .addStatement("__resolving = true")
            .addStatement("val __ctx = __ctx()")
            .beginControlFlow("try")
            .addStatement("val base = __base")
            .addStatement("var __tmpModified = __modified")
            .apply { addAssociationResolution() }
            .beginControlFlow("if (base !== null && __tmpModified === null)")
            .addStatement("this.__resolved = base")
            .addStatement("return base")
            .endControlFlow()
            .apply {
                JimmerImmutableDraftKotlinValidationRenderer(context)
                    .addTypeValidationStatements(this, "__tmpModified")
            }
            .addStatement("this.__resolved = __tmpModified")
            .addStatement("return __tmpModified!!")
            .nextControlFlow("finally")
            .addStatement("__resolving = false")
            .endControlFlow()
            .build()
    }

    private fun FunSpec.Builder.addAssociationResolution() {
        val resolvable = context.propsInDeclarationOrder.filter { prop ->
            prop.valueFieldName != null && (prop.immutableReference || prop.list)
        }
        if (resolvable.isEmpty()) {
            return
        }
        beginControlFlow("if (__tmpModified === null)")
        resolvable.forEach { prop ->
            beginControlFlow(
                "if (__isLoaded(%T.byIndex(%L)))",
                PROP_ID,
                prop.slotName,
            )
            addStatement("val oldValue = base!!.%N", prop.name)
            addStatement(
                "val newValue = __ctx.%L(oldValue)",
                if (prop.list) "resolveList" else "resolveObject",
            )
            beginControlFlow("if (oldValue !== newValue)")
            addStatement("this@%L.%N = newValue", DRAFT_IMPL, prop.name)
            endControlFlow()
            endControlFlow()
        }
        addStatement("__tmpModified = __modified")
        nextControlFlow("else")
        resolvable.forEach { prop ->
            val valueField = requireNotNull(prop.valueFieldName)
            if (prop.list) {
                addStatement(
                    "__tmpModified.%N = %T.of(__tmpModified.%N, __ctx.resolveList(__tmpModified.%N))",
                    valueField,
                    NON_SHARED_LIST,
                    valueField,
                    valueField,
                )
            } else if (prop.immutableReference) {
                addStatement(
                    "__tmpModified.%N = __ctx.resolveObject(__tmpModified.%N)",
                    valueField,
                    valueField,
                )
            }
        }
        endControlFlow()
    }

    private fun CodeBlock.Builder.addPropertyWhen(
        argument: DraftPropertyArgument,
        expression: (JimmerImmutableDraftPropPlan) -> CodeBlock,
        idNameFallback: CodeBlock = CodeBlock.of("__get(prop.asName())"),
        elseExpression: CodeBlock = context.illegalPropertyCode("prop"),
        blankLineAfterElse: Boolean = true,
    ): CodeBlock.Builder {
        if (argument == DraftPropertyArgument.ID) {
            beginControlFlow("when (prop.asIndex())")
            add("-1 ->\n\t%L\n", idNameFallback)
        } else {
            beginControlFlow("when (prop)")
        }
        type.propsBySlot.forEach { prop ->
            if (argument == DraftPropertyArgument.ID) {
                add("%L ->\n\t", prop.slotName)
            } else {
                add("%S ->\n\t", prop.name)
            }
            add("%L\n", expression(prop))
        }
        add("else -> %L\n", elseExpression)
        if (blankLineAfterElse) {
            add("\n")
        }
        endControlFlow()
        return this
    }

    private fun showIllegalPropertyCode(argument: DraftPropertyArgument): CodeBlock {
        return CodeBlock.builder()
            .add("throw IllegalArgumentException(\n")
            .indent()
            .add(
                "%S + \n",
                if (argument == DraftPropertyArgument.ID) {
                    "Illegal property id: \""
                } else {
                    "Illegal property name: \""
                },
            )
            .add("prop + \n")
            .add("%S\n", "\",it does not exists")
            .unindent()
            .add(")")
            .build()
    }

    private fun loadedStateExpression(prop: JimmerImmutableDraftPropPlan): CodeBlock {
        return prop.loadedStateFieldName?.let { fieldName -> CodeBlock.of("%N", fieldName) }
            ?: CodeBlock.of("%N !== null", requireNotNull(prop.valueFieldName))
    }

    private fun primitiveDefault(prop: JimmerImmutableDraftPropPlan): CodeBlock {
        if (!prop.primitive) {
            return CodeBlock.of("null")
        }
        val primitive = prop.type as LsiPrimitiveType
        return when (primitive.kind) {
            LsiPrimitiveKind.BOOLEAN -> CodeBlock.of("false")
            LsiPrimitiveKind.CHAR -> CodeBlock.of("Char.MIN_VALUE")
            LsiPrimitiveKind.FLOAT -> CodeBlock.of("0F")
            LsiPrimitiveKind.DOUBLE -> CodeBlock.of("0.0")
            LsiPrimitiveKind.BYTE,
            LsiPrimitiveKind.SHORT,
            LsiPrimitiveKind.INT,
            LsiPrimitiveKind.LONG,
            -> CodeBlock.of("0")
            LsiPrimitiveKind.UNIT,
            LsiPrimitiveKind.VOID,
            -> CodeBlock.of("Unit")
        }
    }

    private fun unmodified(): CodeBlock = CodeBlock.of("(__modified ?: __base!!)")

    private fun modified(): CodeBlock = CodeBlock.of(
        "(__modified ?: __base!!.clone())\n.also { __modified = it }"
    )

    private fun CodeBlock.Builder.addFrozenCheck(): CodeBlock.Builder {
        beginControlFlow("if (__resolved != null)")
        addStatement("throw %T(%S)", IllegalStateException::class, FROZEN_EXCEPTION_MESSAGE)
        endControlFlow()
        return this
    }
}

private enum class DraftPropertyArgument(
    val type: TypeName,
) {
    ID(PROP_ID),
    NAME(STRING),
}

private val MAKE_ID_ONLY = MemberName("org.babyfish.jimmer.kt", "makeIdOnly")
