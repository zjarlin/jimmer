package org.babyfish.jimmer.compiler.dto

import site.addzero.lsi.core.LsiLocation
import site.addzero.lsi.core.LsiSource
import site.addzero.lsi.core.LsiSymbolId

@JvmInline
internal value class JimmerDtoTypeId(
    val value: String,
) : Comparable<JimmerDtoTypeId> {
    init {
        require(value.isNotBlank()) { "DTO type id cannot be blank" }
        require(value == value.trim()) { "DTO type id cannot have surrounding whitespace: '$value'" }
    }

    override fun compareTo(other: JimmerDtoTypeId): Int = value.compareTo(other.value)

    override fun toString(): String = value
}

@JvmInline
internal value class JimmerDtoPropId(
    val value: String,
) : Comparable<JimmerDtoPropId> {
    init {
        require(value.isNotBlank()) { "DTO property id cannot be blank" }
        require(value == value.trim()) { "DTO property id cannot have surrounding whitespace: '$value'" }
    }

    override fun compareTo(other: JimmerDtoPropId): Int = value.compareTo(other.value)

    override fun toString(): String = value
}

internal data class JimmerDtoRenderGraph(
    val source: LsiSource,
    val rootTypeIds: List<JimmerDtoTypeId>,
    val types: List<JimmerDtoType>,
    val props: List<JimmerDtoProp>,
) {
    val typesById: Map<JimmerDtoTypeId, JimmerDtoType> = types.associateBy(JimmerDtoType::id)

    val propsById: Map<JimmerDtoPropId, JimmerDtoProp> = props.associateBy(JimmerDtoProp::id)

    val originatingSources: Set<LsiSource> = buildSet {
        add(source)
        types.forEach { type ->
            add(type.location.source)
            type.annotations.forEach { annotation -> addAnnotationSources(annotation) }
            type.superInterfaces.forEach { typeRef -> addTypeRefSources(typeRef) }
            type.polymorphism?.branches.orEmpty().forEach { branch -> add(branch.location.source) }
        }
        props.forEach { prop ->
            add(prop.aliasLocation.source)
            prop.annotations.forEach { annotation -> addAnnotationSources(annotation) }
            when (prop) {
                is JimmerDtoBaseProp -> {
                    add(prop.baseLocation.source)
                    prop.config?.filter?.let { filter -> add(filter.location.source) }
                    prop.config?.recursion?.let { recursion -> add(recursion.location.source) }
                }
                is JimmerDtoUserProp -> addTypeRefSources(prop.type)
                is JimmerDtoFoldProp -> Unit
            }
        }
    }.toSortedSet()

    init {
        require(rootTypeIds == rootTypeIds.distinct()) { "DTO render graph root type ids must be distinct" }
        require(types == types.sortedBy(JimmerDtoType::id)) { "DTO render graph types must use stable id order" }
        require(props == props.sortedBy(JimmerDtoProp::id)) { "DTO render graph properties must use stable id order" }
        require(typesById.size == types.size) { "DTO render graph cannot contain duplicate type ids" }
        require(propsById.size == props.size) { "DTO render graph cannot contain duplicate property ids" }
        require(rootTypeIds.all(typesById::containsKey)) { "DTO render graph root type must exist" }
        require(source in originatingSources) {
            "DTO render graph source must be one of its originating sources"
        }
        types.forEach(::validateType)
        props.forEach(::validateProp)
    }

    private fun validateType(type: JimmerDtoType) {
        require(type.propIds.distinct().size == type.propIds.size) {
            "DTO type property ids must be distinct: ${type.id.value}"
        }
        require(type.hiddenFlatPropIds.distinct().size == type.hiddenFlatPropIds.size) {
            "DTO type hidden flat property ids must be distinct: ${type.id.value}"
        }
        require((type.propIds + type.hiddenFlatPropIds).all(propsById::containsKey)) {
            "DTO type property must exist in the render graph: ${type.id.value}"
        }
        require((type.propIds + type.hiddenFlatPropIds).all { propId ->
            propsById.getValue(propId).ownerTypeId == type.id
        }) {
            "DTO type property owner must match the containing type: ${type.id.value}"
        }
        validateAnnotations(type.annotations)
        type.superInterfaces.forEach(::validateTypeRef)
        type.polymorphism?.branches.orEmpty().forEach { branch ->
            require(typesById.containsKey(branch.bodyTypeId)) {
                "DTO polymorphic branch body type must exist: ${branch.bodyTypeId.value}"
            }
            require(typesById.containsKey(branch.mergedTypeId)) {
                "DTO polymorphic branch merged type must exist: ${branch.mergedTypeId.value}"
            }
        }
    }

    private fun validateProp(prop: JimmerDtoProp) {
        require(typesById.containsKey(prop.ownerTypeId)) {
            "DTO property owner type must exist: ${prop.id.value}"
        }
        validateAnnotations(prop.annotations)
        when (prop) {
            is JimmerDtoBaseProp -> {
                require(prop.nextPropId == null || propsById.containsKey(prop.nextPropId)) {
                    "DTO next property must exist: ${prop.id.value}"
                }
                require(prop.nextPropId == null || propsById.getValue(prop.nextPropId).ownerTypeId == prop.ownerTypeId) {
                    "DTO next property must use the same owner: ${prop.id.value}"
                }
                require(propsById.containsKey(prop.tailPropId)) {
                    "DTO tail property must exist: ${prop.id.value}"
                }
                require(propsById.getValue(prop.tailPropId).ownerTypeId == prop.ownerTypeId) {
                    "DTO tail property must use the same owner: ${prop.id.value}"
                }
                require(prop.targetTypeId == null || typesById.containsKey(prop.targetTypeId)) {
                    "DTO target type must exist: ${prop.id.value}"
                }
            }
            is JimmerDtoFoldProp -> {
                require(typesById.containsKey(prop.targetTypeId)) {
                    "DTO fold target type must exist: ${prop.id.value}"
                }
                require(prop.nullGuardPropId == null || propsById.containsKey(prop.nullGuardPropId)) {
                    "DTO fold null guard property must exist: ${prop.id.value}"
                }
                require(
                    prop.nullGuardPropId == null ||
                        propsById.getValue(prop.nullGuardPropId).ownerTypeId == prop.ownerTypeId
                ) {
                    "DTO fold null guard property must use the same owner: ${prop.id.value}"
                }
            }
            is JimmerDtoUserProp -> validateTypeRef(prop.type)
        }
    }

    private fun validateAnnotations(annotations: List<JimmerDtoAnnotation>) {
        annotations.forEach { annotation ->
            annotation.arguments.forEach { argument -> validateAnnotationValue(argument.value) }
        }
    }

    private fun validateAnnotationValue(value: JimmerDtoAnnotationValue) {
        when (value) {
            is JimmerDtoAnnotationValue.ArrayValue -> value.elements.forEach(::validateAnnotationValue)
            is JimmerDtoAnnotationValue.AnnotationValue -> validateAnnotations(listOf(value.annotation))
            is JimmerDtoAnnotationValue.TypeValue -> validateTypeRef(value.type)
            is JimmerDtoAnnotationValue.EnumValue,
            is JimmerDtoAnnotationValue.LiteralValue,
            -> Unit
        }
    }

    private fun validateTypeRef(type: JimmerDtoTypeRef) {
        type.arguments.mapNotNull(JimmerDtoTypeArgument::type).forEach(::validateTypeRef)
    }

    private fun MutableSet<LsiSource>.addAnnotationSources(annotation: JimmerDtoAnnotation) {
        annotation.arguments.forEach { argument -> addAnnotationValueSources(argument.value) }
    }

    private fun MutableSet<LsiSource>.addAnnotationValueSources(value: JimmerDtoAnnotationValue) {
        when (value) {
            is JimmerDtoAnnotationValue.ArrayValue -> value.elements.forEach { element ->
                addAnnotationValueSources(element)
            }
            is JimmerDtoAnnotationValue.AnnotationValue -> addAnnotationSources(value.annotation)
            is JimmerDtoAnnotationValue.TypeValue -> addTypeRefSources(value.type)
            is JimmerDtoAnnotationValue.EnumValue,
            is JimmerDtoAnnotationValue.LiteralValue,
            -> Unit
        }
    }

    private fun MutableSet<LsiSource>.addTypeRefSources(type: JimmerDtoTypeRef) {
        add(type.location.source)
        type.arguments.mapNotNull(JimmerDtoTypeArgument::type).forEach { argumentType ->
            addTypeRefSources(argumentType)
        }
    }
}

