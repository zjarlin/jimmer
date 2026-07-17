package org.babyfish.jimmer.compiler.lsi.apt

import com.sun.source.util.Trees
import org.babyfish.jimmer.compiler.lsi.LsiFrontendOptions
import site.addzero.lsi.core.LsiLanguage
import site.addzero.lsi.core.LsiLocation
import site.addzero.lsi.core.LsiOrigin
import site.addzero.lsi.core.LsiOriginKind
import site.addzero.lsi.core.LsiPosition
import site.addzero.lsi.core.LsiSource
import site.addzero.lsi.core.LsiSourceKind
import site.addzero.lsi.model.LsiModality
import site.addzero.lsi.model.LsiVisibility
import javax.annotation.processing.ProcessingEnvironment
import javax.lang.model.element.Element
import javax.lang.model.element.Modifier

internal class AptLsiContext(
    val processingEnvironment: ProcessingEnvironment,
    val frontendOptions: LsiFrontendOptions,
) {

    val elements = processingEnvironment.elementUtils

    val types = processingEnvironment.typeUtils

    private val trees = try {
        Trees.instance(processingEnvironment)
    } catch (_: IllegalArgumentException) {
        null
    }

    fun documentation(element: Element): String? {
        return elements.getDocComment(element)
            ?.trim()
            ?.takeIf(String::isNotEmpty)
    }

    fun source(element: Element): LsiSource? {
        val compilationUnit = trees?.getPath(element)?.compilationUnit ?: return null
        val sourceFile = compilationUnit.sourceFile ?: return null
        val path = sourceFile.toUri().path
            ?.takeIf(String::isNotBlank)
            ?: sourceFile.name.takeIf(String::isNotBlank)
            ?: return null
        return LsiSource.of(
            path = path,
            language = LsiLanguage.JAVA,
            kind = LsiSourceKind.SOURCE,
        )
    }

    fun location(element: Element): LsiLocation? {
        val treePath = trees?.getPath(element) ?: return null
        val compilationUnit = treePath.compilationUnit
        val source = source(element) ?: return null
        val sourcePositions = trees.sourcePositions
        val startOffset = sourcePositions.getStartPosition(compilationUnit, treePath.leaf)
        val endOffset = sourcePositions.getEndPosition(compilationUnit, treePath.leaf)
        if (startOffset < 0 || endOffset < 0) {
            return null
        }
        val lineMap = compilationUnit.lineMap ?: return null
        val start = LsiPosition(
            line = lineMap.getLineNumber(startOffset).toInt(),
            column = lineMap.getColumnNumber(startOffset).toInt(),
        )
        val end = LsiPosition(
            line = lineMap.getLineNumber(endOffset).toInt(),
            column = lineMap.getColumnNumber(endOffset).toInt(),
        )
        return LsiLocation(
            source = source,
            start = start,
            end = if (end >= start) end else start,
        )
    }

    fun origin(element: Element): LsiOrigin {
        val source = source(element)
        return if (source != null) {
            LsiOrigin(
                kind = LsiOriginKind.SOURCE,
                source = source,
            )
        } else {
            LsiOrigin(LsiOriginKind.BINARY)
        }
    }
}

internal fun Element.toLsiVisibility(): LsiVisibility {
    return when {
        Modifier.PUBLIC in modifiers -> LsiVisibility.PUBLIC
        Modifier.PROTECTED in modifiers -> LsiVisibility.PROTECTED
        Modifier.PRIVATE in modifiers -> LsiVisibility.PRIVATE
        else -> LsiVisibility.PACKAGE_PRIVATE
    }
}

internal fun Element.toLsiModality(): LsiModality {
    return when {
        Modifier.SEALED in modifiers -> LsiModality.SEALED
        Modifier.ABSTRACT in modifiers -> LsiModality.ABSTRACT
        Modifier.FINAL in modifiers || Modifier.PRIVATE in modifiers || Modifier.STATIC in modifiers -> {
            LsiModality.FINAL
        }
        else -> LsiModality.OPEN
    }
}
