package org.babyfish.jimmer.compiler.dto

import org.babyfish.jimmer.compiler.immutable.JimmerFormulaKind
import org.babyfish.jimmer.compiler.immutable.JimmerImmutablePrimaryMapping
import org.babyfish.jimmer.compiler.immutable.JimmerImmutableProp
import org.babyfish.jimmer.compiler.immutable.JimmerImmutableSchema
import org.babyfish.jimmer.compiler.immutable.JimmerImmutableType
import org.babyfish.jimmer.compiler.immutable.JimmerImmutableTypeKind
import org.babyfish.jimmer.compiler.immutable.JimmerImmutableView
import org.babyfish.jimmer.compiler.immutable.hasImmutableMarker
import org.babyfish.jimmer.compiler.immutable.normalizedTypeSignature
import org.babyfish.jimmer.dto.compiler.DtoCompiler
import org.babyfish.jimmer.dto.compiler.DtoFile
import org.babyfish.jimmer.dto.compiler.DtoModifier
import org.babyfish.jimmer.dto.compiler.PropConfig
import org.babyfish.jimmer.dto.compiler.SimplePropType
import org.babyfish.jimmer.dto.compiler.spi.BaseProp
import org.babyfish.jimmer.dto.compiler.spi.BaseType
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.model.LsiAnnotation
import site.addzero.lsi.model.LsiArrayType
import site.addzero.lsi.model.LsiDeclaredType
import site.addzero.lsi.model.LsiPrimitiveKind
import site.addzero.lsi.model.LsiPrimitiveType
import site.addzero.lsi.model.LsiTypeDeclaration
import site.addzero.lsi.model.LsiTypeDeclarationKind
import site.addzero.lsi.model.LsiTypeParameterRef
import site.addzero.lsi.model.LsiTypeRef
import site.addzero.lsi.model.LsiUnresolvedType
import site.addzero.lsi.model.LsiWorkspace

internal class LsiDtoTypeRegistry(
    immutableSchema: JimmerImmutableSchema,
    val workspace: LsiWorkspace,
) {
    private val typesById: Map<LsiSymbolId, LsiDtoBaseType>

    private val typesByQualifiedName: Map<String, LsiDtoBaseType>

    init {
        typesById = immutableSchema.types
            .sortedBy(JimmerImmutableType::id)
            .associate { immutableType ->
                immutableType.id to LsiDtoBaseType(immutableType, this)
            }
        typesByQualifiedName = typesById.values.associateBy(LsiDtoBaseType::qualifiedName)
    }

    operator fun get(typeId: LsiSymbolId): LsiDtoBaseType? = typesById[typeId]

    fun type(qualifiedName: String): LsiDtoBaseType? = typesByQualifiedName[qualifiedName]

    fun superTypes(type: LsiDtoBaseType): List<LsiDtoBaseType> {
        return type.immutableType.superTypeIds.mapNotNull(typesById::get)
    }

    fun directSubTypes(type: LsiDtoBaseType): List<LsiDtoBaseType> {
        return typesById.values
            .filter { candidate -> candidate.immutableType.primarySuperTypeId == type.id }
            .sortedBy(LsiDtoBaseType::id)
    }

    fun props(type: LsiDtoBaseType): Map<String, LsiDtoBaseProp> {
        return type.immutableType.props.associate { immutableProp ->
            immutableProp.name to LsiDtoBaseProp(type, immutableProp, this)
        }
    }

    fun targetType(prop: LsiDtoBaseProp): LsiDtoBaseType? {
        return prop.immutableProp.targetTypeId?.let(typesById::get)
    }

    fun genericTypeCount(qualifiedName: String): Int? {
        val typeId = LsiSymbolId.type(qualifiedName)
        val declaration = workspace[typeId] as? LsiTypeDeclaration
        if (declaration != null) {
            return declaration.typeParameters.size
        }
        val immutableType = typesById[typeId]
        if (immutableType != null) {
            return immutableType.immutableType.typeParameterIds.size
        }
        return STANDARD_GENERIC_TYPE_COUNTS[qualifiedName]
    }
}

internal class LsiDtoBaseType(
    internal val immutableType: JimmerImmutableType,
    private val registry: LsiDtoTypeRegistry,
) : BaseType {
    val id: LsiSymbolId
        get() = immutableType.id

    override val name: String = immutableType.qualifiedName.substringAfterLast('.')

    override val packageName: String = immutableType.qualifiedName.substringBeforeLast('.', "")

    override val qualifiedName: String = immutableType.qualifiedName

    override val isEntity: Boolean = immutableType.kind == JimmerImmutableTypeKind.ENTITY

    internal val props: Map<String, LsiDtoBaseProp> by lazy {
        registry.props(this)
    }

    internal val declaredProps: Map<String, LsiDtoBaseProp> by lazy {
        props.filterValues { prop -> prop.immutableProp.declaringTypeId == id }
    }

    internal val idProp: LsiDtoBaseProp?
        get() = props.values.firstOrNull { prop -> prop.isId }

    override fun toString(): String = qualifiedName
}

