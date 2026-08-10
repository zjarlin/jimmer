package org.babyfish.jimmer.compiler.immutable

import site.addzero.lsi.anno.sourceLsiAnnotation

import org.babyfish.jimmer.currentVersion
import org.babyfish.jimmer.impl.util.StringUtil
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.jimmer.ImmutableDraftRuntimePropKind
import site.addzero.lsi.jimmer.ImmutableTypeKind
import site.addzero.lsi.anno.LsiAnnotation
import site.addzero.lsi.anno.LsiAnnotationValue
import site.addzero.lsi.type.LsiArrayType
import site.addzero.lsi.type.LsiDeclaredType
import site.addzero.lsi.type.LsiFunctionType
import site.addzero.lsi.type.LsiPrimitiveKind
import site.addzero.lsi.type.LsiPrimitiveType
import site.addzero.lsi.type.LsiTypeParameter
import site.addzero.lsi.type.LsiTypeParameterRef
import site.addzero.lsi.type.LsiType
import site.addzero.lsi.type.LsiUnresolvedType
import site.addzero.lsi.anno.LsiSourceAnnotationArgument
import site.addzero.lsi.model.LsiCodeBlock
import site.addzero.lsi.model.LsiCodeBuilder
import site.addzero.lsi.model.LsiConstructor
import site.addzero.lsi.field.LsiField
import site.addzero.lsi.file.LsiFile
import site.addzero.lsi.method.LsiMethod
import site.addzero.lsi.model.LsiMember
import site.addzero.lsi.model.LsiModifier
import site.addzero.lsi.method.LsiParameter
import site.addzero.lsi.clazz.LsiClass
import site.addzero.lsi.model.LsiTypeDeclarationKind
import site.addzero.lsi.model.LsiTypeReferenceStyle

/**
 * 把一个不可变类型的 Draft 代码生成计划降低为 Java LSI Poet 文件。
 */
internal fun JimmerImmutableDraftCodegenSchema.toJavaDraftPoetFile(
    type: JimmerImmutableDraftTypePlan,
): LsiFile {
    require(typesById[type.typeId] == type) {
        "Immutable draft type '${type.typeId.value}' does not belong to the supplied schema"
    }
    return JavaDraftPoetContext(this, type).file()
}

