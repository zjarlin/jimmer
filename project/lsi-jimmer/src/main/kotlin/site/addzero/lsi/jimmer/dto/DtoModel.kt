package site.addzero.lsi.jimmer.dto

import site.addzero.lsi.core.LsiLocation
import site.addzero.lsi.core.LsiSource
import site.addzero.lsi.core.LsiSymbolId

@JvmInline
value class DtoTypeId(
    val value: String,
) : Comparable<DtoTypeId> {
    init {
        require(value.isNotBlank()) { "DTO type id cannot be blank" }
        require(value == value.trim()) { "DTO type id cannot have surrounding whitespace: '$value'" }
    }

    override fun compareTo(other: DtoTypeId): Int = value.compareTo(other.value)

    override fun toString(): String = value
}

@JvmInline
value class DtoPropId(
    val value: String,
) : Comparable<DtoPropId> {
    init {
        require(value.isNotBlank()) { "DTO property id cannot be blank" }
        require(value == value.trim()) { "DTO property id cannot have surrounding whitespace: '$value'" }
    }

    override fun compareTo(other: DtoPropId): Int = value.compareTo(other.value)

    override fun toString(): String = value
}

data class DtoGraph(
    val source: LsiSource,
    val rootTypeIds: List<DtoTypeId>,
    val types: List<DtoType>,
    val props: List<DtoProp>,
) {
    val typesById: Map<DtoTypeId, DtoType> = types.associateBy(DtoType::id)

    val propsById: Map<DtoPropId, DtoProp> = props.associateBy(DtoProp::id)

    init {
        require(rootTypeIds == rootTypeIds.distinct()) { "DTO graph root type ids must be distinct" }
        require(types == types.sortedBy(DtoType::id)) { "DTO graph types must use stable id order" }
        require(props == props.sortedBy(DtoProp::id)) { "DTO graph properties must use stable id order" }
        require(typesById.size == types.size) { "DTO graph cannot contain duplicate type ids" }
        require(propsById.size == props.size) { "DTO graph cannot contain duplicate property ids" }
        require(rootTypeIds.all(typesById::containsKey)) { "DTO graph root type must exist" }
        types.forEach(::validateType)
        props.forEach(::validateProp)
    }

    private fun validateType(type: DtoType) {
        require(type.propIds.distinct().size == type.propIds.size) {
            "DTO type property ids must be distinct: ${type.id.value}"
        }
        require(type.hiddenFlatPropIds.distinct().size == type.hiddenFlatPropIds.size) {
            "DTO type hidden flat property ids must be distinct: ${type.id.value}"
        }
        require((type.propIds + type.hiddenFlatPropIds).all(propsById::containsKey)) {
            "DTO type property must exist in the DTO graph: ${type.id.value}"
        }
        require((type.propIds + type.hiddenFlatPropIds).all { propId ->
            propsById.getValue(propId).ownerTypeId == type.id
        }) {
            "DTO type property owner must match the containing type: ${type.id.value}"
        }
        require(type.propIds.map { propId -> propsById.getValue(propId).name }.distinct().size == type.propIds.size) {
            "DTO type property names must be distinct: ${type.id.value}"
        }
        require(
            type.hiddenFlatPropIds
                .map { propId -> propsById.getValue(propId).name }
                .distinct()
                .size == type.hiddenFlatPropIds.size
        ) {
            "DTO type hidden flat property names must be distinct: ${type.id.value}"
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

    private fun validateProp(prop: DtoProp) {
        require(typesById.containsKey(prop.ownerTypeId)) {
            "DTO property owner type must exist: ${prop.id.value}"
        }
        validateAnnotations(prop.annotations)
        when (prop) {
            is DtoBaseProp -> {
                val ownerType = typesById.getValue(prop.ownerTypeId)
                require(
                    (
                        prop.inputModifier != DtoModifier.DYNAMIC &&
                            prop.inputModifier != DtoModifier.FUZZY
                    ) ||
                        DtoModifier.INPUT in ownerType.modifiers
                ) {
                    "Dynamic or fuzzy DTO property must belong to an input type: ${prop.id.value}"
                }
                val nextProp = prop.nextPropId?.let(propsById::get)
                require(prop.nextPropId == null || nextProp != null) {
                    "DTO next property must exist: ${prop.id.value}"
                }
                require(nextProp == null || nextProp is DtoBaseProp) {
                    "DTO next property must be a base property: ${prop.id.value}"
                }
                require(nextProp == null || nextProp.ownerTypeId == prop.ownerTypeId) {
                    "DTO next property must use the same owner: ${prop.id.value}"
                }
                val tailProp = propsById[prop.tailPropId]
                require(tailProp != null) {
                    "DTO tail property must exist: ${prop.id.value}"
                }
                require(tailProp is DtoBaseProp) {
                    "DTO tail property must be a base property: ${prop.id.value}"
                }
                require(tailProp.ownerTypeId == prop.ownerTypeId) {
                    "DTO tail property must use the same owner: ${prop.id.value}"
                }
                require(prop.targetTypeId == null || typesById.containsKey(prop.targetTypeId)) {
                    "DTO target type must exist: ${prop.id.value}"
                }
                require(prop.targetTypeReference == null || !prop.recursive) {
                    "Reusable DTO reference cannot be recursive: ${prop.id.value}"
                }
                val referencedSourceType = prop.targetTypeId?.let(typesById::getValue)
                require(
                    prop.targetTypeReference == null ||
                        referencedSourceType == null ||
                        referencedSourceType.baseTypeId == prop.targetTypeReference.targetBaseTypeId
                ) {
                    "Reusable DTO source type must use the referenced base type: ${prop.id.value}"
                }
            }
            is DtoFoldProp -> {
                require(typesById.containsKey(prop.targetTypeId)) {
                    "DTO fold target type must exist: ${prop.id.value}"
                }
                val nullGuardProp = prop.nullGuardPropId?.let(propsById::get)
                require(prop.nullGuardPropId == null || nullGuardProp != null) {
                    "DTO fold null guard property must exist: ${prop.id.value}"
                }
                require(nullGuardProp == null || nullGuardProp is DtoBaseProp) {
                    "DTO fold null guard property must be a base property: ${prop.id.value}"
                }
                require(
                    nullGuardProp == null || nullGuardProp.ownerTypeId == prop.ownerTypeId
                ) {
                    "DTO fold null guard property must use the same owner: ${prop.id.value}"
                }
            }
            is DtoUserProp -> validateTypeRef(prop.type)
        }
    }

    private fun validateAnnotations(annotations: List<DtoAnnotation>) {
        annotations.forEach { annotation ->
            annotation.arguments.forEach { argument -> validateAnnotationValue(argument.value) }
        }
    }

    private fun validateAnnotationValue(value: DtoAnnotationValue) {
        when (value) {
            is DtoAnnotationValue.ArrayValue -> value.elements.forEach(::validateAnnotationValue)
            is DtoAnnotationValue.AnnotationValue -> validateAnnotations(listOf(value.annotation))
            is DtoAnnotationValue.TypeValue -> validateTypeRef(value.type)
            is DtoAnnotationValue.EnumValue,
            is DtoAnnotationValue.LiteralValue,
            -> Unit
        }
    }

    private fun validateTypeRef(type: DtoTypeRef) {
        type.arguments.mapNotNull(DtoTypeArgument::type).forEach(::validateTypeRef)
    }
}

data class DtoType(
    val id: DtoTypeId,
    val baseTypeId: LsiSymbolId?,
    val packageName: String,
    val name: String?,
    val modifiers: Set<DtoModifier>,
    val annotations: List<DtoAnnotation>,
    val superInterfaces: List<DtoTypeRef>,
    val documentation: String?,
    val location: LsiLocation,
    val focusedRecursion: Boolean,
    val propIds: List<DtoPropId>,
    val hiddenFlatPropIds: List<DtoPropId>,
    val polymorphism: DtoPolymorphism?,
) {
    init {
        require(packageName == packageName.trim()) {
            "DTO type package name cannot have surrounding whitespace: '$packageName'"
        }
        require(name == null || name.isNotBlank()) { "DTO type name cannot be blank" }
        baseTypeId?.requireTypeQualifiedName()
    }
}

sealed interface DtoProp {
    val id: DtoPropId
    val ownerTypeId: DtoTypeId
    val name: String
    val alias: String?
    val nullable: Boolean
    val annotations: List<DtoAnnotation>
    val documentation: String?
    val aliasLocation: LsiLocation
}

data class DtoBaseProp(
    override val id: DtoPropId,
    override val ownerTypeId: DtoTypeId,
    override val name: String,
    override val alias: String?,
    override val nullable: Boolean,
    override val annotations: List<DtoAnnotation>,
    override val documentation: String?,
    override val aliasLocation: LsiLocation,
    val baseLocation: LsiLocation,
    val baseProps: List<DtoBasePropBinding>,
    val basePath: String,
    val nextPropId: DtoPropId?,
    val tailPropId: DtoPropId,
    val baseNullable: Boolean,
    val inputModifier: DtoModifier,
    val functionName: String?,
    val targetTypeId: DtoTypeId?,
    val targetTypeReference: DtoReusableTypeReference? = null,
    val enumType: DtoEnumType?,
    val config: DtoPropConfig?,
    val recursive: Boolean,
    val likeOptions: Set<DtoLikeOption>,
    val dtoDocumentation: String? = null,
) : DtoProp {
    init {
        require(baseProps.isNotEmpty()) { "DTO base property must reference at least one immutable property" }
        require(baseProps.map(DtoBasePropBinding::name).distinct().size == baseProps.size) {
            "DTO base property bindings cannot contain duplicate names: ${id.value}"
        }
        require(basePath.isNotBlank()) { "DTO base property path cannot be blank: ${id.value}" }
        require(inputModifier.isInputStrategy) {
            "DTO base property input modifier must be an input strategy: ${inputModifier.name}"
        }
        require(
            nullable ||
                (inputModifier != DtoModifier.DYNAMIC && inputModifier != DtoModifier.FUZZY)
        ) {
            "Dynamic or fuzzy DTO input property must be nullable: ${id.value}"
        }
    }
}

data class DtoReusableTypeReference(
    val qualifiedName: String,
    val targetBaseTypeId: LsiSymbolId,
    val kind: DtoReusableTypeKind,
    val location: LsiLocation,
) {
    init {
        require(qualifiedName.isNotBlank()) { "Reusable DTO qualified name cannot be blank" }
        require(qualifiedName == qualifiedName.trim()) {
            "Reusable DTO qualified name cannot have surrounding whitespace: '$qualifiedName'"
        }
        LsiSymbolId.type(qualifiedName).requireTypeQualifiedName()
        targetBaseTypeId.requireTypeQualifiedName()
    }
}

enum class DtoReusableTypeKind {
    VIEW,
    INPUT,
    SPECIFICATION,
}

data class DtoUserProp(
    override val id: DtoPropId,
    override val ownerTypeId: DtoTypeId,
    override val name: String,
    override val alias: String,
    override val nullable: Boolean,
    override val annotations: List<DtoAnnotation>,
    override val documentation: String?,
    override val aliasLocation: LsiLocation,
    val type: DtoTypeRef,
    val defaultValueText: String?,
) : DtoProp

data class DtoFoldProp(
    override val id: DtoPropId,
    override val ownerTypeId: DtoTypeId,
    override val name: String,
    override val alias: String,
    override val nullable: Boolean,
    override val annotations: List<DtoAnnotation>,
    override val documentation: String?,
    override val aliasLocation: LsiLocation,
    val nullGuardPropId: DtoPropId?,
    val targetTypeId: DtoTypeId,
) : DtoProp

data class DtoBasePropBinding(
    val name: String,
    val propId: LsiSymbolId,
) {
    init {
        require(name.isNotBlank()) { "DTO base property binding name cannot be blank" }
    }
}

enum class DtoModifier(
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

enum class DtoLikeOption {
    INSENSITIVE,
    MATCH_START,
    MATCH_END,
}

data class DtoTypeRef(
    val typeName: String,
    val arguments: List<DtoTypeArgument>,
    val nullable: Boolean,
    val location: LsiLocation,
) {
    init {
        require(typeName.isNotBlank()) { "DTO type reference name cannot be blank" }
    }
}

data class DtoTypeArgument(
    val variance: DtoVariance,
    val type: DtoTypeRef?,
) {
    init {
        require((variance == DtoVariance.STAR) == (type == null)) {
            "Only star-projected DTO type argument can omit its type"
        }
    }
}

enum class DtoVariance {
    INVARIANT,
    IN,
    OUT,
    STAR,
}

data class DtoAnnotation(
    val typeId: LsiSymbolId,
    val arguments: List<DtoAnnotationArgument>,
) {
    init {
        typeId.requireTypeQualifiedName()
        require(arguments.map(DtoAnnotationArgument::name).distinct().size == arguments.size) {
            "DTO annotation cannot contain duplicate argument names: ${typeId.value}"
        }
    }
}

data class DtoAnnotationArgument(
    val name: String,
    val value: DtoAnnotationValue,
) {
    init {
        require(name.isNotBlank()) { "DTO annotation argument name cannot be blank" }
    }
}

sealed interface DtoAnnotationValue {
    data class ArrayValue(val elements: List<DtoAnnotationValue>) : DtoAnnotationValue

    data class AnnotationValue(val annotation: DtoAnnotation) : DtoAnnotationValue

    data class EnumValue(
        val enumTypeId: LsiSymbolId,
        val constant: String,
    ) : DtoAnnotationValue {
        init {
            enumTypeId.requireTypeQualifiedName()
            require(constant.isNotBlank()) { "DTO annotation enum constant cannot be blank" }
        }
    }

    data class TypeValue(val type: DtoTypeRef) : DtoAnnotationValue

    data class LiteralValue(val code: String) : DtoAnnotationValue {
        init {
            require(code.isNotBlank()) { "DTO annotation literal code cannot be blank" }
        }
    }
}

data class DtoEnumType(
    val numeric: Boolean,
    val mappings: List<DtoEnumMapping>,
) {
    init {
        require(mappings.isNotEmpty()) { "DTO enum mapping cannot be empty" }
        require(mappings.map(DtoEnumMapping::constant).distinct().size == mappings.size) {
            "DTO enum mapping cannot contain duplicate constants"
        }
        require(mappings.map(DtoEnumMapping::value).distinct().size == mappings.size) {
            "DTO enum mapping cannot contain duplicate values"
        }
    }
}

data class DtoEnumMapping(
    val constant: String,
    val value: String,
) {
    init {
        require(constant.isNotBlank()) { "DTO enum mapping constant cannot be blank" }
        require(value.isNotBlank()) { "DTO enum mapping value cannot be blank" }
    }
}

data class DtoPropConfig(
    val predicate: DtoPredicate?,
    val orderItems: List<DtoOrderItem>,
    val filter: DtoConfigTypeRef?,
    val recursion: DtoConfigTypeRef?,
    val fetchType: DtoFetchType,
    val limit: DtoLimit?,
    val batch: Int?,
    val depth: Int?,
) {
    init {
        require(batch == null || batch > 0) { "DTO property config batch must be positive" }
        require(depth == null || depth >= 0) { "DTO property config depth cannot be negative" }
        require(filter == null || predicate == null && orderItems.isEmpty()) {
            "DTO property config filter cannot be combined with inline predicates or ordering"
        }
        require(recursion == null || depth == null) {
            "DTO property config recursion strategy cannot be combined with an explicit depth"
        }
    }
}

data class DtoLimit(
    val value: Int,
    val offset: Int,
) {
    init {
        require(value > 0) { "DTO property config limit must be positive" }
        require(offset >= 0) { "DTO property config offset cannot be negative" }
    }
}

data class DtoConfigTypeRef(
    val typeId: LsiSymbolId,
    val location: LsiLocation,
) {
    init {
        typeId.requireTypeQualifiedName()
    }
}

enum class DtoFetchType {
    AUTO,
    SELECT,
    JOIN_IF_NO_CACHE,
    JOIN_ALWAYS,
}

sealed interface DtoPredicate {
    data class And(val predicates: List<DtoPredicate>) : DtoPredicate {
        init {
            require(predicates.isNotEmpty()) { "DTO conjunction cannot be empty" }
        }
    }

    data class Or(val predicates: List<DtoPredicate>) : DtoPredicate {
        init {
            require(predicates.isNotEmpty()) { "DTO disjunction cannot be empty" }
        }
    }

    data class Comparison(
        val path: List<DtoPropPathNode>,
        val operator: DtoComparisonOperator,
        val value: DtoConfigValue,
    ) : DtoPredicate {
        init {
            require(path.isNotEmpty()) { "DTO comparison path cannot be empty" }
        }
    }

    data class Nullity(
        val path: List<DtoPropPathNode>,
        val negative: Boolean,
    ) : DtoPredicate {
        init {
            require(path.isNotEmpty()) { "DTO nullity path cannot be empty" }
        }
    }
}

enum class DtoComparisonOperator(
    val token: String,
) {
    EQ("="),
    NE("<>"),
    LT("<"),
    LE("<="),
    GT(">"),
    GE(">="),
    LIKE("like"),
    ILIKE("ilike"),
    ;

    companion object {
        fun fromToken(token: String): DtoComparisonOperator {
            return entries.singleOrNull { operator -> operator.token == token }
                ?: throw IllegalArgumentException("Unsupported DTO comparison operator: '$token'")
        }
    }
}

sealed interface DtoConfigValue {
    data class BooleanValue(val value: Boolean) : DtoConfigValue

    data class LongValue(val value: Long) : DtoConfigValue

    data class BigIntegerValue(val value: String) : DtoConfigValue

    data class DecimalValue(val value: String) : DtoConfigValue

    data class StringValue(val value: String) : DtoConfigValue
}

data class DtoOrderItem(
    val path: List<DtoPropPathNode>,
    val descending: Boolean,
) {
    init {
        require(path.isNotEmpty()) { "DTO order path cannot be empty" }
    }
}

data class DtoPropPathNode(
    val propId: LsiSymbolId,
    val associatedId: Boolean,
)

data class DtoPolymorphism(
    val exhaustive: Boolean,
    val branches: List<DtoPolymorphicBranch>,
) {
    init {
        require(branches.isNotEmpty()) { "DTO polymorphism must contain at least one branch" }
        require(branches.count { branch -> branch.kind == DtoPolymorphicBranchKind.DEFAULT } <= 1) {
            "DTO polymorphism cannot contain multiple default branches"
        }
        require(branches.map(DtoPolymorphicBranch::className).distinct().size == branches.size) {
            "DTO polymorphism cannot contain duplicate generated branch class names"
        }
        require(branches.map(DtoPolymorphicBranch::bodyTypeId).distinct().size == branches.size) {
            "DTO polymorphism cannot contain duplicate branch body type ids"
        }
        require(branches.map(DtoPolymorphicBranch::mergedTypeId).distinct().size == branches.size) {
            "DTO polymorphism cannot contain duplicate branch merged type ids"
        }
    }
}

data class DtoPolymorphicBranch(
    val kind: DtoPolymorphicBranchKind,
    val targetBaseTypeId: LsiSymbolId?,
    val declaredClassName: String?,
    val className: String,
    val bodyTypeId: DtoTypeId,
    val mergedTypeId: DtoTypeId,
    val implicit: Boolean,
    val location: LsiLocation,
) {
    init {
        require(className.isNotBlank()) { "DTO polymorphic branch class name cannot be blank" }
        require(declaredClassName == null || declaredClassName.isNotBlank()) {
            "DTO polymorphic branch declared class name cannot be blank"
        }
        require((kind == DtoPolymorphicBranchKind.TYPE) == (targetBaseTypeId != null)) {
            "Only DTO type branch can reference a target base type"
        }
        targetBaseTypeId?.requireTypeQualifiedName()
    }
}

enum class DtoPolymorphicBranchKind {
    DEFAULT,
    TYPE,
}
