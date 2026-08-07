package org.babyfish.jimmer.compiler.immutable

import org.babyfish.jimmer.currentVersion
import site.addzero.lsi.core.LsiLanguage
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.jimmer.ImmutableDraftRuntimePropKind
import site.addzero.lsi.jimmer.ImmutablePropValueCategory
import site.addzero.lsi.jimmer.ImmutableTypeKind
import site.addzero.lsi.model.LsiAnnotation
import site.addzero.lsi.model.LsiAnnotationUseSiteTarget
import site.addzero.lsi.model.LsiAnnotationValue
import site.addzero.lsi.model.LsiArrayType
import site.addzero.lsi.model.LsiDeclaredType
import site.addzero.lsi.model.LsiFunctionType
import site.addzero.lsi.model.LsiNullability
import site.addzero.lsi.model.LsiPrimitiveKind
import site.addzero.lsi.model.LsiPrimitiveType
import site.addzero.lsi.model.LsiTypeParameterRef
import site.addzero.lsi.model.LsiTypeRef
import site.addzero.lsi.model.LsiUnresolvedType
import site.addzero.lsi.poet.LsiPoetAccessor
import site.addzero.lsi.poet.LsiPoetAnnotation
import site.addzero.lsi.poet.LsiPoetAnnotationArgument
import site.addzero.lsi.poet.LsiPoetAnnotationArrayStyle
import site.addzero.lsi.poet.LsiPoetAnnotationValue
import site.addzero.lsi.poet.LsiPoetCodeBlock
import site.addzero.lsi.poet.LsiPoetCodeBuilder
import site.addzero.lsi.poet.LsiPoetConstructor
import site.addzero.lsi.poet.LsiPoetDelegationCall
import site.addzero.lsi.poet.LsiPoetDelegationTarget
import site.addzero.lsi.poet.LsiPoetFile
import site.addzero.lsi.poet.LsiPoetFileNameStyle
import site.addzero.lsi.poet.LsiPoetFunction
import site.addzero.lsi.poet.LsiPoetModifier
import site.addzero.lsi.poet.LsiPoetNameStyle
import site.addzero.lsi.poet.LsiPoetParameter
import site.addzero.lsi.poet.LsiPoetProperty
import site.addzero.lsi.poet.LsiPoetType
import site.addzero.lsi.model.LsiTypeDeclarationKind

/**
 * 把一个不可变类型的 Draft 代码生成计划降低为 Kotlin LSI Poet 文件。
 */
internal fun JimmerImmutableDraftCodegenSchema.toKotlinDraftPoetFile(
    type: JimmerImmutableDraftTypePlan,
): LsiPoetFile {
    require(typesById[type.typeId] == type) {
        "Immutable draft type '${type.typeId.value}' does not belong to the supplied schema"
    }
    return ImmutableDraftKotlinPoetContext(this, type).file()
}