private class JavaDraftPoetContext(
    private val schema: JimmerImmutableDraftCodegenSchema,
    private val type: JimmerImmutableDraftTypePlan,
) {

    private val modelType = LsiDeclaredType(type.typeId)

    private val draftTypeId = LsiSymbolId.type("${type.qualifiedName}Draft")

    private val draftRawType = LsiDeclaredType(draftTypeId)

    private val producerType = LsiDeclaredType(LsiSymbolId.type("${type.qualifiedName}Draft.$PRODUCER"))

    private val implementorType = LsiDeclaredType(
        LsiSymbolId.type("${type.qualifiedName}Draft.$PRODUCER.$IMPLEMENTOR")
    )

    private val implType = LsiDeclaredType(LsiSymbolId.type("${type.qualifiedName}Draft.$PRODUCER.$IMPL"))

    private val draftImplType = LsiDeclaredType(
        LsiSymbolId.type("${type.qualifiedName}Draft.$PRODUCER.$DRAFT_IMPL")
    )

    private val builderType = LsiDeclaredType(LsiSymbolId.type("${type.qualifiedName}Draft.$BUILDER"))

    private val parameterizedDraftType = if (type.typeParameters.isEmpty()) {
        draftRawType
    } else {
        draftDeclaredType(
            draftTypeId,
            *type.typeParameters
                .map { parameter -> LsiTypeParameterRef(parameter.id) }
                .toTypedArray(),
        )
    }

    private val legacyProps = buildList {
        type.idPropId?.let { propId -> add(type.propsById.getValue(propId)) }
        addAll(type.propsBySlot.filterNot { prop -> prop.propId == type.idPropId })
    }

    fun file(): LsiFile {
        return LsiFile(
            language = site.addzero.lsi.core.LsiLanguage.JAVA,
            packageName = type.packageName,
            fileName = "${type.simpleName}Draft",
            members = listOf(draftType()),
        )
    }

    private fun draftType(): LsiClass {
        return LsiClass(
            name = "${type.simpleName}Draft",
            kind = LsiTypeDeclarationKind.INTERFACE,
            annotations = buildList {
                add(generatedByAnnotation())
                type.documentation?.takeIf(String::isNotEmpty)?.let { documentation ->
                    add(descriptionAnnotation(documentation))
                }
            },
            modifiers = if (type.visibility == site.addzero.lsi.model.LsiVisibility.PUBLIC) {
                setOf(LsiModifier.PUBLIC)
            } else {
                emptySet()
            },
            typeParameters = type.typeParameters.map(LsiTypeParameter::withoutJavaDraftTypeAnnotations),
            superInterfaces = buildList {
                add(type.selfType.withoutJavaDraftTypeAnnotations())
                if (type.directSuperTypes.isEmpty()) {
                    add(DRAFT_TYPE)
                } else {
                    type.directSuperTypes.mapTo(this) { superType -> superType.toDraftType() }
                }
            },
            members = buildList {
                add(
                    LsiField(
                        name = "$",
                        type = producerType,
                        modifiers = PUBLIC_STATIC_FINAL,
                        initializer = draftCode {
                            type(producerType)
                            text(".INSTANCE")
                        },
                        typeReferenceStyle = LsiTypeReferenceStyle.SAME_PACKAGE_OUTER_QUALIFIED,
                    )
                )
                legacyProps.forEach { prop -> addAll(draftPropMembers(prop)) }
                add(producer())
                if (!type.isMappedSuperclass) {
                    add(builder())
                }
            },
        )
    }

    private fun draftPropMembers(prop: JimmerImmutableDraftPropPlan): List<LsiMember> {
        return buildList {
            if (prop.autoCreateSupported && prop.immutableReference && !prop.list) {
                add(draftGetter(prop, autoCreate = false))
            }
            if (prop.autoCreateSupported) {
                add(draftGetter(prop, autoCreate = true))
            }
            if (prop.writable) {
                add(draftSetter(prop))
            }
            addAll(associatedIdMembers(prop, withImplementation = false))
            if (prop.referenceMutationSupported) {
                add(draftReferenceMutationFunction(prop, withBase = false, withImplementation = false))
                add(draftReferenceMutationFunction(prop, withBase = true, withImplementation = false))
            }
        }
    }

    private fun draftGetter(
        prop: JimmerImmutableDraftPropPlan,
        autoCreate: Boolean,
    ): LsiMethod {
        return LsiMethod(
            name = prop.sourceGetterName,
            annotations = if (!autoCreate && prop.nullable) listOf(NULLABLE_ANNOTATION) else emptyList(),
            modifiers = PUBLIC_ABSTRACT,
            parameters = if (autoCreate) {
                listOf(LsiParameter("autoCreate", BOOLEAN_TYPE))
            } else {
                emptyList()
            },
            returnType = prop.draftType(autoCreate),
        )
    }

    private fun draftSetter(prop: JimmerImmutableDraftPropPlan): LsiMethod {
        return LsiMethod(
            name = prop.javaSetterName,
            annotations = buildList {
                add(OLD_CHAIN_ANNOTATION)
                prop.documentation?.takeIf(String::isNotEmpty)?.let { documentation ->
                    add(descriptionAnnotation(documentation))
                }
            },
            modifiers = PUBLIC_ABSTRACT,
            documentation = prop.documentation?.takeIf(String::isNotEmpty),
            parameters = listOf(
                LsiParameter(prop.codegenName, prop.type.withoutJavaDraftTypeAnnotations())
            ),
            returnType = parameterizedDraftType,
        )
    }

    private fun draftReferenceMutationFunction(
        prop: JimmerImmutableDraftPropPlan,
        withBase: Boolean,
        withImplementation: Boolean,
    ): LsiMethod {
        val functionName = if (prop.list) prop.javaAdderByName else prop.javaApplierName
        return LsiMethod(
            name = functionName,
            annotations = listOf(OLD_CHAIN_ANNOTATION),
            modifiers = buildSet {
                add(LsiModifier.PUBLIC)
                add(if (withImplementation) LsiModifier.OVERRIDE else LsiModifier.ABSTRACT)
            },
            parameters = buildList {
                if (withBase) {
                    add(
                        LsiParameter(
                            "base",
                            prop.elementType.withoutJavaDraftTypeAnnotations(),
                        )
                    )
                }
                add(
                    LsiParameter(
                        "block",
                        draftDeclaredType(DRAFT_CONSUMER_TYPE_ID, prop.draftElementType()),
                    )
                )
            },
            returnType = parameterizedDraftType,
            body = if (!withImplementation) {
                LsiCodeBlock.EMPTY
            } else {
                draftCode {
                    if (withBase) {
                        if (prop.list) {
                            statement {
                                name(prop.sourceGetterName)
                                text("(true).add((")
                                type(prop.draftElementType())
                                text(")")
                                type(prop.draftElementType())
                                text(".$.produce(base, block))")
                            }
                        } else {
                            statement {
                                name(prop.javaSetterName)
                                text("(")
                                type(prop.draftElementType())
                                text(".$.produce(base, block))")
                            }
                        }
                    } else {
                        statement {
                            name(functionName)
                            text("(null, block)")
                        }
                    }
                    returnValue { text("this") }
                }
            },
        )
    }

    private fun producer(): LsiClass {
        return LsiClass(
            name = PRODUCER,
            kind = LsiTypeDeclarationKind.CLASS,
            annotations = listOf(generatedByAnnotation()),
            modifiers = setOf(LsiModifier.PUBLIC, LsiModifier.STATIC),
            members = buildList {
                add(
                    LsiField(
                        name = "INSTANCE",
                        type = producerType,
                        modifiers = STATIC_FINAL,
                        initializer = draftCode {
                            text("new ")
                            type(producerType)
                            text("()")
                        },
                    )
                )
                if (!type.isMappedSuperclass) {
                    legacyProps.forEach { prop -> add(slotField(prop)) }
                }
                add(runtimeTypeField())
                add(LsiConstructor(modifiers = setOf(LsiModifier.PRIVATE)))
                if (!type.isMappedSuperclass) {
                    add(produceFunction(withBase = false, resolveImmediately = false))
                    add(produceFunction(withBase = true, resolveImmediately = false))
                    add(produceFunction(withBase = false, resolveImmediately = true))
                    add(produceFunction(withBase = true, resolveImmediately = true))
                    add(implementor())
                    add(impl())
                    add(draftImpl())
                }
            },
        )
    }

    private fun slotField(prop: JimmerImmutableDraftPropPlan): LsiField {
        return LsiField(
            name = prop.slotName,
            type = INT_TYPE,
            modifiers = PUBLIC_STATIC_FINAL,
            initializer = draftCode {
                if (prop.runtimeOwnerTypeId == type.typeId) {
                    text(prop.slotIndex.toString())
                } else {
                    type(prop.runtimeOwnerTypeId.producerType())
                    text(".${prop.slotName}")
                }
            },
        )
    }

    private fun runtimeTypeField(): LsiField {
        return LsiField(
            name = "TYPE",
            type = RUNTIME_IMMUTABLE_TYPE,
            modifiers = PUBLIC_STATIC_FINAL,
            initializer = runtimeTypeInitializer(),
        )
    }

    private fun runtimeTypeInitializer(): LsiCodeBlock {
        return draftCode {
            type(RUNTIME_IMMUTABLE_TYPE)
            line()
            indent {
                text(".newBuilder(\n")
                indent {
                    string(currentVersion())
                    text(",\n")
                    type(modelType)
                    text(".class,\n")
                    addRuntimeSuperTypes()
                    if (type.isMappedSuperclass) {
                        text("null\n")
                    } else {
                        text("(ctx, base) -> new ")
                        type(draftImplType)
                        text("(ctx, (")
                        type(modelType)
                        text(")base)\n")
                    }
                }
                text(")\n")
                if (!type.isMappedSuperclass) {
                    type.runtimeRedefinedPropIds.forEach { propId ->
                        val prop = type.propsById.getValue(propId)
                        text(".redefine(")
                        string(prop.name)
                        text(", ${prop.slotName})\n")
                    }
                }
                type.runtimeDeclaredPropIds.forEach { propId ->
                    addRuntimeProp(type.propsById.getValue(propId))
                }
                text(".build()")
            }
        }
    }

    private fun LsiCodeBuilder.addRuntimeSuperTypes() {
        when (type.directSuperTypes.size) {
            0 -> {
                type(COLLECTIONS_TYPE)
                text(".emptyList(),\n")
            }
            1 -> {
                type(COLLECTIONS_TYPE)
                text(".singleton(")
                type(type.directSuperTypes.single().declarationId.producerType())
                text(".TYPE),\n")
            }
            else -> {
                type(ARRAYS_TYPE)
                text(".asList(\n")
                indent {
                    type.directSuperTypes.forEachIndexed { index, superType ->
                        if (index != 0) {
                            text(",\n")
                        }
                        type(superType.declarationId.producerType())
                        text(".TYPE")
                    }
                    line()
                }
                text("),\n")
            }
        }
    }

    private fun LsiCodeBuilder.addRuntimeProp(prop: JimmerImmutableDraftPropPlan) {
        val slot = prop.metadataSlotIndex?.let { prop.slotName } ?: "-1"
        when (prop.runtimeProp.kind) {
            ImmutableDraftRuntimePropKind.ID -> {
                text(".id($slot, ")
                string(prop.name)
                text(", ")
                type(prop.runtimeProp.metadataElementType)
                text(".class)\n")
            }
            ImmutableDraftRuntimePropKind.VERSION -> {
                text(".version($slot, ")
                string(prop.name)
                text(")\n")
            }
            ImmutableDraftRuntimePropKind.LOGICAL_DELETED -> {
                text(".logicalDeleted($slot, ")
                string(prop.name)
                text(", ")
                type(prop.runtimeProp.metadataElementType)
                text(".class, ${prop.nullable})\n")
            }
            ImmutableDraftRuntimePropKind.KEY_SCALAR -> {
                text(".key($slot, ")
                string(prop.name)
                text(", ")
                type(prop.runtimeProp.metadataElementType)
                text(".class, ${prop.nullable})\n")
            }
            ImmutableDraftRuntimePropKind.KEY_REFERENCE -> {
                text(".keyReference($slot, ")
                string(prop.name)
                text(", ")
                type(LsiDeclaredType(requireNotNull(prop.runtimeProp.associationAnnotationTypeId)))
                text(".class, ")
                type(prop.runtimeProp.metadataElementType)
                text(".class, ${prop.nullable})\n")
            }
            ImmutableDraftRuntimePropKind.ASSOCIATION -> {
                text(".add($slot, ")
                string(prop.name)
                text(", ")
                type(LsiDeclaredType(requireNotNull(prop.runtimeProp.associationAnnotationTypeId)))
                text(".class, ")
                type(prop.runtimeProp.metadataElementType)
                text(".class, ${prop.nullable})\n")
            }
            ImmutableDraftRuntimePropKind.VALUE -> {
                text(".add($slot, ")
                string(prop.name)
                text(", ")
                type(IMMUTABLE_PROP_CATEGORY_TYPE)
                text(".${prop.runtimeProp.valueCategory.name}, ")
                type(prop.runtimeProp.metadataElementType)
                text(".class, ${prop.nullable})\n")
            }
        }
    }

    private fun produceFunction(
        withBase: Boolean,
        resolveImmediately: Boolean,
    ): LsiMethod {
        return LsiMethod(
            name = "produce",
            modifiers = setOf(LsiModifier.PUBLIC),
            parameters = buildList {
                if (withBase) {
                    add(LsiParameter("base", modelType))
                }
                if (resolveImmediately) {
                    add(LsiParameter("resolveImmediately", BOOLEAN_TYPE))
                }
                add(
                    LsiParameter(
                        "block",
                        draftDeclaredType(DRAFT_CONSUMER_TYPE_ID, draftRawType),
                    )
                )
            },
            returnType = modelType,
            body = draftCode {
                returnValue {
                    text("(")
                    type(modelType)
                    text(")")
                    type(INTERNAL_TYPE)
                    text(".produce(TYPE, ${if (withBase) "base" else "null"}")
                    if (resolveImmediately) {
                        text(", resolveImmediately")
                    }
                    text(", block)")
                }
            },
        )
    }

    private fun implementor(): LsiClass {
        return LsiClass(
            name = IMPLEMENTOR,
            kind = LsiTypeDeclarationKind.CLASS,
            annotations = listOf(generatedByAnnotation(), jsonPropertyOrderAnnotation()),
            modifiers = setOf(
                LsiModifier.PUBLIC,
                LsiModifier.STATIC,
                LsiModifier.ABSTRACT,
            ),
            documentation = "Class, not interface, for free-marker",
            superInterfaces = listOf(modelType, IMMUTABLE_SPI_TYPE),
            members = buildList {
                legacyProps.forEach { prop ->
                    prop.javaDeeperPropIdName?.let { fieldName ->
                        add(
                            LsiField(
                                name = fieldName,
                                type = PROP_ID_TYPE,
                                modifiers = PUBLIC_STATIC_FINAL,
                                initializer = draftCode {
                                    type(producerType)
                                    text(".TYPE.getProp(")
                                    string(prop.name)
                                    text(").getManyToManyViewBaseDeeperProp().getId()")
                                },
                            )
                        )
                    }
                }
                add(implementorGetFunction(byId = true))
                add(implementorGetFunction(byId = false))
                legacyProps.forEach { prop -> addAll(implementorGetterMembers(prop)) }
                add(
                    LsiMethod(
                        name = "__type",
                        modifiers = PUBLIC_FINAL_OVERRIDE,
                        returnType = RUNTIME_IMMUTABLE_TYPE,
                        body = draftCode { returnValue { text("TYPE") } },
                    )
                )
                add(
                    LsiMethod(
                        name = "getDummyPropForJacksonError__",
                        modifiers = PUBLIC_FINAL,
                        returnType = INT_TYPE,
                        body = draftCode {
                            statement {
                                text("throw new ")
                                type(IMMUTABLE_MODULE_REQUIRED_EXCEPTION_TYPE)
                                text("()")
                            }
                        },
                    )
                )
            },
        )
    }

    private fun implementorGetFunction(byId: Boolean): LsiMethod {
        return LsiMethod(
            name = "__get",
            modifiers = PUBLIC_FINAL_OVERRIDE,
            parameters = listOf(
                LsiParameter("prop", if (byId) PROP_ID_TYPE else STRING_TYPE)
            ),
            returnType = OBJECT_TYPE,
            body = draftCode {
                if (byId) {
                    statement { text("int __propIndex = prop.asIndex()") }
                    beginControlFlow { text("switch (__propIndex)") }
                    text("case -1:\n\t\t")
                    returnValue { text("__get(prop.asName())") }
                } else {
                    beginControlFlow { text("switch (prop)") }
                }
                type.propsBySlot.forEach { prop ->
                    addCase(prop, byId)
                    returnValue {
                        if (prop.primitive) {
                            text("(")
                            type(prop.type.boxedForJavaDraft())
                            text(")")
                        }
                        name(prop.sourceGetterName)
                        text("()")
                    }
                }
                statement {
                    text("default: throw new IllegalArgumentException(")
                    string("Illegal property name for \"${type.qualifiedName}\": \"")
                    text(" + prop + ")
                    string("\"")
                    text(")")
                }
                endControlFlow()
            },
        )
    }

    private fun implementorGetterMembers(prop: JimmerImmutableDraftPropPlan): List<LsiMember> {
        return buildList {
            prop.manyToManyBasePropId?.let { basePropId ->
                val baseProp = type.propsById.getValue(basePropId)
                add(
                    LsiMethod(
                        name = prop.sourceGetterName,
                        modifiers = PUBLIC_FINAL_OVERRIDE,
                        returnType = prop.type.withoutJavaDraftTypeAnnotations(),
                        body = draftCode {
                            returnValue {
                                text("new ")
                                type(MANY_TO_MANY_VIEW_LIST_RAW_TYPE)
                                text("<>(${requireNotNull(prop.javaDeeperPropIdName)}, ")
                                name(baseProp.sourceGetterName)
                                text("())")
                            }
                        },
                    )
                )
            }
            if (!prop.isJavaBeanStyle) {
                add(
                    LsiMethod(
                        name = prop.javaBeanGetterName,
                        annotations = prop.annotationPlan.methodAnnotations
                            .map(LsiAnnotation::toJavaDraftPoetAnnotation),
                        modifiers = PUBLIC_FINAL,
                        returnType = prop.type.withoutJavaDraftTypeAnnotations(),
                        body = draftCode {
                            returnValue {
                                name(prop.sourceGetterName)
                                text("()")
                            }
                        },
                    )
                )
            }
        }
    }

    private fun jsonPropertyOrderAnnotation(): LsiAnnotation {
        val values = buildList {
            add("dummyPropForJacksonError__")
            addAll(type.propsBySlot.map(JimmerImmutableDraftPropPlan::name))
        }
        return sourceLsiAnnotation(
            type = JSON_PROPERTY_ORDER_TYPE_ID,
            arguments = listOf(
                LsiSourceAnnotationArgument.Positional(
                    LsiAnnotationValue.ArrayValue(
                        values.map(LsiAnnotationValue::StringValue)
                    )
                )
            ),
        )
    }

    private fun impl(): LsiClass {
        return LsiClass(
            name = IMPL,
            kind = LsiTypeDeclarationKind.CLASS,
            annotations = listOf(generatedByAnnotation()),
            modifiers = setOf(LsiModifier.PRIVATE, LsiModifier.STATIC),
            superClass = implementorType,
            superInterfaces = listOf(CLONEABLE_TYPE, SERIALIZABLE_TYPE),
            members = buildList {
                addAll(implFields())
                implConstructor()?.let(::add)
                legacyProps.forEach { prop -> implGetter(prop)?.let(::add) }
                add(implCloneFunction())
                add(implIsLoadedFunction(byId = true))
                add(implIsLoadedFunction(byId = false))
                add(implIsVisibleFunction(byId = true))
                add(implIsVisibleFunction(byId = false))
                add(implHashCodeFunction(shallow = false))
                add(implHashCodeFunction(shallow = true))
                add(
                    LsiMethod(
                        name = "__hashCode",
                        modifiers = PUBLIC_OVERRIDE,
                        parameters = listOf(LsiParameter("shallow", BOOLEAN_TYPE)),
                        returnType = INT_TYPE,
                        body = draftCode {
                            returnValue { text("shallow ? __shallowHashCode() : hashCode()") }
                        },
                    )
                )
                add(implEqualsFunction(shallow = false))
                add(implEqualsFunction(shallow = true))
                add(
                    LsiMethod(
                        name = "__equals",
                        modifiers = PUBLIC_OVERRIDE,
                        parameters = listOf(
                            LsiParameter("obj", OBJECT_TYPE),
                            LsiParameter("shallow", BOOLEAN_TYPE),
                        ),
                        returnType = BOOLEAN_TYPE,
                        body = draftCode {
                            returnValue { text("shallow ? __shallowEquals(obj) : equals(obj)") }
                        },
                    )
                )
                add(
                    LsiMethod(
                        name = "toString",
                        modifiers = PUBLIC_OVERRIDE,
                        returnType = STRING_TYPE,
                        body = draftCode {
                            returnValue {
                                type(IMMUTABLE_OBJECTS_TYPE)
                                text(".toString(this)")
                            }
                        },
                    )
                )
            },
        )
    }

    private fun implFields(): List<LsiField> {
        return buildList {
            add(
                LsiField(
                    name = VISIBILITY_FIELD,
                    type = VISIBILITY_TYPE,
                    modifiers = setOf(LsiModifier.PRIVATE),
                )
            )
            legacyProps.forEach { prop ->
                prop.valueFieldName?.let { fieldName ->
                    val fieldType = if (prop.list) {
                        draftDeclaredType(NON_SHARED_LIST_TYPE_ID, prop.elementType.boxedForJavaDraft())
                    } else {
                        prop.type.withoutJavaDraftTypeAnnotations()
                    }
                    add(LsiField(name = fieldName, type = fieldType))
                }
                prop.loadedStateFieldName?.let { fieldName ->
                    add(
                        LsiField(
                            name = fieldName,
                            type = BOOLEAN_TYPE,
                            initializer = draftCode { text("false") },
                        )
                    )
                }
            }
        }
    }

    private fun implConstructor(): LsiConstructor? {
        if (!type.requiresVisibilityState) {
            return null
        }
        return LsiConstructor(
            body = draftCode {
                statement {
                    name(VISIBILITY_FIELD)
                    text(" = ")
                    type(VISIBILITY_TYPE)
                    text(".of(${type.propsBySlot.size})")
                }
                legacyProps.filterNot { prop -> prop.valueState.hasValue }.forEach { prop ->
                    statement {
                        name(VISIBILITY_FIELD)
                        text(".show(${prop.slotName}, false)")
                    }
                }
            },
        )
    }

    private fun implGetter(prop: JimmerImmutableDraftPropPlan): LsiMethod? {
        if (prop.languageFormula || prop.manyToManyBasePropId != null) {
            return null
        }
        return LsiMethod(
            name = prop.sourceGetterName,
            annotations = buildList {
                add(JAVA_OVERRIDE_ANNOTATION)
                if (!prop.isJavaBeanStyle) {
                    add(JSON_IGNORE_ANNOTATION)
                }
                if (prop.nullable) {
                    add(NULLABLE_ANNOTATION)
                }
            },
            modifiers = PUBLIC_OVERRIDE,
            returnType = prop.type.withoutJavaDraftTypeAnnotations(),
            body = implGetterBody(prop),
        )
    }

    private fun implGetterBody(prop: JimmerImmutableDraftPropPlan): LsiCodeBlock {
        val basePropId = prop.idViewBasePropId
        if (basePropId != null) {
            val baseProp = type.propsById.getValue(basePropId)
            val targetType = requireNotNull(baseProp.targetTypeId).let(schema.typesById::getValue)
            val targetIdProp = targetType.propsById.getValue(requireNotNull(baseProp.targetIdPropId))
            return draftCode {
                if (baseProp.list) {
                    returnValue {
                        text("new ")
                        type(ID_VIEW_LIST_RAW_TYPE)
                        text("<>(")
                        type(targetType.typeId.producerType())
                        text(".TYPE, ${baseProp.sourceGetterName}())")
                    }
                } else {
                    statement {
                        type(baseProp.elementType.withoutJavaDraftTypeAnnotations())
                        text(" __target = ${baseProp.sourceGetterName}()")
                    }
                    returnValue {
                        if (prop.nullable) {
                            text("__target != null ? __target.${targetIdProp.sourceGetterName}() : null")
                        } else {
                            text("__target.${targetIdProp.sourceGetterName}()")
                        }
                    }
                }
            }
        }
        return draftCode {
            val loadedState = prop.loadedStateFieldName
            beginControlFlow {
                if (loadedState != null) {
                    text("if (!$loadedState)")
                } else {
                    text("if (${requireNotNull(prop.valueFieldName)} == null)")
                }
            }
            statement {
                text("throw new ")
                type(UNLOADED_EXCEPTION_TYPE)
                text("(")
                type(modelType)
                text(".class, ")
                string(prop.name)
                text(")")
            }
            endControlFlow()
            returnValue { name(requireNotNull(prop.valueFieldName)) }
        }
    }

    private fun descriptionAnnotation(documentation: String): LsiAnnotation {
        return sourceLsiAnnotation(
            type = DESCRIPTION_TYPE_ID,
            arguments = listOf(
                LsiSourceAnnotationArgument.Positional(
                    LsiAnnotationValue.StringValue(documentation)
                )
            ),
        )
    }

    private fun implCloneFunction(): LsiMethod {
        return LsiMethod(
            name = "clone",
            modifiers = PUBLIC_OVERRIDE,
            returnType = implType,
            body = draftCode {
                beginControlFlow { text("try") }
                statement {
                    type(implType)
                    text(" copy = (")
                    type(implType)
                    text(") super.clone()")
                }
                statement {
                    type(VISIBILITY_TYPE)
                    text(" originalVisibility = this.$VISIBILITY_FIELD")
                }
                beginControlFlow { text("if (originalVisibility != null)") }
                statement {
                    type(VISIBILITY_TYPE)
                    text(" newVisibility = ")
                    type(VISIBILITY_TYPE)
                    text(".of(${type.propsBySlot.size})")
                }
                beginControlFlow {
                    text("for (int propId = 0; propId < ${type.propsBySlot.size}; propId++)")
                }
                statement { text("newVisibility.show(propId, originalVisibility.visible(propId))") }
                endControlFlow()
                statement { text("copy.$VISIBILITY_FIELD = newVisibility") }
                nextControlFlow { text("else") }
                statement { text("copy.$VISIBILITY_FIELD = null") }
                endControlFlow()
                returnValue { text("copy") }
                nextControlFlow {
                    text("catch(")
                    type(CLONE_NOT_SUPPORTED_EXCEPTION_TYPE)
                    text(" ex)")
                }
                statement {
                    text("throw new AssertionError(ex)")
                }
                endControlFlow()
            },
        )
    }

    private fun implIsLoadedFunction(byId: Boolean): LsiMethod {
        return LsiMethod(
            name = "__isLoaded",
            modifiers = PUBLIC_OVERRIDE,
            parameters = listOf(
                LsiParameter("prop", if (byId) PROP_ID_TYPE else STRING_TYPE)
            ),
            returnType = BOOLEAN_TYPE,
            body = draftCode {
                if (byId) {
                    statement { text("int __propIndex = prop.asIndex()") }
                    beginControlFlow { text("switch (__propIndex)") }
                    text("case -1:\n\t\t")
                    returnValue { text("__isLoaded(prop.asName())") }
                } else {
                    beginControlFlow { text("switch (prop)") }
                }
                type.propsBySlot.forEach { prop ->
                    addCase(prop, byId)
                    returnValue { add(loadedExpression(prop)) }
                }
                addIllegalPropertyCase(byId = null)
                endControlFlow()
            },
        )
    }

    private fun loadedExpression(prop: JimmerImmutableDraftPropPlan): LsiCodeBlock {
        prop.idViewBasePropId?.let { basePropId ->
            val baseProp = type.propsById.getValue(basePropId)
            val targetType = requireNotNull(baseProp.targetTypeId).let(schema.typesById::getValue)
            val targetIdProp = targetType.propsById.getValue(requireNotNull(baseProp.targetIdPropId))
            return draftCode {
                text("__isLoaded(")
                type(PROP_ID_TYPE)
                text(".byIndex(${baseProp.slotName})) && ")
                if (baseProp.list) {
                    text("${baseProp.sourceGetterName}().stream().allMatch(__each -> ((")
                    type(IMMUTABLE_SPI_TYPE)
                    text(")__each).__isLoaded(")
                    type(PROP_ID_TYPE)
                    text(".byIndex(")
                    type(targetType.typeId.producerType())
                    text(".${targetIdProp.slotName})))")
                } else {
                    text("(${baseProp.sourceGetterName}() == null || ((")
                    type(IMMUTABLE_SPI_TYPE)
                    text(")${baseProp.sourceGetterName}()).__isLoaded(")
                    type(PROP_ID_TYPE)
                    text(".byIndex(")
                    type(targetType.typeId.producerType())
                    text(".${targetIdProp.slotName})))")
                }
            }
        }
        prop.manyToManyBasePropId?.let { basePropId ->
            val baseProp = type.propsById.getValue(basePropId)
            return draftCode {
                text("__isLoaded(")
                type(PROP_ID_TYPE)
                text(".byIndex(${baseProp.slotName})) && ${baseProp.sourceGetterName}()")
                text(".stream().allMatch(__each -> ((")
                type(IMMUTABLE_SPI_TYPE)
                text(")__each).__isLoaded(${requireNotNull(prop.javaDeeperPropIdName)}))")
            }
        }
        if (prop.languageFormula) {
            if (prop.formulaDependencyPaths.isEmpty()) {
                return draftCode { text("true") }
            }
            return draftCode {
                prop.formulaDependencyPaths.forEachIndexed { index, path ->
                    if (index != 0) {
                        text(" && ")
                    }
                    if (path.size == 1) {
                        val dependency = propPlan(path.single())
                        text("__isLoaded(")
                        type(PROP_ID_TYPE)
                        text(".byIndex(${dependency.slotName}))")
                    } else {
                        type(IMMUTABLE_OBJECTS_TYPE)
                        text(".isLoadedChain(this")
                        path.forEach { dependencyId ->
                            val dependency = propPlan(dependencyId)
                            text(", ")
                            type(PROP_ID_TYPE)
                            text(".byIndex(")
                            type(dependency.runtimeOwnerTypeId.producerType())
                            text(".${dependency.slotName})")
                        }
                        text(")")
                    }
                }
            }
        }
        prop.loadedStateFieldName?.let { return draftCode { name(it) } }
        return draftCode { text("${requireNotNull(prop.valueFieldName)} != null") }
    }

    private fun implIsVisibleFunction(byId: Boolean): LsiMethod {
        return LsiMethod(
            name = "__isVisible",
            modifiers = PUBLIC_OVERRIDE,
            parameters = listOf(
                LsiParameter("prop", if (byId) PROP_ID_TYPE else STRING_TYPE)
            ),
            returnType = BOOLEAN_TYPE,
            body = draftCode {
                beginControlFlow { text("if ($VISIBILITY_FIELD == null)") }
                returnValue { text("true") }
                endControlFlow()
                if (byId) {
                    statement { text("int __propIndex = prop.asIndex()") }
                    beginControlFlow { text("switch (__propIndex)") }
                    text("case -1:\n\t\t")
                    returnValue { text("__isVisible(prop.asName())") }
                } else {
                    beginControlFlow { text("switch (prop)") }
                }
                type.propsBySlot.forEach { prop ->
                    addCase(prop, byId)
                    returnValue { text("$VISIBILITY_FIELD.visible(${prop.slotName})") }
                }
                statement { text("default: return true") }
                endControlFlow()
            },
        )
    }

    private fun implHashCodeFunction(shallow: Boolean): LsiMethod {
        return LsiMethod(
            name = if (shallow) "__shallowHashCode" else "hashCode",
            modifiers = if (shallow) {
                setOf(LsiModifier.PRIVATE)
            } else {
                PUBLIC_OVERRIDE
            },
            returnType = INT_TYPE,
            body = draftCode {
                statement {
                    text("int hash = $VISIBILITY_FIELD != null ? $VISIBILITY_FIELD.hashCode() : 0")
                }
                legacyProps.filter { prop -> prop.valueState.hasValue }.forEach { prop ->
                    addHashCode(prop, shallow)
                }
                returnValue { text("hash") }
            },
        )
    }

    private fun LsiCodeBuilder.addHashCode(
        prop: JimmerImmutableDraftPropPlan,
        shallow: Boolean,
    ) {
        val valueField = requireNotNull(prop.valueFieldName)
        if (prop.primitive) {
            beginControlFlow { text("if (${requireNotNull(prop.loadedStateFieldName)})") }
            statement {
                text("hash = 31 * hash + ")
                type(prop.type.boxedForJavaDraft())
                text(".hashCode($valueField)")
            }
            if (!shallow && type.idPropId == prop.propId) {
                text("// If entity-id is loaded, return directly\n")
                returnValue { text("hash") }
            }
            endControlFlow()
            return
        }
        if (shallow) {
            beginControlFlow {
                val loadedState = prop.loadedStateFieldName
                if (loadedState != null) {
                    text("if ($loadedState)")
                } else {
                    text("if ($valueField != null)")
                }
            }
            statement {
                text("hash = 31 * hash + ")
                type(SYSTEM_TYPE)
                text(".identityHashCode($valueField)")
            }
            endControlFlow()
            return
        }
        beginControlFlow {
            val loadedState = prop.loadedStateFieldName
            if (loadedState != null) {
                text("if ($loadedState && $valueField != null)")
            } else {
                text("if ($valueField != null)")
            }
        }
        statement { text("hash = 31 * hash + $valueField.hashCode()") }
        if (type.idPropId == prop.propId) {
            text("// If entity-id is loaded, return directly\n")
            returnValue { text("hash") }
        }
        endControlFlow()
    }

    private fun implEqualsFunction(shallow: Boolean): LsiMethod {
        return LsiMethod(
            name = if (shallow) "__shallowEquals" else "equals",
            modifiers = if (shallow) {
                setOf(LsiModifier.PRIVATE)
            } else {
                PUBLIC_OVERRIDE
            },
            parameters = listOf(LsiParameter("obj", OBJECT_TYPE)),
            returnType = BOOLEAN_TYPE,
            body = draftCode {
                beginControlFlow {
                    text("if (obj == null || !(obj instanceof ")
                    type(implementorType)
                    text("))")
                }
                returnValue { text("false") }
                endControlFlow()
                statement {
                    type(implementorType)
                    text(" __other = (")
                    type(implementorType)
                    text(")obj")
                }
                legacyProps.forEach { prop -> addEquals(prop, shallow) }
                returnValue { text("true") }
            },
        )
    }

    private fun LsiCodeBuilder.addEquals(
        prop: JimmerImmutableDraftPropPlan,
        shallow: Boolean,
    ) {
        beginControlFlow {
            text("if (__isVisible(")
            type(PROP_ID_TYPE)
            text(".byIndex(${prop.slotName})) != __other.__isVisible(")
            type(PROP_ID_TYPE)
            text(".byIndex(${prop.slotName})))")
        }
        returnValue { text("false") }
        endControlFlow()
        if (!prop.valueState.hasValue) {
            return
        }
        val valueField = requireNotNull(prop.valueFieldName)
        val loadedName = prop.forcedLoadedStateName
        statement {
            val loadedState = prop.loadedStateFieldName
            if (loadedState != null) {
                text("boolean $loadedName = this.$loadedState")
            } else {
                text("boolean $loadedName = $valueField != null")
            }
        }
        beginControlFlow {
            text("if ($loadedName != __other.__isLoaded(")
            type(PROP_ID_TYPE)
            text(".byIndex(${prop.slotName})))")
        }
        returnValue { text("false") }
        endControlFlow()
        if (shallow || prop.primitive) {
            if (!shallow && type.idPropId == prop.propId) {
                beginControlFlow { text("if ($loadedName)") }
                text("// If entity-id is loaded, return directly\n")
                returnValue { text("$valueField == __other.${prop.sourceGetterName}()") }
                endControlFlow()
            } else {
                beginControlFlow {
                    text("if ($loadedName && $valueField != __other.${prop.sourceGetterName}())")
                }
                returnValue { text("false") }
                endControlFlow()
            }
            return
        }
        if (type.idPropId == prop.propId) {
            beginControlFlow { text("if ($loadedName)") }
            text("// If entity-id is loaded, return directly\n")
            returnValue {
                type(OBJECTS_TYPE)
                text(".equals($valueField, __other.${prop.sourceGetterName}())")
            }
            endControlFlow()
            return
        }
        beginControlFlow {
            text("if ($loadedName && !")
            type(OBJECTS_TYPE)
            text(".equals($valueField, __other.${prop.sourceGetterName}()))")
        }
        returnValue { text("false") }
        endControlFlow()
    }

    private fun draftImpl(): LsiClass {
        return LsiClass(
            name = DRAFT_IMPL,
            kind = LsiTypeDeclarationKind.CLASS,
            annotations = listOf(generatedByAnnotation()),
            modifiers = setOf(LsiModifier.PRIVATE, LsiModifier.STATIC),
            superClass = implementorType,
            superInterfaces = listOf(DRAFT_SPI_TYPE, draftRawType),
            members = buildList {
                addAll(draftImplFields())
                addAll(ImmutableDraftJavaValidationPoet.staticMembers(type))
                add(draftImplConstructor())
                addAll(draftImplReadonlyFunctions())
                legacyProps.forEach { prop ->
                    draftImplGetter(prop)?.let(::add)
                    draftImplCreator(prop)?.let(::add)
                    draftImplSetter(prop)?.let(::add)
                    addAll(associatedIdMembers(prop, withImplementation = true))
                    if (prop.referenceMutationSupported) {
                        add(draftReferenceMutationFunction(prop, withBase = false, withImplementation = true))
                        add(draftReferenceMutationFunction(prop, withBase = true, withImplementation = true))
                    }
                }
                add(draftImplSetFunction(byId = true))
                add(draftImplSetFunction(byId = false))
                add(draftImplShowFunction(byId = true))
                add(draftImplShowFunction(byId = false))
                add(draftImplUnloadFunction(byId = true))
                add(draftImplUnloadFunction(byId = false))
                add(
                    LsiMethod(
                        name = "__draftContext",
                        modifiers = PUBLIC_OVERRIDE,
                        returnType = DRAFT_CONTEXT_TYPE,
                        body = draftCode { returnValue { name(DRAFT_CONTEXT_FIELD) } },
                    )
                )
                add(draftImplResolveFunction())
                add(
                    LsiMethod(
                        name = "__isResolved",
                        modifiers = PUBLIC_OVERRIDE,
                        returnType = BOOLEAN_TYPE,
                        body = draftCode { returnValue { text("$DRAFT_RESOLVED_FIELD != null") } },
                    )
                )
                add(draftImplModifiedFunction())
            },
        )
    }

    private fun draftImplFields(): List<LsiField> {
        return listOf(
            LsiField(
                name = DRAFT_CONTEXT_FIELD,
                type = DRAFT_CONTEXT_TYPE,
                modifiers = setOf(LsiModifier.PRIVATE),
            ),
            LsiField(
                name = DRAFT_BASE_FIELD,
                type = implType,
                modifiers = setOf(LsiModifier.PRIVATE),
            ),
            LsiField(
                name = DRAFT_MODIFIED_FIELD,
                type = implType,
                modifiers = setOf(LsiModifier.PRIVATE),
            ),
            LsiField(
                name = DRAFT_RESOLVING_FIELD,
                type = BOOLEAN_TYPE,
                modifiers = setOf(LsiModifier.PRIVATE),
            ),
            LsiField(
                name = DRAFT_RESOLVED_FIELD,
                type = modelType,
                modifiers = setOf(LsiModifier.PRIVATE),
            ),
        )
    }

    private fun draftImplConstructor(): LsiConstructor {
        return LsiConstructor(
            parameters = listOf(
                LsiParameter("ctx", DRAFT_CONTEXT_TYPE),
                LsiParameter("base", modelType),
            ),
            body = draftCode {
                statement { text("$DRAFT_CONTEXT_FIELD = ctx") }
                beginControlFlow { text("if (base != null)") }
                statement {
                    text("$DRAFT_BASE_FIELD = (")
                    type(implType)
                    text(")base")
                }
                endControlFlow()
                beginControlFlow { text("else") }
                statement {
                    text("$DRAFT_MODIFIED_FIELD = new ")
                    type(implType)
                    text("()")
                }
                endControlFlow()
            },
        )
    }

    private fun draftImplReadonlyFunctions(): List<LsiMethod> {
        return listOf(
            readonlyDelegate("__isLoaded", PROP_ID_TYPE, BOOLEAN_TYPE, "prop"),
            readonlyDelegate("__isLoaded", STRING_TYPE, BOOLEAN_TYPE, "prop"),
            readonlyDelegate("__isVisible", PROP_ID_TYPE, BOOLEAN_TYPE, "prop"),
            readonlyDelegate("__isVisible", STRING_TYPE, BOOLEAN_TYPE, "prop"),
            LsiMethod(
                name = "hashCode",
                modifiers = PUBLIC_OVERRIDE,
                returnType = INT_TYPE,
                body = draftCode {
                    returnValue {
                        add(unmodifiedExpression)
                        text(".hashCode()")
                    }
                },
            ),
            LsiMethod(
                name = "__hashCode",
                modifiers = PUBLIC_OVERRIDE,
                parameters = listOf(LsiParameter("shallow", BOOLEAN_TYPE)),
                returnType = INT_TYPE,
                body = draftCode {
                    returnValue {
                        add(unmodifiedExpression)
                        text(".__hashCode(shallow)")
                    }
                },
            ),
            LsiMethod(
                name = "equals",
                modifiers = PUBLIC_OVERRIDE,
                parameters = listOf(LsiParameter("obj", OBJECT_TYPE)),
                returnType = BOOLEAN_TYPE,
                body = draftCode {
                    returnValue {
                        add(unmodifiedExpression)
                        text(".equals(obj)")
                    }
                },
            ),
            LsiMethod(
                name = "__equals",
                modifiers = PUBLIC_OVERRIDE,
                parameters = listOf(
                    LsiParameter("obj", OBJECT_TYPE),
                    LsiParameter("shallow", BOOLEAN_TYPE),
                ),
                returnType = BOOLEAN_TYPE,
                body = draftCode {
                    returnValue {
                        add(unmodifiedExpression)
                        text(".__equals(obj, shallow)")
                    }
                },
            ),
            LsiMethod(
                name = "toString",
                modifiers = PUBLIC_OVERRIDE,
                returnType = STRING_TYPE,
                body = draftCode {
                    returnValue {
                        type(IMMUTABLE_OBJECTS_TYPE)
                        text(".toString(this)")
                    }
                },
            ),
        )
    }

    private fun readonlyDelegate(
        functionName: String,
        parameterType: LsiType,
        returnType: LsiType,
        parameterName: String,
    ): LsiMethod {
        return LsiMethod(
            name = functionName,
            modifiers = PUBLIC_OVERRIDE,
            parameters = listOf(LsiParameter(parameterName, parameterType)),
            returnType = returnType,
            body = draftCode {
                returnValue {
                    add(unmodifiedExpression)
                    text(".$functionName($parameterName)")
                }
            },
        )
    }

    private fun draftImplGetter(prop: JimmerImmutableDraftPropPlan): LsiMethod? {
        if (prop.manyToManyBasePropId != null) {
            return null
        }
        return LsiMethod(
            name = prop.sourceGetterName,
            annotations = buildList {
                add(JAVA_OVERRIDE_ANNOTATION)
                if (!prop.isJavaBeanStyle) {
                    add(JSON_IGNORE_ANNOTATION)
                }
                if (prop.nullable) {
                    add(NULLABLE_ANNOTATION)
                }
            },
            modifiers = PUBLIC_OVERRIDE,
            returnType = prop.draftType(autoCreate = false),
            body = draftImplGetterBody(prop),
        )
    }

    private fun draftImplGetterBody(prop: JimmerImmutableDraftPropPlan): LsiCodeBlock {
        prop.idViewBasePropId?.let { basePropId ->
            val baseProp = type.propsById.getValue(basePropId)
            val targetType = requireNotNull(baseProp.targetTypeId).let(schema.typesById::getValue)
            val targetIdProp = targetType.propsById.getValue(requireNotNull(baseProp.targetIdPropId))
            return draftCode {
                if (baseProp.list) {
                    statement {
                        type(draftDeclaredType(LIST_TYPE_ID, targetIdProp.type.boxedForJavaDraft()))
                        text(" __ids = new ")
                        type(ARRAY_LIST_RAW_TYPE)
                        text("<>(${baseProp.sourceGetterName}().size())")
                    }
                    beginControlFlow {
                        text("for (")
                        type(baseProp.elementType.withoutJavaDraftTypeAnnotations())
                        text(" __target : ${baseProp.sourceGetterName}())")
                    }
                    statement { text("__ids.add(__target.${targetIdProp.sourceGetterName}())") }
                    endControlFlow()
                    returnValue { text("__ids") }
                } else {
                    statement {
                        type(baseProp.elementType.withoutJavaDraftTypeAnnotations())
                        text(" __target = ${baseProp.sourceGetterName}()")
                    }
                    returnValue {
                        if (prop.nullable) {
                            text("__target != null ? __target.${targetIdProp.sourceGetterName}() : null")
                        } else {
                            text("__target.${targetIdProp.sourceGetterName}()")
                        }
                    }
                }
            }
        }
        return draftCode {
            when {
                prop.list -> {
                    returnValue {
                        text("$DRAFT_CONTEXT_FIELD.toDraftList(")
                        add(unmodifiedExpression)
                        text(".${prop.sourceGetterName}(), ")
                        type(prop.elementType.withoutJavaDraftTypeAnnotations())
                        text(".class, ${prop.immutableReference})")
                    }
                }
                prop.immutableReference -> {
                    returnValue {
                        text("$DRAFT_CONTEXT_FIELD.toDraftObject(")
                        add(unmodifiedExpression)
                        text(".${prop.sourceGetterName}())")
                    }
                }
                else -> {
                    returnValue {
                        add(unmodifiedExpression)
                        text(".${prop.sourceGetterName}()")
                    }
                }
            }
        }
    }

    private fun draftImplCreator(prop: JimmerImmutableDraftPropPlan): LsiMethod? {
        if (!prop.autoCreateSupported) {
            return null
        }
        val realProp = prop.idViewBasePropId?.let(type.propsById::getValue) ?: prop
        return LsiMethod(
            name = prop.sourceGetterName,
            modifiers = PUBLIC_OVERRIDE,
            parameters = listOf(LsiParameter("autoCreate", BOOLEAN_TYPE)),
            returnType = prop.draftType(autoCreate = true),
            body = draftCode {
                beginControlFlow {
                    text("if (autoCreate && ")
                    if (prop.nullable) {
                        text("(!__isLoaded(")
                        type(PROP_ID_TYPE)
                        text(".byIndex(${realProp.slotName})) || ${realProp.sourceGetterName}() == null))")
                    } else {
                        text("!__isLoaded(")
                        type(PROP_ID_TYPE)
                        text(".byIndex(${realProp.slotName})))")
                    }
                }
                statement {
                    if (prop.list) {
                        text("${realProp.javaSetterName}(new ")
                        type(ARRAY_LIST_RAW_TYPE)
                        text("<>())")
                    } else {
                        text("${realProp.javaSetterName}(")
                        type(realProp.draftElementType())
                        text(".$.produce(null, null))")
                    }
                }
                endControlFlow()
                if (prop.list) {
                    if (realProp.propId != prop.propId) {
                        val targetType = requireNotNull(realProp.targetTypeId).let(schema.typesById::getValue)
                        returnValue {
                            text("new ")
                            type(MUTABLE_ID_VIEW_LIST_RAW_TYPE)
                            text("<>(")
                            type(targetType.typeId.producerType())
                            text(".TYPE, ${realProp.sourceGetterName}())")
                        }
                    } else {
                        returnValue {
                            text("$DRAFT_CONTEXT_FIELD.toDraftList(")
                            add(unmodifiedExpression)
                            text(".${prop.sourceGetterName}(), ")
                            type(prop.elementType.withoutJavaDraftTypeAnnotations())
                            text(".class, ${prop.immutableReference})")
                        }
                    }
                } else {
                    returnValue {
                        text("$DRAFT_CONTEXT_FIELD.toDraftObject(")
                        add(unmodifiedExpression)
                        text(".${prop.sourceGetterName}())")
                    }
                }
            },
        )
    }

    private fun draftImplSetter(prop: JimmerImmutableDraftPropPlan): LsiMethod? {
        if (!prop.writable) {
            return null
        }
        return LsiMethod(
            name = prop.javaSetterName,
            modifiers = PUBLIC_OVERRIDE,
            parameters = listOf(
                LsiParameter(prop.codegenName, prop.type.withoutJavaDraftTypeAnnotations())
            ),
            returnType = draftRawType,
            body = draftCode {
                addFrozenCheck()
                val basePropId = prop.idViewBasePropId
                if (basePropId != null) {
                    addIdViewSetter(prop, type.propsById.getValue(basePropId))
                } else {
                    add(ImmutableDraftJavaValidationPoet.validationCode(type, prop, prop.codegenName))
                    statement {
                        type(implType)
                        text(" __tmpModified = $DRAFT_MODIFIED_FIELD()")
                    }
                    statement {
                        text("__tmpModified.${requireNotNull(prop.valueFieldName)} = ")
                        if (prop.list) {
                            type(NON_SHARED_LIST_RAW_TYPE)
                            text(".of(__tmpModified.${prop.valueFieldName}, ${prop.codegenName})")
                        } else {
                            name(prop.codegenName)
                        }
                    }
                    prop.loadedStateFieldName?.let { loadedState ->
                        statement { text("__tmpModified.$loadedState = true") }
                    }
                }
                returnValue { text("this") }
            },
        )
    }

    private fun LsiCodeBuilder.addIdViewSetter(
        prop: JimmerImmutableDraftPropPlan,
        baseProp: JimmerImmutableDraftPropPlan,
    ) {
        val targetType = requireNotNull(baseProp.targetTypeId).let(schema.typesById::getValue)
        val targetIdProp = targetType.propsById.getValue(requireNotNull(baseProp.targetIdPropId))
        if (!prop.primitive) {
            beginControlFlow { text("if (${prop.codegenName} != null)") }
        }
        if (prop.list) {
            statement {
                type(
                    draftDeclaredType(
                        LIST_TYPE_ID,
                        baseProp.elementType.withoutJavaDraftTypeAnnotations(),
                    )
                )
                text(" __targets = new ")
                type(ARRAY_LIST_RAW_TYPE)
                text("<>(${prop.codegenName}.size())")
            }
            beginControlFlow {
                text("for (")
                type(targetIdProp.type.withoutJavaDraftTypeAnnotations())
                text(" __id : ${prop.codegenName})")
            }
            statement {
                text("__targets.add(")
                type(IMMUTABLE_OBJECTS_TYPE)
                text(".makeIdOnly(")
                type(baseProp.elementType.withoutJavaDraftTypeAnnotations())
                text(".class, __id))")
            }
            endControlFlow()
            statement { text("${baseProp.javaSetterName}(__targets)") }
        } else {
            statement {
                text("${baseProp.javaSetterName}(")
                type(IMMUTABLE_OBJECTS_TYPE)
                text(".makeIdOnly(")
                type(baseProp.elementType.withoutJavaDraftTypeAnnotations())
                text(".class, ${prop.codegenName}))")
            }
        }
        if (!prop.primitive) {
            nextControlFlow { text("else") }
            statement {
                text("${baseProp.javaSetterName}(")
                if (prop.list) {
                    type(COLLECTIONS_TYPE)
                    text(".emptyList()")
                } else {
                    text("null")
                }
                text(")")
            }
            endControlFlow()
        }
    }

    private fun LsiCodeBuilder.addFrozenCheck() {
        beginControlFlow { text("if ($DRAFT_RESOLVED_FIELD != null)") }
        statement {
            text("throw new ")
            type(ILLEGAL_STATE_EXCEPTION_TYPE)
            text("(")
            string(FROZEN_MESSAGE)
            text(")")
        }
        endControlFlow()
    }

    private fun draftImplSetFunction(byId: Boolean): LsiMethod {
        return LsiMethod(
            name = "__set",
            annotations = listOf(SUPPRESS_ALL_ANNOTATION),
            modifiers = PUBLIC_OVERRIDE,
            parameters = listOf(
                LsiParameter("prop", if (byId) PROP_ID_TYPE else STRING_TYPE),
                LsiParameter("value", OBJECT_TYPE),
            ),
            body = draftCode {
                if (byId) {
                    statement { text("int __propIndex = prop.asIndex()") }
                    beginControlFlow { text("switch (__propIndex)") }
                    text("case -1:\n\t\t")
                    statement { text("__set(prop.asName(), value)") }
                    returnVoid()
                } else {
                    beginControlFlow { text("switch (prop)") }
                }
                type.propsBySlot.forEach { prop ->
                    addCase(prop, byId)
                    addDynamicSetCase(prop)
                }
                addIllegalPropertyCase(byId)
                endControlFlow()
            },
        )
    }

    private fun LsiCodeBuilder.addDynamicSetCase(prop: JimmerImmutableDraftPropPlan) {
        val castType = prop.type.boxedForJavaDraft()
        when {
            prop.isDiscriminator -> {
                statement {
                    type(implType)
                    text(" __tmpModified = $DRAFT_MODIFIED_FIELD()")
                }
                statement {
                    text("__tmpModified.${requireNotNull(prop.valueFieldName)} = (")
                    type(castType)
                    text(")value")
                }
                prop.loadedStateFieldName?.let { loadedState ->
                    statement { text("__tmpModified.$loadedState = true") }
                }
                statement { text("break") }
            }
            prop.languageFormula || prop.manyToManyBasePropId != null -> {
                statement { text("break") }
            }
            prop.primitive -> {
                statement {
                    text("if (value == null) throw new ")
                    type(ILLEGAL_ARGUMENT_EXCEPTION_TYPE)
                    text("(")
                    string(
                        "'${prop.name}' cannot be null, if you want to set null, please use any annotation " +
                            "whose simple name is \"Nullable\" to decorate the property"
                    )
                    text(");\n${prop.javaSetterName}((")
                    type(castType)
                    text(")value);\nbreak")
                }
            }
            prop.writable -> {
                statement {
                    text("${prop.javaSetterName}((")
                    type(castType)
                    text(")value);break")
                }
            }
            else -> statement { text("break") }
        }
    }

    private fun draftImplShowFunction(byId: Boolean): LsiMethod {
        return LsiMethod(
            name = "__show",
            modifiers = PUBLIC_OVERRIDE,
            parameters = listOf(
                LsiParameter("prop", if (byId) PROP_ID_TYPE else STRING_TYPE),
                LsiParameter("visible", BOOLEAN_TYPE),
            ),
            body = draftCode {
                addFrozenCheck()
                statement {
                    type(VISIBILITY_TYPE)
                    text(" __visibility = ")
                    add(unmodifiedExpression)
                    text(".$VISIBILITY_FIELD")
                }
                beginControlFlow { text("if (__visibility == null)") }
                beginControlFlow { text("if (visible)") }
                returnVoid()
                endControlFlow()
                statement {
                    text("$DRAFT_MODIFIED_FIELD().$VISIBILITY_FIELD = __visibility = ")
                    type(VISIBILITY_TYPE)
                    text(".of(${type.propsBySlot.size})")
                }
                endControlFlow()
                if (byId) {
                    statement { text("int __propIndex = prop.asIndex()") }
                    beginControlFlow { text("switch (__propIndex)") }
                    text("case -1:\n\t\t")
                    statement { text("__show(prop.asName(), visible)") }
                    returnVoid()
                } else {
                    beginControlFlow { text("switch (prop)") }
                }
                type.propsBySlot.forEach { prop ->
                    addCase(prop, byId)
                    statement { text("__visibility.show(${prop.slotName}, visible);break") }
                }
                statement {
                    text("default: throw new IllegalArgumentException(\n")
                    indent {
                        string(
                            "Illegal property ${if (byId) "id" else "name"} " +
                                "for \"${type.qualifiedName}\": \""
                        )
                        text(" + \nprop + \n")
                        string("\",it does not exists")
                        line()
                    }
                    text(")")
                }
                endControlFlow()
            },
        )
    }

    private fun draftImplUnloadFunction(byId: Boolean): LsiMethod {
        return LsiMethod(
            name = "__unload",
            modifiers = PUBLIC_OVERRIDE,
            parameters = listOf(
                LsiParameter("prop", if (byId) PROP_ID_TYPE else STRING_TYPE)
            ),
            body = draftCode {
                addFrozenCheck()
                if (byId) {
                    statement { text("int __propIndex = prop.asIndex()") }
                    beginControlFlow { text("switch (__propIndex)") }
                    text("case -1:\n\t\t")
                    statement { text("__unload(prop.asName())") }
                    returnVoid()
                } else {
                    beginControlFlow { text("switch (prop)") }
                }
                type.propsBySlot.forEach { prop ->
                    addCase(prop, byId)
                    addUnloadCase(prop)
                }
                statement {
                    text("default: throw new IllegalArgumentException(")
                    string("Illegal property ${if (byId) "id" else "name"} for \"${type.qualifiedName}\": \"")
                    text(" + prop + ")
                    string("\", it does not exist or its loaded state is not controllable")
                    text(")")
                }
                endControlFlow()
            },
        )
    }

    private fun LsiCodeBuilder.addUnloadCase(prop: JimmerImmutableDraftPropPlan) {
        val basePropId = prop.idViewBasePropId ?: prop.manyToManyBasePropId
        when {
            basePropId != null -> {
                val baseProp = type.propsById.getValue(basePropId)
                statement {
                    text("__unload(")
                    type(PROP_ID_TYPE)
                    text(".byIndex(${baseProp.slotName}));break")
                }
            }
            prop.languageFormula -> statement { text("break") }
            prop.loadedStateFieldName != null -> {
                statement {
                    text("$DRAFT_MODIFIED_FIELD().${requireNotNull(prop.valueFieldName)} = ")
                    add(prop.unloadedValueLiteral)
                }
                statement {
                    text("$DRAFT_MODIFIED_FIELD().${prop.loadedStateFieldName} = false;break")
                }
            }
            prop.valueFieldName != null -> {
                statement { text("$DRAFT_MODIFIED_FIELD().${prop.valueFieldName} = null;break") }
            }
            else -> statement { text("break") }
        }
    }

    private fun draftImplResolveFunction(): LsiMethod {
        return LsiMethod(
            name = "__resolve",
            modifiers = PUBLIC_OVERRIDE,
            returnType = OBJECT_TYPE,
            body = draftCode {
                beginControlFlow { text("if ($DRAFT_RESOLVED_FIELD != null)") }
                returnValue { name(DRAFT_RESOLVED_FIELD) }
                endControlFlow()
                beginControlFlow { text("if ($DRAFT_RESOLVING_FIELD)") }
                statement {
                    text("throw new ")
                    type(CIRCULAR_REFERENCE_EXCEPTION_TYPE)
                    text("()")
                }
                endControlFlow()
                statement { text("$DRAFT_RESOLVING_FIELD = true") }
                beginControlFlow { text("try") }
                addResolveCode()
                endControlFlow()
                beginControlFlow { text("finally") }
                statement { text("$DRAFT_RESOLVING_FIELD = false") }
                endControlFlow()
            },
        )
    }

    private fun LsiCodeBuilder.addResolveCode() {
        statement {
            type(implementorType)
            text(" base = $DRAFT_BASE_FIELD")
        }
        statement {
            type(implType)
            text(" __tmpModified = $DRAFT_MODIFIED_FIELD")
        }
        val resolvableProps = legacyProps.filter { prop ->
            prop.valueState.hasValue && (prop.immutableReference || prop.list)
        }
        if (resolvableProps.isNotEmpty()) {
            beginControlFlow { text("if (__tmpModified == null)") }
            resolvableProps.forEach { prop ->
                beginControlFlow {
                    text("if (base.__isLoaded(")
                    type(PROP_ID_TYPE)
                    text(".byIndex(${prop.slotName})))")
                }
                statement {
                    type(prop.type.withoutJavaDraftTypeAnnotations())
                    text(" oldValue = base.${prop.sourceGetterName}()")
                }
                statement {
                    type(prop.type.withoutJavaDraftTypeAnnotations())
                    text(" newValue = $DRAFT_CONTEXT_FIELD.")
                    text(if (prop.list) "resolveList" else "resolveObject")
                    text("(oldValue)")
                }
                beginControlFlow { text("if (oldValue != newValue)") }
                statement { text("${prop.javaSetterName}(newValue)") }
                endControlFlow()
                endControlFlow()
            }
            statement { text("__tmpModified = $DRAFT_MODIFIED_FIELD") }
            nextControlFlow { text("else") }
            resolvableProps.forEach { prop ->
                val valueField = requireNotNull(prop.valueFieldName)
                statement {
                    text("__tmpModified.$valueField = ")
                    if (prop.list) {
                        type(NON_SHARED_LIST_RAW_TYPE)
                        text(".of(__tmpModified.$valueField, ")
                        text("$DRAFT_CONTEXT_FIELD.resolveList(__tmpModified.$valueField))")
                    } else {
                        text("$DRAFT_CONTEXT_FIELD.resolveObject(__tmpModified.$valueField)")
                    }
                }
            }
            endControlFlow()
        }
        beginControlFlow { text("if ($DRAFT_BASE_FIELD != null && __tmpModified == null)") }
        statement { text("this.$DRAFT_RESOLVED_FIELD = base") }
        returnValue { text("base") }
        endControlFlow()
        add(ImmutableDraftJavaValidationPoet.typeValidationCode(type, "__tmpModified"))
        statement { text("this.$DRAFT_RESOLVED_FIELD = __tmpModified") }
        returnValue { text("__tmpModified") }
    }

    private fun draftImplModifiedFunction(): LsiMethod {
        return LsiMethod(
            name = DRAFT_MODIFIED_FIELD,
            returnType = implType,
            body = draftCode {
                statement {
                    type(implType)
                    text(" __tmpModified = $DRAFT_MODIFIED_FIELD")
                }
                beginControlFlow { text("if (__tmpModified == null)") }
                statement { text("__tmpModified = $DRAFT_BASE_FIELD.clone()") }
                statement { text("$DRAFT_MODIFIED_FIELD = __tmpModified") }
                endControlFlow()
                returnValue { text("__tmpModified") }
            },
        )
    }

    private fun builder(): LsiClass {
        return LsiClass(
            name = BUILDER,
            kind = LsiTypeDeclarationKind.CLASS,
            annotations = listOf(generatedByAnnotation()),
            modifiers = setOf(LsiModifier.PUBLIC, LsiModifier.STATIC),
            members = buildList {
                add(
                    LsiField(
                        name = "__draft",
                        type = draftImplType,
                        modifiers = setOf(LsiModifier.PRIVATE, LsiModifier.FINAL),
                    )
                )
                add(
                    LsiConstructor(
                        modifiers = setOf(LsiModifier.PUBLIC),
                        body = draftCode { statement { text("this(null)") } },
                    )
                )
                add(builderBaseConstructor())
                legacyProps.filter(JimmerImmutableDraftPropPlan::writable).forEach { prop ->
                    add(builderSetter(prop))
                }
                add(
                    LsiMethod(
                        name = "build",
                        modifiers = setOf(LsiModifier.PUBLIC),
                        returnType = modelType,
                        body = draftCode {
                            returnValue {
                                text("(")
                                type(modelType)
                                text(")__draft.$DRAFT_MODIFIED_FIELD()")
                            }
                        },
                    )
                )
            },
        )
    }

    private fun builderBaseConstructor(): LsiConstructor {
        return LsiConstructor(
            modifiers = setOf(LsiModifier.PUBLIC),
            parameters = listOf(
                LsiParameter(
                    name = "base",
                    type = modelType.withJavaDraftNullity(nullable = true),
                )
            ),
            body = draftCode {
                statement {
                    text("__draft = new ")
                    type(draftImplType)
                    text("(null, base)")
                }
                legacyProps.filter(JimmerImmutableDraftPropPlan::visibilityControllable).forEach { prop ->
                    statement {
                        text("__draft.__show(")
                        type(PROP_ID_TYPE)
                        text(".byIndex(")
                        type(producerType)
                        text(".${prop.slotName}), false)")
                    }
                }
            },
        )
    }

    private fun builderSetter(prop: JimmerImmutableDraftPropPlan): LsiMethod {
        return LsiMethod(
            name = prop.codegenName,
            annotations = prop.annotationPlan.builderMethodAnnotations
                .map(LsiAnnotation::toJavaDraftPoetAnnotation),
            modifiers = setOf(LsiModifier.PUBLIC),
            parameters = listOf(
                LsiParameter(
                    name = prop.codegenName,
                    type = prop.type
                        .boxedForJavaDraft()
                        .withJavaDraftNullity(prop.nullable),
                )
            ),
            returnType = builderType,
            body = draftCode {
                if (prop.nullable) {
                    statement { text("__draft.${prop.javaSetterName}(${prop.codegenName})") }
                    addBuilderVisibility(prop)
                } else {
                    beginControlFlow { text("if (${prop.codegenName} != null)") }
                    statement { text("__draft.${prop.javaSetterName}(${prop.codegenName})") }
                    addBuilderVisibility(prop)
                    endControlFlow()
                }
                returnValue { text("this") }
            },
        )
    }

    private fun LsiCodeBuilder.addBuilderVisibility(prop: JimmerImmutableDraftPropPlan) {
        if (!prop.visibilityControllable) {
            return
        }
        statement {
            text("__draft.__show(")
            type(PROP_ID_TYPE)
            text(".byIndex(")
            type(producerType)
            text(".${prop.slotName}), true)")
        }
    }

    private fun associatedIdMembers(
        prop: JimmerImmutableDraftPropPlan,
        withImplementation: Boolean,
    ): List<LsiMethod> {
        val contract = prop.associatedId ?: return emptyList()
        val targetType = requireNotNull(prop.targetTypeId).let(schema.typesById::getValue)
        val targetIdProp = targetType.propsById.getValue(contract.targetIdPropId)
        val idType = if (prop.nullable) {
            targetIdProp.type.boxedForJavaDraft()
        } else {
            targetIdProp.type.withoutJavaDraftTypeAnnotations()
        }
        return listOf(
            associatedIdGetter(prop, targetType, targetIdProp, idType, withImplementation),
            associatedIdSetter(prop, targetIdProp, idType, contract.name, withImplementation),
        )
    }

    private fun associatedIdGetter(
        prop: JimmerImmutableDraftPropPlan,
        targetType: JimmerImmutableDraftTypePlan,
        targetIdProp: JimmerImmutableDraftPropPlan,
        idType: LsiType,
        withImplementation: Boolean,
    ): LsiMethod {
        return LsiMethod(
            name = StringUtil.identifier(prop.sourceGetterName, "Id"),
            annotations = buildList {
                add(JSON_IGNORE_ANNOTATION)
                if (idType !is LsiPrimitiveType || idType.boxed) {
                    add(if (prop.nullable) NULLABLE_ANNOTATION else NON_NULL_ANNOTATION)
                }
            },
            modifiers = buildSet {
                add(LsiModifier.PUBLIC)
                add(if (withImplementation) LsiModifier.OVERRIDE else LsiModifier.ABSTRACT)
            },
            returnType = idType,
            body = if (!withImplementation) {
                LsiCodeBlock.EMPTY
            } else {
                draftCode {
                    if (prop.nullable) {
                        statement {
                            type(LsiDeclaredType(targetType.typeId))
                            text(" value = ${prop.sourceGetterName}()")
                        }
                        beginControlFlow { text("if (value == null)") }
                        returnValue { text("null") }
                        endControlFlow()
                        returnValue { text("value.${targetIdProp.sourceGetterName}()") }
                    } else {
                        returnValue {
                            text("${prop.sourceGetterName}().${targetIdProp.sourceGetterName}()")
                        }
                    }
                }
            },
        )
    }

    private fun associatedIdSetter(
        prop: JimmerImmutableDraftPropPlan,
        targetIdProp: JimmerImmutableDraftPropPlan,
        idType: LsiType,
        parameterName: String,
        withImplementation: Boolean,
    ): LsiMethod {
        return LsiMethod(
            name = StringUtil.identifier(prop.javaSetterName, "Id"),
            annotations = listOf(OLD_CHAIN_ANNOTATION),
            modifiers = buildSet {
                add(LsiModifier.PUBLIC)
                add(if (withImplementation) LsiModifier.OVERRIDE else LsiModifier.ABSTRACT)
            },
            parameters = listOf(
                LsiParameter(
                    name = parameterName,
                    type = if (idType !is LsiPrimitiveType || idType.boxed) {
                        idType.withJavaDraftNullity(prop.nullable)
                    } else {
                        idType
                    },
                )
            ),
            returnType = LsiDeclaredType(prop.sourceDeclaringTypeId.draftTypeId()),
            body = if (!withImplementation) {
                LsiCodeBlock.EMPTY
            } else {
                draftCode {
                    if (prop.nullable) {
                        beginControlFlow { text("if ($parameterName == null)") }
                        statement { text("${prop.javaSetterName}(null)") }
                        returnValue { text("this") }
                        endControlFlow()
                        statement {
                            text("${prop.sourceGetterName}(true).${targetIdProp.javaSetterName}($parameterName)")
                        }
                    } else {
                        statement {
                            text("${prop.sourceGetterName}(true).${targetIdProp.javaSetterName}(")
                            type(OBJECTS_TYPE)
                            text(".requireNonNull($parameterName, ")
                            string("\"${prop.name}\" cannot be null")
                            text("))")
                        }
                    }
                    returnValue { text("this") }
                }
            },
        )
    }

    private fun LsiCodeBuilder.addCase(
        prop: JimmerImmutableDraftPropPlan,
        byId: Boolean,
    ) {
        if (byId) {
            text("case ${prop.slotName}:\n\t\t")
        } else {
            text("case ")
            string(prop.name)
            text(":\n\t\t")
        }
    }

    private fun LsiCodeBuilder.addIllegalPropertyCase(byId: Boolean?) {
        statement {
            text("default: throw new IllegalArgumentException(")
            val propertyKind = when (byId) {
                true -> "id"
                false, null -> "name"
            }
            string("Illegal property $propertyKind for \"${type.qualifiedName}\": \"")
            text(" + prop + ")
            string("\"")
            text(")")
        }
    }

    private fun propPlan(propId: LsiSymbolId): JimmerImmutableDraftPropPlan {
        return schema.types.asSequence()
            .mapNotNull { candidate -> candidate.propsById[propId] }
            .firstOrNull()
            ?: error("Cannot resolve immutable draft property '${propId.value}'")
    }

    private fun generatedByAnnotation(): LsiAnnotation {
        return sourceLsiAnnotation(
            type = GENERATED_BY_TYPE_ID,
            arguments = listOf(
                LsiSourceAnnotationArgument.Named(
                    name = "type",
                    value = LsiAnnotationValue.ClassValue(modelType),
                )
            ),
        )
    }

    private val unmodifiedExpression: LsiCodeBlock
        get() = draftCode { text("($DRAFT_MODIFIED_FIELD!= null ? $DRAFT_MODIFIED_FIELD : $DRAFT_BASE_FIELD)") }
}

