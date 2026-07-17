package org.babyfish.jimmer.compiler.error

import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.model.LsiAnnotation
import site.addzero.lsi.model.LsiAnnotationValue
import site.addzero.lsi.model.LsiEnumEntry
import site.addzero.lsi.model.LsiPrimitiveType
import site.addzero.lsi.model.LsiTypeDeclaration
import site.addzero.lsi.model.LsiTypeDeclarationKind
import site.addzero.lsi.model.LsiTypeRef
import site.addzero.lsi.model.LsiWorkspace

data class ErrorPrecompileOptions(
    val checkedException: Boolean = false,
)

class ErrorPrecompileException(
    val declarationId: LsiSymbolId,
    message: String,
) : IllegalArgumentException(message)

class ErrorPrecompiler(
    private val options: ErrorPrecompileOptions = ErrorPrecompileOptions(),
) {
    fun compile(workspace: LsiWorkspace): ErrorPrecompiledSchema {
        val types = workspace.declarationsOfType<LsiTypeDeclaration>()
            .sortedBy(LsiTypeDeclaration::qualifiedName)
        val families = types
            .filter { type -> type.annotations.hasAnnotation(ERROR_FAMILY_ANNOTATION) }
            .map { type -> compileFamily(type, types) }
        return ErrorPrecompiledSchema(families)
    }

    private fun compileFamily(
        type: LsiTypeDeclaration,
        allTypes: List<LsiTypeDeclaration>,
    ): ErrorFamilyModel {
        if (type.kind != LsiTypeDeclarationKind.ENUM) {
            throw ErrorPrecompileException(
                declarationId = type.id,
                message = "Only enum can be decorated by '@${ERROR_FAMILY_ANNOTATION.value}'",
            )
        }
        val longSimpleName = type.longSimpleName(allTypes)
        val exceptionStem = longSimpleName.errorExceptionStem()
        val familyAnnotation = requireNotNull(type.annotations.annotation(ERROR_FAMILY_ANNOTATION))
        val family = familyAnnotation.stringValue("value")
            ?.takeIf(String::isNotBlank)
            ?: exceptionStem.toUpperSnake()
        val declaredFields = type.annotations.compileFields(type.id)
        val codes = type.enumEntries.map { entry ->
            compileCode(entry, declaredFields)
        }
        return ErrorFamilyModel(
            id = type.id,
            qualifiedName = type.qualifiedName,
            packageName = type.packageName(allTypes),
            family = family,
            exceptionSimpleName = exceptionStem + "Exception",
            checkedException = options.checkedException,
            documentation = type.documentation.normalizedDocumentation(),
            originatingSources = type.origin.source?.let(::setOf).orEmpty(),
            declaredFields = declaredFields,
            codes = codes,
        )
    }

    private fun compileCode(
        entry: LsiEnumEntry,
        sharedFields: List<ErrorFieldModel>,
    ): ErrorCodeModel {
        val declaredFields = entry.annotations.compileFields(entry.id)
        val sharedNames = sharedFields.mapTo(hashSetOf(), ErrorFieldModel::name)
        val duplicate = declaredFields.firstOrNull { field -> field.name in sharedNames }
        if (duplicate != null) {
            throw ErrorPrecompileException(
                declarationId = entry.id,
                message = "Error field '${duplicate.name}' has already been declared by the error family",
            )
        }
        return ErrorCodeModel(
            id = entry.id,
            enumEntryName = entry.name,
            code = entry.name.toUpperSnake(),
            creatorName = entry.name.toCamelName(upperHead = false),
            exceptionSimpleName = entry.name.toCamelName(upperHead = true),
            documentation = entry.documentation.normalizedDocumentation(),
            declaredFields = declaredFields,
            fields = sharedFields + declaredFields,
        )
    }
}

private fun List<LsiAnnotation>.compileFields(
    declarationId: LsiSymbolId,
): List<ErrorFieldModel> {
    val annotations = flatMap { annotation ->
        when (annotation.type) {
            ERROR_FIELD_ANNOTATION -> listOf(annotation)
            ERROR_FIELDS_ANNOTATION -> annotation.nestedAnnotations("value")
            else -> emptyList()
        }
    }
    val names = hashSetOf<String>()
    return annotations.map { annotation ->
        val name = annotation.stringValue("name")
            ?.takeIf(String::isNotBlank)
            ?: throw ErrorPrecompileException(
                declarationId = declarationId,
                message = "Error field name cannot be blank",
            )
        if (name == "family" || name == "code") {
            throw ErrorPrecompileException(
                declarationId = declarationId,
                message = "Error field '$name' conflicts with built-in exception metadata",
            )
        }
        if (!names.add(name)) {
            throw ErrorPrecompileException(
                declarationId = declarationId,
                message = "Duplicate error field '$name'",
            )
        }
        val type = annotation.classValue("type")
            ?: throw ErrorPrecompileException(
                declarationId = declarationId,
                message = "Error field '$name' must declare a type",
            )
        val list = annotation.booleanValue("list")
        if (list && type is LsiPrimitiveType) {
            throw ErrorPrecompileException(
                declarationId = declarationId,
                message = "Error field '$name' cannot be a list of primitive values",
            )
        }
        ErrorFieldModel(
            name = name,
            type = type,
            list = list,
            nullable = annotation.booleanValue("nullable"),
            documentation = annotation.stringValue("doc").normalizedDocumentation(),
            declaredBy = declarationId,
        )
    }
}

