package org.babyfish.jimmer.compiler.render.apt

internal class AptDtoException @JvmOverloads constructor(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
