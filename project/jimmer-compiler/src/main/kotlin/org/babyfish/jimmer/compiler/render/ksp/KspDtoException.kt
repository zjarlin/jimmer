package org.babyfish.jimmer.compiler.render.ksp

import java.lang.RuntimeException

internal class KspDtoException(
    message: String, cause: Throwable? = null
): RuntimeException(message, cause)