internal data class JimmerDtoType(
    val id: JimmerDtoTypeId,
    val baseTypeId: LsiSymbolId?,
    val packageName: String,
    val name: String?,
    val modifiers: Set<JimmerDtoModifier>,
    val annotations: List<JimmerDtoAnnotation>,
    val superInterfaces: List<JimmerDtoTypeRef>,
    val documentation: String?,
    val location: LsiLocation,
    val focusedRecursion: Boolean,
    val propIds: List<JimmerDtoPropId>,
    val hiddenFlatPropIds: List<JimmerDtoPropId>,
    val polymorphism: JimmerDtoPolymorphism?,
) {
    init {
        require(packageName == packageName.trim()) {
            "DTO type package name cannot have surrounding whitespace: '$packageName'"
        }
        require(name == null || name.isNotBlank()) { "DTO type name cannot be blank" }
        baseTypeId?.requireTypeQualifiedName()
    }
}

internal sealed interface JimmerDtoProp {
    val id: JimmerDtoPropId
    val ownerTypeId: JimmerDtoTypeId
    val name: String
    val alias: String?
    val nullable: Boolean
    val annotations: List<JimmerDtoAnnotation>
    val documentation: String?
    val aliasLocation: LsiLocation
}

internal data class JimmerDtoBaseProp(
    override val id: JimmerDtoPropId,
    override val ownerTypeId: JimmerDtoTypeId,
    override val name: String,
    override val alias: String?,
    override val nullable: Boolean,
    override val annotations: List<JimmerDtoAnnotation>,
    override val documentation: String?,
    override val aliasLocation: LsiLocation,
    val baseLocation: LsiLocation,
    val baseProps: List<JimmerDtoBasePropBinding>,
    val basePath: String,
    val nextPropId: JimmerDtoPropId?,
    val tailPropId: JimmerDtoPropId,
    val baseNullable: Boolean,
    val inputModifier: JimmerDtoModifier,
    val functionName: String?,
    val targetTypeId: JimmerDtoTypeId?,
    val enumType: JimmerDtoEnumType?,
    val config: JimmerDtoPropConfig?,
    val recursive: Boolean,
    val likeOptions: Set<JimmerDtoLikeOption>,
) : JimmerDtoProp {
    init {
        require(baseProps.isNotEmpty()) { "DTO base property must reference at least one immutable property" }
        require(baseProps.map(JimmerDtoBasePropBinding::name).distinct().size == baseProps.size) {
            "DTO base property bindings cannot contain duplicate names: ${id.value}"
        }
        require(basePath.isNotBlank()) { "DTO base property path cannot be blank: ${id.value}" }
        require(inputModifier.isInputStrategy) {
            "DTO base property input modifier must be an input strategy: ${inputModifier.name}"
        }
    }
}

