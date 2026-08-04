package site.addzero.lsi.poet

import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.model.LsiTypeDeclaration
import site.addzero.lsi.model.LsiWorkspace

/**
 * 类型稳定身份对应的精确源码名称结构。
 */
data class LsiPoetTypeName(
    val typeId: LsiSymbolId,
    val packageName: String,
    val simpleNames: List<String>,
) {
    val canonicalName: String = (listOf(packageName).filter(String::isNotEmpty) + simpleNames)
        .joinToString(".")

    init {
        require(typeId.isTypeId()) {
            "LSI Poet type name requires a type id: ${typeId.value}"
        }
        require(packageName == packageName.trim()) {
            "LSI Poet type package cannot have surrounding whitespace: '$packageName'"
        }
        require(packageName.isEmpty() || packageName.isPoetPackageName()) {
            "LSI Poet type package must contain non-empty source-name segments: '$packageName'"
        }
        require(simpleNames.isNotEmpty()) {
            "LSI Poet type name requires at least one simple name: ${typeId.value}"
        }
        require(simpleNames.all(String::isPoetSimpleName)) {
            "LSI Poet type simple names must be non-empty source names without '.': ${simpleNames.joinToString(".")}"
        }
        require(canonicalName == typeId.requireTypeQualifiedName()) {
            "LSI Poet type name '$canonicalName' does not match type id '${typeId.value}'"
        }
    }
}

/** 使用显式包名和简单名创建顶层生成类型的精确源码名称。 */
fun generatedTopLevelPoetTypeName(
    packageName: String,
    simpleName: String,
): LsiPoetTypeName {
    val qualifiedName = if (packageName.isEmpty()) simpleName else "$packageName.$simpleName"
    return LsiPoetTypeName(
        typeId = LsiSymbolId.type(qualifiedName),
        packageName = packageName,
        simpleNames = listOf(simpleName),
    )
}

/**
 * 以冻结源码类型的精确包和 enclosing 链为基准，派生同级生成类型名。
 */
fun LsiWorkspace.generatedSiblingPoetTypeName(
    sourceTypeId: LsiSymbolId,
    generatedTypeId: LsiSymbolId,
    simpleNameSuffix: String,
    nestedSimpleNames: List<String> = emptyList(),
): LsiPoetTypeName {
    require(simpleNameSuffix.isNotEmpty() && '.' !in simpleNameSuffix) {
        "Generated sibling type suffix must be a non-empty source-name fragment: '$simpleNameSuffix'"
    }
    val sourceTypeName = toLsiPoetTypeNames(listOf(sourceTypeId)).single()
    return LsiPoetTypeName(
        typeId = generatedTypeId,
        packageName = sourceTypeName.packageName,
        simpleNames = sourceTypeName.simpleNames.dropLast(1) +
            "${sourceTypeName.simpleNames.last()}$simpleNameSuffix" +
            nestedSimpleNames,
    )
}

/**
 * 从冻结声明及显式生成类型中解析精确源码名称，不根据字符大小写推断包边界。
 */
fun LsiWorkspace.toLsiPoetTypeNames(
    typeIds: Collection<LsiSymbolId>,
    additional: Collection<LsiPoetTypeName> = emptyList(),
): List<LsiPoetTypeName> {
    val duplicateAdditionalIds = additional
        .groupingBy(LsiPoetTypeName::typeId)
        .eachCount()
        .filterValues { count -> count > 1 }
        .keys
        .sorted()
    require(duplicateAdditionalIds.isEmpty()) {
        "Duplicate additional LSI Poet type ids: ${duplicateAdditionalIds.joinToString { id -> id.value }}"
    }
    val additionalById = additional.associateBy(LsiPoetTypeName::typeId)
    additionalById.forEach { (typeId, explicitTypeName) ->
        if (this[typeId] != null) {
            val workspaceTypeName = requirePoetTypeName(typeId)
            require(explicitTypeName == workspaceTypeName) {
                "Additional LSI Poet type name conflicts with workspace declaration: ${typeId.value}"
            }
        }
    }
    val requestedTypeIds = sortedSetOf<LsiSymbolId>().apply {
        typeIds.forEach { typeId ->
            require(typeId.isTypeId()) {
                "LSI Poet type-name resolution requires type ids: ${typeId.value}"
            }
            add(typeId)
        }
    }
    return requestedTypeIds.map { typeId ->
        additionalById[typeId] ?: requirePoetTypeName(typeId)
    }
}

private fun LsiWorkspace.requirePoetTypeName(typeId: LsiSymbolId): LsiPoetTypeName {
    val declarationChain = mutableListOf<LsiTypeDeclaration>()
    val visitedTypeIds = mutableSetOf<LsiSymbolId>()
    var currentTypeId: LsiSymbolId? = typeId
    var nestedDeclaration: LsiTypeDeclaration? = null
    while (currentTypeId != null) {
        require(visitedTypeIds.add(currentTypeId)) {
            "Cyclic LSI enclosing type chain: ${currentTypeId.value}"
        }
        val declaration = this[currentTypeId]
        require(declaration is LsiTypeDeclaration) {
            "Missing LSI type declaration for Poet type name: ${currentTypeId.value}"
        }
        require(declaration.qualifiedName == currentTypeId.requireTypeQualifiedName()) {
            "LSI type declaration qualified name does not match its id: ${currentTypeId.value}"
        }
        nestedDeclaration?.let { child ->
            require(child.qualifiedName == "${declaration.qualifiedName}.${child.name}") {
                "LSI nested type qualified name does not match its enclosing declaration: ${child.id.value}"
            }
        }
        declarationChain += declaration
        nestedDeclaration = declaration
        currentTypeId = declaration.enclosingTypeId
    }

    val simpleNames = declarationChain.asReversed().map(LsiTypeDeclaration::name)
    val qualifiedName = declarationChain.first().qualifiedName
    val simpleNameSuffix = simpleNames.joinToString(".")
    val packageName = when {
        qualifiedName == simpleNameSuffix -> ""
        qualifiedName.endsWith(".$simpleNameSuffix") -> qualifiedName.dropLast(simpleNameSuffix.length + 1)
        else -> throw IllegalArgumentException(
            "LSI type declaration name chain does not match qualified name: $qualifiedName"
        )
    }
    return LsiPoetTypeName(
        typeId = typeId,
        packageName = packageName,
        simpleNames = simpleNames,
    )
}

private fun String.isPoetPackageName(): Boolean = split('.').all(String::isPoetSimpleName)

private fun String.isPoetSimpleName(): Boolean = isNotEmpty() && '.' !in this