internal class ImmutableDraftKotlinPoetContext(
    internal val schema: JimmerImmutableDraftCodegenSchema,
    internal val type: JimmerImmutableDraftTypePlan,
) {

    internal val packageName: String = type.qualifiedName.substringBeforeLast('.', missingDelimiterValue = "")

    internal val simpleName: String = type.qualifiedName.substringAfterLast('.')

    internal val modelType: LsiDeclaredType = type.selfType

    private val modelRawType = LsiDeclaredType(type.typeId)

    private val draftTypeId = LsiSymbolId.type("${type.qualifiedName}$KOTLIN_DRAFT_SUFFIX")

    internal val draftType: LsiDeclaredType = LsiDeclaredType(draftTypeId)

    internal val producerType: LsiDeclaredType = generatedNestedType(KOTLIN_DRAFT_PRODUCER)

    internal val implementorType: LsiDeclaredType = generatedNestedType(
        "$KOTLIN_DRAFT_PRODUCER.$KOTLIN_DRAFT_IMPLEMENTOR"
    )

    internal val implType: LsiDeclaredType = generatedNestedType(
        "$KOTLIN_DRAFT_PRODUCER.$KOTLIN_DRAFT_IMPL"
    )

    internal val draftImplType: LsiDeclaredType = generatedNestedType(
        "$KOTLIN_DRAFT_PRODUCER.$KOTLIN_DRAFT_DRAFT_IMPL"
    )

    internal val builderType: LsiDeclaredType = generatedNestedType(KOTLIN_DRAFT_BUILDER)

    internal val mappedSuperclass: Boolean = type.kind == ImmutableTypeKind.MAPPED_SUPERCLASS

    internal val propsInDeclarationOrder: List<JimmerImmutableDraftPropPlan> = buildList {
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

    internal fun file(): LsiPoetFile {
        val qualifiedFileName = type.kotlinDraftQualifiedFileName()
        return LsiPoetFile(
            language = LsiLanguage.KOTLIN,
            packageName = packageName,
            fileName = qualifiedFileName.substringAfterLast('.'),
            fileNameStyle = LsiPoetFileNameStyle.KOTLIN_SOURCE_STEM,
            annotations = listOf(KOTLIN_DRAFT_WARNING_SUPPRESSION),
            members = buildList {
                add(draftDeclaration())
                if (!mappedSuperclass) {
                    addAll(creatorFunctions())
                }
            },
        )
    }

    internal fun prop(propId: LsiSymbolId): JimmerImmutableDraftPropPlan {
        return requireNotNull(type.propsById[propId]) {
            "Immutable draft property does not belong to ${type.typeId.value}: ${propId.value}"
        }
    }

    internal fun globalProp(
        propId: LsiSymbolId,
    ): Pair<JimmerImmutableDraftTypePlan, JimmerImmutableDraftPropPlan> {
        schema.types.forEach { candidate ->
            candidate.propsById[propId]?.let { prop -> return candidate to prop }
        }
        error("Immutable draft dependency property is missing: ${propId.value}")
    }

    internal fun type(typeId: LsiSymbolId): JimmerImmutableDraftTypePlan {
        return requireNotNull(schema.typesById[typeId]) {
            "Immutable draft target type is not present in schema: ${typeId.value}"
        }
    }

    internal fun modelType(typeId: LsiSymbolId): LsiDeclaredType {
        return LsiDeclaredType(typeId)
    }

    internal fun draftType(typeId: LsiSymbolId): LsiDeclaredType {
        val target = type(typeId)
        val targetDraftId = LsiSymbolId.type("${target.qualifiedName}$KOTLIN_DRAFT_SUFFIX")
        return replaceRawType(target.selfType, targetDraftId)
    }

    internal fun producerType(typeId: LsiSymbolId): LsiDeclaredType {
        val target = type(typeId)
        return LsiDeclaredType(
            LsiSymbolId.type("${target.qualifiedName}$KOTLIN_DRAFT_SUFFIX.$KOTLIN_DRAFT_PRODUCER")
        )
    }

    internal fun propType(prop: JimmerImmutableDraftPropPlan): LsiTypeRef {
        return prop.type
    }

    internal fun propElementType(prop: JimmerImmutableDraftPropPlan): LsiTypeRef {
        return prop.elementType
    }

    internal fun propDraftType(
        prop: JimmerImmutableDraftPropPlan,
        nullable: Boolean = prop.nullable,
    ): LsiTypeRef {
        val draftPropType = when {
            prop.list -> draftDeclaredType(
                KOTLIN_DRAFT_MUTABLE_LIST_TYPE_ID,
                if (prop.immutableReference && prop.targetTypeId != null) {
                    replaceRawType(prop.elementType, draftType(prop.targetTypeId).declarationId)
                } else {
                    prop.elementType
                },
            )
            prop.immutableReference && prop.targetTypeId != null -> {
                replaceRawType(prop.type, draftType(prop.targetTypeId).declarationId)
            }
            else -> prop.type
        }
        return draftPropType.withDraftRootNullability(nullable)
    }

    internal fun associatedIdProp(prop: JimmerImmutableDraftPropPlan): JimmerImmutableDraftPropPlan {
        val contract = requireNotNull(prop.associatedId)
        val targetTypeId = requireNotNull(prop.targetTypeId)
        return requireNotNull(type(targetTypeId).propsById[contract.targetIdPropId]) {
            "Immutable associated id property is missing: ${contract.targetIdPropId.value}"
        }
    }

    internal fun associatedIdType(prop: JimmerImmutableDraftPropPlan): LsiTypeRef {
        return associatedIdProp(prop).type.withDraftRootNullability(prop.nullable)
    }

    internal fun slotReference(prop: JimmerImmutableDraftPropPlan): LsiPoetCodeBlock {
        val owner = type(prop.runtimeOwnerTypeId)
        return draftCode {
            if (owner.typeId == type.typeId || owner.kind == ImmutableTypeKind.MAPPED_SUPERCLASS) {
                text(prop.slotIndex.toString())
            } else {
                type(producerType(owner.typeId))
                text(".")
                name(prop.slotName)
            }
        }
    }

    internal fun ownerSlotReference(propId: LsiSymbolId): LsiPoetCodeBlock {
        val (owner, ownerProp) = globalProp(propId)
        return draftCode {
            type(producerType(owner.typeId))
            text(".")
            name(ownerProp.slotName)
        }
    }

    internal fun generatedByAnnotation(): LsiPoetAnnotation {
        return LsiPoetAnnotation(
            type = KOTLIN_DRAFT_GENERATED_BY_TYPE_ID,
            arguments = listOf(
                LsiPoetAnnotationArgument.Named(
                    name = "type",
                    value = LsiPoetAnnotationValue.ClassValue(modelRawType),
                )
            ),
        )
    }

    internal fun jsonIgnoreAnnotation(): LsiPoetAnnotation {
        return LsiPoetAnnotation(
            type = KOTLIN_DRAFT_JSON_IGNORE_TYPE_ID,
            useSiteTarget = LsiAnnotationUseSiteTarget.GETTER,
        )
    }

    internal fun descriptionAnnotation(text: String): LsiPoetAnnotation {
        return LsiPoetAnnotation(
            type = KOTLIN_DRAFT_DESCRIPTION_TYPE_ID,
            arguments = listOf(
                LsiPoetAnnotationArgument.Named(
                    name = "value",
                    value = LsiPoetAnnotationValue.StringValue(text),
                )
            ),
        )
    }

    internal fun illegalPropertyCode(argumentName: String): LsiPoetCodeBlock {
        return draftCode {
            text("throw IllegalArgumentException(\n")
            indent {
                string("Illegal property name")
                text(" + ")
                line()
                string(" for \"${type.qualifiedName}\": ")
                text(" + ")
                line()
                name(argumentName)
                line()
            }
            text(")")
        }
    }

    internal fun addAssociatedIdProperty(
        prop: JimmerImmutableDraftPropPlan,
        withImplementation: Boolean,
    ): LsiPoetProperty? {
        val contract = prop.associatedId ?: return null
        val associatedIdProp = associatedIdProp(prop)
        return LsiPoetProperty(
            name = contract.name,
            nameStyle = LsiPoetNameStyle.KOTLIN_ESCAPED,
            type = associatedIdType(prop),
            mutable = true,
            annotations = listOf(jsonIgnoreAnnotation()),
            modifiers = buildSet {
                add(LsiPoetModifier.PUBLIC)
                add(if (withImplementation) LsiPoetModifier.OVERRIDE else LsiPoetModifier.ABSTRACT)
            },
            getter = if (!withImplementation) {
                null
            } else {
                LsiPoetAccessor(
                    body = draftCode {
                        returnValue {
                            name(prop.name)
                            text(if (prop.nullable) "?." else ".")
                            name(associatedIdProp.name)
                        }
                    },
                )
            },
            setter = if (!withImplementation) {
                null
            } else {
                LsiPoetAccessor(
                    setterParameterName = contract.name,
                    setterParameterNameStyle = LsiPoetNameStyle.KOTLIN_ESCAPED,
                    body = draftCode {
                        if (prop.nullable) {
                            beginControlFlow {
                                text("if (")
                                name(contract.name)
                                text(" === null)")
                            }
                            statement {
                                text("this.")
                                name(prop.name)
                                text(" = null")
                            }
                            returnVoid()
                            endControlFlow()
                        }
                        statement {
                            name(prop.name)
                            text("().")
                            name(associatedIdProp.name)
                            text(" = ")
                            name(contract.name)
                        }
                    },
                )
            },
        )
    }

    private fun draftDeclaration(): LsiPoetType {
        return LsiPoetType(
            name = "$simpleName$KOTLIN_DRAFT_SUFFIX",
            nameStyle = LsiPoetNameStyle.KOTLIN_ESCAPED,
            kind = LsiTypeDeclarationKind.INTERFACE,
            annotations = buildList {
                add(KOTLIN_DRAFT_DSL_SCOPE_ANNOTATION)
                add(generatedByAnnotation())
                type.documentation?.takeIf(String::isNotEmpty)?.let { documentation ->
                    add(descriptionAnnotation(documentation))
                }
            },
            typeParameters = type.typeParameters,
            superInterfaces = buildList {
                add(modelType)
                if (type.directSuperTypes.isEmpty()) {
                    add(KOTLIN_DRAFT_MARKER_TYPE)
                } else {
                    type.directSuperTypes.mapTo(this) { superType -> draftSuperType(superType) }
                }
            },
            members = buildList {
                type.kotlinDraftPropIds.map(::prop).forEach { prop ->
                    if (prop.manyToManyBasePropId == null) {
                        add(draftProperty(prop))
                        draftAutoCreateFunction(prop)?.let(::add)
                        draftReferenceFunction(prop)?.let(::add)
                        addAssociatedIdProperty(prop, withImplementation = false)?.let(::add)
                    }
                }
                add(producerDeclaration())
                if (!mappedSuperclass) {
                    add(builderDeclaration())
                }
            },
        )
    }

    private fun draftSuperType(superType: LsiDeclaredType): LsiDeclaredType {
        return replaceRawType(
            superType,
            LsiSymbolId.type("${type(superType.declarationId).qualifiedName}$KOTLIN_DRAFT_SUFFIX"),
        )
    }

    private fun draftProperty(prop: JimmerImmutableDraftPropPlan): LsiPoetProperty {
        return LsiPoetProperty(
            name = prop.name,
            nameStyle = LsiPoetNameStyle.KOTLIN_ESCAPED,
            type = propType(prop),
            mutable = prop.writable,
            annotations = buildList {
                prop.documentation?.takeIf(String::isNotEmpty)?.let { documentation ->
                    add(descriptionAnnotation(documentation))
                }
            },
            modifiers = setOf(LsiPoetModifier.OVERRIDE),
        )
    }

    private fun draftAutoCreateFunction(prop: JimmerImmutableDraftPropPlan): LsiPoetFunction? {
        if (!prop.autoCreateSupported) {
            return null
        }
        return LsiPoetFunction(
            name = prop.name,
            nameStyle = LsiPoetNameStyle.KOTLIN_ESCAPED,
            modifiers = setOf(LsiPoetModifier.ABSTRACT),
            returnType = propDraftType(prop, nullable = false),
        )
    }

    private fun draftReferenceFunction(prop: JimmerImmutableDraftPropPlan): LsiPoetFunction? {
        if (!prop.referenceMutationSupported || prop.list) {
            return null
        }
        return LsiPoetFunction(
            name = prop.name,
            nameStyle = LsiPoetNameStyle.KOTLIN_ESCAPED,
            modifiers = setOf(LsiPoetModifier.ABSTRACT),
            parameters = listOf(
                LsiPoetParameter(
                    name = "block",
                    type = draftReceiverFunctionType(propDraftType(prop, nullable = false)),
                )
            ),
        )
    }

    private fun producerDeclaration(): LsiPoetType {
        val runtimePoet = ImmutableDraftKotlinRuntimePoet(this)
        return LsiPoetType(
            name = KOTLIN_DRAFT_PRODUCER,
            nameStyle = LsiPoetNameStyle.KOTLIN_ESCAPED,
            kind = LsiTypeDeclarationKind.OBJECT,
            annotations = listOf(generatedByAnnotation()),
            members = buildList {
                if (!mappedSuperclass) {
                    propsInDeclarationOrder.mapTo(this) { prop -> slotProperty(prop) }
                }
                add(runtimeTypeProperty())
                if (!mappedSuperclass) {
                    add(produceFunction(withBlock = false))
                    add(produceFunction(withBlock = true))
                    add(runtimePoet.implementor())
                    add(runtimePoet.impl())
                    add(runtimePoet.draftImpl())
                }
            },
        )
    }

    private fun slotProperty(prop: JimmerImmutableDraftPropPlan): LsiPoetProperty {
        return LsiPoetProperty(
            name = prop.slotName,
            nameStyle = LsiPoetNameStyle.KOTLIN_ESCAPED,
            type = KOTLIN_DRAFT_INT_TYPE,
            mutable = false,
            modifiers = setOf(LsiPoetModifier.CONST),
            initializer = slotReference(prop),
        )
    }

    private fun runtimeTypeProperty(): LsiPoetProperty {
        return LsiPoetProperty(
            name = "type",
            type = KOTLIN_DRAFT_IMMUTABLE_TYPE,
            mutable = false,
            initializer = runtimeTypeInitializer(),
        )
    }

    private fun runtimeTypeInitializer(): LsiPoetCodeBlock {
        return draftCode {
            if (!mappedSuperclass) {
                preserveExplicitIndentation()
            }
            type(KOTLIN_DRAFT_IMMUTABLE_TYPE)
            line()
            indent {
                text(".newBuilder(\n")
                indent {
                    string(currentVersion())
                    text(",\n")
                    type(modelRawType)
                    text("::class,\n")
                    text("listOf(\n")
                    indent {
                        type.directSuperTypes.forEachIndexed { index, superType ->
                            if (index != 0) {
                                text(",\n")
                            }
                            type(producerType(superType.declarationId))
                            text(".type")
                        }
                        line()
                    }
                    text("),\n")
                    if (mappedSuperclass) {
                        text("null\n")
                    }
                }
                if (mappedSuperclass) {
                    text(")\n")
                } else {
                    text(") { ctx, base ->\n")
                    indent {
                        type(draftImplType)
                        text("(ctx, base as ")
                        type(modelRawType.withDraftRootNullability(true))
                        text(")\n")
                    }
                    text("}\n")
                }
                if (!mappedSuperclass) {
                    type.runtimeRedefinedPropIds.map(::prop).forEach { prop ->
                        text(".redefine(")
                        string(prop.name)
                        text(", ")
                        name(prop.slotName)
                        text(")\n")
                    }
                }
                type.runtimeDeclaredPropIds.map(::prop).forEach { prop -> addRuntimeProp(prop) }
                text(".build()")
            }
        }
    }

    private fun LsiPoetCodeBuilder.addRuntimeProp(prop: JimmerImmutableDraftPropPlan) {
        val metadataType = prop.runtimeProp.metadataElementType.withDraftRootNullability(false)
        when (prop.runtimeProp.kind) {
            ImmutableDraftRuntimePropKind.ID -> {
                text(".id(")
                addRuntimeSlot(prop)
                text(", ")
                string(prop.name)
                text(", ")
                type(metadataType)
                text("::class.java)\n")
            }
            ImmutableDraftRuntimePropKind.VERSION -> {
                text(".version(")
                addRuntimeSlot(prop)
                text(", ")
                string(prop.name)
                text(")\n")
            }
            ImmutableDraftRuntimePropKind.LOGICAL_DELETED -> {
                text(".logicalDeleted(")
                addRuntimeSlot(prop)
                text(", ")
                string(prop.name)
                text(", ")
                type(metadataType)
                text("::class.java, ${prop.nullable})\n")
            }
            ImmutableDraftRuntimePropKind.KEY_SCALAR -> {
                text(".key(")
                addRuntimeSlot(prop)
                text(", ")
                string(prop.name)
                text(", ")
                type(metadataType)
                text("::class.java, ${prop.nullable})\n")
            }
            ImmutableDraftRuntimePropKind.KEY_REFERENCE -> {
                text(".keyReference(")
                addRuntimeSlot(prop)
                text(", ")
                string(prop.name)
                text(", ")
                type(LsiDeclaredType(requireNotNull(prop.runtimeProp.associationAnnotationTypeId)))
                text("::class.java, ")
                type(metadataType)
                text("::class.java, ${prop.nullable})\n")
            }
            ImmutableDraftRuntimePropKind.ASSOCIATION -> {
                text(".add(")
                addRuntimeSlot(prop)
                text(", ")
                string(prop.name)
                text(", ")
                type(LsiDeclaredType(requireNotNull(prop.runtimeProp.associationAnnotationTypeId)))
                text("::class.java, ")
                type(metadataType)
                text("::class.java, ${prop.nullable})\n")
            }
            ImmutableDraftRuntimePropKind.VALUE -> {
                text(".add(")
                addRuntimeSlot(prop)
                text(", ")
                string(prop.name)
                text(", ")
                type(KOTLIN_DRAFT_IMMUTABLE_PROP_CATEGORY)
                text(".${prop.runtimeProp.valueCategory.draftRuntimeName}, ")
                type(metadataType)
                text("::class.java, ${prop.nullable})\n")
            }
        }
    }

    private fun LsiPoetCodeBuilder.addRuntimeSlot(prop: JimmerImmutableDraftPropPlan) {
        if (mappedSuperclass) {
            text("-1")
        } else {
            name(prop.slotName)
        }
    }

    private fun produceFunction(withBlock: Boolean): LsiPoetFunction {
        return LsiPoetFunction(
            name = "produce",
            parameters = buildList {
                add(
                    LsiPoetParameter(
                        name = "base",
                        type = modelRawType.withDraftRootNullability(true),
                        defaultValue = draftCode { text("null") },
                    )
                )
                add(
                    LsiPoetParameter(
                        name = "resolveImmediately",
                        type = KOTLIN_DRAFT_BOOLEAN_TYPE,
                        defaultValue = draftCode { text("false") },
                    )
                )
                if (withBlock) {
                    add(LsiPoetParameter("block", draftReceiverFunctionType(draftType)))
                }
            },
            returnType = modelRawType,
            body = draftCode {
                statement {
                    text("val consumer = ")
                    type(draftDeclaredType(KOTLIN_DRAFT_CONSUMER_TYPE_ID, draftType))
                    text(if (withBlock) " { block(it) }" else " {}")
                }
                returnValue {
                    type(KOTLIN_DRAFT_INTERNAL_TYPE)
                    text(".produce(type, base, resolveImmediately, consumer) as ")
                    type(modelRawType)
                }
            },
        )
    }

    private fun builderDeclaration(): LsiPoetType {
        return LsiPoetType(
            name = KOTLIN_DRAFT_BUILDER,
            kind = LsiTypeDeclarationKind.CLASS,
            annotations = listOf(generatedByAnnotation()),
            members = buildList {
                add(
                    LsiPoetProperty(
                        name = "__draft",
                        type = draftImplType,
                        mutable = false,
                        modifiers = setOf(LsiPoetModifier.PRIVATE),
                    )
                )
                add(builderBaseConstructor())
                add(
                    LsiPoetConstructor(
                        delegationCall = LsiPoetDelegationCall(
                            target = LsiPoetDelegationTarget.THIS,
                            arguments = listOf(draftCode { text("null") }),
                        ),
                    )
                )
                propsInDeclarationOrder.filter(JimmerImmutableDraftPropPlan::writable).forEach { prop ->
                    add(builderSetter(prop))
                }
                add(
                    LsiPoetFunction(
                        name = "build",
                        returnType = modelRawType,
                        body = draftCode {
                            returnValue {
                                text("__draft.__unwrap() as ")
                                type(modelRawType)
                            }
                        },
                    )
                )
            },
        )
    }

    private fun builderBaseConstructor(): LsiPoetConstructor {
        return LsiPoetConstructor(
            parameters = listOf(
                LsiPoetParameter("base", modelRawType.withDraftRootNullability(true))
            ),
            body = draftCode {
                statement {
                    text("__draft = ")
                    type(draftImplType)
                    text("(null, base)")
                }
                propsInDeclarationOrder
                    .filter(JimmerImmutableDraftPropPlan::visibilityControllable)
                    .forEach { prop ->
                        statement {
                            text("__draft.__show(")
                            type(KOTLIN_DRAFT_PROP_ID_TYPE)
                            text(".byIndex(")
                            type(producerType)
                            text(".")
                            name(prop.slotName)
                            text("), false)")
                        }
                    }
            },
        )
    }

    private fun builderSetter(prop: JimmerImmutableDraftPropPlan): LsiPoetFunction {
        return LsiPoetFunction(
            name = prop.name,
            nameStyle = LsiPoetNameStyle.KOTLIN_ESCAPED,
            annotations = prop.annotationPlan.builderMethodAnnotations.map(
                LsiAnnotation::toKotlinDraftPoetAnnotationWithDefaults
            ),
            parameters = listOf(
                LsiPoetParameter(
                    name = prop.name,
                    nameStyle = LsiPoetNameStyle.KOTLIN_ESCAPED,
                    type = propType(prop).withDraftRootNullability(true),
                )
            ),
            returnType = builderType,
            body = draftCode {
                if (prop.nullable) {
                    addBuilderAssignment(prop)
                } else {
                    beginControlFlow {
                        text("if (")
                        name(prop.name)
                        text(" !== null)")
                    }
                    addBuilderAssignment(prop)
                    endControlFlow()
                }
                returnValue { text("this") }
            },
        )
    }

    private fun LsiPoetCodeBuilder.addBuilderAssignment(prop: JimmerImmutableDraftPropPlan) {
        statement {
            text("__draft.")
            name(prop.name)
            text(" = ")
            name(prop.name)
        }
        statement {
            text("__draft.__show(")
            type(KOTLIN_DRAFT_PROP_ID_TYPE)
            text(".byIndex(")
            type(producerType)
            text(".")
            name(prop.slotName)
            text("), true)")
        }
    }

    private fun creatorFunctions(): List<LsiPoetFunction> {
        return listOf(
            newByFunction(withCreator = true, withBase = false, withBlock = true),
            newByFunction(withCreator = true, withBase = true, withBlock = false),
            newByFunction(withCreator = true, withBase = true, withBlock = true),
            newByFunction(withCreator = false, withBase = false, withBlock = true),
            newByFunction(withCreator = false, withBase = true, withBlock = true),
            addByFunction(withBase = false, withBlock = true),
            addByFunction(withBase = true, withBlock = false),
            addByFunction(withBase = true, withBlock = true),
            copyFunction(),
        )
    }

    private fun newByFunction(
        withCreator: Boolean,
        withBase: Boolean,
        withBlock: Boolean,
    ): LsiPoetFunction {
        return LsiPoetFunction(
            name = if (withCreator) "by" else simpleName,
            nameStyle = if (withCreator) {
                LsiPoetNameStyle.IDENTIFIER
            } else {
                LsiPoetNameStyle.KOTLIN_ESCAPED
            },
            annotations = listOf(generatedByAnnotation()),
            receiverType = if (withCreator) {
                draftDeclaredType(KOTLIN_DRAFT_IMMUTABLE_CREATOR_TYPE_ID, modelRawType)
            } else {
                null
            },
            parameters = creatorParameters(withBase, withBlock),
            returnType = modelRawType,
            body = draftCode {
                returnValue {
                    type(producerType)
                    text(".produce(")
                    add(produceArguments(withBase, withBlock))
                    text(")")
                }
            },
        )
    }

    private fun addByFunction(
        withBase: Boolean,
        withBlock: Boolean,
    ): LsiPoetFunction {
        val receiverType = draftDeclaredType(KOTLIN_DRAFT_MUTABLE_LIST_TYPE_ID, draftType)
        return LsiPoetFunction(
            name = "addBy",
            annotations = listOf(generatedByAnnotation()),
            receiverType = receiverType,
            parameters = creatorParameters(withBase, withBlock),
            returnType = receiverType,
            body = draftCode {
                statement {
                    text("add(")
                    type(producerType)
                    text(".produce(")
                    add(produceArguments(withBase, withBlock))
                    text(") as ")
                    type(draftType)
                    text(")")
                }
                returnValue { text("this") }
            },
        )
    }

    private fun copyFunction(): LsiPoetFunction {
        return LsiPoetFunction(
            name = "copy",
            annotations = listOf(generatedByAnnotation()),
            receiverType = modelRawType,
            parameters = listOf(
                LsiPoetParameter(
                    name = "resolveImmediately",
                    type = KOTLIN_DRAFT_BOOLEAN_TYPE,
                    defaultValue = draftCode { text("false") },
                ),
                LsiPoetParameter("block", draftReceiverFunctionType(draftType)),
            ),
            returnType = modelRawType,
            body = draftCode {
                returnValue {
                    type(producerType)
                    text(".produce(this, resolveImmediately, block)")
                }
            },
        )
    }

    private fun creatorParameters(
        withBase: Boolean,
        withBlock: Boolean,
    ): List<LsiPoetParameter> {
        return buildList {
            if (withBase) {
                add(LsiPoetParameter("base", modelRawType.withDraftRootNullability(true)))
            }
            add(
                LsiPoetParameter(
                    name = "resolveImmediately",
                    type = KOTLIN_DRAFT_BOOLEAN_TYPE,
                    defaultValue = draftCode { text("false") },
                )
            )
            if (withBlock) {
                add(LsiPoetParameter("block", draftReceiverFunctionType(draftType)))
            }
        }
    }

    private fun produceArguments(
        withBase: Boolean,
        withBlock: Boolean,
    ): LsiPoetCodeBlock {
        return draftCode {
            text(if (withBase) "base" else "null")
            text(", resolveImmediately")
            if (withBlock) {
                text(", block")
            }
        }
    }

    private fun generatedNestedType(nestedName: String): LsiDeclaredType {
        return LsiDeclaredType(
            LsiSymbolId.type("${type.qualifiedName}$KOTLIN_DRAFT_SUFFIX.$nestedName")
        )
    }
}

private fun LsiAnnotation.toKotlinDraftPoetAnnotationWithDefaults(): LsiPoetAnnotation {
    val preferredOrder = when (type.requireTypeQualifiedName().substringAfterLast('.')) {
        "Size" -> listOf("min", "max", "message", "groups", "payload")
        "Pattern" -> listOf("regexp", "message", "flags", "groups", "payload")
        "Min", "Max" -> listOf("value", "message", "groups", "payload")
        "DecimalMin", "DecimalMax" -> listOf("value", "inclusive", "message", "groups", "payload")
        "Digits" -> listOf("integer", "fraction", "message", "groups", "payload")
        else -> listOf("message", "groups", "payload")
    }
    val orderedArguments = arguments.entries.sortedWith(
        compareBy<Map.Entry<String, site.addzero.lsi.model.LsiAnnotationArgument>> { entry ->
            preferredOrder.indexOf(entry.key).takeIf { index -> index >= 0 } ?: Int.MAX_VALUE
        }
            .thenBy { entry -> if (entry.value.isExplicit) 0 else 1 }
            .thenBy(Map.Entry<String, site.addzero.lsi.model.LsiAnnotationArgument>::key)
    )
    return LsiPoetAnnotation(
        type = type,
        arguments = orderedArguments.map { (name, argument) ->
            LsiPoetAnnotationArgument.Named(
                name = name,
                value = argument.value.toKotlinDraftPoetAnnotationValue(
                    LsiPoetAnnotationArrayStyle.KOTLIN_ARRAY_OF
                ),
            )
        },
        useSiteTarget = useSiteTarget,
    )
}

private fun LsiAnnotation.toKotlinDraftNestedPoetAnnotation(): LsiPoetAnnotation {
    return LsiPoetAnnotation(
        type = type,
        arguments = arguments
            .asSequence()
            .filter { (_, argument) -> argument.isExplicit }
            .sortedBy { (name, _) -> name }
            .map { (name, argument) ->
                LsiPoetAnnotationArgument.Named(
                    name = name,
                    value = argument.value.toKotlinDraftPoetAnnotationValue(
                        LsiPoetAnnotationArrayStyle.LITERAL
                    ),
                )
            }
            .toList(),
        useSiteTarget = useSiteTarget,
    )
}

private fun LsiAnnotationValue.toKotlinDraftPoetAnnotationValue(
    arrayStyle: LsiPoetAnnotationArrayStyle,
): LsiPoetAnnotationValue {
    return when (this) {
        is LsiAnnotationValue.BooleanValue -> LsiPoetAnnotationValue.BooleanValue(value)
        is LsiAnnotationValue.ByteValue -> LsiPoetAnnotationValue.ByteValue(value)
        is LsiAnnotationValue.ShortValue -> LsiPoetAnnotationValue.ShortValue(value)
        is LsiAnnotationValue.IntValue -> LsiPoetAnnotationValue.IntValue(value)
        is LsiAnnotationValue.LongValue -> LsiPoetAnnotationValue.LongValue(value)
        is LsiAnnotationValue.FloatValue -> LsiPoetAnnotationValue.FloatValue(value)
        is LsiAnnotationValue.DoubleValue -> LsiPoetAnnotationValue.DoubleValue(value)
        is LsiAnnotationValue.CharValue -> LsiPoetAnnotationValue.CharValue(value)
        is LsiAnnotationValue.StringValue -> LsiPoetAnnotationValue.StringValue(value)
        is LsiAnnotationValue.EnumValue -> LsiPoetAnnotationValue.EnumValue(enumType, entryName)
        is LsiAnnotationValue.ClassValue -> LsiPoetAnnotationValue.ClassValue(type)
        is LsiAnnotationValue.NestedAnnotationValue -> LsiPoetAnnotationValue.NestedAnnotationValue(
            annotation.toKotlinDraftNestedPoetAnnotation()
        )
        is LsiAnnotationValue.ArrayValue -> LsiPoetAnnotationValue.ArrayValue(
            elements = elements.map { element ->
                element.toKotlinDraftPoetAnnotationValue(arrayStyle)
            },
            sourceStyle = arrayStyle,
        )
    }
}

private fun replaceRawType(
    sourceType: LsiTypeRef,
    rawTypeId: LsiSymbolId,
): LsiDeclaredType {
    val declaredType = sourceType as? LsiDeclaredType
    return LsiDeclaredType(
        declarationId = rawTypeId,
        arguments = declaredType?.arguments.orEmpty(),
        nullability = sourceType.nullability,
        annotations = sourceType.annotations,
    )
}

internal fun LsiTypeRef.withDraftRootNullability(nullable: Boolean): LsiTypeRef {
    val nullability = if (nullable) LsiNullability.NULLABLE else LsiNullability.NON_NULL
    return when (this) {
        is LsiArrayType -> copy(nullability = nullability)
        is LsiDeclaredType -> copy(nullability = nullability)
        is LsiFunctionType -> copy(nullability = nullability)
        is LsiPrimitiveType -> copy(nullability = nullability)
        is LsiTypeParameterRef -> copy(nullability = nullability)
        is LsiUnresolvedType -> copy(nullability = nullability)
    }
}

internal fun draftReceiverFunctionType(receiverType: LsiTypeRef): LsiFunctionType {
    return LsiFunctionType(
        receiverType = receiverType,
        returnType = KOTLIN_DRAFT_UNIT_TYPE,
    )
}

internal val ImmutablePropValueCategory.draftRuntimeName: String
    get() = when (this) {
        ImmutablePropValueCategory.SCALAR -> "SCALAR"
        ImmutablePropValueCategory.SCALAR_LIST -> "SCALAR_LIST"
        ImmutablePropValueCategory.REFERENCE -> "REFERENCE"
        ImmutablePropValueCategory.REFERENCE_LIST -> "REFERENCE_LIST"
    }

internal const val KOTLIN_DRAFT_SUFFIX = "Draft"
internal const val KOTLIN_DRAFT_PRODUCER = "$"
internal const val KOTLIN_DRAFT_IMPLEMENTOR = "Implementor"
internal const val KOTLIN_DRAFT_IMPL = "Impl"
internal const val KOTLIN_DRAFT_DRAFT_IMPL = "DraftImpl"
internal const val KOTLIN_DRAFT_BUILDER = "Builder"
internal const val KOTLIN_DRAFT_FROZEN_EXCEPTION_MESSAGE =
    "The current draft has been resolved so it cannot be modified"

internal val KOTLIN_DRAFT_BOOLEAN_TYPE = LsiPrimitiveType(LsiPrimitiveKind.BOOLEAN)
internal val KOTLIN_DRAFT_INT_TYPE = LsiPrimitiveType(LsiPrimitiveKind.INT)
internal val KOTLIN_DRAFT_UNIT_TYPE = LsiPrimitiveType(LsiPrimitiveKind.UNIT)
internal val KOTLIN_DRAFT_ANY_TYPE_ID = LsiSymbolId.type("kotlin.Any")
internal val KOTLIN_DRAFT_ANY_TYPE = LsiDeclaredType(KOTLIN_DRAFT_ANY_TYPE_ID)
internal val KOTLIN_DRAFT_STRING_TYPE_ID = LsiSymbolId.type("kotlin.String")
internal val KOTLIN_DRAFT_STRING_TYPE = LsiDeclaredType(KOTLIN_DRAFT_STRING_TYPE_ID)
internal val KOTLIN_DRAFT_MUTABLE_LIST_TYPE_ID = LsiSymbolId.type("kotlin.collections.MutableList")
internal val KOTLIN_CLONEABLE_TYPE = LsiDeclaredType(LsiSymbolId.type("kotlin.Cloneable"))
internal val KOTLIN_SERIALIZABLE_TYPE = LsiDeclaredType(LsiSymbolId.type("java.io.Serializable"))
internal val KOTLIN_DRAFT_MARKER_TYPE = LsiDeclaredType(LsiSymbolId.type("org.babyfish.jimmer.Draft"))
internal val KOTLIN_DRAFT_CONSUMER_TYPE_ID = LsiSymbolId.type("org.babyfish.jimmer.DraftConsumer")
internal val KOTLIN_DRAFT_DSL_SCOPE_TYPE_ID = LsiSymbolId.type("org.babyfish.jimmer.kt.DslScope")
internal val KOTLIN_DRAFT_IMMUTABLE_CREATOR_TYPE_ID = LsiSymbolId.type(
    "org.babyfish.jimmer.kt.ImmutableCreator"
)
internal val KOTLIN_DRAFT_IMMUTABLE_TYPE = LsiDeclaredType(
    LsiSymbolId.type("org.babyfish.jimmer.meta.ImmutableType")
)
internal val KOTLIN_DRAFT_IMMUTABLE_PROP_CATEGORY = LsiDeclaredType(
    LsiSymbolId.type("org.babyfish.jimmer.meta.ImmutablePropCategory")
)
internal val KOTLIN_DRAFT_PROP_ID_TYPE = LsiDeclaredType(
    LsiSymbolId.type("org.babyfish.jimmer.meta.PropId")
)
internal val KOTLIN_DRAFT_INTERNAL_TYPE = LsiDeclaredType(
    LsiSymbolId.type("org.babyfish.jimmer.runtime.Internal")
)
internal val KOTLIN_DRAFT_GENERATED_BY_TYPE_ID = LsiSymbolId.type(
    "org.babyfish.jimmer.internal.GeneratedBy"
)
internal val KOTLIN_DRAFT_DESCRIPTION_TYPE_ID = LsiSymbolId.type(
    "org.babyfish.jimmer.client.Description"
)
internal val KOTLIN_DRAFT_JSON_IGNORE_TYPE_ID = LsiSymbolId.type(
    "com.fasterxml.jackson.annotation.JsonIgnore"
)

private val KOTLIN_DRAFT_DSL_SCOPE_ANNOTATION = LsiPoetAnnotation(
    type = KOTLIN_DRAFT_DSL_SCOPE_TYPE_ID,
)

private val KOTLIN_DRAFT_WARNING_SUPPRESSION = LsiPoetAnnotation(
    type = LsiSymbolId.type("kotlin.Suppress"),
    arguments = listOf(
        LsiPoetAnnotationArgument.Positional(
            LsiPoetAnnotationValue.StringValue("warnings")
        )
    ),
    useSiteTarget = LsiAnnotationUseSiteTarget.FILE,
)