private fun LsiDeclaredType.toDraftType(): LsiDeclaredType {
    return (withoutJavaDraftTypeAnnotations() as LsiDeclaredType).copy(
        declarationId = declarationId.draftTypeId()
    )
}

private fun LsiSymbolId.draftTypeId(): LsiSymbolId {
    return LsiSymbolId.type("${requireTypeQualifiedName()}Draft")
}

private fun LsiSymbolId.producerType(): LsiDeclaredType {
    return LsiDeclaredType(LsiSymbolId.type("${requireTypeQualifiedName()}Draft.$PRODUCER"))
}

private fun JimmerImmutableDraftPropPlan.draftElementType(): LsiType {
    return if (immutableReference && !genericTarget && targetTypeId != null) {
        LsiDeclaredType(targetTypeId.draftTypeId())
    } else {
        elementType.withoutJavaDraftTypeAnnotations()
    }
}

private fun JimmerImmutableDraftPropPlan.draftType(autoCreate: Boolean): LsiType {
    if (list && !autoCreate) {
        return type.withoutJavaDraftTypeAnnotations()
    }
    val draftElementType = draftElementType()
    return if (list) {
        draftDeclaredType(LIST_TYPE_ID, draftElementType.boxedForJavaDraft())
    } else {
        draftElementType
    }
}

