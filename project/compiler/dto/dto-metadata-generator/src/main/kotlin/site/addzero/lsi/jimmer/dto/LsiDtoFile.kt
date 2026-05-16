package site.addzero.lsi.jimmer.dto

import org.babyfish.jimmer.dto.compiler.DtoFile

class LsiDtoFile internal constructor(
    internal val rawDtoFile: DtoFile,
) {
    val absolutePath: String
        get() = rawDtoFile.absolutePath
}

internal fun DtoFile.toLsiDtoFile(): LsiDtoFile = LsiDtoFile(this)