internal class LsiDtoBaseProp(
    private val ownerType: LsiDtoBaseType,
    internal val immutableProp: JimmerImmutableProp,
    private val registry: LsiDtoTypeRegistry,
) : BaseProp {
    val id: LsiSymbolId
        get() = immutableProp.id

    override val name: String = immutableProp.name

    override val isNullable: Boolean = immutableProp.nullable

    override val isList: Boolean = immutableProp.list

    override val isReference: Boolean
        get() = !isList && isAssociation(false)

    override val isFormula: Boolean = immutableProp.formulaKind != JimmerFormulaKind.NONE

    override val isTransient: Boolean =
        immutableProp.primaryMapping == JimmerImmutablePrimaryMapping.TRANSIENT

    override val idViewBaseProp: LsiDtoBaseProp? by lazy {
        (immutableProp.view as? JimmerImmutableView.Id)
            ?.basePropId
            ?.let(::ownerProp)
    }

    override val manyToManyViewBaseProp: LsiDtoBaseProp? by lazy {
        (immutableProp.view as? JimmerImmutableView.ManyToMany)
            ?.basePropId
            ?.let(::ownerProp)
    }

    override val isId: Boolean = immutableProp.primaryMapping == JimmerImmutablePrimaryMapping.ID

    override val isKey: Boolean = immutableProp.annotations.hasAnnotation(KEY_ANNOTATION)

    override val isRecursive: Boolean = immutableProp.recursive

    override val isEmbedded: Boolean = immutableProp.embedded

    override val isLogicalDeleted: Boolean =
        immutableProp.primaryMapping == JimmerImmutablePrimaryMapping.LOGICAL_DELETED

    override val isExcludedFromAllScalars: Boolean =
        immutableProp.annotations.hasAnnotation(EXCLUDE_FROM_ALL_SCALARS_ANNOTATION)

    override fun isAssociation(entityLevel: Boolean): Boolean {
        if (!immutableProp.association && !immutableProp.embedded) {
            return false
        }
        if (!entityLevel) {
            return true
        }
        val targetType = registry.targetType(this)
        return immutableProp.association && (targetType == null || targetType.isEntity)
    }

    override fun hasTransientResolver(): Boolean {
        return immutableProp.transientResolver != null
    }

    private fun ownerProp(propId: LsiSymbolId): LsiDtoBaseProp? {
        return ownerType.props.values.firstOrNull { candidate -> candidate.id == propId }
    }

    override fun toString(): String = "${ownerType.qualifiedName}.$name"
}

internal class LsiDtoCompiler(
    dtoFile: DtoFile,
    private val registry: LsiDtoTypeRegistry,
    private val defaultNullableInputModifier: DtoModifier,
) : DtoCompiler<LsiDtoBaseType, LsiDtoBaseProp>(dtoFile) {
    override fun getDefaultNullableInputModifier(): DtoModifier = defaultNullableInputModifier

    override fun getSuperTypes(baseType: LsiDtoBaseType): Collection<LsiDtoBaseType> {
        return registry.superTypes(baseType)
    }

    override fun getType(qualifiedName: String): LsiDtoBaseType? {
        return registry.type(qualifiedName)
    }

    override fun isImmutableType(qualifiedName: String): Boolean {
        val typeId = LsiSymbolId.type(qualifiedName)
        return (registry.workspace[typeId] as? LsiTypeDeclaration)?.hasImmutableMarker() == true
    }

    override fun getDirectSubTypes(baseType: LsiDtoBaseType): Collection<LsiDtoBaseType> {
        return registry.directSubTypes(baseType)
    }

    override fun isSameType(baseType1: LsiDtoBaseType, baseType2: LsiDtoBaseType): Boolean {
        return baseType1.id == baseType2.id
    }

    override fun isInstantiable(baseType: LsiDtoBaseType): Boolean {
        return baseType.immutableType.instantiable
    }

    override fun getDeclaredProps(baseType: LsiDtoBaseType): Map<String, LsiDtoBaseProp> {
        return baseType.declaredProps
    }

    override fun getProps(baseType: LsiDtoBaseType): Map<String, LsiDtoBaseProp> {
        return baseType.props
    }

    override fun getTargetType(baseProp: LsiDtoBaseProp): LsiDtoBaseType? {
        return registry.targetType(baseProp)
    }

    override fun getIdProp(baseType: LsiDtoBaseType): LsiDtoBaseProp? {
        return baseType.idProp
    }

    override fun isGeneratedValue(baseProp: LsiDtoBaseProp): Boolean {
        return baseProp.immutableProp.annotations.hasAnnotation(GENERATED_VALUE_ANNOTATION)
    }

    override fun getEnumConstants(baseProp: LsiDtoBaseProp): List<String>? {
        if (baseProp.isList) {
            return null
        }
        val typeId = (baseProp.immutableProp.type as? LsiDeclaredType)?.declarationId ?: return null
        val declaration = registry.workspace[typeId] as? LsiTypeDeclaration ?: return null
        if (declaration.kind != LsiTypeDeclarationKind.ENUM) {
            return null
        }
        return declaration.enumEntries.map { entry -> entry.name }
    }

    override fun getSimplePropType(baseProp: LsiDtoBaseProp): SimplePropType {
        return baseProp.immutableProp.type.toSimplePropType()
    }

    override fun getSimplePropType(pathNode: PropConfig.PathNode<LsiDtoBaseProp>): SimplePropType {
        if (!pathNode.isAssociatedId) {
            return pathNode.prop.immutableProp.type.toSimplePropType()
        }
        val targetType = registry.targetType(pathNode.prop) ?: return SimplePropType.NONE
        return targetType.idProp?.immutableProp?.type?.toSimplePropType() ?: SimplePropType.NONE
    }

    override fun isSameType(baseProp1: LsiDtoBaseProp, baseProp2: LsiDtoBaseProp): Boolean {
        return baseProp1.clientType().normalizedTypeSignature(ignoreRootNullability = true) ==
            baseProp2.clientType().normalizedTypeSignature(ignoreRootNullability = true)
    }

    override fun getGenericTypeCount(qualifiedName: String): Int? {
        return registry.genericTypeCount(qualifiedName)
    }
}