/**
 * Java Draft 的旧生成契约不会把语义类型上的注解复制到声明类型位置。
 * 这里递归剥离注解，同时保留可空性、泛型投影和函数类型结构。
 */
internal fun LsiType.withoutJavaDraftTypeAnnotations(): LsiType {
    return when (this) {
        is LsiDeclaredType -> copy(
            arguments = arguments.map { argument ->
                argument.copy(type = argument.type?.withoutJavaDraftTypeAnnotations())
            },
            annotations = emptyList(),
        )
        is LsiTypeParameterRef -> copy(annotations = emptyList())
        is LsiPrimitiveType -> copy(annotations = emptyList())
        is LsiArrayType -> copy(
            elementType = elementType.withoutJavaDraftTypeAnnotations(),
            annotations = emptyList(),
        )
        is LsiFunctionType -> copy(
            returnType = returnType.withoutJavaDraftTypeAnnotations(),
            receiverType = receiverType?.withoutJavaDraftTypeAnnotations(),
            parameterTypes = parameterTypes.map(LsiType::withoutJavaDraftTypeAnnotations),
            annotations = emptyList(),
        )
        is LsiUnresolvedType -> copy(annotations = emptyList())
    }
}

internal fun LsiType.boxedForJavaDraft(): LsiType {
    val sourceType = withoutJavaDraftTypeAnnotations()
    return if (sourceType is LsiPrimitiveType) {
        sourceType.copy(boxed = true)
    } else {
        sourceType
    }
}

