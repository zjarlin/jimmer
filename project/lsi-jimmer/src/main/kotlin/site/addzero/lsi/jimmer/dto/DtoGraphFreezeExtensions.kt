package site.addzero.lsi.jimmer.dto

import java.math.BigDecimal
import java.math.BigInteger
import java.util.IdentityHashMap
import org.babyfish.jimmer.dto.compiler.AbstractProp
import org.babyfish.jimmer.dto.compiler.Anno
import org.babyfish.jimmer.dto.compiler.DtoFile
import org.babyfish.jimmer.dto.compiler.DtoModifier as AstDtoModifier
import org.babyfish.jimmer.dto.compiler.DtoPolymorphicBranch as AstDtoPolymorphicBranch
import org.babyfish.jimmer.dto.compiler.DtoProp as AstDtoProp
import org.babyfish.jimmer.dto.compiler.DtoType as AstDtoType
import org.babyfish.jimmer.dto.compiler.DtoTypeKind as AstDtoTypeKind
import org.babyfish.jimmer.dto.compiler.DtoTypeRef as AstDtoTypeReference
import org.babyfish.jimmer.dto.compiler.EnumType
import org.babyfish.jimmer.dto.compiler.FoldProp
import org.babyfish.jimmer.dto.compiler.LikeOption
import org.babyfish.jimmer.dto.compiler.PropConfig
import org.babyfish.jimmer.dto.compiler.TypeRef as AstTypeRef
import org.babyfish.jimmer.dto.compiler.UserProp
import site.addzero.lsi.core.LsiLocation
import site.addzero.lsi.core.LsiPosition
import site.addzero.lsi.core.LsiSource
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.jimmer.ImmutableProp
import site.addzero.lsi.jimmer.ImmutableType
import site.addzero.lsi.model.parseLsiDocumentation

/**
 * 将 DTO 编译器语义树冻结为稳定的 Jimmer LSI 图。
 */
fun List<AstDtoType<ImmutableType, ImmutableProp>>.toLsiDtoGraph(
    source: LsiSource,
): DtoGraph = DtoGraphFreezer(source).freeze(this)

