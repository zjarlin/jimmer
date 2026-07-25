package site.addzero.lsi.jimmer

import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.model.LsiDeclaredType
import site.addzero.lsi.model.LsiTypeRef

/** 返回属性的具体不可变目标类型，泛型目标尚未具体化时返回空。 */
fun ImmutableSchema.targetTypeOf(prop: ImmutableProp): ImmutableType? {
    return prop.targetTypeId?.let(typesById::get)
}

/** 返回关联目标的主键属性，目标或主键尚不可用时返回空。 */
fun ImmutableSchema.targetIdPropOf(prop: ImmutableProp): ImmutableProp? {
    return targetTypeOf(prop)?.idPropId?.let(propsById::get)
}

/** 判断属性是否具有实体关联语义，尚未具体化的泛型实体关联也视为实体关联。 */
fun ImmutableSchema.isEntityAssociation(prop: ImmutableProp): Boolean {
    return prop.association &&
        (prop.genericTarget || targetTypeOf(prop)?.kind == ImmutableTypeKind.ENTITY)
}

/** 判断属性是否关联当前 schema 中可解析的具体实体，不接受未具体化的泛型目标。 */
fun ImmutableSchema.isConcreteEntityAssociation(prop: ImmutableProp): Boolean {
    return prop.association && targetTypeOf(prop)?.kind == ImmutableTypeKind.ENTITY
}

/** 判断属性值是否由不可变对象或不可变对象集合承载。 */
fun ImmutableSchema.isImmutableReference(prop: ImmutableProp): Boolean {
    return prop.association || prop.embedded || targetTypeOf(prop)?.kind == ImmutableTypeKind.IMMUTABLE
}

/** 判断属性是否声明指定类型的有效注解。 */
fun ImmutableProp.hasAnnotation(annotationTypeId: LsiSymbolId): Boolean {
    return annotations.any { annotation -> annotation.type == annotationTypeId }
}

/** 返回主键视图关联的基属性，其他属性返回空。 */
fun ImmutableSchema.idViewBasePropOf(prop: ImmutableProp): ImmutableProp? {
    val view = prop.view as? ImmutableView.Id ?: return null
    return propsById.getValue(view.basePropId)
}

/** 返回多对多视图关联的基属性，其他属性返回空。 */
fun ImmutableSchema.manyToManyViewBasePropOf(prop: ImmutableProp): ImmutableProp? {
    val view = prop.view as? ImmutableView.ManyToMany ?: return null
    return propsById.getValue(view.basePropId)
}

/** 对主键视图返回其关联基属性，其他属性原样返回。 */
fun ImmutableSchema.idViewBasePropOrSelf(prop: ImmutableProp): ImmutableProp {
    return idViewBasePropOf(prop) ?: prop
}

/** 返回沿主继承链严格派生自指定实体的全部子类型。 */
fun ImmutableSchema.strictPrimarySubtypesOf(type: ImmutableType): List<ImmutableType> {
    if (type.kind != ImmutableTypeKind.ENTITY || type.inheritanceRootTypeId == null) {
        return emptyList()
    }
    return types
        .filter { candidate -> candidate.id != type.id && candidate.isPrimarySubtypeOf(type.id, this) }
        .sortedBy(ImmutableType::qualifiedName)
}

/** 返回沿主继承链实际声明同一谱系属性的最近类型。 */
fun ImmutableSchema.primaryLineageOwner(
    type: ImmutableType,
    prop: ImmutableProp,
): ImmutableType {
    if (prop.declaringTypeId == type.id) {
        return type
    }
    val primaryType = type.primarySuperTypeId?.let(typesById::get) ?: return type
    val primaryProp = primaryType.props.firstOrNull { candidate ->
        candidate.lineageRootId() == prop.lineageRootId()
    } ?: return type
    return primaryLineageOwner(primaryType, primaryProp)
}

/** 列表属性返回唯一元素类型，非列表属性返回自身类型。 */
fun ImmutableProp.elementTypeOrSelf(): LsiTypeRef {
    if (!list) {
        return type
    }
    val listType = type as? LsiDeclaredType
        ?: error("List immutable property '${id.value}' must use a declared list type")
    return listType.arguments.singleOrNull()?.type
        ?: error("List immutable property '${id.value}' must declare one element type")
}

/** 返回属性覆盖谱系最初声明的稳定符号 ID。 */
fun ImmutableProp.lineageRootId(): LsiSymbolId {
    return overrideChain.lastOrNull() ?: declarationId
}

private fun ImmutableType.isPrimarySubtypeOf(
    superTypeId: LsiSymbolId,
    schema: ImmutableSchema,
): Boolean {
    var currentTypeId = primarySuperTypeId
    val visited = mutableSetOf<LsiSymbolId>()
    while (currentTypeId != null && visited.add(currentTypeId)) {
        if (currentTypeId == superTypeId) {
            return true
        }
        currentTypeId = schema.typesById[currentTypeId]?.primarySuperTypeId
    }
    return false
}
