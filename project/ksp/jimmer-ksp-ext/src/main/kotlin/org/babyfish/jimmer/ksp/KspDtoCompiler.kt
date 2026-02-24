package org.babyfish.jimmer.ksp

import com.google.devtools.ksp.getClassDeclarationByName
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.squareup.kotlinpoet.BOOLEAN
import com.squareup.kotlinpoet.BYTE
import com.squareup.kotlinpoet.DOUBLE
import com.squareup.kotlinpoet.FLOAT
import com.squareup.kotlinpoet.INT
import com.squareup.kotlinpoet.LONG
import com.squareup.kotlinpoet.SHORT
import com.squareup.kotlinpoet.asTypeName
import org.babyfish.jimmer.dto.compiler.DtoCompiler
import org.babyfish.jimmer.dto.compiler.DtoFile
import org.babyfish.jimmer.dto.compiler.DtoModifier
import org.babyfish.jimmer.dto.compiler.PropConfig
import org.babyfish.jimmer.dto.compiler.SimplePropType
import org.babyfish.jimmer.ksp.immutable.meta.ImmutableProp
import org.babyfish.jimmer.ksp.immutable.meta.ImmutableType
import org.babyfish.jimmer.sql.GeneratedValue
import java.math.BigDecimal
import java.math.BigInteger

class KspDtoCompiler(
    dtoFile: DtoFile,
    private val resolver: Resolver,
    private val defaultNullableInputModifier: DtoModifier
) : DtoCompiler<ImmutableType, ImmutableProp>(dtoFile) {

    override fun getDefaultNullableInputModifier(): DtoModifier =
        defaultNullableInputModifier

    override fun getSuperTypes(baseType: ImmutableType): Collection<ImmutableType> =
        baseType.superTypes

    override fun getDeclaredProps(baseType: ImmutableType): Map<String, ImmutableProp> =
        baseType.declaredProperties

    override fun getProps(baseType: ImmutableType): Map<String, ImmutableProp> =
        baseType.properties

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

    override fun isSameType(baseProp1: ImmutableProp, baseProp2: ImmutableProp): Boolean =
        baseProp1.clientClassName.copy(nullable = false) == baseProp2.clientClassName.copy(nullable = false)

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
