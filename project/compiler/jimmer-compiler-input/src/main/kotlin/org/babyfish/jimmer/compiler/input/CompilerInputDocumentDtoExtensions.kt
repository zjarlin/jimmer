package org.babyfish.jimmer.compiler.input

import site.addzero.lsi.compiler.CompilerInputDocument
import org.babyfish.jimmer.dto.compiler.DtoFile

fun CompilerInputDocument.toDtoFile(): DtoFile {
    return DtoFile(
        source.path,
        relativePath,
        content,
    )
}
