package site.addzero.lsi.jimmer.dto

import org.babyfish.jimmer.dto.compiler.AbstractProp
import org.babyfish.jimmer.dto.compiler.Anno
import org.babyfish.jimmer.dto.compiler.DtoModifier
import org.babyfish.jimmer.dto.compiler.DtoProp
import org.babyfish.jimmer.dto.compiler.EnumType
import org.babyfish.jimmer.dto.compiler.LikeOption
import org.babyfish.jimmer.dto.compiler.TypeRef
import org.babyfish.jimmer.dto.compiler.UserProp
import site.addzero.lsi.anno.LsiAnnotation
import site.addzero.lsi.codegen.ConverterMetadata
import site.addzero.lsi.jimmer.meta.ImmutableProp
import site.addzero.lsi.jimmer.meta.ImmutableType

internal sealed interface LsiDtoAbstractPropView {
    val name: String

    val declaredAlias: String?

    val annotations: List<Anno>

    val isNullable: Boolean

    val doc: String?
}

internal class LsiDtoPropView internal constructor(
    private val dtoProp: DtoProp<ImmutableType, ImmutableProp>,
) : LsiDtoAbstractPropView {
    override val name: String
        get() = dtoProp.name

    override val declaredAlias: String?
        get() = dtoProp.alias

    override val annotations: List<Anno>
        get() = dtoProp.annotations

    override val isNullable: Boolean
        get() = dtoProp.isNullable

    override val doc: String?
        get() = dtoProp.doc

    val inputModifier: DtoModifier
        get() = dtoProp.inputModifier

    val funcName: String?
        get() = dtoProp.funcName

    val enumType: EnumType?
        get() = dtoProp.enumType

    val hasNextProp: Boolean
        get() = dtoProp.nextProp != null

    val isFlat: Boolean
        get() = dtoProp.getFuncName() == "flat"

    val baseProp: ImmutableProp
        get() = dtoProp.baseProp

    val tailBaseProp: ImmutableProp
        get() = dtoProp.toTailProp().baseProp

    val targetType: LsiDtoType?
        get() = dtoProp.targetType?.toLsiDtoType()

    val tailPropView: LsiDtoPropView
        get() = if (!hasNextProp) {
            this
        } else {
            LsiDtoPropView(dtoProp.toTailProp())
        }

    val isRecursive: Boolean
        get() = dtoProp.isRecursive

    val isIdOnly: Boolean
        get() = dtoProp.isIdOnly

    val isBaseNullable: Boolean
        get() = dtoProp.isBaseNullable

    val configView: LsiDtoPropConfigView?
        get() = dtoProp.lsiConfigView

    val tailFieldAnnotations: List<LsiAnnotation>
        get() = dtoProp.toTailProp().baseProp.lsiField.annotations

    val tailBasePropMapValues: List<ImmutableProp>
        get() = dtoProp.toTailProp().basePropMap.values.toList()

    val likeOptions: Set<LikeOption>
        get() = dtoProp.toTailProp().likeOptions

    val enumValueMap: Map<String, String>
        get() = dtoProp.enumType?.valueMap ?: emptyMap()

    val enumConstantMap: Map<String, String>
        get() = dtoProp.enumType?.constantMap ?: emptyMap()

    val pathBaseProps: List<ImmutableProp>
        get() = buildList {
            var current: DtoProp<ImmutableType, ImmutableProp>? = dtoProp
            while (current != null) {
                add(current.baseProp)
                current = current.nextProp
            }
        }

    val stackBaseProps: List<ImmutableProp>
        get() = buildList {
            val tailProp = dtoProp.toTailProp()
            var current: DtoProp<ImmutableType, ImmutableProp>? = dtoProp
            while (current != null) {
                if (current !== tailProp || current.getTargetType() != null) {
                    add(current.getBaseProp())
                }
                current = current.getNextProp()
            }
        }

    fun isFunc(vararg funcNames: String): Boolean =
        dtoProp.isFunc(*funcNames)
}

internal class LsiUserPropView internal constructor(
    private val userProp: UserProp,
) : LsiDtoAbstractPropView {
    override val name: String
        get() = userProp.name

    override val declaredAlias: String?
        get() = userProp.alias

    override val annotations: List<Anno>
        get() = userProp.annotations

    override val isNullable: Boolean
        get() = userProp.isNullable

    override val doc: String?
        get() = userProp.doc

    val typeRef: TypeRef
        get() = userProp.typeRef

    val defaultValueText: String?
        get() = userProp.defaultValueText
}

@Suppress("UNCHECKED_CAST")
internal fun AbstractProp.toLsiDtoAbstractPropView(): LsiDtoAbstractPropView =
    when (this) {
        is UserProp -> LsiUserPropView(this)
        is DtoProp<*, *> -> LsiDtoPropView(this as DtoProp<ImmutableType, ImmutableProp>)
        else -> error("Unsupported abstract prop type: ${this::class.qualifiedName}")
    }

internal fun LsiDtoPropView.resolveDtoConverterMetadata(
    dtoModifiers: Set<DtoModifier>,
): ConverterMetadata? {
    val baseProp = tailBaseProp
    val metadata = baseProp.converterMetadata
    if (metadata != null) {
        return metadata
    }
    val propFuncName = funcName
    if ("id" == propFuncName) {
        val targetMetadata = baseProp.targetType!!.idProp!!.converterMetadata
        if (targetMetadata != null && baseProp.isList && !dtoModifiers.contains(DtoModifier.SPECIFICATION)) {
            return targetMetadata.toListMetadata()
        }
        return targetMetadata
    }
    if ("associatedInEq" == propFuncName || "associatedInNe" == propFuncName) {
        return baseProp.targetType!!.idProp!!.converterMetadata
    }
    if ("associatedIdIn" == propFuncName || "associatedIdNotIn" == propFuncName) {
        return baseProp.targetType!!.idProp!!.converterMetadata?.toListMetadata()
    }
    if (baseProp.idViewBaseProp !== null) {
        return baseProp.idViewBaseProp!!.targetType!!.idProp!!.converterMetadata?.let {
            if (baseProp.isList) {
                it.toListMetadata()
            } else {
                it
            }
        }
    }
    return null
}