internal data class JimmerDtoUserProp(
    override val id: JimmerDtoPropId,
    override val ownerTypeId: JimmerDtoTypeId,
    override val name: String,
    override val alias: String,
    override val nullable: Boolean,
    override val annotations: List<JimmerDtoAnnotation>,
    override val documentation: String?,
    override val aliasLocation: LsiLocation,
    val type: JimmerDtoTypeRef,
    val defaultValueText: String?,
) : JimmerDtoProp

internal data class JimmerDtoFoldProp(
    override val id: JimmerDtoPropId,
    override val ownerTypeId: JimmerDtoTypeId,
    override val name: String,
    override val alias: String,
    override val nullable: Boolean,
    override val annotations: List<JimmerDtoAnnotation>,
    override val documentation: String?,
    override val aliasLocation: LsiLocation,
    val nullGuardPropId: JimmerDtoPropId?,
    val targetTypeId: JimmerDtoTypeId,
) : JimmerDtoProp

internal data class JimmerDtoBasePropBinding(
    val name: String,
    val propId: LsiSymbolId,
) {
    init {
        require(name.isNotBlank()) { "DTO base property binding name cannot be blank" }
    }
}

internal enum class JimmerDtoModifier(
    val isInputStrategy: Boolean,
    val order: Int,
) {
    INPUT(false, 2),
    SPECIFICATION(false, 2),
    SEALED(false, -1),
    UNSAFE(false, 0),
    FIXED(true, 1),
    STATIC(true, 1),
    DYNAMIC(true, 1),
    FUZZY(true, 1),
}

