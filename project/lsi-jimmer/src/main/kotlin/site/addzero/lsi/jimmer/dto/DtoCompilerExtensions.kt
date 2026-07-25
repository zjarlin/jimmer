package site.addzero.lsi.jimmer.dto

import org.babyfish.jimmer.dto.compiler.DtoCompiler
import org.babyfish.jimmer.dto.compiler.DtoFile
import org.babyfish.jimmer.dto.compiler.DtoModifier as AstDtoModifier
import org.babyfish.jimmer.dto.compiler.PropConfig
import org.babyfish.jimmer.dto.compiler.SimplePropType
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.jimmer.FormulaKind
import site.addzero.lsi.jimmer.ImmutableProp
import site.addzero.lsi.jimmer.ImmutableSchema
import site.addzero.lsi.jimmer.ImmutableType
import site.addzero.lsi.jimmer.ImmutableTypeKind
import site.addzero.lsi.jimmer.PrimaryMapping
import site.addzero.lsi.jimmer.hasAnnotation
import site.addzero.lsi.jimmer.idViewBasePropOf
import site.addzero.lsi.jimmer.isJimmerImmutableType
import site.addzero.lsi.jimmer.jimmerTypeSignature
import site.addzero.lsi.jimmer.manyToManyViewBasePropOf
import site.addzero.lsi.jimmer.targetTypeOf
import site.addzero.lsi.model.LsiArrayType
import site.addzero.lsi.model.LsiDeclaredType
import site.addzero.lsi.model.LsiFunctionType
import site.addzero.lsi.model.LsiPrimitiveKind
import site.addzero.lsi.model.LsiPrimitiveType
import site.addzero.lsi.model.LsiTypeDeclaration
import site.addzero.lsi.model.LsiTypeDeclarationKind
import site.addzero.lsi.model.LsiTypeParameterRef
import site.addzero.lsi.model.LsiTypeRef
import site.addzero.lsi.model.LsiUnresolvedType
import site.addzero.lsi.model.LsiWorkspace

/** 基于共享不可变模型与工作区创建 DTO compiler。 */
fun DtoFile.toLsiDtoCompiler(
    immutableSchema: ImmutableSchema,
    workspace: LsiWorkspace,
    defaultNullableInputModifier: AstDtoModifier,
): DtoCompiler<ImmutableType, ImmutableProp> {
    return LsiDtoCompiler(
        dtoFile = this,
        immutableSchema = immutableSchema,
        workspace = workspace,
        defaultNullableInputModifier = defaultNullableInputModifier,
    )
}

/** DTO 属性沿用 Jimmer 的 ID 前置规则，其余属性保持结构声明顺序。 */
private fun ImmutableType.dtoCompilerPropsInOrder(): List<ImmutableProp> {
    val (idProps, remainingProps) = props.partition { prop ->
        prop.primaryMapping == PrimaryMapping.ID
    }
    return idProps + remainingProps
}

