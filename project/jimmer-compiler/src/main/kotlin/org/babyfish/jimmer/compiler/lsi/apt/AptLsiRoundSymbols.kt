package org.babyfish.jimmer.compiler.lsi.apt

import javax.annotation.processing.ProcessingEnvironment
import javax.annotation.processing.RoundEnvironment
import javax.lang.model.element.Element
import javax.lang.model.element.ElementKind
import javax.lang.model.element.ExecutableElement
import javax.lang.model.element.TypeElement
import javax.lang.model.element.VariableElement
import site.addzero.lsi.core.LsiSymbolId
import org.babyfish.jimmer.compiler.lsi.LsiFrontendOptions

/**
 * 当前 APT 轮内的原生符号索引，不得跨轮保存。
 */
internal data class AptLsiRoundSymbols(
    val rootTypes: List<TypeElement>,
    val elementsById: Map<LsiSymbolId, Element>,
) {
    companion object {
        val EMPTY = AptLsiRoundSymbols(emptyList(), emptyMap())
    }
}

internal fun RoundEnvironment.toAptLsiRoundSymbols(
    processingEnvironment: ProcessingEnvironment,
    frontendOptions: LsiFrontendOptions,
    additionalRootTypes: Iterable<TypeElement> = emptyList(),
): AptLsiRoundSymbols {
    val rootTypes = (rootElements.filterIsInstance<TypeElement>() + additionalRootTypes)
        .distinctBy { type -> type.qualifiedName.toString() }
        .sortedBy { type -> type.qualifiedName.toString() }
    return AptLsiRoundSymbolIndexer(processingEnvironment, frontendOptions).index(rootTypes)
}

private class AptLsiRoundSymbolIndexer(
    processingEnvironment: ProcessingEnvironment,
    frontendOptions: LsiFrontendOptions,
) {
    private val context = AptLsiContext(processingEnvironment, frontendOptions)

    private val elementsById = linkedMapOf<LsiSymbolId, Element>()

    fun index(rootTypes: List<TypeElement>): AptLsiRoundSymbols {
        rootTypes.forEach(::indexType)
        return AptLsiRoundSymbols(rootTypes, elementsById.toMap())
    }

    private fun indexType(type: TypeElement) {
        val typeId = LsiSymbolId.type(type.qualifiedName.toString())
        elementsById[typeId] = type
        type.typeParameters.forEach { parameter ->
            elementsById[LsiSymbolId.typeParameter(typeId, parameter.simpleName.toString())] = parameter
        }
        for (element in type.enclosedElements) {
            when (element) {
                is TypeElement -> indexType(element)
                is ExecutableElement -> if (
                    element.kind == ElementKind.METHOD || element.kind == ElementKind.CONSTRUCTOR
                ) {
                    indexCallable(element)
                }
                is VariableElement -> when (element.kind) {
                    ElementKind.ENUM_CONSTANT -> {
                        elementsById[LsiSymbolId.enumEntry(typeId, element.simpleName.toString())] = element
                    }
                    ElementKind.FIELD -> {
                        elementsById[LsiSymbolId.field(typeId, element.simpleName.toString())] = element
                    }
                    else -> Unit
                }
            }
        }
    }

    private fun indexCallable(callable: ExecutableElement) {
        val callableId = context.toLsiCallableId(callable)
        elementsById[callableId] = callable
        callable.typeParameters.forEach { parameter ->
            elementsById[LsiSymbolId.typeParameter(callableId, parameter.simpleName.toString())] = parameter
        }
        callable.parameters.forEachIndexed { index, parameter ->
            elementsById[
                LsiSymbolId.parameter(callableId, index, parameter.simpleName.toString())
            ] = parameter
        }
    }
}
