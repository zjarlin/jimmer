package org.babyfish.jimmer.compiler.dto

import site.addzero.lsi.model.sourceLsiAnnotation

import site.addzero.lsi.core.LsiLanguage
import site.addzero.lsi.jimmer.dto.DtoBaseProp
import site.addzero.lsi.jimmer.dto.DtoGraph
import site.addzero.lsi.jimmer.dto.DtoProp
import site.addzero.lsi.jimmer.dto.dtoLoadedStateStorageNameOrNull
import site.addzero.lsi.model.LsiAnnotationUseSiteTarget
import site.addzero.lsi.type.LsiPrimitiveKind
import site.addzero.lsi.type.LsiPrimitiveType
import site.addzero.lsi.model.LsiAnnotation
import site.addzero.lsi.model.LsiCodeBlock
import site.addzero.lsi.model.LsiField
import site.addzero.lsi.model.LsiModifier
import site.addzero.lsi.model.LsiProperty
import site.addzero.lsi.clazz.LsiClass

/** 将基于实体构造 DTO 时的加载状态初始化表达式降低为平台中立代码。 */
internal fun DtoBaseProp.toBaseLoadedStateInitializerPoetCodeBlock(
    graph: DtoGraph,
    targetLanguage: LsiLanguage,
    accessorName: String,
    baseParameterName: String,
): LsiCodeBlock? {
    dtoLoadedStateStorageNameOrNull(graph, targetLanguage) ?: return null
    return LsiCodeBlock.build {
        name(accessorName)
        text(".")
        name("isLoaded")
        text("(")
        name(baseParameterName)
        text(")")
    }
}

/** 将 Java DTO 的加载状态存储降低为平台中立字段。 */
internal fun DtoProp.toLoadedStateStoragePoetFieldOrNull(
    graph: DtoGraph,
    visibility: LsiModifier,
): LsiField? {
    require(visibility in LOADED_STATE_FIELD_VISIBILITIES) {
        "DTO loaded-state field requires public, protected or private visibility"
    }
    val storageName = dtoLoadedStateStorageNameOrNull(graph, LsiLanguage.JAVA) ?: return null
    return LsiField(
        name = storageName,
        type = BOOLEAN_TYPE,
        modifiers = setOf(visibility),
    )
}

/** 将 Kotlin DTO 的加载状态存储降低为平台中立属性。 */
internal fun DtoProp.toLoadedStateStoragePoetPropertyOrNull(
    graph: DtoGraph,
    mutable: Boolean,
): LsiProperty? {
    val storageName = dtoLoadedStateStorageNameOrNull(graph, LsiLanguage.KOTLIN) ?: return null
    return LsiProperty(
        name = storageName,
        type = BOOLEAN_TYPE,
        mutable = mutable,
        annotations = listOf(
            sourceLsiAnnotation(API_IGNORE_TYPE_NAME.id),
            sourceLsiAnnotation(
                type = JSON_IGNORE_TYPE_NAME.id,
                useSiteTarget = LsiAnnotationUseSiteTarget.GETTER,
            ),
        ),
        initializer = LsiCodeBlock.build {
            name(storageName)
        },
    )
}

private val BOOLEAN_TYPE = LsiPrimitiveType(LsiPrimitiveKind.BOOLEAN)

private val LOADED_STATE_FIELD_VISIBILITIES = setOf(
    LsiModifier.PUBLIC,
    LsiModifier.PROTECTED,
    LsiModifier.PRIVATE,
)

private val API_IGNORE_TYPE_NAME = JimmerDtoPoetTypeNames.create(
    packageName = "org.babyfish.jimmer.client",
    simpleNames = listOf("ApiIgnore"),
)

private val JSON_IGNORE_TYPE_NAME = JimmerDtoPoetTypeNames.create(
    packageName = "com.fasterxml.jackson.annotation",
    simpleNames = listOf("JsonIgnore"),
)

/** 返回加载状态属性注解所需的稳定源码类型名。 */
internal val DTO_LOADED_STATE_POET_TYPE_NAMES: List<LsiClass> = listOf(
    API_IGNORE_TYPE_NAME,
    JSON_IGNORE_TYPE_NAME,
)
