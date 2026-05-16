package site.addzero.lsi.jimmer.dto

class DtoException @JvmOverloads constructor(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
