package site.addzero.lsi.jimmer.dto

import org.babyfish.jimmer.dto.compiler.DtoCompiler
import org.babyfish.jimmer.dto.compiler.DtoFile
import org.babyfish.jimmer.dto.compiler.DtoModifier as AstDtoModifier
import org.babyfish.jimmer.dto.compiler.PropConfig
import org.babyfish.jimmer.dto.compiler.SimplePropType
import org.babyfish.jimmer.dto.compiler.spi.BaseProp
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.jimmer.FormulaKind
import site.addzero.lsi.jimmer.ImmutableProp
import site.addzero.lsi.jimmer.ImmutableSchema
import site.addzero.lsi.jimmer.ImmutableType
import site.addzero.lsi.jimmer.ImmutableTypeKind
import site.addzero.lsi.jimmer.ImmutableView
import site.addzero.lsi.jimmer.PrimaryMapping
import site.addzero.lsi.jimmer.isJimmerImmutableType
import site.addzero.lsi.jimmer.jimmerTypeSignature
import site.addzero.lsi.model.LsiAnnotation
import site.addzero.lsi.model.LsiArrayType
import site.addzero.lsi.model.LsiDeclaredType
import site.addzero.lsi.model.LsiFunctionType
import site.addzero.lsi.model.LsiPrimitiveKind
import site.addzero.lsi.model.LsiPrimitiveType
import site.addzero.lsi.model.LsiTypeDeclaration
import site.addzero.lsi.model.LsiTypeDeclarationKind
import site.addzero.lsi.model.LsiTypeParameterRef
import site.addzero.lsi.model.LsiTypeRef
import site.addzero.lsi.model.LsiTypeSystem
import site.addzero.lsi.model.LsiUnresolvedType
import site.addzero.lsi.model.LsiWorkspace

/** 为 DTO compiler 创建共享的 LSI 类型注册表。 */
fun ImmutableSchema.toLsiDtoTypeRegistry(
    workspace: LsiWorkspace,
): LsiDtoTypeRegistry = LsiDtoTypeRegistry(this, workspace)

/** 基于共享 LSI 类型注册表创建 DTO compiler。 */
fun DtoFile.toLsiDtoCompiler(
    registry: LsiDtoTypeRegistry,
    defaultNullableInputModifier: AstDtoModifier,
): DtoCompiler<ImmutableType, LsiDtoBaseProp> {
    return LsiDtoCompiler(
        dtoFile = this,
        registry = registry,
        defaultNullableInputModifier = defaultNullableInputModifier,
    )
}

/** 在多个 DTO 文档之间共享不可变类型与属性身份。 */
class LsiDtoTypeRegistry internal constructor(
    internal val immutableSchema: ImmutableSchema,
    internal val workspace: LsiWorkspace,
) {
    private val typeSystem = LsiTypeSystem(workspace)

    private val typesById: Map<LsiSymbolId, ImmutableType> = immutableSchema.typesById

    private val typesByQualifiedName: Map<String, ImmutableType> =
        immutableSchema.types.associateBy(ImmutableType::qualifiedName)

    private val propsByTypeId = mutableMapOf<LsiSymbolId, Map<String, LsiDtoBaseProp>>()

    operator fun get(typeId: LsiSymbolId): ImmutableType? = typesById[typeId]

    internal fun type(qualifiedName: String): ImmutableType? = typesByQualifiedName[qualifiedName]

    internal fun superTypes(type: ImmutableType): List<ImmutableType> {
        return type.superTypeIds.mapNotNull(typesById::get)
    }

    internal fun directSubTypes(type: ImmutableType): List<ImmutableType> {
        return typesById.values
            .filter { candidate -> candidate.primarySuperTypeId == type.id }
            .sortedBy(ImmutableType::id)
    }

    internal fun props(type: ImmutableType): Map<String, LsiDtoBaseProp> {
        return propsByTypeId.getOrPut(type.id) {
            type.dtoCompilerPropsInOrder().associateTo(linkedMapOf()) { immutableProp ->
                immutableProp.name to LsiDtoBaseProp(type, immutableProp, this)
            }
        }
    }

    internal fun targetType(prop: LsiDtoBaseProp): ImmutableType? {
        return prop.immutableProp.targetTypeId?.let(typesById::get)
    }

    internal fun genericTypeCount(qualifiedName: String): Int? {
        val typeId = LsiSymbolId.type(qualifiedName)
        val declaration = workspace[typeId] as? LsiTypeDeclaration
        if (declaration != null) {
            return declaration.typeParameters.size
        }
        val immutableType = typesById[typeId]
        if (immutableType != null) {
            return immutableType.typeParameterIds.size
        }
        return STANDARD_GENERIC_TYPE_COUNTS[qualifiedName]
    }

    internal fun resolveSuperType(
        typeId: LsiSymbolId,
        superTypeId: LsiSymbolId,
    ): LsiDeclaredType? = typeSystem.resolveSuperType(typeId, superTypeId)
}

/** DTO 属性沿用 Jimmer 的 ID 前置规则，其余属性保持结构声明顺序。 */
private fun ImmutableType.dtoCompilerPropsInOrder(): List<ImmutableProp> {
    val (idProps, remainingProps) = props.partition { prop ->
        prop.primaryMapping == PrimaryMapping.ID
    }
    return idProps + remainingProps
}

