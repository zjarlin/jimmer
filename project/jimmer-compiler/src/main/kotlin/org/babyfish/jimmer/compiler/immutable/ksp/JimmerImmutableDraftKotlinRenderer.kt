package org.babyfish.jimmer.compiler.immutable.ksp

import com.squareup.kotlinpoet.ANY
import com.squareup.kotlinpoet.BOOLEAN
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.INT
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.LambdaTypeName
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ParameterizedTypeName
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.UNIT
import org.babyfish.jimmer.compiler.immutable.JimmerImmutableAssociatedIdContract
import org.babyfish.jimmer.compiler.immutable.JimmerImmutableDraftArtifactMetadata
import org.babyfish.jimmer.compiler.immutable.JimmerImmutableDraftCodegenSchema
import org.babyfish.jimmer.compiler.immutable.JimmerImmutableDraftPropPlan
import org.babyfish.jimmer.compiler.immutable.JimmerImmutableDraftRuntimePropKind
import org.babyfish.jimmer.compiler.immutable.JimmerImmutableDraftRuntimeValueCategory
import org.babyfish.jimmer.compiler.immutable.JimmerImmutableDraftTypePlan
import site.addzero.lsi.jimmer.ImmutableTypeKind
import org.babyfish.jimmer.compiler.render.ksp.toLegacyKotlinAnnotationSpecWithDefaults
import org.babyfish.jimmer.compiler.render.ksp.toKotlinTypeName
import org.babyfish.jimmer.compiler.render.ksp.toKotlinTypeVariableName
import org.babyfish.jimmer.currentVersion
import site.addzero.lsi.codegen.ArtifactKind
import site.addzero.lsi.codegen.GeneratedArtifact
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.model.LsiDeclaredType

internal class JimmerImmutableDraftKotlinRenderer {

    fun render(
        schema: JimmerImmutableDraftCodegenSchema,
        type: JimmerImmutableDraftTypePlan,
    ): GeneratedArtifact {
        require(schema.typesById[type.typeId] == type) {
            "Kotlin immutable draft renderer requires a type from its schema: ${type.typeId.value}"
        }
        return JimmerImmutableDraftKotlinRenderContext(schema, type).render()
    }
}