/**
 * JSpecify 空值标记属于类型使用注解，不能挂到 Java 参数声明上。
 */
internal fun LsiType.withJavaDraftNullity(nullable: Boolean): LsiType {
    val annotation = LsiAnnotation(
        type = if (nullable) NULLABLE_ANNOTATION.type else NON_NULL_ANNOTATION.type,
    )
    return when (this) {
        is LsiDeclaredType -> copy(annotations = listOf(annotation))
        is LsiTypeParameterRef -> copy(annotations = listOf(annotation))
        is LsiPrimitiveType -> copy(annotations = listOf(annotation))
        is LsiArrayType -> copy(annotations = listOf(annotation))
        is LsiFunctionType -> copy(annotations = listOf(annotation))
        is LsiUnresolvedType -> copy(annotations = listOf(annotation))
    }
}

private fun LsiTypeParameter.withoutJavaDraftTypeAnnotations(): LsiTypeParameter {
    return copy(
        upperBounds = upperBounds.map(LsiType::withoutJavaDraftTypeAnnotations),
    )
}

private val JimmerImmutableDraftPropPlan.isJavaBeanStyle: Boolean
    get() = accessorStyle == JimmerImmutableDraftAccessorStyle.JAVA_BEAN_GET ||
        accessorStyle == JimmerImmutableDraftAccessorStyle.JAVA_BEAN_IS

