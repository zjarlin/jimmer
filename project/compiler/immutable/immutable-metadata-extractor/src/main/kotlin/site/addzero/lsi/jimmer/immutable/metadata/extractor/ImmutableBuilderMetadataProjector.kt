package site.addzero.lsi.jimmer.immutable.metadata.extractor

import site.addzero.lsi.codegen.PRODUCER
import site.addzero.lsi.codegen.nonJimmerMethodAnnotations
import site.addzero.lsi.jimmer.immutable.metadata.model.ImmutableBuilderSetterMetadata
import site.addzero.lsi.jimmer.immutable.metadata.model.ImmutableBuilderTypeMetadata
import site.addzero.lsi.jimmer.meta.ImmutableProp
import site.addzero.lsi.jimmer.meta.ImmutableType

fun ImmutableType.toBuilderTypeMetadata(
    excludedUserTypePrefixes: List<String>,
): ImmutableBuilderTypeMetadata =
    ImmutableBuilderTypeMetadata(
        // 覆盖来源：project/compiler/immutable/jimmer-ksp-immutable/.../BuilderGenerator 的 `ImmutableType` 级读取
        // 迁移说明：Builder 生成链所需的类型命名、DraftImpl 入口、可见性控制槽位与 setter 列表前移到 metadata-extractor，generator 不再自行投影 ImmutableType
        className = lsiClassName,
        producerClassName = lsiDraftClassName(PRODUCER),
        draftImplClassName = lsiDraftClassName(PRODUCER, "DraftImpl"),
        visibleSlotNames = properties.values
            .filter { it.isVisibilityControllable() }
            .map { it.slotName },
        setters = properties.values
            .filter { !it.isImplementationFormula && it.manyToManyViewBaseProp === null }
            .map { it.toBuilderSetterMetadata(excludedUserTypePrefixes) },
    )

private fun ImmutableProp.toBuilderSetterMetadata(
    excludedUserTypePrefixes: List<String>,
): ImmutableBuilderSetterMetadata =
    ImmutableBuilderSetterMetadata(
        // 覆盖来源：project/compiler/immutable/jimmer-ksp-immutable/.../BuilderGenerator.addSetter 的属性级 `ImmutableProp` 读取
        // 迁移说明：Builder setter 所需的参数类型、返回类型、可见性槽位与非 Jimmer 注解复制结果前移到 metadata-extractor，generator 仅负责排版
        name = name,
        parameterLsiTypeName = toLsiTypeName().copyNullable(true),
        returnTypeName = declaringType.lsiDraftClassName("Builder"),
        ownerProducerClassName = declaringType.lsiDraftClassName(PRODUCER),
        slotName = slotName,
        isNullable = isNullable,
        lsiAnnotations = nonJimmerMethodAnnotations(excludedUserTypePrefixes),
    )

private fun ImmutableProp.isVisibilityControllable(): Boolean {
    // 覆盖来源：project/compiler/immutable/jimmer-ksp-immutable/.../BuilderGenerator.isVisibilityControllable
    // 迁移说明：Builder 的可见性控制判定前移到 metadata-extractor，避免 generator projector 再直接保留这段属性语义判断
    return isBaseProp ||
        dependencies.isNotEmpty() ||
        idViewBaseProp !== null ||
        manyToManyViewBaseProp !== null
}