internal class JimmerImmutableDraftKotlinRenderContext(
    val schema: JimmerImmutableDraftCodegenSchema,
    val type: JimmerImmutableDraftTypePlan,
) {

    private val artifactMetadata = JimmerImmutableDraftArtifactMetadata(schema)

    val packageName: String = type.qualifiedName.substringBeforeLast('.', missingDelimiterValue = "")

    val simpleName: String = type.qualifiedName.substringAfterLast('.')

    val modelClass: ClassName = ClassName.bestGuess(type.qualifiedName)

    val modelType: TypeName = type.selfType.toKotlinTypeName()

    val draftClass: ClassName = ClassName(packageName, "$simpleName$DRAFT")

    val producerClass: ClassName = draftClass.nestedClass(PRODUCER)

    val implementorClass: ClassName = producerClass.nestedClass(IMPLEMENTOR)

    val implClass: ClassName = producerClass.nestedClass(IMPL)

    val draftImplClass: ClassName = producerClass.nestedClass(DRAFT_IMPL)

    val builderClass: ClassName = draftClass.nestedClass(BUILDER)

    val mappedSuperclass: Boolean = type.kind == ImmutableTypeKind.MAPPED_SUPERCLASS

    val propsInDeclarationOrder: List<JimmerImmutableDraftPropPlan> = buildList {
        val added = hashSetOf<LsiSymbolId>()
        type.runtimeDeclaredPropIds.forEach { propId ->
            add(prop(propId))
            added += propId
        }
        type.propsBySlot.forEach { prop ->
            if (added.add(prop.propId)) {
                add(prop)
            }
        }
    }

    fun render(): GeneratedArtifact {
        val qualifiedFileName = artifactMetadata.kotlinQualifiedFileName(type)
        val fileName = qualifiedFileName.substringAfterLast('.')
        val fileSpec = FileSpec.builder(packageName, fileName)
            .indent("    ")
            .addAnnotation(suppressWarningsAnnotation())
            .addType(draftTypeSpec())
            .apply {
                if (!mappedSuperclass) {
                    addCreatorFunctions()
                }
            }
            .build()
        return GeneratedArtifact.source(
            kind = ArtifactKind.KOTLIN_SOURCE,
            qualifiedName = qualifiedFileName,
            content = fileSpec.toString(),
            aggregationMode = artifactMetadata.aggregationMode(type),
            originatingSymbols = type.artifactOriginatingSymbols,
            originatingSources = type.artifactOriginatingSources.toSet(),
            dependencySymbols = type.dependencySymbols,
            dependencySources = artifactMetadata.dependencySources(type),
        )
    }

    fun prop(propId: LsiSymbolId): JimmerImmutableDraftPropPlan =
        requireNotNull(type.propsById[propId]) {
            "Immutable draft property does not belong to ${type.typeId.value}: ${propId.value}"
        }

    fun globalProp(propId: LsiSymbolId): Pair<JimmerImmutableDraftTypePlan, JimmerImmutableDraftPropPlan> {
        schema.types.forEach { candidate ->
            candidate.propsById[propId]?.let { prop -> return candidate to prop }
        }
        error("Immutable draft dependency property is missing: ${propId.value}")
    }

    fun type(typeId: LsiSymbolId): JimmerImmutableDraftTypePlan =
        requireNotNull(schema.typesById[typeId]) {
            "Immutable draft target type is not present in schema: ${typeId.value}"
        }

    fun modelClass(typeId: LsiSymbolId): ClassName = ClassName.bestGuess(type(typeId).qualifiedName)

    fun draftClass(typeId: LsiSymbolId): ClassName {
        val target = type(typeId)
        val targetPackage = target.qualifiedName.substringBeforeLast('.', missingDelimiterValue = "")
        return ClassName(targetPackage, "${target.qualifiedName.substringAfterLast('.')}Draft")
    }

    fun producerClass(typeId: LsiSymbolId): ClassName = draftClass(typeId).nestedClass(PRODUCER)

    fun propType(prop: JimmerImmutableDraftPropPlan): TypeName = prop.type.toKotlinTypeName()

    fun propElementType(prop: JimmerImmutableDraftPropPlan): TypeName = prop.elementType.toKotlinTypeName()

    fun propDraftType(
        prop: JimmerImmutableDraftPropPlan,
        nullable: Boolean = prop.nullable,
    ): TypeName {
        val typeName = when {
            prop.list -> MUTABLE_LIST.parameterizedBy(
                if (prop.immutableReference && prop.targetTypeId != null) {
                    replaceRawType(prop.elementType.toKotlinTypeName(), draftClass(prop.targetTypeId))
                } else {
                    prop.elementType.toKotlinTypeName()
                }
            )
            prop.immutableReference && prop.targetTypeId != null ->
                replaceRawType(prop.type.toKotlinTypeName(), draftClass(prop.targetTypeId))
            else -> prop.type.toKotlinTypeName()
        }
        return typeName.copy(nullable = nullable)
    }

    fun associatedIdProp(prop: JimmerImmutableDraftPropPlan): JimmerImmutableDraftPropPlan {
        val contract = requireNotNull(prop.associatedId)
        val target = requireNotNull(prop.targetTypeId)
        return requireNotNull(type(target).propsById[contract.targetIdPropId]) {
            "Immutable associated id property is missing: ${contract.targetIdPropId.value}"
        }
    }

    fun associatedIdType(prop: JimmerImmutableDraftPropPlan): TypeName =
        associatedIdProp(prop).type.toKotlinTypeName().copy(nullable = prop.nullable)

    fun slotReference(prop: JimmerImmutableDraftPropPlan): CodeBlock {
        val owner = type(prop.runtimeOwnerTypeId)
        return if (owner.typeId == type.typeId || owner.kind == ImmutableTypeKind.MAPPED_SUPERCLASS) {
            CodeBlock.of("%L", prop.slotIndex)
        } else {
            CodeBlock.of("%T.%L", producerClass(owner.typeId), prop.slotName)
        }
    }

    fun ownerSlotReference(propId: LsiSymbolId): CodeBlock {
        val (owner, ownerProp) = globalProp(propId)
        return CodeBlock.of("%T.%L", producerClass(owner.typeId), ownerProp.slotName)
    }

    fun generatedByAnnotation() = com.squareup.kotlinpoet.AnnotationSpec.builder(DRAFT_GENERATED_BY)
        .addMember("type = %T::class", modelClass)
        .build()

    fun jsonIgnoreAnnotation() = com.squareup.kotlinpoet.AnnotationSpec.builder(JSON_IGNORE)
        .useSiteTarget(com.squareup.kotlinpoet.AnnotationSpec.UseSiteTarget.GET)
        .build()

    fun descriptionAnnotation(text: String) = com.squareup.kotlinpoet.AnnotationSpec.builder(DESCRIPTION)
        .addMember("value = %S", text)
        .build()

    fun illegalPropertyCode(argumentName: String): CodeBlock = CodeBlock.builder()
        .add("throw IllegalArgumentException(\n")
        .indent()
        .add("%S + \n", "Illegal property name")
        .add("%S + \n", " for \"${type.qualifiedName}\": ")
        .add("%N\n", argumentName)
        .unindent()
        .add(")")
        .build()

    private fun draftTypeSpec(): TypeSpec {
        return TypeSpec.interfaceBuilder(draftClass)
            .addTypeVariables(type.typeParameters.map { parameter -> parameter.toKotlinTypeVariableName() })
            .addAnnotation(DRAFT_DSL_SCOPE)
            .addAnnotation(generatedByAnnotation())
            .addSuperinterface(modelType)
            .apply {
                if (type.directSuperTypes.isEmpty()) {
                    addSuperinterface(DRAFT_CLASS)
                } else {
                    type.directSuperTypes.forEach { superType -> addSuperinterface(draftSuperType(superType)) }
                }
                type.kotlinDraftPropIds.map(::prop).forEach { prop ->
                    if (prop.manyToManyBasePropId == null) {
                        addDraftProperty(prop)
                        addDraftAutoCreateFunction(prop)
                        addDraftReferenceFunction(prop)
                        addAssociatedIdProperty(prop, withImplementation = false)
                    }
                }
                addType(producerTypeSpec())
                if (!mappedSuperclass) {
                    addType(builderTypeSpec())
                }
            }
            .build()
    }

    private fun draftSuperType(superType: LsiDeclaredType): TypeName {
        return replaceRawType(superType.toKotlinTypeName(), draftClass(superType.declarationId))
    }

    private fun TypeSpec.Builder.addDraftProperty(prop: JimmerImmutableDraftPropPlan) {
        addProperty(
            PropertySpec.builder(prop.name, propType(prop))
                .addModifiers(KModifier.OVERRIDE)
                .mutable(prop.writable)
                .build()
        )
    }

    private fun TypeSpec.Builder.addDraftAutoCreateFunction(prop: JimmerImmutableDraftPropPlan) {
        if (!prop.autoCreateSupported) {
            return
        }
        addFunction(
            FunSpec.builder(prop.name)
                .addModifiers(KModifier.ABSTRACT)
                .returns(propDraftType(prop, nullable = false))
                .build()
        )
    }

    private fun TypeSpec.Builder.addDraftReferenceFunction(prop: JimmerImmutableDraftPropPlan) {
        if (!prop.referenceMutationSupported || prop.list) {
            return
        }
        addFunction(
            FunSpec.builder(prop.name)
                .addModifiers(KModifier.ABSTRACT)
                .addParameter(
                    "block",
                    LambdaTypeName.get(
                        receiver = propDraftType(prop, nullable = false),
                        parameters = emptyList(),
                        returnType = UNIT,
                    ),
                )
                .build()
        )
    }

    fun TypeSpec.Builder.addAssociatedIdProperty(
        prop: JimmerImmutableDraftPropPlan,
        withImplementation: Boolean,
    ) {
        val contract = prop.associatedId ?: return
        val associatedIdProp = associatedIdProp(prop)
        addProperty(
            PropertySpec.builder(contract.name, associatedIdType(prop))
                .addModifiers(KModifier.PUBLIC)
                .addAnnotation(jsonIgnoreAnnotation())
                .addModifiers(if (withImplementation) KModifier.OVERRIDE else KModifier.ABSTRACT)
                .mutable()
                .apply {
                    if (withImplementation) {
                        getter(
                            FunSpec.getterBuilder()
                                .addStatement(
                                    "return %N%L%N",
                                    prop.name,
                                    if (prop.nullable) "?." else ".",
                                    associatedIdProp.name,
                                )
                                .build()
                        )
                        setter(
                            FunSpec.setterBuilder()
                                .addParameter(contract.name, associatedIdType(prop))
                                .apply {
                                    if (prop.nullable) {
                                        beginControlFlow("if (%N === null)", contract.name)
                                        addStatement("this.%N = null", prop.name)
                                        addStatement("return")
                                        endControlFlow()
                                    }
                                    addStatement("%N().%N = %N", prop.name, associatedIdProp.name, contract.name)
                                }
                                .build()
                        )
                    }
                }
                .build()
        )
    }

    fun addAssociatedIdPropertyTo(
        builder: TypeSpec.Builder,
        prop: JimmerImmutableDraftPropPlan,
        withImplementation: Boolean,
    ) {
        with(builder) {
            addAssociatedIdProperty(prop, withImplementation)
        }
    }

    private fun producerTypeSpec(): TypeSpec {
        return TypeSpec.objectBuilder(PRODUCER)
            .addAnnotation(generatedByAnnotation())
            .apply {
                if (!mappedSuperclass) {
                    addSlots()
                }
                addProperty(
                    PropertySpec.builder("type", IMMUTABLE_TYPE)
                        .initializer(typeInitializer())
                        .build()
                )
                if (!mappedSuperclass) {
                    addFunction(produceFunction(withBlock = false))
                    addFunction(produceFunction(withBlock = true))
                    addType(JimmerImmutableDraftKotlinRuntimeRenderer(this@JimmerImmutableDraftKotlinRenderContext).implementor())
                    addType(JimmerImmutableDraftKotlinRuntimeRenderer(this@JimmerImmutableDraftKotlinRenderContext).impl())
                    addType(JimmerImmutableDraftKotlinRuntimeRenderer(this@JimmerImmutableDraftKotlinRenderContext).draftImpl())
                }
            }
            .build()
    }

    private fun TypeSpec.Builder.addSlots() {
        propsInDeclarationOrder.forEach { prop ->
            addProperty(
                PropertySpec.builder(prop.slotName, INT, KModifier.CONST)
                    .initializer(slotReference(prop))
                    .build()
            )
        }
    }

    private fun typeInitializer(): CodeBlock {
        return CodeBlock.builder()
            .add("%T\n", IMMUTABLE_TYPE)
            .indent()
            .add(".newBuilder(\n")
            .indent()
            .add("%S,\n", currentVersion())
            .add("%T::class,\n", modelClass)
            .add("listOf(\n")
            .indent()
            .apply {
                type.directSuperTypes.forEachIndexed { index, superType ->
                    if (index != 0) {
                        add(",\n")
                    }
                    add("%T.type", producerClass(superType.declarationId))
                }
            }
            .unindent()
            .add("\n),\n")
            .apply {
                if (mappedSuperclass) {
                    add("null\n")
                    unindent()
                    add(")\n")
                } else {
                    unindent()
                    add(") { ctx, base ->\n")
                    indent()
                    addStatement("%T(ctx, base as %T?)", draftImplClass, modelClass)
                    unindent()
                    add("}\n")
                }
                if (!mappedSuperclass) {
                    type.runtimeRedefinedPropIds.map(::prop).forEach { prop ->
                        add(".redefine(%S, %L)\n", prop.name, prop.slotName)
                    }
                }
                type.runtimeDeclaredPropIds.map(::prop).forEach { prop -> addRuntimeProp(prop) }
            }
            .add(".build()")
            .unindent()
            .build()
    }

    private fun CodeBlock.Builder.addRuntimeProp(prop: JimmerImmutableDraftPropPlan) {
        val propId = if (mappedSuperclass) CodeBlock.of("-1") else CodeBlock.of("%L", prop.slotName)
        val metadataType = prop.runtimeProp.metadataElementType.toKotlinTypeName().copy(nullable = false)
        when (prop.runtimeProp.kind) {
            JimmerImmutableDraftRuntimePropKind.ID -> add(
                ".id(%L, %S, %T::class.java)\n",
                propId,
                prop.name,
                metadataType,
            )
            JimmerImmutableDraftRuntimePropKind.VERSION -> add(
                ".version(%L, %S)\n",
                propId,
                prop.name,
            )
            JimmerImmutableDraftRuntimePropKind.LOGICAL_DELETED -> add(
                ".logicalDeleted(%L, %S, %T::class.java, %L)\n",
                propId,
                prop.name,
                metadataType,
                prop.nullable,
            )
            JimmerImmutableDraftRuntimePropKind.KEY_SCALAR -> add(
                ".key(%L, %S, %T::class.java, %L)\n",
                propId,
                prop.name,
                metadataType,
                prop.nullable,
            )
            JimmerImmutableDraftRuntimePropKind.KEY_REFERENCE -> add(
                ".keyReference(%L, %S, %T::class.java, %T::class.java, %L)\n",
                propId,
                prop.name,
                ClassName.bestGuess(requireNotNull(prop.runtimeProp.associationAnnotationTypeId).requireTypeQualifiedName()),
                metadataType,
                prop.nullable,
            )
            JimmerImmutableDraftRuntimePropKind.ASSOCIATION -> add(
                ".add(%L, %S, %T::class.java, %T::class.java, %L)\n",
                propId,
                prop.name,
                ClassName.bestGuess(requireNotNull(prop.runtimeProp.associationAnnotationTypeId).requireTypeQualifiedName()),
                metadataType,
                prop.nullable,
            )
            JimmerImmutableDraftRuntimePropKind.VALUE -> add(
                ".add(%L, %S, %T.%L, %T::class.java, %L)\n",
                propId,
                prop.name,
                IMMUTABLE_PROP_CATEGORY,
                prop.runtimeProp.valueCategory.runtimeName,
                metadataType,
                prop.nullable,
            )
        }
    }

    private fun produceFunction(withBlock: Boolean): FunSpec {
        return FunSpec.builder("produce")
            .addParameter(
                ParameterSpec.builder("base", modelClass.copy(nullable = true))
                    .defaultValue("null")
                    .build()
            )
            .addParameter(
                ParameterSpec.builder("resolveImmediately", BOOLEAN)
                    .defaultValue("false")
                    .build()
            )
            .apply {
                if (withBlock) {
                    addParameter(
                        "block",
                        LambdaTypeName.get(
                            receiver = draftClass,
                            parameters = emptyList(),
                            returnType = UNIT,
                        ),
                    )
                }
            }
            .returns(modelClass)
            .addStatement(
                "val consumer = %T %L",
                DRAFT_CONSUMER.parameterizedBy(draftClass),
                if (withBlock) "{ block(it) }" else "{}",
            )
            .addStatement(
                "return %T.produce(type, base, resolveImmediately, consumer) as %T",
                INTERNAL,
                modelClass,
            )
            .build()
    }

    private fun builderTypeSpec(): TypeSpec {
        return TypeSpec.classBuilder(BUILDER)
            .addAnnotation(generatedByAnnotation())
            .addProperty(
                PropertySpec.builder("__draft", draftImplClass)
                    .addModifiers(KModifier.PRIVATE)
                    .build()
            )
            .addFunction(
                FunSpec.constructorBuilder()
                    .addParameter("base", modelClass.copy(nullable = true))
                    .addStatement("__draft = %T(null, base)", draftImplClass)
                    .apply {
                        propsInDeclarationOrder.filter(JimmerImmutableDraftPropPlan::visibilityControllable).forEach { prop ->
                            addStatement(
                                "__draft.__show(%T.byIndex(%T.%L), false)",
                                PROP_ID,
                                producerClass,
                                prop.slotName,
                            )
                        }
                    }
                    .build()
            )
            .addFunction(
                FunSpec.constructorBuilder()
                    .callThisConstructor("null")
                    .build()
            )
            .apply {
                propsInDeclarationOrder.filter(JimmerImmutableDraftPropPlan::writable).forEach { prop ->
                    addFunction(builderSetter(prop))
                }
            }
            .addFunction(
                FunSpec.builder("build")
                    .returns(modelClass)
                    .addStatement("return __draft.__unwrap() as %T", modelClass)
                    .build()
            )
            .build()
    }

    private fun builderSetter(prop: JimmerImmutableDraftPropPlan): FunSpec {
        return FunSpec.builder(prop.name)
            .apply {
                prop.annotationPlan.builderMethodAnnotations.forEach { annotation ->
                    addAnnotation(annotation.toLegacyKotlinAnnotationSpecWithDefaults())
                }
            }
            .addParameter(prop.name, propType(prop).copy(nullable = true))
            .returns(builderClass)
            .apply {
                if (prop.nullable) {
                    addStatement("__draft.%N = %N", prop.name, prop.name)
                    addStatement(
                        "__draft.__show(%T.byIndex(%T.%L), true)",
                        PROP_ID,
                        producerClass,
                        prop.slotName,
                    )
                } else {
                    beginControlFlow("if (%N !== null)", prop.name)
                    addStatement("__draft.%N = %N", prop.name, prop.name)
                    addStatement(
                        "__draft.__show(%T.byIndex(%T.%L), true)",
                        PROP_ID,
                        producerClass,
                        prop.slotName,
                    )
                    endControlFlow()
                }
                addStatement("return this")
            }
            .build()
    }

    private fun FileSpec.Builder.addCreatorFunctions() {
        addFunction(newByFunction(withCreator = true, withBase = false, withBlock = true))
        addFunction(newByFunction(withCreator = true, withBase = true, withBlock = false))
        addFunction(newByFunction(withCreator = true, withBase = true, withBlock = true))
        addFunction(newByFunction(withCreator = false, withBase = false, withBlock = true))
        addFunction(newByFunction(withCreator = false, withBase = true, withBlock = true))
        addFunction(addByFunction(withBase = false, withBlock = true))
        addFunction(addByFunction(withBase = true, withBlock = false))
        addFunction(addByFunction(withBase = true, withBlock = true))
        addFunction(copyFunction())
    }

    private fun newByFunction(
        withCreator: Boolean,
        withBase: Boolean,
        withBlock: Boolean,
    ): FunSpec {
        return FunSpec.builder(if (withCreator) "by" else simpleName)
            .addAnnotation(generatedByAnnotation())
            .apply {
                if (withCreator) {
                    receiver(IMMUTABLE_CREATOR.parameterizedBy(modelClass))
                }
                if (withBase) {
                    addParameter("base", modelClass.copy(nullable = true))
                }
                addParameter(
                    ParameterSpec.builder("resolveImmediately", BOOLEAN)
                        .defaultValue("false")
                        .build()
                )
                if (withBlock) {
                    addParameter(
                        "block",
                        LambdaTypeName.get(
                            receiver = draftClass,
                            parameters = emptyList(),
                            returnType = UNIT,
                        ),
                    )
                }
            }
            .returns(modelClass)
            .addStatement(
                "return %T.produce(%L)",
                producerClass,
                produceArguments(withBase, withBlock),
            )
            .build()
    }

    private fun addByFunction(
        withBase: Boolean,
        withBlock: Boolean,
    ): FunSpec {
        val receiverType = MUTABLE_LIST.parameterizedBy(draftClass)
        return FunSpec.builder("addBy")
            .addAnnotation(generatedByAnnotation())
            .receiver(receiverType)
            .apply {
                if (withBase) {
                    addParameter("base", modelClass.copy(nullable = true))
                }
                addParameter(
                    ParameterSpec.builder("resolveImmediately", BOOLEAN)
                        .defaultValue("false")
                        .build()
                )
                if (withBlock) {
                    addParameter(
                        "block",
                        LambdaTypeName.get(
                            receiver = draftClass,
                            parameters = emptyList(),
                            returnType = UNIT,
                        ),
                    )
                }
            }
            .returns(receiverType)
            .addStatement(
                "add(%T.produce(%L) as %T)",
                producerClass,
                produceArguments(withBase, withBlock),
                draftClass,
            )
            .addStatement("return this")
            .build()
    }

    private fun copyFunction(): FunSpec {
        return FunSpec.builder("copy")
            .addAnnotation(generatedByAnnotation())
            .receiver(modelClass)
            .addParameter(
                ParameterSpec.builder("resolveImmediately", BOOLEAN)
                    .defaultValue("false")
                    .build()
            )
            .addParameter(
                "block",
                LambdaTypeName.get(
                    receiver = draftClass,
                    parameters = emptyList(),
                    returnType = UNIT,
                ),
            )
            .returns(modelClass)
            .addStatement("return %T.produce(this, resolveImmediately, block)", producerClass)
            .build()
    }

    private fun produceArguments(withBase: Boolean, withBlock: Boolean): CodeBlock {
        return CodeBlock.builder()
            .add(if (withBase) "base" else "null")
            .add(", resolveImmediately")
            .apply {
                if (withBlock) {
                    add(", block")
                }
            }
            .build()
    }
}