private val JimmerImmutableDraftPropPlan.forcedLoadedStateName: String
    get() = loadedStateFieldName ?: "__${codegenName}Loaded"

private val JimmerImmutableDraftPropPlan.unloadedValueLiteral: LsiCodeBlock
    get() {
        val primitiveType = type as? LsiPrimitiveType ?: return draftCode { text("null") }
        if (primitiveType.boxed) {
            return draftCode { text("null") }
        }
        return when (primitiveType.kind) {
            LsiPrimitiveKind.BOOLEAN -> draftCode { text("false") }
            LsiPrimitiveKind.CHAR -> draftCode { character('\u0000') }
            LsiPrimitiveKind.BYTE,
            LsiPrimitiveKind.SHORT,
            LsiPrimitiveKind.INT,
            LsiPrimitiveKind.LONG,
            LsiPrimitiveKind.FLOAT,
            LsiPrimitiveKind.DOUBLE,
            -> draftCode { text("0") }
            LsiPrimitiveKind.UNIT,
            LsiPrimitiveKind.VOID,
            -> draftCode { text("null") }
        }
    }

private val JimmerImmutableDraftTypePlan.isMappedSuperclass: Boolean
    get() = kind == ImmutableTypeKind.MAPPED_SUPERCLASS

private val JimmerImmutableDraftTypePlan.packageName: String
    get() = qualifiedName.substringBeforeLast('.', missingDelimiterValue = "")

