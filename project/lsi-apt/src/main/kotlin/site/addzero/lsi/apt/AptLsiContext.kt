package site.addzero.lsi.apt

import com.sun.source.util.Trees
import site.addzero.lsi.core.LsiLanguage
import site.addzero.lsi.core.LsiLocation
import site.addzero.lsi.core.LsiOrigin
import site.addzero.lsi.core.LsiOriginKind
import site.addzero.lsi.core.LsiPosition
import site.addzero.lsi.core.LsiSource
import site.addzero.lsi.core.LsiSourceKind
import site.addzero.lsi.model.LsiModality
import site.addzero.lsi.model.LsiVisibility
import site.addzero.lsi.model.LsiFrontendOptions
import site.addzero.lsi.model.LsiGeneratedPeerDocumentationConvention
import java.lang.reflect.Method
import javax.annotation.processing.ProcessingEnvironment
import javax.lang.model.element.Element
import javax.lang.model.element.ExecutableElement
import javax.lang.model.element.Modifier
import javax.lang.model.element.PackageElement
import javax.lang.model.element.TypeElement
import javax.lang.model.util.Elements
import javax.tools.FileObject
import javax.tools.JavaFileObject

internal class AptLsiContext(
    val processingEnvironment: ProcessingEnvironment,
    val frontendOptions: LsiFrontendOptions,
    private val sourceRootTypeNames: Set<String> = emptySet(),
    private val sourceRootPackageNames: Set<String> = emptySet(),
    private val knownSourceRootTypes: Map<String, LsiSource> = emptyMap(),
    private val fallbackSourceKind: LsiSourceKind = LsiSourceKind.SOURCE,
) {

    val elements = processingEnvironment.elementUtils

    val types = processingEnvironment.typeUtils

    private val trees = try {
        Trees.instance(processingEnvironment)
    } catch (_: IllegalArgumentException) {
        null
    }

    /*
     * Elements.getFileObjectOf 晚于 Java 8 引入。反射路径用于获取精确文件名；旧 JDK
     * 或 APT 包装器不可用时，改用当前轮显式传入的源码根生成稳定逻辑路径。
     */
    private val fileObjectOf: Method? = runCatching {
        Elements::class.java.getMethod("getFileObjectOf", Element::class.java)
    }.getOrNull()

    fun sourceDocumentation(element: Element): String? {
        return elements.getDocComment(element)
            ?.trim()
            ?.takeIf(String::isNotEmpty)
    }

    fun documentation(element: Element): String? {
        sourceDocumentation(element)?.let { return it }
        element.description()?.let { return it }
        return element.generatedPeerDocumentation()
    }

    fun source(element: Element): LsiSource? {
        val sourceFile = fileObjectOf
            ?.let { method ->
                runCatching { method.invoke(elements, element) as? FileObject }
                    .getOrNull()
            }
            ?.takeIf { fileObject ->
                (fileObject as? JavaFileObject)?.kind == JavaFileObject.Kind.SOURCE
            }
            ?: trees?.getPath(element)?.compilationUnit?.sourceFile
        if (sourceFile == null) {
            return fallbackSource(element)
        }
        val path = sourceFile.toUri().path
            ?.takeIf(String::isNotBlank)
            ?: sourceFile.name.takeIf(String::isNotBlank)
            ?: return null
        return LsiSource.of(
            path = path,
            language = LsiLanguage.JAVA,
            kind = path.toLsiSourceKind(),
        )
    }

    private fun fallbackSource(element: Element): LsiSource? {
        val path = when (element) {
            is PackageElement -> {
                val packageName = element.qualifiedName.toString()
                if (packageName !in sourceRootPackageNames) {
                    return null
                }
                if (packageName.isEmpty()) "package-info.java" else packageName.replace('.', '/') + "/package-info.java"
            }
            else -> {
                val topLevelType = element.topLevelEnclosingType() ?: return null
                val qualifiedName = topLevelType.qualifiedName.toString()
                knownSourceRootTypes[qualifiedName]?.let { source -> return source }
                if (qualifiedName !in sourceRootTypeNames) {
                    return null
                }
                qualifiedName.replace('.', '/') + ".java"
            }
        }
        return LsiSource.of(
            path = path,
            language = LsiLanguage.JAVA,
            kind = fallbackSourceKind,
        )
    }

    private fun Element.topLevelEnclosingType(): TypeElement? {
        var type = when (this) {
            is TypeElement -> this
            else -> generateSequence(enclosingElement) { enclosing -> enclosing.enclosingElement }
                .filterIsInstance<TypeElement>()
                .firstOrNull()
        } ?: return null
        while (type.enclosingElement is TypeElement) {
            type = type.enclosingElement as TypeElement
        }
        return type
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
            LsiOrigin(
                kind = LsiOriginKind.BINARY,
                language = LsiLanguage.JAVA,
            )
        }
    }

    private fun Element.description(): String? {
        val convention = frontendOptions.documentationConvention ?: return null
        val annotation = annotationMirrors.firstOrNull { mirror ->
            val annotationType = mirror.annotationType.asElement() as? TypeElement
            annotationType?.qualifiedName?.toString() == convention.annotationTypeId.requireTypeQualifiedName()
        } ?: return null
        return elements.getElementValuesWithDefaults(annotation)
            .entries
            .firstOrNull { (member, _) -> member.simpleName.contentEquals(convention.valueMemberName) }
            ?.value
            ?.value
            ?.let { value -> value as? String }
            ?.takeIf(String::isNotBlank)
    }

    private fun Element.generatedPeerDocumentation(): String? {
        val convention = frontendOptions.documentationConvention?.generatedPeer ?: return null
        val owner = when (this) {
            is TypeElement -> this
            is ExecutableElement -> enclosingElement as? TypeElement
            else -> null
        } ?: return null
        if (!owner.isGeneratedPeerOwner(convention)) {
            return null
        }
        val ownerSource = source(owner)
        if (ownerSource != null && ownerSource.kind != LsiSourceKind.GENERATED) {
            return null
        }
        val generatedPeer = elements.getTypeElement(
            "${owner.qualifiedName}${convention.typeSuffix}",
        ) ?: return null
        if (this is TypeElement) {
            return generatedPeer.description()
        }
        val property = this as ExecutableElement
        if (!property.isLsiPropertyGetter()) {
            return null
        }
        val propertyName = property.toLsiPropertyName(frontendOptions)
        return generatedPeer.enclosedElements
            .filterIsInstance<ExecutableElement>()
            .firstOrNull { candidate ->
                candidate.isGeneratedPeerSetter(propertyName, convention)
            }
            ?.description()
    }

    private fun ExecutableElement.isGeneratedPeerSetter(
        propertyName: String,
        convention: LsiGeneratedPeerDocumentationConvention,
    ): Boolean {
        val methodName = simpleName.toString()
        val prefix = convention.propertySetterPrefix
        if (!methodName.startsWith(prefix) || methodName.length == prefix.length || parameters.size != 1) {
            return false
        }
        val suffix = methodName.substring(prefix.length)
        return suffix == propertyName || suffix.decapitalizeFirst() == propertyName
    }

    private fun TypeElement.isGeneratedPeerOwner(
        convention: LsiGeneratedPeerDocumentationConvention,
    ): Boolean {
        return annotationMirrors.any { mirror ->
            val annotationType = mirror.annotationType.asElement() as? TypeElement
            convention.ownerAnnotationTypeIds.any { id ->
                annotationType?.qualifiedName?.toString() == id.requireTypeQualifiedName()
            }
        }
    }
}

private fun String.toLsiSourceKind(): LsiSourceKind {
    val normalized = replace('\\', '/')
    return if (
        normalized.contains("/build/generated/") ||
        normalized.startsWith("build/generated/")
    ) {
        LsiSourceKind.GENERATED
    } else {
        LsiSourceKind.SOURCE
    }
}

private fun String.decapitalizeFirst(): String {
    return first().lowercaseChar() + substring(1)
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