internal enum class JimmerDtoLikeOption {
    INSENSITIVE,
    MATCH_START,
    MATCH_END,
}

internal data class JimmerDtoTypeRef(
    val typeName: String,
    val arguments: List<JimmerDtoTypeArgument>,
    val nullable: Boolean,
    val location: LsiLocation,
) {
    init {
        require(typeName.isNotBlank()) { "DTO type reference name cannot be blank" }
    }
}

internal data class JimmerDtoTypeArgument(
    val variance: JimmerDtoVariance,
    val type: JimmerDtoTypeRef?,
) {
    init {
        require((variance == JimmerDtoVariance.STAR) == (type == null)) {
            "Only star-projected DTO type argument can omit its type"
        }
    }
}

internal enum class JimmerDtoVariance {
    INVARIANT,
    IN,
    OUT,
    STAR,
}

internal data class JimmerDtoAnnotation(
    val typeId: LsiSymbolId,
    val arguments: List<JimmerDtoAnnotationArgument>,
) {
    init {
        typeId.requireTypeQualifiedName()
        require(arguments.map(JimmerDtoAnnotationArgument::name).distinct().size == arguments.size) {
            "DTO annotation cannot contain duplicate argument names: ${typeId.value}"
        }
    }
}

internal data class JimmerDtoAnnotationArgument(
    val name: String,
    val value: JimmerDtoAnnotationValue,
) {
    init {
        require(name.isNotBlank()) { "DTO annotation argument name cannot be blank" }
    }
}

internal sealed interface JimmerDtoAnnotationValue {
    data class ArrayValue(val elements: List<JimmerDtoAnnotationValue>) : JimmerDtoAnnotationValue

    data class AnnotationValue(val annotation: JimmerDtoAnnotation) : JimmerDtoAnnotationValue

    data class EnumValue(
        val enumTypeId: LsiSymbolId,
        val constant: String,
    ) : JimmerDtoAnnotationValue {
        init {
            enumTypeId.requireTypeQualifiedName()
            require(constant.isNotBlank()) { "DTO annotation enum constant cannot be blank" }
        }
    }

    data class TypeValue(val type: JimmerDtoTypeRef) : JimmerDtoAnnotationValue

    data class LiteralValue(val code: String) : JimmerDtoAnnotationValue {
        init {
            require(code.isNotBlank()) { "DTO annotation literal code cannot be blank" }
        }
    }
}

internal data class JimmerDtoEnumType(
    val numeric: Boolean,
    val mappings: List<JimmerDtoEnumMapping>,
) {
    init {
        require(mappings.isNotEmpty()) { "DTO enum mapping cannot be empty" }
        require(mappings.map(JimmerDtoEnumMapping::constant).distinct().size == mappings.size) {
            "DTO enum mapping cannot contain duplicate constants"
        }
        require(mappings.map(JimmerDtoEnumMapping::value).distinct().size == mappings.size) {
            "DTO enum mapping cannot contain duplicate values"
        }
    }
}

internal data class JimmerDtoEnumMapping(
    val constant: String,
    val value: String,
) {
    init {
        require(constant.isNotBlank()) { "DTO enum mapping constant cannot be blank" }
        require(value.isNotBlank()) { "DTO enum mapping value cannot be blank" }
    }
}

