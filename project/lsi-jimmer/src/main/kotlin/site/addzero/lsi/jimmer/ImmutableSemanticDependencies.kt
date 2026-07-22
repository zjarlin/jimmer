package site.addzero.lsi.jimmer

import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.model.LsiAnnotation
import site.addzero.lsi.model.LsiAnnotationValue
import site.addzero.lsi.model.LsiArrayType
import site.addzero.lsi.model.LsiDeclaredType
import site.addzero.lsi.model.LsiFunctionType
import site.addzero.lsi.model.LsiPrimitiveType
import site.addzero.lsi.model.LsiTypeParameterRef
import site.addzero.lsi.model.LsiTypeRef
import site.addzero.lsi.model.LsiUnresolvedType
import site.addzero.lsi.model.LsiWorkspace

/**
 * 收集不可变类型层级及其属性闭包依赖的全部稳定符号。
 */
fun ImmutableSchema.semanticDependencySymbols(
    rootTypeIds: Collection<LsiSymbolId>,
    rootProps: Collection<ImmutableProp>,
    workspace: LsiWorkspace,
): Set<LsiSymbolId> {
    return sortedSetOf<LsiSymbolId>().apply {
        collectImmutableTypeHierarchyDependencies(this@semanticDependencySymbols, rootTypeIds, workspace)
        collectImmutablePropDependencies(this@semanticDependencySymbols, rootProps, workspace)
    }
}

/**
 * 将一个类型层级的稳定语义依赖追加到目标集合。
 */
fun MutableSet<LsiSymbolId>.collectImmutableTypeHierarchyDependencies(
    schema: ImmutableSchema,
    rootTypeIds: Collection<LsiSymbolId>,
    workspace: LsiWorkspace,
) {
    val pending = ArrayDeque(rootTypeIds.sorted())
    val visited = mutableSetOf<LsiSymbolId>()
    while (pending.isNotEmpty()) {
        val typeId = pending.removeFirst()
        if (!visited.add(typeId)) continue
        add(typeId)
        val immutableType = schema.typesById[typeId] ?: continue
        workspace.typeHierarchyEntry(typeId)?.let { hierarchy ->
            hierarchy.typeParameters.forEach { parameter ->
                add(parameter.id)
                parameter.upperBounds.forEach(::collectTypeRefDependencies)
            }
            hierarchy.directSuperTypes.forEach(::collectTypeRefDependencies)
        }
        immutableType.annotations.forEach(::collectAnnotationDependencies)
        addAll(immutableType.typeParameterIds)
        immutableType.discriminatorPropId?.let(::add)
        immutableType.idPropId?.let(::add)
        immutableType.versionPropId?.let(::add)
        immutableType.logicalDeletedPropId?.let(::add)
        val hierarchyIds = buildList {
            addAll(immutableType.superTypeIds)
            immutableType.primarySuperTypeId?.let(::add)
            immutableType.inheritanceRootTypeId?.let(::add)
        }
        addAll(hierarchyIds)
        hierarchyIds.sorted().forEach(pending::addLast)
    }
}

/**
 * 将属性及其映射、视图、公式、校验和转换器依赖追加到目标集合。
 */
