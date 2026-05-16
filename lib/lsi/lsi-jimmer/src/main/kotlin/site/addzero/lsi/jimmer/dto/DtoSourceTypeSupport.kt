package site.addzero.lsi.jimmer.dto

import org.babyfish.jimmer.dto.compiler.DtoFile
import site.addzero.lsi.clazz.LsiClass
import site.addzero.lsi.jimmer.EMBEDDABLE
import site.addzero.lsi.jimmer.ENTITY
import site.addzero.lsi.jimmer.IMMUTABLE
import site.addzero.lsi.jimmer.isJimmerEmbeddable
import site.addzero.lsi.jimmer.isJimmerEntity
import site.addzero.lsi.jimmer.isJimmerImmutable
import site.addzero.lsi.resolver.LsiResolver

fun resolveDtoSourceTypeOrNull(
    dtoFile: DtoFile,
    sourceTypeName: String,
    resolver: LsiResolver,
    include: (LsiClass) -> Boolean,
    fail: (String) -> Nothing,
): LsiClass? {
    val sourceType = resolver.findClassByQualifiedName(sourceTypeName)
        ?: fail(
            "Failed to parse \"" +
                dtoFile.absolutePath +
                "\": No immutable type \"" +
                sourceTypeName +
                "\""
        )
    if (!include(sourceType)) {
        return null
    }
    if (!sourceType.isJimmerEntity &&
        !sourceType.isJimmerEmbeddable &&
        !sourceType.isJimmerImmutable) {
        fail(
            "Failed to parse \"" +
                dtoFile.absolutePath +
                "\": the \"" +
                sourceTypeName +
                "\" is not decorated by \"@" +
                ENTITY +
                "\", \"" +
                EMBEDDABLE +
                "\" or \"" +
                IMMUTABLE +
                "\""
        )
    }
    return sourceType
}