internal data class JimmerDtoPropConfig(
    val predicate: JimmerDtoPredicate?,
    val orderItems: List<JimmerDtoOrderItem>,
    val filter: JimmerDtoConfigTypeRef?,
    val recursion: JimmerDtoConfigTypeRef?,
    val fetchType: JimmerDtoFetchType,
    val limit: Int,
    val offset: Int,
    val batch: Int,
    val depth: Int,
) {
    init {
        require(limit >= 0) { "DTO property config limit cannot be negative" }
        require(offset >= 0) { "DTO property config offset cannot be negative" }
        require(batch >= 0) { "DTO property config batch cannot be negative" }
        require(depth >= 0) { "DTO property config depth cannot be negative" }
    }
}

internal data class JimmerDtoConfigTypeRef(
    val typeId: LsiSymbolId,
    val location: LsiLocation,
) {
    init {
        typeId.requireTypeQualifiedName()
    }
}

internal enum class JimmerDtoFetchType {
    AUTO,
    SELECT,
    JOIN_IF_NO_CACHE,
    JOIN_ALWAYS,
}

internal sealed interface JimmerDtoPredicate {
    data class And(val predicates: List<JimmerDtoPredicate>) : JimmerDtoPredicate {
        init {
            require(predicates.isNotEmpty()) { "DTO conjunction cannot be empty" }
        }
    }

    data class Or(val predicates: List<JimmerDtoPredicate>) : JimmerDtoPredicate {
        init {
            require(predicates.isNotEmpty()) { "DTO disjunction cannot be empty" }
        }
    }

    data class Comparison(
        val path: List<JimmerDtoPropPathNode>,
        val operator: String,
        val value: JimmerDtoConfigValue,
    ) : JimmerDtoPredicate {
        init {
            require(path.isNotEmpty()) { "DTO comparison path cannot be empty" }
            require(operator.isNotBlank()) { "DTO comparison operator cannot be blank" }
        }
    }

    data class Nullity(
        val path: List<JimmerDtoPropPathNode>,
        val negative: Boolean,
    ) : JimmerDtoPredicate {
        init {
            require(path.isNotEmpty()) { "DTO nullity path cannot be empty" }
        }
    }
}

internal sealed interface JimmerDtoConfigValue {
    data class BooleanValue(val value: Boolean) : JimmerDtoConfigValue

    data class LongValue(val value: Long) : JimmerDtoConfigValue

    data class BigIntegerValue(val value: String) : JimmerDtoConfigValue

    data class DecimalValue(val value: String) : JimmerDtoConfigValue

    data class StringValue(val value: String) : JimmerDtoConfigValue
}

internal data class JimmerDtoOrderItem(
    val path: List<JimmerDtoPropPathNode>,
    val descending: Boolean,
) {
    init {
        require(path.isNotEmpty()) { "DTO order path cannot be empty" }
    }
}

internal data class JimmerDtoPropPathNode(
    val propId: LsiSymbolId,
    val associatedId: Boolean,
)

internal data class JimmerDtoPolymorphism(
    val exhaustive: Boolean,
    val branches: List<JimmerDtoPolymorphicBranch>,
) {
    init {
        require(branches.isNotEmpty()) { "DTO polymorphism must contain at least one branch" }
        require(branches.count { branch -> branch.kind == JimmerDtoPolymorphicBranchKind.DEFAULT } <= 1) {
            "DTO polymorphism cannot contain multiple default branches"
        }
    }
}

internal data class JimmerDtoPolymorphicBranch(
    val kind: JimmerDtoPolymorphicBranchKind,
    val targetBaseTypeId: LsiSymbolId?,
    val declaredClassName: String?,
    val className: String,
    val bodyTypeId: JimmerDtoTypeId,
    val mergedTypeId: JimmerDtoTypeId,
    val implicit: Boolean,
    val location: LsiLocation,
) {
    init {
        require(className.isNotBlank()) { "DTO polymorphic branch class name cannot be blank" }
        require(declaredClassName == null || declaredClassName.isNotBlank()) {
            "DTO polymorphic branch declared class name cannot be blank"
        }
        require((kind == JimmerDtoPolymorphicBranchKind.TYPE) == (targetBaseTypeId != null)) {
            "Only DTO type branch can reference a target base type"
        }
        targetBaseTypeId?.requireTypeQualifiedName()
    }
}

internal enum class JimmerDtoPolymorphicBranchKind {
    DEFAULT,
    TYPE,
}