private val JimmerImmutableDraftTypePlan.simpleName: String
    get() = qualifiedName.substringAfterLast('.')

private fun LsiAnnotation.toJavaDraftPoetAnnotation(): LsiAnnotation {
    return sourceLsiAnnotation(
        type = type,
        arguments = arguments.entries.mapNotNull { (name, argument) ->
            if (!argument.isExplicit) {
                null
            } else {
                LsiSourceAnnotationArgument.Named(
                    name = name,
                    value = argument.value.toJavaDraftPoetAnnotationValue(),
                )
            }
        },
    )
}

private fun LsiAnnotationValue.toJavaDraftPoetAnnotationValue(): LsiAnnotationValue {
    return when (this) {
        is LsiAnnotationValue.BooleanValue -> LsiAnnotationValue.BooleanValue(value)
        is LsiAnnotationValue.ByteValue -> LsiAnnotationValue.ByteValue(value)
        is LsiAnnotationValue.ShortValue -> LsiAnnotationValue.ShortValue(value)
        is LsiAnnotationValue.IntValue -> LsiAnnotationValue.IntValue(value)
        is LsiAnnotationValue.LongValue -> LsiAnnotationValue.LongValue(value)
        is LsiAnnotationValue.FloatValue -> LsiAnnotationValue.FloatValue(value)
        is LsiAnnotationValue.DoubleValue -> LsiAnnotationValue.DoubleValue(value)
        is LsiAnnotationValue.CharValue -> LsiAnnotationValue.CharValue(value)
        is LsiAnnotationValue.StringValue -> LsiAnnotationValue.StringValue(value)
        is LsiAnnotationValue.EnumValue -> LsiAnnotationValue.EnumValue(enumType, entryName)
        is LsiAnnotationValue.ClassValue -> LsiAnnotationValue.ClassValue(type)
        is LsiAnnotationValue.NestedAnnotationValue -> LsiAnnotationValue.NestedAnnotationValue(
            annotation.toJavaDraftPoetAnnotation()
        )
        is LsiAnnotationValue.ArrayValue -> LsiAnnotationValue.ArrayValue(
            elements.map(LsiAnnotationValue::toJavaDraftPoetAnnotationValue)
        )
    }
}

