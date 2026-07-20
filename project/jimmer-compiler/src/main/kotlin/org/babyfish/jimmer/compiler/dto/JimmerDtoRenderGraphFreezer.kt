package org.babyfish.jimmer.compiler.dto

import java.math.BigDecimal
import java.math.BigInteger
import java.util.IdentityHashMap
import org.babyfish.jimmer.client.meta.Doc
import org.babyfish.jimmer.compiler.CompilerInputDocumentSnapshot
import org.babyfish.jimmer.dto.compiler.AbstractProp
import org.babyfish.jimmer.dto.compiler.Anno
import org.babyfish.jimmer.dto.compiler.DtoFile
import org.babyfish.jimmer.dto.compiler.DtoModifier
import org.babyfish.jimmer.dto.compiler.DtoPolymorphicBranch
import org.babyfish.jimmer.dto.compiler.DtoProp
import org.babyfish.jimmer.dto.compiler.DtoType
import org.babyfish.jimmer.dto.compiler.EnumType
import org.babyfish.jimmer.dto.compiler.FoldProp
import org.babyfish.jimmer.dto.compiler.LikeOption
import org.babyfish.jimmer.dto.compiler.PropConfig
import org.babyfish.jimmer.dto.compiler.TypeRef
import org.babyfish.jimmer.dto.compiler.UserProp
import site.addzero.lsi.core.LsiLocation
import site.addzero.lsi.core.LsiPosition
import site.addzero.lsi.core.LsiSource
import site.addzero.lsi.core.LsiSymbolId

