package org.babyfish.jimmer.compiler.lsi.ksp

import com.google.devtools.ksp.symbol.FileLocation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSNode
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.symbol.Modifier
import com.google.devtools.ksp.symbol.Origin
import site.addzero.lsi.core.LsiLanguage
import site.addzero.lsi.core.LsiLocation
import site.addzero.lsi.core.LsiOrigin
import site.addzero.lsi.core.LsiOriginKind
import site.addzero.lsi.core.LsiPosition
import site.addzero.lsi.core.LsiSource
import site.addzero.lsi.core.LsiSourceKind
import site.addzero.lsi.model.LsiModality
import site.addzero.lsi.model.LsiVisibility

internal class KspLsiContext {

    fun documentation(declaration: KSDeclaration): String? {
        return declaration.docString
            ?.trim()
            ?.takeIf(String::isNotEmpty)
    }

    fun source(node: KSNode): LsiSource? {
        val declaration = node.enclosingDeclaration() ?: return null
        if (declaration.origin != Origin.JAVA && declaration.origin != Origin.KOTLIN) {
            return null
        }
        val file = declaration.containingFile ?: return null
        val path = file.filePath.takeIf(String::isNotBlank)
            ?: file.fileName.takeIf(String::isNotBlank)
            ?: return null
        return LsiSource.of(
            path = path,
            language = declaration.origin.toLsiLanguage(),
            kind = path.toLsiSourceKind(),
        )
    }

    fun location(node: KSNode): LsiLocation? {
        val fileLocation = node.location as? FileLocation ?: return null
        val source = source(node) ?: LsiSource.of(
            path = fileLocation.filePath,
            language = node.origin.toLsiLanguage(),
            kind = fileLocation.filePath.toLsiSourceKind(),
        )
        val position = LsiPosition(
            line = fileLocation.lineNumber.coerceAtLeast(1),
            column = 1,
        )
        return LsiLocation(
            source = source,
            start = position,
        )
    }

    fun origin(node: KSNode): LsiOrigin {
        val source = source(node)
        val kind = when {
            source?.kind == LsiSourceKind.GENERATED -> LsiOriginKind.GENERATED
            source != null -> LsiOriginKind.SOURCE
            node.origin == Origin.SYNTHETIC -> LsiOriginKind.SYNTHETIC
            else -> LsiOriginKind.BINARY
        }
        return LsiOrigin(
            kind = kind,
            source = source,
        )
    }
}

internal fun KSDeclaration.toLsiVisibility(): LsiVisibility {
    if (Modifier.OVERRIDE in modifiers) {
        val overridden = when (this) {
            is KSPropertyDeclaration -> findOverridee()
            is KSFunctionDeclaration -> findOverridee()
            else -> null
        }
        if (overridden != null) {
            return overridden.toLsiVisibility()
        }
    }
    return when {
        Modifier.PUBLIC in modifiers -> LsiVisibility.PUBLIC
        Modifier.PROTECTED in modifiers -> LsiVisibility.PROTECTED
        Modifier.INTERNAL in modifiers -> LsiVisibility.INTERNAL
        Modifier.PRIVATE in modifiers -> LsiVisibility.PRIVATE
        parentDeclaration != null && parentDeclaration !is KSClassDeclaration -> LsiVisibility.LOCAL
        origin == Origin.JAVA || origin == Origin.JAVA_LIB -> LsiVisibility.PACKAGE_PRIVATE
        else -> LsiVisibility.PUBLIC
    }
}

internal fun KSDeclaration.toLsiModality(): LsiModality {
    return when {
        Modifier.SEALED in modifiers -> LsiModality.SEALED
        Modifier.ABSTRACT in modifiers -> LsiModality.ABSTRACT
        Modifier.OPEN in modifiers -> LsiModality.OPEN
        Modifier.FINAL in modifiers || Modifier.PRIVATE in modifiers -> LsiModality.FINAL
        this is KSClassDeclaration && classKind == com.google.devtools.ksp.symbol.ClassKind.INTERFACE -> {
            LsiModality.ABSTRACT
        }
        else -> LsiModality.FINAL
    }
}

private fun KSNode.enclosingDeclaration(): KSDeclaration? {
    var current: KSNode? = this
    while (current != null) {
        if (current is KSDeclaration) {
            return current
        }
        current = current.parent
    }
    return null
}

private fun Origin.toLsiLanguage(): LsiLanguage {
    return when (this) {
        Origin.KOTLIN, Origin.KOTLIN_LIB -> LsiLanguage.KOTLIN
        Origin.JAVA, Origin.JAVA_LIB -> LsiLanguage.JAVA
        Origin.SYNTHETIC -> LsiLanguage.UNKNOWN
    }
}

private fun String.toLsiSourceKind(): LsiSourceKind {
    val normalized = replace('\\', '/')
    return if (
        normalized.contains("/build/generated/ksp/") ||
        normalized.startsWith("build/generated/ksp/")
    ) {
        LsiSourceKind.GENERATED
    } else {
        LsiSourceKind.SOURCE
    }
}
