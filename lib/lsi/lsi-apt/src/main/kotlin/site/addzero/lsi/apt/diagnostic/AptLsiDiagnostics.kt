package site.addzero.lsi.apt.diagnostic

import site.addzero.lsi.codegen.GeneratorException
import site.addzero.lsi.diagnostic.MetaException
import javax.lang.model.element.Element

object AptLsiDiagnostics {

    @JvmStatic
    fun metaException(element: Element, reason: String): MetaException =
        MetaException(element.toLsiDiagnosticAnchor(), reason)

    @JvmStatic
    fun metaException(
        element: Element,
        reason: String,
        cause: Throwable
    ): MetaException =
        MetaException(element.toLsiDiagnosticAnchor(), reason, cause)

    @JvmStatic
    fun generatorException(message: String, cause: Throwable): GeneratorException =
        GeneratorException(message, cause)
}