private const val PRODUCER = "Producer"

private const val IMPLEMENTOR = "Implementor"

private const val IMPL = "Impl"

private const val DRAFT_IMPL = "DraftImpl"

private const val BUILDER = "Builder"

private const val VISIBILITY_FIELD = "__visibility"

private const val DRAFT_CONTEXT_FIELD = "__ctx"

private const val DRAFT_BASE_FIELD = "__base"

private const val DRAFT_MODIFIED_FIELD = "__modified"

private const val DRAFT_RESOLVING_FIELD = "__resolving"

private const val DRAFT_RESOLVED_FIELD = "__resolved"

private const val FROZEN_MESSAGE = "The current draft has been resolved so it cannot be modified"

private val PUBLIC_STATIC_FINAL = setOf(
    LsiModifier.PUBLIC,
    LsiModifier.STATIC,
    LsiModifier.FINAL,
)

private val STATIC_FINAL = setOf(LsiModifier.STATIC, LsiModifier.FINAL)

private val PUBLIC_ABSTRACT = setOf(LsiModifier.PUBLIC, LsiModifier.ABSTRACT)

private val PUBLIC_FINAL = setOf(LsiModifier.PUBLIC, LsiModifier.FINAL)

private val PUBLIC_OVERRIDE = setOf(LsiModifier.PUBLIC, LsiModifier.OVERRIDE)

private val PUBLIC_FINAL_OVERRIDE = setOf(
    LsiModifier.PUBLIC,
    LsiModifier.FINAL,
    LsiModifier.OVERRIDE,
)

private val BOOLEAN_TYPE = LsiPrimitiveType(LsiPrimitiveKind.BOOLEAN)

private val INT_TYPE = LsiPrimitiveType(LsiPrimitiveKind.INT)

private val OBJECT_TYPE = LsiDeclaredType(LsiSymbolId.type("java.lang.Object"))

private val STRING_TYPE = LsiDeclaredType(LsiSymbolId.type("java.lang.String"))

private val SYSTEM_TYPE = LsiDeclaredType(LsiSymbolId.type("java.lang.System"))

private val CLONEABLE_TYPE = LsiDeclaredType(LsiSymbolId.type("java.lang.Cloneable"))

private val SERIALIZABLE_TYPE = LsiDeclaredType(LsiSymbolId.type("java.io.Serializable"))

private val CLONE_NOT_SUPPORTED_EXCEPTION_TYPE =
    LsiDeclaredType(LsiSymbolId.type("java.lang.CloneNotSupportedException"))

private val ILLEGAL_ARGUMENT_EXCEPTION_TYPE =
    LsiDeclaredType(LsiSymbolId.type("java.lang.IllegalArgumentException"))

private val ILLEGAL_STATE_EXCEPTION_TYPE =
    LsiDeclaredType(LsiSymbolId.type("java.lang.IllegalStateException"))

private val ARRAYS_TYPE = LsiDeclaredType(LsiSymbolId.type("java.util.Arrays"))

private val COLLECTIONS_TYPE = LsiDeclaredType(LsiSymbolId.type("java.util.Collections"))

private val OBJECTS_TYPE = LsiDeclaredType(LsiSymbolId.type("java.util.Objects"))

private val ARRAY_LIST_RAW_TYPE = LsiDeclaredType(LsiSymbolId.type("java.util.ArrayList"))

private val LIST_TYPE_ID = LsiSymbolId.type("java.util.List")

private val DRAFT_TYPE = LsiDeclaredType(LsiSymbolId.type("org.babyfish.jimmer.Draft"))

private val DRAFT_CONSUMER_TYPE_ID = LsiSymbolId.type("org.babyfish.jimmer.DraftConsumer")

private val CIRCULAR_REFERENCE_EXCEPTION_TYPE =
    LsiDeclaredType(LsiSymbolId.type("org.babyfish.jimmer.CircularReferenceException"))

private val IMMUTABLE_OBJECTS_TYPE =
    LsiDeclaredType(LsiSymbolId.type("org.babyfish.jimmer.ImmutableObjects"))

private val UNLOADED_EXCEPTION_TYPE =
    LsiDeclaredType(LsiSymbolId.type("org.babyfish.jimmer.UnloadedException"))

private val GENERATED_BY_TYPE_ID = LsiSymbolId.type("org.babyfish.jimmer.internal.GeneratedBy")

private val DESCRIPTION_TYPE_ID = LsiSymbolId.type("org.babyfish.jimmer.client.Description")

private val IMMUTABLE_MODULE_REQUIRED_EXCEPTION_TYPE =
    LsiDeclaredType(LsiSymbolId.type("org.babyfish.jimmer.jackson.ImmutableModuleRequiredException"))

private val IMMUTABLE_PROP_CATEGORY_TYPE =
    LsiDeclaredType(LsiSymbolId.type("org.babyfish.jimmer.meta.ImmutablePropCategory"))

private val PROP_ID_TYPE = LsiDeclaredType(LsiSymbolId.type("org.babyfish.jimmer.meta.PropId"))

private val RUNTIME_IMMUTABLE_TYPE =
    LsiDeclaredType(LsiSymbolId.type("org.babyfish.jimmer.meta.ImmutableType"))

private val DRAFT_CONTEXT_TYPE =
    LsiDeclaredType(LsiSymbolId.type("org.babyfish.jimmer.runtime.DraftContext"))

private val DRAFT_SPI_TYPE = LsiDeclaredType(LsiSymbolId.type("org.babyfish.jimmer.runtime.DraftSpi"))

private val IMMUTABLE_SPI_TYPE =
    LsiDeclaredType(LsiSymbolId.type("org.babyfish.jimmer.runtime.ImmutableSpi"))

private val INTERNAL_TYPE = LsiDeclaredType(LsiSymbolId.type("org.babyfish.jimmer.runtime.Internal"))

private val NON_SHARED_LIST_TYPE_ID = LsiSymbolId.type("org.babyfish.jimmer.runtime.NonSharedList")

private val NON_SHARED_LIST_RAW_TYPE = LsiDeclaredType(NON_SHARED_LIST_TYPE_ID)

private val VISIBILITY_TYPE = LsiDeclaredType(LsiSymbolId.type("org.babyfish.jimmer.runtime.Visibility"))

private val ID_VIEW_LIST_RAW_TYPE =
    LsiDeclaredType(LsiSymbolId.type("org.babyfish.jimmer.sql.collection.IdViewList"))

private val MANY_TO_MANY_VIEW_LIST_RAW_TYPE =
    LsiDeclaredType(LsiSymbolId.type("org.babyfish.jimmer.sql.collection.ManyToManyViewList"))

private val MUTABLE_ID_VIEW_LIST_RAW_TYPE =
    LsiDeclaredType(LsiSymbolId.type("org.babyfish.jimmer.sql.collection.MutableIdViewList"))

private val NULLABLE_ANNOTATION = sourceLsiAnnotation(
    type = LsiSymbolId.type("org.jspecify.annotations.Nullable")
)

private val NON_NULL_ANNOTATION = sourceLsiAnnotation(
    type = LsiSymbolId.type("org.jspecify.annotations.NonNull")
)

private val OLD_CHAIN_ANNOTATION = sourceLsiAnnotation(
    type = LsiSymbolId.type("org.babyfish.jimmer.lang.OldChain")
)

private val JAVA_OVERRIDE_ANNOTATION = sourceLsiAnnotation(
    type = LsiSymbolId.type("java.lang.Override")
)

private val JSON_IGNORE_ANNOTATION = sourceLsiAnnotation(
    type = LsiSymbolId.type("com.fasterxml.jackson.annotation.JsonIgnore")
)

private val JSON_PROPERTY_ORDER_TYPE_ID =
    LsiSymbolId.type("com.fasterxml.jackson.annotation.JsonPropertyOrder")

private val SUPPRESS_ALL_ANNOTATION = sourceLsiAnnotation(
    type = LsiSymbolId.type("java.lang.SuppressWarnings"),
    arguments = listOf(
        LsiSourceAnnotationArgument.Positional(LsiAnnotationValue.StringValue("all"))
    ),
)
