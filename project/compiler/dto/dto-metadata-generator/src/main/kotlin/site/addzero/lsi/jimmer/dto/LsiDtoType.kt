package site.addzero.lsi.jimmer.dto

import org.babyfish.jimmer.dto.compiler.Anno
import org.babyfish.jimmer.dto.compiler.AbstractProp
import org.babyfish.jimmer.dto.compiler.DtoModifier
import org.babyfish.jimmer.dto.compiler.DtoType
import org.babyfish.jimmer.dto.compiler.TypeRef
import site.addzero.lsi.jimmer.meta.ImmutableProp
import site.addzero.lsi.jimmer.meta.ImmutableType
import site.addzero.lsi.resolver.LsiResolver

internal class LsiDtoType internal constructor(
    private val dtoType: DtoType<ImmutableType, ImmutableProp>,
) {
    val dtoFile: LsiDtoFile
        get() = dtoType.dtoFile.toLsiDtoFile()

    val packageName: String
        get() = dtoType.packageName

    val name: String?
        get() = dtoType.name

    val baseType: ImmutableType
        get() = dtoType.baseType

    val dtoPropViews: List<LsiDtoPropView>
        get() = dtoType.dtoProps.map(::LsiDtoPropView)

    val userPropViews: List<LsiUserPropView>
        get() = dtoType.userProps.map(::LsiUserPropView)

    val propViews: List<LsiDtoAbstractPropView>
        get() = dtoType.props.map(AbstractProp::toLsiDtoAbstractPropView)

    val annotations: List<Anno>
        get() = dtoType.annotations

    val doc: String?
        get() = dtoType.doc

    val superInterfaces: List<TypeRef>
        get() = dtoType.superInterfaces

    val modifiers: Set<DtoModifier>
        get() = dtoType.modifiers

    val isFocusedRecursion: Boolean
        get() = dtoType.isFocusedRecursion

    val hiddenFlatPropViews: List<LsiDtoPropView>
        get() = dtoType.hiddenFlatProps.map(::LsiDtoPropView)

    internal fun analyzeInterfaceMembers(resolver: LsiResolver): DtoInterfaceMembers =
        analyzeDtoInterfaceMembers(resolver, dtoType)
}

internal fun DtoType<ImmutableType, ImmutableProp>.toLsiDtoType(): LsiDtoType = LsiDtoType(this)

internal fun analyzeDtoInterfaceMembers(
    resolver: LsiResolver,
    dtoType: LsiDtoType,
): DtoInterfaceMembers =
    dtoType.analyzeInterfaceMembers(resolver)