private fun replaceRawType(typeName: TypeName, rawType: ClassName): TypeName {
    val parameterized = typeName as? ParameterizedTypeName
    val replaced = if (parameterized == null) {
        rawType
    } else {
        rawType.parameterizedBy(parameterized.typeArguments)
    }
    return replaced.copy(nullable = typeName.isNullable, annotations = typeName.annotations)
}

private fun suppressWarningsAnnotation() = com.squareup.kotlinpoet.AnnotationSpec.builder(DRAFT_SUPPRESS)
    .addMember("%S", "warnings")
    .build()

private val JimmerImmutableDraftRuntimeValueCategory.runtimeName: String
    get() = when (this) {
        JimmerImmutableDraftRuntimeValueCategory.SCALAR -> "SCALAR"
        JimmerImmutableDraftRuntimeValueCategory.SCALAR_LIST -> "SCALAR_LIST"
        JimmerImmutableDraftRuntimeValueCategory.REFERENCE -> "REFERENCE"
        JimmerImmutableDraftRuntimeValueCategory.REFERENCE_LIST -> "REFERENCE_LIST"
    }

internal const val DRAFT = "Draft"
internal const val PRODUCER = "$"
internal const val IMPLEMENTOR = "Implementor"
internal const val IMPL = "Impl"
internal const val DRAFT_IMPL = "DraftImpl"
internal const val BUILDER = "Builder"
internal const val FROZEN_EXCEPTION_MESSAGE =
    "The current draft has been resolved so it cannot be modified"