private class DtoGraphFreezer(
    private val graphSource: LsiSource,
) {
    private val typeIds = IdentityHashMap<AstDtoType<ImmutableType, ImmutableProp>, DtoTypeId>()

    private val propIdsByOwner = mutableMapOf<
        DtoTypeId,
        IdentityHashMap<AbstractProp, DtoPropId>,
        >()

    private val types = mutableMapOf<DtoTypeId, DtoType>()

    private val props = mutableMapOf<DtoPropId, DtoProp>()

    fun freeze(
        compiledTypes: List<AstDtoType<ImmutableType, ImmutableProp>>,
    ): DtoGraph {
        val rootTypeIds = compiledTypes.mapIndexed { index, dtoType ->
            freezeType(
                dtoType = dtoType,
                path = "root:${index.stableIndex()}:${dtoType.name.orEmpty()}",
                location = location(dtoType.dtoFile, 1, 0),
            )
        }
        return DtoGraph(
            source = graphSource,
            rootTypeIds = rootTypeIds,
            types = types.values.sortedBy(DtoType::id),
            props = props.values.sortedBy(DtoProp::id),
        )
    }

    private fun freezeType(
        dtoType: AstDtoType<ImmutableType, ImmutableProp>,
        path: String,
        location: LsiLocation,
    ): DtoTypeId {
        typeIds[dtoType]?.let { typeId -> return typeId }
        val typeId = DtoTypeId("${graphSource.path}#$path")
        require(typeId !in types && typeId !in typeIds.values) {
            "Duplicate DTO type id: ${typeId.value}"
        }
        typeIds[dtoType] = typeId

        val propIds = dtoType.props.mapIndexed { index, prop ->
            freezeProp(
                prop = prop,
                ownerType = dtoType,
                ownerTypeId = typeId,
                path = "$path/prop:${index.stableIndex()}:${prop.name}",
            )
        }
        val hiddenFlatPropIds = dtoType.hiddenFlatProps.mapIndexed { index, prop ->
            freezeProp(
                prop = prop,
                ownerType = dtoType,
                ownerTypeId = typeId,
                path = "$path/hidden-flat:${index.stableIndex()}:${prop.name}",
            )
        }
        val polymorphism = dtoType.polymorphism?.let { value ->
            freezePolymorphism(dtoType, typeId, path, value)
        }
        val type = DtoType(
            id = typeId,
            baseTypeId = dtoType.baseType?.id,
            packageName = dtoType.packageName,
            name = dtoType.name,
            modifiers = dtoType.modifiers
                .sortedWith(compareBy(AstDtoModifier::getOrder, AstDtoModifier::name))
                .mapTo(linkedSetOf()) { modifier -> modifier.toDtoModifier() },
            annotations = dtoType.annotations.map { annotation ->
                annotation.toDtoAnnotation(dtoType.dtoFile)
            },
            superInterfaces = dtoType.superInterfaces.map { typeRef ->
                typeRef.toDtoTypeRef(dtoType.dtoFile)
            },
            documentation = dtoType.effectiveDocumentation(),
            location = location,
            focusedRecursion = dtoType.isFocusedRecursion,
            propIds = propIds,
            hiddenFlatPropIds = hiddenFlatPropIds,
            polymorphism = polymorphism,
        )
        types[typeId] = type
        return typeId
    }

    private fun freezeProp(
        prop: AbstractProp,
        ownerType: AstDtoType<ImmutableType, ImmutableProp>,
        ownerTypeId: DtoTypeId,
        path: String,
    ): DtoPropId {
        val ownerPropIds = propIdsByOwner.getOrPut(ownerTypeId, ::IdentityHashMap)
        ownerPropIds[prop]?.let { propId -> return propId }
        val propId = DtoPropId("${graphSource.path}#$path")
        require(propId !in props && propId !in ownerPropIds.values) {
            "Duplicate DTO property id: ${propId.value}"
        }
        ownerPropIds[prop] = propId
        val frozenProp = when (prop) {
            is AstDtoProp<*, *> -> freezeBaseProp(prop.castBaseProp(), ownerType, propId, ownerTypeId, path)
            is UserProp -> freezeUserProp(prop, ownerType, propId, ownerTypeId)
            is FoldProp<*, *> -> freezeFoldProp(prop.castFoldProp(), ownerType, propId, ownerTypeId, path)
            else -> error("Unsupported DTO property implementation: ${prop.javaClass.name}")
        }
        props[propId] = frozenProp
        return propId
    }

    private fun freezeBaseProp(
        prop: AstDtoProp<ImmutableType, ImmutableProp>,
        ownerType: AstDtoType<ImmutableType, ImmutableProp>,
        propId: DtoPropId,
        ownerTypeId: DtoTypeId,
        path: String,
    ): DtoBaseProp {
        val dtoDocumentation = ownerType.dtoDocumentation(prop)
        val nextPropId = prop.nextProp?.let { nextProp ->
            freezeProp(nextProp, ownerType, ownerTypeId, "$path/next:${nextProp.name}")
        }
        val tailProp = prop.toTailProp()
        val tailPropId = if (tailProp === prop) {
            propId
        } else {
            freezeProp(tailProp, ownerType, ownerTypeId, "$path/tail:${tailProp.name}")
        }
        val targetTypeReference = prop.targetTypeRef
        val targetType = prop.targetType ?: targetTypeReference?.sourceType
        val targetTypeId = targetType?.let {
            freezeType(
                dtoType = it,
                path = if (targetTypeReference == null) {
                    "$path/target:${it.name.orEmpty()}"
                } else {
                    "$path/reference:${targetTypeReference.qualifiedName}"
                },
                location = location(it.dtoFile, prop.aliasLine, prop.aliasColumn),
            )
        }
        return DtoBaseProp(
            id = propId,
            ownerTypeId = ownerTypeId,
            name = prop.name,
            alias = prop.alias,
            nullable = prop.isNullable,
            annotations = prop.annotations.map { annotation ->
                annotation.toDtoAnnotation(prop.declaringFile)
            },
            documentation = ownerType.effectiveDocumentation(prop, dtoDocumentation),
            aliasLocation = location(prop.declaringFile, prop.aliasLine, prop.aliasColumn),
            baseLocation = location(prop.declaringFile, prop.baseLine, prop.baseColumn),
            baseProps = prop.basePropMap.entries.map { (name, baseProp) ->
                DtoBasePropBinding(name, baseProp.id)
            },
            basePath = prop.basePath,
            nextPropId = nextPropId,
            tailPropId = tailPropId,
            baseNullable = prop.isBaseNullable,
            inputModifier = requireNotNull(prop.inputModifier) {
                "DTO base property must declare an input modifier: ${prop.name}"
            }.toDtoModifier(),
            functionName = prop.funcName,
            targetTypeId = targetTypeId,
            targetTypeReference = targetTypeReference?.toDtoReusableTypeReference(prop.declaringFile),
            enumType = prop.enumType?.toDtoEnumType(),
            config = prop.config?.toDtoConfig(prop.declaringFile),
            recursive = prop.isRecursive,
            likeOptions = prop.likeOptions
                .sortedBy(LikeOption::name)
                .mapTo(linkedSetOf()) { option -> option.toDtoLikeOption() },
            dtoDocumentation = dtoDocumentation,
        )
    }

    private fun freezeUserProp(
        prop: UserProp,
        ownerType: AstDtoType<ImmutableType, ImmutableProp>,
        propId: DtoPropId,
        ownerTypeId: DtoTypeId,
    ): DtoUserProp {
        return DtoUserProp(
            id = propId,
            ownerTypeId = ownerTypeId,
            name = prop.name,
            alias = prop.alias,
            nullable = prop.isNullable,
            annotations = prop.annotations.map { annotation ->
                annotation.toDtoAnnotation(prop.declaringFile)
            },
            documentation = ownerType.effectiveDocumentation(prop),
            aliasLocation = location(prop.declaringFile, prop.aliasLine, prop.aliasColumn),
            type = prop.typeRef.toDtoTypeRef(prop.declaringFile),
            defaultValueText = prop.defaultValueText,
        )
    }

    private fun freezeFoldProp(
        prop: FoldProp<ImmutableType, ImmutableProp>,
        ownerType: AstDtoType<ImmutableType, ImmutableProp>,
        propId: DtoPropId,
        ownerTypeId: DtoTypeId,
        path: String,
    ): DtoFoldProp {
        val nullGuardPropId = prop.nullGuardProp?.let { nullGuardProp ->
            freezeProp(nullGuardProp, ownerType, ownerTypeId, "$path/null-guard:${nullGuardProp.name}")
        }
        val targetTypeId = freezeType(
            dtoType = prop.targetType,
            path = "$path/target:${prop.targetType.name.orEmpty()}",
            location = location(prop.targetType.dtoFile, prop.aliasLine, prop.aliasColumn),
        )
        return DtoFoldProp(
            id = propId,
            ownerTypeId = ownerTypeId,
            name = prop.name,
            alias = prop.alias,
            nullable = prop.isNullable,
            annotations = prop.annotations.map { annotation ->
                annotation.toDtoAnnotation(prop.declaringFile)
            },
            documentation = ownerType.effectiveDocumentation(prop),
            aliasLocation = location(prop.declaringFile, prop.aliasLine, prop.aliasColumn),
            nullGuardPropId = nullGuardPropId,
            targetTypeId = targetTypeId,
        )
    }

    private fun freezePolymorphism(
        rootType: AstDtoType<ImmutableType, ImmutableProp>,
        rootTypeId: DtoTypeId,
        rootPath: String,
        polymorphism: org.babyfish.jimmer.dto.compiler.DtoPolymorphism<ImmutableType, ImmutableProp>,
    ): DtoPolymorphism {
        val branches = buildList {
            polymorphism.defaultBranch?.let { branch ->
                add(freezeBranch(rootType, rootTypeId, rootPath, branch, 0))
            }
            polymorphism.typeBranches.forEachIndexed { index, branch ->
                add(freezeBranch(rootType, rootTypeId, rootPath, branch, index))
            }
        }
        return DtoPolymorphism(
            exhaustive = polymorphism.isExhaustive,
            branches = branches,
        )
    }

    private fun freezeBranch(
        rootType: AstDtoType<ImmutableType, ImmutableProp>,
        rootTypeId: DtoTypeId,
        rootPath: String,
        branch: AstDtoPolymorphicBranch<ImmutableType, ImmutableProp>,
        index: Int,
    ): DtoPolymorphicBranch {
        val kind = branch.kind.toDtoBranchKind()
        val branchPath = "$rootPath/polymorphism:${kind.name.lowercase()}:${index.stableIndex()}:${branch.className}"
        val branchLocation = location(branch.dtoType.dtoFile, branch.line, branch.col)
        val bodyTypeId = freezeType(
            dtoType = branch.dtoType,
            path = "$branchPath/body",
            location = branchLocation,
        )
        val mergedTypeId = freezeType(
            dtoType = rootType.mergedWith(branch.dtoType),
            path = "$branchPath/merged:${rootTypeId.value.substringAfterLast('#')}",
            location = branchLocation,
        )
        return DtoPolymorphicBranch(
            kind = kind,
            targetBaseTypeId = branch.targetType?.id,
            declaredClassName = branch.declaredClassName,
            className = branch.className,
            bodyTypeId = bodyTypeId,
            mergedTypeId = mergedTypeId,
            implicit = branch.isImplicit,
            location = branchLocation,
        )
    }

    private fun AstTypeRef.toDtoTypeRef(declaringFile: DtoFile): DtoTypeRef {
        return DtoTypeRef(
            typeName = typeName,
            arguments = arguments.map { argument ->
                val variance = when {
                    argument.typeRef == null -> DtoVariance.STAR
                    argument.isIn -> DtoVariance.IN
                    argument.isOut -> DtoVariance.OUT
                    else -> DtoVariance.INVARIANT
                }
                DtoTypeArgument(
                    variance = variance,
                    type = argument.typeRef?.toDtoTypeRef(declaringFile),
                )
            },
            nullable = isNullable,
            location = location(declaringFile, line, col),
        )
    }

    private fun AstDtoTypeReference<ImmutableType, ImmutableProp>.toDtoReusableTypeReference(
        declaringFile: DtoFile,
    ): DtoReusableTypeReference {
        val typeInfo = requireNotNull(typeInfo) {
            "Reusable DTO reference must be linked before freezing: $qualifiedName"
        }
        return DtoReusableTypeReference(
            qualifiedName = qualifiedName,
            targetBaseTypeId = targetBaseType.id,
            kind = typeInfo.kind.toDtoReusableTypeKind(),
            location = location(declaringFile, line, column),
        )
    }

    private fun Anno.toDtoAnnotation(declaringFile: DtoFile): DtoAnnotation {
        return DtoAnnotation(
            typeId = LsiSymbolId.type(qualifiedName),
            arguments = valueMap.entries.map { (name, value) ->
                DtoAnnotationArgument(name, value.toDtoAnnotationValue(declaringFile))
            },
        )
    }

    private fun Anno.Value.toDtoAnnotationValue(declaringFile: DtoFile): DtoAnnotationValue {
        return when (this) {
            is Anno.ArrayValue -> DtoAnnotationValue.ArrayValue(
                elements.map { element -> element.toDtoAnnotationValue(declaringFile) }
            )
            is Anno.AnnoValue -> DtoAnnotationValue.AnnotationValue(
                anno.toDtoAnnotation(declaringFile)
            )
            is Anno.EnumValue -> DtoAnnotationValue.EnumValue(
                enumTypeId = LsiSymbolId.type(qualifiedName),
                constant = constant,
            )
            is Anno.TypeRefValue -> DtoAnnotationValue.TypeValue(
                typeRef.toDtoTypeRef(declaringFile)
            )
            is Anno.LiteralValue -> DtoAnnotationValue.LiteralValue(value)
            else -> error("Unsupported DTO annotation value implementation: ${javaClass.name}")
        }
    }

    private fun EnumType.toDtoEnumType(): DtoEnumType {
        return DtoEnumType(
            numeric = isNumeric,
            mappings = valueMap.entries.map { (constant, value) ->
                DtoEnumMapping(constant, value)
            },
        )
    }

    private fun PropConfig<ImmutableProp>.toDtoConfig(
        declaringFile: DtoFile,
    ): DtoPropConfig {
        return DtoPropConfig(
            predicate = predicate?.toDtoPredicate(),
            orderItems = orderItems.map { orderItem ->
                DtoOrderItem(
                    path = orderItem.path.map { pathNode -> pathNode.toDtoPathNode() },
                    descending = orderItem.isDesc,
                )
            },
            filter = filterType?.toDtoConfigTypeRef(declaringFile),
            recursion = recursionType?.toDtoConfigTypeRef(declaringFile),
            fetchType = DtoFetchType.valueOf(fetchType),
            limit = limit?.let { limit -> DtoLimit(limit.value, limit.offset) },
            batch = batch,
            depth = depth,
        )
    }

    private fun org.babyfish.jimmer.dto.compiler.ConfigTypeRef.toDtoConfigTypeRef(
        declaringFile: DtoFile,
    ): DtoConfigTypeRef {
        return DtoConfigTypeRef(
            typeId = LsiSymbolId.type(qualifiedName),
            location = LsiLocation(
                source = source(declaringFile),
                start = LsiPosition(line, column),
            ),
        )
    }

    private fun PropConfig.Predicate.toDtoPredicate(): DtoPredicate {
        return when (this) {
            is PropConfig.Predicate.And -> DtoPredicate.And(
                predicates.map { predicate -> predicate.toDtoPredicate() }
            )
            is PropConfig.Predicate.Or -> DtoPredicate.Or(
                predicates.map { predicate -> predicate.toDtoPredicate() }
            )
            is PropConfig.Predicate.Cmp<*> -> DtoPredicate.Comparison(
                path = path.map { pathNode -> pathNode.castPathNode().toDtoPathNode() },
                operator = DtoComparisonOperator.fromToken(operator),
                value = value.toDtoConfigValue(),
            )
            is PropConfig.Predicate.Nullity<*> -> DtoPredicate.Nullity(
                path = path.map { pathNode -> pathNode.castPathNode().toDtoPathNode() },
                negative = isNegative,
            )
            else -> error("Unsupported DTO predicate implementation: ${javaClass.name}")
        }
    }

    private fun PropConfig.PathNode<ImmutableProp>.toDtoPathNode(): DtoPropPathNode {
        return DtoPropPathNode(
            propId = prop.id,
            associatedId = isAssociatedId,
        )
    }

    private fun Any.toDtoConfigValue(): DtoConfigValue {
        return when (this) {
            is Boolean -> DtoConfigValue.BooleanValue(this)
            is Long -> DtoConfigValue.LongValue(this)
            is BigInteger -> DtoConfigValue.BigIntegerValue(toString())
            is BigDecimal -> DtoConfigValue.DecimalValue(toString())
            is String -> DtoConfigValue.StringValue(this)
            else -> error("Unsupported DTO property config value: ${javaClass.name}")
        }
    }

    private fun AstDtoModifier.toDtoModifier(): DtoModifier = DtoModifier.valueOf(name)

    private fun AstDtoTypeKind.toDtoReusableTypeKind(): DtoReusableTypeKind =
        DtoReusableTypeKind.valueOf(name)

    private fun LikeOption.toDtoLikeOption(): DtoLikeOption = DtoLikeOption.valueOf(name)

    private fun AstDtoPolymorphicBranch.Kind.toDtoBranchKind(): DtoPolymorphicBranchKind =
        DtoPolymorphicBranchKind.valueOf(name)

    private fun location(
        declaringFile: DtoFile,
        line: Int,
        zeroBasedColumn: Int,
    ): LsiLocation {
        require(line >= 1) { "DTO source line must be positive: $line" }
        require(zeroBasedColumn >= 0) { "DTO source column cannot be negative: $zeroBasedColumn" }
        return LsiLocation(
            source = source(declaringFile),
            start = LsiPosition(line, zeroBasedColumn + 1),
        )
    }

    private fun source(declaringFile: DtoFile): LsiSource {
        val declaringSource = LsiSource.of(declaringFile.sourcePath)
        return if (declaringSource.path == graphSource.path) {
            graphSource
        } else {
            declaringSource
        }
    }
}