private fun LsiDtoBaseProp.clientType(): LsiTypeRef {
    return immutableProp.converter?.targetType ?: immutableProp.type
}

private fun LsiTypeRef.toSimplePropType(): SimplePropType {
    return when (this) {
        is LsiPrimitiveType -> when (kind) {
            LsiPrimitiveKind.BOOLEAN -> SimplePropType.BOOLEAN
            LsiPrimitiveKind.BYTE -> SimplePropType.BYTE
            LsiPrimitiveKind.SHORT -> SimplePropType.SHORT
            LsiPrimitiveKind.INT -> SimplePropType.INT
            LsiPrimitiveKind.LONG -> SimplePropType.LONG
            LsiPrimitiveKind.FLOAT -> SimplePropType.FLOAT
            LsiPrimitiveKind.DOUBLE -> SimplePropType.DOUBLE
            LsiPrimitiveKind.CHAR,
            LsiPrimitiveKind.UNIT,
            LsiPrimitiveKind.VOID,
            -> SimplePropType.NONE
        }
        is LsiDeclaredType -> SIMPLE_DECLARED_PROP_TYPES[declarationId] ?: SimplePropType.NONE
        is LsiArrayType,
        is LsiTypeParameterRef,
        is LsiUnresolvedType,
        -> SimplePropType.NONE
    }
}

private fun List<LsiAnnotation>.hasAnnotation(type: LsiSymbolId): Boolean {
    return any { annotation -> annotation.type == type }
}

private val SIMPLE_DECLARED_PROP_TYPES = mapOf(
    "java.lang.Boolean" to SimplePropType.BOOLEAN,
    "kotlin.Boolean" to SimplePropType.BOOLEAN,
    "java.lang.Byte" to SimplePropType.BYTE,
    "kotlin.Byte" to SimplePropType.BYTE,
    "java.lang.Short" to SimplePropType.SHORT,
    "kotlin.Short" to SimplePropType.SHORT,
    "java.lang.Integer" to SimplePropType.INT,
    "kotlin.Int" to SimplePropType.INT,
    "java.lang.Long" to SimplePropType.LONG,
    "kotlin.Long" to SimplePropType.LONG,
    "java.lang.Float" to SimplePropType.FLOAT,
    "kotlin.Float" to SimplePropType.FLOAT,
    "java.lang.Double" to SimplePropType.DOUBLE,
    "kotlin.Double" to SimplePropType.DOUBLE,
    "java.math.BigInteger" to SimplePropType.BIG_INTEGER,
    "java.math.BigDecimal" to SimplePropType.BIG_DECIMAL,
    "java.lang.String" to SimplePropType.STRING,
    "kotlin.String" to SimplePropType.STRING,
).mapKeys { (qualifiedName, _) -> LsiSymbolId.type(qualifiedName) }

private val STANDARD_GENERIC_TYPE_COUNTS = mapOf(
    "java.lang.Comparable" to 1,
    "kotlin.Comparable" to 1,
    "java.lang.Iterable" to 1,
    "kotlin.collections.Iterable" to 1,
    "java.util.Collection" to 1,
    "kotlin.collections.Collection" to 1,
    "java.util.List" to 1,
    "kotlin.collections.List" to 1,
    "java.util.Set" to 1,
    "kotlin.collections.Set" to 1,
    "java.util.Map" to 2,
    "kotlin.collections.Map" to 2,
)

private val KEY_ANNOTATION = LsiSymbolId.type("org.babyfish.jimmer.sql.Key")
private val GENERATED_VALUE_ANNOTATION = LsiSymbolId.type("org.babyfish.jimmer.sql.GeneratedValue")
private val EXCLUDE_FROM_ALL_SCALARS_ANNOTATION =
    LsiSymbolId.type("org.babyfish.jimmer.sql.ExcludeFromAllScalars")
