package site.addzero.lsi.ksp

import com.google.devtools.ksp.getClassDeclarationByName
import com.google.devtools.ksp.isAbstract
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.FileLocation
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSDeclaration
import com.google.devtools.ksp.symbol.KSFile
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
import site.addzero.lsi.model.LsiFrontendDocumentationConvention
import site.addzero.lsi.model.LsiFrontendOptions
import site.addzero.lsi.model.LsiGeneratedPeerDocumentationConvention

internal class KspLsiContext(
    private val resolver: Resolver,
    private val frontendOptions: LsiFrontendOptions,
) {

    fun sourceDocumentation(node: KSNode): String? {
        return (node as? KSDeclaration)?.docString
            ?.trim()
            ?.takeIf(String::isNotEmpty)
    }

    fun documentation(declaration: KSDeclaration): String? {
        sourceDocumentation(declaration)?.let { return it }
        declaration.description()?.let { return it }
        return declaration.generatedPeerDocumentation()
    }

    fun source(node: KSNode): LsiSource? {
        if (node is KSFile) {
            return node.toLsiSource()
        }
        val declaration = node.enclosingDeclaration() ?: return null
        if (declaration.origin != Origin.JAVA && declaration.origin != Origin.KOTLIN) {
            return null
        }
        val file = declaration.containingFile ?: return null
        return file.toLsiSource(declaration.origin)
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
            language = node.origin.toLsiLanguage(),
        )
    }

    private fun KSDeclaration.description(): String? {
        val convention = frontendOptions.documentationConvention ?: return null
        return annotations
            .firstOrNull { annotation -> annotation.isDocumentation(convention) }
            ?.arguments
            ?.firstOrNull { argument -> argument.name?.asString() == convention.valueMemberName }
            ?.value
            ?.let { value -> value as? String }
            ?.takeIf(String::isNotBlank)
    }

    private fun KSDeclaration.generatedPeerDocumentation(): String? {
        val convention = frontendOptions.documentationConvention?.generatedPeer ?: return null
        val owner = when (this) {
            is KSClassDeclaration -> this
            is KSPropertyDeclaration -> parentDeclaration as? KSClassDeclaration
            is KSFunctionDeclaration -> parentDeclaration as? KSClassDeclaration
            else -> null
        } ?: return null
        if (!owner.isGeneratedPeerOwner(convention)) {
            return null
        }
        val ownerSource = source(owner)
        if (
            owner.origin != Origin.JAVA_LIB &&
            owner.origin != Origin.KOTLIN_LIB &&
            ownerSource?.kind != LsiSourceKind.GENERATED
        ) {
            return null
        }
        val qualifiedName = owner.qualifiedName?.asString()?.takeIf(String::isNotBlank) ?: return null
        val generatedPeer = resolver.getClassDeclarationByName(
            "${qualifiedName}${convention.typeSuffix}",
        ) ?: return null
        if (this is KSClassDeclaration) {
            return generatedPeer.description()
        }
        val propertyName = when (this) {
            is KSPropertyDeclaration -> simpleName.asString()
            is KSFunctionDeclaration -> {
                if (!isLsiJavaPropertyGetter()) {
                    return null
                }
                toLsiJavaPropertyName(frontendOptions)
            }
            else -> return null
        }
        return generatedPeer.declarations
            .firstNotNullOfOrNull { member ->
                val matches = when (member) {
                    is KSPropertyDeclaration -> member.simpleName.asString() == propertyName
                    is KSFunctionDeclaration -> member.isGeneratedPeerSetter(propertyName, convention)
                    else -> false
                }
                if (matches) member.description() else null
            }
    }

    private fun KSFunctionDeclaration.isGeneratedPeerSetter(
        propertyName: String,
        convention: LsiGeneratedPeerDocumentationConvention,
    ): Boolean {
        val methodName = simpleName.asString()
        val prefix = convention.propertySetterPrefix
        if (!methodName.startsWith(prefix) || methodName.length == prefix.length || parameters.size != 1) {
            return false
        }
        val suffix = methodName.substring(prefix.length)
        return suffix == propertyName || suffix.decapitalizeFirst() == propertyName
    }

    private fun KSAnnotation.isDocumentation(
        convention: LsiFrontendDocumentationConvention,
    ): Boolean {
        return annotationType.resolve().declaration.qualifiedName?.asString() ==
            convention.annotationTypeId.requireTypeQualifiedName()
    }

    private fun KSClassDeclaration.isGeneratedPeerOwner(
        convention: LsiGeneratedPeerDocumentationConvention,
    ): Boolean {
        return annotations.any { annotation ->
            val qualifiedName = annotation.annotationType.resolve().declaration.qualifiedName?.asString()
            convention.ownerAnnotationTypeIds.any { id ->
                qualifiedName == id.requireTypeQualifiedName()
            }
        }
    }

    private fun KSFile.toLsiSource(origin: Origin = this.origin): LsiSource? {
        if (origin != Origin.JAVA && origin != Origin.KOTLIN) {
            return null
        }
        val path = filePath.takeIf(String::isNotBlank)
            ?: fileName.takeIf(String::isNotBlank)
            ?: return null
        return LsiSource.of(
            path = path,
            language = origin.toLsiLanguage(),
            kind = path.toLsiSourceKind(),
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
        this is KSClassDeclaration && isAbstract() -> LsiModality.ABSTRACT
        this is KSPropertyDeclaration && isAbstract() -> LsiModality.ABSTRACT
        this is KSFunctionDeclaration && isAbstract -> LsiModality.ABSTRACT
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

private fun String.decapitalizeFirst(): String {
    return first().lowercaseChar() + substring(1)
}
