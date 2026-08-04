package site.addzero.lsi.apt

import javax.annotation.processing.ProcessingEnvironment
import javax.annotation.processing.RoundEnvironment
import javax.lang.model.element.Element
import javax.lang.model.element.ElementKind
import javax.lang.model.element.ExecutableElement
import javax.lang.model.element.PackageElement
import javax.lang.model.element.TypeElement
import javax.lang.model.element.VariableElement
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.model.LsiFrontendOptions

/**
 * 当前 APT 轮内的原生符号索引，不得跨轮保存。
 */
data class AptLsiRoundSymbols(
    val rootTypes: List<TypeElement>,
    val packageElements: List<PackageElement>,
    val elementsById: Map<LsiSymbolId, Element>,
    val sourceRootTypes: List<TypeElement>,
    val sourcePackageElements: List<PackageElement>,
) {
    companion object {
        val EMPTY = AptLsiRoundSymbols(emptyList(), emptyList(), emptyMap(), emptyList(), emptyList())
    }
}

fun RoundEnvironment.toAptLsiRoundSymbols(
    processingEnvironment: ProcessingEnvironment,
    frontendOptions: LsiFrontendOptions,
    additionalRootTypes: Iterable<TypeElement> = emptyList(),
): AptLsiRoundSymbols {
    val sourceRootTypes = rootElements.filterIsInstance<TypeElement>()
        .distinctBy { type -> type.qualifiedName.toString() }
        .sortedBy { type -> type.qualifiedName.toString() }
    val sourcePackageElements = rootElements.filterIsInstance<PackageElement>()
        .distinctBy { packageElement -> packageElement.qualifiedName.toString() }
        .sortedBy { packageElement -> packageElement.qualifiedName.toString() }
    val rootTypes = (sourceRootTypes + additionalRootTypes)
        .distinctBy { type -> type.qualifiedName.toString() }
        .sortedBy { type -> type.qualifiedName.toString() }
    val packageElements = buildList {
        addAll(rootElements.filterIsInstance<PackageElement>())
        rootTypes.mapTo(this) { type -> processingEnvironment.elementUtils.getPackageOf(type) }
    }.distinctBy { packageElement -> packageElement.qualifiedName.toString() }
        .sortedBy { packageElement -> packageElement.qualifiedName.toString() }
    return AptLsiRoundSymbolIndexer(processingEnvironment, frontendOptions).index(
        rootTypes = rootTypes,
        packageElements = packageElements,
        sourceRootTypes = sourceRootTypes,
        sourcePackageElements = sourcePackageElements,
    )
}

private class AptLsiRoundSymbolIndexer(
    processingEnvironment: ProcessingEnvironment,
    frontendOptions: LsiFrontendOptions,
) {
    private val context = AptLsiContext(processingEnvironment, frontendOptions)

    private val elementsById = linkedMapOf<LsiSymbolId, Element>()

    fun index(
        rootTypes: List<TypeElement>,
        packageElements: List<PackageElement>,
        sourceRootTypes: List<TypeElement>,
        sourcePackageElements: List<PackageElement>,
    ): AptLsiRoundSymbols {
        packageElements.forEach(::indexPackage)
        rootTypes.forEach(::indexType)
        return AptLsiRoundSymbols(
            rootTypes = rootTypes,
            packageElements = packageElements,
            elementsById = elementsById.toMap(),
            sourceRootTypes = sourceRootTypes,
            sourcePackageElements = sourcePackageElements,
        )
    }

    private fun indexPackage(packageElement: PackageElement) {
        elementsById[LsiSymbolId.packageScope(packageElement.qualifiedName.toString())] = packageElement
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