private fun AstDtoType<ImmutableType, ImmutableProp>.effectiveDocumentation(): String? {
    return doc.parseLsiDocumentation()?.canonicalText()
        ?: baseType?.documentation.parseLsiDocumentation()?.canonicalText()
}

private fun AstDtoType<ImmutableType, ImmutableProp>.effectiveDocumentation(
    prop: AbstractProp,
): String? {
    return effectiveDocumentation(prop, dtoDocumentation(prop))
}

private fun AstDtoType<ImmutableType, ImmutableProp>.effectiveDocumentation(
    prop: AbstractProp,
    dtoDocumentation: String?,
): String? {
    dtoDocumentation?.let { documentation -> return documentation }
    val baseProp = (prop as? AstDtoProp<*, *>)
        ?.castBaseProp()
        ?.toTailProp()
        ?.baseProp
    baseProp?.documentation.parseLsiDocumentation()?.canonicalText()?.let { documentation ->
        return documentation
    }
    return baseProp?.let { immutableProp ->
        baseType?.documentation.parseLsiDocumentation()
            ?.parameterValues
            ?.get(immutableProp.name)
    }
}

private fun AstDtoType<ImmutableType, ImmutableProp>.dtoDocumentation(
    prop: AbstractProp,
): String? {
    prop.doc.parseLsiDocumentation()?.canonicalText()?.let { documentation -> return documentation }
    val baseProp = (prop as? AstDtoProp<*, *>)
        ?.castBaseProp()
        ?.toTailProp()
        ?.baseProp
    val parameterName = prop.alias ?: baseProp?.name ?: return null
    return doc.parseLsiDocumentation()?.parameterValues?.get(parameterName)
}

@Suppress("UNCHECKED_CAST")
private fun AstDtoProp<*, *>.castBaseProp(): AstDtoProp<ImmutableType, ImmutableProp> =
    this as AstDtoProp<ImmutableType, ImmutableProp>

@Suppress("UNCHECKED_CAST")
private fun FoldProp<*, *>.castFoldProp(): FoldProp<ImmutableType, ImmutableProp> =
    this as FoldProp<ImmutableType, ImmutableProp>

@Suppress("UNCHECKED_CAST")
private fun PropConfig.PathNode<*>.castPathNode(): PropConfig.PathNode<ImmutableProp> =
    this as PropConfig.PathNode<ImmutableProp>

private fun Int.stableIndex(): String = toString().padStart(8, '0')