private class LsiDtoCompiler(
    dtoFile: DtoFile,
    private val immutableSchema: ImmutableSchema,
    private val workspace: LsiWorkspace,
    private val defaultNullableInputModifier: AstDtoModifier,
) : DtoCompiler<ImmutableType, ImmutableProp>(dtoFile) {
    private val typesByQualifiedName = immutableSchema.types.associateBy(ImmutableType::qualifiedName)

    override fun getDefaultNullableInputModifier(): AstDtoModifier = defaultNullableInputModifier

    override fun getSuperTypes(baseType: ImmutableType): Collection<ImmutableType> {
        return baseType.superTypeIds.mapNotNull(immutableSchema.typesById::get)
    }

    override fun getBaseTypeName(baseType: ImmutableType): String =
        baseType.qualifiedName.substringAfterLast('.')

    override fun getBaseTypeQualifiedName(baseType: ImmutableType): String = baseType.qualifiedName

    override fun isEntity(baseType: ImmutableType): Boolean = baseType.kind == ImmutableTypeKind.ENTITY

    override fun getType(qualifiedName: String): ImmutableType? {
        return typesByQualifiedName[qualifiedName]
    }

    override fun isImmutableType(qualifiedName: String): Boolean {
        val typeId = LsiSymbolId.type(qualifiedName)
        return (workspace[typeId] as? LsiTypeDeclaration)?.isJimmerImmutableType() == true
    }

    override fun getDirectSubTypes(baseType: ImmutableType): Collection<ImmutableType> {
        return immutableSchema.types
            .filter { candidate -> candidate.primarySuperTypeId == baseType.id }
            .sortedBy(ImmutableType::id)
    }

    override fun isSameBaseType(baseType1: ImmutableType, baseType2: ImmutableType): Boolean {
        return baseType1.id == baseType2.id
    }

    override fun isInstantiable(baseType: ImmutableType): Boolean {
        return baseType.instantiable
    }

    override fun getDeclaredProps(baseType: ImmutableType): Map<String, ImmutableProp> {
        return props(baseType).filterValues { prop ->
            prop.declaringTypeId == baseType.id
        }
    }

    override fun getProps(baseType: ImmutableType): Map<String, ImmutableProp> {
        return props(baseType)
    }

    override fun getBasePropName(baseProp: ImmutableProp): String = baseProp.name

    override fun getBasePropDisplayName(baseProp: ImmutableProp): String {
        val ownerType = immutableSchema.typesById.getValue(baseProp.ownerTypeId)
        return "${ownerType.qualifiedName}.${baseProp.name}"
    }

    override fun isBasePropNullable(baseProp: ImmutableProp): Boolean = baseProp.nullable

    override fun isBasePropList(baseProp: ImmutableProp): Boolean = baseProp.list

    override fun isBasePropFormula(baseProp: ImmutableProp): Boolean =
        baseProp.formulaKind != FormulaKind.NONE

    override fun isBasePropTransient(baseProp: ImmutableProp): Boolean =
        baseProp.primaryMapping == PrimaryMapping.TRANSIENT

    override fun getIdViewBaseProp(baseProp: ImmutableProp): ImmutableProp? =
        immutableSchema.idViewBasePropOf(baseProp)

    override fun getManyToManyViewBaseProp(baseProp: ImmutableProp): ImmutableProp? =
        immutableSchema.manyToManyViewBasePropOf(baseProp)

    override fun isBasePropId(baseProp: ImmutableProp): Boolean =
        baseProp.primaryMapping == PrimaryMapping.ID

    override fun isBasePropRecursive(baseProp: ImmutableProp): Boolean = baseProp.recursive

    override fun isBasePropEmbedded(baseProp: ImmutableProp): Boolean = baseProp.embedded

    override fun isBasePropLogicalDeleted(baseProp: ImmutableProp): Boolean =
        baseProp.primaryMapping == PrimaryMapping.LOGICAL_DELETED

    override fun isBasePropExcludedFromAllScalars(baseProp: ImmutableProp): Boolean =
        baseProp.hasAnnotation(EXCLUDE_FROM_ALL_SCALARS_ANNOTATION)

    override fun isBasePropAssociation(baseProp: ImmutableProp, entityLevel: Boolean): Boolean {
        if (!baseProp.association && !baseProp.embedded) {
            return false
        }
        if (!entityLevel) {
            return true
        }
        val targetType = immutableSchema.targetTypeOf(baseProp)
        return baseProp.association && (targetType == null || targetType.kind == ImmutableTypeKind.ENTITY)
    }

    override fun hasBasePropTransientResolver(baseProp: ImmutableProp): Boolean =
        baseProp.transientResolver != null

    override fun getTargetType(baseProp: ImmutableProp): ImmutableType? {
        return immutableSchema.targetTypeOf(baseProp)
    }

    override fun getIdProp(baseType: ImmutableType): ImmutableProp? {
        val idPropId = baseType.idPropId ?: return null
        return immutableSchema.propsById.getValue(idPropId)
    }

    override fun isGeneratedValue(baseProp: ImmutableProp): Boolean {
        return baseProp.hasAnnotation(GENERATED_VALUE_ANNOTATION)
    }

    override fun getEnumConstants(baseProp: ImmutableProp): List<String>? {
        if (baseProp.list) {
            return null
        }
        val typeId = (baseProp.type as? LsiDeclaredType)?.declarationId ?: return null
        val declaration = workspace[typeId] as? LsiTypeDeclaration ?: return null
        if (declaration.kind != LsiTypeDeclarationKind.ENUM) {
            return null
        }
        return declaration.enumEntries.map { entry -> entry.name }
    }

    override fun getSimplePropType(baseProp: ImmutableProp): SimplePropType {
        return baseProp.type.toSimplePropType()
    }

    override fun getSimplePropType(pathNode: PropConfig.PathNode<ImmutableProp>): SimplePropType {
        if (!pathNode.isAssociatedId) {
            return pathNode.prop.type.toSimplePropType()
        }
        val targetType = immutableSchema.targetTypeOf(pathNode.prop) ?: return SimplePropType.NONE
        val idPropId = targetType.idPropId ?: return SimplePropType.NONE
        return immutableSchema.propsById.getValue(idPropId)
            .type
            .toSimplePropType()
    }

    override fun isSameBasePropType(baseProp1: ImmutableProp, baseProp2: ImmutableProp): Boolean {
        return baseProp1.dtoClientType(immutableSchema)
            .jimmerTypeSignature(ignoreRootNullability = true) ==
            baseProp2.dtoClientType(immutableSchema)
                .jimmerTypeSignature(ignoreRootNullability = true)
    }

    override fun getGenericTypeCount(qualifiedName: String): Int? {
        val typeId = LsiSymbolId.type(qualifiedName)
        val declaration = workspace[typeId] as? LsiTypeDeclaration
        if (declaration != null) {
            return declaration.typeParameters.size
        }
        val immutableType = immutableSchema.typesById[typeId]
        if (immutableType != null) {
            return immutableType.typeParameterIds.size
        }
        return STANDARD_GENERIC_TYPE_COUNTS[qualifiedName]
    }

    private fun props(type: ImmutableType): Map<String, ImmutableProp> {
        return type.dtoCompilerPropsInOrder().associateTo(linkedMapOf()) { prop -> prop.name to prop }
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

private val GENERATED_VALUE_ANNOTATION = LsiSymbolId.type("org.babyfish.jimmer.sql.GeneratedValue")
private val EXCLUDE_FROM_ALL_SCALARS_ANNOTATION =
    LsiSymbolId.type("org.babyfish.jimmer.sql.ExcludeFromAllScalars")
