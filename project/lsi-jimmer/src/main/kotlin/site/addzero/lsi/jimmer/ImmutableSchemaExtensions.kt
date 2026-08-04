package site.addzero.lsi.jimmer

import site.addzero.lsi.core.LsiLanguage
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.model.LsiDeclaredType
import site.addzero.lsi.model.LsiTypeRef

/** 返回不可变类型限定名中的包名部分。 */
val ImmutableType.packageName: String
    get() = qualifiedName.substringBeforeLast('.', missingDelimiterValue = "")

/** 返回不可变类型限定名中的简单名部分。 */
val ImmutableType.simpleName: String
    get() = qualifiedName.substringAfterLast('.')

/** 返回不可变源码声明自身的冻结类型引用。 */
fun ImmutableType.sourceTypeRef(): LsiDeclaredType {
    return LsiDeclaredType(id)
}

/** 返回不可变类型对应的生成 Props 类型。 */
fun ImmutableType.generatedPropsType(): LsiDeclaredType {
    return generatedQueryType("${simpleName}Props")
}

/** 返回实体类型对应的生成 Table 类型。 */
fun ImmutableType.generatedTableType(): LsiDeclaredType {
    require(kind == ImmutableTypeKind.ENTITY) {
        "Only immutable entity type can have a generated table type: ${id.value}"
    }
    return generatedQueryType("${simpleName}Table")
}

/** 返回不可变类型对应的生成 Draft 类型。 */
fun ImmutableType.generatedDraftType(): LsiDeclaredType {
    return LsiDeclaredType(LsiSymbolId.type("${qualifiedName}Draft"))
}

/** 返回不可变类型对应的 Draft Producer 类型。 */
fun ImmutableType.generatedDraftProducerType(): LsiDeclaredType {
    return LsiDeclaredType(
        LsiSymbolId.type("${generatedDraftType().declarationId.requireTypeQualifiedName()}.Producer"),
    )
}

/** 返回属性声明类型对应的生成 Props 类型。 */
fun ImmutableSchema.generatedPropsTypeOf(prop: ImmutableProp): LsiDeclaredType {
    require(propsById[prop.id] == prop) {
        "Immutable property does not belong to this schema: ${prop.id.value}"
    }
    val declaringType = requireNotNull(typesById[prop.declaringTypeId]) {
        "Immutable property declaring type does not exist: ${prop.declaringTypeId.value}"
    }
    return declaringType.generatedPropsType()
}

/** 返回不可变类型的有效主键属性；没有主键时返回空。 */
fun ImmutableSchema.idPropOf(type: ImmutableType): ImmutableProp? {
    require(typesById[type.id] == type) {
        "Immutable type does not belong to this schema: ${type.id.value}"
    }
    return type.idPropId?.let { idPropId ->
        requireNotNull(propsById[idPropId]) {
            "Immutable id property does not exist: ${idPropId.value}"
        }
    }
}

/** 判断属性是否为指定源码语言实现的公式属性。 */
fun ImmutableProp.isLanguageFormula(language: LsiLanguage): Boolean {
    require(language == LsiLanguage.JAVA || language == LsiLanguage.KOTLIN) {
        "Language formula check requires Java or Kotlin: $language"
    }
    return formulaKind == FormulaKind.LANGUAGE ||
        formulaKind == FormulaKind.ABSTRACT && language == LsiLanguage.JAVA
}

/** 返回属性在生成 Props 类型中的常量名。 */
fun ImmutableProp.generatedPropsConstantName(): String {
    return name.toUpperSnakeCase()
}

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

/** 返回指定实体及其主继承链上的可实例化实体，结果按限定名稳定排序。 */
fun ImmutableSchema.knownConcreteEntityTypesOf(type: ImmutableType): List<ImmutableType> {
    require(typesById[type.id] == type) {
        "Immutable type does not belong to this schema: ${type.id.value}"
    }
    require(type.kind == ImmutableTypeKind.ENTITY) {
        "Concrete entity types require an entity base type: ${type.id.value}"
    }
    return buildList {
        if (type.instantiable) {
            add(type)
        }
        addAll(
            strictPrimarySubtypesOf(type)
                .filter { candidate ->
                    candidate.kind == ImmutableTypeKind.ENTITY && candidate.instantiable
                },
        )
    }.sortedBy(ImmutableType::qualifiedName)
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

private fun ImmutableType.generatedQueryType(simpleName: String): LsiDeclaredType {
    val qualifiedName = if (packageName.isEmpty()) simpleName else "$packageName.$simpleName"
    return LsiDeclaredType(LsiSymbolId.type(qualifiedName))
}

private fun String.toUpperSnakeCase(): String = buildString(length + 8) {
    var previousLowerCaseOrDigit = false
    for (character in this@toUpperSnakeCase) {
        val lowerCaseOrDigit = character.isLowerCase() || character.isDigit()
        if (previousLowerCaseOrDigit && !lowerCaseOrDigit) {
            append('_')
        }
        previousLowerCaseOrDigit = lowerCaseOrDigit
        append(character.uppercaseChar())
    }
}
