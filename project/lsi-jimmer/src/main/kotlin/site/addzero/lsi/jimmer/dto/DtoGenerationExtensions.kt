package site.addzero.lsi.jimmer.dto

/** 按 DTO 文件中的声明顺序返回根类型。 */
fun DtoGraph.rootTypesInDeclarationOrder(): List<DtoType> {
    return rootTypeIds.map(typesById::getValue)
}

/** 按生成类全限定名返回唯一根类型。 */
fun DtoGraph.rootType(qualifiedName: String): DtoType {
    val matches = rootTypesInDeclarationOrder().filter { type ->
        type.qualifiedNameOrNull() == qualifiedName
    }
    require(matches.size == 1) {
        "DTO graph must contain exactly one root type '$qualifiedName': ${source.path}"
    }
    return matches.single()
}

/** 返回命名 DTO 类型的生成类全限定名。 */
fun DtoType.qualifiedNameOrNull(): String? {
    val simpleName = name ?: return null
    return if (packageName.isEmpty()) simpleName else "$packageName.$simpleName"
}

/** 按 DTO 声明顺序返回用户属性。 */
fun DtoType.userPropsInDeclarationOrder(graph: DtoGraph): List<DtoUserProp> {
    return propsInDeclarationOrder(graph).filterIsInstance<DtoUserProp>()
}

/** 按 DTO 声明顺序返回折叠属性。 */
fun DtoType.foldPropsInDeclarationOrder(graph: DtoGraph): List<DtoFoldProp> {
    return propsInDeclarationOrder(graph).filterIsInstance<DtoFoldProp>()
}

/** 按 DTO 声明顺序返回隐藏展开属性。 */
fun DtoType.hiddenFlatPropsInDeclarationOrder(graph: DtoGraph): List<DtoBaseProp> {
    require(graph.typesById[id] == this) {
        "DTO type does not belong to this graph: ${id.value}"
    }
    return hiddenFlatPropIds.map { propId ->
        graph.propsById.getValue(propId) as? DtoBaseProp
            ?: error("DTO hidden flat property is not a base property: ${propId.value}")
    }
}

/** 按属性名返回基础属性。 */
fun DtoType.baseProp(graph: DtoGraph, name: String): DtoBaseProp {
    return basePropsInDeclarationOrder(graph).singleOrNull { prop -> prop.name == name }
        ?: throw IllegalArgumentException("DTO type '${id.value}' has no base property '$name'")
}

/** 按属性名返回折叠属性。 */
fun DtoType.foldProp(graph: DtoGraph, name: String): DtoFoldProp {
    return foldPropsInDeclarationOrder(graph).singleOrNull { prop -> prop.name == name }
        ?: throw IllegalArgumentException("DTO type '${id.value}' has no fold property '$name'")
}

/** 返回展开路径中的下一属性。 */
fun DtoBaseProp.nextProp(graph: DtoGraph): DtoBaseProp? {
    require(graph.propsById[id] == this) {
        "DTO property does not belong to this graph: ${id.value}"
    }
    return nextPropId?.let { propId -> graph.propsById.getValue(propId) as DtoBaseProp }
}

/** 返回展开路径的尾属性。 */
fun DtoBaseProp.tailProp(graph: DtoGraph): DtoBaseProp {
    require(graph.propsById[id] == this) {
        "DTO property does not belong to this graph: ${id.value}"
    }
    return graph.propsById.getValue(tailPropId) as DtoBaseProp
}

/** 返回需要生成嵌套声明的基础属性目标；递归复用当前声明时返回空。 */
fun DtoBaseProp.generatedTargetType(graph: DtoGraph): DtoType? {
    require(graph.propsById[id] == this) {
        "DTO property does not belong to this graph: ${id.value}"
    }
    if (targetTypeReference != null) {
        return null
    }
    val targetTypeId = targetTypeId ?: return null
    val targetType = graph.typesById.getValue(targetTypeId)
    return targetType.takeIf { !recursive || targetType.focusedRecursion }
}

/** 返回折叠属性需要生成的嵌套声明目标。 */
fun DtoFoldProp.generatedTargetType(graph: DtoGraph): DtoType {
    require(graph.propsById[id] == this) {
        "DTO property does not belong to this graph: ${id.value}"
    }
    return graph.typesById.getValue(targetTypeId)
}

/** 返回折叠属性的空值守卫属性。 */
fun DtoFoldProp.nullGuardProp(graph: DtoGraph): DtoBaseProp? {
    require(graph.propsById[id] == this) {
        "DTO property does not belong to this graph: ${id.value}"
    }
    return nullGuardPropId?.let { propId -> graph.propsById.getValue(propId) as DtoBaseProp }
}

/** 返回唯一默认多态分支。 */
fun DtoPolymorphism.defaultBranch(): DtoPolymorphicBranch? {
    return branches.singleOrNull { branch -> branch.kind == DtoPolymorphicBranchKind.DEFAULT }
}

/** 按声明顺序返回类型多态分支。 */
fun DtoPolymorphism.typeBranchesInDeclarationOrder(): List<DtoPolymorphicBranch> {
    return branches.filter { branch -> branch.kind == DtoPolymorphicBranchKind.TYPE }
}

/** 返回多态分支自身的语义类型。 */
fun DtoPolymorphicBranch.bodyType(graph: DtoGraph): DtoType {
    return graph.typesById.getValue(bodyTypeId)
}

/** 返回多态根与分支合并后的生成语义类型。 */
fun DtoPolymorphicBranch.mergedType(graph: DtoGraph): DtoType {
    return graph.typesById.getValue(mergedTypeId)
}
