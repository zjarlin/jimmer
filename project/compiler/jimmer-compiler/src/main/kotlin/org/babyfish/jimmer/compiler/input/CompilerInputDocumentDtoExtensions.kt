package org.babyfish.jimmer.compiler.input

import org.babyfish.jimmer.compiler.CompilerInputDocument
import org.babyfish.jimmer.dto.compiler.DtoFile

internal fun CompilerInputDocument.toDtoFile(): DtoFile {
    return DtoFile(
        source.path,
        relativePath,
        content,
    )
}
