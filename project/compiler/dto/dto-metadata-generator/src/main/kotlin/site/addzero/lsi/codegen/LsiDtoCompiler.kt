package site.addzero.lsi.codegen

import org.babyfish.jimmer.dto.compiler.DtoCompiler
import org.babyfish.jimmer.dto.compiler.DtoModifier
import org.babyfish.jimmer.dto.compiler.PropConfig
import org.babyfish.jimmer.dto.compiler.SimplePropType
import site.addzero.lsi.jimmer.GENERATED_VALUE
import site.addzero.lsi.jimmer.dto.LsiDtoFile
import site.addzero.lsi.jimmer.dto.LsiDtoModifier
import site.addzero.lsi.jimmer.dto.LsiDtoType
import site.addzero.lsi.jimmer.meta.ImmutableProp
import site.addzero.lsi.jimmer.meta.ImmutableType
import site.addzero.lsi.poet.LsiClassName
import site.addzero.lsi.poet.LsiTypeName

/**
 * DTO compiler bridge 只属于 DTO shared pipeline，不再放在通用 jimmer-ksp-ext 里。
 */
class LsiDtoCompiler(
    dtoFile: LsiDtoFile,
    private val defaultNullableInputModifier: LsiDtoModifier,
    private val genericTypeCountProvider: (String) -> Int?
) : DtoCompiler<ImmutableType, ImmutableProp>(dtoFile.rawDtoFile) {

    override fun getDefaultNullableInputModifier(): DtoModifier =
        defaultNullableInputModifier.toRawDtoModifier()

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
        baseProp.lsiAnnotation(GENERATED_VALUE) !== null

    override fun getEnumConstants(baseProp: ImmutableProp): List<String>? =
        baseProp.lsiType
            ?.lsiClass
            ?.enumEntryNames
            ?.takeIf { it.isNotEmpty() }

    override fun isSameType(baseProp1: ImmutableProp, baseProp2: ImmutableProp): Boolean =
        baseProp1.clientLsiTypeName.copyNullable(false) == baseProp2.clientLsiTypeName.copyNullable(false)

    override fun getSimplePropType(baseProp: ImmutableProp): SimplePropType =
        SIMPLE_PROP_TYPE_MAP[baseProp.toLsiTypeName().copyNullable(false)] ?: SimplePropType.NONE

    override fun getSimplePropType(pathNode: PropConfig.PathNode<ImmutableProp>): SimplePropType =
        SIMPLE_PROP_TYPE_MAP[
            if (pathNode.isAssociatedId) {
                pathNode.prop.targetType!!.idProp!!.toLsiTypeName().copyNullable(false)
            } else {
                pathNode.prop.toLsiTypeName().copyNullable(false)
            }
        ] ?: error(pathNode.prop.toLsiTypeName())

    override fun getGenericTypeCount(qualifiedName: String): Int? =
        genericTypeCountProvider(qualifiedName)

    internal fun compileToLsiDtoTypes(immutableType: ImmutableType): List<LsiDtoType> =
        compile(immutableType).map(::LsiDtoType)

    companion object {
        @JvmStatic
        private val SIMPLE_PROP_TYPE_MAP = mapOf<LsiTypeName, SimplePropType>(
            LsiClassName.bestGuess("kotlin.Boolean") to SimplePropType.BOOLEAN,
            LsiClassName.bestGuess("kotlin.Byte") to SimplePropType.BYTE,
            LsiClassName.bestGuess("kotlin.Short") to SimplePropType.SHORT,
            LsiClassName.bestGuess("kotlin.Int") to SimplePropType.INT,
            LsiClassName.bestGuess("kotlin.Long") to SimplePropType.LONG,
            LsiClassName.bestGuess("kotlin.Float") to SimplePropType.FLOAT,
            LsiClassName.bestGuess("kotlin.Double") to SimplePropType.DOUBLE,
            LsiClassName.bestGuess("java.math.BigInteger") to SimplePropType.BIG_INTEGER,
            LsiClassName.bestGuess("java.math.BigDecimal") to SimplePropType.BIG_DECIMAL,
            LsiClassName.bestGuess("kotlin.String") to SimplePropType.STRING,
        )
    }
}