/** DTO compiler SPI 使用的不可变属性投影。 */
class LsiDtoBaseProp internal constructor(
    private val ownerType: ImmutableType,
    val immutableProp: ImmutableProp,
    private val registry: LsiDtoTypeRegistry,
) : BaseProp {
    val id: LsiSymbolId
        get() = immutableProp.id

    override val name: String = immutableProp.name

    override val isNullable: Boolean = immutableProp.nullable

    override val isList: Boolean = immutableProp.list

    override val isReference: Boolean
        get() = !isList && isAssociation(false)

    override val isFormula: Boolean = immutableProp.formulaKind != FormulaKind.NONE

    override val isTransient: Boolean =
        immutableProp.primaryMapping == PrimaryMapping.TRANSIENT

    override val idViewBaseProp: LsiDtoBaseProp? by lazy {
        (immutableProp.view as? ImmutableView.Id)
            ?.basePropId
            ?.let(::ownerProp)
    }

    override val manyToManyViewBaseProp: LsiDtoBaseProp? by lazy {
        (immutableProp.view as? ImmutableView.ManyToMany)
            ?.basePropId
            ?.let(::ownerProp)
    }

    override val isId: Boolean = immutableProp.primaryMapping == PrimaryMapping.ID

    override val isKey: Boolean = immutableProp.annotations.hasAnnotation(KEY_ANNOTATION)

    override val isRecursive: Boolean = immutableProp.recursive

    override val isEmbedded: Boolean = immutableProp.embedded

    override val isLogicalDeleted: Boolean =
        immutableProp.primaryMapping == PrimaryMapping.LOGICAL_DELETED

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
        return immutableProp.association && (targetType == null || targetType.kind == ImmutableTypeKind.ENTITY)
    }

    override fun hasTransientResolver(): Boolean {
        return immutableProp.transientResolver != null
    }

    private fun ownerProp(propId: LsiSymbolId): LsiDtoBaseProp? {
        return registry.props(ownerType).values.firstOrNull { candidate -> candidate.id == propId }
    }

    override fun toString(): String = "${ownerType.qualifiedName}.$name"
}

private class LsiDtoCompiler(
    dtoFile: DtoFile,
    private val registry: LsiDtoTypeRegistry,
    private val defaultNullableInputModifier: AstDtoModifier,
) : DtoCompiler<ImmutableType, LsiDtoBaseProp>(dtoFile) {
    override fun getDefaultNullableInputModifier(): AstDtoModifier = defaultNullableInputModifier

    override fun getSuperTypes(baseType: ImmutableType): Collection<ImmutableType> {
        return registry.superTypes(baseType)
    }

    override fun getBaseTypeName(baseType: ImmutableType): String =
        baseType.qualifiedName.substringAfterLast('.')

    override fun getBaseTypeQualifiedName(baseType: ImmutableType): String = baseType.qualifiedName

    override fun isEntity(baseType: ImmutableType): Boolean = baseType.kind == ImmutableTypeKind.ENTITY

    override fun getType(qualifiedName: String): ImmutableType? {
        return registry.type(qualifiedName)
    }

    override fun isImmutableType(qualifiedName: String): Boolean {
        val typeId = LsiSymbolId.type(qualifiedName)
        return (registry.workspace[typeId] as? LsiTypeDeclaration)?.isJimmerImmutableType() == true
    }

    override fun getDirectSubTypes(baseType: ImmutableType): Collection<ImmutableType> {
        return registry.directSubTypes(baseType)
    }

    override fun isSameType(baseType1: ImmutableType, baseType2: ImmutableType): Boolean {
        return baseType1.id == baseType2.id
    }

    override fun isInstantiable(baseType: ImmutableType): Boolean {
        return baseType.instantiable
    }

    override fun getDeclaredProps(baseType: ImmutableType): Map<String, LsiDtoBaseProp> {
        return registry.props(baseType).filterValues { prop ->
            prop.immutableProp.declaringTypeId == baseType.id
        }
    }

    override fun getProps(baseType: ImmutableType): Map<String, LsiDtoBaseProp> {
        return registry.props(baseType)
    }

    override fun getTargetType(baseProp: LsiDtoBaseProp): ImmutableType? {
        return registry.targetType(baseProp)
    }

    override fun getIdProp(baseType: ImmutableType): LsiDtoBaseProp? {
        val idPropId = baseType.idPropId ?: return null
        return registry.props(baseType).values.single { prop -> prop.id == idPropId }
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
        val idPropId = targetType.idPropId ?: return SimplePropType.NONE
        return registry.props(targetType).values
            .single { prop -> prop.id == idPropId }
            .immutableProp
            .type
            .toSimplePropType()
    }

    override fun isSameType(baseProp1: LsiDtoBaseProp, baseProp2: LsiDtoBaseProp): Boolean {
        return baseProp1.immutableProp.dtoClientType(registry.immutableSchema)
            .jimmerTypeSignature(ignoreRootNullability = true) ==
            baseProp2.immutableProp.dtoClientType(registry.immutableSchema)
                .jimmerTypeSignature(ignoreRootNullability = true)
    }

    override fun getGenericTypeCount(qualifiedName: String): Int? {
        return registry.genericTypeCount(qualifiedName)
    }
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
        is LsiFunctionType,
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