internal class JimmerDtoRenderGraphFreezer(
    private val inputSnapshot: CompilerInputDocumentSnapshot,
) {
    private val typeIds = IdentityHashMap<DtoType<LsiDtoBaseType, LsiDtoBaseProp>, JimmerDtoTypeId>()

    private val propIdsByOwner = mutableMapOf<
        JimmerDtoTypeId,
        IdentityHashMap<AbstractProp, JimmerDtoPropId>,
        >()

    private val types = mutableMapOf<JimmerDtoTypeId, JimmerDtoType>()

    private val props = mutableMapOf<JimmerDtoPropId, JimmerDtoProp>()

    fun freeze(
        compiledTypes: List<DtoType<LsiDtoBaseType, LsiDtoBaseProp>>,
    ): JimmerDtoRenderGraph {
        val rootTypeIds = compiledTypes.mapIndexed { index, dtoType ->
            freezeType(
                dtoType = dtoType,
                path = "root:${index.stableIndex()}:${dtoType.name.orEmpty()}",
                location = location(dtoType.dtoFile, 1, 0),
            )
        }
        return JimmerDtoRenderGraph(
            source = inputSnapshot.document.source,
            rootTypeIds = rootTypeIds,
            types = types.values.sortedBy(JimmerDtoType::id),
            props = props.values.sortedBy(JimmerDtoProp::id),
        )
    }

    private fun freezeType(
        dtoType: DtoType<LsiDtoBaseType, LsiDtoBaseProp>,
        path: String,
        location: LsiLocation,
    ): JimmerDtoTypeId {
        typeIds[dtoType]?.let { typeId -> return typeId }
        val typeId = JimmerDtoTypeId("${inputSnapshot.document.source.path}#$path")
        require(typeId !in types && typeId !in typeIds.values) {
            "Duplicate DTO render type id: ${typeId.value}"
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
        val type = JimmerDtoType(
            id = typeId,
            baseTypeId = dtoType.baseType?.id,
            packageName = dtoType.packageName,
            name = dtoType.name,
            modifiers = dtoType.modifiers
                .sortedWith(compareBy(DtoModifier::getOrder, DtoModifier::name))
                .mapTo(linkedSetOf()) { modifier -> modifier.toRenderModifier() },
            annotations = dtoType.annotations.map { annotation ->
                annotation.toRenderAnnotation(dtoType.dtoFile)
            },
            superInterfaces = dtoType.superInterfaces.map { typeRef ->
                typeRef.toRenderTypeRef(dtoType.dtoFile)
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
        ownerType: DtoType<LsiDtoBaseType, LsiDtoBaseProp>,
        ownerTypeId: JimmerDtoTypeId,
        path: String,
    ): JimmerDtoPropId {
        val ownerPropIds = propIdsByOwner.getOrPut(ownerTypeId, ::IdentityHashMap)
        ownerPropIds[prop]?.let { propId -> return propId }
        val propId = JimmerDtoPropId("${inputSnapshot.document.source.path}#$path")
        require(propId !in props && propId !in ownerPropIds.values) {
            "Duplicate DTO render property id: ${propId.value}"
        }
        ownerPropIds[prop] = propId
        val frozenProp = when (prop) {
            is DtoProp<*, *> -> freezeBaseProp(prop.castBaseProp(), ownerType, propId, ownerTypeId, path)
            is UserProp -> freezeUserProp(prop, ownerType, propId, ownerTypeId)
            is FoldProp<*, *> -> freezeFoldProp(prop.castFoldProp(), ownerType, propId, ownerTypeId, path)
            else -> error("Unsupported DTO property implementation: ${prop.javaClass.name}")
        }
        props[propId] = frozenProp
        return propId
    }

    private fun freezeBaseProp(
        prop: DtoProp<LsiDtoBaseType, LsiDtoBaseProp>,
        ownerType: DtoType<LsiDtoBaseType, LsiDtoBaseProp>,
        propId: JimmerDtoPropId,
        ownerTypeId: JimmerDtoTypeId,
        path: String,
    ): JimmerDtoBaseProp {
        val nextPropId = prop.nextProp?.let { nextProp ->
            freezeProp(nextProp, ownerType, ownerTypeId, "$path/next:${nextProp.name}")
        }
        val tailProp = prop.toTailProp()
        val tailPropId = if (tailProp === prop) {
            propId
        } else {
            freezeProp(tailProp, ownerType, ownerTypeId, "$path/tail:${tailProp.name}")
        }
        val targetType = prop.targetType ?: prop.targetTypeRef?.sourceType
        val targetTypeId = targetType?.let {
            freezeType(
                dtoType = it,
                path = "$path/target:${it.name.orEmpty()}",
                location = location(it.dtoFile, prop.aliasLine, prop.aliasColumn),
            )
        }
        return JimmerDtoBaseProp(
            id = propId,
            ownerTypeId = ownerTypeId,
            name = prop.name,
            alias = prop.alias,
            nullable = prop.isNullable,
            annotations = prop.annotations.map { annotation ->
                annotation.toRenderAnnotation(prop.declaringFile)
            },
            documentation = ownerType.effectiveDocumentation(prop),
            aliasLocation = location(prop.declaringFile, prop.aliasLine, prop.aliasColumn),
            baseLocation = location(prop.declaringFile, prop.baseLine, prop.baseColumn),
            baseProps = prop.basePropMap.entries.map { (name, baseProp) ->
                JimmerDtoBasePropBinding(name, baseProp.id)
            },
            basePath = prop.basePath,
            nextPropId = nextPropId,
            tailPropId = tailPropId,
            baseNullable = prop.isBaseNullable,
            inputModifier = requireNotNull(prop.inputModifier) {
                "DTO base property must declare an input modifier: ${prop.name}"
            }.toRenderModifier(),
            functionName = prop.funcName,
            targetTypeId = targetTypeId,
            enumType = prop.enumType?.toRenderEnumType(),
            config = prop.config?.toRenderConfig(prop.declaringFile),
            recursive = prop.isRecursive,
            likeOptions = prop.likeOptions
                .sortedBy(LikeOption::name)
                .mapTo(linkedSetOf()) { option -> option.toRenderLikeOption() },
        )
    }

    private fun freezeUserProp(
        prop: UserProp,
        ownerType: DtoType<LsiDtoBaseType, LsiDtoBaseProp>,
        propId: JimmerDtoPropId,
        ownerTypeId: JimmerDtoTypeId,
    ): JimmerDtoUserProp {
        return JimmerDtoUserProp(
            id = propId,
            ownerTypeId = ownerTypeId,
            name = prop.name,
            alias = prop.alias,
            nullable = prop.isNullable,
            annotations = prop.annotations.map { annotation ->
                annotation.toRenderAnnotation(prop.declaringFile)
            },
            documentation = ownerType.effectiveDocumentation(prop),
            aliasLocation = location(prop.declaringFile, prop.aliasLine, prop.aliasColumn),
            type = prop.typeRef.toRenderTypeRef(prop.declaringFile),
            defaultValueText = prop.defaultValueText,
        )
    }

    private fun freezeFoldProp(
        prop: FoldProp<LsiDtoBaseType, LsiDtoBaseProp>,
        ownerType: DtoType<LsiDtoBaseType, LsiDtoBaseProp>,
        propId: JimmerDtoPropId,
        ownerTypeId: JimmerDtoTypeId,
        path: String,
    ): JimmerDtoFoldProp {
        val nullGuardPropId = prop.nullGuardProp?.let { nullGuardProp ->
            freezeProp(nullGuardProp, ownerType, ownerTypeId, "$path/null-guard:${nullGuardProp.name}")
        }
        val targetTypeId = freezeType(
            dtoType = prop.targetType,
            path = "$path/target:${prop.targetType.name.orEmpty()}",
            location = location(prop.targetType.dtoFile, prop.aliasLine, prop.aliasColumn),
        )
        return JimmerDtoFoldProp(
            id = propId,
            ownerTypeId = ownerTypeId,
            name = prop.name,
            alias = prop.alias,
            nullable = prop.isNullable,
            annotations = prop.annotations.map { annotation ->
                annotation.toRenderAnnotation(prop.declaringFile)
            },
            documentation = ownerType.effectiveDocumentation(prop),
            aliasLocation = location(prop.declaringFile, prop.aliasLine, prop.aliasColumn),
            nullGuardPropId = nullGuardPropId,
            targetTypeId = targetTypeId,
        )
    }

    private fun freezePolymorphism(
        rootType: DtoType<LsiDtoBaseType, LsiDtoBaseProp>,
        rootTypeId: JimmerDtoTypeId,
        rootPath: String,
        polymorphism: org.babyfish.jimmer.dto.compiler.DtoPolymorphism<LsiDtoBaseType, LsiDtoBaseProp>,
    ): JimmerDtoPolymorphism {
        val branches = buildList {
            polymorphism.defaultBranch?.let { branch ->
                add(freezeBranch(rootType, rootTypeId, rootPath, branch, 0))
            }
            polymorphism.typeBranches.forEachIndexed { index, branch ->
                add(freezeBranch(rootType, rootTypeId, rootPath, branch, index))
            }
        }
        return JimmerDtoPolymorphism(
            exhaustive = polymorphism.isExhaustive,
            branches = branches,
        )
    }

    private fun freezeBranch(
        rootType: DtoType<LsiDtoBaseType, LsiDtoBaseProp>,
        rootTypeId: JimmerDtoTypeId,
        rootPath: String,
        branch: DtoPolymorphicBranch<LsiDtoBaseType, LsiDtoBaseProp>,
        index: Int,
    ): JimmerDtoPolymorphicBranch {
        val kind = branch.kind.toRenderBranchKind()
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
        return JimmerDtoPolymorphicBranch(
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

    private fun TypeRef.toRenderTypeRef(declaringFile: DtoFile): JimmerDtoTypeRef {
        return JimmerDtoTypeRef(
            typeName = typeName,
            arguments = arguments.map { argument ->
                val variance = when {
                    argument.typeRef == null -> JimmerDtoVariance.STAR
                    argument.isIn -> JimmerDtoVariance.IN
                    argument.isOut -> JimmerDtoVariance.OUT
                    else -> JimmerDtoVariance.INVARIANT
                }
                JimmerDtoTypeArgument(
                    variance = variance,
                    type = argument.typeRef?.toRenderTypeRef(declaringFile),
                )
            },
            nullable = isNullable,
            location = location(declaringFile, line, col),
        )
    }

    private fun Anno.toRenderAnnotation(declaringFile: DtoFile): JimmerDtoAnnotation {
        return JimmerDtoAnnotation(
            typeId = LsiSymbolId.type(qualifiedName),
            arguments = valueMap.entries.map { (name, value) ->
                JimmerDtoAnnotationArgument(name, value.toRenderAnnotationValue(declaringFile))
            },
        )
    }

    private fun Anno.Value.toRenderAnnotationValue(declaringFile: DtoFile): JimmerDtoAnnotationValue {
        return when (this) {
            is Anno.ArrayValue -> JimmerDtoAnnotationValue.ArrayValue(
                elements.map { element -> element.toRenderAnnotationValue(declaringFile) }
            )
            is Anno.AnnoValue -> JimmerDtoAnnotationValue.AnnotationValue(
                anno.toRenderAnnotation(declaringFile)
            )
            is Anno.EnumValue -> JimmerDtoAnnotationValue.EnumValue(
                enumTypeId = LsiSymbolId.type(qualifiedName),
                constant = constant,
            )
            is Anno.TypeRefValue -> JimmerDtoAnnotationValue.TypeValue(
                typeRef.toRenderTypeRef(declaringFile)
            )
            is Anno.LiteralValue -> JimmerDtoAnnotationValue.LiteralValue(value)
            else -> error("Unsupported DTO annotation value implementation: ${javaClass.name}")
        }
    }

    private fun EnumType.toRenderEnumType(): JimmerDtoEnumType {
        return JimmerDtoEnumType(
            numeric = isNumeric,
            mappings = valueMap.entries.map { (constant, value) ->
                JimmerDtoEnumMapping(constant, value)
            },
        )
    }

    private fun PropConfig<LsiDtoBaseProp>.toRenderConfig(
        declaringFile: DtoFile,
    ): JimmerDtoPropConfig {
        return JimmerDtoPropConfig(
            predicate = predicate?.toRenderPredicate(),
            orderItems = orderItems.map { orderItem ->
                JimmerDtoOrderItem(
                    path = orderItem.path.map { pathNode -> pathNode.toRenderPathNode() },
                    descending = orderItem.isDesc,
                )
            },
            filter = filterType?.toRenderConfigTypeRef(declaringFile),
            recursion = recursionType?.toRenderConfigTypeRef(declaringFile),
            fetchType = JimmerDtoFetchType.valueOf(fetchType),
            limit = limit,
            offset = offset,
            batch = batch,
            depth = depth,
        )
    }

    private fun org.babyfish.jimmer.dto.compiler.ConfigTypeRef.toRenderConfigTypeRef(
        declaringFile: DtoFile,
    ): JimmerDtoConfigTypeRef {
        return JimmerDtoConfigTypeRef(
            typeId = LsiSymbolId.type(qualifiedName),
            location = LsiLocation(
                source = source(declaringFile),
                start = LsiPosition(line, column),
            ),
        )
    }

    private fun PropConfig.Predicate.toRenderPredicate(): JimmerDtoPredicate {
        return when (this) {
            is PropConfig.Predicate.And -> JimmerDtoPredicate.And(
                predicates.map { predicate -> predicate.toRenderPredicate() }
            )
            is PropConfig.Predicate.Or -> JimmerDtoPredicate.Or(
                predicates.map { predicate -> predicate.toRenderPredicate() }
            )
            is PropConfig.Predicate.Cmp<*> -> JimmerDtoPredicate.Comparison(
                path = path.map { pathNode -> pathNode.castPathNode().toRenderPathNode() },
                operator = operator,
                value = value.toRenderConfigValue(),
            )
            is PropConfig.Predicate.Nullity<*> -> JimmerDtoPredicate.Nullity(
                path = path.map { pathNode -> pathNode.castPathNode().toRenderPathNode() },
                negative = isNegative,
            )
            else -> error("Unsupported DTO predicate implementation: ${javaClass.name}")
        }
    }

    private fun PropConfig.PathNode<LsiDtoBaseProp>.toRenderPathNode(): JimmerDtoPropPathNode {
        return JimmerDtoPropPathNode(
            propId = prop.id,
            associatedId = isAssociatedId,
        )
    }

    private fun Any.toRenderConfigValue(): JimmerDtoConfigValue {
        return when (this) {
            is Boolean -> JimmerDtoConfigValue.BooleanValue(this)
            is Long -> JimmerDtoConfigValue.LongValue(this)
            is BigInteger -> JimmerDtoConfigValue.BigIntegerValue(toString())
            is BigDecimal -> JimmerDtoConfigValue.DecimalValue(toString())
            is String -> JimmerDtoConfigValue.StringValue(this)
            else -> error("Unsupported DTO property config value: ${javaClass.name}")
        }
    }

    private fun DtoModifier.toRenderModifier(): JimmerDtoModifier = JimmerDtoModifier.valueOf(name)

    private fun LikeOption.toRenderLikeOption(): JimmerDtoLikeOption = JimmerDtoLikeOption.valueOf(name)

    private fun DtoPolymorphicBranch.Kind.toRenderBranchKind(): JimmerDtoPolymorphicBranchKind =
        JimmerDtoPolymorphicBranchKind.valueOf(name)

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
        val graphSource = inputSnapshot.document.source
        return if (declaringFile.absolutePath == graphSource.path) {
            graphSource
        } else {
            LsiSource.of(declaringFile.absolutePath)
        }
    }
}

private fun DtoType<LsiDtoBaseType, LsiDtoBaseProp>.effectiveDocumentation(): String? {
    return Doc.parse(doc)?.toString()
        ?: Doc.parse(baseType?.immutableType?.documentation)?.toString()
}

private fun DtoType<LsiDtoBaseType, LsiDtoBaseProp>.effectiveDocumentation(
    prop: AbstractProp,
): String? {
    Doc.parse(prop.doc)?.toString()?.let { documentation -> return documentation }
    val baseProp = (prop as? DtoProp<*, *>)
        ?.castBaseProp()
        ?.toTailProp()
        ?.baseProp
    val parameterName = prop.alias ?: baseProp?.name
    if (parameterName != null) {
        Doc.parse(doc)?.parameterValueMap?.get(parameterName)?.let { documentation ->
            return documentation
        }
    }
    baseProp?.immutableProp?.documentation?.let(Doc::parse)?.toString()?.let { documentation ->
        return documentation
    }
    return baseProp?.let { immutableProp ->
        Doc.parse(baseType?.immutableType?.documentation)
            ?.parameterValueMap
            ?.get(immutableProp.name)
    }
}

@Suppress("UNCHECKED_CAST")
private fun DtoProp<*, *>.castBaseProp(): DtoProp<LsiDtoBaseType, LsiDtoBaseProp> =
    this as DtoProp<LsiDtoBaseType, LsiDtoBaseProp>

@Suppress("UNCHECKED_CAST")
private fun FoldProp<*, *>.castFoldProp(): FoldProp<LsiDtoBaseType, LsiDtoBaseProp> =
    this as FoldProp<LsiDtoBaseType, LsiDtoBaseProp>

@Suppress("UNCHECKED_CAST")
private fun PropConfig.PathNode<*>.castPathNode(): PropConfig.PathNode<LsiDtoBaseProp> =
    this as PropConfig.PathNode<LsiDtoBaseProp>

private fun Int.stableIndex(): String = toString().padStart(8, '0')
