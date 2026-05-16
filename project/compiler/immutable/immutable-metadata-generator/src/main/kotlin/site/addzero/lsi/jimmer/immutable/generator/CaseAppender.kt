package site.addzero.lsi.jimmer.immutable.generator

import site.addzero.lsi.poet.LsiCodeBlock

internal class CaseAppender(
    private val argKind: PropertyDispatchArgKind
) {

    fun caseLabel(slotName: String, propName: String): LsiCodeBlock =
        if (argKind.usesIndexedSubject) {
            LsiCodeBlock.of("%L ->\n\t", slotName)
        } else {
            LsiCodeBlock.of("%S ->\n\t", propName)
        }

    fun illegalCaseLabel(): LsiCodeBlock? =
        if (argKind.usesIndexedSubject) {
            LsiCodeBlock.of("-1 ->\n\t")
        } else {
            null
        }
}