internal const val EMAIL_PATTERN = "^[^@]+@[^@]+$"
internal const val EMAIL_PATTERN_FIELD = "__email_pattern"

internal val DRAFT_SUPPRESS = ClassName("kotlin", "Suppress")
internal val MUTABLE_LIST = ClassName("kotlin.collections", "MutableList")
internal val CLONEABLE = ClassName("kotlin", "Cloneable")
internal val SERIALIZABLE = ClassName("java.io", "Serializable")
internal val DRAFT_GENERATED_BY = ClassName("org.babyfish.jimmer.internal", "GeneratedBy")
internal val DESCRIPTION = ClassName("org.babyfish.jimmer.client", "Description")
internal val DRAFT_CLASS = ClassName("org.babyfish.jimmer", "Draft")
internal val DRAFT_CONSUMER = ClassName("org.babyfish.jimmer", "DraftConsumer")
internal val DRAFT_DSL_SCOPE = ClassName("org.babyfish.jimmer.kt", "DslScope")
internal val IMMUTABLE_CREATOR = ClassName("org.babyfish.jimmer.kt", "ImmutableCreator")
internal val IMMUTABLE_TYPE = ClassName("org.babyfish.jimmer.meta", "ImmutableType")
internal val IMMUTABLE_PROP_CATEGORY = ClassName("org.babyfish.jimmer.meta", "ImmutablePropCategory")
internal val PROP_ID = ClassName("org.babyfish.jimmer.meta", "PropId")
internal val INTERNAL = ClassName("org.babyfish.jimmer.runtime", "Internal")
internal val IMMUTABLE_SPI = ClassName("org.babyfish.jimmer.runtime", "ImmutableSpi")
internal val IMMUTABLE_OBJECTS = ClassName("org.babyfish.jimmer", "ImmutableObjects")
internal val UNLOADED_EXCEPTION = ClassName("org.babyfish.jimmer", "UnloadedException")
internal val DRAFT_SPI = ClassName("org.babyfish.jimmer.runtime", "DraftSpi")
internal val DRAFT_CONTEXT = ClassName("org.babyfish.jimmer.runtime", "DraftContext")
internal val NON_SHARED_LIST = ClassName("org.babyfish.jimmer.runtime", "NonSharedList")
internal val VISIBILITY = ClassName("org.babyfish.jimmer.runtime", "Visibility")
internal val CIRCULAR_REFERENCE_EXCEPTION = ClassName("org.babyfish.jimmer", "CircularReferenceException")
internal val JSON_IGNORE = ClassName("com.fasterxml.jackson.annotation", "JsonIgnore")
internal val JSON_PROPERTY_ORDER = ClassName("com.fasterxml.jackson.annotation", "JsonPropertyOrder")
internal val IMMUTABLE_MODULE_REQUIRED_EXCEPTION =
    ClassName("org.babyfish.jimmer.jackson", "ImmutableModuleRequiredException")
internal val ID_VIEW_LIST = ClassName("org.babyfish.jimmer.sql.collection", "IdViewList")
internal val MUTABLE_ID_VIEW_LIST = ClassName("org.babyfish.jimmer.sql.collection", "MutableIdViewList")
internal val MANY_TO_MANY_VIEW_LIST = ClassName("org.babyfish.jimmer.sql.collection", "ManyToManyViewList")
internal val VALIDATOR = ClassName("org.babyfish.jimmer.impl.validation", "Validator")
internal val PATTERN = ClassName("java.util.regex", "Pattern")
internal val BIG_INTEGER = ClassName("java.math", "BigInteger")
internal val BIG_DECIMAL = ClassName("java.math", "BigDecimal")
internal val LOCAL_DATE = ClassName("java.time", "LocalDate")
internal val LOCAL_DATE_TIME = ClassName("java.time", "LocalDateTime")
internal val LOCAL_TIME = ClassName("java.time", "LocalTime")
internal val INSTANT = ClassName("java.time", "Instant")
internal val SYSTEM = ClassName("java.lang", "System")