fun MutableSet<LsiSymbolId>.collectImmutablePropDependencies(
    schema: ImmutableSchema,
    rootProps: Collection<ImmutableProp>,
    workspace: LsiWorkspace,
) {
    val pending = ArrayDeque(rootProps.sortedBy(ImmutableProp::id))
    val visited = mutableSetOf<LsiSymbolId>()
    while (pending.isNotEmpty()) {
        val prop = pending.removeFirst()
        if (!visited.add(prop.id)) continue
        add(prop.id)
        add(prop.declarationId)
        add(prop.ownerTypeId)
        add(prop.declaringTypeId)
        addAll(prop.overrideChain)
        prop.primaryAnnotationTypeId?.let(::add)
        collectTypeRefDependencies(prop.type)
        prop.annotations.forEach(::collectAnnotationDependencies)
        collectImmutableTypeHierarchyDependencies(
            schema = schema,
            rootTypeIds = listOfNotNull(prop.ownerTypeId, prop.declaringTypeId, prop.targetTypeId),
            workspace = workspace,
        )
        prop.targetTypeId
            ?.let(schema.typesById::get)
            ?.idPropId
            ?.let(schema.propsById::get)
            ?.let(pending::addLast)
        (prop.overrideChain + prop.declarationId)
            .mapNotNull(schema.propsById::get)
            .sortedBy(ImmutableProp::id)
            .forEach(pending::addLast)
        val dependencyPropIds = buildList {
            prop.mappedBy?.ownerPropId?.let(::add)
            prop.view?.dependencyPropIds?.let(::addAll)
            prop.formulaDependencies.forEach { dependency -> addAll(dependency.propIds) }
        }
        addAll(dependencyPropIds)
        dependencyPropIds
            .mapNotNull(schema.propsById::get)
            .sortedBy(ImmutableProp::id)
            .forEach(pending::addLast)
        when (val resolver = prop.transientResolver) {
            is TransientResolver.Type -> {
                add(resolver.typeId)
                collectImmutableTypeHierarchyDependencies(schema, listOf(resolver.typeId), workspace)
            }
            is TransientResolver.Reference,
            null,
            -> Unit
        }
        prop.validations.forEach { validation ->
            add(validation.annotationTypeId)
            addAll(validation.validatorTypeIds)
        }
        prop.converter?.let { converter ->
            add(converter.converterTypeId)
            converter.sourceType?.let(::collectTypeRefDependencies)
            converter.targetType?.let(::collectTypeRefDependencies)
        }
    }
}

/**
 * 将类型引用中的声明和类型使用注解依赖追加到目标集合。
 */
fun MutableSet<LsiSymbolId>.collectTypeRefDependencies(type: LsiTypeRef) {
    type.annotations.forEach(::collectAnnotationDependencies)
    when (type) {
        is LsiArrayType -> collectTypeRefDependencies(type.elementType)
        is LsiDeclaredType -> {
            add(type.declarationId)
            type.arguments.forEach { argument -> argument.type?.let(::collectTypeRefDependencies) }
        }
        is LsiFunctionType -> {
            type.receiverType?.let(::collectTypeRefDependencies)
            type.parameterTypes.forEach(::collectTypeRefDependencies)
            collectTypeRefDependencies(type.returnType)
        }
        is LsiPrimitiveType,
        is LsiTypeParameterRef,
        is LsiUnresolvedType,
        -> Unit
    }
}

/**
 * 将结构化注解值中的类型、枚举及嵌套注解依赖追加到目标集合。
 */
fun MutableSet<LsiSymbolId>.collectAnnotationDependencies(annotation: LsiAnnotation) {
    add(annotation.type)
    annotation.arguments.values.forEach { argument -> collectAnnotationValueDependencies(argument.value) }
}

private fun MutableSet<LsiSymbolId>.collectAnnotationValueDependencies(value: LsiAnnotationValue) {
    when (value) {
        is LsiAnnotationValue.ArrayValue -> value.elements.forEach(::collectAnnotationValueDependencies)
        is LsiAnnotationValue.ClassValue -> collectTypeRefDependencies(value.type)
        is LsiAnnotationValue.EnumValue -> add(value.enumType)
        is LsiAnnotationValue.NestedAnnotationValue -> collectAnnotationDependencies(value.annotation)
        is LsiAnnotationValue.BooleanValue,
        is LsiAnnotationValue.ByteValue,
        is LsiAnnotationValue.ShortValue,
        is LsiAnnotationValue.IntValue,
        is LsiAnnotationValue.LongValue,
        is LsiAnnotationValue.FloatValue,
        is LsiAnnotationValue.DoubleValue,
        is LsiAnnotationValue.CharValue,
        is LsiAnnotationValue.StringValue,
        -> Unit
    }
}
