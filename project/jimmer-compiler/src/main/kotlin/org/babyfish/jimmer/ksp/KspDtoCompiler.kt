package org.babyfish.jimmer.ksp

import com.google.devtools.ksp.getClassDeclarationByName
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.squareup.kotlinpoet.*
import org.babyfish.jimmer.dto.compiler.*
import org.babyfish.jimmer.ksp.immutable.meta.ImmutableProp
import org.babyfish.jimmer.ksp.immutable.meta.ImmutableType
import org.babyfish.jimmer.sql.GeneratedValue
import site.addzero.lsi.jimmer.ImmutableSchema
import site.addzero.lsi.jimmer.dto.haveSameDtoClientType
import java.math.BigDecimal
import java.math.BigInteger

class KspDtoCompiler(
    dtoFile: DtoFile,
    private val ctx: Context,
    private val defaultNullableInputModifier: DtoModifier,
    private val immutableSchema: ImmutableSchema,
) : DtoCompiler<ImmutableType, ImmutableProp>(dtoFile) {

    private val resolver: Resolver = ctx.resolver

    override fun getDefaultNullableInputModifier(): DtoModifier =
        defaultNullableInputModifier

    override fun getSuperTypes(baseType: ImmutableType): Collection<ImmutableType> =
        baseType.superTypes

    override fun getBaseTypeName(baseType: ImmutableType): String = baseType.simpleName

    override fun getBaseTypeQualifiedName(baseType: ImmutableType): String = baseType.qualifiedName

    override fun isEntity(baseType: ImmutableType): Boolean = baseType.isEntity

    override fun isImmutableType(qualifiedName: String): Boolean =
        resolver.getClassDeclarationByName(qualifiedName)?.let { ctx.typeAnnotationOf(it) !== null } == true

    override fun getType(qualifiedName: String): ImmutableType? =
        ctx.immutableTypeOf(qualifiedName)?.also { ctx.resolve() }

    override fun getDirectSubTypes(baseType: ImmutableType): Collection<ImmutableType> =
        ctx.types
            .filter { it.primarySuperType?.qualifiedName == baseType.qualifiedName }
            .sortedBy { it.qualifiedName }

    override fun isSameBaseType(baseType1: ImmutableType, baseType2: ImmutableType): Boolean =
        baseType1.qualifiedName == baseType2.qualifiedName

    override fun isInstantiable(baseType: ImmutableType): Boolean =
        baseType.isInstantiable

    override fun getDeclaredProps(baseType: ImmutableType): Map<String, ImmutableProp> =
        baseType.declaredProperties

    override fun getProps(baseType: ImmutableType): Map<String, ImmutableProp> =
        baseType.properties

    override fun getBasePropName(baseProp: ImmutableProp): String =
        baseProp.name

    override fun isBasePropNullable(baseProp: ImmutableProp): Boolean =
        baseProp.isNullable

    override fun isBasePropList(baseProp: ImmutableProp): Boolean =
        baseProp.isList

    override fun isBasePropFormula(baseProp: ImmutableProp): Boolean =
        baseProp.isFormula

    override fun isBasePropTransient(baseProp: ImmutableProp): Boolean =
        baseProp.isTransient

    override fun getIdViewBaseProp(baseProp: ImmutableProp): ImmutableProp? =
        baseProp.idViewBaseProp

    override fun getManyToManyViewBaseProp(baseProp: ImmutableProp): ImmutableProp? =
        baseProp.manyToManyViewBaseProp

    override fun isBasePropId(baseProp: ImmutableProp): Boolean =
        baseProp.isId

    override fun isBasePropRecursive(baseProp: ImmutableProp): Boolean =
        baseProp.isRecursive

    override fun isBasePropEmbedded(baseProp: ImmutableProp): Boolean =
        baseProp.isEmbedded

    override fun isBasePropLogicalDeleted(baseProp: ImmutableProp): Boolean =
        baseProp.isLogicalDeleted

    override fun isBasePropExcludedFromAllScalars(baseProp: ImmutableProp): Boolean =
        baseProp.isExcludedFromAllScalars

    override fun isBasePropAssociation(baseProp: ImmutableProp, entityLevel: Boolean): Boolean =
        baseProp.isAssociation(entityLevel)

    override fun hasBasePropTransientResolver(baseProp: ImmutableProp): Boolean =
        baseProp.hasTransientResolver()

    override fun getTargetType(baseProp: ImmutableProp): ImmutableType? =
        baseProp.targetType

    override fun getIdProp(baseType: ImmutableType): ImmutableProp? =
        baseType.idProp

    override fun isGeneratedValue(baseProp: ImmutableProp): Boolean =
        baseProp.annotation(GeneratedValue::class) !== null

    override fun getEnumConstants(baseProp: ImmutableProp): List<String>? =
        (baseProp.resolvedType.declaration as? KSClassDeclaration)?.let { decl ->
            decl.takeIf { it.classKind == ClassKind.ENUM_CLASS }?.let { enumDecl ->
                enumDecl
                    .declarations
                    .filter {
                        it is KSClassDeclaration && it.classKind == ClassKind.ENUM_ENTRY
                    }
                    .map { it.simpleName.asString() }
                    .toList()
            }
        }

    override fun isSameBasePropType(baseProp1: ImmutableProp, baseProp2: ImmutableProp): Boolean =
        immutableSchema.haveSameDtoClientType(
            baseProp1.declaringType.qualifiedName,
            baseProp1.name,
            baseProp2.declaringType.qualifiedName,
            baseProp2.name,
        )

    override fun getSimplePropType(baseProp: ImmutableProp): SimplePropType =
        SIMPLE_PROP_TYPE_MAP[baseProp.typeName().copy(nullable = false)] ?: SimplePropType.NONE

    override fun getSimplePropType(pathNode: PropConfig.PathNode<ImmutableProp>): SimplePropType =
        SIMPLE_PROP_TYPE_MAP[
            if (pathNode.isAssociatedId) {
                pathNode.prop.targetType!!.idProp!!.typeName().copy(nullable = false)
            } else {
                pathNode.prop.typeName().copy(nullable = false)
            }
        ] ?: error(pathNode.prop.typeName())

    override fun getGenericTypeCount(qualifiedName: String): Int? =
        resolver.getClassDeclarationByName(qualifiedName)?.typeParameters?.size

    companion object {
        @JvmStatic
        private val SIMPLE_PROP_TYPE_MAP = mapOf(
            BOOLEAN to SimplePropType.BOOLEAN,
            BYTE to SimplePropType.BYTE,
            SHORT to SimplePropType.SHORT,
            INT to SimplePropType.INT,
            LONG to SimplePropType.LONG,
            FLOAT to SimplePropType.FLOAT,
            DOUBLE to SimplePropType.DOUBLE,

            BigInteger::class.asTypeName().copy(nullable = false) to SimplePropType.BIG_INTEGER,
            BigDecimal::class.asTypeName().copy(nullable = false) to SimplePropType.BIG_DECIMAL,

            String::class.asTypeName().copy(nullable = false) to SimplePropType.STRING,
        )
    }
}