private fun LsiTypeDeclaration.longSimpleName(
    allTypes: List<LsiTypeDeclaration>,
): String {
    val enclosingType = enclosingType(allTypes) ?: return name
    return enclosingType.longSimpleName(allTypes) + "_" + name
}

private fun LsiTypeDeclaration.packageName(
    allTypes: List<LsiTypeDeclaration>,
): String {
    val enclosingType = enclosingType(allTypes)
    if (enclosingType != null) {
        return enclosingType.packageName(allTypes)
    }
    return qualifiedName.removeSuffix(".$name").takeUnless { value -> value == qualifiedName }.orEmpty()
}

private fun LsiTypeDeclaration.enclosingType(
    allTypes: List<LsiTypeDeclaration>,
): LsiTypeDeclaration? {
    return allTypes
        .asSequence()
        .filter { candidate -> candidate.id != id }
        .filter { candidate -> qualifiedName.startsWith(candidate.qualifiedName + ".") }
        .maxByOrNull { candidate -> candidate.qualifiedName.length }
}

private fun String.errorExceptionStem(): String {
    return when {
        endsWith("_ErrorCode") -> dropLast(10)
        endsWith("ErrorCode") -> dropLast(9)
        endsWith("_Error") -> dropLast(6)
        endsWith("Error") -> dropLast(5)
        else -> this
    }
}

private fun String.toCamelName(upperHead: Boolean): String {
    val result = StringBuilder(length)
    var uppercaseNext = upperHead
    for (character in this) {
        if (character == '_') {
            uppercaseNext = true
        } else {
            result.append(if (uppercaseNext) character.uppercaseChar() else character.lowercaseChar())
            uppercaseNext = false
        }
    }
    return result.toString()
}

private fun String.toUpperSnake(): String {
    val result = StringBuilder(length + 8)
    forEachIndexed { index, character ->
        if (character == '_') {
            if (result.isNotEmpty() && result.last() != '_') {
                result.append('_')
            }
            return@forEachIndexed
        }
        val previous = getOrNull(index - 1)
        val next = getOrNull(index + 1)
        val boundary = character.isUpperCase() && index > 0 && previous != '_' &&
            (previous?.isLowerCase() == true || previous?.isDigit() == true || next?.isLowerCase() == true)
        if (boundary && result.isNotEmpty() && result.last() != '_') {
            result.append('_')
        }
        result.append(character.uppercaseChar())
    }
    return result.toString().trim('_')
}

private fun String?.normalizedDocumentation(): String? {
    return this
        ?.replace("\r\n", "\n")
        ?.replace('\r', '\n')
        ?.trim()
        ?.takeIf(String::isNotEmpty)
}

private fun List<LsiAnnotation>.annotation(type: LsiSymbolId): LsiAnnotation? {
    return firstOrNull { annotation -> annotation.type == type }
}

private fun List<LsiAnnotation>.hasAnnotation(type: LsiSymbolId): Boolean {
    return any { annotation -> annotation.type == type }
}

private fun LsiAnnotation.stringValue(name: String): String? {
    return (arguments[name]?.value as? LsiAnnotationValue.StringValue)?.value
}

private fun LsiAnnotation.booleanValue(name: String): Boolean {
    return (arguments[name]?.value as? LsiAnnotationValue.BooleanValue)?.value ?: false
}

private fun LsiAnnotation.classValue(name: String): LsiTypeRef? {
    return (arguments[name]?.value as? LsiAnnotationValue.ClassValue)?.type
}

private fun LsiAnnotation.nestedAnnotations(name: String): List<LsiAnnotation> {
    val value = arguments[name]?.value ?: return emptyList()
    return when (value) {
        is LsiAnnotationValue.NestedAnnotationValue -> listOf(value.annotation)
        is LsiAnnotationValue.ArrayValue -> value.elements.mapNotNull { element ->
            (element as? LsiAnnotationValue.NestedAnnotationValue)?.annotation
        }
        else -> emptyList()
    }
}

private val ERROR_FAMILY_ANNOTATION = LsiSymbolId.type("org.babyfish.jimmer.error.ErrorFamily")
private val ERROR_FIELD_ANNOTATION = LsiSymbolId.type("org.babyfish.jimmer.error.ErrorField")
private val ERROR_FIELDS_ANNOTATION = LsiSymbolId.type("org.babyfish.jimmer.error.ErrorFields")
